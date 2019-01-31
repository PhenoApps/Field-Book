package com.fieldbook.tracker;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

import com.github.paolorotolo.appintro.AppIntro2;
import com.github.paolorotolo.appintro.AppIntroFragment;


public class IntroActivity extends AppIntro2 {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Customize view
        showStatusBar(false);

        // Add slides
        //TODO redo the slide images for this
        addSlide(AppIntroFragment.newInstance(getString(R.string.intro_1_title), getString(R.string.intro_1_body), R.drawable.ic_launcher, Color.parseColor("#A84937")));
        addSlide(AppIntroFragment.newInstance(getString(R.string.intro_2_title), getString(R.string.intro_2_body), R.drawable.ic_launcher, Color.parseColor("#285E3D")));
        addSlide(AppIntroFragment.newInstance(getString(R.string.intro_3_title), getString(R.string.intro_3_body), R.drawable.ic_launcher, Color.parseColor("#0C6291")));
        addSlide(AppIntroFragment.newInstance(getString(R.string.intro_4_title), getString(R.string.intro_4_body), R.drawable.ic_launcher, Color.parseColor("#A84937")));

        // Hide Skip/Done button.
        showSkipButton(false);
        setProgressButtonEnabled(true);
    }

    @Override
    public void onSkipPressed(Fragment currentFragment) {
        super.onSkipPressed(currentFragment);
    }

    @Override
    public void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);
        finish();
    }

    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        super.onSlideChanged(oldFragment, newFragment);
    }
}