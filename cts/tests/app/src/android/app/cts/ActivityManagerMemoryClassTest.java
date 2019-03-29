/*
 * Copyright (C) 2011 The Android Open Source Project
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
package android.app.cts;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.stubs.ActivityManagerMemoryClassLaunchActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.test.ActivityInstrumentationTestCase2;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import com.android.compatibility.common.util.CddTest;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link ActivityInstrumentationTestCase2} that tests {@link ActivityManager#getMemoryClass()}
 * by checking that the memory class matches the proper screen density and by launching an
 * application that attempts to allocate memory on the heap.
 */
public class ActivityManagerMemoryClassTest
        extends ActivityInstrumentationTestCase2<ActivityManagerMemoryClassLaunchActivity> {

    public ActivityManagerMemoryClassTest() {
        super(ActivityManagerMemoryClassLaunchActivity.class);
    }

    public static class ExpectedMemorySizesClass {
        private static final Map<Integer, Integer> expectedMemorySizeForWatch
            =  new HashMap<Integer, Integer>();
        private static final Map<Integer, Integer> expectedMemorySizeForSmallNormalScreen
            =  new HashMap<Integer, Integer>();
        private static final Map<Integer, Integer> expectedMemorySizeForLargeScreen
            =  new HashMap<Integer, Integer>();
        private static final Map<Integer, Integer> expectedMemorySizeForXLargeScreen
            =  new HashMap<Integer, Integer>();

        static {
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_LOW, 32);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_MEDIUM, 32);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_TV, 32);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_HIGH, 36);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_260, 36);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_280, 36);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_300, 36);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_XHIGH, 48);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_340, 48);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_360, 48);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_400, 56);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_420, 64);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_XXHIGH, 88);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_560, 112);
            expectedMemorySizeForWatch.put(DisplayMetrics.DENSITY_XXXHIGH, 154);
        }

        static {
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_LOW, 32);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_MEDIUM, 32);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_TV, 48);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_HIGH, 48);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_260, 48);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_280, 48);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_300, 48);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_XHIGH, 80);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_340, 80);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_360, 80);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_400, 96);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_420, 112);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_XXHIGH, 128);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_560, 192);
            expectedMemorySizeForSmallNormalScreen.put(DisplayMetrics.DENSITY_XXXHIGH, 256);
        }

        static {
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_LOW, 32);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_MEDIUM, 64);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_TV, 80);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_HIGH, 80);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_260, 96);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_280, 96);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_300, 96);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_XHIGH, 128);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_340, 160);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_360, 160);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_400, 192);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_420, 228);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_XXHIGH, 256);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_560, 384);
            expectedMemorySizeForLargeScreen.put(DisplayMetrics.DENSITY_XXXHIGH, 512);
        }

        static {
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_LOW, 48);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_MEDIUM, 80);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_TV, 96);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_HIGH, 96);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_260, 144);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_280, 144);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_300, 144);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_XHIGH, 192);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_340, 192);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_360, 240);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_400, 288);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_420, 336);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_XXHIGH, 384);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_560, 576);
            expectedMemorySizeForXLargeScreen.put(DisplayMetrics.DENSITY_XXXHIGH, 768);
        }

        public static Integer getExpectedMemorySize(
                int screenSize,
                int screenDensity,
                boolean isWatch) {

           if (isWatch) {
              return expectedMemorySizeForWatch.get(screenDensity);
           }

           switch (screenSize) {
                case Configuration.SCREENLAYOUT_SIZE_SMALL:
                case Configuration.SCREENLAYOUT_SIZE_NORMAL:
                    return expectedMemorySizeForSmallNormalScreen.get(screenDensity);
                case Configuration.SCREENLAYOUT_SIZE_LARGE:
                    return expectedMemorySizeForLargeScreen.get(screenDensity);
                case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                    return expectedMemorySizeForXLargeScreen.get(screenDensity);
                default:
                    throw new IllegalArgumentException("No memory requirement specified "
                        + " for screen layout size " + screenSize);
           }
        }
    }

    @CddTest(requirement="3.7")
    public void testGetMemoryClass() throws Exception {
        int memoryClass = getMemoryClass();
        int screenDensity = getScreenDensity();
        int screenSize = getScreenSize();
        assertMemoryForScreenDensity(memoryClass, screenDensity, screenSize);

        runHeapTestApp(memoryClass);
    }

    private int getMemoryClass() {
        Context context = getInstrumentation().getTargetContext();
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return activityManager.getMemoryClass();
    }

    private int getScreenDensity() {
        Context context = getInstrumentation().getTargetContext();
        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        return metrics.densityDpi;
    }

    private int getScreenSize() {
        Context context = getInstrumentation().getTargetContext();
        Configuration config = context.getResources().getConfiguration();
        return config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
    }

    private void assertMemoryForScreenDensity(int memoryClass, int screenDensity, int screenSize) {
        Context context = getInstrumentation().getTargetContext();
        boolean isWatch =
            context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        int expectedMinimumMemory =
            ExpectedMemorySizesClass.getExpectedMemorySize(screenSize, screenDensity, isWatch);

        assertTrue("Expected to have at least " + expectedMinimumMemory
                + "mb of memory for screen density " + screenDensity,
                        memoryClass >= expectedMinimumMemory);
    }

    private void runHeapTestApp(int memoryClass) throws InterruptedException {
        Intent intent = new Intent();
        intent.putExtra(ActivityManagerMemoryClassLaunchActivity.MEMORY_CLASS_EXTRA,
                memoryClass);
        setActivityIntent(intent);
        ActivityManagerMemoryClassLaunchActivity activity = getActivity();
        assertEquals("The test application couldn't allocate memory close to the amount "
                + " specified by the memory class.", Activity.RESULT_OK, activity.getResult());
    }
}
