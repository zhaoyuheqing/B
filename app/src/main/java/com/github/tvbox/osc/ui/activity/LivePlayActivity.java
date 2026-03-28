package com.github.tvbox.osc.ui.activity;

import static xyz.doikki.videoplayer.util.PlayerUtils.stringForTimeVod;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveEpgDate;
import com.github.tvbox.osc.bean.LivePlayerManager;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.controller.LiveController;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemAdapter;
import com.github.tvbox.osc.ui.adapter.LiveEpgAdapter;
import com.github.tvbox.osc.ui.adapter.LiveEpgDateAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingItemAdapter;
import com.github.tvbox.osc.ui.dialog.ApiHistoryDialog;
import com.github.tvbox.osc.ui.dialog.LivePasswordDialog;
import com.github.tvbox.osc.util.EpgCacheHelper;
import com.github.tvbox.osc.util.EpgUtil;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HawkUtils;
import com.github.tvbox.osc.util.JavaUtil;
import com.github.tvbox.osc.util.live.TxtSubscribe;
import com.google.gson.JsonArray;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.apache.commons.lang3.StringUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import kotlin.Pair;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

/**
 * @author pj567
 * @date :2021/1/12
 * @description: LivePlayActivity with EPG cache and preload
 * 
 * ==================== 功能区域索引 ====================
 * 
 * 区域1: 成员变量声明
 *   - 1.1 核心播放器变量
 *   - 1.2 UI控件变量
 *   - 1.3 频道数据变量
 *   - 1.4 右侧设置面板变量
 *   - 1.5 EPG相关变量
 *   - 1.6 EPG缓存管理器
 *   - 1.7 时移/回放相关变量
 *   - 1.8 动画Runnable变量
 *   - 1.9 其他变量
 * 
 * 区域2: 工具方法
 *   - 2.1 播放请求头
 *   - 2.2 时移URL构建
 * 
 * 区域3: EPG获取和预加载（使用 EpgCacheHelper）
 * 
 * 区域4: 生命周期方法
 * 
 * 区域5: UI显示方法
 * 
 * 区域6: 播放控制方法
 * 
 * 区域7: 设置面板方法
 * 
 * 区域8: 初始化方法
 * 
 * 区域9: 辅助方法
 * 
 * ====================================================
 */
public class LivePlayActivity extends BaseActivity {

    // ==================== 区域1: 成员变量声明 ====================
    // ============================================================

    // ---------- 1.1 核心播放器变量 ----------
    private VideoView mVideoView;
    private LiveController controller;

    // ---------- 1.2 UI控件变量 ----------
    // Left Channel View
    private LinearLayout tvLeftChannelListLayout;
    private TvRecyclerView mGroupGridView;
    private LinearLayout mDivLeft;
    private TvRecyclerView mChannelGridView;
    private LinearLayout mDivRight;
    private LinearLayout mGroupEPG;
    private TvRecyclerView mEpgDateGridView;
    private TvRecyclerView mEpgInfoGridView;
    
    // Bottom Channel View
    private LinearLayout tvBottomLayout;
    private ImageView tv_logo;
    private TextView tv_sys_time;
    private TextView tv_size;
    private TextView tv_source;
    private TextView tv_channelname;
    private TextView tv_channelnum;
    private TextView tv_curr_name;
    private TextView tv_curr_time;
    private TextView tv_next_name;
    private TextView tv_next_time;
    
    // Right Channel View
    private LinearLayout tvRightSettingLayout;
    private TvRecyclerView mSettingGroupView;
    private TvRecyclerView mSettingItemView;
    
    // Other UI
    private TextView tvSelectedChannel;
    private TextView tvTime;
    private TextView tvNetSpeed;
    private LinearLayout mBack;
    private LinearLayout llSeekBar;
    private TextView mCurrentTime;
    private SeekBar mSeekBar;
    private TextView mTotalTime;

    // ---------- 1.3 频道数据变量 ----------
    private LiveChannelGroupAdapter liveChannelGroupAdapter;
    private LiveChannelItemAdapter liveChannelItemAdapter;
    private final List<LiveChannelGroup> liveChannelGroupList = new ArrayList<>();
    private final List<LiveSettingGroup> liveSettingGroupList = new ArrayList<>();
    public static int currentChannelGroupIndex = 0;
    private int currentLiveChannelIndex = -1;
    private LiveChannelItem currentLiveChannelItem = null;
    private final ArrayList<Integer> channelGroupPasswordConfirmed = new ArrayList<>();
    private int selectedChannelNumber = 0;

    // ---------- 1.4 右侧设置面板变量 ----------
    private LiveSettingGroupAdapter liveSettingGroupAdapter;
    private LiveSettingItemAdapter liveSettingItemAdapter;
    private final LivePlayerManager livePlayerManager = new LivePlayerManager();
    private int currentLiveChangeSourceTimes = 0;

    // ---------- 1.5 EPG相关变量 ----------
    private LiveEpgDateAdapter epgDateAdapter;
    private LiveEpgAdapter epgListAdapter;
    public String epgStringAddress = "";
    SimpleDateFormat timeFormat = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
    private final Handler mHandler = new Handler();
    private static final Hashtable<String, ArrayList<Epginfo>> hsEpg = new Hashtable<>();
    private List<Epginfo> epgdata = new ArrayList<>();
    
    // ---------- 1.6 EPG缓存管理器 ----------
    private EpgCacheHelper epgCacheHelper;

    // ---------- 1.7 时移/回放相关变量 ----------
    private boolean isShiyiMode = false;
    private static String shiyi_time;

