package com.github.tvbox.osc.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.constant.LiveConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * EPG缓存和网络请求管理类
 * 负责：内存缓存、文件缓存、网络请求、预加载调度
 */
public class EpgCacheHelper {
    private final Context context;
    private final Handler mainHandler;
    private String epgBaseUrl;
    
    // ==================== 缓存相关 ====================
    private final Map<String, ArrayList<Epginfo>> memoryCache = new LinkedHashMap<String, ArrayList<Epginfo>>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ArrayList<Epginfo>> eldest) {
            return size() > LiveConstants.MAX_EPG_MEMORY_CACHE;
        }
    };
    private final Object cacheLock = new Object();
    private final Set<String> pendingRequests = new HashSet<>();
    private final AtomicLong currentChannelRequestId = new AtomicLong(0);
    
    // ==================== 线程池 ====================
    private ExecutorService highPriorityExecutor;  // 当前频道专用
    private ExecutorService lowPriorityExecutor;   // 其他频道预加载
    
    // ==================== HTTP客户端 ====================
    private OkHttpClient httpClient;
    
    // ==================== 构造函数 ====================
    public EpgCacheHelper(Context context, String epgBaseUrl) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.epgBaseUrl = epgBaseUrl;
        this.highPriorityExecutor = Executors.newFixedThreadPool(LiveConstants.HIGH_PRIORITY_THREADS);
        this.lowPriorityExecutor = Executors.newFixedThreadPool(LiveConstants.LOW_PRIORITY_THREADS);
    }
    
    // ==================== 公开接口 ====================
    
    /**
     * 请求 EPG 数据（优先缓存，无则网络）
     * @param channelName 频道名
     * @param date 日期
     * @param callback 回调（在主线程执行）
     */
    public void requestEpg(String channelName, Date date, EpgCallback callback) {
        if (channelName == null || date == null || callback == null) return;
        
        SimpleDateFormat sdf = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
        String dateStr = sdf.format(date);
        
        // 1. 内存缓存
        ArrayList<Epginfo> cached = getFromMemoryCache(channelName, dateStr);
        if (cached != null && !cached.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(channelName, date, cached));
            return;
        }
        
        // 2. 文件缓存
        cached = getFromFileCache(channelName, dateStr);
        if (cached != null && !cached.isEmpty()) {
            putToMemoryCache(channelName, dateStr, cached);
            mainHandler.post(() -> callback.onSuccess(channelName, date, cached));
            return;
        }
        
        // 3. 网络请求（异步）
        final long requestId = currentChannelRequestId.incrementAndGet();
        highPriorityExecutor.execute(() -> {
            fetchFromNetwork(channelName, date, dateStr, requestId, callback);
        });
    }
    
    /**
     * 预加载当前频道的所有日期
     * @param channelName 频道名
     */
    public void preloadCurrentChannel(String channelName) {
        if (channelName == null) return;
        
        final List<String> dates = getPreloadDates();
        highPriorityExecutor.execute(() -> {
            for (String dateStr : dates) {
                String taskKey = channelName + "_" + dateStr;
                synchronized (pendingRequests) {
                    if (pendingRequests.contains(taskKey)) continue;
                    pendingRequests.add(taskKey);
                }
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
                    Date date = sdf.parse(dateStr);
                    // 预加载不需要更新UI，传 null callback
                    fetchFromNetwork(channelName, date, dateStr, 0, null);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    synchronized (pendingRequests) {
                        pendingRequests.remove(taskKey);
                    }
                }
                try {
                    Thread.sleep(LiveConstants.PRELOAD_SLEEP_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }
    
    /**
     * 预加载其他频道的 EPG
     * @param channelNames 所有频道名列表
     * @param currentChannelName 当前频道名（跳过）
     */
    public void preloadOtherChannels(List<String> channelNames, String currentChannelName) {
        if (channelNames == null || channelNames.isEmpty()) return;
        
        final List<String> dates = getPreloadDates();
        lowPriorityExecutor.execute(() -> {
            for (String channelName : channelNames) {
                if (channelName.equals(currentChannelName)) continue;
                for (String dateStr : dates) {
                    String taskKey = channelName + "_" + dateStr;
                    synchronized (pendingRequests) {
                        if (pendingRequests.contains(taskKey)) continue;
                        pendingRequests.add(taskKey);
                    }
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
                        Date date = sdf.parse(dateStr);
                        fetchFromNetwork(channelName, date, dateStr, 0, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        synchronized (pendingRequests) {
                            pendingRequests.remove(taskKey);
                        }
                    }
                    try {
                        Thread.sleep(LiveConstants.PRELOAD_OTHER_SLEEP_MS);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });
    }
    
    /**
     * 更新 EPG 基础 URL（用于动态切换源）
     */
    public void updateBaseUrl(String newUrl) {
        this.epgBaseUrl = newUrl;
    }
    
    /**
     * 清理资源（在 Activity onDestroy 中调用）
     */
    public void destroy() {
        if (highPriorityExecutor != null && !highPriorityExecutor.isShutdown()) {
            highPriorityExecutor.shutdownNow();
        }
        if (lowPriorityExecutor != null && !lowPriorityExecutor.isShutdown()) {
            lowPriorityExecutor.shutdownNow();
        }
        synchronized (cacheLock) {
            memoryCache.clear();
        }
        synchronized (pendingRequests) {
            pendingRequests.clear();
        }
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient = null;
        }
    }
    
    // ==================== 回调接口 ====================
    public interface EpgCallback {
        void onSuccess(String channelName, Date date, ArrayList<Epginfo> epgList);
        void onFailure(String channelName, Date date, Exception e);
    }
    
    // ==================== 私有方法 ====================
    
    // ---------- 内存缓存 ----------
    private ArrayList<Epginfo> getFromMemoryCache(String channelName, String date) {
        String key = channelName + "_" + date;
        synchronized (cacheLock) {
            return memoryCache.get(key);
        }
    }
    
    private void putToMemoryCache(String channelName, String date, ArrayList<Epginfo> epgList) {
        if (epgList == null || epgList.isEmpty()) return;
        String key = channelName + "_" + date;
        synchronized (cacheLock) {
            memoryCache.put(key, epgList);
        }
    }
    
    // ---------- 文件缓存 ----------
    private File getEpgCacheFile(String channelName, String date) {
        String fileName = channelName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "_") + "_" + date + ".json";
        File dir = new File(context.getFilesDir(), LiveConstants.EPG_CACHE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, fileName);
    }
    
    private ArrayList<Epginfo> getFromFileCache(String channelName, String date) {
        File cacheFile = getEpgCacheFile(channelName, date);
        if (!cacheFile.exists() || cacheFile.length() < 50) return null;
        try {
            StringBuilder content = new StringBuilder();
            try (FileReader reader = new FileReader(cacheFile)) {
                char[] buffer = new char[4096];
                int len;
                while ((len = reader.read(buffer)) != -1) {
                    content.append(buffer, 0, len);
                }
            }
            JSONObject cacheData = new JSONObject(content.toString());
            long timestamp = cacheData.optLong("timestamp", 0);
            if (System.currentTimeMillis() - timestamp > LiveConstants.EPG_CACHE_VALID_TIME) {
                cacheFile.delete();
                return null;
            }
            // logoUrl 在这里不处理，由调用方决定是否使用
            JSONArray epgArray = cacheData.optJSONArray("epgList");
            if (epgArray == null || epgArray.length() == 0) return null;
            ArrayList<Epginfo> epgList = new ArrayList<>();
            Date dateObj = parseDate(date);
            for (int i = 0; i < epgArray.length(); i++) {
                JSONObject epgObj = epgArray.getJSONObject(i);
                Epginfo epg = new Epginfo(dateObj, epgObj.optString("title", LiveConstants.NO_PROGRAM), dateObj,
                        epgObj.optString("start", LiveConstants.DEFAULT_START_TIME),
                        epgObj.optString("end", LiveConstants.DEFAULT_END_TIME), i);
                epg.originStart = epgObj.optString("originStart", LiveConstants.DEFAULT_START_TIME);
                epg.originEnd = epgObj.optString("originEnd", LiveConstants.DEFAULT_END_TIME);
                epgList.add(epg);
            }
            return epgList;
        } catch (Exception e) {
            cacheFile.delete();
            return null;
        }
    }
    
    private void saveToFileCache(String channelName, String date, ArrayList<Epginfo> newEpgList, String logoUrl) {
        if (newEpgList == null || newEpgList.isEmpty()) return;
        putToMemoryCache(channelName, date, newEpgList);
        lowPriorityExecutor.execute(() -> {
            try {
                // 合并已有缓存（去重）
                ArrayList<Epginfo> existingList = getFromFileCache(channelName, date);
                Map<String, Epginfo> mergedMap = new LinkedHashMap<>();
                if (existingList != null) {
                    for (Epginfo epg : existingList) {
                        mergedMap.put(epg.start + "_" + epg.end, epg);
                    }
                }
                for (Epginfo epg : newEpgList) {
                    mergedMap.put(epg.start + "_" + epg.end, epg);
                }
                ArrayList<Epginfo> finalList = new ArrayList<>(mergedMap.values());
                finalList.sort((a, b) -> a.start.compareTo(b.start));
                if (finalList.size() > LiveConstants.EPG_MAX_ITEMS) {
                    finalList = new ArrayList<>(finalList.subList(0, LiveConstants.EPG_MAX_ITEMS));
                }
                File cacheFile = getEpgCacheFile(channelName, date);
                File tempFile = new File(cacheFile.getParent(), cacheFile.getName() + ".tmp");
                JSONObject cacheData = new JSONObject();
                cacheData.put("channelName", channelName);
                cacheData.put("date", date);
                cacheData.put("timestamp", System.currentTimeMillis());
                cacheData.put("logoUrl", logoUrl != null ? logoUrl : "");
                JSONArray epgArray = new JSONArray();
                for (Epginfo epg : finalList) {
                    JSONObject epgObj = new JSONObject();
                    epgObj.put("title", epg.title);
                    epgObj.put("start", epg.start);
                    epgObj.put("end", epg.end);
                    epgObj.put("originStart", epg.originStart);
                    epgObj.put("originEnd", epg.originEnd);
                    epgArray.put(epgObj);
                }
                cacheData.put("epgList", epgArray);
                try (FileWriter writer = new FileWriter(tempFile)) {
                    writer.write(cacheData.toString());
                }
                tempFile.renameTo(cacheFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    // ---------- HTTP 客户端 ----------
    private synchronized OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build();
        }
        return httpClient;
    }
    
    // ---------- 预加载日期 ----------
    private List<String> getPreloadDates() {
        List<String> dates = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, -LiveConstants.PRELOAD_DAYS_BEFORE);
        for (int i = 0; i < LiveConstants.PRELOAD_DAYS_BEFORE + LiveConstants.PRELOAD_DAYS_AFTER + 1; i++) {
            dates.add(dateFormat.format(calendar.getTime()));
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return dates;
    }
    
    private Date parseDate(String dateStr) {
        try {
            return new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD).parse(dateStr);
        } catch (Exception e) {
            return new Date();
        }
    }
    
    // ---------- 网络请求核心方法 ----------
    private void fetchFromNetwork(String channelName, Date date, String dateStr, long requestId, EpgCallback callback) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
            String[] epgInfo = EpgUtil.getEpgInfo(channelName);
            String epgTagName = channelName;
            String logoUrl = null;
            if (epgInfo != null) {
                if (epgInfo[0] != null) logoUrl = epgInfo[0];
                if (epgInfo.length > 1 && epgInfo[1] != null && !epgInfo[1].isEmpty()) {
                    epgTagName = epgInfo[1];
                }
            }
            
            String epgUrl;
            if (epgBaseUrl.contains("{name}") && epgBaseUrl.contains("{date}")) {
                epgUrl = epgBaseUrl.replace("{name}", URLEncoder.encode(epgTagName))
                        .replace("{date}", sdf.format(date));
            } else {
                epgUrl = epgBaseUrl + "?ch=" + URLEncoder.encode(epgTagName) + "&date=" + sdf.format(date);
            }
            
            Request request = new Request.Builder().url(epgUrl).build();
            try (okhttp3.Response response = getHttpClient().newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String paramString = response.body().string();
                    ArrayList<Epginfo> arrayList = new ArrayList<>();
                    try {
                        if (paramString.contains("epg_data")) {
                            JSONObject json = new JSONObject(paramString);
                            String newLogoUrl = json.optString("logo", null);
                            if (newLogoUrl != null && !newLogoUrl.isEmpty()) logoUrl = newLogoUrl;
                            JSONArray jSONArray = json.optJSONArray("epg_data");
                            if (jSONArray != null) {
                                int length = Math.min(jSONArray.length(), LiveConstants.EPG_MAX_ITEMS);
                                for (int b = 0; b < length; b++) {
                                    JSONObject jSONObject = jSONArray.getJSONObject(b);
                                    Epginfo epg = new Epginfo(date, jSONObject.optString("title", LiveConstants.NO_PROGRAM),
                                            date, jSONObject.optString("start", LiveConstants.DEFAULT_START_TIME),
                                            jSONObject.optString("end", LiveConstants.DEFAULT_END_TIME), b);
                                    arrayList.add(epg);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (!arrayList.isEmpty()) {
                        saveToFileCache(channelName, dateStr, arrayList, logoUrl);
                        // 只有 callback 不为 null 且 requestId 匹配时才回调 UI
                        if (callback != null && (requestId == 0 || requestId == currentChannelRequestId.get())) {
                            mainHandler.post(() -> callback.onSuccess(channelName, date, arrayList));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (callback != null) {
                mainHandler.post(() -> callback.onFailure(channelName, date, e));
            }
        } finally {
            synchronized (pendingRequests) {
                pendingRequests.remove(channelName + "_" + dateStr);
            }
        }
    }
}
