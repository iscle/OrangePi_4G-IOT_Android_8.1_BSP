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
package android.support.test.launcherhelper;

import android.graphics.Rect;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;

/**
 * A helper class for generic launcher interactions that can be abstracted across different types
 * of launchers.
 *
 */
public class CommonLauncherHelper {

    private static final String LOG_TAG = CommonLauncherHelper.class.getSimpleName();
    private static final int MAX_SCROLL_ATTEMPTS = 40;
    private static final int MIN_INTERACT_SIZE = 100;
    private static final int APP_LAUNCH_TIMEOUT = 10000;
    private static CommonLauncherHelper sInstance;
    private UiDevice mDevice;

    private CommonLauncherHelper(UiDevice uiDevice) {
        mDevice = uiDevice;
    }

    /**
     * Retrieves the singleton instance of {@link CommonLauncherHelper}
     * @param uiDevice
     * @return
     */
    public static CommonLauncherHelper getInstance(UiDevice uiDevice) {
        if (sInstance == null) {
            sInstance = new CommonLauncherHelper(uiDevice);
        }
        return sInstance;
    }

    /**
     * Scrolls a container back to the beginning
     * @param container
     * @param backDirection
     */
    public void scrollBackToBeginning(UiObject2 container, Direction backDirection) {
        scrollBackToBeginning(container, backDirection, MAX_SCROLL_ATTEMPTS);
    }

    /**
     * Scrolls a container back to the beginning
     * @param container
     * @param backDirection
     * @param maxAttempts
     */
    public void scrollBackToBeginning(UiObject2 container, Direction backDirection, int maxAttempts) {
        int attempts = 0;
        while (container.scroll(backDirection, 0.25f)) {
            attempts++;
            if (attempts > maxAttempts) {
                throw new RuntimeException(
                        "scrollBackToBeginning: exceeded max attempts: " + maxAttempts);
            }
        }
    }

    /**
     * Ensures that the described widget has enough visible portion by scrolling its container if
     * necessary
     * @param app
     * @param container
     * @param dir
     */
    private void ensureIconVisible(BySelector app, UiObject2 container, Direction dir) {
        UiObject2 appIcon = mDevice.findObject(app);
        Rect appR = appIcon.getVisibleBounds();
        Rect containerR = container.getVisibleBounds();
        int size = 0;
        int containerSize = 0;
        if (Direction.DOWN.equals(dir) || Direction.UP.equals(dir)) {
            size = appR.height();
            containerSize = containerR.height();
        } else {
            size = appR.width();
            containerSize = containerR.width();
        }
        if (size < MIN_INTERACT_SIZE) {
            // try to figure out how much percentage of the container needs to be scrolled in order
            // to reveal the app icon to have the MIN_INTERACT_SIZE
            float pct = ((float)(MIN_INTERACT_SIZE - size)) / containerSize;
            if (pct < 0.2f) {
                pct = 0.2f;
            }
            container.scroll(dir, pct);
        }
    }

    /**
     * Triggers app launch by interacting with its launcher icon as described, optionally verify
     * that the frontend UI has the expected app package name
     * @param launcherStrategy
     * @param app
     * @param packageName
     * @return
     */
    public long launchApp(ILauncherStrategy launcherStrategy, BySelector app,
            String packageName) {
        return launchApp(launcherStrategy, app, packageName, MAX_SCROLL_ATTEMPTS);
    }

    /**
     * Triggers app launch by interacting with its launcher icon as described, optionally verify
     * that the frontend UI has the expected app package name
     * @param launcherStrategy
     * @param app
     * @param packageName
     * @param maxScrollAttempts
     * @return the SystemClock#uptimeMillis timestamp just before launching the application.
     */
    public long launchApp(ILauncherStrategy launcherStrategy, BySelector app,
            String packageName, int maxScrollAttempts) {
        unlockDeviceIfAsleep();

        if (isAppOpen(packageName)) {
            // Application is already open
            return 0;
        }

        // Go to the home page
        launcherStrategy.open();
        Direction dir = launcherStrategy.getAllAppsScrollDirection();
        // attempt to find the app icon if it's not already on the screen
        if (!mDevice.hasObject(app)) {
            UiObject2 container = launcherStrategy.openAllApps(false);

            if (!mDevice.hasObject(app)) {
                scrollBackToBeginning(container, Direction.reverse(dir));
                int attempts = 0;
                while (!mDevice.hasObject(app) && container.scroll(dir, 0.8f)) {
                    attempts++;
                    if (attempts > maxScrollAttempts) {
                        throw new RuntimeException(
                                "launchApp: exceeded max attempts to locate app icon: "
                                        + maxScrollAttempts);
                    }
                }
            }
            // HACK-ish: ensure icon has enough parts revealed for it to be clicked on
            ensureIconVisible(app, container, dir);
        }

        long ready = SystemClock.uptimeMillis();
        if (!mDevice.findObject(app).clickAndWait(Until.newWindow(), APP_LAUNCH_TIMEOUT)) {
            Log.w(LOG_TAG, "no new window detected after app launch attempt.");
            return ILauncherStrategy.LAUNCH_FAILED_TIMESTAMP;
        }
        mDevice.waitForIdle();
        if (packageName != null) {
            Log.w(LOG_TAG, String.format(
                    "No UI element with package name %s detected.", packageName));
            boolean success = mDevice.wait(Until.hasObject(
                    By.pkg(packageName).depth(0)), APP_LAUNCH_TIMEOUT);
            if (success) {
                return ready;
            } else {
                return ILauncherStrategy.LAUNCH_FAILED_TIMESTAMP;
            }
        } else {
            return ready;
        }
    }

    private boolean isAppOpen (String appPackage) {
        return mDevice.hasObject(By.pkg(appPackage).depth(0));
    }

    private void unlockDeviceIfAsleep () {
        // Turn screen on if necessary
        try {
            if (!mDevice.isScreenOn()) {
                mDevice.wakeUp();
            }
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "Failed to unlock the screen-off device.", e);
        }
        // Check for lock screen element
        if (mDevice.hasObject(By.res("com.android.systemui", "keyguard_bottom_area"))) {
            mDevice.pressMenu();
        }
    }
}
