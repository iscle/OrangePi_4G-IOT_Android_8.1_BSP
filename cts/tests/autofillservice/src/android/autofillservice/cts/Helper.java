/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.autofillservice.cts;

import static android.autofillservice.cts.InstrumentedAutoFillService.SERVICE_NAME;
import static android.autofillservice.cts.UiBot.PORTRAIT;
import static android.provider.Settings.Secure.AUTOFILL_SERVICE;
import static android.provider.Settings.Secure.USER_SETUP_COMPLETE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.app.assist.AssistStructure.WindowNode;
import android.content.Context;
import android.content.pm.PackageManager;
import android.icu.util.Calendar;
import android.service.autofill.FillContext;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewStructure.HtmlInfo;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.webkit.WebView;

import com.android.compatibility.common.util.SystemUtil;

import java.util.List;
import java.util.function.Function;

/**
 * Helper for common funcionalities.
 */
final class Helper {

    private static final String TAG = "AutoFillCtsHelper";

    static final boolean VERBOSE = false;

    static final String ID_USERNAME_LABEL = "username_label";
    static final String ID_USERNAME = "username";
    static final String ID_PASSWORD_LABEL = "password_label";
    static final String ID_PASSWORD = "password";
    static final String ID_LOGIN = "login";
    static final String ID_OUTPUT = "output";

    private static final String CMD_LIST_SESSIONS = "cmd autofill list sessions";

    /**
     * Timeout (in milliseconds) until framework binds / unbinds from service.
     */
    static final long CONNECTION_TIMEOUT_MS = 2000;

    /**
     * Timeout (in milliseconds) until framework unbinds from a service.
     */
    static final long IDLE_UNBIND_TIMEOUT_MS = 5000;

    /**
     * Timeout (in milliseconds) for expected auto-fill requests.
     */
    static final long FILL_TIMEOUT_MS = 2000;

    /**
     * Timeout (in milliseconds) for expected save requests.
     */
    static final long SAVE_TIMEOUT_MS = 5000;

    /**
     * Time to wait if a UI change is not expected
     */
    static final long NOT_SHOWING_TIMEOUT_MS = 500;

    /**
     * Timeout (in milliseconds) for UI operations. Typically used by {@link UiBot}.
     */
    static final int UI_TIMEOUT_MS = 2000;

    /**
     * Timeout (in milliseconds) for an activity to be brought out to top.
     */
    static final int ACTIVITY_RESURRECTION_MS = 5000;

    /**
     * Timeout (in milliseconds) for changing the screen orientation.
     */
    static final int UI_SCREEN_ORIENTATION_TIMEOUT_MS = 5000;

    /**
     * Time to wait in between retries
     */
    static final int RETRY_MS = 100;

    private final static String ACCELLEROMETER_CHANGE =
            "content insert --uri content://settings/system --bind name:s:accelerometer_rotation "
                    + "--bind value:i:%d";

    /**
     * Helper interface used to filter Assist nodes.
     */
    interface NodeFilter {
        /**
         * Returns whether the node passes the filter for such given id.
         */
        boolean matches(ViewNode node, Object id);
    }

    private static final NodeFilter RESOURCE_ID_FILTER = (node, id) -> {
        return id.equals(node.getIdEntry());
    };

    private static final NodeFilter HTML_NAME_FILTER = (node, id) -> {
        return id.equals(getHtmlName(node));
    };

    private static final NodeFilter TEXT_FILTER = (node, id) -> {
        return id.equals(node.getText());
    };

    private static final NodeFilter WEBVIEW_ROOT_FILTER = (node, id) -> {
        // TODO(b/66953802): class name should be android.webkit.WebView, and form name should be
        // inside HtmlInfo, but Chromium 61 does not implement that.
        final String className = node.getClassName();
        final String formName;
        if (className.equals("android.webkit.WebView")) {
            final HtmlInfo htmlInfo = assertHasHtmlTag(node, "form");
            formName = getAttributeValue(htmlInfo, "name");
        } else {
            formName = className;
        }
        return id.equals(formName);
    };