    // ---------- 1.8 动画Runnable变量 ----------
    private final Runnable mHideChannelListRun = new Runnable() {
        @Override
        public void run() {
            if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                tvLeftChannelListLayout.animate().translationX(-tvLeftChannelListLayout.getWidth() / 2).alpha(0.0f)
                        .setDuration(250).setInterpolator(new DecelerateInterpolator())
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                tvLeftChannelListLayout.setVisibility(View.INVISIBLE);
                                tvLeftChannelListLayout.clearAnimation();
                            }
                        });
            }
        }
    };

    private final Runnable mHideChannelInfoRun = new Runnable() {
        @Override
        public void run() {
            mBack.setVisibility(View.INVISIBLE);
            if (tvBottomLayout.getVisibility() == View.VISIBLE) {
                tvBottomLayout.animate().alpha(0.0f).setDuration(250).setInterpolator(new DecelerateInterpolator())
                        .translationY(tvBottomLayout.getHeight() / 2).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                tvBottomLayout.setVisibility(View.INVISIBLE);
                                tvBottomLayout.clearAnimation();
                            }
                        });
            }
        }
    };

    private final Runnable mHideSettingLayoutRun = new Runnable() {
        @Override
        public void run() {
            if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
                tvRightSettingLayout.animate().translationX(tvRightSettingLayout.getWidth() / 2).alpha(0.0f)
                        .setDuration(250).setInterpolator(new DecelerateInterpolator())
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                tvRightSettingLayout.setVisibility(View.INVISIBLE);
                                tvRightSettingLayout.clearAnimation();
                                liveSettingGroupAdapter.setSelectedGroupIndex(-1);
                            }
                        });
            }
        }
    };

    private final Runnable mUpdateLayout = new Runnable() {
        @Override
        public void run() {
            tvLeftChannelListLayout.requestLayout();
            tvRightSettingLayout.requestLayout();
        }
    };

    private final Runnable mUpdateTimeRun = new Runnable() {
        @Override
        public void run() {
            tvTime.setText(new SimpleDateFormat(LiveConstants.TIME_FORMAT_HHMMSS).format(new Date()));
            mHandler.postDelayed(this, 1000);
        }
    };

    private final Runnable mUpdateNetSpeedRun = new Runnable() {
        @Override
        public void run() {
            if (mVideoView != null) {
                tvNetSpeed.setText(String.format("%.2fMB/s", (float) mVideoView.getTcpSpeed() / 1024.0 / 1024.0));
            }
            mHandler.postDelayed(this, 1000);
        }
    };

    private final Runnable tv_sys_timeRunnable = new Runnable() {
        @Override
        public void run() {
            Date date = new Date();
            tv_sys_time.setText(new SimpleDateFormat(LiveConstants.TIME_FORMAT_HHMMSS, Locale.ENGLISH).format(date));
            mHandler.postDelayed(this, 1000);
            if (mVideoView != null && !mIsDragging && mVideoView.getDuration() > 0) {
                int currentPosition = (int) mVideoView.getCurrentPosition();
                mCurrentTime.setText(stringForTimeVod(currentPosition));
                mSeekBar.setProgress(currentPosition);
            }
        }
    };

    private final Runnable mConnectTimeoutChangeSourceRun = new Runnable() {
        @Override
        public void run() {
            if (currentLiveChannelItem == null) return;
            currentLiveChangeSourceTimes++;
            if (currentLiveChannelItem.getSourceNum() == currentLiveChangeSourceTimes) {
                currentLiveChangeSourceTimes = 0;
                Integer[] groupChannelIndex = getNextChannel(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false) ? -1 : 1);
                if (groupChannelIndex[0] >= 0 && groupChannelIndex[1] >= 0) {
                    playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
                }
            } else {
                playNextSource();
            }
        }
    };

    private final Runnable mConnectTimeoutReplayRun = new Runnable() {
        @Override
        public void run() { replayChannel(); }
    };

    private final Runnable mPlaySelectedChannel = new Runnable() {
        @Override
        public void run() {
            tvSelectedChannel.setVisibility(View.GONE);
            tvSelectedChannel.setText("");
            int grpIndx = 0, chaIndx = 0, getMin = 1, getMax;
            for (int j = 0; j < LiveConstants.MAX_CHANNEL_GROUPS; j++) {
                getMax = getMin + getLiveChannels(j).size() - 1;
                if (selectedChannelNumber >= getMin && selectedChannelNumber <= getMax) {
                    grpIndx = j;
                    chaIndx = selectedChannelNumber - getMin + 1;
                    break;
                } else {
                    getMin = getMax + 1;
                }
            }
            if (selectedChannelNumber > 0) {
                playChannel(grpIndx, chaIndx - 1, false);
            }
            selectedChannelNumber = 0;
        }
    };

    private final Runnable mFocusCurrentChannelAndShowChannelList = new Runnable() {
        @Override
        public void run() {
            if (mGroupGridView.isScrolling() || mChannelGridView.isScrolling() || mGroupGridView.isComputingLayout() || mChannelGridView.isComputingLayout()) {
                mHandler.postDelayed(this, 100);
            } else {
                liveChannelGroupAdapter.setSelectedGroupIndex(currentChannelGroupIndex);
                liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
                if (currentLiveChannelIndex >= 0 && currentLiveChannelIndex < mChannelGridView.getAdapter().getItemCount()) {
                    RecyclerView.ViewHolder holder = mChannelGridView.findViewHolderForAdapterPosition(currentLiveChannelIndex);
                    if (holder != null) holder.itemView.requestFocus();
                }
                tvLeftChannelListLayout.setVisibility(View.VISIBLE);
                tvLeftChannelListLayout.setAlpha(0.0f);
                tvLeftChannelListLayout.setTranslationX(-tvLeftChannelListLayout.getWidth() / 2);
                tvLeftChannelListLayout.animate().translationX(0).alpha(1.0f).setDuration(250)
                        .setInterpolator(new DecelerateInterpolator()).setListener(null);
                mHandler.removeCallbacks(mHideChannelListRun);
                mHandler.postDelayed(mHideChannelListRun, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
                mHandler.postDelayed(mUpdateLayout, 255);
            }
        }
    };

    private final Runnable mFocusAndShowSettingGroup = new Runnable() {
        @Override
        public void run() {
            if (mSettingGroupView.isScrolling() || mSettingItemView.isScrolling() || mSettingGroupView.isComputingLayout() || mSettingItemView.isComputingLayout()) {
                mHandler.postDelayed(this, 100);
            } else {
                RecyclerView.ViewHolder holder = mSettingGroupView.findViewHolderForAdapterPosition(0);
                if (holder != null) holder.itemView.requestFocus();
                tvRightSettingLayout.setVisibility(View.VISIBLE);
                tvRightSettingLayout.setAlpha(0.0f);
                tvRightSettingLayout.setTranslationX(tvRightSettingLayout.getWidth() / 2);
                tvRightSettingLayout.animate().translationX(0).alpha(1.0f).setDuration(250)
                        .setInterpolator(new DecelerateInterpolator()).setListener(null);
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, LiveConstants.AUTO_HIDE_SETTINGS_MS);
                mHandler.postDelayed(mUpdateLayout, 255);
            }
        }
    };

    // ---------- 1.9 其他变量 ----------
    boolean mIsDragging;
    boolean isVOD = false;
    boolean PiPON = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0) == 2;
    private long mExitTime = 0;
    private boolean onStopCalled;

    // ==================== 区域2: 工具方法 ====================
    // ============================================================

    // ---------- 2.1 播放请求头 ----------
    private HashMap<String, String> setPlayHeaders(String url) {
        HashMap<String, String> header = new HashMap<>();
        try {
            boolean matchTo = false;
            JSONArray livePlayHeaders = new JSONArray(ApiConfig.get().getLivePlayHeaders().toString());
            for (int i = 0; i < livePlayHeaders.length(); i++) {
                JSONObject headerObj = livePlayHeaders.getJSONObject(i);
                JSONArray flags = headerObj.getJSONArray("flag");
                JSONObject headerData = headerObj.getJSONObject("header");
                for (int j = 0; j < flags.length(); j++) {
                    String flag = flags.getString(j);
                    if (url.contains(flag)) {
                        matchTo = true;
                        break;
                    }
                }
                if (matchTo) {
                    Iterator<String> keys = headerData.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        String value = headerData.getString(key);
                        header.put(key, value);
                    }
                    break;
                }
            }
            if (!matchTo) {
                header.put("User-Agent", LiveConstants.DEFAULT_USER_AGENT);
            }
        } catch (Exception e) {
            header.put("User-Agent", LiveConstants.DEFAULT_USER_AGENT);
        }
        return header;
    }

    // ---------- 2.2 时移URL构建 ----------
    private String[] buildShiyiUrls(String originalUrl, String shiyiTime) {
        String[] result = new String[2];
        String separator = originalUrl.contains("?") ? "&" : "?";
        result[1] = originalUrl + separator + LiveConstants.PLAYSEEK_PARAM + shiyiTime;
        if (originalUrl.contains(LiveConstants.PLTV_FLAG)) {
            String tvodUrl = originalUrl.replace(LiveConstants.PLTV_FLAG, LiveConstants.TVOD_FLAG);
            result[0] = tvodUrl + (tvodUrl.contains("?") ? "&" : "?") + LiveConstants.PLAYSEEK_PARAM + shiyiTime;
        } else {
            result[0] = result[1];
        }
        return result;
    }

    private String[] buildShiyiTimes(String targetDate, String startTime, String endTime) {
        String startDateTime = targetDate + startTime.replace(":", "") + "30";
        String endDateTime;
        if (endTime.compareTo(startTime) < 0) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
                Date date = sdf.parse(targetDate);
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                cal.add(Calendar.DAY_OF_MONTH, 1);
                String nextDay = sdf.format(cal.getTime());
                endDateTime = nextDay + endTime.replace(":", "") + "30";
            } catch (Exception e) {
                endDateTime = targetDate + endTime.replace(":", "") + "30";
            }
        } else {
            endDateTime = targetDate + endTime.replace(":", "") + "30";
        }
        return new String[]{startDateTime, endDateTime};
    }

    private boolean isValidShiyiTime(String startTime, String endTime) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMDHMS);
            return sdf.parse(startTime).getTime() < sdf.parse(endTime).getTime();
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 区域3: EPG获取和预加载 ====================
    // ============================================================

    /**
     * EPG获取统一入口（使用 EpgCacheHelper）
     */
    public void getEpg(Date date) {
        if (currentLiveChannelItem == null || currentLiveChannelItem.getChannelName() == null) return;
        
        final String channelName = currentLiveChannelItem.getChannelName();
        epgCacheHelper.requestEpg(channelName, date, new EpgCacheHelper.EpgCallback() {
            @Override
            public void onSuccess(String channelName, Date date, ArrayList<Epginfo> epgList) {
                // 确保还是当前频道
                if (currentLiveChannelItem != null && channelName.equals(currentLiveChannelItem.getChannelName())) {
                    showEpg(date, epgList);
                    showBottomEpg();
                }
            }
            
            @Override
            public void onFailure(String channelName, Date date, Exception e) {
                // 静默失败
            }
        });
    }

    // ==================== 区域4: 生命周期方法 ====================
    // ============================================================

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live_play;
    }

    @Override
    protected void init() {
        hideSystemUI(false);

        epgStringAddress = Hawk.get(HawkConfig.EPG_URL, "");
        if (StringUtils.isBlank(epgStringAddress)) {
            epgStringAddress = LiveConstants.DEFAULT_EPG_URL;
        }
        
        // 初始化 EPG 缓存管理器
        epgCacheHelper = new EpgCacheHelper(this, epgStringAddress);

        EventBus.getDefault().register(this);
        setLoadSir(findViewById(R.id.live_root));
        mVideoView = findViewById(R.id.mVideoView);

        tvSelectedChannel = findViewById(R.id.tv_selected_channel);
        tv_size = findViewById(R.id.tv_size);
        tv_source = findViewById(R.id.tv_source);
        tv_sys_time = findViewById(R.id.tv_sys_time);

        llSeekBar = findViewById(R.id.ll_seekbar);
        mCurrentTime = findViewById(R.id.curr_time);
        mSeekBar = findViewById(R.id.seekBar);
        mTotalTime = findViewById(R.id.total_time);

        mBack = findViewById(R.id.tvBackButton);
        mBack.setVisibility(View.INVISIBLE);

        tvBottomLayout = findViewById(R.id.tvBottomLayout);
        tvBottomLayout.setVisibility(View.INVISIBLE);
        tv_channelname = findViewById(R.id.tv_channel_name);
        tv_channelnum = findViewById(R.id.tv_channel_number);
        tv_logo = findViewById(R.id.tv_logo);
        tv_curr_time = findViewById(R.id.tv_current_program_time);
        tv_curr_name = findViewById(R.id.tv_current_program_name);
        tv_next_time = findViewById(R.id.tv_next_program_time);
        tv_next_name = findViewById(R.id.tv_next_program_name);

        mGroupEPG = findViewById(R.id.mGroupEPG);
        mDivRight = findViewById(R.id.mDivRight);
        mDivLeft = findViewById(R.id.mDivLeft);
        mEpgDateGridView = findViewById(R.id.mEpgDateGridView);
        mEpgInfoGridView = findViewById(R.id.mEpgInfoGridView);

        tvLeftChannelListLayout = findViewById(R.id.tvLeftChannelListLayout);
        mGroupGridView = findViewById(R.id.mGroupGridView);
        mChannelGridView = findViewById(R.id.mChannelGridView);

        tvRightSettingLayout = findViewById(R.id.tvRightSettingLayout);
        mSettingGroupView = findViewById(R.id.mSettingGroupView);
        mSettingItemView = findViewById(R.id.mSettingItemView);

        tvTime = findViewById(R.id.tvTime);
        tvNetSpeed = findViewById(R.id.tvNetSpeed);

        initEpgDateView();
        initEpgListView();
        initVideoView();
        initChannelGroupView();
        initLiveChannelView();
        initSettingGroupView();
        initSettingItemView();
        initLiveChannelList();
        initLiveSettingGroupList();

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                mHandler.removeCallbacks(mHideChannelInfoRun);
                mHandler.postDelayed(mHideChannelInfoRun, LiveConstants.AUTO_HIDE_CHANNEL_INFO_MS);
                long duration = mVideoView.getDuration();
                long newPosition = (duration * progress) / seekBar.getMax();
                if (mCurrentTime != null)
                    mCurrentTime.setText(stringForTimeVod((int) newPosition));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { mIsDragging = true; }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mIsDragging = false;
                long duration = mVideoView.getDuration();
                long newPosition = (duration * seekBar.getProgress()) / seekBar.getMax();
                mVideoView.seekTo((int) newPosition);
            }
        });
        mSeekBar.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    mIsDragging = true;
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                mIsDragging = false;
                long duration = mVideoView.getDuration();
                long newPosition = (duration * mSeekBar.getProgress()) / mSeekBar.getMax();
                mVideoView.seekTo((int) newPosition);
            }
            return false;
        });
        mBack.setOnClickListener(v -> finish());
    }

    @Override
    public void onUserLeaveHint() {
        if (supportsPiPMode() && PiPON) {
            mHandler.post(mHideChannelListRun);
            mHandler.post(mHideChannelInfoRun);
            mHandler.post(mHideSettingLayoutRun);
            enterPictureInPictureMode();
        }
    }

    @Override
    public void onBackPressed() {
        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
        } else if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        } else if (tvBottomLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelInfoRun);
            mHandler.post(mHideChannelInfoRun);
        } else {
            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
            mHandler.removeCallbacks(mUpdateNetSpeedRun);
            mHandler.removeCallbacks(mUpdateTimeRun);
            mHandler.removeCallbacks(tv_sys_timeRunnable);
            exit();
        }
    }

    private void exit() {
        if (System.currentTimeMillis() - mExitTime < LiveConstants.EXIT_CONFIRM_DELAY_MS) {
            super.onBackPressed();
        } else {
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, getString(R.string.hm_exit_live), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                showSettingGroup();
            } else if (!isListOrSettingLayoutVisible()) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false)) playNext();
                        else playPrevious();
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false)) playPrevious();
                        else playNext();
                        break;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (!isVOD) showSettingGroup();
                        else showChannelInfo();
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (!isVOD) playNextSource();
                        else showChannelInfo();
                        break;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        showChannelList();
                        break;
                    default:
                        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                            keyCode -= KeyEvent.KEYCODE_0;
                        } else if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
                            keyCode -= KeyEvent.KEYCODE_NUMPAD_0;
                        } else {
                            break;
                        }
                        numericKeyDown(keyCode);
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mVideoView != null) mVideoView.resume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        onStopCalled = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null) {
            if (supportsPiPMode()) {
                if (isInPictureInPictureMode()) mVideoView.resume();
                else mVideoView.pause();
            } else {
                mVideoView.pause();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (supportsPiPMode() && !isInPictureInPictureMode() && onStopCalled) {
            mVideoView.release();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        
        if (epgCacheHelper != null) {
            epgCacheHelper.destroy();
        }
        
        hsEpg.clear();
    }

    // ==================== 区域5: UI显示方法 ====================
    // ============================================================

    // ---------- 5.1 频道列表显示/隐藏 ----------
    private void showChannelList() {
        mBack.setVisibility(View.INVISIBLE);
        if (tvBottomLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelInfoRun);
            mHandler.post(mHideChannelInfoRun);
        } else if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        } else if (tvLeftChannelListLayout.getVisibility() == View.INVISIBLE && tvRightSettingLayout.getVisibility() == View.INVISIBLE) {
            liveChannelItemAdapter.setNewData(getLiveChannels(currentChannelGroupIndex));
            if (currentLiveChannelIndex > -1 && currentLiveChannelIndex < mChannelGridView.getAdapter().getItemCount())
                mChannelGridView.scrollToPosition(currentLiveChannelIndex);
            mChannelGridView.setSelection(currentLiveChannelIndex);
            mGroupGridView.scrollToPosition(currentChannelGroupIndex);
            mGroupGridView.setSelection(currentChannelGroupIndex);
            mHandler.postDelayed(mFocusCurrentChannelAndShowChannelList, 200);
            mHandler.post(tv_sys_timeRunnable);
        } else {
            mBack.setVisibility(View.INVISIBLE);
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
            mHandler.removeCallbacks(tv_sys_timeRunnable);
        }
    }

    public void divLoadEpgR(View view) {
        mGroupGridView.setVisibility(View.GONE);
        mEpgInfoGridView.setVisibility(View.VISIBLE);
        mGroupEPG.setVisibility(View.VISIBLE);
        mDivLeft.setVisibility(View.VISIBLE);
        mDivRight.setVisibility(View.GONE);
        tvLeftChannelListLayout.setVisibility(View.INVISIBLE);
        showChannelList();
    }

    public void divLoadEpgL(View view) {
        mGroupGridView.setVisibility(View.VISIBLE);
        mEpgInfoGridView.setVisibility(View.GONE);
        mGroupEPG.setVisibility(View.GONE);
        mDivLeft.setVisibility(View.GONE);
        mDivRight.setVisibility(View.VISIBLE);
        tvLeftChannelListLayout.setVisibility(View.INVISIBLE);
        showChannelList();
    }

    // ---------- 5.2 频道信息显示/隐藏 ----------
    private void showChannelInfo() {
        if (supportsTouch()) mBack.setVisibility(View.VISIBLE);
        if (tvBottomLayout.getVisibility() == View.GONE || tvBottomLayout.getVisibility() == View.INVISIBLE) {
            tvBottomLayout.setVisibility(View.VISIBLE);
            tvBottomLayout.setTranslationY(tvBottomLayout.getHeight() / 2);
            tvBottomLayout.setAlpha(0.0f);
            tvBottomLayout.animate().alpha(1.0f).setDuration(250).setInterpolator(new DecelerateInterpolator())
                    .translationY(0).setListener(null);
        }
        mHandler.removeCallbacks(mHideChannelInfoRun);
        mHandler.postDelayed(mHideChannelInfoRun, LiveConstants.AUTO_HIDE_CHANNEL_INFO_MS);
        mHandler.postDelayed(mUpdateLayout, 255);
    }

    private void toggleChannelInfo() {
        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
        } else if (tvRightSettingLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        } else if (tvBottomLayout.getVisibility() == View.INVISIBLE) {
            showChannelInfo();
        } else {
            mBack.setVisibility(View.INVISIBLE);
            mHandler.removeCallbacks(mHideChannelInfoRun);
            mHandler.post(mHideChannelInfoRun);
            mHandler.post(mUpdateLayout);
        }
    }

    // ---------- 5.3 EPG显示 ----------
    private void showEpg(Date date, ArrayList<Epginfo> arrayList) {
        if (arrayList != null && arrayList.size() > 0) {
            epgdata = arrayList;
            if (currentLiveChannelItem != null) {
                epgListAdapter.CanBack(currentLiveChannelItem.getinclude_back());
            }
            epgListAdapter.setNewData(epgdata);
            int i = -1;
            for (int size = epgdata.size() - 1; size >= 0; size--) {
                if (new Date().compareTo(epgdata.get(size).startdateTime) >= 0) {
                    i = size;
                    break;
                }
            }
            if (i >= 0 && new Date().compareTo(epgdata.get(i).enddateTime) <= 0) {
                mEpgInfoGridView.setSelectedPosition(i);
                mEpgInfoGridView.setSelection(i);
                epgListAdapter.setSelectedEpgIndex(i);
                int finalI = i;
                mEpgInfoGridView.post(() -> mEpgInfoGridView.smoothScrollToPosition(finalI));
            }
        } else {
            Epginfo epgbcinfo = new Epginfo(date, LiveConstants.NO_PROGRAM, date, LiveConstants.DEFAULT_START_TIME, LiveConstants.DEFAULT_END_TIME, 0);
            arrayList.add(epgbcinfo);
            epgdata = arrayList;
            epgListAdapter.setNewData(epgdata);
        }
    }

    // ---------- 5.4 底部信息栏 ----------
    private void showBottomEpg() {
        if (isShiyiMode) return;
        if (currentLiveChannelItem == null || currentLiveChannelItem.getChannelName() == null) {
            tv_curr_name.setText(LiveConstants.NO_PROGRAM);
            tv_next_name.setText("");
            return;
        }
        
        showChannelInfo();
        String channelName = currentLiveChannelItem.getChannelName();
        
        // 使用已缓存的 epgdata（由 showEpg 填充）
        if (epgdata != null && !epgdata.isEmpty()) {
            String[] epgInfo = EpgUtil.getEpgInfo(channelName);
            getTvLogo(channelName, epgInfo == null ? null : epgInfo[0]);
            
            Date now = new Date();
            for (int size = epgdata.size() - 1; size >= 0; size--) {
                Epginfo epg = epgdata.get(size);
                if (now.after(epg.startdateTime) && now.before(epg.enddateTime)) {
                    tv_curr_time.setText(epg.start + " - " + epg.end);
                    tv_curr_name.setText(epg.title);
                    if (size != epgdata.size() - 1) {
                        Epginfo next = epgdata.get(size + 1);
                        tv_next_time.setText(next.start + " - " + next.end);
                        tv_next_name.setText(next.title);
                    } else {
                        tv_next_time.setText(LiveConstants.DEFAULT_START_TIME + " - " + LiveConstants.DEFAULT_END_TIME);
                        tv_next_name.setText(LiveConstants.NO_INFO);
                    }
                    break;
                }
            }
            if (currentLiveChannelItem != null) {
                epgListAdapter.CanBack(currentLiveChannelItem.getinclude_back());
            }
            epgListAdapter.setNewData(epgdata);
            String savedEpgKey = channelName + "_" + epgDateAdapter.getItem(epgDateAdapter.getSelectedIndex()).getDatePresented();
            hsEpg.put(savedEpgKey, epgdata);
        } else {
            tv_curr_name.setText(LiveConstants.NO_PROGRAM);
            tv_next_name.setText("");
        }
    }

    private void getTvLogo(String channelName, String logoUrl) {
        RequestOptions options = new RequestOptions();
        options.diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).placeholder(R.drawable.img_logo_placeholder);
        Glide.with(App.getInstance()).load(logoUrl).apply(options).into(tv_logo);
    }

    // ==================== 区域6: 播放控制方法 ====================
    // ============================================================

    // ---------- 6.1 频道播放 ----------
    private boolean playChannel(int channelGroupIndex, int liveChannelIndex, boolean changeSource) {
        if (channelGroupIndex >= liveChannelGroupList.size()) {
            Toast.makeText(App.getInstance(), "分组不存在", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        List<LiveChannelItem> channels = getLiveChannels(channelGroupIndex);
        if (channels == null || channels.isEmpty() || liveChannelIndex < 0 || liveChannelIndex >= channels.size()) {
            Toast.makeText(App.getInstance(), "频道不存在", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if ((channelGroupIndex == currentChannelGroupIndex && liveChannelIndex == currentLiveChannelIndex && !changeSource)
                || (changeSource && currentLiveChannelItem != null && currentLiveChannelItem.getSourceNum() == 1)) {
            showChannelInfo();
            return true;
        }
        
        if (mVideoView == null) return true;
        mVideoView.release();
        
        if (!changeSource) {
            currentChannelGroupIndex = channelGroupIndex;
            currentLiveChannelIndex = liveChannelIndex;
            currentLiveChannelItem = channels.get(currentLiveChannelIndex);
            Hawk.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem.getChannelName());
            HawkUtils.setLastLiveChannelGroup(liveChannelGroupList.get(currentChannelGroupIndex).getGroupName());
            livePlayerManager.getLiveChannelPlayer(mVideoView, currentLiveChannelItem.getChannelName());
        }
        currentLiveChannelItem.setinclude_back(currentLiveChannelItem.getUrl().indexOf(LiveConstants.PLTV_FLAG + "8888") != -1);

        mHandler.post(tv_sys_timeRunnable);

        tv_channelname.setText(currentLiveChannelItem.getChannelName());
        tv_channelnum.setText("" + currentLiveChannelItem.getChannelNum());
        if (currentLiveChannelItem == null || currentLiveChannelItem.getSourceNum() <= 0) {
            tv_source.setText("1/1");
        } else {
            tv_source.setText("线路 " + (currentLiveChannelItem.getSourceIndex() + 1) + "/" + currentLiveChannelItem.getSourceNum());
        }

        getEpg(new Date());
        showBottomEpg();
        
        // 预加载当前频道的所有日期
        if (epgCacheHelper != null && currentLiveChannelItem != null) {
            epgCacheHelper.preloadCurrentChannel(currentLiveChannelItem.getChannelName());
        }
        
        mVideoView.setUrl(currentLiveChannelItem.getUrl(), setPlayHeaders(currentLiveChannelItem.getUrl()));
        showChannelInfo();
        mVideoView.start();
        return true;
    }

    private boolean replayChannel() {
        if (mVideoView == null || currentLiveChannelItem == null) return true;
        mVideoView.release();
        currentLiveChannelItem = getLiveChannels(currentChannelGroupIndex).get(currentLiveChannelIndex);
        Hawk.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem.getChannelName());
        HawkUtils.setLastLiveChannelGroup(liveChannelGroupList.get(currentChannelGroupIndex).getGroupName());
        livePlayerManager.getLiveChannelPlayer(mVideoView, currentLiveChannelItem.getChannelName());
        currentLiveChannelItem.setinclude_back(currentLiveChannelItem.getUrl().indexOf(LiveConstants.PLTV_FLAG + "8888") != -1);
        mHandler.post(tv_sys_timeRunnable);
        tv_channelname.setText(currentLiveChannelItem.getChannelName());
        tv_channelnum.setText("" + currentLiveChannelItem.getChannelNum());
        if (currentLiveChannelItem == null || currentLiveChannelItem.getSourceNum() <= 0) {
            tv_source.setText("1/1");
        } else {
            tv_source.setText("线路 " + (currentLiveChannelItem.getSourceIndex() + 1) + "/" + currentLiveChannelItem.getSourceNum());
        }

        getEpg(new Date());
        showBottomEpg();
        mVideoView.setUrl(currentLiveChannelItem.getUrl(), setPlayHeaders(currentLiveChannelItem.getUrl()));
        showChannelInfo();
        mVideoView.start();
        return true;
    }

    // ---------- 6.2 换台/换源 ----------
    private void playNext() {
        if (!isCurrentLiveChannelValid()) {
            Toast.makeText(App.getInstance(), "暂无直播源", Toast.LENGTH_SHORT).show();
            return;
        }
        Integer[] groupChannelIndex = getNextChannel(1);
        if (groupChannelIndex[0] >= 0 && groupChannelIndex[1] >= 0) {
            playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
        } else {
            Toast.makeText(App.getInstance(), "无更多频道", Toast.LENGTH_SHORT).show();
        }
    }

    private void playPrevious() {
        if (!isCurrentLiveChannelValid()) {
            Toast.makeText(App.getInstance(), "暂无直播源", Toast.LENGTH_SHORT).show();
            return;
        }
        Integer[] groupChannelIndex = getNextChannel(-1);
        if (groupChannelIndex[0] >= 0 && groupChannelIndex[1] >= 0) {
            playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
        } else {
            Toast.makeText(App.getInstance(), "无更多频道", Toast.LENGTH_SHORT).show();
        }
    }

    public void playPreSource() {
        if (!isCurrentLiveChannelValid()) {
            Toast.makeText(App.getInstance(), "暂无直播源", Toast.LENGTH_SHORT).show();
            return;
        }
        currentLiveChannelItem.preSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }

    public void playNextSource() {
        if (mVideoView == null || !isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.nextSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }

    // ==================== 区域7: 设置面板方法 ====================
    // ============================================================

    // ---------- 7.1 设置面板显示/隐藏 ----------
    private void showSettingGroup() {
        mBack.setVisibility(View.INVISIBLE);
        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
        } else if (tvBottomLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelInfoRun);
            mHandler.post(mHideChannelInfoRun);
        } else if (tvRightSettingLayout.getVisibility() == View.INVISIBLE) {
            if (!isCurrentLiveChannelValid()) {
                Toast.makeText(App.getInstance(), "当前无直播源，部分功能不可用", Toast.LENGTH_SHORT).show();
            }
            loadCurrentSourceList();
            liveSettingGroupAdapter.setNewData(liveSettingGroupList);
            selectSettingGroup(0, false);
            mSettingGroupView.scrollToPosition(0);
            if (currentLiveChannelItem != null) {
                mSettingItemView.scrollToPosition(currentLiveChannelItem.getSourceIndex());
            }
            mHandler.postDelayed(mFocusAndShowSettingGroup, 200);
        } else {
            mBack.setVisibility(View.INVISIBLE);
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        }
    }

    // ---------- 7.2 设置项点击处理 ----------
    private void clickSettingItem(int position) {
        int settingGroupIndex = liveSettingGroupAdapter.getSelectedGroupIndex();
        
        if (settingGroupIndex == 0 && !isCurrentLiveChannelValid()) {
            Toast.makeText(App.getInstance(), "当前无直播源，无法切换线路", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (settingGroupIndex < 4) {
            if (position == liveSettingItemAdapter.getSelectedItemIndex()) return;
            liveSettingItemAdapter.selectItem(position, true, true);
        }
        switch (settingGroupIndex) {
            case 0:
                if (currentLiveChannelItem != null) {
                    currentLiveChannelItem.setSourceIndex(position);
                    playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
                }
                break;
            case 1:
                try {
                    livePlayerManager.changeLivePlayerScale(mVideoView, position,
                            currentLiveChannelItem != null ? currentLiveChannelItem.getChannelName() : "");
                    Toast.makeText(App.getInstance(), currentLiveChannelItem != null ? "画面比例已应用" : "画面比例已设置", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(App.getInstance(), "设置失败", Toast.LENGTH_SHORT).show();
                }
                break;
            case 2:
                try {
                    if (currentLiveChannelItem != null) mVideoView.release();
                    livePlayerManager.changeLivePlayerType(mVideoView, position,
                            currentLiveChannelItem != null ? currentLiveChannelItem.getChannelName() : "");
                    if (currentLiveChannelItem != null) {
                        mVideoView.setUrl(currentLiveChannelItem.getUrl(), setPlayHeaders(currentLiveChannelItem.getUrl()));
                        mVideoView.start();
                    }
                    Toast.makeText(App.getInstance(), currentLiveChannelItem != null ? "解码方式已应用" : "解码方式已设置", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(App.getInstance(), "设置失败", Toast.LENGTH_SHORT).show();
                }
                break;
            case 3:
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, position);
                break;
            case 4:
                boolean select = false;
                switch (position) {
                    case 0:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_TIME, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_TIME, select);
                        showTime();
                        break;
                    case 1:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_NET_SPEED, select);
                        showNetSpeed();
                        break;
                    case 2:
                        select = !Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false);
                        Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE, select);
                        break;
                    case 3:
                        select = !Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false);
                        Hawk.put(HawkConfig.LIVE_CROSS_GROUP, select);
                        break;
                    case 4:
                        select = !Hawk.get(HawkConfig.LIVE_SKIP_PASSWORD, false);
                        Hawk.put(HawkConfig.LIVE_SKIP_PASSWORD, select);
                        break;
                    default:
                        select = false;
                        break;
                }
                liveSettingItemAdapter.selectItem(position, select, false);
                break;
            case 5:
                if (position == 0) {
                    ArrayList<String> liveHistory = Hawk.get(HawkConfig.LIVE_HISTORY, new ArrayList<>());
                    if (liveHistory.isEmpty()) return;
                    String current = Hawk.get(HawkConfig.LIVE_URL, "");
                    int idx = liveHistory.contains(current) ? liveHistory.indexOf(current) : 0;
                    ApiHistoryDialog dialog = new ApiHistoryDialog(LivePlayActivity.this);
                    dialog.setTip(getString(R.string.dia_history_live));
                    dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                        @Override
                        public void click(String liveURL) {
                            Hawk.put(HawkConfig.LIVE_URL, liveURL);
                            liveChannelGroupList.clear();
                            try {
                                liveURL = Base64.encodeToString(liveURL.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                                loadProxyLives("http://127.0.0.1:9978/proxy?do=live&type=txt&ext=" + liveURL);
                            } catch (Throwable th) {
                                th.printStackTrace();
                            }
                            dialog.dismiss();
                        }
                        @Override
                        public void del(String value, ArrayList<String> data) {
                            Hawk.put(HawkConfig.LIVE_HISTORY, data);
                        }
                    }, liveHistory, idx);
                    dialog.show();
                }
                break;
            case 6:
                if (position == 0) finish();
                break;
        }
        mHandler.removeCallbacks(mHideSettingLayoutRun);
        mHandler.postDelayed(mHideSettingLayoutRun, LiveConstants.AUTO_HIDE_SETTINGS_MS);
    }

    // ==================== 区域8: 初始化方法 ====================
    // ============================================================

    // ---------- 8.1 EPG列表初始化 ----------
    private void initEpgListView() {
        mEpgInfoGridView.setHasFixedSize(true);
        mEpgInfoGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        epgListAdapter = new LiveEpgAdapter();
        mEpgInfoGridView.setAdapter(epgListAdapter);
        mEpgInfoGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                mHandler.removeCallbacks(mHideChannelListRun);
                mHandler.postDelayed(mHideChannelListRun, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            }
        });
        
        mEpgInfoGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                epgListAdapter.setFocusedEpgIndex(-1);
            }
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                mHandler.removeCallbacks(mHideChannelListRun);
                mHandler.postDelayed(mHideChannelListRun, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
                epgListAdapter.setFocusedEpgIndex(position);
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (currentLiveChannelItem == null) return;
                Date date = epgDateAdapter.getSelectedIndex() < 0 ? new Date() :
                        epgDateAdapter.getData().get(epgDateAdapter.getSelectedIndex()).getDateParamVal();
                SimpleDateFormat dateFormat = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
                Epginfo selectedData = epgListAdapter.getItem(position);
                String targetDate = dateFormat.format(date);
                String[] shiyiTimes = buildShiyiTimes(targetDate, selectedData.originStart, selectedData.originEnd);
                String shiyiStartdate = shiyiTimes[0];
                String shiyiEnddate = shiyiTimes[1];
                Date now = new Date();
                if (now.compareTo(selectedData.startdateTime) < 0) {
                    Toast.makeText(LivePlayActivity.this, "未到播放时间", Toast.LENGTH_SHORT).show();
                    return;
                }
                epgListAdapter.setSelectedEpgIndex(position);
                if (now.compareTo(selectedData.startdateTime) >= 0 && now.compareTo(selectedData.enddateTime) <= 0) {
                    mVideoView.release();
                    isShiyiMode = false;
                    mVideoView.setUrl(currentLiveChannelItem.getUrl(), setPlayHeaders(currentLiveChannelItem.getUrl()));
                    mVideoView.start();
                    epgListAdapter.setShiyiSelection(-1, false, timeFormat.format(date));
                } else {
                    if (!isValidShiyiTime(shiyiStartdate, shiyiEnddate)) {
                        Toast.makeText(LivePlayActivity.this, "无效的回放时间", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mVideoView.release();
                    shiyi_time = shiyiStartdate + "-" + shiyiEnddate;
                    isShiyiMode = true;
                    String[] shiyiUrls = buildShiyiUrls(currentLiveChannelItem.getUrl(), shiyi_time);
                    String primaryUrl = shiyiUrls[0];
                    mVideoView.setUrl(primaryUrl, setPlayHeaders(primaryUrl));
                    mVideoView.start();
                    epgListAdapter.setShiyiSelection(position, true, timeFormat.format(date));
                    epgListAdapter.notifyDataSetChanged();
                    mEpgInfoGridView.setSelectedPosition(position);
                }
            }
        });

        epgListAdapter.setOnItemClickListener((adapter, view, position) -> {
            if (currentLiveChannelItem == null) return;
            Date date = epgDateAdapter.getSelectedIndex() < 0 ? new Date() :
                    epgDateAdapter.getData().get(epgDateAdapter.getSelectedIndex()).getDateParamVal();
            SimpleDateFormat dateFormat = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
            Epginfo selectedData = epgListAdapter.getItem(position);
            String targetDate = dateFormat.format(date);
            String[] shiyiTimes = buildShiyiTimes(targetDate, selectedData.originStart, selectedData.originEnd);
            String shiyiStartdate = shiyiTimes[0];
            String shiyiEnddate = shiyiTimes[1];
            Date now = new Date();
            if (now.compareTo(selectedData.startdateTime) < 0) {
                Toast.makeText(LivePlayActivity.this, "未到播放时间", Toast.LENGTH_SHORT).show();
                return;
            }
            epgListAdapter.setSelectedEpgIndex(position);
            if (now.compareTo(selectedData.startdateTime) >= 0 && now.compareTo(selectedData.enddateTime) <= 0) {
                mVideoView.release();
                isShiyiMode = false;
                mVideoView.setUrl(currentLiveChannelItem.getUrl(), setPlayHeaders(currentLiveChannelItem.getUrl()));
                mVideoView.start();
                epgListAdapter.setShiyiSelection(-1, false, timeFormat.format(date));
            } else {
                if (!isValidShiyiTime(shiyiStartdate, shiyiEnddate)) {
                    Toast.makeText(LivePlayActivity.this, "无效的回放时间", Toast.LENGTH_SHORT).show();
                    return;
                }
                mVideoView.release();
                shiyi_time = shiyiStartdate + "-" + shiyiEnddate;
                isShiyiMode = true;
                String[] shiyiUrls = buildShiyiUrls(currentLiveChannelItem.getUrl(), shiyi_time);
                String primaryUrl = shiyiUrls[0];
                mVideoView.setUrl(primaryUrl, setPlayHeaders(primaryUrl));
                mVideoView.start();
                epgListAdapter.setShiyiSelection(position, true, timeFormat.format(date));
                epgListAdapter.notifyDataSetChanged();
                mEpgInfoGridView.setSelectedPosition(position);
            }
        });
    }

    private void initEpgDateView() {
        mEpgDateGridView.setHasFixedSize(true);
        mEpgDateGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        epgDateAdapter = new LiveEpgDateAdapter();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        SimpleDateFormat datePresentFormat = new SimpleDateFormat("EEEE", Locale.SIMPLIFIED_CHINESE);
        calendar.add(Calendar.DAY_OF_MONTH, -LiveConstants.PRELOAD_DAYS_BEFORE);
        for (int i = 0; i < LiveConstants.PRELOAD_DAYS_BEFORE + LiveConstants.PRELOAD_DAYS_AFTER + 1; i++) {
            Date dateIns = calendar.getTime();
            LiveEpgDate epgDate = new LiveEpgDate();
            epgDate.setIndex(i);
            if (i == 5) epgDate.setDatePresented("昨天");
            else if (i == 6) epgDate.setDatePresented("今天");
            else if (i == 7) epgDate.setDatePresented("明天");
            else if (i == 8) epgDate.setDatePresented("后天");
            else epgDate.setDatePresented(datePresentFormat.format(dateIns));
            epgDate.setDateParamVal(dateIns);
            epgDateAdapter.addData(epgDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        mEpgDateGridView.setAdapter(epgDateAdapter);
        mEpgDateGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                mHandler.removeCallbacks(mHideChannelListRun);
                mHandler.postDelayed(mHideChannelListRun, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            }
        });
        mEpgDateGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                epgDateAdapter.setFocusedIndex(-1);
            }
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                mHandler.removeCallbacks(mHideChannelListRun);
                mHandler.postDelayed(mHideChannelListRun, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
                epgDateAdapter.setFocusedIndex(position);
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                mHandler.removeCallbacks(mHideChannelListRun);
                mHandler.postDelayed(mHideChannelListRun, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
                epgDateAdapter.setSelectedIndex(position);
                getEpg(epgDateAdapter.getData().get(position).getDateParamVal());
            }
        });
        epgDateAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.postDelayed(mHideChannelListRun, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            epgDateAdapter.setSelectedIndex(position);
            getEpg(epgDateAdapter.getData().get(position).getDateParamVal());
        });
        epgDateAdapter.setSelectedIndex(1);
    }

    // ---------- 8.2 频道列表初始化 ----------
    private void initChannelGroupView() {
        mGroupGridView.setHasFixedSize(true);
        mGroupGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveChannelGroupAdapter = new LiveChannelGroupAdapter();
        mGroupGridView.setAdapter(liveChannelGroupAdapter);
        mGroupGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                mHandler.removeCallbacks(mHideChannelListRun);
                mHandler.postDelayed(mHideChannelListRun, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            }
        });
        mGroupGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                selectChannelGroup(position, true, -1);
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (isNeedInputPassword(position)) showPasswordDialog(position, -1);
            }
        });
        liveChannelGroupAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            selectChannelGroup(position, false, -1);
        });
    }

    private void initLiveChannelView() {
        mChannelGridView.setHasFixedSize(true);
        mChannelGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveChannelItemAdapter = new LiveChannelItemAdapter();
        mChannelGridView.setAdapter(liveChannelItemAdapter);
        mChannelGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                mHandler.removeCallbacks(mHideChannelListRun);
                mHandler.postDelayed(mHideChannelListRun, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            }
        });
        mChannelGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (position < 0) return;
                liveChannelGroupAdapter.setFocusedGroupIndex(-1);
                liveChannelItemAdapter.setFocusedChannelIndex(position);
                mHandler.removeCallbacks(mHideChannelListRun);
                mHandler.postDelayed(mHideChannelListRun, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                clickLiveChannel(position);
            }
        });
        liveChannelItemAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            clickLiveChannel(position);
        });
    }

    private void selectChannelGroup(int groupIndex, boolean focus, int liveChannelIndex) {
        if (groupIndex >= liveChannelGroupList.size()) return;
        if (focus) {
            liveChannelGroupAdapter.setFocusedGroupIndex(groupIndex);
            liveChannelItemAdapter.setFocusedChannelIndex(-1);
        }
        List<LiveChannelItem> channels = getLiveChannels(groupIndex);
        if (channels.isEmpty()) {
            Toast.makeText(App.getInstance(), "该分组暂无直播源", Toast.LENGTH_SHORT).show();
            liveChannelItemAdapter.setNewData(new ArrayList<>());
            return;
        }
        if ((groupIndex > -1 && groupIndex != liveChannelGroupAdapter.getSelectedGroupIndex()) || isNeedInputPassword(groupIndex)) {
            liveChannelGroupAdapter.setSelectedGroupIndex(groupIndex);
            if (isNeedInputPassword(groupIndex)) {
                showPasswordDialog(groupIndex, liveChannelIndex);
                return;
            }
            loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
        }
        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.postDelayed(mHideChannelListRun, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
        }
    }

    private void clickLiveChannel(int position) {
        liveChannelItemAdapter.setSelectedChannelIndex(position);
        epgDateAdapter.setSelectedIndex(6);
        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
        }
        playChannel(liveChannelGroupAdapter.getSelectedGroupIndex(), position, false);
    }

    // ---------- 8.3 设置面板初始化 ----------
    private void initSettingGroupView() {
        mSettingGroupView.setHasFixedSize(true);
        mSettingGroupView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveSettingGroupAdapter = new LiveSettingGroupAdapter();
        mSettingGroupView.setAdapter(liveSettingGroupAdapter);
        mSettingGroupView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, LiveConstants.AUTO_HIDE_SETTINGS_MS);
            }
        });
        mSettingGroupView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                selectSettingGroup(position, true);
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {}
        });
        liveSettingGroupAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            selectSettingGroup(position, false);
        });
    }

    private void initSettingItemView() {
        mSettingItemView.setHasFixedSize(true);
        mSettingItemView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        liveSettingItemAdapter = new LiveSettingItemAdapter();
        mSettingItemView.setAdapter(liveSettingItemAdapter);
        mSettingItemView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, LiveConstants.AUTO_HIDE_SETTINGS_MS);
            }
        });
        mSettingItemView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (position < 0) return;
                liveSettingGroupAdapter.setFocusedGroupIndex(-1);
                liveSettingItemAdapter.setFocusedItemIndex(position);
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, LiveConstants.AUTO_HIDE_SETTINGS_MS);
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                clickSettingItem(position);
            }
        });
        liveSettingItemAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            clickSettingItem(position);
        });
    }

    private void selectSettingGroup(int position, boolean focus) {
        if (!isCurrentLiveChannelValid() && position == 0) {
            Toast.makeText(App.getInstance(), "无直播源，无法切换线路", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (focus) {
            liveSettingGroupAdapter.setFocusedGroupIndex(position);
            liveSettingItemAdapter.setFocusedItemIndex(-1);
        }
        if (position == liveSettingGroupAdapter.getSelectedGroupIndex() || position < -1) return;
        
        liveSettingGroupAdapter.setSelectedGroupIndex(position);
        liveSettingItemAdapter.setNewData(liveSettingGroupList.get(position).getLiveSettingItems());
        switch (position) {
            case 0:
                if (currentLiveChannelItem != null) {
                    liveSettingItemAdapter.selectItem(currentLiveChannelItem.getSourceIndex(), true, false);
                }
                break;
            case 1:
                try {
                    liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerScale(), true, true);
                } catch (Exception e) {
                    liveSettingItemAdapter.selectItem(0, true, true);
                }
                break;
            case 2:
                try {
                    liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerType(), true, true);
                } catch (Exception e) {
                    liveSettingItemAdapter.selectItem(0, true, true);
                }
                break;
        }
        int scrollToPosition = liveSettingItemAdapter.getSelectedItemIndex();
        if (scrollToPosition < 0) scrollToPosition = 0;
        mSettingItemView.scrollToPosition(scrollToPosition);
        mHandler.removeCallbacks(mHideSettingLayoutRun);
        mHandler.postDelayed(mHideSettingLayoutRun, LiveConstants.AUTO_HIDE_SETTINGS_MS);
    }

    // ---------- 8.4 播放器初始化 ----------
    private void initVideoView() {
        controller = new LiveController(this);
        controller.setListener(new LiveController.LiveControlListener() {
            @Override
            public boolean singleTap(MotionEvent e) {
                int fiveScreen = PlayerUtils.getScreenWidth(mContext, true) / 5;
                if (e.getX() > 0 && e.getX() < (fiveScreen * 2)) showChannelList();
                else if ((e.getX() > (fiveScreen * 2)) && (e.getX() < (fiveScreen * 3))) toggleChannelInfo();
                else if (e.getX() > (fiveScreen * 3)) showSettingGroup();
                return true;
            }
            @Override
            public void longPress() { showSettingGroup(); }
            @Override
            public void playStateChanged(int playState) {
                if (currentLiveChannelItem == null) return;
                switch (playState) {
                    case VideoView.STATE_IDLE:
                    case VideoView.STATE_PAUSED:
                        break;
                    case VideoView.STATE_PREPARED:
                        if (mVideoView.getVideoSize().length >= 2) {
                            tv_size.setText(mVideoView.getVideoSize()[0] + " x " + mVideoView.getVideoSize()[1]);
                        }
                        int duration = (int) mVideoView.getDuration();
                        if (duration > 0) {
                            isVOD = true;
                            llSeekBar.setVisibility(View.VISIBLE);
                            mSeekBar.setMax(duration);
                            mSeekBar.setProgress(0);
                            mTotalTime.setText(stringForTimeVod(duration));
                        } else {
                            isVOD = false;
                            llSeekBar.setVisibility(View.GONE);
                        }
                        break;
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PLAYING:
                        currentLiveChangeSourceTimes = 0;
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.removeCallbacks(mConnectTimeoutReplayRun);
                        break;
                    case VideoView.STATE_ERROR:
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.removeCallbacks(mConnectTimeoutReplayRun);
                        if (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 2) == 0) {
                            mHandler.postDelayed(mConnectTimeoutReplayRun, 30 * 1000L);
                        } else {
                            mHandler.post(mConnectTimeoutChangeSourceRun);
                        }
                        break;
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.removeCallbacks(mConnectTimeoutReplayRun);
                        if (Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 2) == 0) {
                            mHandler.postDelayed(mConnectTimeoutReplayRun, 30 * 1000L);
                        } else {
                            mHandler.postDelayed(mConnectTimeoutChangeSourceRun, Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 2) * 5000L);
                        }
                        break;
                }
            }
            @Override
            public void changeSource(int direction) {
                if (direction > 0) playNextSource();
                else playPreSource();
            }
        });
        controller.setCanChangePosition(false);
        controller.setEnableInNormal(true);
        controller.setGestureEnabled(true);
        controller.setDoubleTapTogglePlayEnabled(false);
        mVideoView.setVideoController(controller);
        mVideoView.setProgressManager(null);
    }

    // ---------- 8.5 数据加载 ----------
    private void initLiveChannelList() {
        List<LiveChannelGroup> list = ApiConfig.get().getChannelGroupList();
        if (list.isEmpty()) {
            Toast.makeText(App.getInstance(), getString(R.string.act_live_play_empty_channel), Toast.LENGTH_SHORT).show();
            liveChannelGroupList.clear();
            showSuccess();
            initLiveState();
            return;
        }
        if (list.size() == 1 && list.get(0).getGroupName().startsWith("http://127.0.0.1")) {
            loadProxyLives(list.get(0).getGroupName());
        } else {
            liveChannelGroupList.clear();
            liveChannelGroupList.addAll(list);
            showSuccess();
            initLiveState();
        }
    }

    public void loadProxyLives(String url) {
        try {
            Uri parsedUrl = Uri.parse(url);
            url = new String(Base64.decode(parsedUrl.getQueryParameter("ext"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
            if (url.isEmpty()) {
                Toast.makeText(App.getInstance(), getString(R.string.act_live_play_empty_live_url), Toast.LENGTH_LONG).show();
                liveChannelGroupList.clear();
                showSuccess();
                initLiveState();
                return;
            }
        } catch (Throwable th) {
            Toast.makeText(App.getInstance(), getString(R.string.act_live_play_empty_channel), Toast.LENGTH_SHORT).show();
            liveChannelGroupList.clear();
            showSuccess();
            initLiveState();
            return;
        }
        showLoading();
        OkGo.<String>get(url).execute(new AbsCallback<String>() {
            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                return response.body().string();
            }
            @Override
            public void onSuccess(Response<String> response) {
                JsonArray livesArray;
                LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap = new LinkedHashMap<>();
                TxtSubscribe.parse(linkedHashMap, response.body());
                livesArray = TxtSubscribe.live2JsonArray(linkedHashMap);
                ApiConfig.get().loadLives(livesArray);
                List<LiveChannelGroup> list = ApiConfig.get().getChannelGroupList();
                if (list.isEmpty()) {
                    Toast.makeText(App.getInstance(), getString(R.string.act_live_play_empty_channel), Toast.LENGTH_SHORT).show();
                    liveChannelGroupList.clear();
                    mHandler.post(() -> {
                        LivePlayActivity.this.showSuccess();
                        initLiveState();
                    });
                    return;
                }
                liveChannelGroupList.clear();
                liveChannelGroupList.addAll(list);
                mHandler.post(() -> {
                    LivePlayActivity.this.showSuccess();
                    initLiveState();
                });
            }
            @Override
            public void onError(Response<String> response) {
                super.onError(response);
                Toast.makeText(App.getInstance(), getString(R.string.act_live_play_network_error), Toast.LENGTH_LONG).show();
                liveChannelGroupList.clear();
                mHandler.post(() -> {
                    LivePlayActivity.this.showSuccess();
                    initLiveState();
                });
            }
        });
    }

    private void initLiveState() {
        tvLeftChannelListLayout.setVisibility(View.INVISIBLE);
        tvRightSettingLayout.setVisibility(View.INVISIBLE);
        
        livePlayerManager.init(mVideoView);
        showTime();
        showNetSpeed();
        liveChannelGroupAdapter.setNewData(liveChannelGroupList);
        
        if (liveChannelGroupList.isEmpty() || 
            (liveChannelGroupList.size() == 1 && liveChannelGroupList.get(0).getLiveChannels().isEmpty())) {
            tv_channelname.setText("无直播源");
            tv_channelnum.setText("");
            tv_source.setText("0/0");
            tv_size.setText("");
            tv_curr_name.setText("请先添加直播源");
            tv_next_name.setText("");
        } else {
            int lastChannelGroupIndex = -1;
            int lastLiveChannelIndex = -1;
            Intent intent = getIntent();
            if (intent != null && intent.getExtras() != null) {
                Bundle bundle = intent.getExtras();
                lastChannelGroupIndex = bundle.getInt("groupIndex", 0);
                lastLiveChannelIndex = bundle.getInt("channelIndex", 0);
            } else {
                Pair<Integer, Integer> lastChannel = JavaUtil.findLiveLastChannel(liveChannelGroupList);
                lastChannelGroupIndex = lastChannel.getFirst();
                lastLiveChannelIndex = lastChannel.getSecond();
            }
            selectChannelGroup(lastChannelGroupIndex, false, lastLiveChannelIndex);
            
            if (epgCacheHelper != null && currentLiveChannelItem != null) {
                List<String> allChannelNames = new ArrayList<>();
                for (LiveChannelGroup group : liveChannelGroupList) {
                    for (LiveChannelItem channel : group.getLiveChannels()) {
                        allChannelNames.add(channel.getChannelName());
                    }
                }
                final String currentName = currentLiveChannelItem.getChannelName();
                mHandler.postDelayed(() -> {
                    epgCacheHelper.preloadOtherChannels(allChannelNames, currentName);
                }, LiveConstants.PRELOAD_DELAY_MS);
            }
        }
    }

    private void initLiveSettingGroupList() {
        ArrayList<String> groupNames = new ArrayList<>(Arrays.asList("线路选择", "画面比例", "播放解码", "超时换源", "偏好设置", "直播地址", "退出直播"));
        ArrayList<ArrayList<String>> itemsArrayList = new ArrayList<>();
        itemsArrayList.add(new ArrayList<>());
        itemsArrayList.add(new ArrayList<>(Arrays.asList("默认", "16:9", "4:3", "填充", "原始", "裁剪")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("系统", "ijk硬解", "ijk软解", "exo")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("关", "5s", "10s", "15s", "20s", "25s", "30s")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("显示时间", "显示网速", "换台反转", "跨选分类", "关闭密码")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("列表历史")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("确定")));
        liveSettingGroupList.clear();
        for (int i = 0; i < groupNames.size(); i++) {
            LiveSettingGroup liveSettingGroup = new LiveSettingGroup();
            ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
            liveSettingGroup.setGroupIndex(i);
            liveSettingGroup.setGroupName(groupNames.get(i));
            for (int j = 0; j < itemsArrayList.get(i).size(); j++) {
                LiveSettingItem liveSettingItem = new LiveSettingItem();
                liveSettingItem.setItemIndex(j);
                liveSettingItem.setItemName(itemsArrayList.get(i).get(j));
                liveSettingItemList.add(liveSettingItem);
            }
            liveSettingGroup.setLiveSettingItems(liveSettingItemList);
            liveSettingGroupList.add(liveSettingGroup);
        }
        liveSettingGroupList.get(3).getLiveSettingItems().get(Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 2)).setItemSelected(true);
        liveSettingGroupList.get(4).getLiveSettingItems().get(0).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_TIME, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(1).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(2).setItemSelected(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(3).setItemSelected(Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false));
        liveSettingGroupList.get(4).getLiveSettingItems().get(4).setItemSelected(Hawk.get(HawkConfig.LIVE_SKIP_PASSWORD, false));
    }

    private void loadCurrentSourceList() {
        if (currentLiveChannelItem == null) {
            ArrayList<LiveSettingItem> emptyList = new ArrayList<>();
            LiveSettingItem emptyItem = new LiveSettingItem();
            emptyItem.setItemIndex(0);
            emptyItem.setItemName("无可用线路");
            emptyList.add(emptyItem);
            liveSettingGroupList.get(0).setLiveSettingItems(emptyList);
            return;
        }
        ArrayList<String> currentSourceNames = currentLiveChannelItem.getChannelSourceNames();
        ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
        for (int j = 0; j < currentSourceNames.size(); j++) {
            LiveSettingItem liveSettingItem = new LiveSettingItem();
            liveSettingItem.setItemIndex(j);
            liveSettingItem.setItemName(currentSourceNames.get(j));
            liveSettingItemList.add(liveSettingItem);
        }
        liveSettingGroupList.get(0).setLiveSettingItems(liveSettingItemList);
    }

    // ==================== 区域9: 辅助方法 ====================
    // ============================================================

    // ---------- 9.1 频道数据获取 ----------
    private ArrayList<LiveChannelItem> getLiveChannels(int groupIndex) {
        if (groupIndex >= liveChannelGroupList.size()) return new ArrayList<>();
        if (!isNeedInputPassword(groupIndex)) {
            return liveChannelGroupList.get(groupIndex).getLiveChannels();
        }
        return new ArrayList<>();
    }

    private Integer[] getNextChannel(int direction) {
        if (liveChannelGroupList.isEmpty() || currentLiveChannelItem == null) return new Integer[]{0, -1};
        int channelGroupIndex = currentChannelGroupIndex;
        int liveChannelIndex = currentLiveChannelIndex;
        if (direction > 0) {
            liveChannelIndex++;
            if (liveChannelIndex >= getLiveChannels(channelGroupIndex).size()) {
                liveChannelIndex = 0;
                if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex++;
                        if (channelGroupIndex >= liveChannelGroupList.size()) channelGroupIndex = 0;
                    } while ((channelGroupIndex >= liveChannelGroupList.size() ||
                            !liveChannelGroupList.get(channelGroupIndex).getGroupPassword().isEmpty()) &&
                            channelGroupIndex != currentChannelGroupIndex);
                }
            }
        } else {
            liveChannelIndex--;
            if (liveChannelIndex < 0) {
                if (Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
                    do {
                        channelGroupIndex--;
                        if (channelGroupIndex < 0) channelGroupIndex = liveChannelGroupList.size() - 1;
                    } while ((channelGroupIndex >= liveChannelGroupList.size() ||
                            !liveChannelGroupList.get(channelGroupIndex).getGroupPassword().isEmpty()) &&
                            channelGroupIndex != currentChannelGroupIndex);
                }
                liveChannelIndex = getLiveChannels(channelGroupIndex).size() - 1;
            }
        }
        return new Integer[]{channelGroupIndex, liveChannelIndex};
    }

    // ---------- 9.2 密码验证 ----------
    private boolean isNeedInputPassword(int groupIndex) {
        if (groupIndex >= liveChannelGroupList.size()) return false;
        return !liveChannelGroupList.get(groupIndex).getGroupPassword().isEmpty() && !isPasswordConfirmed(groupIndex);
    }

    private boolean isPasswordConfirmed(int groupIndex) {
        if (Hawk.get(HawkConfig.LIVE_SKIP_PASSWORD, false)) return true;
        for (Integer confirmedNum : channelGroupPasswordConfirmed) {
            if (confirmedNum == groupIndex) return true;
        }
        return false;
    }

    private void showPasswordDialog(int groupIndex, int liveChannelIndex) {
        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) mHandler.removeCallbacks(mHideChannelListRun);
        LivePasswordDialog dialog = new LivePasswordDialog(this);
        dialog.setOnListener(new LivePasswordDialog.OnListener() {
            @Override
            public void onChange(String password) {
                if (password.equals(liveChannelGroupList.get(groupIndex).getGroupPassword())) {
                    channelGroupPasswordConfirmed.add(groupIndex);
                    loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
                } else {
                    Toast.makeText(App.getInstance(), "密码错误", Toast.LENGTH_SHORT).show();
                }
                if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) mHandler.postDelayed(mHideChannelListRun, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            }
            @Override
            public void onCancel() {
                if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    int idx = liveChannelGroupAdapter.getSelectedGroupIndex();
                    liveChannelItemAdapter.setNewData(getLiveChannels(idx));
                }
            }
        });
        dialog.show();
    }

    private void loadChannelGroupDataAndPlay(int groupIndex, int liveChannelIndex) {
        liveChannelItemAdapter.setNewData(getLiveChannels(groupIndex));
        if (groupIndex == currentChannelGroupIndex) {
            if (currentLiveChannelIndex > -1 && currentLiveChannelIndex < mChannelGridView.getAdapter().getItemCount()) {
                mChannelGridView.scrollToPosition(currentLiveChannelIndex);
            }
            liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
        } else {
            mChannelGridView.scrollToPosition(0);
            liveChannelItemAdapter.setSelectedChannelIndex(-1);
        }
        if (liveChannelIndex > -1) {
            clickLiveChannel(liveChannelIndex);
            mGroupGridView.scrollToPosition(groupIndex);
            mChannelGridView.scrollToPosition(liveChannelIndex);
            playChannel(groupIndex, liveChannelIndex, false);
        }
    }

    // ---------- 9.3 时间/网速显示 ----------
    void showTime() {
        if (Hawk.get(HawkConfig.LIVE_SHOW_TIME, false)) {
            mHandler.post(mUpdateTimeRun);
            tvTime.setVisibility(View.VISIBLE);
        } else {
            mHandler.removeCallbacks(mUpdateTimeRun);
            tvTime.setVisibility(View.GONE);
        }
    }

    void showNetSpeed() {
        if (Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false)) {
            mHandler.post(mUpdateNetSpeedRun);
            tvNetSpeed.setVisibility(View.VISIBLE);
        } else {
            mHandler.removeCallbacks(mUpdateNetSpeedRun);
            tvNetSpeed.setVisibility(View.GONE);
        }
    }

    // ---------- 9.4 数字键换台 ----------
    private void numericKeyDown(int digit) {
        selectedChannelNumber = selectedChannelNumber * 10 + digit;
        tvSelectedChannel.setText(Integer.toString(selectedChannelNumber));
        tvSelectedChannel.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mPlaySelectedChannel);
        mHandler.postDelayed(mPlaySelectedChannel, LiveConstants.NUMERIC_TIMEOUT_MS);
    }

    // ---------- 9.5 其他辅助 ----------
    private boolean isListOrSettingLayoutVisible() {
        return tvLeftChannelListLayout.getVisibility() == View.VISIBLE || tvRightSettingLayout.getVisibility() == View.VISIBLE;
    }

    private boolean isCurrentLiveChannelValid() {
        return currentLiveChannelItem != null;
    }

    private int getFirstNoPasswordChannelGroup() {
        for (LiveChannelGroup group : liveChannelGroupList) {
            if (group.getGroupPassword().isEmpty()) return group.getGroupIndex();
        }
        return -1;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_LIVEPLAY_UPDATE) {
            Bundle bundle = (Bundle) event.obj;
            int channelGroupIndex = bundle.getInt("groupIndex", 0);
            int liveChannelIndex = bundle.getInt("channelIndex", 0);
            if (channelGroupIndex != liveChannelGroupAdapter.getSelectedGroupIndex()) {
                selectChannelGroup(channelGroupIndex, true, liveChannelIndex);
            } else {
                clickLiveChannel(liveChannelIndex);
                mGroupGridView.scrollToPosition(channelGroupIndex);
                mChannelGridView.scrollToPosition(liveChannelIndex);
                playChannel(channelGroupIndex, liveChannelIndex, false);
            }
        }
    }
}
