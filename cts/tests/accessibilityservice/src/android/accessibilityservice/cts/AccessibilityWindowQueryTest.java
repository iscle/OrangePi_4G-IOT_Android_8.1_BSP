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

package android.accessibilityservice.cts;

import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_FOCUS;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_SELECTION;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_SELECT;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.view.accessibility.AccessibilityWindowInfo;

import android.widget.Button;
import android.accessibilityservice.cts.R;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeoutException;

/**
 * Test cases for testing the accessibility APIs for querying of the screen content.
 * These APIs allow exploring the screen and requesting an action to be performed
 * on a given view from an AccessibilityService.
 */
public class AccessibilityWindowQueryTest
        extends AccessibilityActivityTestCase<AccessibilityWindowQueryActivity> {
    private static String CONTENT_VIEW_RES_NAME =
            "android.accessibilityservice.cts:id/added_content";
    private static final long TIMEOUT_WINDOW_STATE_IDLE = 500;
    private final UiAutomation.AccessibilityEventFilter mWindowsChangedFilter =
            new UiAutomation.AccessibilityEventFilter() {
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED);
                }
            };
    private final UiAutomation.AccessibilityEventFilter mDividerPresentFilter =
            new UiAutomation.AccessibilityEventFilter() {
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
                            isDividerWindowPresent(getInstrumentation().getUiAutomation())    );
                }
            };
    private final UiAutomation.AccessibilityEventFilter mDividerAbsentFilter =
            new UiAutomation.AccessibilityEventFilter() {
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
                            !isDividerWindowPresent(getInstrumentation().getUiAutomation())   );
                }
            };

    public AccessibilityWindowQueryTest() {
        super(AccessibilityWindowQueryActivity.class);
    }

    @MediumTest
    public void testFindByText() throws Throwable {
        // First, make the root view of the activity an accessibility node. This allows us to
        // later exclude views that are part of the activity's DecorView.
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                getActivity().findViewById(R.id.added_content)
                    .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            }
        });

        // Start looking from the added content instead of from the root accessibility node so
        // that nodes that we don't expect (i.e. window control buttons) are not included in the
        // list of accessibility nodes returned by findAccessibilityNodeInfosByText.
        final AccessibilityNodeInfo addedContent = getInstrumentation().getUiAutomation()
                .getRootInActiveWindow().findAccessibilityNodeInfosByViewId(CONTENT_VIEW_RES_NAME)
                        .get(0);

        // find a view by text
        List<AccessibilityNodeInfo> buttons = addedContent.findAccessibilityNodeInfosByText("b");

        assertEquals(9, buttons.size());
    }

    @MediumTest
    public void testFindByContentDescription() throws Exception {
        // find a view by text
        AccessibilityNodeInfo button = getInstrumentation().getUiAutomation()
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        getString(R.string.contentDescription)).get(0);
        assertNotNull(button);
    }

    @MediumTest
    public void testTraverseWindow() throws Exception {
        verifyNodesInAppWindow(getInstrumentation().getUiAutomation().getRootInActiveWindow());
    }

    @MediumTest
    public void testNoWindowsAccessIfFlagNotSet() throws Exception {
        // Clear window access flag
        AccessibilityServiceInfo info = getInstrumentation().getUiAutomation().getServiceInfo();
        info.flags &= ~AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        getInstrumentation().getUiAutomation().setServiceInfo(info);

        // Make sure the windows cannot be accessed.
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        assertTrue(uiAutomation.getWindows().isEmpty());

        // Find a button to click on.
        final AccessibilityNodeInfo button1 = uiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/button1").get(0);

        // Argh...
        final List<AccessibilityEvent> events = new ArrayList<AccessibilityEvent>();

        // Click the button.
        uiAutomation.executeAndWaitForEvent(new Runnable() {
            @Override
            public void run() {
                button1.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        },
        new UiAutomation.AccessibilityEventFilter() {
            @Override
            public boolean accept(AccessibilityEvent event) {
                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    events.add(event);
                    return true;
                }
                return false;
            }
        },
        TIMEOUT_ASYNC_PROCESSING);

        // Make sure the source window cannot be accessed.
        AccessibilityEvent event = events.get(0);
        assertNull(event.getSource().getWindow());
    }

    @MediumTest
    public void testTraverseAllWindows() throws Exception {
        setAccessInteractiveWindowsFlag();
        try {
            UiAutomation uiAutomation = getInstrumentation().getUiAutomation();

            List<AccessibilityWindowInfo> windows = uiAutomation.getWindows();
            Rect boundsInScreen = new Rect();

            final int windowCount = windows.size();
            for (int i = 0; i < windowCount; i++) {
                AccessibilityWindowInfo window = windows.get(i);

                window.getBoundsInScreen(boundsInScreen);
                assertFalse(boundsInScreen.isEmpty()); // Varies on screen size, emptiness check.
                assertNull(window.getParent());
                assertSame(0, window.getChildCount());
                assertNull(window.getParent());
                assertNotNull(window.getRoot());

                if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    assertTrue(window.isFocused());
                    assertTrue(window.isActive());
                    verifyNodesInAppWindow(window.getRoot());
                } else if (window.getType() == AccessibilityWindowInfo.TYPE_SYSTEM) {
                    assertFalse(window.isFocused());
                    assertFalse(window.isActive());
                }
            }
        } finally {
            clearAccessInteractiveWindowsFlag();
        }
    }

    @MediumTest
    public void testTraverseWindowFromEvent() throws Exception {
        setAccessInteractiveWindowsFlag();
        try {
            UiAutomation uiAutomation = getInstrumentation().getUiAutomation();

            // Find a button to click on.
            final AccessibilityNodeInfo button1 = uiAutomation.getRootInActiveWindow()
                    .findAccessibilityNodeInfosByViewId(
                            "android.accessibilityservice.cts:id/button1").get(0);

            // Argh...
            final List<AccessibilityEvent> events = new ArrayList<AccessibilityEvent>();

            // Click the button.
            uiAutomation.executeAndWaitForEvent(new Runnable() {
                @Override
                public void run() {
                    button1.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            },
            new UiAutomation.AccessibilityEventFilter() {
                @Override
                public boolean accept(AccessibilityEvent event) {
                    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                        events.add(event);
                        return true;
                    }
                    return false;
                }
            },
            TIMEOUT_ASYNC_PROCESSING);

            // Get the source window.
            AccessibilityEvent event = events.get(0);
            AccessibilityWindowInfo window = event.getSource().getWindow();

            // Verify the application window.
            Rect boundsInScreen = new Rect();
            window.getBoundsInScreen(boundsInScreen);
            assertFalse(boundsInScreen.isEmpty()); // Varies on screen size, so just emptiness check.
            assertSame(window.getType(), AccessibilityWindowInfo.TYPE_APPLICATION);
            assertTrue(window.isFocused());
            assertTrue(window.isActive());
            assertNull(window.getParent());
            assertSame(0, window.getChildCount());
            assertNotNull(window.getRoot());

            // Verify the window content.
            verifyNodesInAppWindow(window.getRoot());
        } finally {
            clearAccessInteractiveWindowsFlag();
        }
    }

    @MediumTest
    public void testInteractWithAppWindow() throws Exception {
        setAccessInteractiveWindowsFlag();
        try {
            UiAutomation uiAutomation = getInstrumentation().getUiAutomation();

            // Find a button to click on.
            final AccessibilityNodeInfo button1 = uiAutomation.getRootInActiveWindow()
                    .findAccessibilityNodeInfosByViewId(
                            "android.accessibilityservice.cts:id/button1").get(0);

            // Argh...
            final List<AccessibilityEvent> events = new ArrayList<AccessibilityEvent>();

            // Click the button.
            uiAutomation.executeAndWaitForEvent(new Runnable() {
                @Override
                public void run() {
                    button1.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            },
            new UiAutomation.AccessibilityEventFilter() {
                @Override
                public boolean accept(AccessibilityEvent event) {
                    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                        events.add(event);
                        return true;
                    }
                    return false;
                }
            },
            TIMEOUT_ASYNC_PROCESSING);

            // Get the source window.
            AccessibilityEvent event = events.get(0);
            AccessibilityWindowInfo window = event.getSource().getWindow();

            // Find a another button from the event's window.
            final AccessibilityNodeInfo button2 = window.getRoot()
                    .findAccessibilityNodeInfosByViewId(
                            "android.accessibilityservice.cts:id/button2").get(0);

            // Click the second button.
            uiAutomation.executeAndWaitForEvent(new Runnable() {
                @Override
                public void run() {
                    button2.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            },
            new UiAutomation.AccessibilityEventFilter() {
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED;
                }
            },
            TIMEOUT_ASYNC_PROCESSING);
        } finally {
            clearAccessInteractiveWindowsFlag();
        }
    }

    @MediumTest
    public void testSingleAccessibilityFocusAcrossWindows() throws Exception {
        setAccessInteractiveWindowsFlag();
        try {
            // Add two more windows.
            addTwoAppPanelWindows();

            // Put accessibility focus in the first app window.
            ensureAppWindowFocusedOrFail(0);
            // Make sure there only one accessibility focus.
            assertSingleAccessibilityFocus();

            // Put accessibility focus in the second app window.
            ensureAppWindowFocusedOrFail(1);
            // Make sure there only one accessibility focus.
            assertSingleAccessibilityFocus();

            // Put accessibility focus in the third app window.
            ensureAppWindowFocusedOrFail(2);
            // Make sure there only one accessibility focus.
            assertSingleAccessibilityFocus();
        } finally {
            ensureAccessibilityFocusCleared();
            clearAccessInteractiveWindowsFlag();
        }
    }

    @MediumTest
    public void testPerformActionFocus() throws Exception {
        // find a view and make sure it is not focused
        AccessibilityNodeInfo button = getInstrumentation().getUiAutomation()
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        getString(R.string.button5)).get(0);
        assertFalse(button.isFocused());

        // focus the view
        assertTrue(button.performAction(ACTION_FOCUS));

        // find the view again and make sure it is focused
        button = getInstrumentation().getUiAutomation().getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(getString(R.string.button5)).get(0);
        assertTrue(button.isFocused());
    }

    @MediumTest
    public void testPerformActionClearFocus() throws Exception {
        // find a view and make sure it is not focused
        AccessibilityNodeInfo button = getInstrumentation().getUiAutomation()
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        getString(R.string.button5)).get(0);
        assertFalse(button.isFocused());

        // focus the view
        assertTrue(button.performAction(ACTION_FOCUS));

        // find the view again and make sure it is focused
        button = getInstrumentation().getUiAutomation().getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(getString(R.string.button5)).get(0);
        assertTrue(button.isFocused());

        // unfocus the view
        assertTrue(button.performAction(ACTION_CLEAR_FOCUS));

        // find the view again and make sure it is not focused
        button = getInstrumentation().getUiAutomation().getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(getString(R.string.button5)).get(0);
        assertFalse(button.isFocused());
    }

    @MediumTest
    public void testPerformActionSelect() throws Exception {
        // find a view and make sure it is not selected
        AccessibilityNodeInfo button = getInstrumentation().getUiAutomation()
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        getString(R.string.button5)).get(0);
        assertFalse(button.isSelected());

        // select the view
        assertTrue(button.performAction(ACTION_SELECT));

        // find the view again and make sure it is selected
        button = getInstrumentation().getUiAutomation().getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(getString(R.string.button5)).get(0);
        assertTrue(button.isSelected());
    }

    @MediumTest
    public void testPerformActionClearSelection() throws Exception {
        // find a view and make sure it is not selected
        AccessibilityNodeInfo button = getInstrumentation().getUiAutomation()
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        getString(R.string.button5)).get(0);
        assertFalse(button.isSelected());

        // select the view
        assertTrue(button.performAction(ACTION_SELECT));

        // find the view again and make sure it is selected
        button = getInstrumentation().getUiAutomation().getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(getString(R.string.button5)).get(0);

        assertTrue(button.isSelected());

        // unselect the view
        assertTrue(button.performAction(ACTION_CLEAR_SELECTION));

        // find the view again and make sure it is not selected
        button = getInstrumentation().getUiAutomation().getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(getString(R.string.button5)).get(0);
        assertFalse(button.isSelected());
    }

    @MediumTest
    public void testPerformActionClick() throws Exception {
        // find a view and make sure it is not selected
        final AccessibilityNodeInfo button = getInstrumentation().getUiAutomation()
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        getString(R.string.button5)).get(0);
        assertFalse(button.isSelected());

        // Make an action and wait for an event.
        AccessibilityEvent expected = getInstrumentation().getUiAutomation()
                .executeAndWaitForEvent(new Runnable() {
            @Override
            public void run() {
                button.performAction(ACTION_CLICK);
            }
        }, new UiAutomation.AccessibilityEventFilter() {
            @Override
            public boolean accept(AccessibilityEvent event) {
                return (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED);
            }
        },
        TIMEOUT_ASYNC_PROCESSING);

        // Make sure the expected event was received.
        assertNotNull(expected);
    }

    @MediumTest
    public void testPerformActionLongClick() throws Exception {
        // find a view and make sure it is not selected
        final AccessibilityNodeInfo button = getInstrumentation().getUiAutomation()
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        getString(R.string.button5)).get(0);
        assertFalse(button.isSelected());

        // Make an action and wait for an event.
        AccessibilityEvent expected = getInstrumentation().getUiAutomation()
                .executeAndWaitForEvent(new Runnable() {
            @Override
            public void run() {
                button.performAction(ACTION_LONG_CLICK);
            }
        }, new UiAutomation.AccessibilityEventFilter() {
            @Override
            public boolean accept(AccessibilityEvent event) {
                return (event.getEventType() == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
            }
        },
        TIMEOUT_ASYNC_PROCESSING);

        // Make sure the expected event was received.
        assertNotNull(expected);
    }


    @MediumTest
    public void testPerformCustomAction() throws Exception {
        // find a view and make sure it is not selected
        AccessibilityNodeInfo button = getInstrumentation().getUiAutomation()
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        getString(R.string.button5)).get(0);

        // find the custom action and perform it
        List<AccessibilityAction> actions = button.getActionList();
        final int actionCount = actions.size();
        for (int i = 0; i < actionCount; i++) {
            AccessibilityAction action = actions.get(i);
            if (action.getId() == R.id.foo_custom_action) {
                assertSame(action.getLabel(), "Foo");
                // perform the action
                assertTrue(button.performAction(action.getId()));
                return;
            }
        }
    }

    @MediumTest
    public void testGetEventSource() throws Exception {
        // find a view and make sure it is not focused
        final AccessibilityNodeInfo button = getInstrumentation().getUiAutomation()
                .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                        getString(R.string.button5)).get(0);
        assertFalse(button.isSelected());

        // focus and wait for the event
        AccessibilityEvent awaitedEvent = getInstrumentation().getUiAutomation()
            .executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                assertTrue(button.performAction(ACTION_FOCUS));
            }
        },
                new UiAutomation.AccessibilityEventFilter() {
            @Override
            public boolean accept(AccessibilityEvent event) {
                return (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED);
            }
        },
        TIMEOUT_ASYNC_PROCESSING);

        assertNotNull(awaitedEvent);

        // check that last event source
        AccessibilityNodeInfo source = awaitedEvent.getSource();
        assertNotNull(source);

        // bounds
        Rect buttonBounds = new Rect();
        button.getBoundsInParent(buttonBounds);
        Rect sourceBounds = new Rect();
        source.getBoundsInParent(sourceBounds);

        assertEquals(buttonBounds.left, sourceBounds.left);
        assertEquals(buttonBounds.right, sourceBounds.right);
        assertEquals(buttonBounds.top, sourceBounds.top);
        assertEquals(buttonBounds.bottom, sourceBounds.bottom);

        // char sequence attributes
        assertEquals(button.getPackageName(), source.getPackageName());
        assertEquals(button.getClassName(), source.getClassName());
        assertEquals(button.getText().toString(), source.getText().toString());
        assertSame(button.getContentDescription(), source.getContentDescription());

        // boolean attributes
        assertSame(button.isFocusable(), source.isFocusable());
        assertSame(button.isClickable(), source.isClickable());
        assertSame(button.isEnabled(), source.isEnabled());
        assertNotSame(button.isFocused(), source.isFocused());
        assertSame(button.isLongClickable(), source.isLongClickable());
        assertSame(button.isPassword(), source.isPassword());
        assertSame(button.isSelected(), source.isSelected());
        assertSame(button.isCheckable(), source.isCheckable());
        assertSame(button.isChecked(), source.isChecked());
    }

    @MediumTest
    public void testObjectContract() throws Exception {
        try {
            AccessibilityServiceInfo info = getInstrumentation().getUiAutomation().getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            getInstrumentation().getUiAutomation().setServiceInfo(info);

            // find a view and make sure it is not focused
            AccessibilityNodeInfo button = getInstrumentation().getUiAutomation()
                    .getRootInActiveWindow().findAccessibilityNodeInfosByText(
                            getString(R.string.button5)).get(0);
            AccessibilityNodeInfo parent = button.getParent();
            final int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = parent.getChild(i);
                assertNotNull(child);
                if (child.equals(button)) {
                    assertEquals("Equal objects must have same hasCode.", button.hashCode(),
                            child.hashCode());
                    return;
                }
            }
            fail("Parent's children do not have the info whose parent is the parent.");
        } finally {
            AccessibilityServiceInfo info = getInstrumentation().getUiAutomation().getServiceInfo();
            info.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            getInstrumentation().getUiAutomation().setServiceInfo(info);
        }
    }

    @MediumTest
    public void testWindowDockAndUndock_dividerWindowAppearsAndDisappears() throws Exception {
        if (getInstrumentation().getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // Android TV doesn't support the divider window
            return;
        }

        // Get com.android.internal.R.bool.config_supportsSplitScreenMultiWindow
        try {
            if (!getInstrumentation().getContext().getResources().getBoolean(
                    Resources.getSystem().getIdentifier(
                            "config_supportsSplitScreenMultiWindow", "bool", "android"))) {
                // Check if split screen multi window is not supported.
                return;
            }
        } catch (Resources.NotFoundException e) {
            // Do nothing, assume split screen multi window is supported.
        }

        // Get com.android.internal.R.bool.config_supportsMultiWindow
        if (!getInstrumentation().getContext().getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_supportsMultiWindow", "bool",
                        "android"))) {
            // Check if multiWindow is supported.
            return;
        }
        setAccessInteractiveWindowsFlag();
        final UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        assertFalse(isDividerWindowPresent(uiAutomation));
        Runnable toggleSplitScreenRunnable = new Runnable() {
            @Override
            public void run() {
                assertTrue(uiAutomation.performGlobalAction(
                        AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN));
            }
        };

        uiAutomation.executeAndWaitForEvent(toggleSplitScreenRunnable, mDividerPresentFilter,
                TIMEOUT_ASYNC_PROCESSING);

        uiAutomation.executeAndWaitForEvent(toggleSplitScreenRunnable, mDividerAbsentFilter,
                TIMEOUT_ASYNC_PROCESSING);
    }

    public void testFindPictureInPictureWindow() throws Exception {
        if (!getInstrumentation().getContext().getPackageManager()
                .hasSystemFeature(FEATURE_PICTURE_IN_PICTURE)) {
            return;
        }
        final UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.executeAndWaitForEvent(() -> {
            getInstrumentation().runOnMainSync(() -> {
                getActivity().enterPictureInPictureMode();
            });
        }, mWindowsChangedFilter, TIMEOUT_ASYNC_PROCESSING);
        waitForIdle();

        // We should be able to find a picture-in-picture window now
        int numPictureInPictureWindows = 0;
        final List<AccessibilityWindowInfo> windows = uiAutomation.getWindows();
        final int windowCount = windows.size();
        for (int i = 0; i < windowCount; i++) {
            final AccessibilityWindowInfo window = windows.get(i);
            if (window.isInPictureInPictureMode()) {
                numPictureInPictureWindows++;
            }
        }
        assertTrue(numPictureInPictureWindows >= 1);
    }

    private boolean isDividerWindowPresent(UiAutomation uiAutomation) {
        List<AccessibilityWindowInfo> windows = uiAutomation.getWindows();
        final int windowCount = windows.size();
        for (int i = 0; i < windowCount; i++) {
            AccessibilityWindowInfo window = windows.get(i);
            Rect bounds = new Rect();
            window.getBoundsInScreen(bounds);
            if (window.getType() == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
                return true;
            }
        }
        return false;
    }

    private void assertSingleAccessibilityFocus() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        List<AccessibilityWindowInfo> windows = uiAutomation.getWindows();
        AccessibilityWindowInfo focused = null;

        final int windowCount = windows.size();
        for (int i = 0; i < windowCount; i++) {
            AccessibilityWindowInfo window = windows.get(i);

            if (window.isAccessibilityFocused()) {
                if (focused == null) {
                    focused = window;

                    AccessibilityNodeInfo root = window.getRoot();
                    assertEquals(uiAutomation.findFocus(
                            AccessibilityNodeInfo.FOCUS_ACCESSIBILITY), root);
                    assertEquals(root.findFocus(
                            AccessibilityNodeInfo.FOCUS_ACCESSIBILITY), root);
                } else {
                    throw new AssertionError("Duplicate accessibility focus");
                }
            } else {
                assertNull(window.getRoot().findFocus(
                        AccessibilityNodeInfo.FOCUS_ACCESSIBILITY));
            }
        }
    }

    private void ensureAppWindowFocusedOrFail(int appWindowIndex) throws TimeoutException {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        List<AccessibilityWindowInfo> windows = uiAutomation.getWindows();
        AccessibilityWindowInfo focusTareget = null;

        int visitedAppWindows = -1;
        final int windowCount = windows.size();
        for (int i = 0; i < windowCount; i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                visitedAppWindows++;
                if (appWindowIndex <= visitedAppWindows) {
                    focusTareget = window;
                    break;
                }
            }
        }

        if (focusTareget == null) {
            throw new IllegalStateException("Couldn't find app window: " + appWindowIndex);
        }

        if (focusTareget.isAccessibilityFocused()) {
            return;
        }

        final AccessibilityWindowInfo finalFocusTarget = focusTareget;
        uiAutomation.executeAndWaitForEvent(new Runnable() {
            @Override
            public void run() {
                assertTrue(finalFocusTarget.getRoot().performAction(
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS));
            }
        }, new UiAutomation.AccessibilityEventFilter() {
            @Override
            public boolean accept(AccessibilityEvent event) {
                return event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            }
        }, TIMEOUT_ASYNC_PROCESSING);

        windows = uiAutomation.getWindows();
        for (int i = 0; i < windowCount; i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window.getId() == focusTareget.getId()) {
                assertTrue(window.isAccessibilityFocused());
                break;
            }
        }
    }

    private void addTwoAppPanelWindows() throws TimeoutException {
        final UiAutomation uiAutomation = getInstrumentation().getUiAutomation();

        uiAutomation.waitForIdle(TIMEOUT_WINDOW_STATE_IDLE, TIMEOUT_ASYNC_PROCESSING);

        // Add the first window.
        uiAutomation.executeAndWaitForEvent(new Runnable() {
            @Override
            public void run() {
                getInstrumentation().runOnMainSync(new Runnable() {
                    @Override
                    public void run() {
                        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                        params.gravity = Gravity.TOP;
                        params.y = getStatusBarHeight();
                        params.width = WindowManager.LayoutParams.MATCH_PARENT;
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                        params.token = getActivity().getWindow().getDecorView().getWindowToken();

                        Button button = new Button(getActivity());
                        button.setText(R.string.button1);
                        getActivity().getWindowManager().addView(button, params);
                    }
                });
            }
        }, new UiAutomation.AccessibilityEventFilter() {
            @Override
            public boolean accept(AccessibilityEvent event) {
                return event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            }
        }, TIMEOUT_ASYNC_PROCESSING);

        // Add the second window.
        uiAutomation.executeAndWaitForEvent(new Runnable() {
            @Override
            public void run() {
                getInstrumentation().runOnMainSync(new Runnable() {
                    @Override
                    public void run() {
                        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                        params.gravity = Gravity.BOTTOM;
                        params.width = WindowManager.LayoutParams.MATCH_PARENT;
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                        params.token = getActivity().getWindow().getDecorView().getWindowToken();

                        Button button = new Button(getActivity());
                        button.setText(R.string.button2);
                        getActivity().getWindowManager().addView(button, params);
                    }
                });
            }
        }, new UiAutomation.AccessibilityEventFilter() {
            @Override
            public boolean accept(AccessibilityEvent event) {
                return event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            }
        }, TIMEOUT_ASYNC_PROCESSING);
    }

    private int getStatusBarHeight() {
        final Rect rect = new Rect();
        Window window = getActivity().getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(rect);
        return rect.top;
    }

    private void setAccessInteractiveWindowsFlag () {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        AccessibilityServiceInfo info = uiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        uiAutomation.setServiceInfo(info);
    }

    private void clearAccessInteractiveWindowsFlag () {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        AccessibilityServiceInfo info = uiAutomation.getServiceInfo();
        info.flags &= ~AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        uiAutomation.setServiceInfo(info);
    }

    private void ensureAccessibilityFocusCleared() {
        try {
            final UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
            uiAutomation.executeAndWaitForEvent(new Runnable() {
                @Override
                public void run() {
                    List<AccessibilityWindowInfo> windows = uiAutomation.getWindows();
                    final int windowCount = windows.size();
                    for (int i = 0; i < windowCount; i++) {
                        AccessibilityWindowInfo window = windows.get(i);
                        if (window.isAccessibilityFocused()) {
                            window.getRoot().performAction(
                                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
                        }
                    }
                }
            }, new UiAutomation.AccessibilityEventFilter() {
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return event.getEventType() ==
                            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED;
                }
            }, TIMEOUT_ASYNC_PROCESSING);
        } catch (TimeoutException te) {
            /* ignore */
        }
    }

    private void verifyNodesInAppWindow(AccessibilityNodeInfo root) throws Exception {
        try {
            AccessibilityServiceInfo info = getInstrumentation().getUiAutomation().getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            getInstrumentation().getUiAutomation().setServiceInfo(info);

            root.refresh();

            // make list of expected nodes
            List<String> classNameAndTextList = new ArrayList<String>();
            classNameAndTextList.add("android.widget.LinearLayout");
            classNameAndTextList.add("android.widget.LinearLayout");
            classNameAndTextList.add("android.widget.LinearLayout");
            classNameAndTextList.add("android.widget.LinearLayout");
            classNameAndTextList.add("android.widget.ButtonB1");
            classNameAndTextList.add("android.widget.ButtonB2");
            classNameAndTextList.add("android.widget.ButtonB3");
            classNameAndTextList.add("android.widget.ButtonB4");
            classNameAndTextList.add("android.widget.ButtonB5");
            classNameAndTextList.add("android.widget.ButtonB6");
            classNameAndTextList.add("android.widget.ButtonB7");
            classNameAndTextList.add("android.widget.ButtonB8");
            classNameAndTextList.add("android.widget.ButtonB9");

            boolean verifyContent = false;

            Queue<AccessibilityNodeInfo> fringe = new LinkedList<AccessibilityNodeInfo>();
            fringe.add(root);

            // do a BFS traversal and check nodes
            while (!fringe.isEmpty()) {
                AccessibilityNodeInfo current = fringe.poll();

                if (!verifyContent
                        && CONTENT_VIEW_RES_NAME.equals(current.getViewIdResourceName())) {
                    verifyContent = true;
                }

                if (verifyContent) {
                    CharSequence text = current.getText();
                    String receivedClassNameAndText = current.getClassName().toString()
                            + ((text != null) ? text.toString() : "");
                    String expectedClassNameAndText = classNameAndTextList.remove(0);

                    assertEquals("Did not get the expected node info",
                            expectedClassNameAndText, receivedClassNameAndText);
                }

                final int childCount = current.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    AccessibilityNodeInfo child = current.getChild(i);
                    fringe.add(child);
                }
            }
        } finally {
            AccessibilityServiceInfo info = getInstrumentation().getUiAutomation().getServiceInfo();
            info.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            getInstrumentation().getUiAutomation().setServiceInfo(info);
        }
    }

    @Override
    protected void scrubClass(Class<?> testCaseClass) {
        /* intentionally do not scrub */
    }
}
