/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.support.test.aupt;

import android.app.Instrumentation;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiWatcher;
import android.support.test.uiautomator.Until;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UiWatchers {
    private static final String LOG_TAG = UiWatchers.class.getSimpleName();
    private final List<String> mErrors = new ArrayList<String>();

    /**
     * We can use the UiDevice registerWatcher to register a small script to be executed when the
     * framework is waiting for a control to appear. Waiting may be the cause of an unexpected
     * dialog on the screen and it is the time when the framework runs the registered watchers.
     * This is a sample watcher looking for ANR and crashes. it closes it and moves on. You should
     * create your own watchers and handle error logging properly for your type of tests.
     */
    public void registerAnrAndCrashWatchers(Instrumentation instr) {
        final UiDevice device = UiDevice.getInstance(instr);

        // class names may have changed
        device.registerWatcher("AnrWatcher", new UiWatcher() {
            @Override
            public boolean checkForCondition() {
                UiObject2 window = device.findObject(
                        By.pkg("android").textContains("isn't responding"));
                if (window != null) {
                    String errorText = window.getText();
                    onAnrDetected(errorText);
                    postHandler(device);
                    return true; // triggered
                }
                return false; // no trigger
            }
        });

        device.registerWatcher("CrashWatcher", new UiWatcher() {
            @Override
            public boolean checkForCondition() {
                UiObject2 window = device.findObject(
                        By.pkg("android").textContains("has stopped"));
                if (window != null) {
                    String errorText = window.getText();
                    onCrashDetected(errorText);
                    postHandler(device);
                    return true; // triggered
                }
                return false; // no trigger
            }
        });

        Log.i(LOG_TAG, "Registed GUI Exception watchers");
    }

    public void removeAnrAndCrashWatchers(Instrumentation instr) {
        final UiDevice device = UiDevice.getInstance(instr);
        device.removeWatcher("AnrWatcher");
        device.removeWatcher("CrashWatcher");
    }

    public void onAnrDetected(String errorText) {
        mErrors.add(errorText);
    }

    public void onCrashDetected(String errorText) {
        mErrors.add(errorText);
    }

    public void reset() {
        mErrors.clear();
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(mErrors);
    }

    /**
     * Current implementation ignores the exception and continues.
     */
    public void postHandler(UiDevice device) {
        // TODO: Add custom error logging here

        String formatedOutput = String.format("UI Exception Message: %-20s\n",
                device.getCurrentPackageName());
        Log.e(LOG_TAG, formatedOutput);

        UiObject2 buttonMute = device.findObject(By.res("android", "aerr_mute"));
        if (buttonMute != null) {
            buttonMute.click();
        }

        UiObject2 closeAppButton = device.findObject(By.res("android", "aerr_close"));
        if (closeAppButton != null) {
            closeAppButton.click();
        }

        // sometimes it takes a while for the OK button to become enabled
        UiObject2 buttonOK = device.findObject(By.text("OK").enabled(true));
        if (buttonOK != null) {
            buttonOK.click();
        }
    }
}