    /**
     * Runs a {@code r}, ignoring all {@link RuntimeException} and {@link Error} until the
     * {@link #UI_TIMEOUT_MS} is reached.
     */
    static void eventually(Runnable r) throws Exception {
        eventually(r, UI_TIMEOUT_MS);
    }

    /**
     * Runs a {@code r}, ignoring all {@link RuntimeException} and {@link Error} until the
     * {@code timeout} is reached.
     */
    static void eventually(Runnable r, int timeout) throws Exception {
        long startTime = System.currentTimeMillis();

        while (true) {
            try {
                r.run();
                break;
            } catch (RuntimeException | Error e) {
                if (System.currentTimeMillis() - startTime < timeout) {
                    if (VERBOSE) Log.v(TAG, "Ignoring", e);
                    Thread.sleep(RETRY_MS);
                } else {
                    if (e instanceof RetryableException) {
                        throw e;
                    } else {
                        throw new RetryableException(e, "Timedout out after %d ms", timeout);
                    }
                }
            }
        }
    }

    /**
     * Runs a Shell command, returning a trimmed response.
     */
    static String runShellCommand(String template, Object...args) {
        final String command = String.format(template, args);
        Log.d(TAG, "runShellCommand(): " + command);
        try {
            final String result = SystemUtil
                    .runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
            return TextUtils.isEmpty(result) ? "" : result.trim();
        } catch (Exception e) {
            throw new RuntimeException("Command '" + command + "' failed: ", e);
        }
    }

