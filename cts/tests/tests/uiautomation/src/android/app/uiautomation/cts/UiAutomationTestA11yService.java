/**
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app.uiautomation.cts;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * A stub accessibility service to install for testing UiAutomation's effect on accessibility
 * services
 */
public class UiAutomationTestA11yService extends AccessibilityService {
    public static Object sWaitObjectForConnecting = new Object();

    public static UiAutomationTestA11yService sConnectedInstance;

    @Override
    public void onDestroy() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    protected void onServiceConnected() {
        synchronized (sWaitObjectForConnecting) {
            sConnectedInstance = this;
            sWaitObjectForConnecting.notifyAll();
        }
    }

    public boolean isConnected() {
        try {
            if (getRootInActiveWindow() == null) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
