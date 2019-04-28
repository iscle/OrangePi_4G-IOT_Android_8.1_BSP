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

package android.support.test.launcherhelper;

import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
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
import android.view.KeyEvent;

import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


public class TvLauncherStrategy implements ILeanbackLauncherStrategy {

    private static final String LOG_TAG = TvLauncherStrategy.class.getSimpleName();
    private static final String PACKAGE_LAUNCHER = "com.google.android.tvlauncher";
    private static final String PACKAGE_SETTINGS = "com.android.tv.settings";
    private static final String CHANNEL_TITLE_WATCH_NEXT = "Watch Next";

    // Build version
    private static final int BUILD_INT_BANDGAP = 1010100000;

    // Wait time
    private static final int UI_APP_LAUNCH_WAIT_TIME_MS = 10000;
    private static final int UI_WAIT_TIME_MS = 5000;
    private static final int UI_TRANSITION_WAIT_TIME_MS = 1000;
    private static final int NO_WAIT = 0;

    // Note that the selector specifies criteria for matching an UI element from/to a focused item
    private static final BySelector SELECTOR_TOP_ROW = By.res(PACKAGE_LAUNCHER, "top_row");
    private static final BySelector SELECTOR_APPS_ROW = By.res(PACKAGE_LAUNCHER, "apps_row");
    private static final BySelector SELECTOR_ALL_APPS_VIEW =
            By.res(PACKAGE_LAUNCHER, "row_list_view");
    private static final BySelector SELECTOR_ALL_APPS_LOGO =
            By.res(PACKAGE_LAUNCHER, "channel_logo").focused(true).descContains("Apps");
    private static final BySelector SELECTOR_CONFIG_CHANNELS_ROW =
            By.res(PACKAGE_LAUNCHER, "configure_channels_row");
    private static final BySelector SELECTOR_CONTROLLER_MOVE = By.res(PACKAGE_LAUNCHER, "move");
    private static final BySelector SELECTOR_CONTROLLER_REMOVE = By.res(PACKAGE_LAUNCHER, "remove");
    private static final BySelector SELECTOR_NOTIFICATIONS_ROW = By.res(PACKAGE_LAUNCHER,
            "notifications_row");

    protected UiDevice mDevice;
    protected DPadUtil mDPadUtil;
    private Instrumentation mInstrumentation;

    /** A {@link UiCondition} is a condition to be satisfied by BaseView or UI actions. */
    public interface UiCondition {
        boolean apply(UiObject2 focus);
    }

    /**
     * State of an item in Apps row or channel row on the Home Screen.
     */
    public enum HomeRowState {
        /**
         * State of a row when this or some other items in Apps row or channel row is not selected
         */
        DEFAULT,
        /**
         * State of a row when this or some other items in Apps row or channel row is selected.
         */
        SELECTED,
        /**
         * State of an item when one of the zoomed out states is focused:
         * zoomed_out, channel_actions, move
         */
        ZOOMED_OUT
    }

    /**
     * State of an item in the HomeAppState.ZOOMED_OUT mode
     */
    public enum HomeControllerState {
        /**
         * Default state of an app. one of the program cards or non-channel rows is selected
         */
        DEFAULT,
        /**
         * One of the channel logos is selected, the channel title is zoomed out
         */
        CHANNEL_LOGO,
        /**
         * State when a channel is selected and showing channel actions (remove and move).
         */
        CHANNEL_ACTIONS,
        /**
         * State when a channel is being moved.
         */
        MOVE_CHANNEL
    }

