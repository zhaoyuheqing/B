package com.github.tvbox.osc.ui.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;

import androidx.lifecycle.ViewModelProvider;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HomeActivity extends BaseActivity {

    // takagen99: Added to allow read string（原静态 Resources 获取方法，保留以兼容全项目调用）
    private static Resources res;

    private final Handler mHandler = new Handler();

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live;  // 使用极简布局
    }

    @Override
    protected void init() {
        // 保留原静态资源获取（必须）
        res = getResources();

        // 保留原服务器启动和事件总线（兼容性）
        EventBus.getDefault().register(this);
        ControlManager.get().startServer();
        App.startWebserver();

        // 关键修改：不阻塞界面，直接进入直播（符合 DIYP 风格）
        loadLiveChannelList();

        // 源加载改为异步、不影响开机界面（用户可先看到空列表，后续订阅后刷新）
        mHandler.post(this::initData);
    }

    // 必须保留：全项目调用 HomeActivity.getRes().getString(R.string.xxx)
    public static Resources getRes() {
        return res;
    }

    // 必须保留：UserFragment 等地方调用 HomeActivity.homeRecf()
    public static void homeRecf() {
        int homeRec = Hawk.get(HawkConfig.HOME_REC, -1);
        int limit = 2;
        if (homeRec == limit) homeRec = -1;
        homeRec++;
        Hawk.put(HawkConfig.HOME_REC, homeRec);
    }

    // 必须保留：UserFragment 等地方调用 HomeActivity.reHome(mContext)
    public static boolean reHome(Context appContext) {
        Intent intent = new Intent(appContext, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        appContext.startActivity(intent);
        return true;
    }

    // 核心：开机直接进入直播（不等待源）
    private void loadLiveChannelList() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, GridFragment.newInstance())
                .commitAllowingStateLoss();
    }

    // 简化直播分类占位（空数据，用户后续订阅）
    private MovieSort.SortData getLiveSortData() {
        MovieSort.SortData data = new MovieSort.SortData();
        data.id = "live";
        data.name = "直播";
        return data;
    }

    // 源加载逻辑保留，但移到异步、不阻塞开机（防止 NPE）
    private boolean dataInitOk = false;
    private boolean jarInitOk = false;

    private void initData() {
        if (dataInitOk && jarInitOk) {
            // 源加载完成，可选：通知 GridFragment 刷新
            return;
        }

        ApiConfig.get().loadConfig(false, new ApiConfig.LoadConfigCallback() {
            @Override
            public void success() {
                dataInitOk = true;
                if (ApiConfig.get().getSpider().isEmpty()) {
                    jarInitOk = true;
                }
                // 源成功后可选刷新直播（但不强制）
                // mHandler.postDelayed(() -> EventBus.getDefault().post(new RefreshEvent()), 100);
            }

            @Override
            public void error(String msg) {
                dataInitOk = true;
                jarInitOk = true;
            }

            @Override
            public void retry() {
                mHandler.postDelayed(HomeActivity.this::initData, 1000);
            }
        }, this);
    }

    // 菜单键打开设置（DIYP 核心操作）
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, SettingActivity.class));
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        ControlManager.get().stopServer();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        // 保留原事件处理（推送等）
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            // 原推送逻辑（如果有）
        }
    }
}
