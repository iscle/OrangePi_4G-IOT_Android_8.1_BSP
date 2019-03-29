/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.assist.service;

import android.app.assist.AssistContent;
import android.app.assist.AssistStructure;
import android.assist.service.R;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;

import android.graphics.Point;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Display;
import android.view.ViewTreeObserver;

import java.io.ByteArrayOutputStream;
import java.util.Date;

import android.assist.common.Utils;
import android.view.WindowManager;

public class MainInteractionSession extends VoiceInteractionSession {
    static final String TAG = "MainInteractionSession";

    Intent mStartIntent;
    Context mContext;
    Bundle mAssistData = new Bundle();

    private boolean hasReceivedAssistData = false;
    private boolean hasReceivedScreenshot = false;
    private int mCurColor;
    private int mDisplayHeight;
    private int mDisplayWidth;
    private Bitmap mScreenshot;
    private BroadcastReceiver mReceiver;
    private String mTestName;
    private View mContentView;

    MainInteractionSession(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(Utils.HIDE_SESSION)) {
                    hide();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.HIDE_SESSION);
        mContext.registerReceiver(mReceiver, filter);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        super.onDestroy();
        if (mReceiver != null) {
            mContext.unregisterReceiver(mReceiver);
        }
    }

    @Override
    public void onPrepareShow(Bundle args, int showFlags) {
        if (Utils.LIFECYCLE_NOUI.equals(args.getString(Utils.TESTCASE_TYPE, ""))) {
            setUiEnabled(false);
        } else  {
            setUiEnabled(true);
        }
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        if ((showFlags & SHOW_WITH_ASSIST) == 0) {
            return;
        }
        mTestName = args.getString(Utils.TESTCASE_TYPE, "");
        mCurColor = args.getInt(Utils.SCREENSHOT_COLOR_KEY);
        mDisplayHeight = args.getInt(Utils.DISPLAY_HEIGHT_KEY);
        mDisplayWidth = args.getInt(Utils.DISPLAY_WIDTH_KEY);
        super.onShow(args, showFlags);
        if (mContentView == null) return; // Happens when ui is not enabled.
        mContentView.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    mContentView.getViewTreeObserver().removeOnPreDrawListener(this);
                    Display d = mContentView.getDisplay();
                    Point displayPoint = new Point();
                    d.getRealSize(displayPoint);
                    Intent intent = new Intent(Utils.BROADCAST_CONTENT_VIEW_HEIGHT);
                    intent.putExtra(Utils.EXTRA_CONTENT_VIEW_HEIGHT, mContentView.getHeight());
                    intent.putExtra(Utils.EXTRA_CONTENT_VIEW_WIDTH, mContentView.getWidth());
                    intent.putExtra(Utils.EXTRA_DISPLAY_POINT, displayPoint);
                    mContext.sendBroadcast(intent);
                    return true;
                }
            });
    }

    @Override
    public void onHandleAssist(/*@Nullable */Bundle data, /*@Nullable*/ AssistStructure structure,
        /*@Nullable*/ AssistContent content) {
        Log.i(TAG, "onHandleAssist");
        Log.i(TAG,
                String.format("Bundle: %s, Structure: %s, Content: %s", data, structure, content));
        super.onHandleAssist(data, structure, content);

        // send to test to verify that this is accurate.
        mAssistData.putParcelable(Utils.ASSIST_STRUCTURE_KEY, structure);
        mAssistData.putParcelable(Utils.ASSIST_CONTENT_KEY, content);
        mAssistData.putBundle(Utils.ASSIST_BUNDLE_KEY, data);
        hasReceivedAssistData = true;
        maybeBroadcastResults();
    }

    @Override
    public void onHandleScreenshot(/*@Nullable*/ Bitmap screenshot) {
        Log.i(TAG, String.format("onHandleScreenshot - Screenshot: %s", screenshot));
        super.onHandleScreenshot(screenshot);

        if (screenshot != null) {
            mAssistData.putBoolean(Utils.ASSIST_SCREENSHOT_KEY, true);

            if (mTestName.equals(Utils.SCREENSHOT)) {
                boolean screenshotMatches = compareScreenshot(screenshot, mCurColor);
                Log.i(TAG, "this is a screenshot test. Matches? " + screenshotMatches);
                mAssistData.putBoolean(
                    Utils.COMPARE_SCREENSHOT_KEY, screenshotMatches);
            }
        } else {
            mAssistData.putBoolean(Utils.ASSIST_SCREENSHOT_KEY, false);
        }
        hasReceivedScreenshot = true;
        maybeBroadcastResults();
    }

    private boolean compareScreenshot(Bitmap screenshot, int color) {
        Point size = new Point(mDisplayWidth, mDisplayHeight);

        if (screenshot.getWidth() != size.x || screenshot.getHeight() != size.y) {
            Log.i(TAG, "width  or height didn't match: " + size + " vs " + screenshot.getWidth()
                    + "," + screenshot.getHeight());
            return false;
        }
        int[] pixels = new int[size.x * size.y];
        screenshot.getPixels(pixels, 0, size.x, 0, 0, size.x, size.y);

        int expectedColor = 0;
        int wrongColor = 0;
        for (int pixel : pixels) {
            if (pixel == color) {
                expectedColor += 1;
            } else {
                wrongColor += 1;
            }
        }

        double colorRatio = (double) expectedColor / (expectedColor + wrongColor);
        Log.i(TAG, "the ratio is " + colorRatio);
        if (colorRatio < 0.6) {
            return false;
        }
        return true;
    }

    private void maybeBroadcastResults() {
        if (!hasReceivedAssistData) {
            Log.i(TAG, "waiting for assist data before broadcasting results");
        } else if (!hasReceivedScreenshot) {
            Log.i(TAG, "waiting for screenshot before broadcasting results");
        } else {
            Intent intent = new Intent(Utils.BROADCAST_ASSIST_DATA_INTENT);
            intent.putExtras(mAssistData);
            Log.i(TAG,
                    "broadcasting: " + intent.toString() + ", Bundle = " + mAssistData.toString());
            mContext.sendBroadcast(intent);

            hasReceivedAssistData = false;
            hasReceivedScreenshot = false;
        }
    }

    @Override
    public View onCreateContentView() {
        LayoutInflater f = getLayoutInflater();
        if (f == null) {
            Log.wtf(TAG, "layout inflater was null");
        }
        mContentView = f.inflate(R.layout.assist_layer,null);
        Log.i(TAG, "onCreateContentView");
        return mContentView;
    }
}
