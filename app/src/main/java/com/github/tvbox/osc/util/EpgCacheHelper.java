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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class EpgCacheHelper {
    private final Context context;
    private final Handler mainHandler;
    private String epgBaseUrl;
    
    private final Map<String, ArrayList<Epginfo>> memoryCache = new LinkedHashMap<String, ArrayList<Epginfo>>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ArrayList<Epginfo>> eldest) {
            return size() > LiveConstants.MAX_EPG_MEMORY_CACHE;
        }
    };
    private final Object cacheLock = new Object();
    private final Set<String> pendingRequests = new HashSet<>();
    private final AtomicLong currentChannelRequestId = new AtomicLong(0);
    
    private ExecutorService highPriorityExecutor;
    private ExecutorService lowPriorityExecutor;
    private OkHttpClient httpClient;
    
    public interface LogoCallback {
        void onLogoLoaded(String channelName, String logoUrl);
    }
    private LogoCallback logoCallback;
    
    public interface EpgCallback {
        void onSuccess(String channelName, Date date, ArrayList<Epginfo> epgList);
        void onFailure(String channelName, Date date, Exception e);
    }
    
    public EpgCacheHelper(Context context, String epgBaseUrl) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.epgBaseUrl = epgBaseUrl;
        this.highPriorityExecutor = Executors.newFixedThreadPool(LiveConstants.HIGH_PRIORITY_THREADS);
        this.lowPriorityExecutor = Executors.newFixedThreadPool(LiveConstants.LOW_PRIORITY_THREADS);
    }
    
    public void setLogoCallback(LogoCallback callback) {
        this.logoCallback = callback;
    }
    
    public ArrayList<Epginfo> getCachedEpg(String channelName, String dateStr) {
        ArrayList<Epginfo> cached = getFromMemoryCache(channelName, dateStr);
        if (cached != null && !cached.isEmpty()) return cached;
        cached = getFromFileCache(channelName, dateStr);
        if (cached != null && !cached.isEmpty()) {
            putToMemoryCache(channelName, dateStr, cached);
            return cached;
        }
        return null;
    }
    
    public void requestEpg(String channelName, Date date, EpgCallback callback, boolean isCurrentChannel) {
        if (channelName == null || date == null || callback == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
        String dateStr = sdf.format(date);
        
        ArrayList<Epginfo> cached = getFromMemoryCache(channelName, dateStr);
        if (cached != null && !cached.isEmpty()) {
            final String finalChannelName = channelName;
            final Date finalDate = date;
            final ArrayList<Epginfo> finalCached = cached;
            mainHandler.post(() -> callback.onSuccess(finalChannelName, finalDate, finalCached));
            return;
        }
        
        cached = getFromFileCache(channelName, dateStr);
        if (cached != null && !cached.isEmpty()) {
            putToMemoryCache(channelName, dateStr, cached);
            final String finalChannelName = channelName;
            final Date finalDate = date;
            final ArrayList<Epginfo> finalCached = cached;
            mainHandler.post(() -> callback.onSuccess(finalChannelName, finalDate, finalCached));
            return;
        }
        
        final long requestId = isCurrentChannel ? currentChannelRequestId.incrementAndGet() : 0;
        final String reqChannelName = channelName;
        final Date reqDate = date;
        final String reqDateStr = dateStr;
        highPriorityExecutor.execute(() -> fetchFromNetwork(reqChannelName, reqDate, reqDateStr, requestId, callback));
    }
    
    public void preloadCurrentChannel(String channelName) {
        if (channelName == null) return;
        List<String> dates = getPreloadDates();
        highPriorityExecutor.execute(() -> {
            for (String dateStr : dates) {
                if (getCachedEpg(channelName, dateStr) != null) continue;
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
                    Thread.sleep(LiveConstants.PRELOAD_SLEEP_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }
    
    public void preloadOtherChannels(List<String> channelNames, String currentChannelName) {
        if (channelNames == null || channelNames.isEmpty()) return;
        List<String> dates = getPreloadDates();
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
    
    private File getEpgCacheFile(String channelName, String date) {
        String fileName = channelName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "_") + "_" + date + ".json";
        File dir = new File(context.getFilesDir(), LiveConstants.EPG_CACHE_DIR);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, fileName);
    }
    
    private Date combineDateAndTime(Date date, String timeStr) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD + " HH:mm");
            String dateStr = dateFormat.format(date);
            return dateTimeFormat.parse(dateStr + " " + timeStr);
        } catch (Exception e) {
            return date;
        }
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
            String logoUrl = cacheData.optString("logoUrl", null);
            if (logoUrl != null && !logoUrl.isEmpty() && logoCallback != null) {
                final String finalChannelName = channelName;
                final String finalLogoUrl = logoUrl;
                mainHandler.post(() -> logoCallback.onLogoLoaded(finalChannelName, finalLogoUrl));
            }
            JSONArray epgArray = cacheData.optJSONArray("epgList");
            if (epgArray == null || epgArray.length() == 0) return null;
            
            ArrayList<Epginfo> epgList = new ArrayList<>();
            Date baseDate = parseDate(date);
            
            for (int i = 0; i < epgArray.length(); i++) {
                JSONObject epgObj = epgArray.getJSONObject(i);
                String title = epgObj.optString("title", LiveConstants.NO_PROGRAM);
                String startStr = epgObj.optString("start", LiveConstants.DEFAULT_START_TIME);
                String endStr = epgObj.optString("end", LiveConstants.DEFAULT_END_TIME);
                String originStart = epgObj.optString("originStart", startStr);
                String originEnd = epgObj.optString("originEnd", endStr);
                
                // 构造正确的时间（处理跨天）
                Date startDateTime = combineDateAndTime(baseDate, startStr);
                Date endDateTime = combineDateAndTime(baseDate, endStr);
                if (endDateTime.before(startDateTime)) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(endDateTime);
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                    endDateTime = cal.getTime();
                }
                
                Epginfo epg = new Epginfo(baseDate, title, baseDate, startStr, endStr, i);
                epg.startdateTime = startDateTime;
                epg.enddateTime = endDateTime;
                epg.originStart = originStart;
                epg.originEnd = originEnd;
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
    
    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build();
        }
        return httpClient;
    }
    
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
            return new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD, Locale.getDefault()).parse(dateStr);
        } catch (Exception e) {
            return new Date();
        }
    }
    
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
                epgUrl = epgBaseUrl.replace("{name}", URLEncoder.encode(epgTagName, "UTF-8"))
                        .replace("{date}", sdf.format(date));
            } else {
                epgUrl = epgBaseUrl + "?ch=" + URLEncoder.encode(epgTagName, "UTF-8") + "&date=" + sdf.format(date);
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
                                    String title = jSONObject.optString("title", LiveConstants.NO_PROGRAM);
                                    String startStr = jSONObject.optString("start", LiveConstants.DEFAULT_START_TIME);
                                    String endStr = jSONObject.optString("end", LiveConstants.DEFAULT_END_TIME);
                                    
                                    // 构造正确的时间（处理跨天）
                                    Date startDateTime = combineDateAndTime(date, startStr);
                                    Date endDateTime = combineDateAndTime(date, endStr);
                                    if (endDateTime.before(startDateTime)) {
                                        Calendar cal = Calendar.getInstance();
                                        cal.setTime(endDateTime);
                                        cal.add(Calendar.DAY_OF_MONTH, 1);
                                        endDateTime = cal.getTime();
                                    }
                                    
                                    Epginfo epg = new Epginfo(date, title, date, startStr, endStr, b);
                                    epg.startdateTime = startDateTime;
                                    epg.enddateTime = endDateTime;
                                    epg.originStart = startStr;
                                    epg.originEnd = endStr;
                                    arrayList.add(epg);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (!arrayList.isEmpty()) {
                        saveToFileCache(channelName, dateStr, arrayList, logoUrl);
                        if (callback != null && requestId != 0 && requestId == currentChannelRequestId.get()) {
                            final String finalChannelName = channelName;
                            final Date finalDate = date;
                            final ArrayList<Epginfo> finalArrayList = arrayList;
                            mainHandler.post(() -> callback.onSuccess(finalChannelName, finalDate, finalArrayList));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (callback != null && requestId != 0) {
                final String finalChannelName = channelName;
                final Date finalDate = date;
                final Exception finalException = e;
                mainHandler.post(() -> callback.onFailure(finalChannelName, finalDate, finalException));
            }
        } finally {
            synchronized (pendingRequests) {
                pendingRequests.remove(channelName + "_" + dateStr);
            }
        }
    }
}
