/**
 * Copyright (C) 2012 The Android Open Source Project
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

package android.accessibilityservice.cts;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.content.Context;
import android.test.suitebuilder.annotation.MediumTest;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import android.accessibilityservice.cts.R;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * Test cases for testing the accessibility focus APIs exposed to accessibility
 * services. This test checks how the view hierarchy is reported to accessibility
 * services.
 */
public class AccessibilityViewTreeReportingTest
        extends AccessibilityActivityTestCase<AccessibilityViewTreeReportingActivity>{

    public AccessibilityViewTreeReportingTest() {
        super(AccessibilityViewTreeReportingActivity.class);
    }

    @MediumTest
    public void testDescendantsOfNotImportantViewReportedInOrder1() throws Exception {
        UiAutomation uiAutomation = getUiAutomation(false);
        AccessibilityNodeInfo firstFrameLayout =
                getNodeByText(uiAutomation, R.string.firstFrameLayout);
        assertNotNull(firstFrameLayout);
        assertSame(3, firstFrameLayout.getChildCount());

        // Check if the first child is the right one.
        AccessibilityNodeInfo firstTextView = getNodeByText(uiAutomation, R.string.firstTextView);
        assertEquals(firstTextView, firstFrameLayout.getChild(0));

        // Check if the second child is the right one.
        AccessibilityNodeInfo firstEditText = getNodeByText(uiAutomation, R.string.firstEditText);
        assertEquals(firstEditText, firstFrameLayout.getChild(1));

        // Check if the third child is the right one.
        AccessibilityNodeInfo firstButton = getNodeByText(uiAutomation, R.string.firstButton);
        assertEquals(firstButton, firstFrameLayout.getChild(2));
    }

    @MediumTest
    public void testDescendantsOfNotImportantViewReportedInOrder2() throws Exception {
        UiAutomation uiAutomation = getUiAutomation(false);
        AccessibilityNodeInfo secondFrameLayout =
                getNodeByText(uiAutomation, R.string.secondFrameLayout);
        assertNotNull(secondFrameLayout);
        assertSame(3, secondFrameLayout.getChildCount());

        // Check if the first child is the right one.
        AccessibilityNodeInfo secondTextView = getNodeByText(uiAutomation, R.string.secondTextView);
        assertEquals(secondTextView, secondFrameLayout.getChild(0));

        // Check if the second child is the right one.
        AccessibilityNodeInfo secondEditText = getNodeByText(uiAutomation, R.string.secondEditText);
        assertEquals(secondEditText, secondFrameLayout.getChild(1));

        // Check if the third child is the right one.
        AccessibilityNodeInfo secondButton = getNodeByText(uiAutomation, R.string.secondButton);
        assertEquals(secondButton, secondFrameLayout.getChild(2));
    }

    @MediumTest
    public void testDescendantsOfNotImportantViewReportedInOrder3() throws Exception {
        UiAutomation uiAutomation = getUiAutomation(false);
        AccessibilityNodeInfo rootLinearLayout =
                getNodeByText(uiAutomation, R.string.rootLinearLayout);
        assertNotNull(rootLinearLayout);
        assertSame(4, rootLinearLayout.getChildCount());

        // Check if the first child is the right one.
        AccessibilityNodeInfo firstFrameLayout =
                getNodeByText(uiAutomation, R.string.firstFrameLayout);
        assertEquals(firstFrameLayout, rootLinearLayout.getChild(0));

        // Check if the second child is the right one.
        AccessibilityNodeInfo secondTextView = getNodeByText(uiAutomation, R.string.secondTextView);
        assertEquals(secondTextView, rootLinearLayout.getChild(1));

        // Check if the third child is the right one.
        AccessibilityNodeInfo secondEditText = getNodeByText(uiAutomation, R.string.secondEditText);
        assertEquals(secondEditText, rootLinearLayout.getChild(2));

        // Check if the fourth child is the right one.
        AccessibilityNodeInfo secondButton = getNodeByText(uiAutomation, R.string.secondButton);
        assertEquals(secondButton, rootLinearLayout.getChild(3));
    }

    @MediumTest
    public void testDrawingOrderInImportantParentFollowsXmlOrder() throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                getActivity().findViewById(R.id.firstLinearLayout)
                        .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            }
        });

        UiAutomation uiAutomation = getUiAutomation(false);
        AccessibilityNodeInfo firstTextView = getNodeByText(uiAutomation, R.string.firstTextView);
        AccessibilityNodeInfo firstEditText = getNodeByText(uiAutomation, R.string.firstEditText);
        AccessibilityNodeInfo firstButton = getNodeByText(uiAutomation, R.string.firstButton);

        // Drawing order is: firstTextView, firstEditText, firstButton
        assertTrue(firstTextView.getDrawingOrder() < firstEditText.getDrawingOrder());
        assertTrue(firstEditText.getDrawingOrder() < firstButton.getDrawingOrder());

        // Confirm that obtaining copies doesn't change our results
        AccessibilityNodeInfo copyOfFirstEditText = AccessibilityNodeInfo.obtain(firstEditText);
        assertTrue(firstTextView.getDrawingOrder() < copyOfFirstEditText.getDrawingOrder());
        assertTrue(copyOfFirstEditText.getDrawingOrder() < firstButton.getDrawingOrder());
    }

    @MediumTest
    public void testDrawingOrderGettingAllViewsFollowsXmlOrder() throws Exception {
        UiAutomation uiAutomation = getUiAutomation(true);
        AccessibilityNodeInfo firstTextView = getNodeByText(uiAutomation, R.string.firstTextView);
        AccessibilityNodeInfo firstEditText = getNodeByText(uiAutomation, R.string.firstEditText);
        AccessibilityNodeInfo firstButton = getNodeByText(uiAutomation, R.string.firstButton);

        // Drawing order is: firstTextView, firstEditText, firstButton
        assertTrue(firstTextView.getDrawingOrder() < firstEditText.getDrawingOrder());
        assertTrue(firstEditText.getDrawingOrder() < firstButton.getDrawingOrder());
    }

    @MediumTest
    public void testDrawingOrderWithZCoordsDrawsHighestZLast() throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
           @Override
           public void run() {
               AccessibilityViewTreeReportingActivity activity = getActivity();
               activity.findViewById(R.id.firstTextView).setZ(50);
               activity.findViewById(R.id.firstEditText).setZ(100);
           }
        });

        UiAutomation uiAutomation = getUiAutomation(true);
        AccessibilityNodeInfo firstTextView = getNodeByText(uiAutomation, R.string.firstTextView);
        AccessibilityNodeInfo firstEditText = getNodeByText(uiAutomation, R.string.firstEditText);
        AccessibilityNodeInfo firstButton = getNodeByText(uiAutomation, R.string.firstButton);

        // Drawing order is firstButton (no z), firstTextView (z=50), firstEditText (z=100)
        assertTrue(firstButton.getDrawingOrder() < firstTextView.getDrawingOrder());
        assertTrue(firstTextView.getDrawingOrder() < firstEditText.getDrawingOrder());
    }

    @MediumTest
    public void testDrawingOrderWithCustomDrawingOrder() throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                // Reorganize the hiearchy to replace firstLinearLayout with one that allows us to
                // control the draw order
                AccessibilityViewTreeReportingActivity activity = getActivity();
                LinearLayout rootLinearLayout =
                        (LinearLayout) activity.findViewById(R.id.rootLinearLayout);
                LinearLayout firstLinearLayout =
                        (LinearLayout) activity.findViewById(R.id.firstLinearLayout);
                View firstTextView = activity.findViewById(R.id.firstTextView);
                View firstEditText = activity.findViewById(R.id.firstEditText);
                View firstButton = activity.findViewById(R.id.firstButton);
                firstLinearLayout.removeAllViews();
                LinearLayoutWithDrawingOrder layoutWithDrawingOrder =
                        new LinearLayoutWithDrawingOrder(activity);
                rootLinearLayout.addView(layoutWithDrawingOrder);
                layoutWithDrawingOrder.addView(firstTextView);
                layoutWithDrawingOrder.addView(firstEditText);
                layoutWithDrawingOrder.addView(firstButton);
                layoutWithDrawingOrder.childDrawingOrder = new int[] {2, 0, 1};
            }
        });

        UiAutomation uiAutomation = getUiAutomation(true);
        AccessibilityNodeInfo firstTextView = getNodeByText(uiAutomation, R.string.firstTextView);
        AccessibilityNodeInfo firstEditText = getNodeByText(uiAutomation, R.string.firstEditText);
        AccessibilityNodeInfo firstButton = getNodeByText(uiAutomation, R.string.firstButton);

        // Drawing order is firstEditText, firstButton, firstTextView
        assertTrue(firstEditText.getDrawingOrder() < firstButton.getDrawingOrder());
        assertTrue(firstButton.getDrawingOrder() < firstTextView.getDrawingOrder());
    }

    @MediumTest
    public void testDrawingOrderWithNotImportantSiblingConsidersItsChildren() throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                // Make the first frame layout a higher Z so it's drawn last
                getActivity().findViewById(R.id.firstFrameLayout).setZ(100);
            }
        });
        UiAutomation uiAutomation = getUiAutomation(false);
        AccessibilityNodeInfo secondTextView = getNodeByText(uiAutomation, R.string.secondTextView);
        AccessibilityNodeInfo secondEditText = getNodeByText(uiAutomation, R.string.secondEditText);
        AccessibilityNodeInfo secondButton = getNodeByText(uiAutomation, R.string.secondButton);
        AccessibilityNodeInfo firstFrameLayout =
                getNodeByText(uiAutomation, R.string.firstFrameLayout);
        assertTrue(secondTextView.getDrawingOrder() < firstFrameLayout.getDrawingOrder());
        assertTrue(secondEditText.getDrawingOrder() < firstFrameLayout.getDrawingOrder());
        assertTrue(secondButton.getDrawingOrder() < firstFrameLayout.getDrawingOrder());
    }

    @MediumTest
    public void testDrawingOrderWithNotImportantParentConsidersParentSibling() throws Exception {
        UiAutomation uiAutomation = getUiAutomation(false);
        AccessibilityNodeInfo firstFrameLayout =
                getNodeByText(uiAutomation, R.string.firstFrameLayout);
        AccessibilityNodeInfo secondTextView = getNodeByText(uiAutomation, R.string.secondTextView);
        AccessibilityNodeInfo secondEditText = getNodeByText(uiAutomation, R.string.secondEditText);
        AccessibilityNodeInfo secondButton = getNodeByText(uiAutomation, R.string.secondButton);

        assertTrue(secondTextView.getDrawingOrder() > firstFrameLayout.getDrawingOrder());
        assertTrue(secondEditText.getDrawingOrder() > firstFrameLayout.getDrawingOrder());
        assertTrue(secondButton.getDrawingOrder() > firstFrameLayout.getDrawingOrder());
    }

    @MediumTest
    public void testDrawingOrderRootNodeHasIndex0() throws Exception {
        assertEquals(0, getUiAutomation(false).getRootInActiveWindow().getDrawingOrder());
    }

    @MediumTest
    public void testAccessibilityImportanceReportingForImportantView() throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                // Manually control importance for firstButton
                AccessibilityViewTreeReportingActivity activity = getActivity();
                View firstButton = activity.findViewById(R.id.firstButton);
                firstButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            }
        });

        UiAutomation uiAutomation = getUiAutomation(true);
        AccessibilityNodeInfo firstButtonNode = getNodeByText(uiAutomation, R.string.firstButton);
        assertTrue(firstButtonNode.isImportantForAccessibility());
    }

    @MediumTest
    public void testAccessibilityImportanceReportingForUnimportantView() throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                // Manually control importance for firstButton
                AccessibilityViewTreeReportingActivity activity = getActivity();
                View firstButton = activity.findViewById(R.id.firstButton);
                firstButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
        });

        UiAutomation uiAutomation = getUiAutomation(true);
        AccessibilityNodeInfo firstButtonNode = getNodeByText(uiAutomation, R.string.firstButton);
        assertFalse(firstButtonNode.isImportantForAccessibility());
    }

    @MediumTest
    public void testAddViewToLayout_receiveSubtreeEvent() throws Throwable {
        final LinearLayout layout =
                (LinearLayout) getActivity().findViewById(R.id.secondLinearLayout);
        final Button newButton = new Button(getActivity());
        newButton.setText("New Button");
        newButton.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        newButton.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        AccessibilityEvent awaitedEvent =
                getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                        new Runnable() {
                            @Override
                            public void run() {
                                // trigger the event
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        layout.addView(newButton);
                                    }
                                });
                            }},
                        new UiAutomation.AccessibilityEventFilter() {
                            // check the received event
                            @Override
                            public boolean accept(AccessibilityEvent event) {
                                boolean isContentChanged = event.getEventType()
                                        == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                int isSubTree = (event.getContentChangeTypes()
                                        & AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
                                boolean isFromThisPackage = TextUtils.equals(event.getPackageName(),
                                        getActivity().getPackageName());
                                return isContentChanged && (isSubTree != 0) && isFromThisPackage;
                            }
                        },
                        TIMEOUT_ASYNC_PROCESSING);
        // The event should come from a view that's important for accessibility, even though the
        // layout we added it to isn't important. Otherwise services may not find out about the
        // new button.
        assertTrue(awaitedEvent.getSource().isImportantForAccessibility());
    }

    private UiAutomation getUiAutomation(boolean getNonImportantViews) {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        AccessibilityServiceInfo serviceInfo = uiAutomation.getServiceInfo();
        serviceInfo.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        serviceInfo.flags |= getNonImportantViews ?
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS : 0;
        uiAutomation.setServiceInfo(serviceInfo);
        return uiAutomation;
    }

    private AccessibilityNodeInfo getNodeByText(UiAutomation uiAutomation, int stringId) {
        return uiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByText(getString(stringId)).get(0);
    }

    class LinearLayoutWithDrawingOrder extends LinearLayout {
        public int[] childDrawingOrder;
        LinearLayoutWithDrawingOrder(Context context) {
            super(context);
            setChildrenDrawingOrderEnabled(true);
        }

        @Override
        protected int getChildDrawingOrder(int childCount, int i) {
            return childDrawingOrder[i];
        }
    }
}
