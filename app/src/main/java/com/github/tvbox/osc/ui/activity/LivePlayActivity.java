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
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.controller.LiveController;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemAdapter;
import com.github.tvbox.osc.ui.adapter.LiveEpgAdapter;
import com.github.tvbox.osc.ui.adapter.LiveEpgDateAdapter;
import com.github.tvbox.osc.ui.dialog.ApiHistoryDialog;
import com.github.tvbox.osc.ui.dialog.LivePasswordDialog;
import com.github.tvbox.osc.ui.panel.LiveSettingsPanel;
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

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import kotlin.Pair;
import okhttp3.Request;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

/**
 * LivePlayActivity - 重构完成版
 * 已完成：常量抽取、EPG缓存抽取、设置面板抽取、删除hsEpg
 * 
 * ==================== 完整功能区域索引 ====================
 * 
 * 区域1: 成员变量声明
 *   1.1 核心播放器变量
 *   1.2 UI控件变量
 *   1.3 频道数据变量
 *   1.4 播放器管理变量
 *   1.5 EPG相关变量
 *   1.6 EPG缓存管理器
 *   1.7 设置面板
 *   1.8 时移/回放相关变量
 *   1.9 动画Runnable变量
 *   1.10 其他变量
 * 
 * 区域2: 工具方法
 *   2.1 播放请求头
 *   2.2 时移URL构建
 * 
 * 区域3: EPG获取和预加载
 * 
 * 区域4: 生命周期方法
 *   4.1 getLayoutResID()
 *   4.2 init() - 重要
 *   4.3 onUserLeaveHint()
 *   4.4 onBackPressed()
 *   4.5 exit()
 *   4.6 dispatchKeyEvent()
 *   4.7 onResume()
 *   4.8 onStop()
 *   4.9 onPause()
 *   4.10 onPictureInPictureModeChanged()
 *   4.11 onDestroy()
 * 
 * 区域5: UI显示方法
 *   5.1 showChannelList()
 *   5.2 divLoadEpgR()
 *   5.3 divLoadEpgL()
 *   5.4 showSettingGroup()
 *   5.5 showChannelInfo()
 *   5.6 toggleChannelInfo()
 *   5.7 showEpg()
 *   5.8 showBottomEpg()
 *   5.9 getTvLogo()
 * 
 * 区域6: 播放控制方法
 *   6.1 playChannel()
 *   6.2 replayChannel()
 *   6.3 playNext()
 *   6.4 playPrevious()
 *   6.5 playPreSource()
 *   6.6 playNextSource()
 * 
 * 区域7: 初始化方法
 *   7.1 initEpgListView()
 *   7.2 initEpgDateView()
 *   7.3 initChannelGroupView()
 *   7.4 initLiveChannelView()
 *   7.5 selectChannelGroup()
 *   7.6 clickLiveChannel()
 *   7.7 initVideoView()
 *   7.8 initLiveChannelList()
 *   7.9 loadProxyLives()
 *   7.10 initLiveState() - 重要
 * 
 * 区域8: 辅助方法
 *   8.1 getLiveChannels()
 *   8.2 getNextChannel()
 *   8.3 isNeedInputPassword()
 *   8.4 isPasswordConfirmed()
 *   8.5 showPasswordDialog()
 *   8.6 loadChannelGroupDataAndPlay()
 *   8.7 showTime()
 *   8.8 showNetSpeed()
 *   8.9 numericKeyDown()
 *   8.10 isListOrSettingLayoutVisible()
 *   8.11 isCurrentLiveChannelValid()
 *   8.12 refresh()
 * 
 * ============================================================
 */
public class LivePlayActivity extends BaseActivity {

    // ==================== 区域1: 成员变量声明 ====================

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
    public static int currentChannelGroupIndex = 0;
    private int currentLiveChannelIndex = -1;
    private LiveChannelItem currentLiveChannelItem = null;
    private final ArrayList<Integer> channelGroupPasswordConfirmed = new ArrayList<>();
    private int selectedChannelNumber = 0;

    // ---------- 1.4 播放器管理变量 ----------
    private final LivePlayerManager livePlayerManager = new LivePlayerManager();
    private int currentLiveChangeSourceTimes = 0;

