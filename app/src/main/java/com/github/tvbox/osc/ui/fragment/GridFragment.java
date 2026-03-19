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
import com.chad.library.adapter.base.BaseViewHolder;          // ← 与原版一致（旧版 Brvah 路径）
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.MovieSort;
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
 * 纯直播极简壳 - 引用已与原版一致（使用旧版 Brvah 路径）
 */
public class GridFragment extends BaseLazyFragment {

    private TvRecyclerView mGridView;
    private SimpleChannelAdapter channelAdapter;
    private boolean isLoaded = false;

    private static final String BUILT_IN_SOURCE = "https://frosty-block-011f.pohoy71288.workers.dev/";

    private static class LiveChannel {
        String name;
        String playUrl;
    }

    private static class SimpleChannelAdapter extends BaseQuickAdapter<LiveChannel, BaseViewHolder> {
        public SimpleChannelAdapter() {
            super(R.layout.item_grid);
        }

        @Override
        protected void convert(@NonNull BaseViewHolder holder, LiveChannel channel) {
            holder.setText(R.id.tvTitle, channel.name);  // 确保 item_grid.xml 有 tvTitle
        }
    }

    // 无参版本（主用）
    public static GridFragment newInstance() {
        return new GridFragment();
    }

    // 兼容旧版 HomeActivity 调用（忽略 SortData 参数）
    public static GridFragment newInstance(MovieSort.SortData sortData) {
        return newInstance();
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

        // 焦点动画
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

        mGridView.setOnLongClickListener(v -> {
            jumpToSetting();
            return true;
        });

        channelAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            LiveChannel channel = adapter.getItem(position);
            if (channel != null && channel.playUrl != null && !channel.playUrl.isEmpty()) {
                Bundle bundle = new Bundle();
                bundle.putString("title", channel.name);
                bundle.putString("url", channel.playUrl);
                jumpActivity(LivePlayActivity.class, bundle);
            } else {
                Toast.makeText(mContext, "无效播放地址", Toast.LENGTH_SHORT).show();
            }
        });

        channelAdapter.setOnItemLongClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            jumpToSetting();
            return true;
        });

        channelAdapter.setLoadMoreView(new LoadMoreView());
        channelAdapter.setEnableLoadMore(false);

        TextView emptyTv = new TextView(mContext);
        emptyTv.setText("暂无直播频道\n\n长按任意位置 或 点击这里\n添加直播源");
        emptyTv.setTextColor(0xFFFFFFFF);
        emptyTv.setTextSize(22);
        emptyTv.setGravity(Gravity.CENTER);
        emptyTv.setPadding(0, 400, 0, 0);
        emptyTv.setClickable(true);
        emptyTv.setFocusable(true);
        emptyTv.setOnClickListener(v -> jumpToSetting());
        channelAdapter.setEmptyView(emptyTv);
    }

    private void jumpToSetting() {
        jumpActivity(SettingActivity.class);
    }

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

    public void forceRefresh() {
        channelAdapter.setNewData(null);
        loadChannels();
    }
}
