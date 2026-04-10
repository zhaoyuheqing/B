package com.github.tvbox.osc.ui.panel;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.player.controller.LivePlaybackManager;
import com.github.tvbox.osc.util.EpgCacheHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class LiveControlPanel {
    private static final String TAG = "LiveControlPanel";
    private final Context context;
    private final LivePlaybackManager playbackManager;
    private final EpgCacheHelper epgCacheHelper;
    private final Handler handler;
    private final FrameLayout container;
    private View panelView;
    private SeekBar seekBar;
    private TextView tvProgramName;
    private TextView btnSpeed;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private TextView btnPlayPause;
    private TextView btnRewind10;
    private TextView btnForward10;
    private TextView tvDateWeek;
    private TextView tvCurrentEpg;
    private boolean isVisible = false;
    private final Runnable autoHideRunnable = this::hide;

    public LiveControlPanel(Context context, FrameLayout container, LivePlaybackManager playbackManager, EpgCacheHelper epgCacheHelper, Handler handler) {
        this.context = context;
        this.container = container;
        this.playbackManager = playbackManager;
        this.epgCacheHelper = epgCacheHelper;
        this.handler = handler;
        initView();
    }

    private void initView() {
        LayoutInflater inflater = LayoutInflater.from(context);
        panelView = inflater.inflate(R.layout.layout_live_control_panel, container, false);
        seekBar = panelView.findViewById(R.id.control_seekbar);
        tvProgramName = panelView.findViewById(R.id.control_program_name);
        btnSpeed = panelView.findViewById(R.id.control_speed_btn);
        tvCurrentTime = panelView.findViewById(R.id.control_current_time);
        tvTotalTime = panelView.findViewById(R.id.control_total_time);
        btnPlayPause = panelView.findViewById(R.id.control_play_pause);
        btnRewind10 = panelView.findViewById(R.id.control_rewind_10);
        btnForward10 = panelView.findViewById(R.id.control_forward_10);
        tvDateWeek = panelView.findViewById(R.id.control_date_week);
        tvCurrentEpg = panelView.findViewById(R.id.control_current_epg);

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnRewind10.setOnClickListener(v -> onSeekRelative(-10));
        btnForward10.setOnClickListener(v -> onSeekRelative(10));
        btnSpeed.setOnClickListener(v -> cycleSpeed());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateTimeByProgress(progress);
                    if (playbackManager.getPlaybackType() == 0) {
                        long targetTime = getLiveTimeFromProgress(progress);
                        updateEpgByTime(targetTime);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(autoHideRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                if (playbackManager.getPlaybackType() == 0) {
                    long targetTime = getLiveTimeFromProgress(progress);
                    playbackManager.seekToLiveTime(targetTime);
                } else {
                    long targetPosMs = (long) progress * 1000L;
                    playbackManager.seekTo((int) targetPosMs);
                }
                handler.removeCallbacks(autoHideRunnable);
                handler.postDelayed(autoHideRunnable, LiveConstants.CONTROL_PANEL_AUTO_HIDE_MS);
            }
        });

        container.addView(panelView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        panelView.setVisibility(View.GONE);
        Log.d(TAG, "Control panel initialized");
    }

    // ========== 直播模式进度条辅助方法 ==========
    // 根据进度值（秒）计算直播模式下的绝对时间（毫秒）
    // progress: 0 表示当前时间（最右端），maxSec 表示24小时前（最左端）
    private long getLiveTimeFromProgress(int progressSec) {
        long maxSec = LiveConstants.LIVE_REPLAY_WINDOW_MS / 1000;
        long now = System.currentTimeMillis();
        long offsetSec = maxSec - progressSec;
        return now - offsetSec * 1000;
    }

    // 根据绝对时间（毫秒）计算进度条位置（直播模式）
    private int getProgressFromLiveTime(long targetTimeMs) {
        long now = System.currentTimeMillis();
        long maxSec = LiveConstants.LIVE_REPLAY_WINDOW_MS / 1000;
        long diffSec = (now - targetTimeMs) / 1000;
        if (diffSec < 0) diffSec = 0;
        if (diffSec > maxSec) diffSec = maxSec;
        return (int) (maxSec - diffSec);
    }

    // ========== 公共方法 ==========
    public void show() {
        if (isVisible) return;
        updateUI();
        container.setVisibility(View.VISIBLE);
        container.bringToFront();
        panelView.setVisibility(View.VISIBLE);
        isVisible = true;
        handler.removeCallbacks(autoHideRunnable);
        handler.postDelayed(autoHideRunnable, LiveConstants.CONTROL_PANEL_AUTO_HIDE_MS);
    }

    public void hide() {
        if (!isVisible) return;
        panelView.setVisibility(View.GONE);
        container.setVisibility(View.GONE);
        isVisible = false;
        handler.removeCallbacks(autoHideRunnable);
    }

    public boolean isShowing() {
        return isVisible;
    }

    public void refresh() {
        if (isVisible) updateUI();
    }

    // ========== UI 更新 ==========
    private void updateUI() {
        String programName = playbackManager.getCurrentChannel() != null ?
                playbackManager.getCurrentChannel().getChannelName() : "直播";
        tvProgramName.setText(programName);

        float speed = playbackManager.getCurrentSpeed();
        btnSpeed.setText(String.format(Locale.US, "倍速 %.1fx", speed));

        if (playbackManager.getPlaybackType() == 0) {
            // 直播模式
            long now = System.currentTimeMillis();
            long liveTime = playbackManager.getCurrentLiveTime();
            long maxSec = LiveConstants.LIVE_REPLAY_WINDOW_MS / 1000;
            seekBar.setMax((int) maxSec);
            int progress = getProgressFromLiveTime(liveTime);
            seekBar.setProgress(progress);
            // 更新时间显示
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            tvCurrentTime.setText(sdf.format(new Date(now)));      // 右侧当前时间
            tvTotalTime.setText(sdf.format(new Date(liveTime)));   // 左侧回放点时间
            // 更新节目信息
            updateEpgByTime(liveTime);
        } else {
            // 点播/回放模式
            long duration = playbackManager.getDuration();
            long currentPos = playbackManager.getCurrentPosition();
            seekBar.setMax((int) (duration / 1000));
            seekBar.setProgress((int) (currentPos / 1000));
            tvCurrentTime.setText(formatTime(currentPos));
            tvTotalTime.setText(formatTime(duration));
        }

        btnPlayPause.setText(playbackManager.isPlaying() ? "暂停" : "播放");

        SimpleDateFormat dateFormat = new SimpleDateFormat("MM月dd日 EEEE", Locale.CHINA);
        tvDateWeek.setText(dateFormat.format(new Date()));
    }

    private void updateTimeByProgress(int progressSec) {
        if (playbackManager.getPlaybackType() == 0) {
            long targetTime = getLiveTimeFromProgress(progressSec);
            long now = System.currentTimeMillis();
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            tvCurrentTime.setText(sdf.format(new Date(now)));
            tvTotalTime.setText(sdf.format(new Date(targetTime)));
        } else {
            long progressMs = progressSec * 1000L;
            tvCurrentTime.setText(formatTime(progressMs));
            long totalMs = playbackManager.getDraggableRange();
            tvTotalTime.setText(formatTime(totalMs));
        }
    }

    private void updateEpgByTime(long absoluteTimeMs) {
        if (playbackManager.getCurrentChannel() == null || epgCacheHelper == null) {
            tvCurrentEpg.setText("暂无节目信息");
            return;
        }
        String channelName = playbackManager.getCurrentChannel().getChannelName();
        SimpleDateFormat sdf = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD);
        String dateStr = sdf.format(new Date(absoluteTimeMs));
        ArrayList<Epginfo> epgList = epgCacheHelper.getCachedEpg(channelName, dateStr);
        if (epgList == null || epgList.isEmpty()) {
            tvCurrentEpg.setText("暂无节目信息");
            return;
        }
        Date targetDate = new Date(absoluteTimeMs);
        for (Epginfo epg : epgList) {
            if (targetDate.after(epg.startdateTime) && targetDate.before(epg.enddateTime)) {
                tvCurrentEpg.setText("正在播放：" + epg.title + " " + epg.start + "-" + epg.end);
                return;
            }
        }
        tvCurrentEpg.setText("暂无节目信息");
    }

    private String formatTime(long ms) {
        int seconds = (int) (ms / 1000);
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }

    // ========== 交互事件 ==========
    private void togglePlayPause() {
        if (playbackManager.isPlaying()) {
            playbackManager.pause();
        } else {
            playbackManager.resume();
        }
        btnPlayPause.setText(playbackManager.isPlaying() ? "暂停" : "播放");
    }

    private void cycleSpeed() {
        float[] speeds = LiveConstants.SPEEDS;
        float current = playbackManager.getCurrentSpeed();
        int index = 0;
        for (int i = 0; i < speeds.length; i++) {
            if (Math.abs(speeds[i] - current) < 0.01f) {
                index = i;
                break;
            }
        }
        index = (index + 1) % speeds.length;
        float newSpeed = speeds[index];
        playbackManager.setSpeed(newSpeed);
        btnSpeed.setText(String.format(Locale.US, "倍速 %.1fx", newSpeed));
    }

    private void onSeekRelative(int seconds) {
        if (playbackManager.getPlaybackType() == 0) {
            // 直播模式
            long currentLiveTime = playbackManager.getCurrentLiveTime();
            long newTime = currentLiveTime + seconds * 1000L;
            long now = System.currentTimeMillis();
            long minTime = now - LiveConstants.LIVE_REPLAY_WINDOW_MS;
            if (newTime < minTime) newTime = minTime;
            if (newTime > now) newTime = now;
            playbackManager.seekToLiveTime(newTime);
            // 更新 UI
            int newProgress = getProgressFromLiveTime(newTime);
            seekBar.setProgress(newProgress);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            tvCurrentTime.setText(sdf.format(new Date(now)));
            tvTotalTime.setText(sdf.format(new Date(newTime)));
            updateEpgByTime(newTime);
        } else {
            // 点播/回放模式
            long currentPos = playbackManager.getCurrentPosition();
            long newPos = currentPos + seconds * 1000L;
            if (newPos < 0) newPos = 0;
            long duration = playbackManager.getDuration();
            if (newPos > duration) newPos = duration;
            playbackManager.seekTo((int) newPos);
            seekBar.setProgress((int) (newPos / 1000));
            tvCurrentTime.setText(formatTime(newPos));
        }
        // 重置自动隐藏计时器
        handler.removeCallbacks(autoHideRunnable);
        handler.postDelayed(autoHideRunnable, LiveConstants.CONTROL_PANEL_AUTO_HIDE_MS);
    }
}
