package com.github.tvbox.osc.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;

public class GridFragment extends BaseLazyFragment {

    private LinearLayout emptyLayout;
    private Button btnAddSource;
    private Button btnEnterLive;
    private Handler autoEnterHandler = new Handler(Looper.getMainLooper());
    private Runnable autoEnterRunnable;

    public static GridFragment newInstance() {
        return new GridFragment();
    }

    public static GridFragment newInstance(Object sortData) {
        return newInstance();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_grid;
    }

    @Override
    protected void init() {
        initView();
    }

    private void initView() {
        emptyLayout = new LinearLayout(requireContext());
        emptyLayout.setOrientation(LinearLayout.VERTICAL);
        emptyLayout.setGravity(Gravity.CENTER);
        emptyLayout.setPadding(0, 180, 0, 0);

        TextView tvEmpty = new TextView(requireContext());
        tvEmpty.setText("暂无直播频道\n点击屏幕任意位置添加直播源");
        tvEmpty.setTextColor(0xFFFFFFFF);
        tvEmpty.setTextSize(22);
        tvEmpty.setGravity(Gravity.CENTER);
        emptyLayout.addView(tvEmpty);

        btnAddSource = new Button(requireContext());
        btnAddSource.setText("添加直播源");
        btnAddSource.setTextColor(0xFFFFFFFF);
        btnAddSource.setBackgroundColor(0xFF3366CC);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 50;
        btnAddSource.setLayoutParams(params);
        btnAddSource.setOnClickListener(v -> 
            startActivity(new Intent(requireContext(), SettingActivity.class)));
        emptyLayout.addView(btnAddSource);

        btnEnterLive = new Button(requireContext());
        btnEnterLive.setText("进入直播");
        btnEnterLive.setTextColor(0xFFFFFFFF);
        btnEnterLive.setBackgroundColor(0xFF4CAF50);
        params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 30;
        btnEnterLive.setLayoutParams(params);
        btnEnterLive.setOnClickListener(v -> enterLive());
        emptyLayout.addView(btnEnterLive);

        // 添加 emptyLayout 到根视图
        View root = getView();
        if (root instanceof ViewGroup) {
            ((ViewGroup) root).removeAllViews();
            ((ViewGroup) root).addView(emptyLayout);
        }
    }

    // ==================== 核心修复：完整触发 loadConfig → parseJson ====================
    private void enterLive() {
        String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "").trim();
        if (liveUrl.isEmpty()) {
            Toast.makeText(requireContext(), "请先添加直播源地址", Toast.LENGTH_LONG).show();
            startActivity(new Intent(requireContext(), SettingActivity.class));
            return;
        }

        ApiConfig apiConfig = ApiConfig.get();

        // 关键：主动调用 loadConfig，确保 parseJson 完整执行（IJK + proxy 包装 + 其他初始化）
        apiConfig.loadConfig(false, new ApiConfig.LoadConfigCallback() {
            @Override
            public void success() {
                Toast.makeText(requireContext(), "源配置已初始化，即将进入播放...", Toast.LENGTH_SHORT).show();

                // 延迟跳转，确保 parseJson 完成
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    startActivity(new Intent(requireContext(), LivePlayActivity.class));
                }, 500);
            }

            @Override
            public void error(String msg) {
                Toast.makeText(requireContext(), "源初始化失败: " + msg + "\n仍尝试进入直播", Toast.LENGTH_LONG).show();
                // 失败也尝试进入（容错）
                startActivity(new Intent(requireContext(), LivePlayActivity.class));
            }

            @Override
            public void retry() {
                // 可重试一次
                new Handler(Looper.getMainLooper()).postDelayed(() -> enterLive(), 2000);
            }
        }, requireActivity());
    }

    // 点击屏幕任意位置跳转设置
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (view != null) {
            view.setOnClickListener(v -> 
                startActivity(new Intent(requireContext(), SettingActivity.class)));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "").trim();
        if (liveUrl.isEmpty()) {
            Toast.makeText(requireContext(), "未检测到直播源\n点击屏幕任意位置添加", Toast.LENGTH_LONG).show();
            if (autoEnterRunnable != null) {
                autoEnterHandler.removeCallbacks(autoEnterRunnable);
            }
        } else {
            Toast.makeText(requireContext(), "直播源已保存\n3秒后自动进入直播（或点击按钮立即进入）", Toast.LENGTH_LONG).show();

            autoEnterRunnable = this::enterLive;
            autoEnterHandler.postDelayed(autoEnterRunnable, 3000);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (autoEnterRunnable != null) {
            autoEnterHandler.removeCallbacks(autoEnterRunnable);
        }
    }
}
