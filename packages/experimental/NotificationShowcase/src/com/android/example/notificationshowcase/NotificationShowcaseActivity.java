// dummy notifications for demos
// for anandx@google.com by dsandler@google.com

package com.android.example.notificationshowcase;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationManagerCompat;
import android.view.View;
import android.widget.Button;

public class NotificationShowcaseActivity extends Activity {
    private static final String TAG = "NotificationShowcase";
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Button disableBtn = (Button) findViewById(R.id.disable);
        NotificationManagerCompat noMa = NotificationManagerCompat.from(this);
        if(noMa.areNotificationsEnabled()) {
            disableBtn.setText(R.string.disable_button_label);
        } else {
            disableBtn.setText(R.string.enable_button_label);
        }
    }

    public void doPost(View v) {
        Intent intent = new Intent(NotificationService.ACTION_CREATE);
        intent.setComponent(new ComponentName(this, NotificationService.class));
        startService(intent);
    }

    public void doRemove(View v) {
        Intent intent = new Intent(NotificationService.ACTION_DESTROY);
        intent.setComponent(new ComponentName(this, NotificationService.class));
        startService(intent);
    }

    public void doPrefs(View v) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(this, SettingsActivity.class));
        startActivity(intent);
    }

    public void doDisable(View v) {
        startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:com.android.example.notificationshowcase")));
    }
}
