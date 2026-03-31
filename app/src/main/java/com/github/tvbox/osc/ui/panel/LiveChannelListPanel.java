package com.github.tvbox.osc.ui.panel;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class LiveChannelListPanel {

    public interface ChannelListListener {
        void onGroupSelected(int groupIndex);
        void onChannelSelected(int groupIndex, int channelIndex);
        void onEpgModeChanged(boolean isEpg);
        void onEpgItemClicked(Epginfo epgItem, int selectedDateIndex);

        List<LiveChannelGroup> getChannelGroups();
        List<LiveChannelItem> getLiveChannels(int groupIndex);
        int getCurrentGroupIndex();
        int getCurrentChannelIndex();
        void updateCurrentChannel(int groupIndex, int channelIndex);
        boolean isNeedInputPassword(int groupIndex);
    }

    private final WeakReference<Context> contextRef;
    private final Handler handler;

    private final WeakReference<LinearLayout> rootViewRef;
    private final WeakReference<TvRecyclerView> groupViewRef;
    private final WeakReference<TvRecyclerView> channelViewRef;

    private final WeakReference<LinearLayout> groupEpgRef;
    private final WeakReference<LinearLayout> divLeftRef;
    private final WeakReference<LinearLayout> divRightRef;
    private final WeakReference<TvRecyclerView> epgDateViewRef;
    private final WeakReference<TvRecyclerView> epgInfoViewRef;

    private LiveChannelGroupAdapter groupAdapter;
    private LiveChannelItemAdapter channelAdapter;

    private ChannelListListener listener;
    private boolean isShowing = false;
    private boolean isEpgMode = false;

    private final Runnable hideRunnable = this::hideInternal;

    // 简化后的焦点同步逻辑：仅使用 setSelection，并确保在布局完成后执行
    private final Runnable focusCurrentChannelRunnable = new Runnable() {
        @Override
        public void run() {
            TvRecyclerView groupView = groupViewRef.get();
            TvRecyclerView channelView = channelViewRef.get();
            if (groupView == null || channelView == null || listener == null) return;

            int groupIdx = listener.getCurrentGroupIndex();
            int channelIdx = listener.getCurrentChannelIndex();

            // 仅使用 setSelection，它内部会处理滚动和焦点
            groupView.setSelection(groupIdx);
            channelView.setSelection(channelIdx);
        }
    };

    public LiveChannelListPanel(@NonNull Context context, @NonNull Handler handler,
                                @NonNull LinearLayout rootView, @NonNull TvRecyclerView groupView,
                                @NonNull TvRecyclerView channelView, @NonNull LinearLayout groupEpg,
                                @NonNull LinearLayout divLeft, @NonNull LinearLayout divRight,
                                @NonNull TvRecyclerView epgDate, @NonNull TvRecyclerView epgInfo) {
        this.contextRef = new WeakReference<>(context);
        this.handler = handler;
        this.rootViewRef = new WeakReference<>(rootView);
        this.groupViewRef = new WeakReference<>(groupView);
        this.channelViewRef = new WeakReference<>(channelView);
        this.groupEpgRef = new WeakReference<>(groupEpg);
        this.divLeftRef = new WeakReference<>(divLeft);
        this.divRightRef = new WeakReference<>(divRight);
        this.epgDateViewRef = new WeakReference<>(epgDate);
        this.epgInfoViewRef = new WeakReference<>(epgInfo);
        rootView.setVisibility(View.INVISIBLE);
    }

    public void setListener(ChannelListListener listener) {
        this.listener = listener;
    }

    public void init() {
        initGroupView();
        initChannelView();
    }

    public void syncHighlightFromActivity(int groupIndex, int channelIndex) {
        if (groupAdapter != null) {
            groupAdapter.setSelectedGroupIndex(groupIndex);
            groupAdapter.setFocusedGroupIndex(groupIndex);
        }
        if (channelAdapter != null) {
            channelAdapter.setSelectedChannelIndex(channelIndex);
            channelAdapter.setFocusedChannelIndex(channelIndex);
        }
    }

    public void refreshFull(List<LiveChannelGroup> groups, int groupIndex, int channelIndex) {
        if (groupAdapter != null) {
            groupAdapter.setNewData(groups);
            groupAdapter.setSelectedGroupIndex(groupIndex);
        }
        List<LiveChannelItem> channels = listener != null ? listener.getLiveChannels(groupIndex) : new ArrayList<>();
        if (channelAdapter != null) {
            channelAdapter.setNewData(channels);
            channelAdapter.setSelectedChannelIndex(channelIndex);
        }
        // 数据刷新后，在布局完成后同步焦点
        TvRecyclerView channelView = channelViewRef.get();
        if (channelView != null) {
            channelView.post(() -> channelView.setSelection(channelIndex));
        }
    }

    public void refreshChannelData(List<LiveChannelItem> channels, int channelIndex) {
        if (channelAdapter != null) {
            channelAdapter.setNewData(channels);
            channelAdapter.setSelectedChannelIndex(channelIndex);
        }
        // 数据刷新后，在布局完成后同步焦点
        TvRecyclerView channelView = channelViewRef.get();
        if (channelView != null) {
            channelView.post(() -> channelView.setSelection(channelIndex));
        }
    }

    public void updateSelectionAndScroll(int groupIndex, int channelIndex) {
        if (groupAdapter != null) groupAdapter.setSelectedGroupIndex(groupIndex);
        if (channelAdapter != null) channelAdapter.setSelectedChannelIndex(channelIndex);
        // 确保焦点同步
        TvRecyclerView groupView = groupViewRef.get();
        TvRecyclerView channelView = channelViewRef.get();
        if (groupView != null) groupView.setSelection(groupIndex);
        if (channelView != null) channelView.setSelection(channelIndex);
    }

    public void loadGroup(int groupIndex, List<LiveChannelGroup> allGroups) {
        if (isEpgMode) showChannelMode();
        if (groupAdapter != null) groupAdapter.setSelectedGroupIndex(groupIndex);

        // 分组改变后，频道列表应显示第一个频道（不播放），所以选中项设为 0
        int targetIndex = 0;

        List<LiveChannelItem> channels = listener != null ? listener.getLiveChannels(groupIndex) : new ArrayList<>();
        if (channelAdapter != null) {
            channelAdapter.setNewData(channels);
            channelAdapter.setSelectedChannelIndex(targetIndex);
        }
        syncHighlightFromActivity(groupIndex, targetIndex);
        // 布局完成后同步焦点
        TvRecyclerView channelView = channelViewRef.get();
        if (channelView != null) {
            channelView.post(() -> channelView.setSelection(targetIndex));
        }
    }

    public void notifyEpgClicked(Epginfo item, int dateIndex) {
        if (listener != null) listener.onEpgItemClicked(item, dateIndex);
    }

    public void showEpgMode() {
        if (isEpgMode) return;
        isEpgMode = true;
        setEpgViewsVisible(true);
        setChannelViewsVisible(false);
        if (listener != null) listener.onEpgModeChanged(true);
        // 显示后重置定时器
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
    }

    public void showChannelMode() {
        if (!isEpgMode) return;
        isEpgMode = false;
        setEpgViewsVisible(false);
        setChannelViewsVisible(true);
        if (listener != null) {
            listener.onEpgModeChanged(false);
            // 刷新频道数据，保证列表显示正确
            int groupIdx = listener.getCurrentGroupIndex();
            int channelIdx = listener.getCurrentChannelIndex();
            refreshFull(listener.getChannelGroups(), groupIdx, channelIdx);
        }
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
        // 在布局完成后同步焦点
        TvRecyclerView channelView = channelViewRef.get();
        if (channelView != null) {
            channelView.post(focusCurrentChannelRunnable);
        }
    }

    public void show() {
        LinearLayout rootView = rootViewRef.get();
        if (rootView == null) return;

        if (isEpgMode) {
            setEpgViewsVisible(true);
            setChannelViewsVisible(false);
        } else {
            // 显示频道模式前，先刷新数据
            if (listener != null) {
                int groupIdx = listener.getCurrentGroupIndex();
                int channelIdx = listener.getCurrentChannelIndex();
                refreshFull(listener.getChannelGroups(), groupIdx, channelIdx);
            }
            setEpgViewsVisible(false);
            setChannelViewsVisible(true);
        }

        if (rootView.getVisibility() != View.VISIBLE) {
            rootView.setVisibility(View.VISIBLE);
            rootView.setAlpha(0.0f);
            rootView.setTranslationX(-rootView.getWidth() / 2f);
            rootView.animate()
                    .translationX(0)
                    .alpha(1.0f)
                    .setDuration(250)
                    .setInterpolator(new DecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // 动画结束后强制同步焦点
                            handler.post(focusCurrentChannelRunnable);
                        }
                    });
        } else {
            // 如果已经可见，仍然需要同步焦点
            handler.post(focusCurrentChannelRunnable);
        }

        isShowing = true;
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
    }

    public void hide() {
        hideInternal();
    }

    public boolean isShowing() {
        return isShowing;
    }

    private void setEpgViewsVisible(boolean visible) {
        LinearLayout groupEpg = groupEpgRef.get();
        LinearLayout divLeft = divLeftRef.get();
        TvRecyclerView epgInfo = epgInfoViewRef.get();
        if (groupEpg != null) groupEpg.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (divLeft != null) divLeft.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (epgInfo != null) epgInfo.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setChannelViewsVisible(boolean visible) {
        LinearLayout divRight = divRightRef.get();
        TvRecyclerView groupView = groupViewRef.get();
        if (divRight != null) divRight.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (groupView != null) groupView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void initGroupView() {
        TvRecyclerView groupView = groupViewRef.get();
        if (groupView == null) return;
        groupView.setHasFixedSize(true);
        groupView.setLayoutManager(new V7LinearLayoutManager(groupView.getContext(), 1, false));
        groupAdapter = new LiveChannelGroupAdapter();
        groupView.setAdapter(groupAdapter);
        groupView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (groupAdapter != null) groupAdapter.setFocusedGroupIndex(position);
                // 分组列表焦点移动时，重置自动隐藏定时器
                handler.removeCallbacks(hideRunnable);
                handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                FastClickCheckUtil.check(itemView);
                if (listener != null) listener.onGroupSelected(position);
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
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (position < 0) return;
                if (groupAdapter != null) groupAdapter.setFocusedGroupIndex(-1);
                if (channelAdapter != null) channelAdapter.setFocusedChannelIndex(position);
                // 频道列表焦点移动时，重置自动隐藏定时器
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

    private void hideInternal() {
        LinearLayout rootView = rootViewRef.get();
        if (rootView == null || rootView.getVisibility() != View.VISIBLE) return;

        rootView.animate()
                .translationX(-rootView.getWidth() / 2f)
                .alpha(0.0f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootView.setVisibility(View.INVISIBLE);
                        isShowing = false;
                    }
                });

        handler.removeCallbacks(hideRunnable);
    }

    public void destroy() {
        handler.removeCallbacks(hideRunnable);
        handler.removeCallbacks(focusCurrentChannelRunnable);

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
        isEpgMode = false;

        LinearLayout root = rootViewRef.get();
        if (root != null && root.getVisibility() == View.VISIBLE) {
            root.setVisibility(View.INVISIBLE);
            root.clearAnimation();
        }
    }
}
