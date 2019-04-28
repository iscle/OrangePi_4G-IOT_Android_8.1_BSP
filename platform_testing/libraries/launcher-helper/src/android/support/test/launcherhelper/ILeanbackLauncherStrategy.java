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
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;

/**
 * Defines the common use cases that any types of TV launcher UI automation helper should fulfill.
 * <p>Class will be instantiated by {@link LauncherStrategyFactory} based on current launcher
 * package, and a {@link UiDevice} instance will be provided via {@link #setUiDevice(UiDevice)}
 * method.
 */
public interface ILeanbackLauncherStrategy extends ILauncherStrategy {

    /**
     * Sets an instance of instrumentation
     */
    public void setInstrumentation(Instrumentation instrumentation);

    /**
     * Searches for a given query on TV launcher
     */
    public void search(String query);

    /**
     * Returns a {@link BySelector} describing the search row
     * @return
     */
    public BySelector getSearchRowSelector();

    /**
     * Returns a {@link BySelector} describing the notification row (or recommendation row)
     * @return
     */
    public BySelector getNotificationRowSelector();

    /**
     * Returns a {@link BySelector} describing the apps row
     * @return
     */
    public BySelector getAppsRowSelector();

    /**
     * Returns a {@link BySelector} describing the games row
     * @return
     */
    public BySelector getGamesRowSelector();

    /**
     * Returns a {@link BySelector} describing the settings row
     * @return
     */
    public BySelector getSettingsRowSelector();

    /**
     * Returns a {@link BySelector} describing the app widget (eg, clock widget)
     * @return
     */
    public BySelector getAppWidgetSelector();

    /**
     * Returns a {@link BySelector} describing the Now Playing card
     * @return
     */
    public BySelector getNowPlayingCardSelector();

    /**
     * Returns a {@link UiObject2} describing the focused search row,
     * or the top row on new TV Launcher
     * @return
     */
    public UiObject2 selectSearchRow();

    /**
     * Returns a {@link UiObject2} describing the focused notification row
     * @return
     */
    public UiObject2 selectNotificationRow();

    /**
     * Returns a {@link UiObject2} describing the focused apps row
     * @return
     */
    public UiObject2 selectAppsRow();

    /**
     * Returns a {@link UiObject2} describing the focused games row
     * @return
     */
    public UiObject2 selectGamesRow();

    /**
     * Returns a {@link UiObject2} describing the focused settings row
     * @return
     */
    public UiObject2 selectSettingsRow();

    /**
     * Returns whether there is a match for the given app widget selector.
     * @return
     */
    public boolean hasAppWidgetSelector();

    /**
     * Returns whether there is a Now Playing card on leanback launcher
     * @return
     */
    public boolean hasNowPlayingCard();
}
