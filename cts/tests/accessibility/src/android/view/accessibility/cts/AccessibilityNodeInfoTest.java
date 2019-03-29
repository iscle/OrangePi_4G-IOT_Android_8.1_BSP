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

import android.graphics.Rect;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.view.accessibility.cts.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class for testing {@link AccessibilityNodeInfo}.
 */
public class AccessibilityNodeInfoTest extends AndroidTestCase {

    /** The number of properties of the {@link AccessibilityNodeInfo} class that are marshalled. */
    private static final int NUM_MARSHALLED_PROPERTIES = 33;

    /**
     * The number of properties that are purposely not marshalled
     * mOriginalText - Used when resolving clickable spans; intentionally not parceled
     */
    private static final int NUM_NONMARSHALLED_PROPERTIES = 1;

    @SmallTest
    public void testMarshaling() throws Exception {
        // no new fields, so we are testing marshaling of all such
        AccessibilityRecordTest.assertNoNewNonStaticFieldsAdded(AccessibilityNodeInfo.class,
                NUM_MARSHALLED_PROPERTIES + NUM_NONMARSHALLED_PROPERTIES);

        // fully populate the node info to marshal
        AccessibilityNodeInfo sentInfo = AccessibilityNodeInfo.obtain(new View(getContext()));
        fullyPopulateAccessibilityNodeInfo(sentInfo);

        // marshal and unmarshal the node info
        Parcel parcel = Parcel.obtain();
        sentInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AccessibilityNodeInfo receivedInfo = AccessibilityNodeInfo.CREATOR.createFromParcel(parcel);

        // make sure all fields properly marshaled
        assertEqualsAccessibilityNodeInfo(sentInfo, receivedInfo);
    }

    /**
     * Tests if {@link AccessibilityNodeInfo}s are properly reused.
     */
    @SmallTest
    public void testReuse() {
        AccessibilityEvent firstInfo = AccessibilityEvent.obtain();
        firstInfo.recycle();
        AccessibilityEvent secondInfo = AccessibilityEvent.obtain();
        assertSame("AccessibilityNodeInfo not properly reused", firstInfo, secondInfo);
    }

    /**
     * Tests if {@link AccessibilityNodeInfo} are properly recycled.
     */
    @SmallTest
    public void testRecycle() {
        // obtain and populate an node info
        AccessibilityNodeInfo populatedInfo = AccessibilityNodeInfo.obtain();
        fullyPopulateAccessibilityNodeInfo(populatedInfo);

        // recycle and obtain the same recycled instance
        populatedInfo.recycle();
        AccessibilityNodeInfo recycledInfo = AccessibilityNodeInfo.obtain();

        // check expectations
        assertAccessibilityNodeInfoCleared(recycledInfo);
    }

