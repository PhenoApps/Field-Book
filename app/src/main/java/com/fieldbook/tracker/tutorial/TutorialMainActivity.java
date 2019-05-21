package com.fieldbook.tracker.tutorial;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.fieldbook.tracker.ConfigActivity;
import com.fieldbook.tracker.R;

public class TutorialMainActivity extends Activity {
    public static Activity thisActivity;

    private int screen;

    private final int max = 7;

    @Override
    public void onDestroy() {
        ConfigActivity.helpActive = false;
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ConfigActivity.helpActive = true;

        thisActivity = this;

        // Makes the screen a system alert, so it can "float" above other screens
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        getWindow().setGravity(Gravity.BOTTOM);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        setContentView(R.layout.activity_tutorial);

        Button close = findViewById(R.id.close);
        Button prev = findViewById(R.id.prev);
        Button next = findViewById(R.id.next);

        final TextView header = findViewById(R.id.header);
        final TextView content = findViewById(R.id.field_count);

        screen = 1;

        // Load help strings
        final String array[] = new String[max];
        array[0] = getString(R.string.tutorial_main_1);
        array[1] = getString(R.string.tutorial_main_2);
        array[2] = getString(R.string.tutorial_main_3);
        array[3] = getString(R.string.tutorial_main_4);
        array[4] = getString(R.string.tutorial_main_5);
        array[5] = getString(R.string.tutorial_main_6);

        header.setText(getString(R.string.tutorial_dialog_title) + " " + screen + "/" + max);
        content.setText(array[screen - 1]);

        // move one step back in the tutorial
        prev.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {

                screen -= 1;

                if (screen < 1)
                    screen = 1;

                header.setText(getString(R.string.tutorial_dialog_title) + " " + screen + "/" + max);
                content.setText(array[screen - 1]);
            }
        });

        // move one step forward in the tutorial
        next.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {

                screen += 1;

                if (screen > max)
                    screen = max;

                header.setText(getString(R.string.tutorial_dialog_title) + " " + screen + "/" + max);
                content.setText(array[screen - 1]);
            }
        });

        // close screen
        // help active is to indicate tips/hints is no longer open
        // user is now able to open tutorial
        close.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                ConfigActivity.helpActive = false;
                finish();
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }

}
