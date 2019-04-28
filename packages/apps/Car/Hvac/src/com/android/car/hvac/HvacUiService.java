/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.hvac;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import com.android.car.hvac.controllers.HvacPanelController;
import com.android.car.hvac.ui.SystemUiObserver;
import com.android.car.hvac.ui.TemperatureBarOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates a sliding panel for HVAC controls and adds it to the window manager above SystemUI.
 */
public class HvacUiService extends Service {
    private static final String TAG = "HvacUiService";

    private final List<View> mAddedViews = new ArrayList<>();

    private WindowManager mWindowManager;

    private View mPanel;
    private View mContainer;

    private int mPanelCollapsedHeight;
    private int mPanelFullExpandedHeight;
    private int mScreenBottom;
    private int mScreenWidth;

    private int mTemperatureSideMargin;
    private int mTemperatureOverlayWidth;
    private int mTemperatureOverlayHeight;
    private int mTemperatureBarCollapsedHeight;

    private HvacPanelController mHvacPanelController;
    private HvacController mHvacController;

    private ViewGroup mDriverTemperatureBarTouchOverlay;
    private ViewGroup mPassengerTemperatureBarTouchOverlay;
    private TemperatureBarOverlay mDriverTemperatureBar;
    private TemperatureBarOverlay mPassengerTemperatureBar;

    private int mStatusBarHeight = -1;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    @Override
    public void onCreate() {
        Resources res = getResources();
        mPanelCollapsedHeight = res.getDimensionPixelSize(R.dimen.car_hvac_panel_collapsed_height);
        mPanelFullExpandedHeight
                = res.getDimensionPixelSize(R.dimen.car_hvac_panel_full_expanded_height);

        mTemperatureSideMargin = res.getDimensionPixelSize(R.dimen.temperature_side_margin);
        mTemperatureOverlayWidth = res.getDimensionPixelSize(R.dimen.temperature_bar_width_expanded);
        mTemperatureOverlayHeight
                = res.getDimensionPixelSize(R.dimen.car_hvac_panel_full_expanded_height);
        mTemperatureBarCollapsedHeight
                = res.getDimensionPixelSize(R.dimen.temperature_bar_collapsed_height);

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        DisplayMetrics metrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        mScreenBottom = metrics.heightPixels - getStatusBarHeight();
        mScreenWidth = metrics.widthPixels;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        params.packageName = this.getPackageName();
        params.gravity = Gravity.TOP | Gravity.LEFT;

        params.x = 0;
        params.y = 0;

        params.width = mScreenWidth;
        params.height = mScreenBottom;
        params.setTitle("HVAC Container");
        disableAnimations(params);

        mContainer = inflater.inflate(R.layout.hvac_panel, null);
        mContainer.setLayoutParams(params);

        // The top padding should be calculated on the screen height and the height of the
        // expanded hvac panel. The space defined by the padding is meant to be clickable for
        // dismissing the hvac panel.
        int topPadding = mScreenBottom - mPanelFullExpandedHeight;
        mContainer.setPadding(0, topPadding, 0, 0);

        mContainer.setFocusable(false);
        mContainer.setClickable(false);
        mContainer.setFocusableInTouchMode(false);

        mPanel = mContainer.findViewById(R.id.hvac_center_panel);
        mPanel.getLayoutParams().height = mPanelCollapsedHeight;

        addViewToWindowManagerAndTrack(mContainer, params);

        createTemperatureBars(inflater);
        mHvacPanelController = new HvacPanelController(this /* context */, mContainer,
                mWindowManager, mDriverTemperatureBar, mPassengerTemperatureBar,
                mDriverTemperatureBarTouchOverlay, mPassengerTemperatureBarTouchOverlay);
        Intent bindIntent = new Intent(this /* context */, HvacController.class);
        if (!bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Failed to connect to HvacController.");
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        SystemUiObserver observer =
                (SystemUiObserver) inflater.inflate(R.layout.system_ui_observer, null);
        observer.setListener(visible -> {
            adjustPosition(mDriverTemperatureBarTouchOverlay, visible);
            adjustPosition(mPassengerTemperatureBarTouchOverlay, visible);
            adjustPosition(mDriverTemperatureBar, visible);
            adjustPosition(mPassengerTemperatureBar, visible);
            adjustPosition(mContainer, visible);
        });
        addViewToWindowManagerAndTrack(observer, params);
    }

    private void addViewToWindowManagerAndTrack(View view, WindowManager.LayoutParams params) {
        mWindowManager.addView(view, params);
        mAddedViews.add(view);
    }

    private void adjustPosition(View v, boolean systemUiVisible) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
        if (systemUiVisible) {
            lp.y -= getStatusBarHeight();
        } else {
            lp.y += getStatusBarHeight();
        }
        mWindowManager.updateViewLayout(v, lp);
    }


