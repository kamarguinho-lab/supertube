package com.kamarguinho.supertube.tv;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.kamarguinho.supertube.R;

/** Ponto de entrada na TV (aparece na tela inicial do Android TV / Google TV). */
public class TvMainActivity extends FragmentActivity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_fragment);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.tv_container, new TvBrowseFragment())
                    .commit();
        }
    }
}
