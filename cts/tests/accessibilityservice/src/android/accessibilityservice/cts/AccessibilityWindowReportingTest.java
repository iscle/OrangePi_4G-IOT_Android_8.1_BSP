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

package android.accessibilityservice.cts;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Tests that AccessibilityWindowInfos are properly populated
 */
public class AccessibilityWindowReportingTest
        extends AccessibilityActivityTestCase<AccessibilityWindowReportingActivity> {
    UiAutomation mUiAutomation;

    public AccessibilityWindowReportingTest() {
        super(AccessibilityWindowReportingActivity.class);
    }

    public void setUp() throws Exception {
        super.setUp();
        mUiAutomation = getInstrumentation().getUiAutomation();
        AccessibilityServiceInfo info = mUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        mUiAutomation.setServiceInfo(info);
    }

    public void tearDown() throws Exception {
        mUiAutomation.destroy();
        super.tearDown();
    }

    public void testWindowTitle_getTitleReturnsTitle() {
        AccessibilityWindowInfo window = findWindowByTitle(getActivity().getTitle());
        assertNotNull("Window title not reported to accessibility", window);
        window.recycle();
    }

    public void testUpdatedWindowTitle_generatesEventAndIsReturnedByGetTitle() {
        final String updatedTitle = "Updated Title";
        try {
            mUiAutomation.executeAndWaitForEvent(new Runnable() {
                @Override
                public void run() {
                    getInstrumentation().runOnMainSync(new Runnable() {
                        @Override
                        public void run() {
                            getActivity().setTitle(updatedTitle);
                        }
                    });
                }
            }, new UiAutomation.AccessibilityEventFilter() {
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED);
                }
            }, TIMEOUT_ASYNC_PROCESSING);
        } catch (TimeoutException exception) {
            throw new RuntimeException(
                    "Failed to get windows changed event for title update", exception);
        }
        AccessibilityWindowInfo window = findWindowByTitle(updatedTitle);
        assertNotNull("Updated window title not reported to accessibility", window);
        window.recycle();
    }

    public void testGetAnchorForDropDownForAutoCompleteTextView_returnsTextViewNode() {
        final AutoCompleteTextView autoCompleteTextView =
                (AutoCompleteTextView) getActivity().findViewById(R.id.autoCompleteLayout);
        AccessibilityNodeInfo autoCompleteTextInfo = mUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/autoCompleteLayout")
                .get(0);

        // For the drop-down
        final String[] COUNTRIES = new String[] {"Belgium", "France", "Italy", "Germany", "Spain"};

        try {
            mUiAutomation.executeAndWaitForEvent(new Runnable() {
                @Override
                public void run() {
                    getInstrumentation().runOnMainSync(new Runnable() {
                        @Override
                        public void run() {
                            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
                                    android.R.layout.simple_dropdown_item_1line, COUNTRIES);
                            autoCompleteTextView.setAdapter(adapter);
                            autoCompleteTextView.showDropDown();
                        }
                    });
                }
            }, new UiAutomation.AccessibilityEventFilter() {
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED;
                }
            }, TIMEOUT_ASYNC_PROCESSING);
        } catch (TimeoutException exception) {
            throw new RuntimeException(
                    "Failed to get window changed event when showing dropdown", exception);
        }

        // Find the pop-up window
        boolean foundPopup = false;
        List<AccessibilityWindowInfo> windows = mUiAutomation.getWindows();
        for (int i = 0; i < windows.size(); i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window.getAnchor() == null) {
                continue;
            }
            assertEquals(autoCompleteTextInfo, window.getAnchor());
            assertFalse("Found multiple pop-ups anchored to one text view", foundPopup);
            foundPopup = true;
        }
        assertTrue("Failed to find accessibility window for auto-complete pop-up", foundPopup);
    }

    private AccessibilityWindowInfo findWindowByTitle(CharSequence title) {
        List<AccessibilityWindowInfo> windows = mUiAutomation.getWindows();
        AccessibilityWindowInfo returnValue = null;
        for (int i = 0; i < windows.size(); i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (TextUtils.equals(title, window.getTitle())) {
                returnValue = window;
            } else {
                window.recycle();
            }
        }
        return returnValue;
    }
}
