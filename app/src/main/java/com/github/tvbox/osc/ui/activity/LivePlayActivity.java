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
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveEpgDate;
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.controller.LivePlaybackManager;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.ui.adapter.LiveEpgAdapter;
import com.github.tvbox.osc.ui.adapter.LiveEpgDateAdapter;
import com.github.tvbox.osc.ui.dialog.ApiHistoryDialog;
import com.github.tvbox.osc.ui.dialog.LivePasswordDialog;
import com.github.tvbox.osc.ui.panel.LiveChannelListPanel;
import com.github.tvbox.osc.ui.panel.LiveControlPanel;  // 新增导入
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
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

public class LivePlayActivity extends BaseActivity implements LiveChannelListPanel.ChannelListListener {

    private VideoView mVideoView;
    private LivePlaybackManager playbackManager;

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

    private LinearLayout mGroupEPG;
    private LinearLayout mDivLeft;
    private LinearLayout mDivRight;
    private TvRecyclerView mEpgDateGridView;
    private TvRecyclerView mEpgInfoGridView;

    private TextView tvSelectedChannel;
    private TextView tvTime;
    private TextView tvNetSpeed;
    private LinearLayout mBack;
    private LinearLayout llSeekBar;
    private TextView mCurrentTime;
    private SeekBar mSeekBar;
    private TextView mTotalTime;

    private final List<LiveChannelGroup> liveChannelGroupList = new ArrayList<>();
    public static int currentChannelGroupIndex = 0;
    private int currentLiveChannelIndex = -1;
    private LiveChannelItem currentLiveChannelItem = null;
    private final ArrayList<Integer> channelGroupPasswordConfirmed = new ArrayList<>();
    private int selectedChannelNumber = 0;

    private LiveEpgDateAdapter epgDateAdapter;
    private LiveEpgAdapter epgListAdapter;
    public String epgStringAddress = "";
    private final SimpleDateFormat timeFormat = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
    private final Handler mHandler = new Handler();
    private List<Epginfo> epgdata = new ArrayList<>();
    private EpgCacheHelper epgCacheHelper;

    private LiveSettingsPanel settingsPanel;
    private LiveChannelListPanel channelListPanel;
    private LiveControlPanel controlPanel;  // 新增
    private FrameLayout controlPanelContainer;  // 新增

