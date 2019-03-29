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

package com.android.cts.usepermission;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.util.ArrayMap;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.ScrollView;
import android.widget.Switch;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public abstract class BasePermissionsTest {
    private static final String PLATFORM_PACKAGE_NAME = "android";

    private static final long IDLE_TIMEOUT_MILLIS = 500;
    private static final long GLOBAL_TIMEOUT_MILLIS = 5000;

    private static final long RETRY_TIMEOUT = 3 * GLOBAL_TIMEOUT_MILLIS;
    private static final String LOG_TAG = "BasePermissionsTest";

    private static Map<String, String> sPermissionToLabelResNameMap = new ArrayMap<>();
    static {
        // Contacts
        sPermissionToLabelResNameMap.put(Manifest.permission.READ_CONTACTS,
                "@android:string/permgrouplab_contacts");
        sPermissionToLabelResNameMap.put(Manifest.permission.WRITE_CONTACTS,
                "@android:string/permgrouplab_contacts");
        // Calendar
        sPermissionToLabelResNameMap.put(Manifest.permission.READ_CALENDAR,
                "@android:string/permgrouplab_calendar");
        sPermissionToLabelResNameMap.put(Manifest.permission.WRITE_CALENDAR,
                "@android:string/permgrouplab_calendar");
        // SMS
        sPermissionToLabelResNameMap.put(Manifest.permission.SEND_SMS,
                "@android:string/permgrouplab_sms");
        sPermissionToLabelResNameMap.put(Manifest.permission.RECEIVE_SMS,
                "@android:string/permgrouplab_sms");
        sPermissionToLabelResNameMap.put(Manifest.permission.READ_SMS,
                "@android:string/permgrouplab_sms");
        sPermissionToLabelResNameMap.put(Manifest.permission.RECEIVE_WAP_PUSH,
                "@android:string/permgrouplab_sms");
        sPermissionToLabelResNameMap.put(Manifest.permission.RECEIVE_MMS,
                "@android:string/permgrouplab_sms");
        sPermissionToLabelResNameMap.put("android.permission.READ_CELL_BROADCASTS",
                "@android:string/permgrouplab_sms");
        // Storage
        sPermissionToLabelResNameMap.put(Manifest.permission.READ_EXTERNAL_STORAGE,
                "@android:string/permgrouplab_storage");
        sPermissionToLabelResNameMap.put(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                "@android:string/permgrouplab_storage");
        // Location
        sPermissionToLabelResNameMap.put(Manifest.permission.ACCESS_FINE_LOCATION,
                "@android:string/permgrouplab_location");
        sPermissionToLabelResNameMap.put(Manifest.permission.ACCESS_COARSE_LOCATION,
                "@android:string/permgrouplab_location");
        // Phone
        sPermissionToLabelResNameMap.put(Manifest.permission.READ_PHONE_STATE,
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put(Manifest.permission.CALL_PHONE,
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put("android.permission.ACCESS_IMS_CALL_SERVICE",
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put(Manifest.permission.READ_CALL_LOG,
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put(Manifest.permission.WRITE_CALL_LOG,
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put(Manifest.permission.ADD_VOICEMAIL,
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put(Manifest.permission.USE_SIP,
                "@android:string/permgrouplab_phone");
        sPermissionToLabelResNameMap.put(Manifest.permission.PROCESS_OUTGOING_CALLS,
                "@android:string/permgrouplab_phone");
        // Microphone
        sPermissionToLabelResNameMap.put(Manifest.permission.RECORD_AUDIO,
                "@android:string/permgrouplab_microphone");
        // Camera
        sPermissionToLabelResNameMap.put(Manifest.permission.CAMERA,
                "@android:string/permgrouplab_camera");
        // Body sensors
        sPermissionToLabelResNameMap.put(Manifest.permission.BODY_SENSORS,
                "@android:string/permgrouplab_sensors");
    }

    private Context mContext;
    private Resources mPlatformResources;
    private boolean mWatch;

    protected static Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    protected static void assertPermissionRequestResult(BasePermissionActivity.Result result,
            int requestCode, String[] permissions, boolean[] granted) {
        assertEquals(requestCode, result.requestCode);
        for (int i = 0; i < permissions.length; i++) {
            assertEquals(permissions[i], result.permissions[i]);
            assertEquals(granted[i] ? PackageManager.PERMISSION_GRANTED
                    : PackageManager.PERMISSION_DENIED, result.grantResults[i]);

        }
    }

    protected static UiDevice getUiDevice() {
        return UiDevice.getInstance(getInstrumentation());
    }

    protected static Activity launchActivity(String packageName,
            Class<?> clazz, Bundle extras) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(packageName, clazz.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (extras != null) {
            intent.putExtras(extras);
        }
        Activity activity = getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();

        return activity;
    }

    @Before
    public void beforeTest() {
        mContext = InstrumentationRegistry.getTargetContext();
        try {
            Context platformContext = mContext.createPackageContext(PLATFORM_PACKAGE_NAME, 0);
            mPlatformResources = platformContext.getResources();
        } catch (PackageManager.NameNotFoundException e) {
            /* cannot happen */
        }

        mWatch = mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);

        UiObject2 button = getUiDevice().findObject(By.text("Close"));
        if (button != null) {
            button.click();
        }
    }

    protected BasePermissionActivity.Result requestPermissions(
            String[] permissions, int requestCode, Class<?> clazz, Runnable postRequestAction)
            throws Exception {
        // Start an activity
        BasePermissionActivity activity = (BasePermissionActivity) launchActivity(
                getInstrumentation().getTargetContext().getPackageName(), clazz, null);

        activity.waitForOnCreate();

        // Request the permissions
        activity.requestPermissions(permissions, requestCode);

        // Define a more conservative idle criteria
        getInstrumentation().getUiAutomation().waitForIdle(
                IDLE_TIMEOUT_MILLIS, GLOBAL_TIMEOUT_MILLIS);

        // Perform the post-request action
        if (postRequestAction != null) {
            postRequestAction.run();
        }

        BasePermissionActivity.Result result = activity.getResult();
        activity.finish();
        return result;
    }

    protected void clickAllowButton() throws Exception {
        scrollToBottomIfWatch();
        getUiDevice().findObject(new UiSelector().resourceId(
                "com.android.packageinstaller:id/permission_allow_button")).click();
    }

    protected void clickDenyButton() throws Exception {
        scrollToBottomIfWatch();
        getUiDevice().findObject(new UiSelector().resourceId(
                "com.android.packageinstaller:id/permission_deny_button")).click();
    }

    protected void clickDontAskAgainCheckbox() throws Exception {
        getUiDevice().findObject(new UiSelector().resourceId(
                "com.android.packageinstaller:id/do_not_ask_checkbox")).click();
    }

    protected void clickDontAskAgainButton() throws Exception {
        scrollToBottomIfWatch();
        getUiDevice().findObject(new UiSelector().resourceId(
                "com.android.packageinstaller:id/permission_deny_dont_ask_again_button")).click();
    }

    protected void grantPermission(String permission) throws Exception {
        grantPermissions(new String[]{permission});
    }

    protected void grantPermissions(String[] permissions) throws Exception {
        setPermissionGrantState(permissions, true, false);
    }

    protected void revokePermission(String permission) throws Exception {
        revokePermissions(new String[] {permission}, false);
    }

    protected void revokePermissions(String[] permissions, boolean legacyApp) throws Exception {
        setPermissionGrantState(permissions, false, legacyApp);
    }

    private void scrollToBottomIfWatch() throws Exception {
        if (mWatch) {
            UiScrollable scrollable =
                    new UiScrollable(new UiSelector().className(ScrollView.class));
            if (scrollable.exists()) {
                scrollable.flingToEnd(10);
            }
        }
    }

    private void setPermissionGrantState(String[] permissions, boolean granted,
            boolean legacyApp) throws Exception {
        getUiDevice().pressBack();
        waitForIdle();
        getUiDevice().pressBack();
        waitForIdle();
        getUiDevice().pressBack();
        waitForIdle();

        if (isTv()) {
            getUiDevice().pressHome();
            waitForIdle();
        }

        // Open the app details settings
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.parse("package:" + mContext.getPackageName()));
        startActivity(intent);

        waitForIdle();

        // Open the permissions UI
        String label = mContext.getResources().getString(R.string.Permissions);
        AccessibilityNodeInfo permLabelView = getNodeTimed(() -> findByText(label), true);
        Assert.assertNotNull("Permissions label should be present", permLabelView);

        AccessibilityNodeInfo permItemView = findCollectionItem(permLabelView);
        Assert.assertNotNull("Permissions item should be present", permItemView);

        click(permItemView);

        waitForIdle();

        for (String permission : permissions) {
            // Find the permission toggle
            String permissionLabel = getPermissionLabel(permission);

            AccessibilityNodeInfo labelView = getNodeTimed(() -> findByText(permissionLabel), true);
            Assert.assertNotNull("Permission label should be present", labelView);

            AccessibilityNodeInfo itemView = findCollectionItem(labelView);
            Assert.assertNotNull("Permission item should be present", itemView);

            final AccessibilityNodeInfo toggleView = findSwitch(itemView);
            Assert.assertNotNull("Permission toggle should be present", toggleView);

            final boolean wasGranted = toggleView.isChecked();
            if (granted != wasGranted) {
                // Toggle the permission

                if (!itemView.getActionList().contains(AccessibilityAction.ACTION_CLICK)) {
                    click(toggleView);
                } else {
                    click(itemView);
                }

                waitForIdle();

                if (wasGranted && legacyApp) {
                    scrollToBottomIfWatch();
                    String packageName = getInstrumentation().getContext().getPackageManager()
                            .getPermissionControllerPackageName();
                    String resIdName = "com.android.packageinstaller"
                            + ":string/grant_dialog_button_deny_anyway";
                    Resources resources = getInstrumentation().getContext()
                            .createPackageContext(packageName, 0).getResources();
                    final int confirmResId = resources.getIdentifier(resIdName, null, null);
                    String confirmTitle = resources.getString(confirmResId);
                    UiObject denyAnyway = getUiDevice().findObject(new UiSelector()
                            .textStartsWith(confirmTitle));
                    denyAnyway.click();

                    waitForIdle();
                }
            }
        }

        getUiDevice().pressBack();
        waitForIdle();
        getUiDevice().pressBack();
        waitForIdle();
    }

    private String getPermissionLabel(String permission) throws Exception {
        String labelResName = sPermissionToLabelResNameMap.get(permission);
        assertNotNull("Unknown permisison " + permission, labelResName);
        final int resourceId = mPlatformResources.getIdentifier(labelResName, null, null);
        return mPlatformResources.getString(resourceId);
    }

    private void startActivity(final Intent intent) throws Exception {
        getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                () -> {
            try {
                getInstrumentation().getContext().startActivity(intent);
            } catch (Exception e) {
                fail("Cannot start activity: " + intent);
            }
        }, (AccessibilityEvent event) -> event.getEventType()
                        == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
         , GLOBAL_TIMEOUT_MILLIS);
    }

    private AccessibilityNodeInfo findByText(String text) throws Exception {
        AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
        AccessibilityNodeInfo result = findByText(root, text);
        if (result != null) {
            return result;
        }
        return findByTextInCollection(root, text);
    }

    private static AccessibilityNodeInfo findByText(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        for (AccessibilityNodeInfo node : nodes) {
            if (node.getText().toString().equals(text)) {
                return node;
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo findByTextInCollection(AccessibilityNodeInfo root,
            String text)  throws Exception {
        AccessibilityNodeInfo result;
        final int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child == null) {
                continue;
            }
            if (child.getCollectionInfo() != null) {
                scrollTop(child);
                result = getNodeTimed(() -> findByText(child, text), false);
                if (result != null) {
                    return result;
                }
                try {
                    while (child.getActionList().contains(AccessibilityAction.ACTION_SCROLL_FORWARD)) {
                        scrollForward(child);
                        result = getNodeTimed(() -> findByText(child, text), false);
                        if (result != null) {
                            return result;
                        }
                    }
                } catch (TimeoutException e) {
                     /* ignore */
                }
            } else {
                result = findByTextInCollection(child, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static void scrollTop(AccessibilityNodeInfo node) throws Exception {
        try {
            while (node.getActionList().contains(AccessibilityAction.ACTION_SCROLL_BACKWARD)) {
                scroll(node, false);
            }
        } catch (TimeoutException e) {
            /* ignore */
        }
    }

    private static void scrollForward(AccessibilityNodeInfo node) throws Exception {
            scroll(node, true);
    }

    private static void scroll(AccessibilityNodeInfo node, boolean forward) throws Exception {
        getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                () -> {
                    if (isTv()) {
                        if (forward) {
                            getUiDevice().pressDPadDown();
                        } else {
                            for (int i = 0; i < 50; i++) {
                                getUiDevice().pressDPadUp();
                            }
                        }
                    } else {
                        node.performAction(forward
                                ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                                : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                    }
                },
                (AccessibilityEvent event) -> event.getEventType()
                        == AccessibilityEvent.TYPE_VIEW_SCROLLED
                        || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                GLOBAL_TIMEOUT_MILLIS);
        node.refresh();
        waitForIdle();
    }

    private static void click(AccessibilityNodeInfo node) throws Exception {
        getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                () -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                (AccessibilityEvent event) -> event.getEventType()
                        == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                        || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                GLOBAL_TIMEOUT_MILLIS);
    }

    private static AccessibilityNodeInfo findCollectionItem(AccessibilityNodeInfo current)
            throws Exception {
        AccessibilityNodeInfo result = current;
        while (result != null) {
            if (result.getCollectionItemInfo() != null) {
                return result;
            }
            result = result.getParent();
        }
        return null;
    }

    private static AccessibilityNodeInfo findSwitch(AccessibilityNodeInfo root) throws Exception {
        if (Switch.class.getName().equals(root.getClassName().toString())) {
            return root;
        }
        final int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child == null) {
                continue;
            }
            if (Switch.class.getName().equals(child.getClassName().toString())) {
                return child;
            }
            AccessibilityNodeInfo result = findSwitch(child);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo getNodeTimed(
            Callable<AccessibilityNodeInfo> callable, boolean retry) throws Exception {
        final long startTimeMillis = SystemClock.uptimeMillis();
        while (true) {
            try {
                AccessibilityNodeInfo node = callable.call();

                if (node != null) {
                    return node;
                }
            } catch (NullPointerException e) {
                Log.e(LOG_TAG, "NPE while finding AccessibilityNodeInfo", e);
            }

            final long elapsedTimeMillis = SystemClock.uptimeMillis() - startTimeMillis;
            if (!retry || elapsedTimeMillis > RETRY_TIMEOUT) {
                return null;
            }
            SystemClock.sleep(2 * elapsedTimeMillis);
        }
    }

    private static void waitForIdle() throws TimeoutException {
        getInstrumentation().getUiAutomation().waitForIdle(IDLE_TIMEOUT_MILLIS,
                GLOBAL_TIMEOUT_MILLIS);
    }

    private static boolean isTv() {
        return getInstrumentation().getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }
 }
