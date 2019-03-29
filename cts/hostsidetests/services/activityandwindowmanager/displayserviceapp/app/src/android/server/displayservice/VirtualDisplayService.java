/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.server.displayservice;

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

public class VirtualDisplayService extends Service {
    private static final String NOTIFICATION_CHANNEL_ID = "cts/VirtualDisplayService";
    private static final String TAG = "VirtualDisplayService";

    private static final int FOREGROUND_ID = 1;

    private static final int DENSITY = 160;
    private static final int HEIGHT = 480;
    private static final int WIDTH = 800;

    private ImageReader mReader;
    private VirtualDisplay mVirtualDisplay;

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(new NotificationChannel(
            NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
            NotificationManager.IMPORTANCE_DEFAULT));
        Notification notif = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .build();
        startForeground(FOREGROUND_ID, notif);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String command = intent.getStringExtra("command");
        Log.d(TAG, "Got command: " + command);

        if ("create".equals(command)) {
            createVirtualDisplay(intent);
        } if ("off".equals(command)) {
            mVirtualDisplay.setSurface(null);
        } else if ("on".equals(command)) {
            mVirtualDisplay.setSurface(mReader.getSurface());
        } else if ("destroy".equals(command)) {
            destroyVirtualDisplay();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createVirtualDisplay(Intent intent) {
        mReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2);

        final DisplayManager displayManager = getSystemService(DisplayManager.class);
        final String name = "CtsVirtualDisplay";

        int flags = VIRTUAL_DISPLAY_FLAG_PRESENTATION | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
        if (intent.getBooleanExtra("show_content_when_locked", false /* defaultValue */)) {
            flags |= 1 << 5; // VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD
        }
        mVirtualDisplay = displayManager.createVirtualDisplay(
                name, WIDTH, HEIGHT, DENSITY, mReader.getSurface(), flags);
    }

    private void destroyVirtualDisplay() {
        mVirtualDisplay.release();
        mReader.close();
    }
}
