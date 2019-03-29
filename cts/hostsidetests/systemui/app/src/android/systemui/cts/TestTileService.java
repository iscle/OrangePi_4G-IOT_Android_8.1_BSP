/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package android.systemui.cts;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.view.View;

/**
 * A test tile that logs everything that happens to it.
 * The tests will manipulate the state of the QS tile through ADB and verify
 * the correct callbacks actually happened.
 */
public class TestTileService extends TileService {
    protected final String TAG = getClass().getSimpleName();

    public static final String SHOW_DIALOG = "android.sysui.testtile.action.SHOW_DIALOG";
    public static final String START_ACTIVITY = "android.sysui.testtile.action.START_ACTIVITY";

    public static final String TEST_PREFIX = "TileTest_";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, TEST_PREFIX + "onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, TEST_PREFIX + "onDestroy");
    }

    @Override
    public void onTileAdded() {
        Log.i(TAG, TEST_PREFIX + "onTileAdded");
    }

    @Override
    public void onTileRemoved() {
        Log.i(TAG, TEST_PREFIX + "onTileRemoved");
        super.onTileRemoved();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.i(TAG, TEST_PREFIX + "onStartListening");
        IntentFilter filter = new IntentFilter(SHOW_DIALOG);
        filter.addAction(START_ACTIVITY);
        registerReceiver(mReceiver, filter);

        // Set up some initial good state.
        getQsTile().setLabel(TAG);
        getQsTile().setContentDescription("CTS Test Tile");
        getQsTile().setIcon(Icon.createWithResource(this, android.R.drawable.ic_secure));
        getQsTile().setState(Tile.STATE_ACTIVE);
        getQsTile().updateTile();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        Log.i(TAG, TEST_PREFIX + "onStopListening");
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onClick() {
        super.onClick();
        Log.i(TAG, TEST_PREFIX + "onClick");
        Log.i(TAG, TEST_PREFIX + "is_secure_" + isSecure());
        Log.i(TAG, TEST_PREFIX + "is_locked_" + isLocked());
        unlockAndRun(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, TEST_PREFIX + "unlockAndRunRun");
            }
        });
    }

    private void handleStartActivity() {
        startActivityAndCollapse(new Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void handleShowDialog() {
        Log.i(TAG, TEST_PREFIX + "handleShowDialog");
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(new FocusView(this, dialog));
        try {
            showDialog(dialog);
        } catch (Exception e) {
            Log.i(TAG, TEST_PREFIX + "onWindowAddFailed", e);
        }
    }

    private class FocusView extends View {
        private final Dialog mDialog;

        public FocusView(Context context, Dialog dialog) {
            super(context);
            mDialog = dialog;
        }

        @Override
        public void onWindowFocusChanged(boolean hasWindowFocus) {
            Log.i(TAG, TEST_PREFIX + "onWindowFocusChanged_" + hasWindowFocus);
            if (hasWindowFocus) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        mDialog.dismiss();
                    }
                });
            }
            super.onWindowFocusChanged(hasWindowFocus);
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SHOW_DIALOG)) {
                handleShowDialog();
            } else {
                handleStartActivity();
            }
        }
    };
}
