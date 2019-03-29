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
package android.fragment.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentController;
import android.app.FragmentManager;
import android.app.FragmentManagerNonConfig;
import android.os.Looper;
import android.os.Parcelable;
import android.support.test.rule.ActivityTestRule;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FragmentTestUtil {
    public static void waitForExecution(final ActivityTestRule<? extends Activity> rule) {
        // Wait for two cycles. When starting a postponed transition, it will post to
        // the UI thread and then the execution will be added onto the queue after that.
        // The two-cycle wait makes sure fragments have the opportunity to complete both
        // before returning.
        try {
            rule.runOnUiThread(() -> {
            });
            rule.runOnUiThread(() -> {
            });
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void runOnUiThreadRethrow(ActivityTestRule<? extends Activity> rule,
            Runnable r) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            r.run();
        } else {
            try {
                rule.runOnUiThread(r);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    public static boolean executePendingTransactions(
            final ActivityTestRule<? extends Activity> rule) {
        return executePendingTransactions(rule, rule.getActivity().getFragmentManager());
    }

    public static boolean executePendingTransactions(
            final ActivityTestRule<? extends Activity> rule, final FragmentManager fm) {
        final boolean[] ret = new boolean[1];
        runOnUiThreadRethrow(rule, new Runnable() {
            @Override
            public void run() {
                ret[0] = fm.executePendingTransactions();
            }
        });
        return ret[0];
    }

    public static boolean popBackStackImmediate(final ActivityTestRule<? extends Activity> rule) {
        return popBackStackImmediate(rule, rule.getActivity().getFragmentManager());
    }

    public static boolean popBackStackImmediate(final ActivityTestRule<? extends Activity> rule,
            final FragmentManager fm) {
        final boolean[] ret = new boolean[1];
        runOnUiThreadRethrow(rule, new Runnable() {
            @Override
            public void run() {
                ret[0] = fm.popBackStackImmediate();
            }
        });
        return ret[0];
    }

    public static boolean popBackStackImmediate(final ActivityTestRule<FragmentTestActivity> rule,
            final int id, final int flags) {
        return popBackStackImmediate(rule, rule.getActivity().getFragmentManager(), id, flags);
    }

    public static boolean popBackStackImmediate(final ActivityTestRule<FragmentTestActivity> rule,
            final FragmentManager fm, final int id, final int flags) {
        final boolean[] ret = new boolean[1];
        runOnUiThreadRethrow(rule, new Runnable() {
            @Override
            public void run() {
                ret[0] = fm.popBackStackImmediate(id, flags);
            }
        });
        return ret[0];
    }

    public static boolean popBackStackImmediate(final ActivityTestRule<FragmentTestActivity> rule,
            final String name, final int flags) {
        return popBackStackImmediate(rule, rule.getActivity().getFragmentManager(), name, flags);
    }

    public static boolean popBackStackImmediate(final ActivityTestRule<FragmentTestActivity> rule,
            final FragmentManager fm, final String name, final int flags) {
        final boolean[] ret = new boolean[1];
        runOnUiThreadRethrow(rule, new Runnable() {
            @Override
            public void run() {
                ret[0] = fm.popBackStackImmediate(name, flags);
            }
        });
        return ret[0];
    }

    public static void setContentView(final ActivityTestRule<FragmentTestActivity> rule,
            final int layoutId) {
        final Activity activity = rule.getActivity();
        runOnUiThreadRethrow(rule, new Runnable() {
            @Override
            public void run() {
                activity.setContentView(layoutId);
            }
        });
    }

    public static void assertChildren(ViewGroup container, Fragment... fragments) {
        final int numFragments = fragments == null ? 0 : fragments.length;
        assertEquals("There aren't the correct number of fragment Views in its container",
                numFragments, container.getChildCount());
        for (int i = 0; i < numFragments; i++) {
            assertEquals("Wrong Fragment View order for [" + i + "]", container.getChildAt(i),
                    fragments[i].getView());
        }
    }

    public static FragmentController createController(ActivityTestRule<FragmentTestActivity> rule) {
        final FragmentController[] controller = new FragmentController[1];
        final FragmentTestActivity activity = rule.getActivity();
        runOnUiThreadRethrow(rule, () -> {
            HostCallbacks hostCallbacks = new HostCallbacks(activity, null, 0);
            controller[0] = FragmentController.createController(hostCallbacks);
        });
        return controller[0];
    }


    public static void resume(ActivityTestRule<FragmentTestActivity> rule,
            FragmentController fragmentController,
            Pair<Parcelable, FragmentManagerNonConfig> savedState) {
        runOnUiThreadRethrow(rule, () -> {
            fragmentController.attachHost(null);
            if (savedState != null) {
                fragmentController.restoreAllState(savedState.first, savedState.second);
            }
            fragmentController.dispatchCreate();
            fragmentController.dispatchActivityCreated();
            fragmentController.noteStateNotSaved();
            fragmentController.execPendingActions();
            fragmentController.dispatchStart();
            fragmentController.reportLoaderStart();
            fragmentController.dispatchResume();
            fragmentController.execPendingActions();
        });
    }

    public static Pair<Parcelable, FragmentManagerNonConfig> destroy(
            ActivityTestRule<FragmentTestActivity> rule, FragmentController fragmentController) {
        final Pair<Parcelable, FragmentManagerNonConfig>[] result = new Pair[1];
        runOnUiThreadRethrow(rule, () -> {
            fragmentController.dispatchPause();
            final Parcelable savedState = fragmentController.saveAllState();
            final FragmentManagerNonConfig nonConfig = fragmentController.retainNestedNonConfig();
            fragmentController.dispatchStop();
            fragmentController.doLoaderStop(false);
            fragmentController.dispatchDestroy();
            fragmentController.doLoaderDestroy();
            result[0] = Pair.create(savedState, nonConfig);
        });
        return result[0];
    }

    public static boolean isVisible(Fragment fragment) {
        View view = fragment.getView();
        AccessibilityNodeInfo accessibilityNodeInfo = view.createAccessibilityNodeInfo();
        boolean isVisible = accessibilityNodeInfo.isVisibleToUser();
        accessibilityNodeInfo.recycle();
        return isVisible;
    }

    /**
     * Allocates until a garbage collection occurs.
     */
    public static void forceGC() {
        // This works on ART:
        Runtime.getRuntime().gc();
        Runtime.getRuntime().runFinalization();
        Runtime.getRuntime().gc();
        Runtime.getRuntime().runFinalization();
    }

    /**
     * Restarts the RecreatedActivity and waits for the new activity to be resumed.
     *
     * @return The newly-restarted Activity
     */
    public static <T extends RecreatedActivity> T recreateActivity(
            ActivityTestRule<? extends Activity> rule, T activity) throws InterruptedException {
        // Now switch the orientation
        RecreatedActivity.sResumed = new CountDownLatch(1);
        RecreatedActivity.sDestroyed = new CountDownLatch(1);

        runOnUiThreadRethrow(rule, () -> {
            activity.recreate();
        });
        assertTrue(RecreatedActivity.sResumed.await(1, TimeUnit.SECONDS));
        assertTrue(RecreatedActivity.sDestroyed.await(1, TimeUnit.SECONDS));
        T newActivity = (T) RecreatedActivity.sActivity;

        waitForExecution(rule);

        RecreatedActivity.clearState();
        return newActivity;
    }
}


