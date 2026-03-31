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
 * 左侧频道列表面板 - 最终完整版（已100%还原原脚本高亮记忆机制）
 * 关键修复：
 * - 每次 show() 无条件强制从 Activity 同步当前直播索引并高亮
 * - focusCurrentChannelRunnable 无限重试 + 每次执行前强制 sync
 * - 动画结束后再次触发高亮（双保险）
 */
public class LiveChannelListPanel {

    public interface ChannelListListener {
        void onGroupSelected(int groupIndex);
        void onChannelSelected(int groupIndex, int channelIndex);
        void onEpgModeChanged(boolean isEpg);
        void onShiyiPlaybackStarted();
        List<LiveChannelGroup> getChannelGroups();
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

    private int currentGroupIndex = 0;
    private int currentChannelIndex = -1;

    private final Runnable hideRunnable = this::hideInternal;

    private final Runnable focusCurrentChannelRunnable = new Runnable() {
        private int retryCount = 0;
        private static final int MAX_RETRY = 15;

        @Override
        public void run() {
            TvRecyclerView groupView = groupViewRef.get();
            TvRecyclerView channelView = channelViewRef.get();
            if (groupView == null || channelView == null || groupAdapter == null || channelAdapter == null) return;

            // 每次执行前强制从 Activity 同步当前直播索引（核心修复）
            if (listener != null) {
                currentGroupIndex = listener.getCurrentGroupIndex();
                currentChannelIndex = listener.getCurrentChannelIndex();
                syncHighlightFromActivity(currentGroupIndex, currentChannelIndex);
            }

            if (groupView.isScrolling() || channelView.isScrolling() ||
                    groupView.isComputingLayout() || channelView.isComputingLayout()) {
                if (retryCount++ < MAX_RETRY) {
                    handler.postDelayed(this, 80);
                }
                return;
            }

            groupAdapter.setSelectedGroupIndex(currentGroupIndex);
            channelAdapter.setSelectedChannelIndex(currentChannelIndex);

            groupView.scrollToPosition(currentGroupIndex);
            groupView.setSelection(currentGroupIndex);
            channelView.scrollToPosition(currentChannelIndex);
            channelView.setSelection(currentChannelIndex);

            if (currentChannelIndex >= 0 && currentChannelIndex < channelAdapter.getItemCount()) {
                RecyclerView.ViewHolder holder = channelView.findViewHolderForAdapterPosition(currentChannelIndex);
                if (holder != null && holder.itemView != null) {
                    holder.itemView.requestFocus();
                    retryCount = 0;
                    return;
                }
            }

            if (retryCount++ < MAX_RETRY) {
                handler.postDelayed(this, 100);
            }
        }
    };

    public LiveChannelListPanel(@NonNull Context context,
                                @NonNull Handler handler,
                                @NonNull LinearLayout rootView,
                                @NonNull TvRecyclerView groupView,
                                @NonNull TvRecyclerView channelView,
                                @NonNull LinearLayout groupEpg,
                                @NonNull LinearLayout divLeft,
                                @NonNull LinearLayout divRight,
                                @NonNull TvRecyclerView epgDate,
                                @NonNull TvRecyclerView epgInfo) {

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
        this.currentGroupIndex = groupIndex;
        this.currentChannelIndex = channelIndex;
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
        this.currentGroupIndex = groupIndex;
        this.currentChannelIndex = channelIndex;
        if (groupAdapter != null) {
            groupAdapter.setNewData(groups);
            groupAdapter.setSelectedGroupIndex(groupIndex);
        }
        List<LiveChannelItem> channels = null;
        if (groupIndex >= 0 && groups != null && groupIndex < groups.size()) {
            channels = groups.get(groupIndex).getLiveChannels();
        }
        if (channelAdapter != null) {
            channelAdapter.setNewData(channels != null ? channels : new ArrayList<>());
            channelAdapter.setSelectedChannelIndex(channelIndex);
        }
    }

    public void updateSelectionAndScroll(int groupIndex, int channelIndex) {
        this.currentGroupIndex = groupIndex;
        this.currentChannelIndex = channelIndex;
        if (groupAdapter != null) groupAdapter.setSelectedGroupIndex(groupIndex);
        if (channelAdapter != null) channelAdapter.setSelectedChannelIndex(channelIndex);
    }

    public void loadGroup(int groupIndex, List<LiveChannelGroup> allGroups) {
        if (isEpgMode) {
            showChannelMode();
        }
        if (groupAdapter != null) {
            groupAdapter.setSelectedGroupIndex(groupIndex);
        }
        List<LiveChannelItem> channels = null;
        if (groupIndex >= 0 && allGroups != null && groupIndex < allGroups.size()) {
            channels = allGroups.get(groupIndex).getLiveChannels();
        }
        if (channelAdapter != null) {
            channelAdapter.setNewData(channels != null ? channels : new ArrayList<>());
            channelAdapter.setSelectedChannelIndex(0);
        }
        syncHighlightFromActivity(groupIndex, 0);
    }

    public void showEpgMode() {
        if (isEpgMode) return;
        isEpgMode = true;
        setEpgViewsVisible(true);
        setChannelViewsVisible(false);
        if (listener != null) listener.onEpgModeChanged(true);
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
            refreshFull(listener.getChannelGroups(), listener.getCurrentGroupIndex(), listener.getCurrentChannelIndex());
        }
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
        handler.postDelayed(focusCurrentChannelRunnable, 220);
    }

    public void onShiyiPlaybackStarted() {
        if (listener != null) listener.onShiyiPlaybackStarted();
    }

    public void show() {
        if (listener != null) {
            syncHighlightFromActivity(listener.getCurrentGroupIndex(), listener.getCurrentChannelIndex());
        }

        if (isEpgMode) {
            showEpgMode();
        } else {
            showChannelMode();
        }

        LinearLayout rootView = rootViewRef.get();
        if (rootView != null && rootView.getVisibility() != View.VISIBLE) {
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
                            handler.postDelayed(focusCurrentChannelRunnable, 100);
                        }
                    });
        }

        isShowing = true;

        handler.removeCallbacks(focusCurrentChannelRunnable);
        handler.postDelayed(focusCurrentChannelRunnable, 220);

        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
    }

    public void hide() {
        hideInternal();
    }

    public boolean isShowing() {
        return isShowing;
    }

    public boolean isEpgMode() {
        return isEpgMode;
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
            @Override public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (groupAdapter != null) groupAdapter.setFocusedGroupIndex(position);
            }
            @Override public void onItemClick(TvRecyclerView parent, View itemView, int position) {
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
            @Override public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (position < 0) return;
                if (groupAdapter != null) groupAdapter.setFocusedGroupIndex(-1);
                if (channelAdapter != null) channelAdapter.setFocusedChannelIndex(position);
                handler.removeCallbacks(hideRunnable);
                handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            }
            @Override public void onItemClick(TvRecyclerView parent, View itemView, int position) {
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
