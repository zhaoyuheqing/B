package com.github.tvbox.osc.player.controller;

import android.content.Context;
import android.os.Handler;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LivePlayerManager;
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import xyz.doikki.videoplayer.player.VideoView;

public class LivePlaybackManager {

    private final WeakReference<Context> contextRef;
    private final Handler mainHandler;
    private VideoView videoView;
    private LiveController controller;
    private final LivePlayerManager playerManager = new LivePlayerManager();

    private LiveChannelItem currentChannel;
    private boolean isShiyiMode = false;
    private String shiyiTime = null;
    private int currentChangeSourceTimes = 0;

    private PlaybackListener listener;

    private final Runnable timeoutChangeSourceRun = this::handleTimeoutChangeSource;
    private final Runnable timeoutReplayRun = this::replayChannel;

    public interface PlaybackListener {
        boolean onSingleTap(MotionEvent e);
        void onLongPress();
        void onPlayStateChanged(int playState);
        void onVideoSizeChanged(int width, int height);
        void onChannelInfoUpdate(LiveChannelItem channel);
        void onShiyiModeChanged(boolean isShiyi, String timeRange);
        void onNeedShowBottomEpg();
        void onAutoSwitchToNextChannel();
    }

    public LivePlaybackManager(@NonNull Context context, @NonNull Handler handler, @NonNull VideoView videoView) {
        this.contextRef = new WeakReference<>(context);
        this.mainHandler = handler;
        this.videoView = videoView;
        initController();
    }

