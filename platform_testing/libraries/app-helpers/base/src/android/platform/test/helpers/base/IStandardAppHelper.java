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

package android.platform.test.helpers;

import android.content.pm.PackageManager.NameNotFoundException;

import java.io.IOException;

public interface IStandardAppHelper extends IAppHelper {

    /**
     * Setup expectation: On the launcher home screen.
     *
     * Launches the desired application.
     */
    abstract void open();

    /**
     * Setup expectation: None
     * <p>
     * Presses back until the launcher package is visible, i.e. the home screen. This can be
     * overriden for custom functionality, however consider and document the exit state if doing so.
     */
    abstract void exit();

    /**
     * Setup expectations: This application is on the initial launch screen.
     * <p>
     * Dismiss all visible relevant dialogs and block until this process is complete.
     */
    abstract void dismissInitialDialogs();

    /**
     * Setup expectations: None
     * <p>
     * Get the target application's component package.
     * @return the package name for this helper's application.
     */
    abstract String getPackage();

    /**
     * Setup expectations: None.
     * <p>
     * Get the target application's launcher name.
     * @return the name of this application's launcher.
     */
    abstract String getLauncherName();

    /**
     * Setup expectations: None
     * <p>
     * Get the target application's version String.
     * @return the version code
     * @throws NameNotFoundException if {@code getPackage} is not found
     */
    abstract String getVersion() throws NameNotFoundException;

    /**
     * Setup expectations: None
     * @return true, if this app's package is the root (depth 0), and false otherwise
     */
    abstract boolean isAppInForeground();

    /**
     * Setup expectations: None
     * <p>
     * Captures a screenshot and UI XML with the supplied name.
     * @param name the screenshot prefix
     * @throws IOException if there is a capture failure
     * @throws RuntimeException if creating the screenshot directory fails.
     */
    abstract boolean captureScreenshot(String name) throws IOException;

    /**
     * Sends text events to the device through key codes.
     * <p>
     * Note: use this only when text accessibility is not supported.
     * @param text the text to input as events
     * @param delay the delay between each event
     * @return true if successful, false otherwise
     */
    abstract boolean sendTextEvents(String text, long delay);
}
