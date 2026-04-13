package com.github.tvbox.osc.player.controller;

import android.content.Context;
import android.os.Handler;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LivePlayerManager;
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.util.EpgCacheHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayerHelper;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimeZone;

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
    private int currentScale = 0;
    private int currentPlayerType = 0;

    private boolean isLive24hMode = false;
    private int currentSegmentIndex = -1;
    private long currentSegmentEndTime = 0;
    private boolean isSegmentSeeking = false;

    // 底部栏信息缓存
    private EpgCacheHelper epgCacheHelper;
    private BottomBarInfo bottomBarInfo = new BottomBarInfo();

    private PlaybackListener listener;

    private final Runnable timeoutChangeSourceRun = this::handleTimeoutChangeSource;
    private final Runnable timeoutReplayRun = this::handleTimeoutReplay;

    public interface PlaybackListener {
        boolean onSingleTap(MotionEvent e);
        void onLongPress();
        void onPlayStateChanged(int playState);
        void onVideoSizeChanged(int width, int height);
        void onCurrentChannelChanged(LiveChannelItem channel, boolean isChangeSource);
        void onAutoSwitchToNextChannel(boolean reverse);
        void onTimeoutReplay();
        void onShiyiModeChanged(boolean isShiyi, String timeRange);
        void onRequestChangeSource(int direction);
        void showControlPanel();
    }

    public static class BottomBarInfo {
        public int staticProgress = 0;
        public String currentProgramTime = "--:-- - --:--";
        public String remainingTime = "剩余 --:--:--";
        public String decodeType = "硬解";
        public String audioTrack = "立体声";
        public boolean canShiyi = false;
    }

    public LivePlaybackManager(@NonNull Context context, @NonNull Handler handler, @NonNull VideoView videoView) {
        this.contextRef = new WeakReference<>(context);
        this.mainHandler = handler;
        this.videoView = videoView;
        initController();
    }

    public void setEpgCacheHelper(EpgCacheHelper helper) {
        this.epgCacheHelper = helper;
    }

    public void updateBottomBarInfo() {
        if (bottomBarInfo == null) bottomBarInfo = new BottomBarInfo();
        bottomBarInfo.decodeType = PlayerHelper.getPlayerName(getCurrentPlayerType());
        bottomBarInfo.audioTrack = "立体声";
        bottomBarInfo.canShiyi = currentChannel != null && currentChannel.getinclude_back();

        if (currentChannel == null || epgCacheHelper == null) {
            bottomBarInfo.staticProgress = 0;
            bottomBarInfo.currentProgramTime = "--:-- - --:--";
            bottomBarInfo.remainingTime = "剩余 --:--:--";
            return;
        }

        String channelName = currentChannel.getChannelName();
        Date now = new Date();
        SimpleDateFormat sdf = LiveConstants.getGMT8Formatter(LiveConstants.DATE_FORMAT_YMD);
        String today = sdf.format(now);
        ArrayList<com.github.tvbox.osc.bean.Epginfo> epgList = epgCacheHelper.getCachedEpg(channelName, today);
        if (epgList == null || epgList.isEmpty()) {
            bottomBarInfo.staticProgress = 0;
            bottomBarInfo.currentProgramTime = "--:-- - --:--";
            bottomBarInfo.remainingTime = "剩余 --:--:--";
            return;
        }

        com.github.tvbox.osc.bean.Epginfo currentEpg = null;
        for (com.github.tvbox.osc.bean.Epginfo epg : epgList) {
            if (now.after(epg.startdateTime) && now.before(epg.enddateTime)) {
                currentEpg = epg;
                break;
            }
        }

        if (currentEpg != null) {
            bottomBarInfo.currentProgramTime = currentEpg.start + " - " + currentEpg.end;
            long remainingMs = currentEpg.enddateTime.getTime() - now.getTime();
            if (remainingMs < 0) remainingMs = 0;
            long remainingSec = remainingMs / 1000;
            bottomBarInfo.remainingTime = String.format("剩余 %02d:%02d:%02d", remainingSec / 3600, (remainingSec % 3600) / 60, remainingSec % 60);
            long totalMs = currentEpg.enddateTime.getTime() - currentEpg.startdateTime.getTime();
            long elapsedMs = now.getTime() - currentEpg.startdateTime.getTime();
            int progress = totalMs > 0 ? (int) (elapsedMs * 1000 / totalMs) : 0;
            if (progress < 0) progress = 0;
            if (progress > 1000) progress = 1000;
            bottomBarInfo.staticProgress = progress;
        } else {
            bottomBarInfo.staticProgress = 0;
            bottomBarInfo.currentProgramTime = "--:-- - --:--";
            bottomBarInfo.remainingTime = "剩余 --:--:--";
        }
    }

    public BottomBarInfo getBottomBarInfo() {
        return bottomBarInfo;
    }

    private void initController() {
        Context ctx = contextRef.get();
        if (ctx == null) return;
        controller = new LiveController(ctx);
        controller.setListener(new LiveController.LiveControlListener() {
            @Override public boolean singleTap(MotionEvent e) { return listener != null && listener.onSingleTap(e); }
            @Override public void longPress() { if (listener != null) listener.onLongPress(); }
            @Override public void playStateChanged(int playState) {
                handlePlayState(playState);
                if (listener != null) listener.onPlayStateChanged(playState);
            }
            @Override public void changeSource(int direction) {
                if (direction > 0) playNextSource();
                else playPreSource();
            }
            @Override public void showControlPanel() {
                if (listener != null) listener.showControlPanel();
            }
        });
        controller.setCanChangePosition(false);
        controller.setEnableInNormal(true);
        controller.setGestureEnabled(true);
        controller.setDoubleTapTogglePlayEnabled(false);
        videoView.setVideoController(controller);
        videoView.setProgressManager(null);
        playerManager.init(videoView);
        this.currentScale = playerManager.getLivePlayerScale();
        this.currentPlayerType = playerManager.getLivePlayerType();
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
                currentChangeSourceTimes = 0;
                break;
            case VideoView.STATE_BUFFERING:
            case VideoView.STATE_PREPARING:
            case VideoView.STATE_ERROR:
            case VideoView.STATE_PLAYBACK_COMPLETED:
                if (isLive24hMode && currentSegmentIndex < LiveConstants.SEGMENT_COUNT - 1) {
                    long nextStart = currentSegmentEndTime;
                    if (nextStart < System.currentTimeMillis()) {
                        seekToLiveTimeSegment(nextStart + 1000, true);
                        return;
                    }
                }
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

    public void cancelAllTimeouts() {
        mainHandler.removeCallbacks(timeoutChangeSourceRun);
        mainHandler.removeCallbacks(timeoutReplayRun);
    }

    private void handleTimeoutChangeSource() {
        if (currentChannel == null) return;
        currentChangeSourceTimes++;
        if (currentChannel.getSourceNum() == currentChangeSourceTimes) {
            currentChangeSourceTimes = 0;
            if (listener != null) {
                boolean reverse = Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false);
                listener.onAutoSwitchToNextChannel(reverse);
            }
        } else {
            playNextSource();
        }
    }

    private void handleTimeoutReplay() {
        if (currentChannel != null && listener != null) listener.onTimeoutReplay();
    }

    public void setListener(PlaybackListener listener) { this.listener = listener; }

    public void playChannel(LiveChannelItem channel, boolean isChangeSource) {
        if (channel == null || videoView == null) return;
        setLive24hMode(false);
        if (isChangeSource && channel.getSourceNum() == 1) {
            if (listener != null) listener.onCurrentChannelChanged(channel, true);
            return;
        }
        resetShiyiMode();
        videoView.release();
        currentChannel = channel;
        currentChannel.setinclude_back(currentChannel.getUrl().indexOf(LiveConstants.PLTV_FLAG + "8888") != -1);
        playerManager.getLiveChannelPlayer(videoView, currentChannel.getChannelName());
        this.currentPlayerType = playerManager.getLivePlayerType();
        this.currentScale = playerManager.getLivePlayerScale();
        videoView.setUrl(currentChannel.getUrl(), buildPlayHeaders(currentChannel.getUrl()));
        videoView.start();
        if (listener != null) listener.onCurrentChannelChanged(channel, isChangeSource);
    }

    public void playShiyi(String shiyiTimeRange) {
        if (currentChannel == null || videoView == null) return;
        isShiyiMode = true;
        shiyiTime = shiyiTimeRange;
        if (listener != null) listener.onShiyiModeChanged(true, shiyiTimeRange);
        String[] urls = buildShiyiUrls(currentChannel.getUrl(), shiyiTimeRange);
        videoView.release();
        videoView.setUrl(urls[0], buildPlayHeaders(urls[0]));
        videoView.start();
    }

    public void playNextSource() {
        if (listener != null) listener.onRequestChangeSource(1);
        setLive24hMode(false);
    }

    public void playPreSource() {
        if (listener != null) listener.onRequestChangeSource(-1);
        setLive24hMode(false);
    }

    public void resetShiyiMode() {
        isShiyiMode = false;
        shiyiTime = null;
        setLive24hMode(false);
        if (listener != null) listener.onShiyiModeChanged(false, null);
    }

    public void changePlayerType(int typeIndex) {
        if (videoView == null || currentChannel == null) return;
        videoView.release();
        playerManager.changeLivePlayerType(videoView, typeIndex, currentChannel.getChannelName());
        this.currentPlayerType = playerManager.getLivePlayerType();
        String url = currentChannel.getUrl();
        videoView.setUrl(url, buildPlayHeaders(url));
        videoView.start();
        if (listener != null) listener.onCurrentChannelChanged(currentChannel, true);
    }

    public void changeScale(int scaleIndex) {
        if (videoView != null && currentChannel != null) {
            playerManager.changeLivePlayerScale(videoView, scaleIndex, currentChannel.getChannelName());
            this.currentScale = scaleIndex;
            if (listener != null) listener.onCurrentChannelChanged(currentChannel, true);
        }
    }

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
        SimpleDateFormat sdf = LiveConstants.getGMT8Formatter(LiveConstants.DATE_FORMAT_YMD_NUM);
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
            SimpleDateFormat sdf = LiveConstants.getGMT8Formatter(LiveConstants.DATE_FORMAT_YMDHMS);
            return sdf.parse(startTime).getTime() < sdf.parse(endTime).getTime();
        } catch (Exception e) { return false; }
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

    public void seekTo(int position) { if (videoView != null) videoView.seekTo(position); }
    public long getCurrentPosition() { return videoView != null ? videoView.getCurrentPosition() : 0; }
    public long getDuration() { return videoView != null ? videoView.getDuration() : 0; }
    public int[] getVideoSize() { return videoView != null ? videoView.getVideoSize() : new int[]{0,0}; }
    public float getTcpSpeed() { return videoView != null ? videoView.getTcpSpeed() : 0; }
    public void pause() { if (videoView != null) videoView.pause(); }
    public void resume() { if (videoView != null) videoView.resume(); }
    public void release() {
        cancelAllTimeouts();
        if (videoView != null) {
            videoView.release();
            videoView = null;
        }
        currentChannel = null;
        contextRef.clear();
        listener = null;
    }
    public boolean isShiyiMode() { return isShiyiMode; }
    public String getShiyiTime() { return shiyiTime; }
    public LiveChannelItem getCurrentChannel() { return currentChannel; }
    public int getCurrentScale() { return currentScale; }
    public int getCurrentPlayerType() { return currentPlayerType; }

    public void setLive24hMode(boolean enabled) {
        this.isLive24hMode = enabled;
        if (!enabled) {
            currentSegmentIndex = -1;
            currentSegmentEndTime = 0;
        }
    }
    public boolean isLive24hMode() { return isLive24hMode; }
    public int getCurrentSegmentIndex() { return currentSegmentIndex; }
    public long getCurrentSegmentEndTime() { return currentSegmentEndTime; }

    public int getPlaybackType() {
        if (isShiyiMode && isLive24hMode) return 3;
        if (isShiyiMode) return 1;
        if (currentChannel != null && getDuration() > 0) return 2;
        return 0;
    }

    public long getDraggableRange() {
        if (isLive24hMode) {
            return LiveConstants.LIVE_REPLAY_WINDOW_MS;
        } else if (getPlaybackType() == 2) {
            return getDuration();
        } else {
            return 0;
        }
    }

    public long getCurrentLiveTime() {
        if (!isShiyiMode) return System.currentTimeMillis();
        if (shiyiTime == null || !shiyiTime.contains("-")) return System.currentTimeMillis();
        try {
            String[] parts = shiyiTime.split("-");
            SimpleDateFormat sdf = LiveConstants.getGMT8Formatter(LiveConstants.DATE_FORMAT_YMDHMS);
            long start = sdf.parse(parts[0]).getTime();
            return start + getCurrentPosition();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    public void seekToLiveTimeSegment(long targetTimeMs, boolean enable24hMode) {
        if (isSegmentSeeking) {
            isSegmentSeeking = false;
            return;
        }
        if (enable24hMode) setLive24hMode(true);
        long now = System.currentTimeMillis();
        long minTime = now - LiveConstants.LIVE_REPLAY_WINDOW_MS;
        targetTimeMs = Math.max(minTime, Math.min(now, targetTimeMs));

        long offsetFromNow = now - targetTimeMs;
        int segmentIndex = (int) (offsetFromNow / LiveConstants.SEGMENT_DURATION_MS);
        segmentIndex = Math.min(segmentIndex, LiveConstants.SEGMENT_COUNT - 1);

        long segmentEnd;
        if (segmentIndex == 0) {
            segmentEnd = now;
        } else {
            segmentEnd = now - segmentIndex * LiveConstants.SEGMENT_DURATION_MS;
        }
        long segmentStart = segmentEnd - LiveConstants.SEGMENT_DURATION_MS;

        long playStart = Math.max(segmentStart, Math.min(segmentEnd, targetTimeMs));

        SimpleDateFormat sdf = LiveConstants.getGMT8Formatter(LiveConstants.DATE_FORMAT_YMDHMS);
        String startStr = sdf.format(new Date(playStart));
        String endStr = sdf.format(new Date(segmentEnd));
        String timeRange = startStr + "-" + endStr;

        currentSegmentIndex = segmentIndex;
        currentSegmentEndTime = segmentEnd;

        isSegmentSeeking = true;
        playShiyi(timeRange);
        isSegmentSeeking = false;
    }

    public void seekRelative(int seconds) {
        if (isLive24hMode) {
            long currentLiveTime = getCurrentLiveTime();
            long newTime = currentLiveTime + seconds * 1000L;
            long now = System.currentTimeMillis();
            long minTime = now - LiveConstants.LIVE_REPLAY_WINDOW_MS;
            if (newTime < minTime) newTime = minTime;
            if (newTime > now) newTime = now;
            seekToLiveTimeSegment(newTime, true);
        } else if (getPlaybackType() == 2) {
            long newPos = getCurrentPosition() + seconds * 1000L;
            if (newPos < 0) newPos = 0;
            long duration = getDuration();
            if (newPos > duration) newPos = duration;
            seekTo((int) newPos);
        }
    }

    public void setSpeed(float speed) {
        if (videoView != null) videoView.setSpeed(speed);
    }

    public float getCurrentSpeed() {
        return videoView != null ? videoView.getSpeed() : 1.0f;
    }

    public boolean isPlaying() {
        return videoView != null && videoView.isPlaying();
    }
}
