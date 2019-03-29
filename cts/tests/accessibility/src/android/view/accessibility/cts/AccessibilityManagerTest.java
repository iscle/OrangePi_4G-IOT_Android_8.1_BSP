/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Service;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.test.InstrumentationTestCase;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener;
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener;

import com.android.compatibility.common.util.PollingCheck;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class for testing {@link AccessibilityManager}.
 */
public class AccessibilityManagerTest extends InstrumentationTestCase {

    private static final String SPEAKING_ACCESSIBLITY_SERVICE_NAME =
        "android.view.accessibility.cts.SpeakingAccessibilityService";

    private static final String VIBRATING_ACCESSIBLITY_SERVICE_NAME =
        "android.view.accessibility.cts.VibratingAccessibilityService";

    private static final String MULTIPLE_FEEDBACK_TYPES_ACCESSIBILITY_SERVICE_NAME =
        "android.view.accessibility.cts.SpeakingAndVibratingAccessibilityService";

    private static final long WAIT_FOR_ACCESSIBILITY_ENABLED_TIMEOUT = 3000; // 3s

    private AccessibilityManager mAccessibilityManager;

    private Context mTargetContext;

    private Handler mHandler;

    @Override
    public void setUp() throws Exception {
        mAccessibilityManager = (AccessibilityManager)
                getInstrumentation().getContext().getSystemService(Service.ACCESSIBILITY_SERVICE);
        mTargetContext = getInstrumentation().getTargetContext();
        mHandler = new Handler(mTargetContext.getMainLooper());
    }

    @Override
    public void tearDown() throws Exception {
        ServiceControlUtils.turnAccessibilityOff(getInstrumentation());
    }

    public void testAddAndRemoveAccessibilityStateChangeListener() throws Exception {
        AccessibilityStateChangeListener listener = (state) -> {
                /* do nothing */
        };
        assertTrue(mAccessibilityManager.addAccessibilityStateChangeListener(listener));
        assertTrue(mAccessibilityManager.removeAccessibilityStateChangeListener(listener));
        assertFalse(mAccessibilityManager.removeAccessibilityStateChangeListener(listener));
    }

    public void testAddAndRemoveTouchExplorationStateChangeListener() throws Exception {
        TouchExplorationStateChangeListener listener = (boolean enabled) -> {
            // Do nothing.
        };
        assertTrue(mAccessibilityManager.addTouchExplorationStateChangeListener(listener));
        assertTrue(mAccessibilityManager.removeTouchExplorationStateChangeListener(listener));
        assertFalse(mAccessibilityManager.removeTouchExplorationStateChangeListener(listener));
    }

    public void testIsTouchExplorationEnabled() throws Exception {
        ServiceControlUtils.enableSpeakingAndVibratingServices(getInstrumentation());
        new PollingCheck() {
            @Override
            protected boolean check() {
                return mAccessibilityManager.isTouchExplorationEnabled();
            }
        }.run();
    }