    // ---------- 1.5 EPG相关变量 ----------
    private LiveEpgDateAdapter epgDateAdapter;
    private LiveEpgAdapter epgListAdapter;
    public String epgStringAddress = "";
    SimpleDateFormat timeFormat = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
    private final Handler mHandler = new Handler();
    private List<Epginfo> epgdata = new ArrayList<>();

    // ---------- 1.6 EPG缓存管理器 ----------
    private EpgCacheHelper epgCacheHelper;

    // ---------- 1.7 设置面板 ----------
    private LiveSettingsPanel settingsPanel;

    // ---------- 1.8 时移/回放相关变量 ----------
    private boolean isShiyiMode = false;
    private static String shiyi_time;

    // ---------- 1.9 动画Runnable变量 ----------
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

    private final Runnable mUpdateLayout = new Runnable() {
        @Override
        public void run() {
            tvLeftChannelListLayout.requestLayout();
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
            if (mGroupGridView.isScrolling() || mChannelGridView.isScrolling() || 
                mGroupGridView.isComputingLayout() || mChannelGridView.isComputingLayout()) {
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

    // ---------- 1.10 其他变量 ----------
    boolean mIsDragging;
    boolean isVOD = false;
    boolean PiPON = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0) == 2;
    private long mExitTime = 0;
    private boolean onStopCalled;

    // ==================== 区域2: 工具方法 ====================

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

    public void getEpg(Date date) {
        if (currentLiveChannelItem == null || currentLiveChannelItem.getChannelName() == null) return;

        final String channelName = currentLiveChannelItem.getChannelName();
        SimpleDateFormat sdf = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
        final String dateStr = sdf.format(date);

        ArrayList<Epginfo> cached = epgCacheHelper.getCachedEpg(channelName, dateStr);
        if (cached != null && !cached.isEmpty()) {
            showEpg(date, cached);
            showBottomEpg();
            return;
        }

        epgCacheHelper.requestEpg(channelName, date, new EpgCacheHelper.EpgCallback() {
            @Override
            public void onSuccess(String channelName, Date date, ArrayList<Epginfo> epgList) {
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

    // ---------- 4.1 getLayoutResID() ----------
    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live_play;
    }

    // ---------- 4.2 init() ----------
    @Override
    protected void init() {
        hideSystemUI(false);

        epgStringAddress = Hawk.get(HawkConfig.EPG_URL, "");
        if (StringUtils.isBlank(epgStringAddress)) {
            epgStringAddress = LiveConstants.DEFAULT_EPG_URL;
        }

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

        // ==================== 初始化设置面板 ====================
        LinearLayout tvRightSettingLayout = findViewById(R.id.tvRightSettingLayout);
        TvRecyclerView mSettingGroupView = findViewById(R.id.mSettingGroupView);
        TvRecyclerView mSettingItemView = findViewById(R.id.mSettingItemView);

        settingsPanel = new LiveSettingsPanel(this, mHandler, 
                tvRightSettingLayout, mSettingGroupView, mSettingItemView);
        settingsPanel.init();
        // 注意：setCurrentScale 和 setCurrentPlayerType 已移到 initLiveState() 中调用
        settingsPanel.setListener(new LiveSettingsPanel.SettingsListener() {
            @Override
            public void onSourceChanged(int sourceIndex) {
                if (currentLiveChannelItem != null) {
                    currentLiveChannelItem.setSourceIndex(sourceIndex);
                    playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
                }
            }

            @Override
            public void onScaleChanged(int scaleIndex) {
                try {
                    livePlayerManager.changeLivePlayerScale(mVideoView, scaleIndex,
                            currentLiveChannelItem != null ? currentLiveChannelItem.getChannelName() : "");
                    Toast.makeText(LivePlayActivity.this, 
                            currentLiveChannelItem != null ? "画面比例已应用" : "画面比例已设置", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(LivePlayActivity.this, "设置失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onPlayerTypeChanged(int typeIndex) {
                try {
                    if (currentLiveChannelItem != null) mVideoView.release();
                    livePlayerManager.changeLivePlayerType(mVideoView, typeIndex,
                            currentLiveChannelItem != null ? currentLiveChannelItem.getChannelName() : "");
                    if (currentLiveChannelItem != null) {
                        mVideoView.setUrl(currentLiveChannelItem.getUrl(), setPlayHeaders(currentLiveChannelItem.getUrl()));
                        mVideoView.start();
                    }
                    Toast.makeText(LivePlayActivity.this,
                            currentLiveChannelItem != null ? "解码方式已应用" : "解码方式已设置", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(LivePlayActivity.this, "设置失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onTimeoutChanged(int timeoutIndex) {
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, timeoutIndex);
            }

            @Override
            public void onPreferenceChanged(String key, boolean value) {
                if (HawkConfig.LIVE_SHOW_TIME.equals(key)) showTime();
                else if (HawkConfig.LIVE_SHOW_NET_SPEED.equals(key)) showNetSpeed();
            }

            @Override
            public void onLiveAddressSelected() {
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

            @Override
            public void onExit() {
                finish();
            }
        });

        tvTime = findViewById(R.id.tvTime);
        tvNetSpeed = findViewById(R.id.tvNetSpeed);

        initEpgDateView();
        initEpgListView();
        initVideoView();
        initChannelGroupView();
        initLiveChannelView();
        initLiveChannelList();

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

        mBack.setOnClickListener(v -> finish());
    }

    // ---------- 4.3 onUserLeaveHint() ----------
    @Override
    public void onUserLeaveHint() {
        if (supportsPiPMode() && PiPON) {
            mHandler.post(mHideChannelListRun);
            mHandler.post(mHideChannelInfoRun);
            if (settingsPanel != null) settingsPanel.hide();
            enterPictureInPictureMode();
        }
    }

    // ---------- 4.4 onBackPressed() ----------
    @Override
    public void onBackPressed() {
        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
        } else if (settingsPanel != null && settingsPanel.isShowing()) {
            settingsPanel.hide();
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

    // ---------- 4.5 exit() ----------
    private void exit() {
        if (System.currentTimeMillis() - mExitTime < LiveConstants.EXIT_CONFIRM_DELAY_MS) {
            super.onBackPressed();
        } else {
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, getString(R.string.hm_exit_live), Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- 4.6 dispatchKeyEvent() ----------
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

    // ---------- 4.7 onResume() ----------
    @Override
    protected void onResume() {
        super.onResume();
        if (mVideoView != null) mVideoView.resume();
    }

    // ---------- 4.8 onStop() ----------
    @Override
    protected void onStop() {
        super.onStop();
        onStopCalled = true;
    }

    // ---------- 4.9 onPause() ----------
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

    // ---------- 4.10 onPictureInPictureModeChanged() ----------
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (supportsPiPMode() && !isInPictureInPictureMode() && onStopCalled) {
            mVideoView.release();
        }
    }

    // ---------- 4.11 onDestroy() ----------
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
        if (settingsPanel != null) {
            settingsPanel.destroy();
        }
    }

    // ==================== 区域5: UI显示方法 ====================

    // ---------- 5.1 showChannelList() ----------
    private void showChannelList() {
        mBack.setVisibility(View.INVISIBLE);
        if (tvBottomLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelInfoRun);
            mHandler.post(mHideChannelInfoRun);
        } else if (settingsPanel != null && settingsPanel.isShowing()) {
            settingsPanel.hide();
        } else if (tvLeftChannelListLayout.getVisibility() == View.INVISIBLE) {
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

    // ---------- 5.2 divLoadEpgR() ----------
    public void divLoadEpgR(View view) {
        mGroupGridView.setVisibility(View.GONE);
        mEpgInfoGridView.setVisibility(View.VISIBLE);
        mGroupEPG.setVisibility(View.VISIBLE);
        mDivLeft.setVisibility(View.VISIBLE);
        mDivRight.setVisibility(View.GONE);
        tvLeftChannelListLayout.setVisibility(View.INVISIBLE);
        showChannelList();
    }

    // ---------- 5.3 divLoadEpgL() ----------
    public void divLoadEpgL(View view) {
        mGroupGridView.setVisibility(View.VISIBLE);
        mEpgInfoGridView.setVisibility(View.GONE);
        mGroupEPG.setVisibility(View.GONE);
        mDivLeft.setVisibility(View.GONE);
        mDivRight.setVisibility(View.VISIBLE);
        tvLeftChannelListLayout.setVisibility(View.INVISIBLE);
        showChannelList();
    }

    // ---------- 5.4 showSettingGroup() ----------
    private void showSettingGroup() {
        mBack.setVisibility(View.INVISIBLE);

        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.post(mHideChannelListRun);
        } else if (tvBottomLayout.getVisibility() == View.VISIBLE) {
            mHandler.post(mHideChannelInfoRun);
        }

        if (settingsPanel != null) {
            settingsPanel.show();
        }
    }

    // ---------- 5.5 showChannelInfo() ----------
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

    // ---------- 5.6 toggleChannelInfo() ----------
    private void toggleChannelInfo() {
        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
        } else if (settingsPanel != null && settingsPanel.isShowing()) {
            settingsPanel.hide();
        } else if (tvBottomLayout.getVisibility() == View.INVISIBLE) {
            showChannelInfo();
        } else {
            mBack.setVisibility(View.INVISIBLE);
            mHandler.removeCallbacks(mHideChannelInfoRun);
            mHandler.post(mHideChannelInfoRun);
            mHandler.post(mUpdateLayout);
        }
    }

    // ---------- 5.7 showEpg() ----------
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
            Epginfo epgbcinfo = new Epginfo(date, LiveConstants.NO_PROGRAM, date,
                    LiveConstants.DEFAULT_START_TIME, LiveConstants.DEFAULT_END_TIME, 0);
            arrayList.add(epgbcinfo);
            epgdata = arrayList;
            epgListAdapter.setNewData(epgdata);
        }
    }

    // ---------- 5.8 showBottomEpg() ----------
    private void showBottomEpg() {
        if (isShiyiMode) return;
        if (currentLiveChannelItem == null || currentLiveChannelItem.getChannelName() == null) {
            tv_curr_name.setText(LiveConstants.NO_PROGRAM);
            tv_next_name.setText("");
            return;
        }

        showChannelInfo();
        String channelName = currentLiveChannelItem.getChannelName();

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
        } else {
            tv_curr_name.setText(LiveConstants.NO_PROGRAM);
            tv_next_name.setText("");
        }
    }

    // ---------- 5.9 getTvLogo() ----------
    private void getTvLogo(String channelName, String logoUrl) {
        RequestOptions options = new RequestOptions();
        options.diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).placeholder(R.drawable.img_logo_placeholder);
        Glide.with(App.getInstance()).load(logoUrl).apply(options).into(tv_logo);
    }

    // ==================== 区域6: 播放控制方法 ====================

    // ---------- 6.1 playChannel() ----------
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

            // 更新设置面板线路列表
            if (settingsPanel != null) {
                settingsPanel.updateSourceList(currentLiveChannelItem);
                settingsPanel.setCurrentSourceIndex(currentLiveChannelItem.getSourceIndex());
            }
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

        if (epgCacheHelper != null && currentLiveChannelItem != null) {
            epgCacheHelper.preloadCurrentChannel(currentLiveChannelItem.getChannelName());
        }

        mVideoView.setUrl(currentLiveChannelItem.getUrl(), setPlayHeaders(currentLiveChannelItem.getUrl()));
        showChannelInfo();
        mVideoView.start();
        return true;
    }

    // ---------- 6.2 replayChannel() ----------
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

    // ---------- 6.3 playNext() ----------
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

    // ---------- 6.4 playPrevious() ----------
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

    // ---------- 6.5 playPreSource() ----------
    public void playPreSource() {
        if (!isCurrentLiveChannelValid()) {
            Toast.makeText(App.getInstance(), "暂无直播源", Toast.LENGTH_SHORT).show();
            return;
        }
        currentLiveChannelItem.preSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }

    // ---------- 6.6 playNextSource() ----------
    public void playNextSource() {
        if (mVideoView == null || !isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.nextSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
    }

    // ==================== 区域7: 初始化方法 ====================

    // ---------- 7.1 initEpgListView() ----------
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

    // ---------- 7.2 initEpgDateView() ----------
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

    // ---------- 7.3 initChannelGroupView() ----------
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

    // ---------- 7.4 initLiveChannelView() ----------
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

    // ---------- 7.5 selectChannelGroup() ----------
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

    // ---------- 7.6 clickLiveChannel() ----------
    private void clickLiveChannel(int position) {
        liveChannelItemAdapter.setSelectedChannelIndex(position);
        epgDateAdapter.setSelectedIndex(6);
        if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
        }
        playChannel(liveChannelGroupAdapter.getSelectedGroupIndex(), position, false);
    }

    // ---------- 7.7 initVideoView() ----------
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

    // ---------- 7.8 initLiveChannelList() ----------
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

    // ---------- 7.9 loadProxyLives() ----------
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

    // ---------- 7.10 initLiveState() ----------
    private void initLiveState() {
        tvLeftChannelListLayout.setVisibility(View.INVISIBLE);

        livePlayerManager.init(mVideoView);
        
        // ==================== 设置面板当前值（必须在 livePlayerManager.init() 之后） ====================
        if (settingsPanel != null) {
            settingsPanel.setCurrentScale(livePlayerManager.getLivePlayerScale());
            settingsPanel.setCurrentPlayerType(livePlayerManager.getLivePlayerType());
        }
        // ============================================================================================
        
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

            // 预加载其他频道 EPG
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

    // ==================== 区域8: 辅助方法 ====================

    // ---------- 8.1 getLiveChannels() ----------
    private ArrayList<LiveChannelItem> getLiveChannels(int groupIndex) {
        if (groupIndex >= liveChannelGroupList.size()) return new ArrayList<>();
        if (!isNeedInputPassword(groupIndex)) {
            return liveChannelGroupList.get(groupIndex).getLiveChannels();
        }
        return new ArrayList<>();
    }

    // ---------- 8.2 getNextChannel() ----------
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

    // ---------- 8.3 isNeedInputPassword() ----------
    private boolean isNeedInputPassword(int groupIndex) {
        if (groupIndex >= liveChannelGroupList.size()) return false;
        return !liveChannelGroupList.get(groupIndex).getGroupPassword().isEmpty() && !isPasswordConfirmed(groupIndex);
    }

    // ---------- 8.4 isPasswordConfirmed() ----------
    private boolean isPasswordConfirmed(int groupIndex) {
        if (Hawk.get(HawkConfig.LIVE_SKIP_PASSWORD, false)) return true;
        for (Integer confirmedNum : channelGroupPasswordConfirmed) {
            if (confirmedNum == groupIndex) return true;
        }
        return false;
    }

    // ---------- 8.5 showPasswordDialog() ----------
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

    // ---------- 8.6 loadChannelGroupDataAndPlay() ----------
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

    // ---------- 8.7 showTime() ----------
    void showTime() {
        if (Hawk.get(HawkConfig.LIVE_SHOW_TIME, false)) {
            mHandler.post(mUpdateTimeRun);
            tvTime.setVisibility(View.VISIBLE);
        } else {
            mHandler.removeCallbacks(mUpdateTimeRun);
            tvTime.setVisibility(View.GONE);
        }
    }

    // ---------- 8.8 showNetSpeed() ----------
    void showNetSpeed() {
        if (Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false)) {
            mHandler.post(mUpdateNetSpeedRun);
            tvNetSpeed.setVisibility(View.VISIBLE);
        } else {
            mHandler.removeCallbacks(mUpdateNetSpeedRun);
            tvNetSpeed.setVisibility(View.GONE);
        }
    }

    // ---------- 8.9 numericKeyDown() ----------
    private void numericKeyDown(int digit) {
        selectedChannelNumber = selectedChannelNumber * 10 + digit;
        tvSelectedChannel.setText(Integer.toString(selectedChannelNumber));
        tvSelectedChannel.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mPlaySelectedChannel);
        mHandler.postDelayed(mPlaySelectedChannel, LiveConstants.NUMERIC_TIMEOUT_MS);
    }

    // ---------- 8.10 isListOrSettingLayoutVisible() ----------
    private boolean isListOrSettingLayoutVisible() {
        return tvLeftChannelListLayout.getVisibility() == View.VISIBLE ||
                (settingsPanel != null && settingsPanel.isShowing());
    }

    // ---------- 8.11 isCurrentLiveChannelValid() ----------
    private boolean isCurrentLiveChannelValid() {
        return currentLiveChannelItem != null;
    }

    // ---------- 8.12 refresh() ----------
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
