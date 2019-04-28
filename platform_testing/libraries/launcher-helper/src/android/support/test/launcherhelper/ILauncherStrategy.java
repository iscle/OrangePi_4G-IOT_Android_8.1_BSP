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

import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;

/**
 * Defines the common use cases a launcher UI automation helper should fulfill.
 * <p>Class will be instantiated by {@link LauncherStrategyFactory} based on current launcher
 * package, and a {@link UiDevice} instance will be provided via {@link #setUiDevice(UiDevice)}
 * method.
 */
public interface ILauncherStrategy {
    public static final long LAUNCH_FAILED_TIMESTAMP = -1;

    /**
     * Returns the launcher application package that this {@link ILauncherStrategy} can automate
     * @return
     */
    public String getSupportedLauncherPackage();

    /**
     * Injects a {@link UiDevice} instance for UI interactions
     * @param uiDevice
     */
    public void setUiDevice(UiDevice uiDevice);

    /**
     * Shows the home screen of launcher
     */
    public void open();

    /**
     * Opens the all apps drawer of launcher
     * @param reset if the all apps drawer should be reset to the beginning
     * @return {@link UiObject2} representation of the all apps drawer
     */
    public UiObject2 openAllApps(boolean reset);

    /**
     * Returns a {@link BySelector} describing the button to open the all apps drawer
     * @return
     */
    public BySelector getAllAppsButtonSelector();

    /**
     * Returns a {@link BySelector} describing the all apps drawer
     * @return
     */
    public BySelector getAllAppsSelector();

    /**
     * Retrieves the all apps drawer forward scroll direction as implemented by the launcher
     * @return
     */
    public Direction getAllAppsScrollDirection();

    /**
     * Opens the all widgets drawer of launcher
     * @param reset if the all widgets drawer should be reset to the beginning
     * @return {@link UiObject2} representation of the all widgets drawer
     */
    public UiObject2 openAllWidgets(boolean reset);

    /**
     * Returns a {@link BySelector} describing the all widgets drawer
     * @return
     */
    public BySelector getAllWidgetsSelector();

    /**
     * Retrieves the all widgets drawer forward scroll direction as implemented by the launcher
     * @return
     */
    public Direction getAllWidgetsScrollDirection();

    /**
     * Returns a {@link BySelector} describing the home screen workspace
     * @return
     */
    public BySelector getWorkspaceSelector();

    /**
     * Returns a {@link BySelector} describing the home screen hot seat (app icons at the bottom)
     * @return
     */
    public BySelector getHotSeatSelector();

    /**
     * Retrieves the home screen workspace forward scroll direction as implemented by the launcher
     * @return
     */
    public Direction getWorkspaceScrollDirection();

    /**
     * Launch the named application
     * @param appName the name of the application to launch as shown in launcher
     * @param packageName the expected package name to verify that the application has been launched
     *                    into foreground. If <code>null</code> is provided, no verification is
     *                    performed.
     * @return <code>true</code> if application is verified to be in foreground after launch, or the
     *   verification is skipped; <code>false</code> otherwise.
     */
    public long launch(String appName, String packageName);
}
