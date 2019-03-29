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

package com.android.app2;

import android.app.Service;
import android.content.Intent;
import android.graphics.Point;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.LinkedList;

import static android.graphics.Color.BLUE;
import static android.view.Gravity.LEFT;
import static android.view.Gravity.TOP;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

/** Service for creating and managing alert windows. */
public class AlertWindowService extends Service {

    private static final String TAG = "AlertWindowService";
    private static final boolean DEBUG = false;

    public static final int MSG_ADD_ALERT_WINDOW = 1;
    public static final int MSG_REMOVE_ALERT_WINDOW = 2;
    public static final int MSG_REMOVE_ALL_ALERT_WINDOWS = 3;

    public static String NOTIFICATION_MESSENGER_EXTRA =
            "com.android.app2.AlertWindowService.NOTIFICATION_MESSENGER_EXTRA";
    public static final int MSG_ON_ALERT_WINDOW_ADDED = 4;
    public static final int MSG_ON_ALERT_WINDOW_REMOVED = 5;

    private LinkedList<View> mAlertWindows = new LinkedList<>();

    private Messenger mOutgoingMessenger = null;
    private final Messenger mIncomingMessenger = new Messenger(new IncomingHandler());

    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ADD_ALERT_WINDOW:
                    addAlertWindow();
                    break;
                case MSG_REMOVE_ALERT_WINDOW:
                    removeAlertWindow();
                    break;
                case MSG_REMOVE_ALL_ALERT_WINDOWS:
                    removeAllAlertWindows();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private void addAlertWindow() {
        final Point size = new Point();
        final WindowManager wm = getSystemService(WindowManager.class);
        wm.getDefaultDisplay().getSize(size);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                TYPE_APPLICATION_OVERLAY,
                FLAG_NOT_FOCUSABLE | FLAG_WATCH_OUTSIDE_TOUCH | FLAG_NOT_TOUCHABLE);
        params.width = size.x / 3;
        params.height = size.y / 3;
        params.gravity = TOP | LEFT;

        final TextView view = new TextView(this);
        view.setText("AlertWindowService" + mAlertWindows.size());
        view.setBackgroundColor(BLUE);
        wm.addView(view, params);
        mAlertWindows.add(view);

        if (DEBUG) Log.e(TAG, "addAlertWindow " + mAlertWindows.size());
        if (mOutgoingMessenger != null) {
            try {
                mOutgoingMessenger.send(Message.obtain(null, MSG_ON_ALERT_WINDOW_ADDED));
            } catch (RemoteException e) {

            }
        }
    }

    private void removeAlertWindow() {
        if (mAlertWindows.size() == 0) {
            return;
        }
        final WindowManager wm = getSystemService(WindowManager.class);
        wm.removeView(mAlertWindows.pop());

        if (DEBUG) Log.e(TAG, "removeAlertWindow " + mAlertWindows.size());
        if (mOutgoingMessenger != null) {
            try {
                mOutgoingMessenger.send(Message.obtain(null, MSG_ON_ALERT_WINDOW_REMOVED));
            } catch (RemoteException e) {

            }
        }
    }

    private void removeAllAlertWindows() {
        while (mAlertWindows.size() > 0) {
            removeAlertWindow();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DEBUG) Log.e(TAG, "onBind");
        mOutgoingMessenger = intent.getParcelableExtra(NOTIFICATION_MESSENGER_EXTRA);
        return mIncomingMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (DEBUG) Log.e(TAG, "onUnbind");
        removeAllAlertWindows();
        return super.onUnbind(intent);
    }
}
