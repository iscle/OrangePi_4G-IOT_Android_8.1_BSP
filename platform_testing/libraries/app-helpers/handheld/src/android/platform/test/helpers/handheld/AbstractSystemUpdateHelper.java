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

import android.app.Instrumentation;
import android.content.Intent;

public abstract class AbstractSystemUpdateHelper extends AbstractStandardAppHelper {

    protected final static String SYSTEM_UPDATE = "android.settings.SYSTEM_UPDATE_SETTINGS";
    public AbstractSystemUpdateHelper(Instrumentation instr) {
        super(instr);
    }

    /**
     * As System Update is a subcomponent of Settings, it will not appear in the launcher.
     * It needs to be opened directly via its activity.
     */
    @Override
    public void open() {
        Intent startIntent = new Intent(SYSTEM_UPDATE);
        startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mInstrumentation.getContext().startActivity(
                startIntent);
        try {
            // wait for app to open
            Thread.sleep(7000);
        } catch (InterruptedException ignored) {
            // do nothing
        }
    }

    /**
     * Check whether or not an update is available
     * @return true if the device has an update available, false otherwise.
     */
    public abstract boolean isUpdateAvailable();

    /**
     * If an update is available, download it. Otherwise, throw {@link IllegalStateException}.
     * Precondition: The device is on the System Update screen.
     * Postcondition: A system update will be ready to install.
     * @return true if the download succeeded
     */
    public abstract boolean downloadUpdate();

    /**
     * Click on an existing OTA notification.
     * Precondition: The notification drawer is open and an OTA notification exists.
     * Postcondition: The device is on the System Update screen.
     */
    public abstract void clickOtaNotification();

    /**
     * Check whether or not an OTA notification is present
     * @return true if an OTA notification is in the notification drawer, false otherwise.
     */
    public abstract boolean hasOtaNotification();

    /**
     * Check whether or not an attempted OTA download is completed
     * @return true if an OTA is ready to install, false otherwise
     */
    public abstract boolean isOtaDownloadCompleted();

    /**
     * Install an OTA. This will cause the device to power off.
     * Precondition: A system update is ready to install.
     * Postcondition: The device will reboot.
     * @return true if the "Install" button was successfully clicked, false otherwise
     */
    public abstract boolean installOta();
}
