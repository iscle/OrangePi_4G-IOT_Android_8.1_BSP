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
 * limitations under the License.
 */
package android.car.cluster.sample;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static java.lang.Integer.parseInt;

import android.app.ActivityOptions;
import android.car.CarNotConnectedException;
import android.car.cluster.ClusterActivityState;
import android.car.cluster.renderer.InstrumentClusterRenderingService;
import android.car.cluster.renderer.NavigationRenderer;
import android.car.navigation.CarNavigationInstrumentCluster;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Dummy implementation of {@link SampleClusterServiceImpl} to log all interaction.
 */
public class SampleClusterServiceImpl extends InstrumentClusterRenderingService {

    private static final String TAG = SampleClusterServiceImpl.class.getSimpleName();

    private Listener mListener;
    private final Binder mLocalBinder = new LocalBinder();
    static final String LOCAL_BINDING_ACTION = "local";

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind, intent: " + intent);
        return (LOCAL_BINDING_ACTION.equals(intent.getAction()))
                ? mLocalBinder : super.onBind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");

        Display clusterDisplay = getInstrumentClusterDisplay(this);
        if (clusterDisplay == null) {
            Log.e(TAG, "Unable to find instrument cluster display");
            return;
        }

        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(clusterDisplay.getDisplayId());
        Intent intent = new Intent(this, MainClusterActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent, options.toBundle());
    }

    @Override
    protected void onKeyEvent(KeyEvent keyEvent) {
        Log.i(TAG, "onKeyEvent, keyEvent: " + keyEvent + ", listener: " + mListener);
        if (mListener != null) {
            mListener.onKeyEvent(keyEvent);
        }
    }

    void registerListener(Listener listener) {
        mListener = listener;
    }

    void unregisterListener() {
        mListener = null;
    }

    @Override
    protected NavigationRenderer getNavigationRenderer() {
        NavigationRenderer navigationRenderer = new NavigationRenderer() {
            @Override
            public CarNavigationInstrumentCluster getNavigationProperties() {
                Log.i(TAG, "getNavigationProperties");
                CarNavigationInstrumentCluster config =
                        CarNavigationInstrumentCluster.createCluster(1000);
                Log.i(TAG, "getNavigationProperties, returns: " + config);
                return config;
            }

            @Override
            public void onStartNavigation() {
                Log.i(TAG, "onStartNavigation");
            }

            @Override
            public void onStopNavigation() {
                Log.i(TAG, "onStopNavigation");
            }

            @Override
            public void onNextTurnChanged(int event, CharSequence eventName, int turnAngle,
                    int turnNumber, Bitmap image, int turnSide) {
                Log.i(TAG, "event: " + event + ", eventName: " + eventName +
                        ", turnAngle: " + turnAngle + ", turnNumber: " + turnNumber +
                        ", image: " + image + ", turnSide: " + turnSide);
                mListener.onShowToast("Next turn: " + eventName);
            }

            @Override
            public void onNextTurnDistanceChanged(int distanceMeters, int timeSeconds,
                    int displayDistanceMillis, int displayDistanceUnit) {
                Log.i(TAG, "onNextTurnDistanceChanged, distanceMeters: " + distanceMeters
                        + ", timeSeconds: " + timeSeconds
                        + ", displayDistanceMillis: " + displayDistanceMillis
                        + ", displayDistanceUnit: " + displayDistanceUnit);
                mListener.onShowToast("Next turn distance: " + distanceMeters + " meters.");
            }
        };

        Log.i(TAG, "createNavigationRenderer, returns: " + navigationRenderer);
        return navigationRenderer;
    }

    class LocalBinder extends Binder {
        SampleClusterServiceImpl getService() {
            // Return this instance of LocalService so clients can call public methods
            return SampleClusterServiceImpl.this;
        }
    }

    interface Listener {
        void onKeyEvent(KeyEvent event);
        void onShowToast(String text);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (args != null && args.length > 0) {
            execShellCommand(args);
        }
    }

    private void doKeyEvent(int keyCode) {
        Log.i(TAG, "doKeyEvent, keyCode: " + keyCode);
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        KeyEvent event = obtainKeyEvent(keyCode, downTime, eventTime, KeyEvent.ACTION_DOWN);
        onKeyEvent(event);

        eventTime = SystemClock.uptimeMillis();
        event = obtainKeyEvent(keyCode, downTime, eventTime, KeyEvent.ACTION_UP);
        onKeyEvent(event);
    }

    private KeyEvent obtainKeyEvent(int keyCode, long downTime, long eventTime, int action) {
        int scanCode = 0;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            scanCode = 108;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            scanCode = 106;
        }
        return KeyEvent.obtain(
                    downTime,
                    eventTime,
                    action,
                    keyCode,
                    0 /* repeat */,
                    0 /* meta state */,
                    0 /* deviceId*/,
                    scanCode /* scancode */,
                    KeyEvent.FLAG_FROM_SYSTEM /* flags */,
                    InputDevice.SOURCE_KEYBOARD,
                    null /* characters */);
    }

    private void execShellCommand(String[] args) {
        Log.i(TAG, "execShellCommand, args: " + Arrays.toString(args));

        String command = args[0];

        switch (command) {
            case "injectKey": {
                if (args.length > 1) {
                    doKeyEvent(parseInt(args[1]));
                } else {
                    Log.i(TAG, "Not enough arguments");
                }
                break;
            }
            case "destroyOverlayDisplay": {
                Settings.Global.putString(getContentResolver(),
                    Global.OVERLAY_DISPLAY_DEVICES, "");
                break;
            }

            case "createOverlayDisplay": {
                if (args.length > 1) {
                    Settings.Global.putString(getContentResolver(),
                            Global.OVERLAY_DISPLAY_DEVICES, args[1]);
                } else {
                    Log.i(TAG, "Not enough arguments, expected 2");
                }
                break;
            }

            case "setUnobscuredArea": {
                if (args.length > 5) {
                    Rect unobscuredArea = new Rect(parseInt(args[2]), parseInt(args[3]),
                            parseInt(args[4]), parseInt(args[5]));
                    try {
                        setClusterActivityState(args[1],
                                ClusterActivityState.create(true, unobscuredArea).toBundle());
                    } catch (CarNotConnectedException e) {
                        Log.i(TAG, "Failed to set activity state.", e);
                    }
                } else {
                    Log.i(TAG, "wrong format, expected: category left top right bottom");
                }
            }
        }
    }

    private static Display getInstrumentClusterDisplay(Context context) {
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        Display[] displays = displayManager.getDisplays();

        Log.d(TAG, "There are currently " + displays.length + " displays connected.");
        for (Display display : displays) {
            Log.d(TAG, "  " + display);
        }

        if (displays.length > 1) {
            // TODO: assuming that secondary display is instrument cluster. Put this into settings?
            return displays[1];
        }
        return null;
    }

}
