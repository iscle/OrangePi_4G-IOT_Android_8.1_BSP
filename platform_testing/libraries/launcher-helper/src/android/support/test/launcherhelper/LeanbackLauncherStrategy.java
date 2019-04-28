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
 * limitations under the License
 */

package android.support.test.launcherhelper;

import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.test.utils.DPadUtil;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class LeanbackLauncherStrategy implements ILeanbackLauncherStrategy {

    private static final String LOG_TAG = LeanbackLauncherStrategy.class.getSimpleName();
    private static final String PACKAGE_LAUNCHER = "com.google.android.leanbacklauncher";
    private static final String PACKAGE_SEARCH = "com.google.android.katniss";

    private static final int MAX_SCROLL_ATTEMPTS = 20;
    private static final int APP_LAUNCH_TIMEOUT = 10000;
    private static final int SHORT_WAIT_TIME = 5000;    // 5 sec
    private static final int NOTIFICATION_WAIT_TIME = 60000;

    protected UiDevice mDevice;
    protected DPadUtil mDPadUtil;
    private Instrumentation mInstrumentation;


    /**
     * {@inheritDoc}
     */
    @Override
    public String getSupportedLauncherPackage() {
        return PACKAGE_LAUNCHER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUiDevice(UiDevice uiDevice) {
        mDevice = uiDevice;
        mDPadUtil = new DPadUtil(mDevice);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void open() {
        // if we see main list view, assume at home screen already
        if (!mDevice.hasObject(getWorkspaceSelector())) {
            mDPadUtil.pressHome();
            // ensure launcher is shown
            if (!mDevice.wait(Until.hasObject(getWorkspaceSelector()), SHORT_WAIT_TIME)) {
                // HACK: dump hierarchy to logcat
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    mDevice.dumpWindowHierarchy(baos);
                    baos.flush();
                    baos.close();
                    String[] lines = baos.toString().split("\\r?\\n");
                    for (String line : lines) {
                        Log.d(LOG_TAG, line.trim());
                    }
                } catch (IOException ioe) {
                    Log.e(LOG_TAG, "error dumping XML to logcat", ioe);
                }
                throw new RuntimeException("Failed to open leanback launcher");
            }
            mDevice.waitForIdle();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiObject2 openAllApps(boolean reset) {
        UiObject2 appsRow = selectAppsRow();
        if (appsRow == null) {
            throw new RuntimeException("Could not find all apps row");
        }
        if (reset) {
            Log.w(LOG_TAG, "The reset will be ignored on leanback launcher");
        }
        return appsRow;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getWorkspaceSelector() {
        return By.res(getSupportedLauncherPackage(), "main_list_view");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getSearchRowSelector() {
        return By.res(getSupportedLauncherPackage(), "search_view");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getNotificationRowSelector() {
        return By.res(getSupportedLauncherPackage(), "notification_view");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getAppsRowSelector() {
        return By.res(getSupportedLauncherPackage(), "list").desc("Apps");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getGamesRowSelector() {
        return By.res(getSupportedLauncherPackage(), "list").desc("Games");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getSettingsRowSelector() {
        return By.res(getSupportedLauncherPackage(), "list").desc("").hasDescendant(
                By.res(getSupportedLauncherPackage(), "icon"), 3);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getAppWidgetSelector() {
        return By.clazz(getSupportedLauncherPackage(), "android.appwidget.AppWidgetHostView");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getNowPlayingCardSelector() {
        return By.res(getSupportedLauncherPackage(), "content_text").text("Now Playing");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Direction getAllAppsScrollDirection() {
        return Direction.RIGHT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getAllAppsSelector() {
        // On Leanback launcher the Apps row corresponds to the All Apps on phone UI
        return getAppsRowSelector();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long launch(String appName, String packageName) {
        BySelector app = By.res(getSupportedLauncherPackage(), "app_banner").desc(appName);
        return launchApp(this, app, packageName, isGame(packageName));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInstrumentation(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void search(String query) {
        if (selectSearchRow() == null) {
            throw new RuntimeException("Could not find search row.");
        }

        BySelector keyboardOrb = By.res(getSupportedLauncherPackage(), "keyboard_orb");
        UiObject2 orbButton = mDevice.wait(Until.findObject(keyboardOrb), SHORT_WAIT_TIME);
        if (orbButton == null) {
            throw new RuntimeException("Could not find keyboard orb.");
        }
        if (orbButton.isFocused()) {
            mDPadUtil.pressDPadCenter();
        } else {
            // Move the focus to keyboard orb by DPad button.
            mDPadUtil.pressDPadRight();
            if (orbButton.isFocused()) {
                mDPadUtil.pressDPadCenter();
            }
        }
        mDevice.wait(Until.gone(keyboardOrb), SHORT_WAIT_TIME);

        BySelector searchEditor = By.res(PACKAGE_SEARCH, "search_text_editor");
        UiObject2 editText = mDevice.wait(Until.findObject(searchEditor), SHORT_WAIT_TIME);
        if (editText == null) {
            throw new RuntimeException("Could not find search text input.");
        }

        editText.setText(query);
        SystemClock.sleep(SHORT_WAIT_TIME);

        // Note that Enter key is pressed instead of DPad keys to dismiss leanback IME
        mDPadUtil.pressEnter();
        mDevice.wait(Until.gone(searchEditor), SHORT_WAIT_TIME);
    }

    /**
     * {@inheritDoc}
     *
     * Assume that the rows are sorted in the following order from the top:
     *  Search, Notification(, Partner), Apps, Games, Settings(, and Inputs)
     */
    @Override
    public UiObject2 selectNotificationRow() {
        if (!isNotificationRowSelected()) {
            open();
            mDPadUtil.pressHome();    // Home key to move to the first card in the Notification row
        }
        return mDevice.wait(Until.findObject(
                getNotificationRowSelector().hasDescendant(By.focused(true), 3)), SHORT_WAIT_TIME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiObject2 selectSearchRow() {
        if (!isSearchRowSelected()) {
            selectNotificationRow();
            mDPadUtil.pressDPadUp();
        }
        return mDevice.wait(Until.findObject(
                getSearchRowSelector().hasDescendant(By.focused(true))), SHORT_WAIT_TIME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiObject2 selectAppsRow() {
        // Start finding Apps row from Notification row
        return findRow(getAppsRowSelector());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiObject2 selectGamesRow() {
        return findRow(getGamesRowSelector());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiObject2 selectSettingsRow() {
        // Assume that the Settings row is at the lowest bottom
        UiObject2 settings = findRow(getSettingsRowSelector(), Direction.DOWN);
        if (settings != null && isSettingsRowSelected()) {
            return settings;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasAppWidgetSelector() {
        return mDevice.wait(Until.hasObject(getAppWidgetSelector()), SHORT_WAIT_TIME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNowPlayingCard() {
        return mDevice.wait(Until.hasObject(getNowPlayingCardSelector()), SHORT_WAIT_TIME);
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getAllAppsButtonSelector() {
        throw new UnsupportedOperationException(
                "The 'All Apps' button is not available on Leanback Launcher.");
    }

    @SuppressWarnings("unused")
    @Override
    public UiObject2 openAllWidgets(boolean reset) {
        throw new UnsupportedOperationException(
                "All Widgets is not available on Leanback Launcher.");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getAllWidgetsSelector() {
        throw new UnsupportedOperationException(
                "All Widgets is not available on Leanback Launcher.");
    }

    @SuppressWarnings("unused")
    @Override
    public Direction getAllWidgetsScrollDirection() {
        throw new UnsupportedOperationException(
                "All Widgets is not available on Leanback Launcher.");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getHotSeatSelector() {
        throw new UnsupportedOperationException(
                "Hot Seat is not available on Leanback Launcher.");
    }

    @SuppressWarnings("unused")
    @Override
    public Direction getWorkspaceScrollDirection() {
        throw new UnsupportedOperationException(
                "Workspace is not available on Leanback Launcher.");
    }

    protected long launchApp(ILauncherStrategy launcherStrategy, BySelector app,
            String packageName, boolean isGame) {
        return launchApp(launcherStrategy, app, packageName, isGame, MAX_SCROLL_ATTEMPTS);
    }

    protected long launchApp(ILauncherStrategy launcherStrategy, BySelector app,
            String packageName, boolean isGame, int maxScrollAttempts) {
        unlockDeviceIfAsleep();

        if (isAppOpen(packageName)) {
            // Application is already open
            return 0;
        }

        // Go to the home page
        launcherStrategy.open();

        // attempt to find the app/game icon if it's not already on the screen
        UiObject2 container;
        if (isGame) {
            container = selectGamesRow();
        } else {
            container = launcherStrategy.openAllApps(false);
        }
        UiObject2 appIcon = container.findObject(app);
        int attempts = 0;
        while (attempts++ < maxScrollAttempts) {
            UiObject2 focused = container.wait(Until.findObject(By.focused(true)), SHORT_WAIT_TIME);
            if (focused == null) {
                throw new IllegalStateException(
                        "The App/Game row may have lost focus while activity is in transition");
            }

            // Compare the focused icon and the app icon to search for.
            UiObject2 focusedIcon = focused.findObject(
                    By.res(getSupportedLauncherPackage(), "app_banner"));

            if (appIcon == null) {
                appIcon = findApp(container, focusedIcon, app);
                if (appIcon == null) {
                    throw new RuntimeException("Failed to find the app icon on screen: "
                            + packageName);
                }
                continue;
            } else if (focusedIcon.equals(appIcon)) {
                // The app icon is on the screen, and selected.
                break;
            } else {
                // The app icon is on the screen, but not selected yet
                // Move one step closer to the app icon
                Point currentPosition = focusedIcon.getVisibleCenter();
                Point targetPosition = appIcon.getVisibleCenter();
                int dx = targetPosition.x - currentPosition.x;
                int dy = targetPosition.y - currentPosition.y;
                final int MARGIN = 10;
                // The sequence of moving should be kept in the following order so as not to
                // be stuck in case that the apps row are not even.
                if (dx < -MARGIN) {
                    mDPadUtil.pressDPadLeft();
                    continue;
                }
                if (dy < -MARGIN) {
                    mDPadUtil.pressDPadUp();
                    continue;
                }
                if (dx > MARGIN) {
                    mDPadUtil.pressDPadRight();
                    continue;
                }
                if (dy > MARGIN) {
                    mDPadUtil.pressDPadDown();
                    continue;
                }
                throw new RuntimeException(
                        "Failed to navigate to the app icon on screen: " + packageName);
            }
        }

        if (attempts == maxScrollAttempts) {
            throw new RuntimeException(
                    "scrollBackToBeginning: exceeded max attempts: " + maxScrollAttempts);
        }

        // The app icon is already found and focused.
        long ready = SystemClock.uptimeMillis();
        mDPadUtil.pressDPadCenter();
        if (!mDevice.wait(Until.hasObject(By.pkg(packageName).depth(0)), APP_LAUNCH_TIMEOUT)) {
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

    /**
     * Launch the named notification
     *
     * @param appName - the name of the application to launch in the Notification row
     * @return true if application is verified to be in foreground after launch; false otherwise.
     */
    public boolean launchNotification(String appName) {
        // Wait until notification content is loaded
        long currentTimeMs = System.currentTimeMillis();
        while (isNotificationPreparing() &&
                (System.currentTimeMillis() - currentTimeMs > NOTIFICATION_WAIT_TIME)) {
            Log.d(LOG_TAG, "Preparing recommendation...");
            SystemClock.sleep(SHORT_WAIT_TIME);
        }

        // Find a Notification that matches a given app name
        UiObject2 card = findNotificationCard(
                By.res(getSupportedLauncherPackage(), "card").descContains(appName));
        if (card == null) {
            throw new IllegalStateException(
                    String.format("The Notification that matches %s not found", appName));
        }
        Log.d(LOG_TAG,
                String.format("The application %s found in the Notification row. [content_desc]%s",
                        appName, card.getContentDescription()));

        // Click and wait until the Notification card opens
        return mDPadUtil.pressDPadCenterAndWait(Until.newWindow(), APP_LAUNCH_TIMEOUT);
    }

    protected boolean isSearchRowSelected() {
        UiObject2 row = mDevice.findObject(getSearchRowSelector());
        if (row == null) {
            return false;
        }
        return row.hasObject(By.focused(true));
    }

    protected boolean isAppsRowSelected() {
        UiObject2 row = mDevice.findObject(getAppsRowSelector());
        if (row == null) {
            return false;
        }
        return row.hasObject(By.focused(true));
    }

    protected boolean isGamesRowSelected() {
        UiObject2 row = mDevice.findObject(getGamesRowSelector());
        if (row == null) {
            return false;
        }
        return row.hasObject(By.focused(true));
    }

    protected boolean isNotificationRowSelected() {
        UiObject2 row = mDevice.findObject(getNotificationRowSelector());
        if (row == null) {
            return false;
        }
        return row.hasObject(By.focused(true));
    }

    protected boolean isSettingsRowSelected() {
        // Settings label is only visible if the settings row is selected
        UiObject2 row = mDevice.findObject(getSettingsRowSelector());
        return (row != null && row.hasObject(
                By.res(getSupportedLauncherPackage(), "label").text("Settings")));
    }

    protected boolean isAppOpen (String appPackage) {
        return mDevice.hasObject(By.pkg(appPackage).depth(0));
    }

    protected void unlockDeviceIfAsleep () {
        // Turn screen on if necessary
        try {
            if (!mDevice.isScreenOn()) {
                mDevice.wakeUp();
            }
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "Failed to unlock the screen-off device.", e);
        }
    }

    protected boolean isNotificationPreparing() {
        // Ensure that the Notification row is visible on screen
        if (!mDevice.hasObject(getNotificationRowSelector())) {
            selectNotificationRow();
        }
        return mDevice.hasObject(By.res(getSupportedLauncherPackage(), "notification_preparing"));
    }

    protected UiObject2 findNotificationCard(BySelector selector) {
        // Move to the first notification row, start searching to the right, then to the left
        mDPadUtil.pressHome();
        UiObject2 card;
        if ((card = findNotificationCard(selector, Direction.RIGHT)) != null) {
            return card;
        }
        if ((card = findNotificationCard(selector, Direction.LEFT)) != null) {
            return card;
        }
        return null;
    }

    /**
     * Find the card in the Notification row that matches BySelector in a given direction.
     * If a card is already selected, it returns regardless of the direction parameter.
     * @param selector
     * @param direction
     * @return
     */
    protected UiObject2 findNotificationCard(BySelector selector, Direction direction) {
        if (direction != Direction.RIGHT && direction != Direction.LEFT) {
            throw new IllegalArgumentException("Required to go either left or right to find a card"
                    + "in the Notification row");
        }

        // Find the Notification row
        UiObject2 notification = mDevice.findObject(getNotificationRowSelector());
        if (notification == null) {
            mDPadUtil.pressHome();
            notification = mDevice.wait(Until.findObject(getNotificationRowSelector()),
                    SHORT_WAIT_TIME);
            if (notification == null) {
                throw new IllegalStateException("The Notification row is not found");
            }
        }

        // Find a focused card in the Notification row that matches a given selector
        UiObject2 currentFocus = notification.findObject(
                By.res(getSupportedLauncherPackage(), "card").focused(true));
        UiObject2 previousFocus = null;
        while (!currentFocus.equals(previousFocus)) {
            if (currentFocus.hasObject(selector)) {
                return currentFocus;   // Found
            }
            mDPadUtil.pressDPad(direction);
            previousFocus = currentFocus;
            currentFocus = notification.findObject(
                    By.res(getSupportedLauncherPackage(), "card").focused(true));
        }
        Log.d(LOG_TAG, "Failed to find the Notification card until it reaches the end.");
        return null;
    }

    protected UiObject2 findApp(UiObject2 container, UiObject2 focusedIcon, BySelector app) {
        UiObject2 appIcon;
        // The app icon is not on the screen.
        // Search by going left first until it finds the app icon on the screen
        String prevText = focusedIcon.getContentDescription();
        String nextText;
        do {
            mDPadUtil.pressDPadLeft();
            appIcon = container.findObject(app);
            if (appIcon != null) {
                return appIcon;
            }
            nextText = container.findObject(By.focused(true)).findObject(
                    By.res(getSupportedLauncherPackage(),
                            "app_banner")).getContentDescription();
        } while (nextText != null && !nextText.equals(prevText));

        // If we haven't found it yet, search by going right
        do {
            mDPadUtil.pressDPadRight();
            appIcon = container.findObject(app);
            if (appIcon != null) {
                return appIcon;
            }
            nextText = container.findObject(By.focused(true)).findObject(
                    By.res(getSupportedLauncherPackage(),
                            "app_banner")).getContentDescription();
        } while (nextText != null && !nextText.equals(prevText));
        return null;
    }

    /**
     * Find the focused row that matches BySelector in a given direction.
     * If the row is already selected, it returns regardless of the direction parameter.
     * @param row
     * @param direction
     * @return
     */
    protected UiObject2 findRow(BySelector row, Direction direction) {
        if (direction != Direction.DOWN && direction != Direction.UP) {
            throw new IllegalArgumentException("Required to go either up or down to find rows");
        }

        UiObject2 currentFocused = mDevice.wait(Until.findObject(By.focused(true)),
                SHORT_WAIT_TIME);
        UiObject2 prevFocused = null;
        while (!currentFocused.equals(prevFocused)) {
            UiObject2 rowObject = mDevice.findObject(row);
            if (rowObject != null && rowObject.hasObject(By.focused(true))) {
                return rowObject;   // Found
            }

            mDPadUtil.pressDPad(direction);
            prevFocused = currentFocused;
            currentFocused = mDevice.wait(Until.findObject(By.focused(true)), SHORT_WAIT_TIME);
        }
        Log.d(LOG_TAG, "Failed to find the row until it reaches the end.");
        return null;
    }

    protected UiObject2 findRow(BySelector row) {
        UiObject2 rowObject;
        // Search by going down first until it finds the focused row.
        if ((rowObject = findRow(row, Direction.DOWN)) != null) {
            return rowObject;
        }
        // If we haven't found it yet, search by going up
        if ((rowObject = findRow(row, Direction.UP)) != null) {
            return rowObject;
        }
        return null;
    }

    public void selectRestrictedProfile() {
        UiObject2 button = findSettingInRow(
                By.res(getSupportedLauncherPackage(), "label").text("Restricted Profile"),
                Direction.RIGHT);
        if (button == null) {
            throw new IllegalStateException("Restricted Profile not found on launcher");
        }
        mDPadUtil.pressDPadCenterAndWait(Until.newWindow(), APP_LAUNCH_TIMEOUT);
    }

    protected UiObject2 findSettingInRow(BySelector selector, Direction direction) {
        if (direction != Direction.RIGHT && direction != Direction.LEFT) {
            throw new IllegalArgumentException("Either left or right is allowed");
        }
        if (!isSettingsRowSelected()) {
            selectSettingsRow();
        }

        UiObject2 setting;
        UiObject2 currentFocused = mDevice.findObject(By.focused(true));
        UiObject2 prevFocused = null;
        while (!currentFocused.equals(prevFocused)) {
            if ((setting = currentFocused.findObject(selector)) != null) {
                return setting;
            }

            mDPadUtil.pressDPad(direction);
            mDevice.waitForIdle();
            prevFocused = currentFocused;
            currentFocused = mDevice.findObject(By.focused(true));
        }
        Log.d(LOG_TAG, "Failed to find the setting in Settings row.");
        return null;
    }

    private boolean isGame(String packageName) {
        boolean isGame = false;
        if (mInstrumentation != null) {
            try {
                ApplicationInfo appInfo =
                        mInstrumentation.getTargetContext().getPackageManager().getApplicationInfo(
                                packageName, 0);
                // TV game apps should use the "isGame" tag added since the L release. They are
                // listed on the Games row on the Leanback Launcher.
                isGame = ((appInfo.flags & ApplicationInfo.FLAG_IS_GAME) != 0) ||
                        (appInfo.metaData != null && appInfo.metaData.getBoolean("isGame", false));
                Log.i(LOG_TAG, String.format("The package %s isGame: %b", packageName, isGame));
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(LOG_TAG,
                        String.format("No package found: %s, error:%s", packageName, e.toString()));
                return false;
            }
        }
        return isGame;
    }
}
