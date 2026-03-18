package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.base.BaseActivity;  // ← 这一行必须加！

public class HomeActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live);

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
