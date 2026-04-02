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
        void onEpgItemClicked(Epginfo epgItem, int position, int selectedDateIndex);
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
    private final Runnable focusCurrentChannelRunnable = new Runnable() {
        private int retryCount = 0;
        private static final int MAX_RETRY = 15;
        @Override
        public void run() {
            TvRecyclerView groupView = groupViewRef.get();
            TvRecyclerView channelView = channelViewRef.get();
            if (groupView == null || channelView == null || listener == null) return;
            int groupIdx = listener.getCurrentGroupIndex();
            int channelIdx = listener.getCurrentChannelIndex();
            if (groupAdapter != null) {
                groupAdapter.setSelectedGroupIndex(groupIdx);
            }
            if (channelAdapter != null) {
                channelAdapter.setSelectedChannelIndex(channelIdx);
            }
            if (groupView.isScrolling() || channelView.isScrolling() ||
                    groupView.isComputingLayout() || channelView.isComputingLayout()) {
                if (retryCount++ < MAX_RETRY) {
                    handler.postDelayed(this, 100);
                }
                return;
            }
            groupView.scrollToPosition(groupIdx);
            groupView.setSelection(groupIdx);
            channelView.post(() -> {
                channelView.scrollToPosition(channelIdx);
                channelView.setSelection(channelIdx);
                RecyclerView.ViewHolder holder = channelView.findViewHolderForAdapterPosition(channelIdx);
                if (holder != null && holder.itemView != null) {
                    holder.itemView.requestFocus();
                } else {
                    channelView.postDelayed(() -> {
                        RecyclerView.ViewHolder holder2 = channelView.findViewHolderForAdapterPosition(channelIdx);
                        if (holder2 != null && holder2.itemView != null) {
                            holder2.itemView.requestFocus();
                        }
                    }, 150);
                }
            });
            retryCount = 0;
        }
    };
    private final Runnable focusEpgRunnable = new Runnable() {
        private int retryCount = 0;
        private static final int MAX_RETRY = 10;
        @Override
        public void run() {
            TvRecyclerView epgInfo = epgInfoViewRef.get();
            if (epgInfo == null) return;
            if (epgInfo.getVisibility() == View.VISIBLE) {
                epgInfo.requestFocus();
                retryCount = 0;
            } else {
                TvRecyclerView epgDate = epgDateViewRef.get();
                if (epgDate != null && epgDate.getVisibility() == View.VISIBLE) {
                    epgDate.requestFocus();
                    retryCount = 0;
                } else if (retryCount++ < MAX_RETRY) {
                    handler.postDelayed(this, 100);
                }
            }
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

    // ========== 数据刷新 ==========
    public void refreshFull(List<LiveChannelGroup> groups, int groupIndex, int channelIndex) {
        if (groupAdapter != null) {
            groupAdapter.setNewData(groups);
            groupAdapter.setSelectedGroupIndex(groupIndex);
        }
        if (channelAdapter != null) {
            List<LiveChannelItem> channels = listener != null ? listener.getLiveChannels(groupIndex) : new ArrayList<>();
            channelAdapter.setNewData(channels);
            channelAdapter.setSelectedChannelIndex(channelIndex);
        }
    }

    public void updateCurrentSelection(int groupIndex, int channelIndex) {
        if (groupAdapter != null) {
            groupAdapter.setSelectedGroupIndex(groupIndex);
        }
        if (channelAdapter != null) {
            channelAdapter.setSelectedChannelIndex(channelIndex);
            if (isShowing) {
                TvRecyclerView channelView = channelViewRef.get();
                if (channelView != null) {
                    channelView.post(() -> {
                        channelView.scrollToPosition(channelIndex);
                        channelView.setSelection(channelIndex);
                    });
                }
            }
        }
    }

    // 修正：点击分组时，若分组为当前播放分组则高亮当前频道，否则清除高亮
    public void loadGroup(int groupIndex, List<LiveChannelGroup> allGroups) {
        if (isEpgMode) showChannelMode();
        if (groupAdapter != null) groupAdapter.setSelectedGroupIndex(groupIndex);
        if (channelAdapter != null) {
            List<LiveChannelItem> channels = listener != null ? listener.getLiveChannels(groupIndex) : new ArrayList<>();
            channelAdapter.setNewData(channels);
            int currentGroup = listener != null ? listener.getCurrentGroupIndex() : -1;
            int currentChannel = listener != null ? listener.getCurrentChannelIndex() : -1;
            if (groupIndex == currentGroup && currentChannel >= 0 && currentChannel < channels.size()) {
                channelAdapter.setSelectedChannelIndex(currentChannel);
            } else {
                channelAdapter.setSelectedChannelIndex(-1);
            }
        }
    }

    // ========== EPG 回调 ==========
    public void notifyEpgClicked(Epginfo item, int position, int dateIndex) {
        resetHideTimer();
        if (listener != null) {
            listener.onEpgItemClicked(item, position, dateIndex);
        }
    }

    // ========== 模式切换 ==========
    public void showEpgMode() {
        if (isEpgMode) return;
        isEpgMode = true;
        setEpgViewsVisible(true);
        setChannelViewsVisible(false);
        if (listener != null) listener.onEpgModeChanged(true);
        resetHideTimer();
        // 焦点恢复
        TvRecyclerView epgInfo = epgInfoViewRef.get();
        if (epgInfo != null && epgInfo.getVisibility() == View.VISIBLE) {
            handler.postDelayed(() -> epgInfo.requestFocus(), 100);
        }
    }

    public void showChannelMode() {
        if (isEpgMode) {
            isEpgMode = false;
            setEpgViewsVisible(false);
            setChannelViewsVisible(true);
            if (listener != null) {
                listener.onEpgModeChanged(false);
                refreshFull(listener.getChannelGroups(), listener.getCurrentGroupIndex(), listener.getCurrentChannelIndex());
            }
        } else {
            setEpgViewsVisible(false);
            setChannelViewsVisible(true);
            if (listener != null) {
                updateCurrentSelection(listener.getCurrentGroupIndex(), listener.getCurrentChannelIndex());
            }
        }
        resetHideTimer();
    }

    public boolean isEpgMode() {
        return isEpgMode;
    }

    // ========== 显示/隐藏 ==========
    // 显示时强制刷新全量数据，确保界面同步
    public void show() {
        LinearLayout rootView = rootViewRef.get();
        if (rootView == null) return;

        if (listener != null) {
            refreshFull(listener.getChannelGroups(), listener.getCurrentGroupIndex(), listener.getCurrentChannelIndex());
        }

        if (isEpgMode) {
            setEpgViewsVisible(true);
            setChannelViewsVisible(false);
        } else {
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
                            handler.removeCallbacks(focusCurrentChannelRunnable);
                            handler.removeCallbacks(focusEpgRunnable);
                            if (isEpgMode) {
                                handler.postDelayed(focusEpgRunnable, 300);
                            } else {
                                handler.post(focusCurrentChannelRunnable);
                            }
                        }
                    });
        } else {
            handler.removeCallbacks(focusCurrentChannelRunnable);
            handler.removeCallbacks(focusEpgRunnable);
            if (isEpgMode) {
                handler.postDelayed(focusEpgRunnable, 300);
            } else {
                handler.post(focusCurrentChannelRunnable);
            }
        }
        isShowing = true;
        resetHideTimer();
    }

    public void hide() {
        hideInternal();
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void resetHideTimer() {
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
    }

    // ========== 内部方法 ==========
    private void setEpgViewsVisible(boolean visible) {
        LinearLayout groupEpg = groupEpgRef.get();
        LinearLayout divLeft = divLeftRef.get();
        TvRecyclerView epgInfo = epgInfoViewRef.get();
        TvRecyclerView epgDate = epgDateViewRef.get();
        if (groupEpg != null) groupEpg.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (divLeft != null) divLeft.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (epgInfo != null) epgInfo.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (epgDate != null) epgDate.setVisibility(visible ? View.VISIBLE : View.GONE);
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
                resetHideTimer();
            }
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                FastClickCheckUtil.check(itemView);
                resetHideTimer();
                if (listener != null) listener.onGroupSelected(position);
            }
        });
        groupAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            resetHideTimer();
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
                resetHideTimer();
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
        resetHideTimer();
        if (listener == null) return;
        int groupIndex = listener.getCurrentGroupIndex();
        if (groupIndex < 0) return;
        listener.onChannelSelected(groupIndex, position);
        hide();
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
        handler.removeCallbacks(focusEpgRunnable);
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
