package com.github.tvbox.osc.ui.panel;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 左侧频道列表面板 - 最终稳定版
 * 职责：纯UI展示 + 用户交互
 * 所有业务状态由 Activity 维护，Panel 通过 refreshFull / loadGroup 等方法更新
 */
public class LiveChannelListPanel {

    public interface ChannelListListener {
        void onGroupSelected(int groupIndex);
        void onChannelSelected(int groupIndex, int channelIndex);
    }

    private final WeakReference<Context> contextRef;
    private final Handler handler;

    private final WeakReference<LinearLayout> rootViewRef;
    private final WeakReference<TvRecyclerView> groupViewRef;
    private final WeakReference<TvRecyclerView> channelViewRef;

    private LiveChannelGroupAdapter groupAdapter;
    private LiveChannelItemAdapter channelAdapter;

    private ChannelListListener listener;
    private boolean isShowing = false;

    private final Runnable hideRunnable = this::hideInternal;
    private final Runnable focusAndShowRunnable = this::focusAndShowInternal;

    public LiveChannelListPanel(@NonNull Context context,
                                @NonNull Handler handler,
                                @NonNull LinearLayout rootView,
                                @NonNull TvRecyclerView groupView,
                                @NonNull TvRecyclerView channelView) {

        this.contextRef = new WeakReference<>(context);
        this.handler = handler;
        this.rootViewRef = new WeakReference<>(rootView);
        this.groupViewRef = new WeakReference<>(groupView);
        this.channelViewRef = new WeakReference<>(channelView);

        rootView.setVisibility(View.INVISIBLE);
    }

    public void setListener(ChannelListListener listener) {
        this.listener = listener;
    }

    public void init() {
        initGroupView();
        initChannelView();
    }

    // ==================== 统一刷新入口 ====================

    /**
     * 完整刷新面板（推荐主要使用这个方法）
     */
    public void refreshFull(List<LiveChannelGroup> groups, int currentGroupIndex, int currentChannelIndex) {
        if (groupAdapter != null) {
            groupAdapter.setNewData(groups);
            groupAdapter.setSelectedGroupIndex(currentGroupIndex);
        }

        List<LiveChannelItem> channels = null;
        if (currentGroupIndex >= 0 && groups != null && currentGroupIndex < groups.size()) {
            channels = groups.get(currentGroupIndex).getLiveChannels();
        }
        if (channelAdapter != null) {
            channelAdapter.setNewData(channels != null ? channels : new ArrayList<>());
            channelAdapter.setSelectedChannelIndex(currentChannelIndex);
        }

        if (isShowing) {
            scrollToCurrent(currentGroupIndex, currentChannelIndex);
        }
    }

    /**
     * 更新高亮和滚动（播放频道切换后调用）
     */
    public void updateSelectionAndScroll(int groupIndex, int channelIndex) {
        if (groupAdapter != null) groupAdapter.setSelectedGroupIndex(groupIndex);
        if (channelAdapter != null) channelAdapter.setSelectedChannelIndex(channelIndex);

        if (isShowing) {
            scrollToCurrent(groupIndex, channelIndex);
        }
    }

    /**
     * 切换到指定分组并加载其频道列表（密码验证成功后强烈推荐使用）
     */
    public void loadGroup(int groupIndex, List<LiveChannelGroup> allGroups) {
        if (groupAdapter != null) {
            groupAdapter.setSelectedGroupIndex(groupIndex);
        }

        List<LiveChannelItem> channels = null;
        if (groupIndex >= 0 && allGroups != null && groupIndex < allGroups.size()) {
            channels = allGroups.get(groupIndex).getLiveChannels();
        }

        if (channelAdapter != null) {
            channelAdapter.setNewData(channels != null ? channels : new ArrayList<>());
            channelAdapter.setSelectedChannelIndex(-1); // 默认不选中具体频道
        }

        if (isShowing) {
            scrollToCurrent(groupIndex, -1);
        }
    }

    private void scrollToCurrent(int groupIndex, int channelIndex) {
        TvRecyclerView gv = groupViewRef.get();
        if (gv != null && groupIndex >= 0) {
            gv.scrollToPosition(groupIndex);
            gv.setSelection(groupIndex);
        }

        TvRecyclerView cv = channelViewRef.get();
        if (cv != null && channelIndex >= 0) {
            cv.scrollToPosition(channelIndex);
            cv.setSelection(channelIndex);
        }
    }

