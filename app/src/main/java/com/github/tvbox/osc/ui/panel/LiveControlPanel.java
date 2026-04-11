package com.github.tvbox.osc.ui.panel;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
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
import java.util.TimeZone;

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
    private TextView tvCurrentTime;   // 左侧时间
    private TextView tvTotalTime;     // 右侧时间
    private TextView btnPlayPause;
    private TextView btnRewind10;
    private TextView btnForward10;
    private TextView tvDateWeek;
    private TextView tvCurrentEpg;
    private boolean isVisible = false;
    private final Runnable autoHideRunnable = this::hide;

    // 连续点击防抖
    private long pendingSeekOffset = 0;
    private final Runnable pendingSeekRunnable = this::executePendingSeek;

    // 衔接防重复标志
    private boolean isPreparingNextSegment = false;

    // 焦点模式
    private boolean isButtonFocusMode = false;
    // 普通回放时预设的EPG信息
    private String currentEpgInfo = "";
    // 长按拖动相关
    private Handler longPressHandler = new Handler();
    private Runnable longPressRunnable;
    private int longPressKeyCode = 0;
    private static final long LONG_PRESS_DELAY = 500;
    private static final long REPEAT_INTERVAL = 100;

    // 每秒刷新定时器
    private final Runnable liveProgressUpdater = new Runnable() {
        @Override
        public void run() {
            if (!isVisible) return;
            updateProgressAndTime();
            handler.postDelayed(this, 1000);
        }
    };

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
                    if (playbackManager.isLive24hMode()) {
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
                if (playbackManager.getPlaybackType() == 0 && !playbackManager.isLive24hMode()) {
                    playbackManager.setLive24hMode(true);
                }
                if (playbackManager.isLive24hMode()) {
                    long targetTime = getLiveTimeFromProgress(progress);
                    playbackManager.seekToLiveTimeSegment(targetTime, true);
                } else if (playbackManager.getPlaybackType() == 2 || playbackManager.getPlaybackType() == 1) {
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
        panelView.setOnClickListener(v -> hide());
        Log.d(TAG, "Control panel initialized");
    }

    // ========== 辅助方法 ==========
    private SimpleDateFormat getTimeFormatter() {
        SimpleDateFormat sdf = new SimpleDateFormat(LiveConstants.TIME_FORMAT_HHMMSS, Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf;
    }

    private void updateTimeDisplayForLive(long targetTime, long now) {
        SimpleDateFormat sdf = getTimeFormatter();
        // 左侧显示回放点时间，右侧显示当前时间
        tvCurrentTime.setText(sdf.format(new Date(targetTime)));
        tvTotalTime.setText(sdf.format(new Date(now)));
    }

    private long getLiveTimeFromProgress(int progressSec) {
        long maxSec = LiveConstants.LIVE_REPLAY_WINDOW_MS / 1000;
        long now = System.currentTimeMillis();
        long offsetSec = maxSec - progressSec;
        return now - offsetSec * 1000;
    }

    private int getProgressFromLiveTime(long targetTimeMs) {
        long now = System.currentTimeMillis();
        long maxSec = LiveConstants.LIVE_REPLAY_WINDOW_MS / 1000;
        long diffSec = (now - targetTimeMs) / 1000;
        if (diffSec < 0) diffSec = 0;
        if (diffSec > maxSec) diffSec = maxSec;
        return (int) (maxSec - diffSec);
    }

    private void updateEpgByTime(long absoluteTimeMs) {
        if (playbackManager.getCurrentChannel() == null || epgCacheHelper == null) {
            tvCurrentEpg.setText("暂无节目信息");
            return;
        }
        String channelName = playbackManager.getCurrentChannel().getChannelName();
        SimpleDateFormat sdf = LiveConstants.getGMT8Formatter(LiveConstants.DATE_FORMAT_YMD);
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

    // ========== UI 更新 ==========
    private void updateUI() {
        String programName = playbackManager.getCurrentChannel() != null ?
                playbackManager.getCurrentChannel().getChannelName() : "直播";
        tvProgramName.setText(programName);

        float speed = playbackManager.getCurrentSpeed();
        btnSpeed.setText(String.format(Locale.US, "%.1fx", speed));

        if (playbackManager.isLive24hMode()) {
            long now = System.currentTimeMillis();
            long liveTime = playbackManager.getCurrentLiveTime();
            long maxSec = LiveConstants.LIVE_REPLAY_WINDOW_MS / 1000;
            seekBar.setMax((int) maxSec);
            int progress = getProgressFromLiveTime(liveTime);
            seekBar.setProgress(progress);
            updateTimeDisplayForLive(liveTime, now);
            updateEpgByTime(liveTime);
        } else if (playbackManager.getPlaybackType() == 2) {
            long duration = playbackManager.getDuration();
            long currentPos = playbackManager.getCurrentPosition();
            if (duration > 0) {
                seekBar.setMax((int) (duration / 1000));
                seekBar.setProgress((int) (currentPos / 1000));
                tvCurrentTime.setText(formatTime(currentPos));
                tvTotalTime.setText(formatTime(duration));
            }
            tvCurrentEpg.setText("");
        } else if (playbackManager.getPlaybackType() == 1) {
            // 普通回放（EPG触发的时移）
            long duration = playbackManager.getDuration();
            long currentPos = playbackManager.getCurrentPosition();
            if (duration > 0) {
                seekBar.setMax((int) (duration / 1000));
                seekBar.setProgress((int) (currentPos / 1000));
                tvCurrentTime.setText(formatTime(currentPos));
                tvTotalTime.setText(formatTime(duration));
            } else {
                long now = System.currentTimeMillis();
                long maxSec = LiveConstants.LIVE_REPLAY_WINDOW_MS / 1000;
                seekBar.setMax((int) maxSec);
                seekBar.setProgress((int) maxSec);
                SimpleDateFormat sdf = getTimeFormatter();
                tvCurrentTime.setText(sdf.format(new Date(now)));
                tvTotalTime.setText("回放");
            }
            // 直接显示预设的EPG信息
            tvCurrentEpg.setText(currentEpgInfo);
        } else {
            // 纯直播视觉模式
            long now = System.currentTimeMillis();
            long maxSec = LiveConstants.LIVE_REPLAY_WINDOW_MS / 1000;
            seekBar.setMax((int) maxSec);
            seekBar.setProgress((int) maxSec);
            SimpleDateFormat sdf = getTimeFormatter();
            tvCurrentTime.setText(sdf.format(new Date(now)));
            tvTotalTime.setText(sdf.format(new Date(now)));
            tvCurrentEpg.setText("");
        }

        btnPlayPause.setText(playbackManager.isPlaying() ? "暂停" : "播放");

        SimpleDateFormat dateFormat = LiveConstants.getGMT8Formatter("MM月dd日 EEEE");
        tvDateWeek.setText(dateFormat.format(new Date()));
    }

    private void updateProgressAndTime() {
        if (playbackManager.isLive24hMode()) {
            long now = System.currentTimeMillis();
            long liveTime = playbackManager.getCurrentLiveTime();
            int progress = getProgressFromLiveTime(liveTime);
            seekBar.setProgress(progress);
            updateTimeDisplayForLive(liveTime, now);
            updateEpgByTime(liveTime);
            checkAndSwitchToNextSegment(liveTime);
        } else if (playbackManager.getPlaybackType() == 2) {
            long duration = playbackManager.getDuration();
            long currentPos = playbackManager.getCurrentPosition();
            if (duration > 0) {
                if (seekBar.getMax() != (int) (duration / 1000)) {
                    seekBar.setMax((int) (duration / 1000));
                }
                seekBar.setProgress((int) (currentPos / 1000));
                tvCurrentTime.setText(formatTime(currentPos));
                tvTotalTime.setText(formatTime(duration));
            }
        } else if (playbackManager.getPlaybackType() == 1) {
            long duration = playbackManager.getDuration();
            long currentPos = playbackManager.getCurrentPosition();
            if (duration > 0) {
                if (seekBar.getMax() != (int) (duration / 1000)) {
                    seekBar.setMax((int) (duration / 1000));
                }
                seekBar.setProgress((int) (currentPos / 1000));
                tvCurrentTime.setText(formatTime(currentPos));
                tvTotalTime.setText(formatTime(duration));
            } else {
                long now = System.currentTimeMillis();
                long maxSec = LiveConstants.LIVE_REPLAY_WINDOW_MS / 1000;
                if (seekBar.getMax() != (int) maxSec) seekBar.setMax((int) maxSec);
                if (seekBar.getProgress() != (int) maxSec) seekBar.setProgress((int) maxSec);
                SimpleDateFormat sdf = getTimeFormatter();
                tvCurrentTime.setText(sdf.format(new Date(now)));
                tvTotalTime.setText("回放");
            }
            // EPG信息保持不变（不动态更新）
        } else if (playbackManager.getPlaybackType() == 0) {
            long now = System.currentTimeMillis();
            long maxSec = LiveConstants.LIVE_REPLAY_WINDOW_MS / 1000;
            if (seekBar.getMax() != (int) maxSec) seekBar.setMax((int) maxSec);
            if (seekBar.getProgress() != (int) maxSec) seekBar.setProgress((int) maxSec);
            SimpleDateFormat sdf = getTimeFormatter();
            tvCurrentTime.setText(sdf.format(new Date(now)));
            tvTotalTime.setText(sdf.format(new Date(now)));
        }
    }

    private void updateTimeByProgress(int progressSec) {
        if (playbackManager.isLive24hMode()) {
            long targetTime = getLiveTimeFromProgress(progressSec);
            long now = System.currentTimeMillis();
            updateTimeDisplayForLive(targetTime, now);
        } else if (playbackManager.getPlaybackType() == 2 || playbackManager.getPlaybackType() == 1) {
            long progressMs = progressSec * 1000L;
            tvCurrentTime.setText(formatTime(progressMs));
            long totalMs = playbackManager.getDuration();
            tvTotalTime.setText(formatTime(totalMs));
        }
    }

    private void checkAndSwitchToNextSegment(long liveTime) {
        if (isPreparingNextSegment) return;
        int idx = playbackManager.getCurrentSegmentIndex();
        long segmentEnd = playbackManager.getCurrentSegmentEndTime();
        if (idx <= 0 || segmentEnd <= 0) return;
        long timeToEnd = segmentEnd - liveTime;
        if (timeToEnd > 0 && timeToEnd <= LiveConstants.SEGMENT_SWITCH_THRESHOLD_MS) {
            isPreparingNextSegment = true;
            playbackManager.seekToLiveTimeSegment(segmentEnd, true);
            handler.postDelayed(() -> isPreparingNextSegment = false, 3000);
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
        btnSpeed.setText(String.format(Locale.US, "%.1fx", newSpeed));
    }

    private void onSeekRelative(int seconds) {
        if (playbackManager.getPlaybackType() == 0 && !playbackManager.isLive24hMode()) {
            playbackManager.setLive24hMode(true);
        }
        pendingSeekOffset += seconds * 1000L;
        handler.removeCallbacks(pendingSeekRunnable);
        handler.postDelayed(pendingSeekRunnable, 300);
    }

    private void executePendingSeek() {
        if (pendingSeekOffset == 0) return;
        long offset = pendingSeekOffset;
        pendingSeekOffset = 0;
        if (playbackManager.isLive24hMode()) {
            long currentLiveTime = playbackManager.getCurrentLiveTime();
            long newTime = currentLiveTime + offset;
            long now = System.currentTimeMillis();
            long minTime = now - LiveConstants.LIVE_REPLAY_WINDOW_MS;
            if (newTime < minTime) newTime = minTime;
            if (newTime > now) newTime = now;
            playbackManager.seekToLiveTimeSegment(newTime, true);
            int newProgress = getProgressFromLiveTime(newTime);
            seekBar.setProgress(newProgress);
            updateTimeDisplayForLive(newTime, now);
            updateEpgByTime(newTime);
        } else if (playbackManager.getPlaybackType() == 2 || playbackManager.getPlaybackType() == 1) {
            long currentPos = playbackManager.getCurrentPosition();
            long newPos = currentPos + offset;
            if (newPos < 0) newPos = 0;
            long duration = playbackManager.getDuration();
            if (newPos > duration) newPos = duration;
            playbackManager.seekTo((int) newPos);
            seekBar.setProgress((int) (newPos / 1000));
            tvCurrentTime.setText(formatTime(newPos));
        }
        handler.removeCallbacks(autoHideRunnable);
        handler.postDelayed(autoHideRunnable, LiveConstants.CONTROL_PANEL_AUTO_HIDE_MS);
    }

    // ========== 遥控器按键处理 ==========
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!isVisible) return false;
        int action = event.getAction();
        int keyCode = event.getKeyCode();

        // 返回键：隐藏面板
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN) {
                hide();
            }
            return true;
        }

        // 左右键处理（默认模式）
        if (!isButtonFocusMode && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (action == KeyEvent.ACTION_DOWN) {
                startLongPressRepeat(keyCode);
            } else if (action == KeyEvent.ACTION_UP) {
                boolean wasLongPress = (longPressRunnable != null);
                stopLongPressRepeat();
                if (!wasLongPress) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        playbackManager.seekRelative(-10);
                    } else {
                        playbackManager.seekRelative(10);
                    }
                    updateUI();
                }
            }
            return true;
        }

        // 确定键
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (isButtonFocusMode) {
                    View focused = panelView.findFocus();
                    if (focused != null && focused != panelView) {
                        focused.performClick();
                    }
                } else {
                    togglePlayPause();
                }
            }
            return true;
        }

        // 上下键：切换焦点模式或移动焦点
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (!isButtonFocusMode) {
                    enterButtonFocusMode();
                } else {
                    moveFocus(keyCode == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN);
                }
            }
            return true;
        }

        return false;
    }

    private void startLongPressRepeat(int keyCode) {
        if (longPressRunnable != null) return;
        longPressKeyCode = keyCode;
        longPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (longPressKeyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    playbackManager.seekRelative(-1);
                } else {
                    playbackManager.seekRelative(1);
                }
                updateUI();
                longPressHandler.postDelayed(this, REPEAT_INTERVAL);
            }
        };
        longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_DELAY);
    }

    private void stopLongPressRepeat() {
        if (longPressRunnable != null) {
            longPressHandler.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    // ========== 焦点管理 ==========
    private void enterButtonFocusMode() {
        isButtonFocusMode = true;
        btnSpeed.requestFocus();
    }

    private void exitButtonFocusMode() {
        isButtonFocusMode = false;
        View focused = panelView.findFocus();
        if (focused != null) focused.clearFocus();
    }

    private void moveFocus(int direction) {
        View current = panelView.findFocus();
        if (current == null) return;
        View next = current.focusSearch(direction);
        if (next != null) next.requestFocus();
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
        handler.removeCallbacks(liveProgressUpdater);
        handler.post(liveProgressUpdater);
        Log.d(TAG, "Panel shown");
    }

    public void hide() {
        if (!isVisible) return;
        exitButtonFocusMode();
        panelView.setVisibility(View.GONE);
        container.setVisibility(View.GONE);
        isVisible = false;
        handler.removeCallbacks(autoHideRunnable);
        handler.removeCallbacks(liveProgressUpdater);
        Log.d(TAG, "Panel hidden");
    }

    public boolean isShowing() {
        return isVisible;
    }

    public void refresh() {
        if (isVisible) updateUI();
    }

    public void setCurrentEpgInfo(String epgInfo) {
        this.currentEpgInfo = epgInfo;
    }

    public boolean isPointInside(int x, int y) {
        int[] location = new int[2];
        panelView.getLocationOnScreen(location);
        android.graphics.Rect rect = new android.graphics.Rect(
                location[0], location[1],
                location[0] + panelView.getWidth(),
                location[1] + panelView.getHeight());
        return rect.contains(x, y);
    }
}
