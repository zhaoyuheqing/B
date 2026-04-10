package com.github.tvbox.osc.constant;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class LiveConstants {
    // EPG 缓存相关
    public static final String EPG_CACHE_DIR = "epg_cache";
    public static final long EPG_CACHE_VALID_TIME = 24 * 60 * 60 * 1000; // 24小时
    public static final int MAX_EPG_MEMORY_CACHE = 10;
    public static final int EPG_MAX_ITEMS = 50;

    // 预加载相关
    public static final int HIGH_PRIORITY_THREADS = 2;
    public static final int LOW_PRIORITY_THREADS = 1;
    public static final int PRELOAD_DELAY_MS = 5000;
    public static final int PRELOAD_SLEEP_MS = 150;
    public static final int PRELOAD_OTHER_SLEEP_MS = 100;
    public static final int PRELOAD_DAYS_BEFORE = 6;
    public static final int PRELOAD_DAYS_AFTER = 2;

    // UI 自动隐藏延迟
    public static final int AUTO_HIDE_CHANNEL_LIST_MS = 6000;
    public static final int AUTO_HIDE_CHANNEL_INFO_MS = 6000;
    public static final int AUTO_HIDE_SETTINGS_MS = 5000;

    // 数字换台超时
    public static final int NUMERIC_TIMEOUT_MS = 2000;

    // EPG 默认地址
    public static final String DEFAULT_EPG_URL = "https://epg.112114.xyz/";

    // 播放器默认 User-Agent
    public static final String DEFAULT_USER_AGENT = "Lavf/59.27.100";

    // 时移相关
    public static final String PLTV_FLAG = "/PLTV/";
    public static final String TVOD_FLAG = "/TVOD/";
    public static final String PLAYSEEK_PARAM = "playseek=";

    // 日期时间格式
    public static final String DATE_FORMAT_YMD = "yyyy-MM-dd";
    public static final String DATE_FORMAT_YMD_NUM = "yyyyMMdd";
    public static final String DATE_FORMAT_YMDHMS = "yyyyMMddHHmmss";
    public static final String TIME_FORMAT_HHMM = "HH:mm";
    public static final String TIME_FORMAT_HHMMSS = "HH:mm:ss";

    // 节目占位符
    public static final String NO_PROGRAM = "暂无节目信息";
    public static final String NO_INFO = "No Information";

    // 频道号范围
    public static final int MAX_CHANNEL_GROUPS = 20;

    // 退出确认延迟
    public static final int EXIT_CONFIRM_DELAY_MS = 2000;

    // 其他
    public static final String DEFAULT_START_TIME = "00:00";
    public static final String DEFAULT_END_TIME = "23:59";

    // ========== 控制面板相关 ==========
    public static final float[] SPEEDS = {0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f};
    public static final long LIVE_REPLAY_WINDOW_MS = 24 * 60 * 60 * 1000L;   // 24小时
    public static final long SEGMENT_DURATION_MS = 8 * 60 * 60 * 1000L;       // 8小时
    public static final int SEGMENT_COUNT = 3;                                 // 3段
    public static final long CONTROL_PANEL_AUTO_HIDE_MS = 5000L;               // 5秒自动隐藏
    public static final long SEGMENT_SWITCH_THRESHOLD_MS = 8000L;              // 提前8秒切换

    /**
     * 获取 GMT+8 时区的 SimpleDateFormat（带秒）
     */
    public static SimpleDateFormat getGMT8FormatterWithSeconds() {
        SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT_HHMMSS, Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf;
    }

    /**
     * 获取 GMT+8 时区的 SimpleDateFormat（自定义格式）
     */
    public static SimpleDateFormat getGMT8Formatter(String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf;
    }
}
