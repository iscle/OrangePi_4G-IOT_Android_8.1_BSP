/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.assist.cts;

import android.app.ActivityManager;
import android.app.assist.AssistContent;
import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.assist.common.Utils;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Bundle;
import android.os.LocaleList;
import android.provider.Settings;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import com.android.compatibility.common.util.SystemUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AssistTestBase extends ActivityInstrumentationTestCase2<TestStartActivity> {
    private static final String TAG = "AssistTestBase";

    protected ActivityManager mActivityManager;
    protected TestStartActivity mTestActivity;
    protected AssistContent mAssistContent;
    protected AssistStructure mAssistStructure;
    protected boolean mScreenshot;
    protected Bitmap mAppScreenshot;
    protected BroadcastReceiver mReceiver;
    protected Bundle mAssistBundle;
    protected Context mContext;
    protected CountDownLatch mLatch, mScreenshotLatch, mHasResumedLatch;
    protected boolean mScreenshotMatches;
    private Point mDisplaySize;
    private String mTestName;
    private View mView;

    public AssistTestBase() {
        super(TestStartActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getTargetContext();
        SystemUtil.runShellCommand(getInstrumentation(),
                "settings put secure assist_structure_enabled 1");
        SystemUtil.runShellCommand(getInstrumentation(),
                "settings put secure assist_screenshot_enabled 1");
        logContextAndScreenshotSetting();

        // reset old values
        mScreenshotMatches = false;
        mScreenshot = false;
        mAssistStructure = null;
        mAssistContent = null;
        mAssistBundle = null;

        if (mReceiver != null) {
            mContext.unregisterReceiver(mReceiver);
        }
        mReceiver = new TestResultsReceiver();
        mContext.registerReceiver(mReceiver,
            new IntentFilter(Utils.BROADCAST_ASSIST_DATA_INTENT));
    }

    @Override
    protected void tearDown() throws Exception {
        mTestActivity.finish();
        mContext.sendBroadcast(new Intent(Utils.HIDE_SESSION));
        if (mReceiver != null) {
            mContext.unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        super.tearDown();
    }

    /**
     * Starts the shim service activity
     */
    protected void startTestActivity(String testName) {
        Intent intent = new Intent();
        mTestName = testName;
        intent.setAction("android.intent.action.TEST_START_ACTIVITY_" + testName);
        intent.setComponent(new ComponentName(getInstrumentation().getContext(),
                TestStartActivity.class));
        intent.putExtra(Utils.TESTCASE_TYPE, testName);
        setActivityIntent(intent);
        mTestActivity = getActivity();
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
    }

    /**
     * Called when waiting for Assistant's Broadcast Receiver to be setup
     */
    public void waitForAssistantToBeReady(CountDownLatch latch) throws Exception {
        Log.i(TAG, "waiting for assistant to be ready before continuing");
        if (!latch.await(Utils.TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Assistant was not ready before timeout of: " + Utils.TIMEOUT_MS + "msec");
        }
    }

    /**
     * Send broadcast to MainInteractionService to start a session
     */
    protected void startSession() {
        startSession(mTestName, new Bundle());
    }

    protected void startSession(String testName, Bundle extras) {
        Intent intent = new Intent(Utils.BROADCAST_INTENT_START_ASSIST);
        Log.i(TAG, "passed in class test name is: " + testName);
        intent.putExtra(Utils.TESTCASE_TYPE, testName);
        addDimensionsToIntent(intent);
        intent.putExtras(extras);
        mContext.sendBroadcast(intent);
    }

    /**
     * Calculate display dimensions (including navbar) to pass along in the given intent.
     */
    private void addDimensionsToIntent(Intent intent) {
        if (mDisplaySize == null) {
            Display display = mTestActivity.getWindowManager().getDefaultDisplay();
            mDisplaySize = new Point();
            display.getRealSize(mDisplaySize);
        }
        intent.putExtra(Utils.DISPLAY_WIDTH_KEY, mDisplaySize.x);
        intent.putExtra(Utils.DISPLAY_HEIGHT_KEY, mDisplaySize.y);
    }

    /**
     * Called after startTestActivity. Includes check for receiving context.
     */
    protected boolean waitForBroadcast() throws Exception {
        mTestActivity.start3pApp(mTestName);
        mTestActivity.startTest(mTestName);
        return waitForContext();
    }

    protected boolean waitForContext() throws Exception {
        mLatch = new CountDownLatch(1);

        if (mReceiver != null) {
            mContext.unregisterReceiver(mReceiver);
        }
        mReceiver = new TestResultsReceiver();
        mContext.registerReceiver(mReceiver,
                new IntentFilter(Utils.BROADCAST_ASSIST_DATA_INTENT));

        if (!mLatch.await(Utils.getAssistDataTimeout(mTestName), TimeUnit.MILLISECONDS)) {
            fail("Fail to receive broadcast in " + Utils.getAssistDataTimeout(mTestName) + "msec");
        }
        Log.i(TAG, "Received broadcast with all information.");
        return true;
    }

    /**
     * Checks that the nullness of values are what we expect.
     *
     * @param isBundleNull True if assistBundle should be null.
     * @param isStructureNull True if assistStructure should be null.
     * @param isContentNull True if assistContent should be null.
     * @param isScreenshotNull True if screenshot should be null.
     */
    protected void verifyAssistDataNullness(boolean isBundleNull, boolean isStructureNull,
            boolean isContentNull, boolean isScreenshotNull) {

        if ((mAssistContent == null) != isContentNull) {
            fail(String.format("Should %s have been null - AssistContent: %s",
                    isContentNull ? "" : "not", mAssistContent));
        }

        if ((mAssistStructure == null) != isStructureNull) {
            fail(String.format("Should %s have been null - AssistStructure: %s",
                    isStructureNull ? "" : "not", mAssistStructure));
        }

        if ((mAssistBundle == null) != isBundleNull) {
            fail(String.format("Should %s have been null - AssistBundle: %s",
                    isBundleNull ? "" : "not", mAssistBundle));
        }

        if (mScreenshot == isScreenshotNull) {
            fail(String.format("Should %s have been null - Screenshot: %s",
                    isScreenshotNull ? "":"not", mScreenshot));
        }
    }

    /**
     * Sends a broadcast with the specified scroll positions to the test app.
     */
    protected void scrollTestApp(int scrollX, int scrollY, boolean scrollTextView,
            boolean scrollScrollView) {
        mTestActivity.scrollText(scrollX, scrollY, scrollTextView, scrollScrollView);
        Intent intent = null;
        if (scrollTextView) {
            intent = new Intent(Utils.SCROLL_TEXTVIEW_ACTION);
        } else if (scrollScrollView) {
            intent = new Intent(Utils.SCROLL_SCROLLVIEW_ACTION);
        }
        intent.putExtra(Utils.SCROLL_X_POSITION, scrollX);
        intent.putExtra(Utils.SCROLL_Y_POSITION, scrollY);
        mContext.sendBroadcast(intent);
    }

    /**
     * Verifies the view hierarchy of the backgroundApp matches the assist structure.
     * @param backgroundApp ComponentName of app the assistant is invoked upon
     * @param isSecureWindow Denotes whether the activity has FLAG_SECURE set
     */
    protected void verifyAssistStructure(ComponentName backgroundApp, boolean isSecureWindow) {
        // Check component name matches
        assertEquals(backgroundApp.flattenToString(),
                mAssistStructure.getActivityComponent().flattenToString());
        long acquisitionStart = mAssistStructure.getAcquisitionStartTime();
        long acquisitionEnd = mAssistStructure.getAcquisitionEndTime();
        assertTrue(acquisitionStart > 0);
        assertTrue(acquisitionEnd > 0);
        assertTrue(acquisitionEnd >= acquisitionStart);
        Log.i(TAG, "Traversing down structure for: " + backgroundApp.flattenToString());
        mView = mTestActivity.findViewById(android.R.id.content).getRootView();
        verifyHierarchy(mAssistStructure, isSecureWindow);
    }

    protected void logContextAndScreenshotSetting() {
        Log.i(TAG, "Context is: " + Settings.Secure.getString(
                mContext.getContentResolver(), "assist_structure_enabled"));
        Log.i(TAG, "Screenshot is: " + Settings.Secure.getString(
                mContext.getContentResolver(), "assist_screenshot_enabled"));
    }

    /**
     * Recursively traverse and compare properties in the View hierarchy with the Assist Structure.
     */
    public void verifyHierarchy(AssistStructure structure, boolean isSecureWindow) {
        Log.i(TAG, "verifyHierarchy");

        int numWindows = structure.getWindowNodeCount();
        // TODO: multiple windows?
        assertEquals("Number of windows don't match", 1, numWindows);
        int[] appLocationOnScreen = new int[2];
        mView.getLocationOnScreen(appLocationOnScreen);

        for (int i = 0; i < numWindows; i++) {
            AssistStructure.WindowNode windowNode = structure.getWindowNodeAt(i);
            Log.i(TAG, "Title: " + windowNode.getTitle());
            // Verify top level window bounds are as big as the app and pinned to its top-left
            // corner.
            assertEquals("Window left position wrong: was " + windowNode.getLeft(),
                    windowNode.getLeft(), appLocationOnScreen[0]);
            assertEquals("Window top position wrong: was " + windowNode.getTop(),
                    windowNode.getTop(), appLocationOnScreen[1]);
            traverseViewAndStructure(
                    mView,
                    windowNode.getRootViewNode(),
                    isSecureWindow);
        }
    }

    private void traverseViewAndStructure(View parentView, ViewNode parentNode,
            boolean isSecureWindow) {
        ViewGroup parentGroup;

        if (parentView == null && parentNode == null) {
            Log.i(TAG, "Views are null, done traversing this branch.");
            return;
        } else if (parentNode == null || parentView == null) {
            fail(String.format("Views don't match. View: %s, Node: %s", parentView, parentNode));
        }

        // Debugging
        Log.i(TAG, "parentView is of type: " + parentView.getClass().getName());
        if (parentView instanceof ViewGroup) {
            for (int childInt = 0; childInt < ((ViewGroup) parentView).getChildCount();
                    childInt++) {
                Log.i(TAG,
                        "viewchild" + childInt + " is of type: "
                        + ((ViewGroup) parentView).getChildAt(childInt).getClass().getName());
            }
        }
        String parentViewId = null;
        if (parentView.getId() > 0) {
            parentViewId = mTestActivity.getResources().getResourceEntryName(parentView.getId());
            Log.i(TAG, "View ID: " + parentViewId);
        }

        Log.i(TAG, "parentNode is of type: " + parentNode.getClassName());
        for (int nodeInt = 0; nodeInt < parentNode.getChildCount(); nodeInt++) {
            Log.i(TAG,
                    "nodechild" + nodeInt + " is of type: "
                    + parentNode.getChildAt(nodeInt).getClassName());
        }
        Log.i(TAG, "Node ID: " + parentNode.getIdEntry());

        assertEquals("IDs do not match", parentViewId, parentNode.getIdEntry());

        int numViewChildren = 0;
        int numNodeChildren = 0;
        if (parentView instanceof ViewGroup) {
            numViewChildren = ((ViewGroup) parentView).getChildCount();
        }
        numNodeChildren = parentNode.getChildCount();

        if (isSecureWindow) {
            assertTrue("ViewNode property isAssistBlocked is false", parentNode.isAssistBlocked());
            assertEquals("Secure window should only traverse root node.", 0, numNodeChildren);
            isSecureWindow = false;
        } else if (parentNode.getClassName().equals("android.webkit.WebView")) {
            // WebView will also appear to have no children while the node does, traverse node
            assertTrue("AssistStructure returned a WebView where the view wasn't one",
                    parentView instanceof WebView);

            boolean textInWebView = false;

            for (int i = numNodeChildren - 1; i >= 0; i--) {
               textInWebView |= traverseWebViewForText(parentNode.getChildAt(i));
            }
            assertTrue("Did not find expected strings inside WebView", textInWebView);
        } else {
            assertEquals("Number of children did not match.", numViewChildren, numNodeChildren);

            verifyViewProperties(parentView, parentNode);

            if (parentView instanceof ViewGroup) {
                parentGroup = (ViewGroup) parentView;

                // TODO: set a max recursion level
                for (int i = numNodeChildren - 1; i >= 0; i--) {
                    View childView = parentGroup.getChildAt(i);
                    ViewNode childNode = parentNode.getChildAt(i);

                    // if isSecureWindow, should not have reached this point.
                    assertFalse(isSecureWindow);
                    traverseViewAndStructure(childView, childNode, isSecureWindow);
                }
            }
        }
    }

    /** 
     * Return true if the expected strings are found in the WebView, else fail.
     */
    private boolean traverseWebViewForText(ViewNode parentNode) {
        boolean textFound = false;
        if (parentNode.getText() != null 
                && parentNode.getText().toString().equals(Utils.WEBVIEW_HTML_GREETING)) {
            return true;
        }
        for (int i = parentNode.getChildCount() - 1; i >= 0; i--) {
            textFound |= traverseWebViewForText(parentNode.getChildAt(i));
        }
        return textFound;
    }

    /**
     * Return true if the expected domain is found in the WebView, else fail.
     */
    protected void verifyAssistStructureHasWebDomain(String domain) {
        assertTrue(traverse(mAssistStructure.getWindowNodeAt(0).getRootViewNode(), (n) -> {
            return n.getWebDomain() != null && domain.equals(n.getWebDomain());
        }));
    }

    /**
     * Return true if the expected LocaleList is found in the WebView, else fail.
     */
    protected void verifyAssistStructureHasLocaleList(LocaleList localeList) {
        assertTrue(traverse(mAssistStructure.getWindowNodeAt(0).getRootViewNode(), (n) -> {
            return n.getLocaleList() != null && localeList.equals(n.getLocaleList());
        }));
    }

    interface ViewNodeVisitor {
        boolean visit(ViewNode node);
    }

    private boolean traverse(ViewNode parentNode, ViewNodeVisitor visitor) {
        if (visitor.visit(parentNode)) {
            return true;
        }
        for (int i = parentNode.getChildCount() - 1; i >= 0; i--) {
            if (traverse(parentNode.getChildAt(i), visitor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compare view properties of the view hierarchy with that reported in the assist structure.
     */
    private void verifyViewProperties(View parentView, ViewNode parentNode) {
        assertEquals("Left positions do not match.", parentView.getLeft(), parentNode.getLeft());
        assertEquals("Top positions do not match.", parentView.getTop(), parentNode.getTop());
        assertEquals("Opaque flags do not match.", parentView.isOpaque(), parentNode.isOpaque());

        int viewId = parentView.getId();

        if (viewId > 0) {
            if (parentNode.getIdEntry() != null) {
                assertEquals("View IDs do not match.",
                        mTestActivity.getResources().getResourceEntryName(viewId),
                        parentNode.getIdEntry());
            }
        } else {
            assertNull("View Node should not have an ID.", parentNode.getIdEntry());
        }

        Log.i(TAG, "parent text: " + parentNode.getText());
        if (parentView instanceof TextView) {
            Log.i(TAG, "view text: " + ((TextView) parentView).getText());
        }


        assertEquals("Scroll X does not match.", parentView.getScrollX(), parentNode.getScrollX());
        assertEquals("Scroll Y does not match.", parentView.getScrollY(), parentNode.getScrollY());
        assertEquals("Heights do not match.", parentView.getHeight(), parentNode.getHeight());
        assertEquals("Widths do not match.", parentView.getWidth(), parentNode.getWidth());

        if (parentView instanceof TextView) {
            if (parentView instanceof EditText) {
                assertEquals("Text selection start does not match",
                        ((EditText) parentView).getSelectionStart(),
                        parentNode.getTextSelectionStart());
                assertEquals("Text selection end does not match",
                        ((EditText) parentView).getSelectionEnd(),
                        parentNode.getTextSelectionEnd());
            }
            TextView textView = (TextView) parentView;
            assertEquals(textView.getTextSize(), parentNode.getTextSize());
            String viewString = textView.getText().toString();
            String nodeString = parentNode.getText().toString();

            if (parentNode.getScrollX() == 0 && parentNode.getScrollY() == 0) {
                Log.i(TAG, "Verifying text within TextView at the beginning");
                Log.i(TAG, "view string: " + viewString);
                Log.i(TAG, "node string: " + nodeString);
                assertTrue("String length is unexpected: original string - " + viewString.length() +
                                ", string in AssistData - " + nodeString.length(),
                        viewString.length() >= nodeString.length());
                assertTrue("Expected a longer string to be shown. expected: "
                                + Math.min(viewString.length(), 30) + " was: " + nodeString
                                .length(),
                        nodeString.length() >= Math.min(viewString.length(), 30));
                for (int x = 0; x < parentNode.getText().length(); x++) {
                    assertEquals("Char not equal at index: " + x,
                            ((TextView) parentView).getText().toString().charAt(x),
                            parentNode.getText().charAt(x));
                }
            } else if (parentNode.getScrollX() == parentView.getWidth()) {

            }
        } else {
            assertNull(parentNode.getText());
        }
    }

    class TestResultsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equalsIgnoreCase(Utils.BROADCAST_ASSIST_DATA_INTENT)) {
                Log.i(TAG, "Received broadcast with assist data.");
                Bundle assistData = intent.getExtras();
                AssistTestBase.this.mAssistBundle = assistData.getBundle(Utils.ASSIST_BUNDLE_KEY);
                AssistTestBase.this.mAssistStructure = assistData.getParcelable(
                        Utils.ASSIST_STRUCTURE_KEY);
                AssistTestBase.this.mAssistContent = assistData.getParcelable(
                        Utils.ASSIST_CONTENT_KEY);

                AssistTestBase.this.mScreenshot =
                        assistData.getBoolean(Utils.ASSIST_SCREENSHOT_KEY, false);

                AssistTestBase.this.mScreenshotMatches = assistData.getBoolean(
                        Utils.COMPARE_SCREENSHOT_KEY, false);

                if (mLatch != null) {
                    Log.i(AssistTestBase.TAG, "counting down latch. received assist data.");
                    mLatch.countDown();
                }
            } else if (intent.getAction().equals(Utils.APP_3P_HASRESUMED)) {
                if (mHasResumedLatch != null) {
                    mHasResumedLatch.countDown();
                }
            }
        }
    }
}