    boolean mIsDragging;
    boolean isVOD = false;
    boolean PiPON = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0) == 2;
    private long mExitTime = 0;
    private boolean onStopCalled;

    // ========== Runnable ==========
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
            if (mGroupEPG != null) mGroupEPG.requestLayout();
            if (mEpgDateGridView != null) mEpgDateGridView.requestLayout();
            if (mEpgInfoGridView != null) mEpgInfoGridView.requestLayout();
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
            if (playbackManager != null) {
                tvNetSpeed.setText(String.format("%.2fMB/s", (float) playbackManager.getTcpSpeed() / 1024.0 / 1024.0));
            }
            mHandler.postDelayed(this, 1000);
        }
    };

    private final Runnable tv_sys_timeRunnable = new Runnable() {
        @Override
        public void run() {
            tv_sys_time.setText(new SimpleDateFormat(LiveConstants.TIME_FORMAT_HHMMSS, Locale.ENGLISH).format(new Date()));
            mHandler.postDelayed(this, 1000);
            if (playbackManager != null && !mIsDragging && playbackManager.getDuration() > 0) {
                int pos = (int) playbackManager.getCurrentPosition();
                mCurrentTime.setText(stringForTimeVod(pos));
                mSeekBar.setProgress(pos);
            }
        }
    };

    private final Runnable mPlaySelectedChannel = new Runnable() {
        @Override
        public void run() {
            tvSelectedChannel.setVisibility(View.GONE);
            tvSelectedChannel.setText("");
            if (selectedChannelNumber <= 0) {
                selectedChannelNumber = 0;
                return;
            }

            int targetGroup = -1;
            int targetChannel = -1;
            int cumulativeMin = 1;

            for (int g = 0; g < liveChannelGroupList.size(); g++) {
                LiveChannelGroup group = liveChannelGroupList.get(g);
                if (isNeedInputPassword(g)) {
                    continue;
                }
                int channelCount = group.getLiveChannels().size();
                int cumulativeMax = cumulativeMin + channelCount - 1;
                if (selectedChannelNumber >= cumulativeMin && selectedChannelNumber <= cumulativeMax) {
                    targetGroup = g;
                    targetChannel = selectedChannelNumber - cumulativeMin;
                    break;
                }
                cumulativeMin = cumulativeMax + 1;
            }

            if (targetGroup >= 0 && targetChannel >= 0) {
                if (isNeedInputPassword(targetGroup)) {
                    showPasswordDialogForGroup(targetGroup, targetChannel);
                } else {
                    playChannel(targetGroup, targetChannel, false);
                }
            }
            selectedChannelNumber = 0;
        }
    };

    // ========== 工具方法 ==========
    public void getEpg(Date date) {
        // ... 原有方法内容保持不变（省略，因篇幅限制，实际使用时应保留完整原代码）
        // 注意：为节省篇幅，此处省略原有方法体。您需要保留原文件中的所有现有方法。
        // 以下仅示意，实际请将原文件内容完整复制，并只添加上述新增的成员和修改点。
    }

    // ========== 生命周期 ==========
    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live_play;
    }

    @Override
    protected void init() {
        hideSystemUI(false);
        epgStringAddress = Hawk.get(HawkConfig.EPG_URL, "");
        if (StringUtils.isBlank(epgStringAddress)) epgStringAddress = LiveConstants.DEFAULT_EPG_URL;
        epgCacheHelper = new EpgCacheHelper(this, epgStringAddress);
        epgCacheHelper.setLogoCallback((channelName, logoUrl) -> {
            if (currentLiveChannelItem != null && channelName.equals(currentLiveChannelItem.getChannelName())) {
                getTvLogo(channelName, logoUrl);
            }
        });

        EventBus.getDefault().register(this);
        setLoadSir(findViewById(R.id.live_root));

        mVideoView = findViewById(R.id.mVideoView);
        playbackManager = new LivePlaybackManager(this, mHandler, mVideoView);
        playbackManager.setListener(new LivePlaybackManager.PlaybackListener() {
            @Override
            public boolean onSingleTap(MotionEvent e) {
                if (controlPanel != null && controlPanel.isShowing()) {
                    return true;
                }
                return handleSingleTap(e);
            }

            @Override
            public void onLongPress() {
                showSettingGroup();
            }

            @Override
            public void onPlayStateChanged(int playState) {
                updateUIForPlayState(playState);
            }

            @Override
            public void onVideoSizeChanged(int width, int height) {
                tv_size.setText(width + " x " + height);
            }

            @Override
            public void onCurrentChannelChanged(LiveChannelItem channel, boolean isChangeSource) {
                currentLiveChannelItem = channel;
                updateChannelUI(channel);
                if (settingsPanel != null && settingsPanel.isShowing()) {
                    settingsPanel.refreshCurrentGroup();
                }
                if (!isChangeSource) {
                    if (channelListPanel != null) {
                        channelListPanel.updateCurrentSelection(currentChannelGroupIndex, currentLiveChannelIndex);
                    }
                }
                showChannelInfo();
            }

            @Override
            public void onAutoSwitchToNextChannel(boolean reverse) {
                if (reverse) playPreviousSilent();
                else playNextSilent();
            }

            @Override
            public void onTimeoutReplay() {
                replayChannel();
            }

            @Override
            public void onShiyiModeChanged(boolean isShiyi, String timeRange) {
                showBottomEpg();
            }

            @Override
            public void onRequestChangeSource(int direction) {
                if (direction > 0) playNextSource();
                else playPreSource();
            }

            @Override
            public void showControlPanel() {
                if (controlPanel != null) {
                    controlPanel.show();
                }
            }
        });

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

        // 设置面板
        LinearLayout tvRightSettingLayout = findViewById(R.id.tvRightSettingLayout);
        TvRecyclerView mSettingGroupView = findViewById(R.id.mSettingGroupView);
        TvRecyclerView mSettingItemView = findViewById(R.id.mSettingItemView);
        settingsPanel = new LiveSettingsPanel(this, mHandler, tvRightSettingLayout, mSettingGroupView, mSettingItemView);
        settingsPanel.init();
        settingsPanel.setListener(new LiveSettingsPanel.SettingsListener() {
            @Override public void onSourceChanged(int sourceIndex) {
                if (currentLiveChannelItem != null) {
                    currentLiveChannelItem.setSourceIndex(sourceIndex);
                    playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
                }
            }
            @Override public void onScaleChanged(int scaleIndex) {
                if (playbackManager == null) return;
                try {
                    playbackManager.changeScale(scaleIndex);
                    Toast.makeText(LivePlayActivity.this, "画面比例已应用", Toast.LENGTH_SHORT).show();
                    if (settingsPanel != null && settingsPanel.isShowing()) settingsPanel.refreshCurrentGroup();
                } catch (Exception e) {
                    Toast.makeText(LivePlayActivity.this, "设置失败", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onPlayerTypeChanged(int typeIndex) {
                if (playbackManager == null) return;
                try {
                    playbackManager.changePlayerType(typeIndex);
                    refreshAfterPlayerTypeChange();
                    Toast.makeText(LivePlayActivity.this, "解码方式已应用", Toast.LENGTH_SHORT).show();
                    if (settingsPanel != null && settingsPanel.isShowing()) settingsPanel.refreshCurrentGroup();
                } catch (Exception e) {
                    Toast.makeText(LivePlayActivity.this, "设置失败", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onTimeoutChanged(int timeoutIndex) {
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, timeoutIndex);
            }
            @Override public void onPreferenceChanged(String key, boolean value) {
                if (HawkConfig.LIVE_SHOW_TIME.equals(key)) showTime();
                else if (HawkConfig.LIVE_SHOW_NET_SPEED.equals(key)) showNetSpeed();
            }
            @Override public void onLiveAddressSelected() {
                ArrayList<String> liveHistory = Hawk.get(HawkConfig.LIVE_HISTORY, new ArrayList<>());
                if (liveHistory.isEmpty()) return;
                String current = Hawk.get(HawkConfig.LIVE_URL, "");
                int idx = liveHistory.contains(current) ? liveHistory.indexOf(current) : 0;
                ApiHistoryDialog dialog = new ApiHistoryDialog(LivePlayActivity.this);
                dialog.setTip(getString(R.string.dia_history_live));
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override public void click(String liveURL) {
                        Hawk.put(HawkConfig.LIVE_URL, liveURL);
                        liveChannelGroupList.clear();
                        try {
                            liveURL = Base64.encodeToString(liveURL.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                            loadProxyLives("http://127.0.0.1:9978/proxy?do=live&type=txt&ext=" + liveURL);
                        } catch (Throwable th) { th.printStackTrace(); }
                        dialog.dismiss();
                    }
                    @Override public void del(String value, ArrayList<String> data) {
                        Hawk.put(HawkConfig.LIVE_HISTORY, data);
                    }
                }, liveHistory, idx);
                dialog.show();
            }
            @Override public void onExit() { finish(); }

            @Override public int getCurrentPlayerType() {
                return playbackManager != null ? playbackManager.getCurrentPlayerType() : 0;
            }
            @Override public int getCurrentScale() {
                return playbackManager != null ? playbackManager.getCurrentScale() : 0;
            }
        });

        // 左侧列表面板
        LinearLayout tvLeftChannelListLayout = findViewById(R.id.tvLeftChannelListLayout);
        TvRecyclerView mGroupGridView = findViewById(R.id.mGroupGridView);
        TvRecyclerView mChannelGridView = findViewById(R.id.mChannelGridView);
        channelListPanel = new LiveChannelListPanel(this, mHandler, tvLeftChannelListLayout, mGroupGridView, mChannelGridView,
                mGroupEPG, mDivLeft, mDivRight, mEpgDateGridView, mEpgInfoGridView);
        channelListPanel.setListener(this);
        channelListPanel.init();

        // 初始化控制面板容器和面板
        controlPanelContainer = findViewById(R.id.control_panel_container);
        controlPanel = new LiveControlPanel(this, controlPanelContainer, playbackManager, mHandler);

        tvTime = findViewById(R.id.tvTime);
        tvNetSpeed = findViewById(R.id.tvNetSpeed);

        initEpgDateView();
        initEpgListView();
        initLiveChannelList();

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                mHandler.removeCallbacks(mHideChannelInfoRun);
                mHandler.postDelayed(mHideChannelInfoRun, LiveConstants.AUTO_HIDE_CHANNEL_INFO_MS);
                if (playbackManager == null) return;
                long duration = playbackManager.getDuration();
                long newPosition = (duration * progress) / seekBar.getMax();
                if (mCurrentTime != null) mCurrentTime.setText(stringForTimeVod((int) newPosition));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { mIsDragging = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                mIsDragging = false;
                if (playbackManager == null) return;
                long duration = playbackManager.getDuration();
                long newPosition = (duration * seekBar.getProgress()) / seekBar.getMax();
                playbackManager.seekTo((int) newPosition);
            }
        });
        mBack.setOnClickListener(v -> finish());

        mHandler.postDelayed(mUpdateLayout, 255);
    }

    // ... 以下为原有方法（getTvLogo, showChannelInfo, playChannel, dispatchKeyEvent 等）
    // 由于篇幅限制，这里不能全部列出。您需要将原文件中的所有方法完整保留，并在此基础上：
    // 1. 在 dispatchKeyEvent 开头添加：
    //    if (controlPanel != null && controlPanel.isShowing()) { return true; }
    // 2. 在 onBackPressed 开头添加：
    //    if (controlPanel != null && controlPanel.isShowing()) { controlPanel.hide(); return; }
    // 3. 在 onPause 中添加： if (controlPanel != null) controlPanel.hide();
    // 
    // 请确保原文件中的所有其他方法（如 playNext, playPrevious, initEpgListView 等）都原样保留。
    // 为节省篇幅，此处省略重复代码。实际使用时，请将上述修改合并到您的原文件中。

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (controlPanel != null && controlPanel.isShowing()) {
            return true;
        }
        // 原有 dispatchKeyEvent 代码保持不变
        // ... (省略)
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (controlPanel != null && controlPanel.isShowing()) {
            controlPanel.hide();
            return;
        }
        // 原有 onBackPressed 代码
        // ...
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (controlPanel != null) controlPanel.hide();
        // 原有 onPause 代码
        // ...
    }

    // 其他原有方法保持不变...
}
