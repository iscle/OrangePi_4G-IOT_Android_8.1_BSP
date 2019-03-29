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

package android.view.accessibility.cts;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility methods for enabling and disabling the services used in this package
 */
public class ServiceControlUtils {
    private static final int TIMEOUT_FOR_SERVICE_ENABLE = 10000; // millis; 10s

    private static final String SETTING_ENABLE_SPEAKING_AND_VIBRATING_SERVICES =
            "android.view.accessibility.cts/.SpeakingAccessibilityService:"
            + "android.view.accessibility.cts/.VibratingAccessibilityService";

    private static final String SETTING_ENABLE_MULTIPLE_FEEDBACK_TYPES_SERVICE =
            "android.view.accessibility.cts/.SpeakingAndVibratingAccessibilityService";

    /**
     * Enable {@code SpeakingAccessibilityService} and {@code VibratingAccessibilityService}
     *
     * @param instrumentation A valid instrumentation
     */
    public static void enableSpeakingAndVibratingServices(Instrumentation instrumentation)
            throws IOException {
        Context context = instrumentation.getContext();

        // Get permission to enable accessibility
        UiAutomation uiAutomation = instrumentation.getUiAutomation();

        // Change the settings to enable the two services
        ContentResolver cr = context.getContentResolver();
        String alreadyEnabledServices = Settings.Secure.getString(
                cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ParcelFileDescriptor fd = uiAutomation.executeShellCommand("settings --user cur put secure "
                + Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES + " "
                + alreadyEnabledServices + ":"
                + SETTING_ENABLE_SPEAKING_AND_VIBRATING_SERVICES);
        InputStream in = new FileInputStream(fd.getFileDescriptor());
        byte[] buffer = new byte[4096];
        while (in.read(buffer) > 0);
        uiAutomation.destroy();

        // Wait for speaking service to be connected
        long timeoutTimeMillis = SystemClock.uptimeMillis() + TIMEOUT_FOR_SERVICE_ENABLE;
        boolean speakingServiceStarted = false;
        while (!speakingServiceStarted && (SystemClock.uptimeMillis() < timeoutTimeMillis)) {
            synchronized (SpeakingAccessibilityService.sWaitObjectForConnecting) {
                if (SpeakingAccessibilityService.sConnectedInstance != null) {
                    speakingServiceStarted = true;
                    break;
                }
                if (!speakingServiceStarted) {
                    try {
                        SpeakingAccessibilityService.sWaitObjectForConnecting.wait(
                                timeoutTimeMillis - SystemClock.uptimeMillis());
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
        if (!speakingServiceStarted) {
            throw new RuntimeException("Speaking accessibility service not starting");
        }

        // Wait for vibrating service to be connected
        while (SystemClock.uptimeMillis() < timeoutTimeMillis) {
            synchronized (VibratingAccessibilityService.sWaitObjectForConnecting) {
                if (VibratingAccessibilityService.sConnectedInstance != null) {
                    return;
                }

                try {
                    VibratingAccessibilityService.sWaitObjectForConnecting.wait(
                            timeoutTimeMillis - SystemClock.uptimeMillis());
                } catch (InterruptedException e) {
                }
            }
        }
        throw new RuntimeException("Vibrating accessibility service not starting");
    }

    /**
     * Enable {@link SpeakingAndVibratingAccessibilityService} for tests requiring a service with
     * multiple feedback types
     *
     * @param instrumentation A valid instrumentation
     */
    public static void enableMultipleFeedbackTypesService(Instrumentation instrumentation)
            throws IOException {
        Context context = instrumentation.getContext();

        // Get permission to enable accessibility
        UiAutomation uiAutomation = instrumentation.getUiAutomation();

        // Change the settings to enable the services
        ContentResolver cr = context.getContentResolver();
        String alreadyEnabledServices = Settings.Secure.getString(
                cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ParcelFileDescriptor fd = uiAutomation.executeShellCommand("settings --user cur put secure "
                + Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES + " "
                + alreadyEnabledServices + ":"
                + SETTING_ENABLE_MULTIPLE_FEEDBACK_TYPES_SERVICE);
        InputStream in = new FileInputStream(fd.getFileDescriptor());
        byte[] buffer = new byte[4096];
        while (in.read(buffer) > 0);
        uiAutomation.destroy();

        // Wait for the service to be connected
        long timeoutTimeMillis = SystemClock.uptimeMillis() + TIMEOUT_FOR_SERVICE_ENABLE;
        boolean multipleFeedbackTypesServiceEnabled = false;
        while (!multipleFeedbackTypesServiceEnabled && (SystemClock.uptimeMillis()
                < timeoutTimeMillis)) {
            synchronized (SpeakingAndVibratingAccessibilityService.sWaitObjectForConnecting) {
                if (SpeakingAndVibratingAccessibilityService.sConnectedInstance != null) {
                    multipleFeedbackTypesServiceEnabled = true;
                    break;
                }
                if (!multipleFeedbackTypesServiceEnabled) {
                    try {
                        SpeakingAndVibratingAccessibilityService.sWaitObjectForConnecting.wait(
                                timeoutTimeMillis - SystemClock.uptimeMillis());
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
        if (!multipleFeedbackTypesServiceEnabled) {
            throw new RuntimeException(
                    "Multiple feedback types accessibility service not starting");
        }
    }

    /**
     * Turn off all accessibility services. Assumes permissions to write settings are already
     * set, which they are in
     * {@link ServiceControlUtils#enableSpeakingAndVibratingServices(Instrumentation)}.
     *
     * @param instrumentation A valid instrumentation
     */
    public static void turnAccessibilityOff(Instrumentation instrumentation) {
        if (SpeakingAccessibilityService.sConnectedInstance != null) {
            SpeakingAccessibilityService.sConnectedInstance.disableSelf();
            SpeakingAccessibilityService.sConnectedInstance = null;
        }
        if (VibratingAccessibilityService.sConnectedInstance != null) {
            VibratingAccessibilityService.sConnectedInstance.disableSelf();
            VibratingAccessibilityService.sConnectedInstance = null;
        }
        if (SpeakingAndVibratingAccessibilityService.sConnectedInstance != null) {
            SpeakingAndVibratingAccessibilityService.sConnectedInstance.disableSelf();
            SpeakingAndVibratingAccessibilityService.sConnectedInstance = null;
        }

        final Object waitLockForA11yOff = new Object();
        AccessibilityManager manager = (AccessibilityManager) instrumentation
                .getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        manager.addAccessibilityStateChangeListener(
                new AccessibilityManager.AccessibilityStateChangeListener() {
                    @Override
                    public void onAccessibilityStateChanged(boolean b) {
                        synchronized (waitLockForA11yOff) {
                            waitLockForA11yOff.notifyAll();
                        }
                    }
                });
        long timeoutTimeMillis = SystemClock.uptimeMillis() + TIMEOUT_FOR_SERVICE_ENABLE;
        while (SystemClock.uptimeMillis() < timeoutTimeMillis) {
            synchronized (waitLockForA11yOff) {
                if (!manager.isEnabled()) {
                    return;
                }
                try {
                    waitLockForA11yOff.wait(timeoutTimeMillis - SystemClock.uptimeMillis());
                } catch (InterruptedException e) {
                    // Ignored; loop again
                }
            }
        }
        throw new RuntimeException("Unable to turn accessibility off");
    }
}