    /**
     * A TvLauncherUnsupportedOperationException is an exception specific to TV Launcher. This will
     * be thrown when the feature/method is not available on the TV Launcher.
     */
    class TvLauncherUnsupportedOperationException extends UnsupportedOperationException {
        TvLauncherUnsupportedOperationException() {
            super();
        }
        TvLauncherUnsupportedOperationException(String msg) {
            super(msg);
        }
    }

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
            if (!mDevice.wait(Until.hasObject(getWorkspaceSelector()), UI_WAIT_TIME_MS)) {
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
                throw new RuntimeException("Failed to open TV launcher");
            }
            mDevice.waitForIdle();
        }
    }

    /**
     * {@inheritDoc}
     * There are two different ways to open All Apps view. If longpress is true, it will long press
     * the HOME key to open it. Otherwise it will navigate to the "APPS" logo on the Apps row.
     */
    @Override
    public UiObject2 openAllApps(boolean longpress) {
        if (!mDevice.hasObject(getAllAppsSelector())) {
            if (longpress) {
                mDPadUtil.longPressKeyCode(KeyEvent.KEYCODE_HOME);
            } else {
                Assert.assertNotNull("Could not find all apps logo", selectAllAppsLogo());
                mDPadUtil.pressDPadCenter();
            }
        }
        return mDevice.wait(Until.findObject(getAllAppsSelector()), UI_WAIT_TIME_MS);
    }

    public boolean openSettings() {
        Assert.assertNotNull(selectTopRow());
        Assert.assertNotNull(selectBidirect(By.res(getSupportedLauncherPackage(), "settings"),
                Direction.RIGHT));
        mDPadUtil.pressDPadCenter();
        return mDevice.wait(
                Until.hasObject(By.res(PACKAGE_SETTINGS, "decor_title").text("Settings")),
                UI_WAIT_TIME_MS);
    }

    public boolean openCustomizeChannels() {
        Assert.assertNotNull(selectCustomizeChannelsRow());
        Assert.assertNotNull(
                select(By.res(getSupportedLauncherPackage(), "button"), Direction.RIGHT,
                        UI_WAIT_TIME_MS));
        mDPadUtil.pressDPadCenter();
        return mDevice.wait(
                Until.hasObject(By.res(PACKAGE_LAUNCHER, "decor_title").text("Customize channels")),
                UI_WAIT_TIME_MS);
    }

    /**
     * Get the launcher's version code.
     * @return the version code. -1 if the launcher package is not found.
     */
    public int getVersionCode() {
        String pkg = getSupportedLauncherPackage();
        if (null == pkg || pkg.isEmpty()) {
            throw new RuntimeException("Can't find version of empty package");
        }
        if (mInstrumentation == null) {
            Log.w(LOG_TAG, "Instrumentation is null. setInstrumentation should be called "
                    + "to get the version code");
            return -1;
        }
        PackageManager pm = mInstrumentation.getContext().getPackageManager();
        PackageInfo pInfo = null;
        try {
            pInfo = pm.getPackageInfo(pkg, 0);
            return pInfo.versionCode;
        } catch (NameNotFoundException e) {
            Log.w(LOG_TAG, String.format("package name is not found: %s", pkg));
            return -1;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getWorkspaceSelector() {
        return By.res(getSupportedLauncherPackage(), "home_view_container");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getSearchRowSelector() {
        return  SELECTOR_TOP_ROW;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getAppsRowSelector() {
        return SELECTOR_APPS_ROW;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getGamesRowSelector() {
        // Note that the apps and games are now in the same row on new TV Launcher.
        return getAppsRowSelector();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Direction getAllAppsScrollDirection() {
        return Direction.DOWN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getAllAppsSelector() {
        return SELECTOR_ALL_APPS_VIEW;
    }

    public BySelector getAllAppsLogoSelector() {
        return SELECTOR_ALL_APPS_LOGO;
    }

    /**
     * Returns a {@link BySelector} describing a given favorite app
     */
    public BySelector getFavoriteAppSelector(String appName) {
        return By.res(getSupportedLauncherPackage(), "favorite_app_banner").desc(appName);
    }

    /**
     * Returns a {@link BySelector} describing a given app in Apps View
     */
    public BySelector getAppInAppsViewSelector(String appName) {
        if (getVersionCode() > BUILD_INT_BANDGAP) {
            // bandgap or higher
            return By.res(getSupportedLauncherPackage(), "banner_image").desc(appName);
        }
        return By.res(getSupportedLauncherPackage(), "app_title").text(appName);
    }

    // Return a {@link BySelector} indicating a channel logo (in either zoom-in or default mode)
    public BySelector getChannelLogoSelector() {
        return By.res(getSupportedLauncherPackage(), "channel_logo");
    }
    public BySelector getChannelLogoSelector(String channelTitle) {
        return getChannelLogoSelector().desc(channelTitle);
    }

    // Return the list of rows including "top_row", "apps_row", "channel"
    // and "configure_channels_row"
    public BySelector getRowListSelector() {
        return By.res(getSupportedLauncherPackage(), "home_row_list");
    }

    public HomeRowState getHomeRowState() {
        HomeRowState state = HomeRowState.DEFAULT;
        if (isAppsRowSelected() || isChannelRowSelected()) {
            if (getHomeControllerState() != HomeControllerState.DEFAULT) {
                state = HomeRowState.ZOOMED_OUT;
            } else {
                state = HomeRowState.SELECTED;
            }
        }
        Log.d(LOG_TAG, String.format("[HomeRowState]%s", state));
        return state;
    }

    public HomeControllerState getHomeControllerState() {
        HomeControllerState state = HomeControllerState.DEFAULT;
        UiObject2 focus = findFocus();
        if (focus.hasObject(getChannelLogoSelector())) {
            state = HomeControllerState.CHANNEL_LOGO;
        } else if (focus.hasObject(SELECTOR_CONTROLLER_MOVE)) {
            state = HomeControllerState.MOVE_CHANNEL;
        } else if (focus.hasObject(SELECTOR_CONTROLLER_REMOVE)) {
            state = HomeControllerState.CHANNEL_ACTIONS;
        }
        Log.d(LOG_TAG, String.format("[HomeControllerState]%s", state));
        return state;
    }

    // Return an index of a focused app or program in the Row. 0-based.
    public int getFocusedItemIndexInRow() {
        UiObject2 focusedChannel = mDevice.wait(Until.findObject(
                By.res(getSupportedLauncherPackage(), "items_list")
                        .hasDescendant(By.focused(true))), UI_WAIT_TIME_MS);
        if (focusedChannel == null) {
            Log.w(LOG_TAG, "getFocusedItemIndexInRow: no channel has a focused item. "
                    + "A focus may be at a logo or the top row.");
            return -1;
        }
        int index = 0;
        for (UiObject2 program : focusedChannel.getChildren()) {
            if (findFocus(program, NO_WAIT) != null) {
                break;
            }
            ++index;
        }
        Log.d(LOG_TAG, String.format("getFocusedItemIndexInRow [index]%d", index));
        return index;
    }

    /**
     * Return true if any item in Channel row is selected. eg, program, zoomed out, channel actions
     */
    public boolean isChannelRowSelected(String channelTitle) {
        return isChannelRowSelected(getChannelLogoSelector(channelTitle));
    }
    public boolean isChannelRowSelected() {
        return isChannelRowSelected(getChannelLogoSelector());
    }
    protected boolean isChannelRowSelected(final BySelector channelSelector) {
        UiObject2 rowList = mDevice.findObject(getRowListSelector());
        for (UiObject2 row : rowList.getChildren()) {
            if (findFocus(row, NO_WAIT) != null) {
                return row.hasObject(channelSelector);
            }
        }
        return false;
    }

    public boolean isOnHomeScreen() {
        if (!isAppOpen(getSupportedLauncherPackage())) {
            Log.w(LOG_TAG, "This launcher is not in foreground");
        }
        return mDevice.hasObject(getWorkspaceSelector());
    }

    public boolean isFirstAppSelected() {
        if (!isAppsRowSelected()) {
            return false;
        }
        return (getFocusedItemIndexInRow() == 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long launch(String appName, String packageName) {
        Log.d(LOG_TAG, String.format("launching [name]%s [package]%s", appName, packageName));
        return launchApp(this, appName, packageName, isGame(packageName));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This function must be called before any UI test runs on TV.
     * </p>
     */
    @Override
    public void setInstrumentation(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiObject2 selectSearchRow() {
        // The Search orb is now on top row on TV Launcher
        return selectTopRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiObject2 selectAppsRow() {
        return selectAppsRow(false);
    }

    public UiObject2 selectAppsRow(boolean useHomeKey) {
        Log.d(LOG_TAG, "selectAppsRow");
        if (!isOnHomeScreen()) {
            Log.w(LOG_TAG, "selectAppsRow should be called on Home screen");
            open();
        }

        if (useHomeKey) {
            // Press the HOME key to move a focus to the first app in the Apps row.
            mDPadUtil.pressHome();
        } else {
            selectBidirect(getAppsRowSelector().hasDescendant(By.focused(true)),
                    Direction.DOWN);
        }
        return isAppsRowSelected() ? findFocus() : null;
    }

    /**
     * Select a channel row that matches a given name.
     */
    public UiObject2 selectChannelRow(final String channelTitle) {
        Log.d(LOG_TAG, String.format("selectChannelRow [channel]%s", channelTitle));

        // Move out if any channel action button (eg, remove, move) is focused, so that
        // it can scroll vertically to find a given row.
        selectBidirect(
                new UiCondition() {
                    @Override
                    public boolean apply(UiObject2 focus) {
                        HomeControllerState state = getHomeControllerState();
                        return !(state == HomeControllerState.CHANNEL_ACTIONS
                                || state == HomeControllerState.MOVE_CHANNEL);
                    }
                }, Direction.RIGHT);

        // Then scroll vertically to find a given row
        UiObject2 focused = selectBidirect(
                new UiCondition() {
                    @Override
                    public boolean apply(UiObject2 focus) {
                        return isChannelRowSelected(channelTitle);
                    }
                }, Direction.DOWN);
        return focused;
    }

    /**
     * Select the All Apps logo (or icon).
     */
    public UiObject2 selectAllAppsLogo() {
        Log.d(LOG_TAG, "selectAllAppsLogo");
        return selectChannelLogo("Apps");
    }

    public UiObject2 selectChannelLogo(final String channelTitle) {
        Log.d(LOG_TAG, String.format("selectChannelLogo [channel]%s", channelTitle));

        if (!isChannelRowSelected(channelTitle)) {
            Assert.assertNotNull(selectChannelRow(channelTitle));
        }
        return selectBidirect(
                new UiCondition() {
                    @Override
                    public boolean apply(UiObject2 focus) {
                        return getHomeControllerState() == HomeControllerState.CHANNEL_LOGO;
                    }
                },
                Direction.LEFT);
    }

    /**
     * Returns a {@link UiObject2} describing the Top row on TV Launcher
     * @return
     */
    public UiObject2 selectTopRow() {
        open();
        mDPadUtil.pressHome();
        // Move up until it reaches the top.
        int maxAttempts = 3;
        while (maxAttempts-- > 0 && move(Direction.UP)) {
            SystemClock.sleep(UI_TRANSITION_WAIT_TIME_MS);
        }
        return mDevice.wait(
                Until.findObject(getSearchRowSelector().hasDescendant(By.focused(true))),
                UI_TRANSITION_WAIT_TIME_MS);
    }

    /**
     * Returns a {@link UiObject2} describing the Notification row on TV Launcher
     * @return
     */
    public UiObject2 selectNotificationRow() {
        return selectBidirect(By.copy(SELECTOR_NOTIFICATIONS_ROW).hasDescendant(By.focused(true)),
                Direction.UP);
    }

    /**
     * Returns a {@link UiObject2} describing the customize channel row on TV Launcher
     * @return
     */
    public UiObject2 selectCustomizeChannelsRow() {
        return select(By.copy(SELECTOR_CONFIG_CHANNELS_ROW).hasDescendant(By.focused(true)),
                Direction.DOWN, UI_TRANSITION_WAIT_TIME_MS);
    }

    public UiObject2 selectWatchNextRow() {
        return selectChannelRow(CHANNEL_TITLE_WATCH_NEXT);
    }

    /**
     * Select the first app icon in the Apps row
     */
    public UiObject2 selectFirstAppIcon() {
        if (!isFirstAppSelected()) {
            Assert.assertNotNull("The Apps row must be selected.",
                    selectAppsRow(/*useHomeKey*/ true));
            mDPadUtil.pressBack();
            if (getHomeRowState() == HomeRowState.ZOOMED_OUT) {
                mDPadUtil.pressDPadRight();
            }
        }
        Assert.assertTrue("The first app in Apps row must be selected.", isFirstAppSelected());
        return findFocus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiObject2 selectGamesRow() {
        return selectAppsRow();
    }

    /**
     * Select the given app in All Apps activity.
     * When the All Apps opens, the focus is always at the top right.
     * Search from left to right, and down to the next row, from right to left, and
     * down to the next row like a zigzag pattern until the app is found.
     */
    protected UiObject2 selectAppInAllApps(BySelector appSelector, String packageName) {
        Assert.assertTrue(mDevice.hasObject(getAllAppsSelector()));

        // Assume that the focus always starts at the top left of the Apps view.
        final int maxScrollAttempts = 20;
        final int margin = 30;
        int attempts = 0;
        UiObject2 focused = null;
        UiObject2 expected = null;
        while (attempts++ < maxScrollAttempts) {
            focused = mDevice.wait(Until.findObject(By.focused(true)), UI_WAIT_TIME_MS);
            expected = mDevice.wait(Until.findObject(appSelector), UI_WAIT_TIME_MS);

            if (expected == null) {
                mDPadUtil.pressDPadDown();
                continue;
            } else if (focused.hasObject(appSelector)) {
                // The app icon is selected.
                Log.i(LOG_TAG, String.format("The app %s is selected", packageName));
                break;
            } else {
                // The app icon is on the screen, but not selected yet
                // Move one step closer to the app icon
                Point currentPosition = focused.getVisibleCenter();
                Point targetPosition = expected.getVisibleCenter();
                int dx = targetPosition.x - currentPosition.x;
                int dy = targetPosition.y - currentPosition.y;
                Log.d(LOG_TAG, String.format("selectAppInAllApps: [dx,dx][%d,%d]", dx, dy));
                if (dy > margin) {
                    mDPadUtil.pressDPadDown();
                    continue;
                }
                if (dx > margin) {
                    mDPadUtil.pressDPadRight();
                    continue;
                }
                if (dy < -margin) {
                    mDPadUtil.pressDPadUp();
                    continue;
                }
                if (dx < -margin) {
                    mDPadUtil.pressDPadLeft();
                    continue;
                }
                throw new RuntimeException(
                        "Failed to navigate to the app icon on screen: " + packageName);
            }
        }
        return expected;
    }

    /**
     * Select the given app in All Apps activity in zigzag manner.
     * When the All Apps opens, the focus is always at the top left.
     * Search from left to right, and down to the next row, from right to left, and
     * down to the next row like a zigzag pattern until it founds a given app.
     */
    protected UiObject2 selectAppInAllAppsZigZag(BySelector appSelector, String packageName) {
        Assert.assertTrue(mDevice.hasObject(getAllAppsSelector()));
        Direction direction = Direction.RIGHT;
        UiObject2 app = select(appSelector, direction, UI_TRANSITION_WAIT_TIME_MS);
        while (app == null && move(Direction.DOWN)) {
            direction = Direction.reverse(direction);
            app = select(appSelector, direction, UI_TRANSITION_WAIT_TIME_MS);
        }
        if (app != null) {
            Log.i(LOG_TAG, String.format("The app %s is selected", packageName));
        }
        return app;
    }

    /**
     * Select the given app in All Apps using the versioned BySelector for the app
     */
    public UiObject2 selectAppInAllApps(String appName, String packageName) {
        UiObject2 app = null;
        int versionCode = getVersionCode();
        if (versionCode > BUILD_INT_BANDGAP) {
            // bandgap or higher
            Log.i(LOG_TAG,
                    String.format("selectAppInAllApps: app banner has app name [versionCode]%d",
                            versionCode));
            app = selectAppInAllApps(getAppInAppsViewSelector(appName), packageName);
        } else {
            app = selectAppInAllAppsZigZag(getAppInAppsViewSelector(appName), packageName);
        }
        return app;
    }

    /**
     * Launch the given app in the Apps view.
     */
    public boolean launchAppInAppsView(String appName, String packageName) {
        Log.d(LOG_TAG, String.format("launching in apps view [appName]%s [packageName]%s",
                appName, packageName));
        openAllApps(true);
        UiObject2 app = selectAppInAllApps(appName, packageName);
        if (app == null) {
            throw new RuntimeException(
                "Failed to navigate to the app icon in the Apps view: " + packageName);
        }

        // The app icon is already found and focused. Then wait for it to open.
        BySelector appMain = By.pkg(packageName).depth(0);
        mDPadUtil.pressDPadCenter();
        if (!mDevice.wait(Until.hasObject(appMain), UI_APP_LAUNCH_WAIT_TIME_MS)) {
            Log.w(LOG_TAG, String.format(
                    "No UI element with package name %s detected.", packageName));
            return false;
        }
        return true;
    }

    protected long launchApp(ILauncherStrategy launcherStrategy, String appName,
            String packageName, boolean isGame) {
        unlockDeviceIfAsleep();

        if (isAppOpen(packageName)) {
            // Application is already open
            return 0;
        }

        // Go to the home page, and select the Apps row
        launcherStrategy.open();
        selectAppsRow();

        // Search for the app in the Favorite Apps row first.
        // If not exists, open the 'All Apps' and search for the app there
        UiObject2 app = null;
        BySelector favAppSelector = getFavoriteAppSelector(appName);
        if (mDevice.hasObject(favAppSelector)) {
            app = selectBidirect(By.copy(favAppSelector).focused(true), Direction.RIGHT);
        } else {
            openAllApps(true);
            app = selectAppInAllApps(appName, packageName);
        }
        if (app == null) {
            throw new RuntimeException(
                    "Failed to navigate to the app icon on screen: " + packageName);
        }

        // The app icon is already found and focused. Then wait for it to open.
        long ready = SystemClock.uptimeMillis();
        BySelector appMain = By.pkg(packageName).depth(0);
        mDPadUtil.pressDPadCenter();
        if (packageName != null) {
            if (!mDevice.wait(Until.hasObject(appMain), UI_APP_LAUNCH_WAIT_TIME_MS)) {
                Log.w(LOG_TAG, String.format(
                    "No UI element with package name %s detected.", packageName));
                return ILauncherStrategy.LAUNCH_FAILED_TIMESTAMP;
            }
        }
        return ready;
    }

    protected boolean isTopRowSelected() {
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
        return isAppsRowSelected();
    }

    // TODO(hyungtaekim): Move in the common helper
    protected boolean isAppOpen(String appPackage) {
        return mDevice.hasObject(By.pkg(appPackage).depth(0));
    }

    // TODO(hyungtaekim): Move in the common helper
    protected void unlockDeviceIfAsleep() {
        // Turn screen on if necessary
        try {
            if (!mDevice.isScreenOn()) {
                mDevice.wakeUp();
            }
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "Failed to unlock the screen-off device.", e);
        }
    }

    private boolean isGame(String packageName) {
        boolean isGame = false;
        if (mInstrumentation != null) {
            try {
                ApplicationInfo appInfo =
                        mInstrumentation.getTargetContext().getPackageManager().getApplicationInfo(
                                packageName, 0);
                // TV game apps should use the "isGame" tag added since the L release. They are
                // listed on the Games row on the TV Launcher.
                isGame = (appInfo.metaData != null && appInfo.metaData.getBoolean("isGame", false))
                        || ((appInfo.flags & ApplicationInfo.FLAG_IS_GAME) != 0);
                Log.i(LOG_TAG, String.format("The package %s isGame: %b", packageName, isGame));
            } catch (NameNotFoundException e) {
                Log.w(LOG_TAG,
                        String.format("No package found: %s, error:%s", packageName, e.toString()));
                return false;
            }
        }
        return isGame;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void search(String query) {
        // TODO: Implement this method when the feature is available
        throw new UnsupportedOperationException("search is not yet implemented");
    }

    public void selectRestrictedProfile() {
        // TODO: Implement this method when the feature is available
        throw new UnsupportedOperationException(
                "The Restricted Profile is not yet available on TV Launcher.");
    }


    // Convenient methods for UI actions

    /**
     * Select an UI element with given {@link BySelector}. This action keeps moving a focus
     * in a given {@link Direction} until it finds a matched element.
     * @param selector the search criteria to match an element
     * @param direction the direction to find
     * @param timeoutMs timeout in milliseconds to select
     * @return a UiObject2 which represents the matched element
     */
    public UiObject2 select(final BySelector selector, Direction direction, long timeoutMs) {
        return select(new UiCondition() {
            @Override
            public boolean apply(UiObject2 focus) {
                return mDevice.hasObject(selector);
            }
        }, direction, timeoutMs);
    }

    public UiObject2 select(UiCondition condition, Direction direction, long timeoutMs) {
        UiObject2 focus = findFocus(null, timeoutMs);
        while (!condition.apply(focus)) {
            Log.d(LOG_TAG, String.format("conditional select: moving a focus from %s to %s",
                    focus, direction));
            UiObject2 focused = focus;
            mDPadUtil.pressDPad(direction);
            focus = findFocus();
            // Hack: A focus might be lost in some UI. Take one more step forward.
            if (focus == null) {
                mDPadUtil.pressDPad(direction);
                focus = findFocus(null, timeoutMs);
            }
            // Check if it reaches to an end where it no longer moves a focus to next element
            if (focused.equals(focus)) {
                Log.d(LOG_TAG, "conditional select: not found until it reaches to an end.");
                return null;
            }
        }
        Log.i(LOG_TAG, String.format("conditional select: selected", focus));
        return focus;
    }

    /**
     * Select an element with a given {@link BySelector} in both given direction and reverse.
     */
    public UiObject2 selectBidirect(BySelector selector, Direction direction) {
        Log.d(LOG_TAG, String.format("selectBidirect [direction]%s", direction));
        UiObject2 object = select(selector, direction, UI_TRANSITION_WAIT_TIME_MS);
        if (object == null) {
            object = select(selector, Direction.reverse(direction), UI_TRANSITION_WAIT_TIME_MS);
        }
        return object;
    }

    public UiObject2 selectBidirect(UiCondition condition, Direction direction) {
        UiObject2 object = select(condition, direction, UI_WAIT_TIME_MS);
        if (object == null) {
            object = select(condition, Direction.reverse(direction), UI_WAIT_TIME_MS);
        }
        return object;
    }

    /**
     * Simulate a move pressing a key code.
     * Return true if a focus is shifted on TV UI, otherwise false.
     */
    public boolean move(Direction direction) {
        int keyCode = KeyEvent.KEYCODE_UNKNOWN;
        switch (direction) {
            case LEFT:
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                break;
            case RIGHT:
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                break;
            case UP:
                keyCode = KeyEvent.KEYCODE_DPAD_UP;
                break;
            case DOWN:
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                break;
            default:
                throw new RuntimeException(String.format("This direction %s is not supported.",
                    direction));
        }
        UiObject2 focus = mDevice.wait(Until.findObject(By.focused(true)),
                UI_TRANSITION_WAIT_TIME_MS);
        mDPadUtil.pressKeyCodeAndWait(keyCode);
        return !focus.equals(mDevice.wait(Until.findObject(By.focused(true)),
                UI_TRANSITION_WAIT_TIME_MS));
    }

    /**
     * Return the {@link UiObject2} that has a focused element searching through the entire view
     * hierarchy.
     */
    public UiObject2 findFocus(UiObject2 fromObject, long timeoutMs) {
        UiObject2 focused;
        if (fromObject == null) {
            focused = mDevice.wait(Until.findObject(By.focused(true)), timeoutMs);
        } else {
            focused = fromObject.wait(Until.findObject(By.focused(true)), timeoutMs);
        }
        return focused;
    }

    public UiObject2 findFocus() {
        return findFocus(null, UI_WAIT_TIME_MS);
    }

    // Unsupported methods

    @SuppressWarnings("unused")
    @Override
    public BySelector getNotificationRowSelector() {
        throw new TvLauncherUnsupportedOperationException("No Notification row");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getSettingsRowSelector() {
        throw new TvLauncherUnsupportedOperationException("No Settings row");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getAppWidgetSelector() {
        throw new TvLauncherUnsupportedOperationException();
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getNowPlayingCardSelector() {
        throw new TvLauncherUnsupportedOperationException("No Now Playing Card");
    }

    @SuppressWarnings("unused")
    @Override
    public UiObject2 selectSettingsRow() {
        throw new TvLauncherUnsupportedOperationException("No Settings row");
    }

    @SuppressWarnings("unused")
    @Override
    public boolean hasAppWidgetSelector() {
        throw new TvLauncherUnsupportedOperationException();
    }

    @SuppressWarnings("unused")
    @Override
    public boolean hasNowPlayingCard() {
        throw new TvLauncherUnsupportedOperationException("No Now Playing Card");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getAllAppsButtonSelector() {
        throw new TvLauncherUnsupportedOperationException("No All Apps button");
    }

    @SuppressWarnings("unused")
    @Override
    public UiObject2 openAllWidgets(boolean reset) {
        throw new TvLauncherUnsupportedOperationException("No All Widgets");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getAllWidgetsSelector() {
        throw new TvLauncherUnsupportedOperationException("No All Widgets");
    }

    @SuppressWarnings("unused")
    @Override
    public Direction getAllWidgetsScrollDirection() {
        throw new TvLauncherUnsupportedOperationException("No All Widgets");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getHotSeatSelector() {
        throw new TvLauncherUnsupportedOperationException("No Hot seat");
    }

    @SuppressWarnings("unused")
    @Override
    public Direction getWorkspaceScrollDirection() {
        throw new TvLauncherUnsupportedOperationException("No Workspace");
    }
}
