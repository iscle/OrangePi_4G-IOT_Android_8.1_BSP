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

import static android.car.cluster.CarInstrumentClusterManager.CATEGORY_NAVIGATION;

import android.app.ActivityOptions;
import android.car.CarNotConnectedException;
import android.car.cluster.ClusterActivityState;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

public class NavigationFragment extends Fragment {
    private final static String TAG = "Cluster.NavigationFragment";

    private SurfaceView mSurfaceView;
    private DisplayManager mDisplayManager;
    private Rect mUnobscuredBounds;

    // Static because we want to keep alive this virtual display when navigating through
    // ViewPager (this fragment gets dynamically destroyed and created)
    private static VirtualDisplay mVirtualDisplay;
    private static int mRegisteredNavDisplayId = Display.INVALID_DISPLAY;

    public NavigationFragment() {
        // Required empty public constructor
    }

    private final DisplayListener mDisplayListener = new DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            int navDisplayId = getVirtualDisplayId();
            Log.i(TAG, "onDisplayAdded, displayId: " + displayId
                    + ", navigation display id: " + navDisplayId);

            if (navDisplayId == displayId) {
                try {
                    getService().setClusterActivityLaunchOptions(
                            CATEGORY_NAVIGATION,
                            ActivityOptions.makeBasic()
                                    .setLaunchDisplayId(displayId));
                    mRegisteredNavDisplayId = displayId;

                    getService().setClusterActivityState(
                            CATEGORY_NAVIGATION,
                            ClusterActivityState.create(true, mUnobscuredBounds).toBundle());
                } catch (CarNotConnectedException e) {
                    throw new IllegalStateException(
                            "Failed to report nav activity cluster launch options", e);
                }
            }
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            if (mRegisteredNavDisplayId == displayId) {
                try {
                    mRegisteredNavDisplayId = Display.INVALID_DISPLAY;
                    getService().setClusterActivityLaunchOptions(
                            CATEGORY_NAVIGATION, null);
                } catch (CarNotConnectedException e) {
                    // This can happen only during shutdown, ignore.
                }
            }
        }

        @Override
        public void onDisplayChanged(int displayId) {}
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView");
        mDisplayManager = getActivity().getSystemService(DisplayManager.class);
        mDisplayManager.registerDisplayListener(mDisplayListener, new Handler());

        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_navigation, container, false);

        mSurfaceView = root.findViewById(R.id.nav_surface);
        mSurfaceView.getHolder().addCallback(new Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.i(TAG, "surfaceCreated, holder: " + holder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.i(TAG, "surfaceChanged, holder: " + holder + ", size:" + width + "x" + height
                        + ", format:" + format);

                //Create dummy unobscured area to report to navigation activity.
                mUnobscuredBounds = new Rect(40, 0, width - 80, height - 40);

                if (mVirtualDisplay == null) {
                    mVirtualDisplay = createVirtualDisplay(holder.getSurface(), width, height);
                } else {
                    mVirtualDisplay.setSurface(holder.getSurface());
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.i(TAG, "surfaceDestroyed, holder: " + holder + ", detaching surface from"
                        + " display, surface: " + holder.getSurface());
                // detaching surface is similar to turning off the display
                mVirtualDisplay.setSurface(null);
            }
        });

        return root;
    }

    private VirtualDisplay createVirtualDisplay(Surface surface, int width, int height) {
        Log.i(TAG, "createVirtualDisplay, surface: " + surface + ", width: " + width
                + "x" + height);
        return mDisplayManager.createVirtualDisplay("Cluster-App-VD", width, height, 160, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }

    private SampleClusterServiceImpl getService() {
        return ((MainClusterActivity) getActivity()).getService();
    }

    private int getVirtualDisplayId() {
        return (mVirtualDisplay != null && mVirtualDisplay.getDisplay() != null)
                ? mVirtualDisplay.getDisplay().getDisplayId() : Display.INVALID_DISPLAY;
    }
}
