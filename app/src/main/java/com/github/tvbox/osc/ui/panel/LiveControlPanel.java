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
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.player.controller.LivePlaybackManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LiveControlPanel {
    private static final String TAG = "LiveControlPanel";
    private final Context context;
    private final LivePlaybackManager playbackManager;
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
    private boolean isVisible = false;
    private final Runnable autoHideRunnable = this::hide;

    public LiveControlPanel(Context context, FrameLayout container, LivePlaybackManager playbackManager, Handler handler) {
        this.context = context;
        this.container = container;
        this.playbackManager = playbackManager;
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

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnRewind10.setOnClickListener(v -> playbackManager.seekRelative(-10));
        btnForward10.setOnClickListener(v -> playbackManager.seekRelative(10));
        btnSpeed.setOnClickListener(v -> cycleSpeed());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) updateTimeByProgress(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(autoHideRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                long targetPosMs = (long) seekBar.getProgress() * 1000L;
                if (playbackManager.getPlaybackType() == 0) {
                    long now = System.currentTimeMillis();
                    long targetTime = now - (LiveConstants.LIVE_REPLAY_WINDOW_MS - targetPosMs);
                    playbackManager.seekToLiveTime(targetTime);
                } else {
                    playbackManager.seekTo((int) targetPosMs);
                }
                hide();
            }
        });

        container.addView(panelView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        panelView.setVisibility(View.GONE);
        Log.d(TAG, "Control panel initialized");
    }

    public void show() {
        if (isVisible) return;
        Log.d(TAG, "show() called, container visibility: " + container.getVisibility());
        updateUI();
        panelView.setVisibility(View.VISIBLE);
        // 确保容器可见（可能被其他布局覆盖）
        container.setVisibility(View.VISIBLE);
        container.bringToFront();
        isVisible = true;
        handler.removeCallbacks(autoHideRunnable);
        handler.postDelayed(autoHideRunnable, LiveConstants.CONTROL_PANEL_AUTO_HIDE_MS);
        Log.d(TAG, "Panel shown");
    }

    public void hide() {
        if (!isVisible) return;
        Log.d(TAG, "hide() called");
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

    private void updateUI() {
        String programName = playbackManager.getCurrentChannel() != null ?
                playbackManager.getCurrentChannel().getChannelName() : "直播";
        tvProgramName.setText(programName);

        float speed = playbackManager.getCurrentSpeed();
        btnSpeed.setText(String.format(Locale.US, "倍速 %.1fx", speed));

        long rangeMs = playbackManager.getDraggableRange();
        long currentPosMs;
        if (playbackManager.getPlaybackType() == 0) {
            seekBar.setMax((int) (rangeMs / 1000));
            long now = System.currentTimeMillis();
            long liveTime = playbackManager.getCurrentLiveTime();
            long offset = now - liveTime;
            if (offset < 0) offset = 0;
            if (offset > rangeMs) offset = rangeMs;
            currentPosMs = offset;
        } else {
            seekBar.setMax((int) (rangeMs / 1000));
            currentPosMs = playbackManager.getCurrentPosition();
        }
        seekBar.setProgress((int) (currentPosMs / 1000));
        updateTimeDisplay(currentPosMs, rangeMs);

        btnPlayPause.setText(playbackManager.isPlaying() ? "暂停" : "播放");
    }

    private void updateTimeDisplay(long currentMs, long totalMs) {
        if (playbackManager.getPlaybackType() == 0) {
            long liveTime = playbackManager.getCurrentLiveTime();
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            tvCurrentTime.setText(sdf.format(new Date(liveTime)));
            tvTotalTime.setText("直播");
        } else {
            tvCurrentTime.setText(formatTime(currentMs));
            tvTotalTime.setText(formatTime(totalMs));
        }
    }

    private void updateTimeByProgress(int progressSec) {
        long progressMs = progressSec * 1000L;
        if (playbackManager.getPlaybackType() == 0) {
            long now = System.currentTimeMillis();
            long targetTime = now - (LiveConstants.LIVE_REPLAY_WINDOW_MS - progressMs);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            tvCurrentTime.setText(sdf.format(new Date(targetTime)));
            tvTotalTime.setText("直播");
        } else {
            tvCurrentTime.setText(formatTime(progressMs));
            long totalMs = playbackManager.getDraggableRange();
            tvTotalTime.setText(formatTime(totalMs));
        }
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
}
