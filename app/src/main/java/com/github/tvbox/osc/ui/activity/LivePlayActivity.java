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
                if (isNeedInputPassword(grpIndx)) {
                    showPasswordDialogForGroup(grpIndx, chaIndx - 1);
                } else {
                    playChannel(grpIndx, chaIndx - 1, false);
                }
            }
            selectedChannelNumber = 0;
        }
    };

    // ========== 工具方法 ==========
    public void getEpg(Date date) {
        if (currentLiveChannelItem == null || currentLiveChannelItem.getChannelName() == null) return;
        final String channelName = currentLiveChannelItem.getChannelName();
        final Date requestDate = date;
        SimpleDateFormat sdf = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
        final String dateStr = sdf.format(date);
        ArrayList<Epginfo> cached = epgCacheHelper.getCachedEpg(channelName, dateStr);
        if (cached != null && !cached.isEmpty()) {
            showEpg(date, cached);
            showBottomEpg();
            return;
        }
        epgCacheHelper.requestEpg(channelName, date, true, new EpgCacheHelper.EpgCallback() {
            @Override
            public void onSuccess(String channelName, Date date, ArrayList<Epginfo> epgList) {
                if (currentLiveChannelItem != null && channelName.equals(currentLiveChannelItem.getChannelName()) && date.equals(requestDate)) {
                    showEpg(date, epgList);
                    showBottomEpg();
                }
            }
            @Override
            public void onFailure(String channelName, Date date, Exception e) {
                if (currentLiveChannelItem != null && channelName.equals(currentLiveChannelItem.getChannelName()) && date.equals(requestDate)) {
                    showEpg(date, new ArrayList<>());
                    showBottomEpg();
                }
            }
        });
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

                if (!isChangeSource) {
                    if (channelListPanel != null) {
                        channelListPanel.updateCurrentSelection(currentChannelGroupIndex, currentLiveChannelIndex);
                    }
                }
                showChannelInfo();
            }

            @Override
            public void onAutoSwitchToNextChannel(boolean reverse) {
                if (reverse) {
                    playPreviousSilent();
                } else {
                    playNextSilent();
                }
            }

            @Override
            public void onTimeoutReplay() {
                replayChannel();
            }

            @Override
            public void onShiyiModeChanged(boolean isShiyi, String timeRange) {
                showBottomEpg();
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
                    if (playbackManager != null) playbackManager.changeScale(scaleIndex);
                    Toast.makeText(LivePlayActivity.this, "画面比例已应用", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(LivePlayActivity.this, "设置失败", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onPlayerTypeChanged(int typeIndex) {
                try {
                    if (playbackManager != null) playbackManager.changePlayerType(typeIndex);
                    Toast.makeText(LivePlayActivity.this, "解码方式已应用", Toast.LENGTH_SHORT).show();
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

        // 左侧列表面板
        LinearLayout tvLeftChannelListLayout = findViewById(R.id.tvLeftChannelListLayout);
        TvRecyclerView mGroupGridView = findViewById(R.id.mGroupGridView);
        TvRecyclerView mChannelGridView = findViewById(R.id.mChannelGridView);
        channelListPanel = new LiveChannelListPanel(this, mHandler, tvLeftChannelListLayout, mGroupGridView, mChannelGridView,
                mGroupEPG, mDivLeft, mDivRight, mEpgDateGridView, mEpgInfoGridView);
        channelListPanel.setListener(this);
        channelListPanel.init();

        tvTime = findViewById(R.id.tvTime);
        tvNetSpeed = findViewById(R.id.tvNetSpeed);

        initEpgDateView();
        initEpgListView();
        initLiveChannelList();

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                mHandler.removeCallbacks(mHideChannelInfoRun);
                mHandler.postDelayed(mHideChannelInfoRun, LiveConstants.AUTO_HIDE_CHANNEL_INFO_MS);
                long duration = playbackManager.getDuration();
                long newPosition = (duration * progress) / seekBar.getMax();
                if (mCurrentTime != null) mCurrentTime.setText(stringForTimeVod((int) newPosition));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { mIsDragging = true; }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mIsDragging = false;
                long duration = playbackManager.getDuration();
                long newPosition = (duration * seekBar.getProgress()) / seekBar.getMax();
                playbackManager.seekTo((int) newPosition);
            }
        });
        mBack.setOnClickListener(v -> finish());

        mHandler.postDelayed(mUpdateLayout, 255);
    }

    // ========== ChannelListListener 接口实现 ==========
    @Override
    public List<LiveChannelGroup> getChannelGroups() {
        return liveChannelGroupList;
    }

    @Override
    public List<LiveChannelItem> getLiveChannels(int groupIndex) {
        if (groupIndex >= liveChannelGroupList.size()) return new ArrayList<>();
        if (!isNeedInputPassword(groupIndex)) {
            return liveChannelGroupList.get(groupIndex).getLiveChannels();
        }
        return new ArrayList<>();
    }

    @Override
    public int getCurrentGroupIndex() {
        return currentChannelGroupIndex;
    }

    @Override
    public int getCurrentChannelIndex() {
        return currentLiveChannelIndex;
    }

    @Override
    public void updateCurrentChannel(int groupIndex, int channelIndex) {
        currentChannelGroupIndex = groupIndex;
        currentLiveChannelIndex = channelIndex;
        if (groupIndex >= 0 && groupIndex < liveChannelGroupList.size()) {
            List<LiveChannelItem> channels = getLiveChannels(groupIndex);
            if (channelIndex >= 0 && channelIndex < channels.size()) {
                currentLiveChannelItem = channels.get(channelIndex);
            }
        }
    }

    @Override
    public boolean isNeedInputPassword(int groupIndex) {
        if (groupIndex >= liveChannelGroupList.size()) return false;
        return !liveChannelGroupList.get(groupIndex).getGroupPassword().isEmpty() && !isPasswordConfirmed(groupIndex);
    }

    @Override
    public void onGroupSelected(int groupIndex) {
        if (isNeedInputPassword(groupIndex)) {
            showPasswordDialogForGroup(groupIndex, -1);
        } else {
            if (channelListPanel != null) {
                channelListPanel.loadGroup(groupIndex, liveChannelGroupList);
            }
        }
    }

    @Override
    public void onChannelSelected(int groupIndex, int channelIndex) {
        playChannel(groupIndex, channelIndex, false);
    }

    @Override
    public void onEpgModeChanged(boolean isEpg) {
        if (isEpg && currentLiveChannelItem != null) {
            epgDateAdapter.setSelectedIndex(6);
            getEpg(epgDateAdapter.getData().get(6).getDateParamVal());
        } else if (!isEpg) {
            resetShiyiMode();
        }
    }

    @Override
    public void onEpgItemClicked(Epginfo epgItem, int position, int selectedDateIndex) {
        if (currentLiveChannelItem == null || epgItem == null) return;
        Date date = epgDateAdapter.getData().get(selectedDateIndex).getDateParamVal();
        SimpleDateFormat dateFormat = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD_NUM);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        String targetDate = dateFormat.format(date);
        String[] shiyiTimes = playbackManager.buildShiyiTimes(targetDate, epgItem.originStart, epgItem.originEnd);
        String shiyiStartdate = shiyiTimes[0];
        String shiyiEnddate = shiyiTimes[1];
        Date now = new Date();
        if (now.compareTo(epgItem.startdateTime) < 0) {
            Toast.makeText(this, "未到播放时间", Toast.LENGTH_SHORT).show();
            return;
        }
        epgListAdapter.setSelectedEpgIndex(position);
        if (now.compareTo(epgItem.startdateTime) >= 0 && now.compareTo(epgItem.enddateTime) <= 0) {
            playbackManager.playChannel(currentLiveChannelItem, false);
            epgListAdapter.setShiyiSelection(-1, false, timeFormat.format(date));
            showBottomEpg();
        } else {
            if (!playbackManager.isValidShiyiTime(shiyiStartdate, shiyiEnddate)) {
                Toast.makeText(this, "无效的回放时间", Toast.LENGTH_SHORT).show();
                return;
            }
            String shiyiRange = shiyiStartdate + "-" + shiyiEnddate;
            playbackManager.playShiyi(shiyiRange);
            showBottomEpg();
            epgListAdapter.setShiyiSelection(position, true, timeFormat.format(date));
            epgListAdapter.notifyDataSetChanged();
            mEpgInfoGridView.setSelectedPosition(position);
            mEpgInfoGridView.setSelection(position);
        }
    }

    // ========== UI 显示 ==========
    private void showChannelList() {
        mBack.setVisibility(View.INVISIBLE);
        if (tvBottomLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelInfoRun);
            mHandler.post(mHideChannelInfoRun);
        } else if (settingsPanel != null && settingsPanel.isShowing()) {
            settingsPanel.hide();
        } else if (channelListPanel != null) {
            if (channelListPanel.isShowing()) {
                channelListPanel.hide();
                mHandler.removeCallbacks(tv_sys_timeRunnable);
                return;
            }
            channelListPanel.show();
            mHandler.post(tv_sys_timeRunnable);
            mHandler.postDelayed(mUpdateLayout, 255);
        }
    }

    public void divLoadEpgR(View view) {
        if (settingsPanel != null && settingsPanel.isShowing()) settingsPanel.hide();
        if (channelListPanel != null) {
            if (channelListPanel.isShowing() && channelListPanel.isEpgMode()) {
                channelListPanel.hide();
            } else {
                channelListPanel.showEpgMode();
                if (!channelListPanel.isShowing()) {
                    channelListPanel.show();
                    mHandler.post(tv_sys_timeRunnable);
                }
            }
        }
        mHandler.postDelayed(mUpdateLayout, 255);
    }

    public void divLoadEpgL(View view) {
        if (channelListPanel != null) {
            if (channelListPanel.isShowing() && !channelListPanel.isEpgMode()) {
                channelListPanel.hide();
            } else {
                channelListPanel.showChannelMode();
                if (!channelListPanel.isShowing()) {
                    channelListPanel.show();
                    mHandler.post(tv_sys_timeRunnable);
                }
            }
        }
        mHandler.postDelayed(mUpdateLayout, 255);
    }

    private void showSettingGroup() {
        mBack.setVisibility(View.INVISIBLE);
        if (channelListPanel != null && channelListPanel.isShowing()) channelListPanel.hide();
        if (tvBottomLayout.getVisibility() == View.VISIBLE) mHandler.post(mHideChannelInfoRun);
        if (settingsPanel != null) {
            if (settingsPanel.isShowing()) settingsPanel.hide();
            else settingsPanel.show();
        }
    }

    private void showChannelInfo() {
        if (supportsTouch()) mBack.setVisibility(View.VISIBLE);
        if (tvBottomLayout.getVisibility() == View.GONE || tvBottomLayout.getVisibility() == View.INVISIBLE) {
            tvBottomLayout.setVisibility(View.VISIBLE);
            tvBottomLayout.setTranslationY(tvBottomLayout.getHeight() / 2);
            tvBottomLayout.setAlpha(0.0f);
            tvBottomLayout.animate().alpha(1.0f).setDuration(250).setInterpolator(new DecelerateInterpolator())
                    .translationY(0).setListener(null);
        }
        if (currentLiveChannelItem != null) {
            showBottomEpg();
        }
        mHandler.removeCallbacks(mHideChannelInfoRun);
        mHandler.postDelayed(mHideChannelInfoRun, LiveConstants.AUTO_HIDE_CHANNEL_INFO_MS);
        mHandler.postDelayed(mUpdateLayout, 255);
    }

    private void toggleChannelInfo() {
        if (tvBottomLayout.getVisibility() == View.INVISIBLE) {
            showChannelInfo();
        } else {
            mBack.setVisibility(View.INVISIBLE);
            mHandler.removeCallbacks(mHideChannelInfoRun);
            mHandler.post(mHideChannelInfoRun);
            mHandler.postDelayed(mUpdateLayout, 255);
        }
    }

    private void showEpg(Date date, ArrayList<Epginfo> arrayList) {
        if (arrayList != null && !arrayList.isEmpty()) {
            epgdata = arrayList;
            if (currentLiveChannelItem != null) {
                epgListAdapter.CanBack(currentLiveChannelItem.getinclude_back());
            }
            epgListAdapter.setNewData(epgdata);
            int i = -1;
            Date now = new Date();
            for (int size = epgdata.size() - 1; size >= 0; size--) {
                if (now.compareTo(epgdata.get(size).startdateTime) >= 0) {
                    i = size;
                    break;
                }
            }
            // 修复：时移模式下不自动高亮直播段，保留回放高亮
            boolean isShiyi = playbackManager != null && playbackManager.isShiyiMode();
            if (!isShiyi && i >= 0 && now.compareTo(epgdata.get(i).enddateTime) <= 0) {
                mEpgInfoGridView.setSelectedPosition(i);
                mEpgInfoGridView.setSelection(i);
                epgListAdapter.setSelectedEpgIndex(i);
                int finalI = i;
                mEpgInfoGridView.post(() -> mEpgInfoGridView.smoothScrollToPosition(finalI));
            }
        } else {
            Epginfo epgbcinfo = new Epginfo(date, LiveConstants.NO_PROGRAM, date,
                    LiveConstants.DEFAULT_START_TIME, LiveConstants.DEFAULT_END_TIME, 0);
            arrayList = new ArrayList<>();
            arrayList.add(epgbcinfo);
            epgdata = arrayList;
            epgListAdapter.setNewData(epgdata);
        }
    }

    private void showBottomEpg() {
        // 时移模式下也刷新底部 EPG（显示实时直播信息），不提前返回
        if (currentLiveChannelItem == null || currentLiveChannelItem.getChannelName() == null) {
            tv_curr_name.setText(LiveConstants.NO_PROGRAM);
            tv_next_name.setText("");
            return;
        }

        showChannelInfo();
        String channelName = currentLiveChannelItem.getChannelName();
        Date selectedDate = epgDateAdapter.getSelectedIndex() < 0
                ? new Date()
                : epgDateAdapter.getData().get(epgDateAdapter.getSelectedIndex()).getDateParamVal();

        String dateStr = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD).format(selectedDate);
        ArrayList<Epginfo> currentEpgData = epgCacheHelper.getCachedEpg(channelName, dateStr);

        if (currentEpgData != null && !currentEpgData.isEmpty()) {
            Date now = new Date();
            boolean found = false;
            for (int size = currentEpgData.size() - 1; size >= 0; size--) {
                Epginfo epg = currentEpgData.get(size);
                if (now.compareTo(epg.startdateTime) >= 0 && now.compareTo(epg.enddateTime) <= 0) {
                    tv_curr_time.setText(epg.start + " - " + epg.end);
                    tv_curr_name.setText(epg.title);
                    if (size != currentEpgData.size() - 1) {
                        Epginfo next = currentEpgData.get(size + 1);
                        tv_next_time.setText(next.start + " - " + next.end);
                        tv_next_name.setText(next.title);
                    } else {
                        tv_next_time.setText(LiveConstants.DEFAULT_START_TIME + " - " + LiveConstants.DEFAULT_END_TIME);
                        tv_next_name.setText(LiveConstants.NO_INFO);
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                tv_curr_name.setText(LiveConstants.NO_PROGRAM);
                tv_next_name.setText("");
            }

            epgdata = currentEpgData;
            if (epgListAdapter != null) {
                if (currentLiveChannelItem != null) {
                    epgListAdapter.CanBack(currentLiveChannelItem.getinclude_back());
                }
                epgListAdapter.setNewData(currentEpgData);
            }
        } else {
            tv_curr_name.setText(LiveConstants.NO_PROGRAM);
            tv_next_name.setText("");
        }
    }

    void getTvLogo(String channelName, String logoUrl) {
        RequestOptions options = new RequestOptions().diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).placeholder(R.drawable.img_logo_placeholder);
        Glide.with(App.getInstance()).load(logoUrl).apply(options).into(tv_logo);
    }

    // ========== 播放控制 ==========
    private boolean playChannel(int channelGroupIndex, int liveChannelIndex, boolean changeSource) {
        if (playbackManager != null && playbackManager.isShiyiMode()) {
            resetShiyiMode();
        }
        if (epgListAdapter != null) {
            epgListAdapter.setShiyiSelection(-1, false, null);
        }

        if (!changeSource && epgDateAdapter != null) {
            epgDateAdapter.setSelectedIndex(6);
        }

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

        if (!changeSource) {
            currentChannelGroupIndex = channelGroupIndex;
            currentLiveChannelIndex = liveChannelIndex;
            currentLiveChannelItem = channels.get(currentLiveChannelIndex);
            Hawk.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem.getChannelName());
            HawkUtils.setLastLiveChannelGroup(liveChannelGroupList.get(currentChannelGroupIndex).getGroupName());
            if (settingsPanel != null) {
                settingsPanel.updateSourceList(currentLiveChannelItem);
                settingsPanel.setCurrentSourceIndex(currentLiveChannelItem.getSourceIndex());
                // 同步设置面板的解码和比例高亮
                if (playbackManager != null) {
                    settingsPanel.syncScale(playbackManager.getCurrentScale());
                    settingsPanel.syncPlayerType(playbackManager.getCurrentPlayerType());
                }
            }
            if (channelListPanel != null) {
                channelListPanel.updateCurrentSelection(currentChannelGroupIndex, currentLiveChannelIndex);
            }
        }

        playbackManager.playChannel(currentLiveChannelItem, changeSource);
        getEpg(new Date());
        mHandler.post(tv_sys_timeRunnable);

        // 预加载当前频道的所有日期 EPG（高优先级）
        if (epgCacheHelper != null && currentLiveChannelItem != null) {
            epgCacheHelper.preloadCurrentChannel(currentLiveChannelItem.getChannelName());
        }

        return true;
    }

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

    private void playNextSilent() {
        if (!isCurrentLiveChannelValid()) return;
        Integer[] groupChannelIndex = getNextChannel(1);
        if (groupChannelIndex[0] >= 0 && groupChannelIndex[1] >= 0) {
            playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
        }
    }

    private void playPreviousSilent() {
        if (!isCurrentLiveChannelValid()) return;
        Integer[] groupChannelIndex = getNextChannel(-1);
        if (groupChannelIndex[0] >= 0 && groupChannelIndex[1] >= 0) {
            playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
        }
    }

    public void playPreSource() {
        if (playbackManager != null) playbackManager.playPreSource();
    }

    public void playNextSource() {
        if (playbackManager != null) playbackManager.playNextSource();
    }

    private void replayChannel() {
        if (currentLiveChannelItem == null || currentChannelGroupIndex < 0) return;

        List<LiveChannelItem> channels = getLiveChannels(currentChannelGroupIndex);
        if (currentLiveChannelIndex < 0 || currentLiveChannelIndex >= channels.size()) return;

        currentLiveChannelItem = channels.get(currentLiveChannelIndex);

        playbackManager.playChannel(currentLiveChannelItem, false);
        getEpg(new Date());
        showChannelInfo();
        mHandler.post(tv_sys_timeRunnable);
    }

    // ========== 初始化 ==========
    private void initEpgListView() {
        mEpgInfoGridView.setHasFixedSize(true);
        mEpgInfoGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        epgListAdapter = new LiveEpgAdapter();
        mEpgInfoGridView.setAdapter(epgListAdapter);

        TvRecyclerView.OnItemListener listener = new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                epgListAdapter.setFocusedEpgIndex(-1);
            }
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                epgListAdapter.setFocusedEpgIndex(position);
                if (channelListPanel != null) channelListPanel.resetHideTimer();
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                Epginfo item = epgListAdapter.getItem(position);
                int dateIndex = epgDateAdapter.getSelectedIndex();
                if (channelListPanel != null) {
                    channelListPanel.notifyEpgClicked(item, position, dateIndex);
                }
            }
        };
        mEpgInfoGridView.setOnItemListener(listener);

        mEpgInfoGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE && channelListPanel != null) {
                    channelListPanel.resetHideTimer();
                }
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
        mEpgDateGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                epgDateAdapter.setFocusedIndex(-1);
            }
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                epgDateAdapter.setFocusedIndex(position);
                if (channelListPanel != null) channelListPanel.resetHideTimer();
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (channelListPanel != null) channelListPanel.resetHideTimer();
                epgDateAdapter.setSelectedIndex(position);
                getEpg(epgDateAdapter.getData().get(position).getDateParamVal());
            }
        });
        epgDateAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            if (channelListPanel != null) channelListPanel.resetHideTimer();
            epgDateAdapter.setSelectedIndex(position);
            getEpg(epgDateAdapter.getData().get(position).getDateParamVal());
        });
        epgDateAdapter.setSelectedIndex(6);

        mEpgDateGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE && channelListPanel != null) {
                    channelListPanel.resetHideTimer();
                }
            }
        });
    }

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
        if (settingsPanel != null) {
            settingsPanel.setCurrentScale(playbackManager.getCurrentScale());
            settingsPanel.setCurrentPlayerType(playbackManager.getCurrentPlayerType());
        }
        showTime();
        showNetSpeed();
        if (channelListPanel != null) {
            channelListPanel.refreshFull(liveChannelGroupList, currentChannelGroupIndex, currentLiveChannelIndex);
        }
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
            if (lastChannelGroupIndex >= 0 && lastLiveChannelIndex >= 0 &&
                    lastChannelGroupIndex < liveChannelGroupList.size() &&
                    lastLiveChannelIndex < liveChannelGroupList.get(lastChannelGroupIndex).getLiveChannels().size()) {
                if (!isNeedInputPassword(lastChannelGroupIndex)) {
                    playChannel(lastChannelGroupIndex, lastLiveChannelIndex, false);
                } else {
                    showChannelList();
                }
            } else if (!liveChannelGroupList.isEmpty() && !liveChannelGroupList.get(0).getLiveChannels().isEmpty()) {
                if (!isNeedInputPassword(0)) {
                    playChannel(0, 0, false);
                } else {
                    showChannelList();
                }
            }
            // 延迟预加载其他频道 EPG（低优先级）
            if (epgCacheHelper != null && currentLiveChannelItem != null) {
                List<String> allChannelNames = new ArrayList<>();
                for (LiveChannelGroup group : liveChannelGroupList) {
                    for (LiveChannelItem channel : group.getLiveChannels()) {
                        allChannelNames.add(channel.getChannelName());
                    }
                }
                final String currentName = currentLiveChannelItem.getChannelName();
                mHandler.postDelayed(() -> epgCacheHelper.preloadOtherChannels(allChannelNames, currentName), LiveConstants.PRELOAD_DELAY_MS);
            }
        }
    }

    // ========== 辅助方法 ==========
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

    private boolean isPasswordConfirmed(int groupIndex) {
        if (Hawk.get(HawkConfig.LIVE_SKIP_PASSWORD, false)) return true;
        for (Integer confirmedNum : channelGroupPasswordConfirmed) {
            if (confirmedNum == groupIndex) return true;
        }
        return false;
    }

    private void showPasswordDialogForGroup(int groupIndex, int targetChannelIndex) {
        LivePasswordDialog dialog = new LivePasswordDialog(this);
        dialog.setOnListener(new LivePasswordDialog.OnListener() {
            @Override
            public void onChange(String password) {
                if (password.equals(liveChannelGroupList.get(groupIndex).getGroupPassword())) {
                    channelGroupPasswordConfirmed.add(groupIndex);
                    if (channelListPanel != null) {
                        channelListPanel.loadGroup(groupIndex, liveChannelGroupList);
                    }
                    int finalChannelIndex = targetChannelIndex;
                    if (finalChannelIndex < 0) finalChannelIndex = 0;
                    List<LiveChannelItem> channels = getLiveChannels(groupIndex);
                    if (finalChannelIndex < channels.size()) {
                        playChannel(groupIndex, finalChannelIndex, false);
                    } else if (!channels.isEmpty()) {
                        playChannel(groupIndex, 0, false);
                    }
                } else {
                    Toast.makeText(App.getInstance(), "密码错误", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onCancel() {
                if (channelListPanel != null) {
                    channelListPanel.refreshFull(liveChannelGroupList, currentChannelGroupIndex, currentLiveChannelIndex);
                    channelListPanel.resetHideTimer();
                }
            }
        });
        dialog.show();
    }

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

    private void numericKeyDown(int digit) {
        selectedChannelNumber = selectedChannelNumber * 10 + digit;
        tvSelectedChannel.setText(Integer.toString(selectedChannelNumber));
        tvSelectedChannel.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mPlaySelectedChannel);
        mHandler.postDelayed(mPlaySelectedChannel, LiveConstants.NUMERIC_TIMEOUT_MS);
    }

    private boolean isListOrSettingLayoutVisible() {
        return (channelListPanel != null && channelListPanel.isShowing()) ||
                (settingsPanel != null && settingsPanel.isShowing());
    }

    private boolean isCurrentLiveChannelValid() {
        return currentLiveChannelItem != null;
    }

    private void resetShiyiMode() {
        if (playbackManager != null) {
            playbackManager.resetShiyiMode();
        }
        showBottomEpg();
    }

    // ========== 辅助方法 ==========
    private boolean handleSingleTap(MotionEvent e) {
        boolean leftShowing = (channelListPanel != null && channelListPanel.isShowing());
        boolean bottomShowing = (tvBottomLayout.getVisibility() == View.VISIBLE);
        boolean rightShowing = (settingsPanel != null && settingsPanel.isShowing());

        if (leftShowing || bottomShowing || rightShowing) {
            if (leftShowing) channelListPanel.hide();
            if (bottomShowing) {
                mHandler.removeCallbacks(mHideChannelInfoRun);
                mHandler.post(mHideChannelInfoRun);
            }
            if (rightShowing) settingsPanel.hide();
            return true;
        }

        int fiveScreen = PlayerUtils.getScreenWidth(this, true) / 5;
        float x = e.getX();
        if (x > 0 && x < (fiveScreen * 2)) {
            showChannelList();
        } else if (x > (fiveScreen * 2) && x < (fiveScreen * 3)) {
            toggleChannelInfo();
        } else if (x > (fiveScreen * 3)) {
            showSettingGroup();
        }
        return true;
    }

    private void updateUIForPlayState(int playState) {
        if (playState == VideoView.STATE_PREPARED) {
            long duration = playbackManager.getDuration();
            if (duration > 0) {
                isVOD = true;
                llSeekBar.setVisibility(View.VISIBLE);
                mSeekBar.setMax((int) duration);
                mSeekBar.setProgress(0);
                mTotalTime.setText(stringForTimeVod((int) duration));
            } else {
                isVOD = false;
                llSeekBar.setVisibility(View.GONE);
            }
        }
    }

    private void updateChannelUI(LiveChannelItem channel) {
        tv_channelname.setText(channel.getChannelName());
        tv_channelnum.setText("" + channel.getChannelNum());
        tv_source.setText(channel.getSourceNum() <= 0 ? "1/1"
                : "线路 " + (channel.getSourceIndex() + 1) + "/" + channel.getSourceNum());

        if (settingsPanel != null) {
            settingsPanel.updateSourceList(channel);
            settingsPanel.setCurrentSourceIndex(channel.getSourceIndex());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_LIVEPLAY_UPDATE) {
            Bundle bundle = (Bundle) event.obj;
            int groupIndex = bundle.getInt("groupIndex", 0);
            int channelIndex = bundle.getInt("channelIndex", 0);
            if (isNeedInputPassword(groupIndex)) {
                showPasswordDialogForGroup(groupIndex, channelIndex);
            } else {
                playChannel(groupIndex, channelIndex, false);
            }
        }
    }

    // ========== 生命周期 ==========
    @Override
    public void onUserLeaveHint() {
        if (supportsPiPMode() && PiPON) {
            if (channelListPanel != null) channelListPanel.hide();
            if (settingsPanel != null) settingsPanel.hide();
            mHandler.post(mHideChannelInfoRun);
            enterPictureInPictureMode();
        }
    }

    @Override
    public void onBackPressed() {
        if (playbackManager != null) playbackManager.cancelAllTimeouts();
        if (channelListPanel != null && channelListPanel.isShowing()) {
            channelListPanel.hide();
        } else if (settingsPanel != null && settingsPanel.isShowing()) {
            settingsPanel.hide();
        } else if (tvBottomLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelInfoRun);
            mHandler.post(mHideChannelInfoRun);
        } else {
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
        if (playbackManager != null) playbackManager.resume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        onStopCalled = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playbackManager != null) {
            if (supportsPiPMode()) {
                if (isInPictureInPictureMode()) playbackManager.resume();
                else playbackManager.pause();
            } else {
                playbackManager.pause();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (supportsPiPMode() && !isInPictureInPictureMode() && onStopCalled) {
            if (playbackManager != null) playbackManager.release();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playbackManager != null) playbackManager.release();
        if (epgCacheHelper != null) epgCacheHelper.destroy();
        if (settingsPanel != null) settingsPanel.destroy();
        if (channelListPanel != null) channelListPanel.destroy();
        mHandler.removeCallbacks(tv_sys_timeRunnable);
        mHandler.removeCallbacks(mUpdateTimeRun);
        mHandler.removeCallbacks(mUpdateNetSpeedRun);
        mHandler.removeCallbacks(mHideChannelInfoRun);
        mHandler.removeCallbacks(mPlaySelectedChannel);
    }
}