    /**
     * Tests whether the event describes its contents consistently.
     */
    @SmallTest
    public void testDescribeContents() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        assertSame("Accessibility node infos always return 0 for this method.", 0,
                info.describeContents());
        fullyPopulateAccessibilityNodeInfo(info);
        assertSame("Accessibility node infos always return 0 for this method.", 0,
                info.describeContents());
    }

    /**
     * Tests whether accessibility actions are properly added.
     */
    @SmallTest
    public void testAddActions() {
        List<AccessibilityAction> customActions = new ArrayList<AccessibilityAction>();
        customActions.add(new AccessibilityAction(AccessibilityNodeInfo.ACTION_FOCUS, "Foo"));
        customActions.add(new AccessibilityAction(R.id.foo_custom_action, "Foo"));

        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.addAction(AccessibilityNodeInfo.ACTION_FOCUS);
        info.addAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS);
        for (AccessibilityAction customAction : customActions) {
            info.addAction(customAction);
        }

        assertSame(info.getActions(), (AccessibilityNodeInfo.ACTION_FOCUS
                | AccessibilityNodeInfo.ACTION_CLEAR_FOCUS));

        List<AccessibilityAction> allActions = new ArrayList<AccessibilityAction>();
        allActions.add(AccessibilityAction.ACTION_CLEAR_FOCUS);
        allActions.addAll(customActions);
        assertEquals(info.getActionList(), allActions);
    }

    /**
     * Tests whether we catch addition of an action with invalid id.
     */
    @SmallTest
    public void testCreateInvalidActionId() {
        try {
            new AccessibilityAction(3, null);
        } catch (IllegalArgumentException iae) {
            /* expected */
        }
    }

    /**
     * Tests whether accessibility actions are properly removed.
     */
    @SmallTest
    public void testRemoveActions() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();

        info.addAction(AccessibilityNodeInfo.ACTION_FOCUS);
        assertSame(info.getActions(), AccessibilityNodeInfo.ACTION_FOCUS);

        info.removeAction(AccessibilityNodeInfo.ACTION_FOCUS);
        assertSame(info.getActions(), 0);
        assertTrue(info.getActionList().isEmpty());

        AccessibilityAction customFocus = new AccessibilityAction(
                AccessibilityNodeInfo.ACTION_FOCUS, "Foo");
        info.addAction(AccessibilityNodeInfo.ACTION_FOCUS);
        info.addAction(customFocus);
        assertSame(info.getActionList().size(), 1);
        assertEquals(info.getActionList().get(0), customFocus);
        assertSame(info.getActions(), AccessibilityNodeInfo.ACTION_FOCUS);

        info.removeAction(customFocus);
        assertSame(info.getActions(), 0);
        assertTrue(info.getActionList().isEmpty());
    }

    /**
     * While CharSequence is immutable, some classes implementing it are mutable. Make sure they
     * can't change the object by changing the objects backing CharSequence
     */
    @SmallTest
    public void testChangeTextAfterSetting_shouldNotAffectInfo() {
        final String originalText = "Cassowaries";
        final String newText = "Hornbill";
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        StringBuffer updatingString = new StringBuffer(originalText);
        info.setText(updatingString);
        info.setError(updatingString);
        info.setContentDescription(updatingString);

        updatingString.delete(0, updatingString.length());
        updatingString.append(newText);

        assertTrue(TextUtils.equals(originalText, info.getText()));
        assertTrue(TextUtils.equals(originalText, info.getError()));
        assertTrue(TextUtils.equals(originalText, info.getContentDescription()));
    }

    /**
     * Fully populates the {@link AccessibilityNodeInfo} to marshal.
     *
     * @param info The node info to populate.
     */
    private void fullyPopulateAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        info.setParent(new View(getContext()));
        info.setSource(new View(getContext()));
        info.addChild(new View(getContext()));
        info.addChild(new View(getContext()), 1);
        info.setBoundsInParent(new Rect(1,1,1,1));
        info.setBoundsInScreen(new Rect(2,2,2,2));
        info.setClassName("foo.bar.baz.Class");
        info.setContentDescription("content description");
        info.setPackageName("foo.bar.baz");
        info.setText("text");
        info.setHintText("hint");
        info.setCheckable(true);
        info.setChecked(true);
        info.setClickable(true);
        info.setEnabled(true);
        info.setFocusable(true);
        info.setFocused(true);
        info.setImportantForAccessibility(true);
        info.setLongClickable(true);
        info.setContextClickable(true);
        info.setPassword(true);
        info.setScrollable(true);
        info.setSelected(true);
        info.addAction(AccessibilityNodeInfo.ACTION_FOCUS);
        info.addAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS);
        info.addAction(new AccessibilityAction(AccessibilityNodeInfo.ACTION_FOCUS, "Foo"));
        info.addAction(new AccessibilityAction(R.id.foo_custom_action, "Foo"));
        info.setAccessibilityFocused(true);
        info.setMovementGranularities(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE);
        info.setLabeledBy(new View(getContext()));
        info.setLabelFor(new View(getContext()));
        info.setViewIdResourceName("foo.bar:id/baz");
        info.setDrawingOrder(5);
        info.setAvailableExtraData(
                Arrays.asList(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY));
    }

    /**
     * Compares all properties of the <code>expectedInfo</code> and the
     * <code>receviedInfo</code> to verify that the received node info is
     * the one that is expected.
     */
    public static void assertEqualsAccessibilityNodeInfo(AccessibilityNodeInfo expectedInfo,
            AccessibilityNodeInfo receivedInfo) {
        Rect expectedBounds = new Rect();
        Rect receivedBounds = new Rect();
        expectedInfo.getBoundsInParent(expectedBounds);
        receivedInfo.getBoundsInParent(receivedBounds);
        assertEquals("boundsInParent has incorrect value", expectedBounds, receivedBounds);
        expectedInfo.getBoundsInScreen(expectedBounds);
        receivedInfo.getBoundsInScreen(receivedBounds);
        assertEquals("boundsInScreen has incorrect value", expectedBounds, receivedBounds);
        assertEquals("className has incorrect value", expectedInfo.getClassName(),
                receivedInfo.getClassName());
        assertEquals("contentDescription has incorrect value", expectedInfo.getContentDescription(),
                receivedInfo.getContentDescription());
        assertEquals("packageName has incorrect value", expectedInfo.getPackageName(),
                receivedInfo.getPackageName());
        assertEquals("text has incorrect value", expectedInfo.getText(), receivedInfo.getText());
        assertEquals("Hint text has incorrect value",
                expectedInfo.getHintText(), receivedInfo.getHintText());
        assertSame("checkable has incorrect value", expectedInfo.isCheckable(),
                receivedInfo.isCheckable());
        assertSame("checked has incorrect value", expectedInfo.isChecked(),
                receivedInfo.isChecked());
        assertSame("clickable has incorrect value", expectedInfo.isClickable(),
                receivedInfo.isClickable());
        assertSame("enabled has incorrect value", expectedInfo.isEnabled(),
                receivedInfo.isEnabled());
        assertSame("focusable has incorrect value", expectedInfo.isFocusable(),
                receivedInfo.isFocusable());
        assertSame("focused has incorrect value", expectedInfo.isFocused(),
                receivedInfo.isFocused());
        assertSame("importantForAccessibility has incorrect value",
                expectedInfo.isImportantForAccessibility(),
                receivedInfo.isImportantForAccessibility());
        assertSame("longClickable has incorrect value", expectedInfo.isLongClickable(),
                receivedInfo.isLongClickable());
        assertSame("contextClickable has incorrect value", expectedInfo.isContextClickable(),
                receivedInfo.isContextClickable());
        assertSame("password has incorrect value", expectedInfo.isPassword(),
                receivedInfo.isPassword());
        assertSame("scrollable has incorrect value", expectedInfo.isScrollable(),
                receivedInfo.isScrollable());
        assertSame("selected has incorrect value", expectedInfo.isSelected(),
                receivedInfo.isSelected());
        assertSame("actions has incorrect value", expectedInfo.getActions(),
                receivedInfo.getActions());
        assertEquals("actionsSet has incorrect value", expectedInfo.getActionList(),
                receivedInfo.getActionList());
        assertSame("childCount has incorrect value", expectedInfo.getChildCount(),
                receivedInfo.getChildCount());
        assertSame("childCount has incorrect value", expectedInfo.getChildCount(),
                receivedInfo.getChildCount());
        assertSame("accessibilityFocused has incorrect value",
                expectedInfo.isAccessibilityFocused(),
                receivedInfo.isAccessibilityFocused());
        assertSame("movementGranularities has incorrect value",
                expectedInfo.getMovementGranularities(),
                receivedInfo.getMovementGranularities());
        assertEquals("viewId has incorrect value", expectedInfo.getViewIdResourceName(),
                receivedInfo.getViewIdResourceName());
        assertEquals("drawing order has incorrect value", expectedInfo.getDrawingOrder(),
                receivedInfo.getDrawingOrder());
        assertEquals("Extra data flags have incorrect value", expectedInfo.getAvailableExtraData(),
                receivedInfo.getAvailableExtraData());
    }

    /**
     * Asserts that an {@link AccessibilityNodeInfo} is cleared.
     *
     * @param info The node info to check.
     */
    public static void assertAccessibilityNodeInfoCleared(AccessibilityNodeInfo info) {
        Rect bounds = new Rect();
        info.getBoundsInParent(bounds);
        assertTrue("boundsInParent not properly recycled", bounds.isEmpty());
        info.getBoundsInScreen(bounds);
        assertTrue("boundsInScreen not properly recycled", bounds.isEmpty());
        assertNull("className not properly recycled", info.getClassName());
        assertNull("contentDescription not properly recycled", info.getContentDescription());
        assertNull("packageName not properly recycled", info.getPackageName());
        assertNull("text not properly recycled", info.getText());
        assertNull("Hint text not properly recycled", info.getHintText());
        assertFalse("checkable not properly recycled", info.isCheckable());
        assertFalse("checked not properly recycled", info.isChecked());
        assertFalse("clickable not properly recycled", info.isClickable());
        assertFalse("enabled not properly recycled", info.isEnabled());
        assertFalse("focusable not properly recycled", info.isFocusable());
        assertFalse("focused not properly recycled", info.isFocused());
        assertFalse("importantForAccessibility not properly recycled",
                info.isImportantForAccessibility());
        assertFalse("longClickable not properly recycled", info.isLongClickable());
        assertFalse("contextClickable not properly recycled", info.isContextClickable());
        assertFalse("password not properly recycled", info.isPassword());
        assertFalse("scrollable not properly recycled", info.isScrollable());
        assertFalse("selected not properly recycled", info.isSelected());
        assertSame("actions not properly recycled", 0, info.getActions());
        assertFalse("accessibilityFocused not properly recycled", info.isAccessibilityFocused());
        assertSame("movementGranularities not properly recycled", 0,
                info.getMovementGranularities());
        assertNull("viewId not properly recycled", info.getViewIdResourceName());
        assertEquals(0, info.getDrawingOrder());
        assertTrue(info.getAvailableExtraData().isEmpty());
    }
}
