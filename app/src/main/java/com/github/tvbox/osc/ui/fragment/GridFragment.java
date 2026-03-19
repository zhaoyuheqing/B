package com.github.tvbox.osc.ui.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.tv.widget.LoadMoreView;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.LOG;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯直播极简壳 - 最终可用版
 * - 无首页源依赖
 * - 内置 TXT 直播源
 * - 点击直跳 LivePlayActivity（传 url）
 * - 长按任意位置跳设置添加源
 * - 自定义 LiveChannel 类，避免 bean 字段问题
 */
public class GridFragment extends BaseLazyFragment {

    private TvRecyclerView mGridView;
    private SimpleChannelAdapter channelAdapter;
    private boolean isLoaded = false;

    // 内置 TXT 直播源地址（可替换）
    private static final String BUILT_IN_SOURCE = "https://frosty-block-011f.pohoy71288.workers.dev/";

    // 自定义频道类（只存必要信息）
    private static class LiveChannel {
        String name;     // 频道名称
        String playUrl;  // 播放地址
    }

    // 简单适配器（只显示频道名）
    private static class SimpleChannelAdapter extends BaseQuickAdapter<LiveChannel, BaseViewHolder> {
        public SimpleChannelAdapter() {
            super(R.layout.item_grid);  // 复用原布局，或换成简单 TextView 布局
        }

        @Override
        protected void convert(@NonNull BaseViewHolder holder, LiveChannel channel) {
            holder.setText(R.id.tvTitle, channel.name);  // 假设 item_grid 有 tvTitle
            // 如果布局有其他控件，可继续设置
        }
    }

    public static GridFragment newInstance() {
        return new GridFragment();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_grid;
    }

    @Override
    protected void init() {
        initView();
        loadChannels();
    }

    private void initView() {
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(mContext, 5));  // 列数可调

        channelAdapter = new SimpleChannelAdapter();
        mGridView.setAdapter(channelAdapter);

        // 焦点动画（遥控器选中放大）
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.15f).scaleY(1.15f).setDuration(200).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {}
        });

        // 长按整个网格（包括空白处）添加源
        mGridView.setOnLongClickListener(v -> {
            jumpToSetting();
            return true;
        });

        // 点击频道 → 直接播放
        channelAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            LiveChannel channel = adapter.getItem(position);
            if (channel != null && channel.playUrl != null && !channel.playUrl.isEmpty()) {
                Bundle bundle = new Bundle();
                bundle.putString("title", channel.name);
                bundle.putString("url", channel.playUrl);  // LivePlayActivity 读取 "url"
                jumpActivity(LivePlayActivity.class, bundle);
            } else {
                Toast.makeText(mContext, "无效播放地址", Toast.LENGTH_SHORT).show();
            }
        });

        // 长按频道也添加源
        channelAdapter.setOnItemLongClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            jumpToSetting();
            return true;
        });

        channelAdapter.setLoadMoreView(new LoadMoreView());
        channelAdapter.setEnableLoadMore(false);

        // 空状态提示（可点击添加源）
        TextView emptyTv = new TextView(mContext);
        emptyTv.setText("暂无直播频道\n\n长按遥控器任意位置 或 点击这里\n添加/更新直播源");
        emptyTv.setTextColor(0xFFFFFFFF);
        emptyTv.setTextSize(22);
        emptyTv.setGravity(Gravity.CENTER);
        emptyTv.setPadding(0, 400, 0, 0);
        emptyTv.setClickable(true);
        emptyTv.setFocusable(true);
        emptyTv.setFocusableInTouchMode(true);
        emptyTv.setOnClickListener(v -> jumpToSetting());
        channelAdapter.setEmptyView(emptyTv);
    }

    private void jumpToSetting() {
        jumpActivity(SettingActivity.class);
    }

    // 加载内置 TXT 源
    private void loadChannels() {
        new Thread(() -> {
            try {
                URL url = new URL(BUILT_IN_SOURCE);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                List<LiveChannel> channels = new ArrayList<>();
                String line;
                int index = 1;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    String[] parts = line.split(",", 2);
                    if (parts.length >= 2) {
                        LiveChannel ch = new LiveChannel();
                        ch.name = parts[0].trim();
                        ch.playUrl = parts[1].trim();
                        channels.add(ch);
                    }
                }
                reader.close();

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (channels.isEmpty()) {
                        showEmpty();
                    } else {
                        showSuccess();
                        isLoaded = true;
                        channelAdapter.setNewData(channels);
                    }
                });

            } catch (Exception e) {
                LOG.e("直播源加载失败", e);
                new Handler(Looper.getMainLooper()).post(this::showEmpty);
            }
        }).start();
    }

    // 手动刷新（添加源后可调用）
    public void forceRefresh() {
        channelAdapter.setNewData(null);
        loadChannels();
    }
}
