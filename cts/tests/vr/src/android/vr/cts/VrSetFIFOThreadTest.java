/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.vr.cts;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;
import android.test.ActivityInstrumentationTestCase2;
import android.content.ComponentName;
import android.util.Log;
import android.app.ActivityManager;
import android.content.Context;
import android.os.RemoteException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import android.provider.Settings;
import com.android.cts.verifier.vr.MockVrListenerService;

public class VrSetFIFOThreadTest extends ActivityInstrumentationTestCase2<OpenGLESActivity> {
    private OpenGLESActivity mActivity;
    private ActivityManager mActivityManager;
    private Context mContext;
    private static final int SCHED_OTHER = 0;
    private static final int SCHED_FIFO = 1;
    private static final int SCHED_RESET_ON_FORK = 0x40000000;
    public static final String ENABLED_VR_LISTENERS = "enabled_vr_listeners";
    private static final String TAG = "VrSetFIFOThreadTest";

    public VrSetFIFOThreadTest() {
        super(OpenGLESActivity.class);
    }

    private void setIntent(int viewIndex, int createProtected,
        int priorityAttribute, int mutableAttribute) {
        Intent intent = new Intent();
        intent.putExtra(OpenGLESActivity.EXTRA_VIEW_INDEX, viewIndex);
        intent.putExtra(OpenGLESActivity.EXTRA_PROTECTED, createProtected);
        intent.putExtra(OpenGLESActivity.EXTRA_PRIORITY, priorityAttribute);
        intent.putExtra(OpenGLESActivity.EXTRA_MUTABLE, mutableAttribute);
        setActivityIntent(intent);
    }

    public void testSetVrThreadAPISuccess() throws Throwable {
        mContext = getInstrumentation().getTargetContext();
        setIntent(OpenGLESActivity.RENDERER_BASIC, 1, 0, 0);
        ComponentName requestedComponent = new ComponentName(mContext, MockVrListenerService.class);
        String old_vr_listener = Settings.Secure.getString(mContext.getContentResolver(), ENABLED_VR_LISTENERS);
        Settings.Secure.putString(mContext.getContentResolver(),
            ENABLED_VR_LISTENERS,
            requestedComponent.flattenToString());
        mActivity = getActivity();
        assertTrue(mActivity.waitForFrameDrawn());

        if (mActivity.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE)) {
            int vr_thread = 0, policy = 0;
            mActivity.setVrModeEnabled(true, requestedComponent);
            vr_thread = Process.myTid();
            mActivityManager =
                  (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            mActivityManager.setVrThread(vr_thread);
            policy = (int) Process.getThreadScheduler(vr_thread);
            Log.e(TAG, "scheduling policy: " + policy);
            assertEquals((SCHED_FIFO | SCHED_RESET_ON_FORK), policy);
        }
        Settings.Secure.putString(mContext.getContentResolver(),
            ENABLED_VR_LISTENERS, old_vr_listener);
    }

    public void testSetVrThreadAPIFailure() throws Throwable {
        mContext = getInstrumentation().getTargetContext();
        setIntent(OpenGLESActivity.RENDERER_BASIC, 1, 0, 0);
        ComponentName requestedComponent = new ComponentName(mContext, MockVrListenerService.class);
        String old_vr_listener = Settings.Secure.getString(mContext.getContentResolver(), ENABLED_VR_LISTENERS);
        Settings.Secure.putString(mContext.getContentResolver(),
            ENABLED_VR_LISTENERS,
            requestedComponent.flattenToString());
        mActivity = getActivity();
        assertTrue(mActivity.waitForFrameDrawn());
        if (mActivity.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE)) {
            int vr_thread = 0, policy = 0;
            mActivity.setVrModeEnabled(false, requestedComponent);
            vr_thread = Process.myTid();
            mActivityManager =
                  (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            mActivityManager.setVrThread(vr_thread);
            policy = (int) Process.getThreadScheduler(vr_thread);
            Log.e(TAG, "scheduling policy: " + policy);
            assertEquals(SCHED_OTHER, policy);
        }
        Settings.Secure.putString(mContext.getContentResolver(),
            ENABLED_VR_LISTENERS, old_vr_listener);
    }
}
