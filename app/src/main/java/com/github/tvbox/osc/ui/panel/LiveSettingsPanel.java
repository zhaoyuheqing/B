package com.github.tvbox.osc.ui.panel;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.ui.adapter.LiveSettingGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingItemAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;

import com.orhanobut.hawk.Hawk;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LiveSettingsPanel {

    public interface SettingsListener {
        void onSourceChanged(int sourceIndex);
        void onScaleChanged(int scaleIndex);
        void onPlayerTypeChanged(int typeIndex);
        void onTimeoutChanged(int timeoutIndex);
        void onPreferenceChanged(String key, boolean value);
        void onLiveAddressSelected();
        void onExit();
        int getCurrentPlayerType();
        int getCurrentScale();
    }

    private final WeakReference<Context> contextRef;
    private final Handler handler;
    private final WeakReference<LinearLayout> rootViewRef;
    private final WeakReference<TvRecyclerView> groupViewRef;
    private final WeakReference<TvRecyclerView> itemViewRef;

    private LiveSettingGroupAdapter groupAdapter;
    private LiveSettingItemAdapter itemAdapter;
    private final ArrayList<LiveSettingGroup> settingGroups = new ArrayList<>();

    private SettingsListener listener;
    private boolean isShowing = false;
    private LiveChannelItem currentChannel;

    private final Runnable hideRunnable = this::hideInternal;
    private final Runnable focusAndShowRunnable = this::focusAndShowInternal;

    public LiveSettingsPanel(@NonNull Context context, @NonNull Handler handler,
                             @NonNull LinearLayout rootView, @NonNull TvRecyclerView groupView,
                             @NonNull TvRecyclerView itemView) {
        this.contextRef = new WeakReference<>(context);
        this.handler = handler;
        this.rootViewRef = new WeakReference<>(rootView);
        this.groupViewRef = new WeakReference<>(groupView);
        this.itemViewRef = new WeakReference<>(itemView);
        rootView.setVisibility(View.INVISIBLE);
    }

    public void init() {
        initSettingGroups();
        initGroupView();
        initItemView();
    }

    public void setListener(SettingsListener listener) {
        this.listener = listener;
    }

    private void initSettingGroups() {
        ArrayList<String> groupNames = new ArrayList<>(Arrays.asList(
                "线路选择", "画面比例", "播放解码", "超时换源", "偏好设置", "直播地址", "退出直播"
        ));
        ArrayList<ArrayList<String>> itemsArrayList = new ArrayList<>();
        itemsArrayList.add(new ArrayList<>());
        itemsArrayList.add(new ArrayList<>(Arrays.asList("默认", "16:9", "4:3", "填充", "原始", "裁剪")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("系统", "ijk硬解", "ijk软解", "exo")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("关", "5s", "10s", "15s", "20s", "25s", "30s")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("显示时间", "显示网速", "换台反转", "跨选分类", "关闭密码")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("列表历史")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("确定退出")));

        settingGroups.clear();
        for (int i = 0; i < groupNames.size(); i++) {
            LiveSettingGroup group = new LiveSettingGroup();
            group.setGroupIndex(i);
            group.setGroupName(groupNames.get(i));
            ArrayList<LiveSettingItem> itemList = new ArrayList<>();
            for (int j = 0; j < itemsArrayList.get(i).size(); j++) {
                LiveSettingItem item = new LiveSettingItem();
                item.setItemIndex(j);
                item.setItemName(itemsArrayList.get(i).get(j));
                itemList.add(item);
            }
            group.setLiveSettingItems(itemList);
            settingGroups.add(group);
        }
        restoreHawkSettings();
    }

    private void restoreHawkSettings() {
        int timeout = Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 2);
        if (settingGroups.size() > 3) {
            List<LiveSettingItem> items = settingGroups.get(3).getLiveSettingItems();
            if (timeout >= 0 && timeout < items.size())
                items.get(timeout).setItemSelected(true);
        }
        if (settingGroups.size() > 4) {
            List<LiveSettingItem> prefItems = settingGroups.get(4).getLiveSettingItems();
            if (!prefItems.isEmpty())
                prefItems.get(0).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_TIME, false));
            if (prefItems.size() > 1)
                prefItems.get(1).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false));
            if (prefItems.size() > 2)
                prefItems.get(2).setItemSelected(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false));
            if (prefItems.size() > 3)
                prefItems.get(3).setItemSelected(Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false));
            if (prefItems.size() > 4)
                prefItems.get(4).setItemSelected(Hawk.get(HawkConfig.LIVE_SKIP_PASSWORD, false));
        }
    }

    private void initGroupView() {
        TvRecyclerView groupView = groupViewRef.get();
        if (groupView == null) return;
        groupView.setHasFixedSize(true);
        groupView.setLayoutManager(new V7LinearLayoutManager(groupView.getContext(), 1, false));
        groupAdapter = new LiveSettingGroupAdapter();
        groupView.setAdapter(groupAdapter);
        groupAdapter.setNewData(settingGroups);
        groupView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                selectGroup(position, true);
            }
            @Override public void onItemClick(TvRecyclerView parent, View itemView, int position) {}
        });
        groupAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            selectGroup(position, false);
        });
    }

    private void initItemView() {
        TvRecyclerView itemView = itemViewRef.get();
        if (itemView == null) return;
        itemView.setHasFixedSize(true);
        itemView.setLayoutManager(new V7LinearLayoutManager(itemView.getContext(), 1, false));
        itemAdapter = new LiveSettingItemAdapter();
        itemView.setAdapter(itemAdapter);
        itemView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (position < 0) return;
                if (groupAdapter != null) groupAdapter.setFocusedGroupIndex(-1);
                if (itemAdapter != null) itemAdapter.setFocusedItemIndex(position);
                handler.removeCallbacks(hideRunnable);
                handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_SETTINGS_MS);
            }
            @Override public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                clickItem(position);
            }
        });
        itemAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            clickItem(position);
        });
    }

    private void selectGroup(int position, boolean focus) {
        if (position < 0 || position >= settingGroups.size() || groupAdapter == null || itemAdapter == null)
            return;
        if (focus) {
            groupAdapter.setFocusedGroupIndex(position);
            itemAdapter.setFocusedItemIndex(-1);
        }
        groupAdapter.setSelectedGroupIndex(position);
        itemAdapter.setNewData(settingGroups.get(position).getLiveSettingItems());

        if (position == 0 && currentChannel != null) {
            int idx = currentChannel.getSourceIndex();
            List<LiveSettingItem> data = itemAdapter.getData();
            if (idx >= 0 && data != null && idx < data.size())
                itemAdapter.selectItem(idx, true, false);
        } else if (position == 1 && listener != null) {
            itemAdapter.selectItem(listener.getCurrentScale(), true, true);
        } else if (position == 2 && listener != null) {
            itemAdapter.selectItem(listener.getCurrentPlayerType(), true, true);
        }

        int scrollPos = itemAdapter.getSelectedItemIndex();
        if (scrollPos < 0) scrollPos = 0;
        TvRecyclerView itemView = itemViewRef.get();
        if (itemView != null) itemView.scrollToPosition(scrollPos);

        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_SETTINGS_MS);
    }

    private void clickItem(int position) {
        if (groupAdapter == null || itemAdapter == null) return;
        int groupIndex = groupAdapter.getSelectedGroupIndex();
        if (groupIndex < 0 || groupIndex >= settingGroups.size()) return;
        if (groupIndex == 0 && currentChannel == null) {
            showToast("当前无直播源，无法切换线路");
            return;
        }
        if (groupIndex < 4) itemAdapter.selectItem(position, true, true);
        if (listener == null) return;

        switch (groupIndex) {
            case 0:
                if (currentChannel != null) {
                    currentChannel.setSourceIndex(position);
                    listener.onSourceChanged(position);
                }
                break;
            case 1:
                listener.onScaleChanged(position);
                break;
            case 2:
                listener.onPlayerTypeChanged(position);
                break;
            case 3:
                listener.onTimeoutChanged(position);
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, position);
                break;
            case 4:
                handlePreferenceChange(position);
                break;
            case 5:
                if (position == 0) listener.onLiveAddressSelected();
                break;
            case 6:
                if (position == 0) listener.onExit();
                break;
        }
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_SETTINGS_MS);
    }

    private void handlePreferenceChange(int position) {
        String key = null;
        switch (position) {
            case 0: key = HawkConfig.LIVE_SHOW_TIME; break;
            case 1: key = HawkConfig.LIVE_SHOW_NET_SPEED; break;
            case 2: key = HawkConfig.LIVE_CHANNEL_REVERSE; break;
            case 3: key = HawkConfig.LIVE_CROSS_GROUP; break;
            case 4: key = HawkConfig.LIVE_SKIP_PASSWORD; break;
        }
        if (key == null) return;
        boolean newValue = !Hawk.get(key, false);
        Hawk.put(key, newValue);
        if (listener != null) listener.onPreferenceChanged(key, newValue);
        if (itemAdapter != null) itemAdapter.selectItem(position, newValue, false);
    }

    public void show() {
        if (isShowing) {
            handler.removeCallbacks(hideRunnable);
            handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_SETTINGS_MS);
            return;
        }
        refreshSourceListDisplay();
        if (groupAdapter != null) groupAdapter.setNewData(settingGroups);
        selectGroup(0, false);
        TvRecyclerView groupView = groupViewRef.get();
        if (groupView != null) groupView.scrollToPosition(0);
        handler.postDelayed(focusAndShowRunnable, 200);
        isShowing = true;
    }

    private void focusAndShowInternal() {
        TvRecyclerView groupView = groupViewRef.get();
        TvRecyclerView itemView = itemViewRef.get();
        LinearLayout rootView = rootViewRef.get();
        if (groupView == null || rootView == null) {
            isShowing = false;
            return;
        }
        boolean isScrolling = groupView.isScrolling() ||
                (itemView != null && itemView.isScrolling()) ||
                groupView.isComputingLayout() ||
                (itemView != null && itemView.isComputingLayout());
        if (isScrolling) {
            handler.postDelayed(focusAndShowRunnable, 100);
            return;
        }
        groupView.scrollToPosition(0);
        groupView.setSelection(0);
        groupView.requestFocus();
        rootView.setVisibility(View.VISIBLE);
        rootView.setAlpha(0.0f);
        rootView.setTranslationX(rootView.getWidth() / 2f);
        rootView.animate()
                .translationX(0)
                .alpha(1.0f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(null);
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_SETTINGS_MS);
        // 延迟请求布局，避免使用未初始化的变量
        handler.postDelayed(() -> {
            LinearLayout root = rootViewRef.get();
            if (root != null) root.requestLayout();
        }, 255);
    }

    public void hide() {
        hideInternal();
    }

    private void hideInternal() {
        LinearLayout rootView = rootViewRef.get();
        if (rootView == null || rootView.getVisibility() != View.VISIBLE) return;
        rootView.animate()
                .translationX(rootView.getWidth() / 2f)
                .alpha(0.0f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        LinearLayout view = rootViewRef.get();
                        if (view != null) {
                            view.setVisibility(View.INVISIBLE);
                            view.clearAnimation();
                        }
                        if (groupAdapter != null) groupAdapter.setSelectedGroupIndex(-1);
                        isShowing = false;
                    }
                });
        handler.removeCallbacks(hideRunnable);
    }

    public void updateSourceList(LiveChannelItem channel) {
        this.currentChannel = channel;
        refreshSourceListDisplay();
    }

    private void refreshSourceListDisplay() {
        if (settingGroups.isEmpty()) return;
        LiveSettingGroup sourceGroup = settingGroups.get(0);
        if (sourceGroup == null) return;
        if (currentChannel == null || currentChannel.getChannelSourceNames() == null) {
            ArrayList<LiveSettingItem> empty = new ArrayList<>();
            LiveSettingItem item = new LiveSettingItem();
            item.setItemIndex(0);
            item.setItemName("无可用线路");
            empty.add(item);
            sourceGroup.setLiveSettingItems(empty);
            return;
        }
        ArrayList<String> sourceNames = currentChannel.getChannelSourceNames();
        ArrayList<LiveSettingItem> itemList = new ArrayList<>();
        for (int j = 0; j < sourceNames.size(); j++) {
            LiveSettingItem item = new LiveSettingItem();
            item.setItemIndex(j);
            item.setItemName(sourceNames.get(j));
            itemList.add(item);
        }
        sourceGroup.setLiveSettingItems(itemList);
        if (isShowing && groupAdapter != null && groupAdapter.getSelectedGroupIndex() == 0 && itemAdapter != null) {
            itemAdapter.setNewData(itemList);
            int idx = currentChannel.getSourceIndex();
            if (idx >= 0 && idx < itemList.size())
                itemAdapter.selectItem(idx, true, false);
        }
    }

    public void setCurrentSourceIndex(int index) {
        if (currentChannel == null) return;
        if (index < 0 || index >= currentChannel.getSourceNum()) return;
        currentChannel.setSourceIndex(index);
        if (isShowing && groupAdapter != null && groupAdapter.getSelectedGroupIndex() == 0 && itemAdapter != null)
            itemAdapter.selectItem(index, true, false);
    }

    public void refreshCurrentGroup() {
        if (!isShowing || groupAdapter == null || itemAdapter == null || listener == null) return;
        int selectedGroup = groupAdapter.getSelectedGroupIndex();
        if (selectedGroup >= 0) {
            selectGroup(selectedGroup, false);
        }
    }

    public boolean isShowing() { return isShowing; }

    private void showToast(String message) {
        Context ctx = contextRef.get();
        if (ctx != null) Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
    }

    public void destroy() {
        handler.removeCallbacks(hideRunnable);
        handler.removeCallbacks(focusAndShowRunnable);
        if (groupAdapter != null) {
            groupAdapter.setNewData(null);
            groupAdapter = null;
        }
        if (itemAdapter != null) {
            itemAdapter.setNewData(null);
            itemAdapter = null;
        }
        listener = null;
        currentChannel = null;
        settingGroups.clear();
        isShowing = false;
        LinearLayout root = rootViewRef.get();
        if (root != null && root.getVisibility() == View.VISIBLE) {
            root.setVisibility(View.INVISIBLE);
            root.clearAnimation();
        }
    }
}