    private void initController() {
        Context ctx = contextRef.get();
        if (ctx == null) return;

        controller = new LiveController(ctx);
        controller.setListener(new LiveController.LiveControlListener() {
            @Override
            public boolean singleTap(MotionEvent e) {
                return listener != null && listener.onSingleTap(e);
            }

            @Override
            public void longPress() {
                if (listener != null) listener.onLongPress();
            }

            @Override
            public void playStateChanged(int playState) {
                handlePlayState(playState);
                if (listener != null) listener.onPlayStateChanged(playState);
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
        videoView.setVideoController(controller);
        videoView.setProgressManager(null);

        playerManager.init(videoView);
    }

    private void handlePlayState(int playState) {
        if (currentChannel == null) return;

        switch (playState) {
            case VideoView.STATE_PREPARED:
                currentChangeSourceTimes = 0;
                cancelAllTimeouts();
                if (listener != null) {
                    int[] size = videoView.getVideoSize();
                    if (size.length >= 2) listener.onVideoSizeChanged(size[0], size[1]);
                }
                break;

            case VideoView.STATE_BUFFERED:
            case VideoView.STATE_PLAYING:
                cancelAllTimeouts();
                break;

            case VideoView.STATE_BUFFERING:
            case VideoView.STATE_PREPARING:
            case VideoView.STATE_ERROR:
            case VideoView.STATE_PLAYBACK_COMPLETED:
                startTimeoutTimer();
                break;
        }
    }

    private void startTimeoutTimer() {
        cancelAllTimeouts();
        int timeout = Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 2);
        if (timeout == 0) {
            mainHandler.postDelayed(timeoutReplayRun, 30_000);
        } else {
            mainHandler.postDelayed(timeoutChangeSourceRun, timeout * 5000L);
        }
    }

    private void cancelAllTimeouts() {
        mainHandler.removeCallbacks(timeoutChangeSourceRun);
        mainHandler.removeCallbacks(timeoutReplayRun);
    }

    private void handleTimeoutChangeSource() {
        if (currentChannel == null) return;
        currentChangeSourceTimes++;
        if (currentChannel.getSourceNum() == currentChangeSourceTimes) {
            currentChangeSourceTimes = 0;
            if (listener != null) listener.onAutoSwitchToNextChannel();
        } else {
            playNextSource();
        }
    }

    // ====================== 公开 API ======================

    public void setListener(PlaybackListener listener) {
        this.listener = listener;
    }

    public void playChannel(LiveChannelItem channel, boolean isChangeSource) {
        if (channel == null) return;
        resetShiyiMode();
        videoView.release();
        currentChannel = channel;
        playerManager.getLiveChannelPlayer(videoView, channel.getChannelName());

        if (listener != null) {
            listener.onChannelInfoUpdate(channel);
            listener.onNeedShowBottomEpg();
        }

        videoView.setUrl(channel.getUrl(), buildPlayHeaders(channel.getUrl()));
        videoView.start();
    }

    public void playShiyi(String shiyiTimeRange) {
        if (currentChannel == null) return;
        isShiyiMode = true;
        shiyiTime = shiyiTimeRange;
        if (listener != null) listener.onShiyiModeChanged(true, shiyiTimeRange);

        String[] urls = buildShiyiUrls(currentChannel.getUrl(), shiyiTimeRange);
        videoView.release();
        videoView.setUrl(urls[0], buildPlayHeaders(urls[0]));
        videoView.start();
    }

    public void playNextSource() {
        if (currentChannel == null) return;
        resetShiyiMode();
        currentChannel.nextSource();
        playChannel(currentChannel, true);
    }

    public void playPreSource() {
        if (currentChannel == null) return;
        resetShiyiMode();
        currentChannel.preSource();
        playChannel(currentChannel, true);
    }

    public void replayChannel() {
        if (currentChannel == null) return;
        resetShiyiMode();
        playChannel(currentChannel, false);
    }

    public void resetShiyiMode() {
        isShiyiMode = false;
        shiyiTime = null;
        if (listener != null) listener.onShiyiModeChanged(false, null);
    }

    // ====================== 时移工具方法 ======================
    public String[] buildShiyiUrls(String originalUrl, String shiyiTime) {
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

    public String[] buildShiyiTimes(String targetDate, String startTime, String endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMD_NUM);
        String startDateTime = targetDate + startTime.replace(":", "") + "30";
        String endDateTime;
        if (endTime.compareTo(startTime) < 0) {
            try {
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

    public boolean isValidShiyiTime(String startTime, String endTime) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(LiveConstants.DATE_FORMAT_YMDHMS);
            return sdf.parse(startTime).getTime() < sdf.parse(endTime).getTime();
        } catch (Exception e) {
            return false;
        }
    }

    private HashMap<String, String> buildPlayHeaders(String url) {
        HashMap<String, String> header = new HashMap<>();
        try {
            boolean matchTo = false;
            JSONArray livePlayHeaders = new JSONArray(ApiConfig.get().getLivePlayHeaders().toString());
            for (int i = 0; i < livePlayHeaders.length(); i++) {
                JSONObject headerObj = livePlayHeaders.getJSONObject(i);
                JSONArray flags = headerObj.getJSONArray("flag");
                JSONObject headerData = headerObj.getJSONObject("header");
                for (int j = 0; j < flags.length(); j++) {
                    if (url.contains(flags.getString(j))) {
                        matchTo = true;
                        break;
                    }
                }
                if (matchTo) {
                    Iterator<String> keys = headerData.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        header.put(key, headerData.getString(key));
                    }
                    break;
                }
            }
            if (!matchTo) header.put("User-Agent", LiveConstants.DEFAULT_USER_AGENT);
        } catch (Exception e) {
            header.put("User-Agent", LiveConstants.DEFAULT_USER_AGENT);
        }
        return header;
    }

    // ====================== 播放器查询与控制 ======================
    public void seekTo(int position) {
        if (videoView != null) {
            videoView.seekTo(position);
        }
    }

    public long getCurrentPosition() {
        return videoView != null ? videoView.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return videoView != null ? videoView.getDuration() : 0;
    }

    public int[] getVideoSize() {
        return videoView != null ? videoView.getVideoSize() : new int[]{0, 0};
    }

    public float getTcpSpeed() {
        return videoView != null ? videoView.getTcpSpeed() : 0;
    }

    public void pause() {
        if (videoView != null) videoView.pause();
    }

    public void resume() {
        if (videoView != null) videoView.resume();
    }

    public void release() {
        cancelAllTimeouts();
        if (videoView != null) {
            videoView.release();
            videoView = null;
        }
        contextRef.clear();
        listener = null;
    }

    public boolean isShiyiMode() {
        return isShiyiMode;
    }

    public String getShiyiTime() {
        return shiyiTime;
    }

    public LiveChannelItem getCurrentChannel() {
        return currentChannel;
    }

    public void changeScale(int scaleIndex) {
        // 如果 LivePlayerManager 支持比例切换，可在此实现
    }

    public void changePlayerType(int typeIndex) {
        // 如果 LivePlayerManager 支持解码切换，可在此实现
    }
}