    /**
     * Dump the assist structure on logcat.
     */
    static void dumpStructure(String message, AssistStructure structure) {
        final StringBuffer buffer = new StringBuffer(message)
                .append(": component=")
                .append(structure.getActivityComponent());
        final int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            final WindowNode windowNode = structure.getWindowNodeAt(i);
            dump(buffer, windowNode.getRootViewNode(), " ", 0);
        }
        Log.i(TAG, buffer.toString());
    }

    /**
     * Dump the contexts on logcat.
     */
    static void dumpStructure(String message, List<FillContext> contexts) {
        for (FillContext context : contexts) {
            dumpStructure(message, context.getStructure());
        }
    }

    /**
     * Dumps the state of the autofill service on logcat.
     */
    static void dumpAutofillService() {
        Log.i(TAG, "dumpsys autofill\n\n" + runShellCommand("dumpsys autofill"));
    }

    /**
     * Sets whether the user completed the initial setup.
     */
    static void setUserComplete(Context context, boolean complete) {
        if (isUserComplete() == complete) return;

        final OneTimeSettingsListener observer = new OneTimeSettingsListener(context,
                USER_SETUP_COMPLETE);
        final String newValue = complete ? "1" : null;
        runShellCommand("settings put secure %s %s default", USER_SETUP_COMPLETE, newValue);
        observer.assertCalled();

        assertIsUserComplete(complete);
    }

    /**
     * Gets whether the user completed the initial setup.
     */
    static boolean isUserComplete() {
        final String isIt = runShellCommand("settings get secure %s", USER_SETUP_COMPLETE);
        return "1".equals(isIt);
    }

    /**
     * Assets that user completed (or not) the initial setup.
     */
    static void assertIsUserComplete(boolean expected) {
        final boolean actual = isUserComplete();
        assertWithMessage("Invalid value for secure setting %s", USER_SETUP_COMPLETE)
                .that(actual).isEqualTo(expected);
    }

    private static void dump(StringBuffer buffer, ViewNode node, String prefix, int childId) {
        final int childrenSize = node.getChildCount();
        buffer.append("\n").append(prefix)
            .append('#').append(childId).append(':')
            .append("resId=").append(node.getIdEntry())
            .append(" class=").append(node.getClassName())
            .append(" text=").append(node.getText())
            .append(" class=").append(node.getClassName())
            .append(" webDomain=").append(node.getWebDomain())
            .append(" #children=").append(childrenSize);

        buffer.append("\n").append(prefix)
            .append("   afId=").append(node.getAutofillId())
            .append(" afType=").append(node.getAutofillType())
            .append(" afValue=").append(node.getAutofillValue())
            .append(" checked=").append(node.isChecked())
            .append(" focused=").append(node.isFocused());

        final HtmlInfo htmlInfo = node.getHtmlInfo();
        if (htmlInfo != null) {
            buffer.append("\nHtmlInfo: tag=").append(htmlInfo.getTag())
                .append(", attrs: ").append(htmlInfo.getAttributes());
        }

        prefix += " ";
        if (childrenSize > 0) {
            for (int i = 0; i < childrenSize; i++) {
                dump(buffer, node.getChildAt(i), prefix, i);
            }
        }
    }

    /**
     * Gets a node if it matches the filter criteria for the given id.
     */
    static ViewNode findNodeByFilter(@NonNull AssistStructure structure, @NonNull Object id,
            @NonNull NodeFilter filter) {
        Log.v(TAG, "Parsing request for activity " + structure.getActivityComponent());
        final int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            final WindowNode windowNode = structure.getWindowNodeAt(i);
            final ViewNode rootNode = windowNode.getRootViewNode();
            final ViewNode node = findNodeByFilter(rootNode, id, filter);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /**
     * Gets a node if it matches the filter criteria for the given id.
     */
    static ViewNode findNodeByFilter(@NonNull List<FillContext> contexts, @NonNull Object id,
            @NonNull NodeFilter filter) {
        for (FillContext context : contexts) {
            ViewNode node = findNodeByFilter(context.getStructure(), id, filter);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /**
     * Gets a node if it matches the filter criteria for the given id.
     */
    static ViewNode findNodeByFilter(@NonNull ViewNode node, @NonNull Object id,
            @NonNull NodeFilter filter) {
        if (filter.matches(node, id)) {
            return node;
        }
        final int childrenSize = node.getChildCount();
        if (childrenSize > 0) {
            for (int i = 0; i < childrenSize; i++) {
                final ViewNode found = findNodeByFilter(node.getChildAt(i), id, filter);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Gets a node given its Android resource id, or {@code null} if not found.
     */
    static ViewNode findNodeByResourceId(AssistStructure structure, String resourceId) {
        return findNodeByFilter(structure, resourceId, RESOURCE_ID_FILTER);
    }

    /**
     * Gets a node given its Android resource id, or {@code null} if not found.
     */
    static ViewNode findNodeByResourceId(List<FillContext> contexts, String resourceId) {
        return findNodeByFilter(contexts, resourceId, RESOURCE_ID_FILTER);
    }

    /**
     * Gets a node given its Android resource id, or {@code null} if not found.
     */
    static ViewNode findNodeByResourceId(ViewNode node, String resourceId) {
        return findNodeByFilter(node, resourceId, RESOURCE_ID_FILTER);
    }

    /**
     * Gets a node given the name of its HTML INPUT tag, or {@code null} if not found.
     */
    static ViewNode findNodeByHtmlName(AssistStructure structure, String htmlName) {
        return findNodeByFilter(structure, htmlName, HTML_NAME_FILTER);
    }

    /**
     * Gets a node given the name of its HTML INPUT tag, or {@code null} if not found.
     */
    static ViewNode findNodeByHtmlName(List<FillContext> contexts, String htmlName) {
        return findNodeByFilter(contexts, htmlName, HTML_NAME_FILTER);
    }

    /**
     * Gets a node given the name of its HTML INPUT tag, or {@code null} if not found.
     */
    static ViewNode findNodeByHtmlName(ViewNode node, String htmlName) {
        return findNodeByFilter(node, htmlName, HTML_NAME_FILTER);
    }

    /**
     * Gets the {@code name} attribute of a node representing an HTML input tag.
     */
    @Nullable
    static String getHtmlName(@NonNull ViewNode node) {
        final HtmlInfo htmlInfo = node.getHtmlInfo();
        if (htmlInfo == null) {
            return null;
        }
        final String tag = htmlInfo.getTag();
        if (!"input".equals(tag)) {
            Log.w(TAG, "getHtmlName(): invalid tag (" + tag + ") on " + htmlInfo);
            return null;
        }
        for (Pair<String, String> attr : htmlInfo.getAttributes()) {
            if ("name".equals(attr.first)) {
                return attr.second;
            }
        }
        Log.w(TAG, "getHtmlName(): no 'name' attribute on " + htmlInfo);
        return null;
    }

    /**
     * Gets a node given its expected text, or {@code null} if not found.
     */
    static ViewNode findNodeByText(AssistStructure structure, String text) {
        return findNodeByFilter(structure, text, TEXT_FILTER);
    }

    /**
     * Gets a node given its expected text, or {@code null} if not found.
     */
    static ViewNode findNodeByText(ViewNode node, String text) {
        return findNodeByFilter(node, text, TEXT_FILTER);
    }

    /**
     * Asserts a text-base node is sanitized.
     */
    static void assertTextIsSanitized(ViewNode node) {
        final CharSequence text = node.getText();
        final String resourceId = node.getIdEntry();
        if (!TextUtils.isEmpty(text)) {
            throw new AssertionError("text on sanitized field " + resourceId + ": " + text);
        }
        assertNodeHasNoAutofillValue(node);
    }

    static void assertNodeHasNoAutofillValue(ViewNode node) {
        final AutofillValue value = node.getAutofillValue();
        if (value != null) {
            final String text = value.isText() ? value.getTextValue().toString() : "N/A";
            throw new AssertionError("node has value: " + value + " text=" + text);
        }
    }

    /**
     * Asserts the contents of a text-based node that is also auto-fillable.
     *
     */
    static void assertTextOnly(ViewNode node, String expectedValue) {
        assertText(node, expectedValue, false);
    }

    /**
     * Asserts the contents of a text-based node that is also auto-fillable.
     *
     */
    static void assertTextAndValue(ViewNode node, String expectedValue) {
        assertText(node, expectedValue, true);
    }

    /**
     * Asserts a text-base node exists and verify its values.
     */
    static ViewNode assertTextAndValue(AssistStructure structure, String resourceId,
            String expectedValue) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertTextAndValue(node, expectedValue);
        return node;
    }

    /**
     * Asserts a text-base node exists and is sanitized.
     */
    static ViewNode assertValue(AssistStructure structure, String resourceId,
            String expectedValue) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertTextValue(node, expectedValue);
        return node;
    }

    private static void assertText(ViewNode node, String expectedValue, boolean isAutofillable) {
        assertWithMessage("wrong text on %s", node).that(node.getText().toString())
                .isEqualTo(expectedValue);
        final AutofillValue value = node.getAutofillValue();
        if (isAutofillable) {
            assertWithMessage("null auto-fill value on %s", node).that(value).isNotNull();
            assertWithMessage("wrong auto-fill value on %s", node)
                    .that(value.getTextValue().toString()).isEqualTo(expectedValue);
        } else {
            assertWithMessage("node %s should not have AutofillValue", node).that(value).isNull();
        }
    }

    /**
     * Asserts the auto-fill value of a text-based node.
     */
    static ViewNode assertTextValue(ViewNode node, String expectedText) {
        final AutofillValue value = node.getAutofillValue();
        assertWithMessage("null autofill value on %s", node).that(value).isNotNull();
        assertWithMessage("wrong autofill type on %s", node).that(value.isText()).isTrue();
        assertWithMessage("wrong autofill value on %s", node).that(value.getTextValue().toString())
                .isEqualTo(expectedText);
        return node;
    }

    /**
     * Asserts the auto-fill value of a list-based node.
     */
    static ViewNode assertListValue(ViewNode node, int expectedIndex) {
        final AutofillValue value = node.getAutofillValue();
        assertWithMessage("null autofill value on %s", node).that(value).isNotNull();
        assertWithMessage("wrong autofill type on %s", node).that(value.isList()).isTrue();
        assertWithMessage("wrong autofill value on %s", node).that(value.getListValue())
                .isEqualTo(expectedIndex);
        return node;
    }

    /**
     * Asserts the auto-fill value of a toggle-based node.
     */
    static void assertToggleValue(ViewNode node, boolean expectedToggle) {
        final AutofillValue value = node.getAutofillValue();
        assertWithMessage("null autofill value on %s", node).that(value).isNotNull();
        assertWithMessage("wrong autofill type on %s", node).that(value.isToggle()).isTrue();
        assertWithMessage("wrong autofill value on %s", node).that(value.getToggleValue())
                .isEqualTo(expectedToggle);
    }

    /**
     * Asserts the auto-fill value of a date-based node.
     */
    static void assertDateValue(Object object, AutofillValue value, int year, int month, int day) {
        assertWithMessage("null autofill value on %s", object).that(value).isNotNull();
        assertWithMessage("wrong autofill type on %s", object).that(value.isDate()).isTrue();

        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(value.getDateValue());

        assertWithMessage("Wrong year on AutofillValue %s", value)
            .that(cal.get(Calendar.YEAR)).isEqualTo(year);
        assertWithMessage("Wrong month on AutofillValue %s", value)
            .that(cal.get(Calendar.MONTH)).isEqualTo(month);
        assertWithMessage("Wrong day on AutofillValue %s", value)
             .that(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(day);
    }

    /**
     * Asserts the auto-fill value of a date-based node.
     */
    static void assertDateValue(ViewNode node, int year, int month, int day) {
        assertDateValue(node, node.getAutofillValue(), year, month, day);
    }

    /**
     * Asserts the auto-fill value of a date-based view.
     */
    static void assertDateValue(View view, int year, int month, int day) {
        assertDateValue(view, view.getAutofillValue(), year, month, day);
    }

    /**
     * Asserts the auto-fill value of a time-based node.
     */
    private static void assertTimeValue(Object object, AutofillValue value, int hour, int minute) {
        assertWithMessage("null autofill value on %s", object).that(value).isNotNull();
        assertWithMessage("wrong autofill type on %s", object).that(value.isDate()).isTrue();

        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(value.getDateValue());

        assertWithMessage("Wrong hour on AutofillValue %s", value)
            .that(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(hour);
        assertWithMessage("Wrong minute on AutofillValue %s", value)
            .that(cal.get(Calendar.MINUTE)).isEqualTo(minute);
    }

    /**
     * Asserts the auto-fill value of a time-based node.
     */
    static void assertTimeValue(ViewNode node, int hour, int minute) {
        assertTimeValue(node, node.getAutofillValue(), hour, minute);
    }

    /**
     * Asserts the auto-fill value of a time-based view.
     */
    static void assertTimeValue(View view, int hour, int minute) {
        assertTimeValue(view, view.getAutofillValue(), hour, minute);
    }

    /**
     * Asserts a text-base node exists and is sanitized.
     */
    static ViewNode assertTextIsSanitized(AssistStructure structure, String resourceId) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertWithMessage("no ViewNode with id %s", resourceId).that(node).isNotNull();
        assertTextIsSanitized(node);
        return node;
    }

    /**
     * Asserts a list-based node exists and is sanitized.
     */
    static void assertListValueIsSanitized(AssistStructure structure, String resourceId) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertWithMessage("no ViewNode with id %s", resourceId).that(node).isNotNull();
        assertTextIsSanitized(node);
    }

    /**
     * Asserts a toggle node exists and is sanitized.
     */
    static void assertToggleIsSanitized(AssistStructure structure, String resourceId) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertNodeHasNoAutofillValue(node);
        assertWithMessage("ViewNode %s should not be checked", resourceId).that(node.isChecked())
                .isFalse();
    }

    /**
     * Asserts a node exists and has the {@code expected} number of children.
     */
    static void assertNumberOfChildren(AssistStructure structure, String resourceId, int expected) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        final int actual = node.getChildCount();
        if (actual != expected) {
            dumpStructure("assertNumberOfChildren()", structure);
            throw new AssertionError("assertNumberOfChildren() for " + resourceId
                    + " failed: expected " + expected + ", got " + actual);
        }
    }

    /**
     * Asserts the number of children in the Assist structure.
     */
    static void assertNumberOfChildren(AssistStructure structure, int expected) {
        assertWithMessage("wrong number of nodes").that(structure.getWindowNodeCount())
                .isEqualTo(1);
        final int actual = getNumberNodes(structure);
        if (actual != expected) {
            dumpStructure("assertNumberOfChildren()", structure);
            throw new AssertionError("assertNumberOfChildren() for structure failed: expected "
                    + expected + ", got " + actual);
        }
    }

    /**
     * Gets the total number of nodes in an structure, including all descendants.
     */
    static int getNumberNodes(AssistStructure structure) {
        int count = 0;
        final int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            final WindowNode windowNode = structure.getWindowNodeAt(i);
            final ViewNode rootNode = windowNode.getRootViewNode();
            count += getNumberNodes(rootNode);
        }
        return count;
    }

    /**
     * Gets the total number of nodes in an node, including all descendants and the node itself.
     */
    private static int getNumberNodes(ViewNode node) {
        int count = 1;
        final int childrenSize = node.getChildCount();
        if (childrenSize > 0) {
            for (int i = 0; i < childrenSize; i++) {
                count += getNumberNodes(node.getChildAt(i));
            }
        }
        return count;
    }

    /**
     * Creates an array of {@link AutofillId} mapped from the {@code structure} nodes with the given
     * {@code resourceIds}.
     */
    static AutofillId[] getAutofillIds(Function<String, ViewNode> nodeResolver,
            String[] resourceIds) {
        if (resourceIds == null) return null;

        final AutofillId[] requiredIds = new AutofillId[resourceIds.length];
        for (int i = 0; i < resourceIds.length; i++) {
            final String resourceId = resourceIds[i];
            final ViewNode node = nodeResolver.apply(resourceId);
            if (node == null) {
                throw new AssertionError("No node with savable resourceId " + resourceId);
            }
            requiredIds[i] = node.getAutofillId();

        }
        return requiredIds;
    }

    /**
     * Prevents the screen to rotate by itself
     */
    public static void disableAutoRotation(UiBot uiBot) {
        runShellCommand(ACCELLEROMETER_CHANGE, 0);
        uiBot.setScreenOrientation(PORTRAIT);
    }

    /**
     * Allows the screen to rotate by itself
     */
    public static void allowAutoRotation() {
        runShellCommand(ACCELLEROMETER_CHANGE, 1);
    }

    /**
     * Wait until a process starts and returns the process ID of the process.
     *
     * @return The pid of the process
     */
    public static int getOutOfProcessPid(@NonNull String processName) {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime <= UI_TIMEOUT_MS) {
            String[] allProcessDescs = runShellCommand("ps -eo PID,ARGS=CMD").split("\n");

            for (String processDesc : allProcessDescs) {
                String[] pidAndName = processDesc.trim().split(" ");

                if (pidAndName[1].equals(processName)) {
                    return Integer.parseInt(pidAndName[0]);
                }
            }

            try {
                Thread.sleep(RETRY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        throw new IllegalStateException("process not found");
    }

    /**
     * Gets the maximum number of partitions per session.
     */
    public static int getMaxPartitions() {
        return Integer.parseInt(runShellCommand("cmd autofill get max_partitions"));
    }

    /**
     * Sets the maximum number of partitions per session.
     */
    public static void setMaxPartitions(int value) {
        runShellCommand("cmd autofill set max_partitions %d", value);
        assertThat(getMaxPartitions()).isEqualTo(value);
    }

    /**
     * Checks if device supports the Autofill feature.
     */
    public static boolean hasAutofillFeature() {
        return RequiredFeatureRule.hasFeature(PackageManager.FEATURE_AUTOFILL);
    }

    /**
     * Uses Shell command to get the Autofill logging level.
     */
    public static String getLoggingLevel() {
        return runShellCommand("cmd autofill get log_level");
    }

    /**
     * Uses Shell command to set the Autofill logging level.
     */
    public static void setLoggingLevel(String level) {
        runShellCommand("cmd autofill set log_level %s", level);
    }

    /**
     * Uses Settings to enable the given autofill service for the default user, and checks the
     * value was properly check, throwing an exception if it was not.
     */
    public static void enableAutofillService(Context context, String serviceName) {
        if (isAutofillServiceEnabled(serviceName)) return;

        final OneTimeSettingsListener observer = new OneTimeSettingsListener(context,
                AUTOFILL_SERVICE);
        runShellCommand("settings put secure %s %s default", AUTOFILL_SERVICE, serviceName);
        observer.assertCalled();
        assertAutofillServiceStatus(serviceName, true);
    }

    /**
     * Uses Settings to disable the given autofill service for the default user, and checks the
     * value was properly check, throwing an exception if it was not.
     */
    public static void disableAutofillService(Context context, String serviceName) {
        if (!isAutofillServiceEnabled(serviceName)) return;

        final OneTimeSettingsListener observer = new OneTimeSettingsListener(context,
                AUTOFILL_SERVICE);
        runShellCommand("settings delete secure %s", AUTOFILL_SERVICE);
        observer.assertCalled();
        assertAutofillServiceStatus(serviceName, false);
    }

    /**
     * Checks whether the given service is set as the autofill service for the default user.
     */
    public static boolean isAutofillServiceEnabled(String serviceName) {
        final String actualName = runShellCommand("settings get secure %s", AUTOFILL_SERVICE);
        return serviceName.equals(actualName);
    }

    /**
     * Asserts whether the given service is enabled as the autofill service for the default user.
     */
    public static void assertAutofillServiceStatus(String serviceName, boolean enabled) {
        final String actual = runShellCommand("settings get secure %s", AUTOFILL_SERVICE);
        final String expected = enabled ? serviceName : "null";
        assertWithMessage("Invalid value for secure setting %s", AUTOFILL_SERVICE)
                .that(actual).isEqualTo(expected);
    }

    /**
     * Asserts that there is no session left in the service.
     */
    public static void assertNoDanglingSessions() {
        final String result = runShellCommand(CMD_LIST_SESSIONS);
        assertWithMessage("Dangling sessions ('%s'): %s'", CMD_LIST_SESSIONS, result).that(result)
                .isEmpty();
    }

    /**
     * Asserts that there is a pending session for the given package.
     */
    public static void assertHasSessions(String packageName) {
        final String result = runShellCommand(CMD_LIST_SESSIONS);
        assertThat(result).contains(packageName);
    }

    /**
     * Destroys all sessions.
     */
    public static void destroyAllSessions() {
        runShellCommand("cmd autofill destroy sessions");
        assertNoDanglingSessions();
    }

    /**
     * Gets the instrumentation context.
     */
    public static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    /**
     * Cleans up the autofill state; should be called before pretty much any test.
     */
    public static void preTestCleanup() {
        if (!hasAutofillFeature()) return;

        Log.d(TAG, "preTestCleanup()");

        disableAutofillService(getContext(), SERVICE_NAME);
        InstrumentedAutoFillService.setIgnoreUnexpectedRequests(true);

        destroyAllSessions();
        InstrumentedAutoFillService.resetStaticState();
        AuthenticationActivity.resetStaticState();
    }

    /**
     * Asserts the node has an {@code HTMLInfo} property, with the given tag.
     */
    public static HtmlInfo assertHasHtmlTag(ViewNode node, String expectedTag) {
        final HtmlInfo info = node.getHtmlInfo();
        assertWithMessage("node doesn't have htmlInfo").that(info).isNotNull();
        assertWithMessage("wrong tag").that(info.getTag()).isEqualTo(expectedTag);
        return info;
    }

    /**
     * Gets the value of an {@code HTMLInfo} attribute.
     */
    @Nullable
    public static String getAttributeValue(HtmlInfo info, String attribute) {
        for (Pair<String, String> pair : info.getAttributes()) {
            if (pair.first.equals(attribute)) {
                return pair.second;
            }
        }
        return null;
    }

    /**
     * Asserts a {@code HTMLInfo} has an attribute with a given value.
     */
    public static void assertHasAttribute(HtmlInfo info, String attribute, String expectedValue) {
        final String actualValue = getAttributeValue(info, attribute);
        assertWithMessage("Attribute %s not found", attribute).that(actualValue).isNotNull();
        assertWithMessage("Wrong value for Attribute %s", attribute)
            .that(actualValue).isEqualTo(expectedValue);
    }

    /**
     * Finds a {@link WebView} node given its expected form name.
     */
    public static ViewNode findWebViewNode(AssistStructure structure, String formName) {
        return findNodeByFilter(structure, formName, WEBVIEW_ROOT_FILTER);
    }

    /**
     * Finds a {@link WebView} node given its expected form name.
     */
    public static ViewNode findWebViewNode(ViewNode node, String formName) {
        return findNodeByFilter(node, formName, WEBVIEW_ROOT_FILTER);
    }

    private Helper() {
    }
}