    // ==================== 显示/隐藏 ====================

    public void show() {
        if (isShowing) {
            handler.removeCallbacks(hideRunnable);
            handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            return;
        }

        handler.postDelayed(focusAndShowRunnable, 200);
        isShowing = true;
    }

    public void hide() {
        hideInternal();
    }

    public boolean isShowing() {
        return isShowing;
    }

    // ==================== 初始化 ====================

    private void initGroupView() {
        TvRecyclerView groupView = groupViewRef.get();
        if (groupView == null) return;

        groupView.setHasFixedSize(true);
        groupView.setLayoutManager(new V7LinearLayoutManager(groupView.getContext(), 1, false));

        groupAdapter = new LiveChannelGroupAdapter();
        groupView.setAdapter(groupAdapter);

        groupView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (groupAdapter != null) groupAdapter.setFocusedGroupIndex(position);
                if (channelAdapter != null) channelAdapter.setFocusedChannelIndex(-1);
            }
        });

        groupAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            if (listener != null) listener.onGroupSelected(position);
        });
    }

    private void initChannelView() {
        TvRecyclerView channelView = channelViewRef.get();
        if (channelView == null) return;

        channelView.setHasFixedSize(true);
        channelView.setLayoutManager(new V7LinearLayoutManager(channelView.getContext(), 1, false));

        channelAdapter = new LiveChannelItemAdapter();
        channelView.setAdapter(channelAdapter);

        channelView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (position < 0) return;
                if (groupAdapter != null) groupAdapter.setFocusedGroupIndex(-1);
                if (channelAdapter != null) channelAdapter.setFocusedChannelIndex(position);

                handler.removeCallbacks(hideRunnable);
                handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                clickChannel(position);
            }
        });

        channelAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            clickChannel(position);
        });
    }

    private void clickChannel(int position) {
        if (listener == null || groupAdapter == null) return;

        int groupIndex = groupAdapter.getSelectedGroupIndex();
        if (groupIndex < 0) return;

        handler.removeCallbacks(hideRunnable);
        handler.post(hideRunnable);

        listener.onChannelSelected(groupIndex, position);
    }

    // ==================== 动画 ====================

    private void focusAndShowInternal() {
        TvRecyclerView groupView = groupViewRef.get();
        TvRecyclerView channelView = channelViewRef.get();
        LinearLayout rootView = rootViewRef.get();

        if (groupView == null || rootView == null) {
            isShowing = false;
            return;
        }

        boolean isScrolling = groupView.isScrolling() ||
                (channelView != null && channelView.isScrolling()) ||
                groupView.isComputingLayout() ||
                (channelView != null && channelView.isComputingLayout());

        if (isScrolling) {
            handler.postDelayed(focusAndShowRunnable, 100);
            return;
        }

        groupView.scrollToPosition(0);
        groupView.setSelection(0);
        groupView.requestFocus();

        rootView.setVisibility(View.VISIBLE);
        rootView.setAlpha(0.0f);
        rootView.setTranslationX(-rootView.getWidth() / 2f);
        rootView.animate()
                .translationX(0)
                .alpha(1.0f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(null);

        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
    }

    private void hideInternal() {
        LinearLayout rootView = rootViewRef.get();
        if (rootView == null || rootView.getVisibility() != View.VISIBLE) return;

        rootView.animate()
                .translationX(-rootView.getWidth() / 2f)
                .alpha(0.0f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        LinearLayout view = rootViewRef.get();
                        if (view != null) view.setVisibility(View.INVISIBLE);
                        isShowing = false;
                    }
                });

        handler.removeCallbacks(hideRunnable);
    }

    public void destroy() {
        handler.removeCallbacks(hideRunnable);
        handler.removeCallbacks(focusAndShowRunnable);

        if (groupAdapter != null) {
            groupAdapter.setNewData(null);
            groupAdapter = null;
        }
        if (channelAdapter != null) {
            channelAdapter.setNewData(null);
            channelAdapter = null;
        }

        listener = null;
        isShowing = false;

        LinearLayout root = rootViewRef.get();
        if (root != null && root.getVisibility() == View.VISIBLE) {
            root.setVisibility(View.INVISIBLE);
            root.clearAnimation();
        }
    }
}