    @Override
    public void onDestroy() {
        for (View view : mAddedViews) {
            mWindowManager.removeView(view);
        }
        mAddedViews.clear();
        if(mHvacController != null){
            unbindService(mServiceConnection);
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mHvacController = ((HvacController.LocalBinder) service).getService();
            final Context context = HvacUiService.this;

            final Runnable r = new Runnable() {
                @Override
                public void run() {
                    // Once the hvac controller has refreshed its values from the vehicle,
                    // bind all the values.
                    mHvacPanelController.updateHvacController(mHvacController);
                }
            };

            if (mHvacController != null) {
                mHvacController.requestRefresh(r, new Handler(context.getMainLooper()));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mHvacController = null;
            mHvacPanelController.updateHvacController(null);
            //TODO: b/29126575 reconnect to controller if it is restarted
        }
    };

    private WindowManager.LayoutParams createClickableOverlayLayoutParam(String title) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.setTitle(title);
        return lp;
    }

    private TemperatureBarOverlay createTemperatureBarOverlay(LayoutInflater inflater,
            int gravity, String title) {
        TemperatureBarOverlay button = (TemperatureBarOverlay) inflater
                .inflate(R.layout.hvac_temperature_bar_overlay, null);

        WindowManager.LayoutParams params = createClickableOverlayLayoutParam(title);
        params.gravity = gravity;
        params.x = mTemperatureSideMargin;
        params.y = mScreenBottom - mTemperatureOverlayHeight;
        params.width = mTemperatureOverlayWidth;
        params.height = mTemperatureOverlayHeight;

        disableAnimations(params);
        button.setLayoutParams(params);
        addViewToWindowManagerAndTrack(button, params);

        return button;
    }

    /**
     * Creates a touchable overlay in the dimensions of a collapsed {@link TemperatureBarOverlay}.
     * @return a {@link ViewGroup} that was added to the {@link WindowManager}
     */
    private ViewGroup addTemperatureTouchOverlay(int gravity, String title) {
        WindowManager.LayoutParams params = createClickableOverlayLayoutParam(title);
        params.gravity = gravity;
        params.x = mTemperatureSideMargin;
        params.y = mScreenBottom - mTemperatureBarCollapsedHeight;
        params.width = mTemperatureOverlayWidth;
        params.height = mTemperatureBarCollapsedHeight;

        ViewGroup overlay = new LinearLayout(this /* context */);
        overlay.setLayoutParams(params);
        addViewToWindowManagerAndTrack(overlay, params);
        return overlay;
    }

    private void createTemperatureBars(LayoutInflater inflater) {
        mDriverTemperatureBar
                = createTemperatureBarOverlay(
                        inflater, Gravity.TOP | Gravity.LEFT, "HVAC Driver Temp");
        mPassengerTemperatureBar
                = createTemperatureBarOverlay(
                        inflater, Gravity.TOP | Gravity.RIGHT, "HVAC Passenger Temp");

        // Create a transparent overlay that is the size of the collapsed temperature bar.
        // It will receive touch events and trigger the expand/collapse of the panel. This is
        // necessary since changing the height of the temperature bar overlay dynamically, causes
        // a jank when WindowManager updates the view with a new height. This hack allows us
        // to maintain the temperature bar overlay at constant (expanded) height and just
        // update whether or not it is touchable/clickable.
        mDriverTemperatureBarTouchOverlay
                = addTemperatureTouchOverlay(
                        Gravity.TOP | Gravity.LEFT, "HVAC Driver Touch Overlay");
        mPassengerTemperatureBarTouchOverlay
                = addTemperatureTouchOverlay(
                        Gravity.TOP | Gravity.RIGHT, "HVAC Passenger Touch Overlay");
    }

    /**
     * Disables animations when window manager updates a child view.
     */
    private void disableAnimations(WindowManager.LayoutParams params) {
        try {
            int currentFlags = (Integer) params.getClass().getField("privateFlags").get(params);
            params.getClass().getField("privateFlags").set(params, currentFlags | 0x00000040);
        } catch (Exception e) {
            Log.e(TAG, "Error disabling animation");
        }
    }

    private int getStatusBarHeight() {
        // Cache the result to keep it fast.
        if (mStatusBarHeight >= 0) {
            return mStatusBarHeight;
        }

        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        mStatusBarHeight = result;
        return result;
    }
}