    public void testGetInstalledAccessibilityServicesList() throws Exception {
        List<AccessibilityServiceInfo> installedServices =
            mAccessibilityManager.getInstalledAccessibilityServiceList();
        assertFalse("There must be at least one installed service.", installedServices.isEmpty());
        boolean speakingServiceInstalled = false;
        boolean vibratingServiceInstalled = false;
        final int serviceCount = installedServices.size();
        for (int i = 0; i < serviceCount; i++) {
            AccessibilityServiceInfo installedService = installedServices.get(i);
            ServiceInfo serviceInfo = installedService.getResolveInfo().serviceInfo;
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && SPEAKING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                speakingServiceInstalled = true;
            }
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && VIBRATING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                vibratingServiceInstalled = true;
            }
        }
        assertTrue("The speaking service should be installed.", speakingServiceInstalled);
        assertTrue("The vibrating service should be installed.", vibratingServiceInstalled);
    }

    public void testGetEnabledAccessibilityServiceList() throws Exception {
        ServiceControlUtils.enableSpeakingAndVibratingServices(getInstrumentation());
        List<AccessibilityServiceInfo> enabledServices =
            mAccessibilityManager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        boolean speakingServiceEnabled = false;
        boolean vibratingServiceEnabled = false;
        final int serviceCount = enabledServices.size();
        for (int i = 0; i < serviceCount; i++) {
            AccessibilityServiceInfo enabledService = enabledServices.get(i);
            ServiceInfo serviceInfo = enabledService.getResolveInfo().serviceInfo;
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && SPEAKING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                speakingServiceEnabled = true;
            }
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && VIBRATING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                vibratingServiceEnabled = true;
            }
        }
        assertTrue("The speaking service should be enabled.", speakingServiceEnabled);
        assertTrue("The vibrating service should be enabled.", vibratingServiceEnabled);
    }

    public void testGetEnabledAccessibilityServiceListForType() throws Exception {
        ServiceControlUtils.enableSpeakingAndVibratingServices(getInstrumentation());
        List<AccessibilityServiceInfo> enabledServices =
            mAccessibilityManager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_SPOKEN);
        assertSame("There should be only one enabled speaking service.", 1, enabledServices.size());
        final int serviceCount = enabledServices.size();
        for (int i = 0; i < serviceCount; i++) {
            AccessibilityServiceInfo enabledService = enabledServices.get(i);
            ServiceInfo serviceInfo = enabledService.getResolveInfo().serviceInfo;
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && SPEAKING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                return;
            }
        }
        fail("The speaking service is not enabled.");
    }

    public void testGetEnabledAccessibilityServiceListForTypes() throws Exception {
        ServiceControlUtils.enableSpeakingAndVibratingServices(getInstrumentation());
        // For this test, also enable a service with multiple feedback types
        ServiceControlUtils.enableMultipleFeedbackTypesService(getInstrumentation());

        List<AccessibilityServiceInfo> enabledServices =
                mAccessibilityManager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_SPOKEN
                                | AccessibilityServiceInfo.FEEDBACK_HAPTIC);
        assertSame("There should be 3 enabled accessibility services.", 3, enabledServices.size());
        boolean speakingServiceEnabled = false;
        boolean vibratingServiceEnabled = false;
        boolean multipleFeedbackTypesServiceEnabled = false;
        final int serviceCount = enabledServices.size();
        for (int i = 0; i < serviceCount; i++) {
            AccessibilityServiceInfo enabledService = enabledServices.get(i);
            ServiceInfo serviceInfo = enabledService.getResolveInfo().serviceInfo;
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && SPEAKING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                speakingServiceEnabled = true;
            }
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && VIBRATING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                vibratingServiceEnabled = true;
            }
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && MULTIPLE_FEEDBACK_TYPES_ACCESSIBILITY_SERVICE_NAME.equals(
                    serviceInfo.name)) {
                multipleFeedbackTypesServiceEnabled = true;
            }
        }
        assertTrue("The speaking service should be enabled.", speakingServiceEnabled);
        assertTrue("The vibrating service should be enabled.", vibratingServiceEnabled);
        assertTrue("The multiple feedback types service should be enabled.",
                multipleFeedbackTypesServiceEnabled);
    }

    @SuppressWarnings("deprecation")
    public void testGetAccessibilityServiceList() throws Exception {
        List<ServiceInfo> services = mAccessibilityManager.getAccessibilityServiceList();
        boolean speakingServiceInstalled = false;
        boolean vibratingServiceInstalled = false;
        final int serviceCount = services.size();
        for (int i = 0; i < serviceCount; i++) {
            ServiceInfo serviceInfo = services.get(i);
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && SPEAKING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                speakingServiceInstalled = true;
            }
            if (mTargetContext.getPackageName().equals(serviceInfo.packageName)
                    && VIBRATING_ACCESSIBLITY_SERVICE_NAME.equals(serviceInfo.name)) {
                vibratingServiceInstalled = true;
            }
        }
        assertTrue("The speaking service should be installed.", speakingServiceInstalled);
        assertTrue("The vibrating service should be installed.", vibratingServiceInstalled);
    }

    public void testInterrupt() throws Exception {
        // The APIs are heavily tested in the android.accessibilityservice package.
        // This just makes sure the call does not throw an exception.
        ServiceControlUtils.enableSpeakingAndVibratingServices(getInstrumentation());
        waitForAccessibilityEnabled();
        mAccessibilityManager.interrupt();
    }

    public void testSendAccessibilityEvent() throws Exception {
        // The APIs are heavily tested in the android.accessibilityservice package.
        // This just makes sure the call does not throw an exception.
        ServiceControlUtils.enableSpeakingAndVibratingServices(getInstrumentation());
        waitForAccessibilityEnabled();
        mAccessibilityManager.sendAccessibilityEvent(AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_VIEW_CLICKED));
    }

    public void testTouchExplorationListenerNoHandler() throws Exception {
        final Object waitObject = new Object();
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);

        TouchExplorationStateChangeListener listener = (boolean b) -> {
            synchronized (waitObject) {
                atomicBoolean.set(b);
                waitObject.notifyAll();
            }
        };
        mAccessibilityManager.addTouchExplorationStateChangeListener(listener);
        ServiceControlUtils.enableSpeakingAndVibratingServices(getInstrumentation());
        assertAtomicBooleanBecomes(atomicBoolean, true, waitObject,
                "Touch exploration state listener not called when services enabled");
        assertTrue("Listener told that touch exploration is enabled, but manager says disabled",
                mAccessibilityManager.isTouchExplorationEnabled());
        ServiceControlUtils.turnAccessibilityOff(getInstrumentation());
        assertAtomicBooleanBecomes(atomicBoolean, false, waitObject,
                "Touch exploration state listener not called when services disabled");
        assertFalse("Listener told that touch exploration is disabled, but manager says it enabled",
                mAccessibilityManager.isTouchExplorationEnabled());
        mAccessibilityManager.removeTouchExplorationStateChangeListener(listener);
    }

    public void testTouchExplorationListenerWithHandler() throws Exception {
        final Object waitObject = new Object();
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);

        TouchExplorationStateChangeListener listener = (boolean b) -> {
            synchronized (waitObject) {
                atomicBoolean.set(b);
                waitObject.notifyAll();
            }
        };
        mAccessibilityManager.addTouchExplorationStateChangeListener(listener, mHandler);
        ServiceControlUtils.enableSpeakingAndVibratingServices(getInstrumentation());
        assertAtomicBooleanBecomes(atomicBoolean, true, waitObject,
                "Touch exploration state listener not called when services enabled");
        assertTrue("Listener told that touch exploration is enabled, but manager says disabled",
                mAccessibilityManager.isTouchExplorationEnabled());
        ServiceControlUtils.turnAccessibilityOff(getInstrumentation());
        assertAtomicBooleanBecomes(atomicBoolean, false, waitObject,
                "Touch exploration state listener not called when services disabled");
        assertFalse("Listener told that touch exploration is disabled, but manager says it enabled",
                mAccessibilityManager.isTouchExplorationEnabled());
        mAccessibilityManager.removeTouchExplorationStateChangeListener(listener);
    }

    public void testAccessibilityStateListenerNoHandler() throws Exception {
        final Object waitObject = new Object();
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);

        AccessibilityStateChangeListener listener = (boolean b) -> {
            synchronized (waitObject) {
                atomicBoolean.set(b);
                waitObject.notifyAll();
            }
        };
        mAccessibilityManager.addAccessibilityStateChangeListener(listener);
        ServiceControlUtils.enableMultipleFeedbackTypesService(getInstrumentation());
        assertAtomicBooleanBecomes(atomicBoolean, true, waitObject,
                "Accessibility state listener not called when services enabled");
        assertTrue("Listener told that accessibility is enabled, but manager says disabled",
                mAccessibilityManager.isEnabled());
        ServiceControlUtils.turnAccessibilityOff(getInstrumentation());
        assertAtomicBooleanBecomes(atomicBoolean, false, waitObject,
                "Accessibility state listener not called when services disabled");
        assertFalse("Listener told that accessibility is disabled, but manager says enabled",
                mAccessibilityManager.isEnabled());
        mAccessibilityManager.removeAccessibilityStateChangeListener(listener);
    }

    public void testAccessibilityStateListenerWithHandler() throws Exception {
        final Object waitObject = new Object();
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);

        AccessibilityStateChangeListener listener = (boolean b) -> {
            synchronized (waitObject) {
                atomicBoolean.set(b);
                waitObject.notifyAll();
            }
        };
        mAccessibilityManager.addAccessibilityStateChangeListener(listener, mHandler);
        ServiceControlUtils.enableMultipleFeedbackTypesService(getInstrumentation());
        assertAtomicBooleanBecomes(atomicBoolean, true, waitObject,
                "Accessibility state listener not called when services enabled");
        assertTrue("Listener told that accessibility is enabled, but manager says disabled",
                mAccessibilityManager.isEnabled());
        ServiceControlUtils.turnAccessibilityOff(getInstrumentation());
        assertAtomicBooleanBecomes(atomicBoolean, false, waitObject,
                "Accessibility state listener not called when services disabled");
        assertFalse("Listener told that accessibility is disabled, but manager says enabled",
                mAccessibilityManager.isEnabled());
        mAccessibilityManager.removeAccessibilityStateChangeListener(listener);
    }

    private void assertAtomicBooleanBecomes(AtomicBoolean atomicBoolean,
            boolean expectedValue, Object waitObject, String message)
            throws Exception {
        long timeoutTime = System.currentTimeMillis() + WAIT_FOR_ACCESSIBILITY_ENABLED_TIMEOUT;
        synchronized (waitObject) {
            while ((atomicBoolean.get() != expectedValue)
                    && (System.currentTimeMillis() < timeoutTime)) {
                waitObject.wait(timeoutTime - System.currentTimeMillis());
            }
        }
        assertTrue(message, atomicBoolean.get() == expectedValue);
    }

    private void waitForAccessibilityEnabled() throws InterruptedException {
        final Object waitObject = new Object();

        AccessibilityStateChangeListener listener = (boolean b) -> {
            synchronized (waitObject) {
                waitObject.notifyAll();
            }
        };
        mAccessibilityManager.addAccessibilityStateChangeListener(listener);
        long timeoutTime = System.currentTimeMillis() + WAIT_FOR_ACCESSIBILITY_ENABLED_TIMEOUT;
        synchronized (waitObject) {
            while (!mAccessibilityManager.isEnabled()
                    && (System.currentTimeMillis() < timeoutTime)) {
                waitObject.wait(timeoutTime - System.currentTimeMillis());
            }
        }
        mAccessibilityManager.removeAccessibilityStateChangeListener(listener);
        assertTrue("Timed out enabling accessibility", mAccessibilityManager.isEnabled());
    }
}
