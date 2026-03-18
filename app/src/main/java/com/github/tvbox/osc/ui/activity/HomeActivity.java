package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.fragment.GridFragment;

public class HomeActivity extends BaseActivity {  // 必须是 BaseActivity（原项目基类）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live);

        // 直接加载直播频道列表
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.container, GridFragment.newInstance(getLiveSortData()))
            .commitAllowingStateLoss();
    }

    private MovieSort.SortData getLiveSortData() {
        MovieSort.SortData data = new MovieSort.SortData();
        data.id = "live";
        data.name = "直播";
        return data;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, SettingActivity.class));
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
