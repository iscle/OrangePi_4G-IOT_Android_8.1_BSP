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

import static android.autofillservice.cts.Helper.NOT_SHOWING_TIMEOUT_MS;
import static android.autofillservice.cts.Helper.SAVE_TIMEOUT_MS;
import static android.autofillservice.cts.Helper.UI_TIMEOUT_MS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_ADDRESS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_USERNAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.res.Resources;
import android.os.RemoteException;
import android.os.SystemClock;
import android.service.autofill.SaveInfo;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.text.Html;
import android.util.Log;
import android.view.accessibility.AccessibilityWindowInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Helper for UI-related needs.
 */
final class UiBot {

    private static final String RESOURCE_ID_DATASET_PICKER = "autofill_dataset_picker";
    private static final String RESOURCE_ID_SAVE_SNACKBAR = "autofill_save";
    private static final String RESOURCE_ID_SAVE_ICON = "autofill_save_icon";
    private static final String RESOURCE_ID_SAVE_TITLE = "autofill_save_title";
    private static final String RESOURCE_ID_CONTEXT_MENUITEM = "floating_toolbar_menu_item_text";

    private static final String RESOURCE_STRING_SAVE_TITLE = "autofill_save_title";
    private static final String RESOURCE_STRING_SAVE_TITLE_WITH_TYPE =
            "autofill_save_title_with_type";
    private static final String RESOURCE_STRING_SAVE_TYPE_PASSWORD = "autofill_save_type_password";
    private static final String RESOURCE_STRING_SAVE_TYPE_ADDRESS = "autofill_save_type_address";
    private static final String RESOURCE_STRING_SAVE_TYPE_CREDIT_CARD =
            "autofill_save_type_credit_card";
    private static final String RESOURCE_STRING_SAVE_TYPE_USERNAME = "autofill_save_type_username";
    private static final String RESOURCE_STRING_SAVE_TYPE_EMAIL_ADDRESS =
            "autofill_save_type_email_address";
    private static final String RESOURCE_STRING_AUTOFILL = "autofill";
    private static final String RESOURCE_STRING_DATASET_PICKER_ACCESSIBILITY_TITLE =
            "autofill_picker_accessibility_title";
    private static final String RESOURCE_STRING_SAVE_SNACKBAR_ACCESSIBILITY_TITLE =
            "autofill_save_accessibility_title";

    private static final String TAG = "AutoFillCtsUiBot";

    /** Pass to {@link #setScreenOrientation(int)} to change the display to portrait mode */
    public static int PORTRAIT = 0;

    /** Pass to {@link #setScreenOrientation(int)} to change the display to landscape mode */
    public static int LANDSCAPE = 1;


    private final UiDevice mDevice;
    private final Context mContext;
    private final String mPackageName;
    private final UiAutomation mAutoman;

    UiBot(Instrumentation instrumentation) throws Exception {
        mDevice = UiDevice.getInstance(instrumentation);
        mContext = instrumentation.getContext();
        mPackageName = mContext.getPackageName();
        mAutoman = instrumentation.getUiAutomation();
    }

    /**
     * Asserts the dataset chooser is not shown.
     */
    void assertNoDatasets() {
        final UiObject2 picker;
        try {
            picker = findDatasetPicker(NOT_SHOWING_TIMEOUT_MS);
        } catch (Throwable t) {
            // Use a more elegant check than catching the expection because it's not showing...
            return;
        }
        throw new RetryableException(
                "Should not be showing datasets, but got " + getChildrenAsText(picker));
    }

    /**
     * Asserts the dataset chooser is shown and contains the given datasets.
     *
     * @return the dataset picker object.
     */
    UiObject2 assertDatasets(String...names) {
        final UiObject2 picker = findDatasetPicker();
        assertWithMessage("wrong dataset names").that(getChildrenAsText(picker))
                .containsExactlyElementsIn(Arrays.asList(names));
        return picker;
    }

    /**
     * Gets the text of this object children.
     */
    List<String> getChildrenAsText(UiObject2 object) {
        final List<String> list = new ArrayList<>();
        getChildrenAsText(object, list);
        return list;
    }

    private static void getChildrenAsText(UiObject2 object, List<String> children) {
        final String text = object.getText();
        if (text != null) {
            children.add(text);
        }
        for (UiObject2 child : object.getChildren()) {
            getChildrenAsText(child, children);
        }
    }

    /**
     * Selects a dataset that should be visible in the floating UI.
     */
    void selectDataset(String name) {
        final UiObject2 picker = findDatasetPicker();
        selectDataset(picker, name);
    }

    /**
     * Selects a dataset that should be visible in the floating UI.
     */
    void selectDataset(UiObject2 picker, String name) {
        final UiObject2 dataset = picker.findObject(By.text(name));
        if (dataset == null) {
            throw new AssertionError("no dataset " + name + " in " + getChildrenAsText(picker));
        }
        dataset.click();
    }

    /**
     * Selects a view by text.
     *
     * <p><b>NOTE:</b> when selecting an option in dataset picker is shown, prefer
     * {@link #selectDataset(String)}.
     */
    void selectByText(String name) {
        Log.v(TAG, "selectByText(): " + name);

        final UiObject2 object = waitForObject(By.text(name));
        object.click();
    }

    /**
     * Asserts a text is shown.
     *
     * <p><b>NOTE:</b> when asserting the dataset picker is shown, prefer
     * {@link #assertDatasets(String...)}.
     */
    public UiObject2 assertShownByText(String text) {
        return assertShownByText(text, UI_TIMEOUT_MS);
    }

    public UiObject2 assertShownByText(String text, int timeoutMs) {
        final UiObject2 object = waitForObject(By.text(text), timeoutMs);
        assertWithMessage("No node with text '%s'", text).that(object).isNotNull();
        return object;
    }

    /**
     * Asserts a node with the given content description is shown.
     *
     */
    public UiObject2 assertShownByContentDescription(String contentDescription) {
        final UiObject2 object = waitForObject(By.desc(contentDescription));
        assertWithMessage("No node with content description '%s'", contentDescription).that(object)
                .isNotNull();
        return object;
    }

    /**
     * Checks if a View with a certain text exists.
     */
    boolean hasViewWithText(String name) {
        Log.v(TAG, "hasViewWithText(): " + name);

        return mDevice.findObject(By.text(name)) != null;
    }

    /**
     * Selects a view by id.
     */
    void selectById(String id) {
        Log.v(TAG, "selectById(): " + id);

        final UiObject2 view = waitForObject(By.res(id));
        view.click();
    }

    /**
     * Asserts the id is shown on the screen.
     */
    void assertShownById(String id) {
        assertThat(waitForObject(By.res(id))).isNotNull();
    }

    /**
     * Asserts the id is shown on the screen, using a resource id from the test package.
     */
    UiObject2 assertShownByRelativeId(String id) {
        final UiObject2 obj = waitForObject(By.res(mPackageName, id));
        assertThat(obj).isNotNull();
        return obj;
    }

    /**
     * Gets the text set on a view.
     */
    String getTextById(String id) {
        final UiObject2 obj = waitForObject(By.res(id));
        return obj.getText();
    }

    /**
     * Focus in the view with the given resource id.
     */
    void focusByRelativeId(String id) {
        waitForObject(By.res(mPackageName, id)).click();
    }

    /**
     * Sets a new text on a view.
     */
    void setTextById(String id, String newText) {
        UiObject2 view = waitForObject(By.res(id));
        view.setText(newText);
    }

    /**
     * Asserts the save snackbar is showing and returns it.
     */
    UiObject2 assertSaveShowing(int type) {
        return assertSaveShowing(SAVE_TIMEOUT_MS, type);
    }

    /**
     * Asserts the save snackbar is showing and returns it.
     */
    UiObject2 assertSaveShowing(long timeout, int type) {
        return assertSaveShowing(null, timeout, type);
    }

    /**
     * Presses the Back button.
     */
    void pressBack() {
        Log.d(TAG, "pressBack()");
        mDevice.pressBack();
    }

    /**
     * Presses the Home button.
     */
    void pressHome() {
        Log.d(TAG, "pressHome()");
        mDevice.pressHome();
    }

    /**
     * Uses the Recents button to switch back to previous activity
     */
    void switchAppsUsingRecents() throws RemoteException {
        Log.d(TAG, "switchAppsUsingRecents()");

        // Press once to show list of apps...
        mDevice.pressRecentApps();
        // ...press again to go back to the activity.
        mDevice.pressRecentApps();
    }

    /**
     * Asserts the save snackbar is not showing and returns it.
     */
    void assertSaveNotShowing(int type) {
        try {
            assertSaveShowing(NOT_SHOWING_TIMEOUT_MS, type);
        } catch (Throwable t) {
            // TODO: use a more elegant check than catching the expection because it's not showing
            // (in which case it wouldn't need a type as parameter).
            return;
        }
        throw new RetryableException("snack bar is showing");
    }

    private String getSaveTypeString(int type) {
        final String typeResourceName;
        switch (type) {
            case SAVE_DATA_TYPE_PASSWORD:
                typeResourceName = RESOURCE_STRING_SAVE_TYPE_PASSWORD;
                break;
            case SAVE_DATA_TYPE_ADDRESS:
                typeResourceName = RESOURCE_STRING_SAVE_TYPE_ADDRESS;
                break;
            case SAVE_DATA_TYPE_CREDIT_CARD:
                typeResourceName = RESOURCE_STRING_SAVE_TYPE_CREDIT_CARD;
                break;
            case SAVE_DATA_TYPE_USERNAME:
                typeResourceName = RESOURCE_STRING_SAVE_TYPE_USERNAME;
                break;
            case SAVE_DATA_TYPE_EMAIL_ADDRESS:
                typeResourceName = RESOURCE_STRING_SAVE_TYPE_EMAIL_ADDRESS;
                break;
            default:
                throw new IllegalArgumentException("Unsupported type: " + type);
        }
        return getString(typeResourceName);
    }

    UiObject2 assertSaveShowing(String description, int... types) {
        return assertSaveShowing(SaveInfo.NEGATIVE_BUTTON_STYLE_CANCEL, description,
                SAVE_TIMEOUT_MS, types);
    }

    UiObject2 assertSaveShowing(String description, long timeout, int... types) {
        return assertSaveShowing(SaveInfo.NEGATIVE_BUTTON_STYLE_CANCEL, description, timeout,
                types);
    }

    UiObject2 assertSaveShowing(int negativeButtonStyle, String description,
            int... types) {
        return assertSaveShowing(negativeButtonStyle, description, SAVE_TIMEOUT_MS, types);
    }

    UiObject2 assertSaveShowing(int negativeButtonStyle, String description, long timeout,
            int... types) {
        final UiObject2 snackbar = waitForObject(By.res("android", RESOURCE_ID_SAVE_SNACKBAR),
                timeout);

        final UiObject2 titleView =
                waitForObject(snackbar, By.res("android", RESOURCE_ID_SAVE_TITLE), UI_TIMEOUT_MS);
        assertWithMessage("save title (%s) is not shown", RESOURCE_ID_SAVE_TITLE).that(titleView)
                .isNotNull();

        final UiObject2 iconView =
                waitForObject(snackbar, By.res("android", RESOURCE_ID_SAVE_ICON), UI_TIMEOUT_MS);
        assertWithMessage("save icon (%s) is not shown", RESOURCE_ID_SAVE_ICON).that(iconView)
                .isNotNull();

        final String actualTitle = titleView.getText();
        Log.d(TAG, "save title: " + actualTitle);

        final String serviceLabel = InstrumentedAutoFillService.class.getSimpleName();
        switch (types.length) {
            case 1:
                final String expectedTitle = (types[0] == SAVE_DATA_TYPE_GENERIC)
                        ? Html.fromHtml(getString(RESOURCE_STRING_SAVE_TITLE,
                                serviceLabel), 0).toString()
                        : Html.fromHtml(getString(RESOURCE_STRING_SAVE_TITLE_WITH_TYPE,
                                getSaveTypeString(types[0]), serviceLabel), 0).toString();
                assertThat(actualTitle).isEqualTo(expectedTitle);
                break;
            case 2:
                // We cannot predict the order...
                assertThat(actualTitle).contains(getSaveTypeString(types[0]));
                assertThat(actualTitle).contains(getSaveTypeString(types[1]));
                break;
            case 3:
                // We cannot predict the order...
                assertThat(actualTitle).contains(getSaveTypeString(types[0]));
                assertThat(actualTitle).contains(getSaveTypeString(types[1]));
                assertThat(actualTitle).contains(getSaveTypeString(types[2]));
                break;
            default:
                throw new IllegalArgumentException("Invalid types: " + Arrays.toString(types));
        }

        if (description != null) {
            final UiObject2 saveSubTitle = snackbar.findObject(By.text(description));
            assertWithMessage("save subtitle(%s)", description).that(saveSubTitle).isNotNull();
        }

        final String negativeButtonText = (negativeButtonStyle
                == SaveInfo.NEGATIVE_BUTTON_STYLE_REJECT) ? "NOT NOW" : "NO THANKS";
        UiObject2 negativeButton = snackbar.findObject(By.text(negativeButtonText));
        assertWithMessage("negative button (%s)", negativeButtonText)
                .that(negativeButton).isNotNull();

        final String expectedAccessibilityTitle =
                getString(RESOURCE_STRING_SAVE_SNACKBAR_ACCESSIBILITY_TITLE);
        assertAccessibilityTitle(snackbar, expectedAccessibilityTitle);

        return snackbar;
    }

    /**
     * Taps an option in the save snackbar.
     *
     * @param yesDoIt {@code true} for 'YES', {@code false} for 'NO THANKS'.
     * @param types expected types of save info.
     */
    void saveForAutofill(boolean yesDoIt, int... types) {
        final UiObject2 saveSnackBar = assertSaveShowing(
                SaveInfo.NEGATIVE_BUTTON_STYLE_CANCEL, null, types);
        saveForAutofill(saveSnackBar, yesDoIt);
    }

    /**
     * Taps an option in the save snackbar.
     *
     * @param yesDoIt {@code true} for 'YES', {@code false} for 'NO THANKS'.
     * @param types expected types of save info.
     */
    void saveForAutofill(int negativeButtonStyle, boolean yesDoIt, int... types) {
        final UiObject2 saveSnackBar = assertSaveShowing(negativeButtonStyle,null, types);
        saveForAutofill(saveSnackBar, yesDoIt);
    }

    /**
     * Taps an option in the save snackbar.
     *
     * @param saveSnackBar Save snackbar, typically obtained through
     *            {@link #assertSaveShowing(int)}.
     * @param yesDoIt {@code true} for 'YES', {@code false} for 'NO THANKS'.
     */
    void saveForAutofill(UiObject2 saveSnackBar, boolean yesDoIt) {
        final String id = yesDoIt ? "autofill_save_yes" : "autofill_save_no";

        final UiObject2 button = saveSnackBar.findObject(By.res("android", id));
        assertWithMessage("save button (%s)", id).that(button).isNotNull();
        button.click();
    }

    /**
     * Gets the AUTOFILL contextual menu by long pressing a text field.
     *
     * <p><b>NOTE:</b> this method should only be called in scenarios where we explicitly want to
     * test the overflow menu. For all other scenarios where we want to test manual autofill, it's
     * better to call {@code AFM.requestAutofill()} directly, because it's less error-prone and
     * faster.
     *
     * @param id resource id of the field.
     */
    UiObject2 getAutofillMenuOption(String id) {
        final UiObject2 field = waitForObject(By.res(mPackageName, id));
        // TODO: figure out why obj.longClick() doesn't always work
        field.click(3000);

        final List<UiObject2> menuItems = waitForObjects(
                By.res("android", RESOURCE_ID_CONTEXT_MENUITEM));
        final String expectedText = getString(RESOURCE_STRING_AUTOFILL);
        final StringBuffer menuNames = new StringBuffer();
        for (UiObject2 menuItem : menuItems) {
            final String menuName = menuItem.getText();
            if (menuName.equalsIgnoreCase(expectedText)) {
                return menuItem;
            }
            menuNames.append("'").append(menuName).append("' ");
        }
        throw new RetryableException("no '%s' on '%s'", expectedText, menuNames);
    }

    /**
     * Gets a string from the Android resources.
     */
    private String getString(String id) {
        final Resources resources = mContext.getResources();
        final int stringId = resources.getIdentifier(id, "string", "android");
        return resources.getString(stringId);
    }

    /**
     * Gets a string from the Android resources.
     */
    private String getString(String id, Object... formatArgs) {
        final Resources resources = mContext.getResources();
        final int stringId = resources.getIdentifier(id, "string", "android");
        return resources.getString(stringId, formatArgs);
    }

    /**
     * Waits for and returns an object.
     *
     * @param selector {@link BySelector} that identifies the object.
     */
    private UiObject2 waitForObject(BySelector selector) {
        return waitForObject(selector, UI_TIMEOUT_MS);
    }

    /**
     * Waits for and returns an object.
     *
     * @param parent where to find the object (or {@code null} to use device's root).
     * @param selector {@link BySelector} that identifies the object.
     * @param timeout timeout in ms
     */
    private UiObject2 waitForObject(UiObject2 parent, BySelector selector, long timeout) {
        // NOTE: mDevice.wait does not work for the save snackbar, so we need a polling approach.
        final int maxTries = 5;
        final long napTime = timeout / maxTries;
        for (int i = 1; i <= maxTries; i++) {
            final UiObject2 uiObject = parent != null
                    ? parent.findObject(selector)
                    : mDevice.findObject(selector);
            if (uiObject != null) {
                return uiObject;
            }
            SystemClock.sleep(napTime);
        }
        throw new RetryableException("Object with selector '%s' not found in %d ms",
                selector, UI_TIMEOUT_MS);

    }

    /**
     * Waits for and returns an object.
     *
     * @param selector {@link BySelector} that identifies the object.
     * @param timeout timeout in ms
     */
    private UiObject2 waitForObject(BySelector selector, long timeout) {
        return waitForObject(null, selector, timeout);
    }

    /**
     * Waits for and returns a list of objects.
     *
     * @param selector {@link BySelector} that identifies the object.
     */
    private List<UiObject2> waitForObjects(BySelector selector) {
        return waitForObjects(selector, UI_TIMEOUT_MS);
    }

    /**
     * Waits for and returns a list of objects.
     *
     * @param selector {@link BySelector} that identifies the object.
     * @param timeout timeout in ms
     */
    private List<UiObject2> waitForObjects(BySelector selector, long timeout) {
        // NOTE: mDevice.wait does not work for the save snackbar, so we need a polling approach.
        final int maxTries = 5;
        final long napTime = timeout / maxTries;
        for (int i = 1; i <= maxTries; i++) {
            final List<UiObject2> uiObjects = mDevice.findObjects(selector);
            if (uiObjects != null && !uiObjects.isEmpty()) {
                return uiObjects;
            }
            SystemClock.sleep(napTime);
        }
        throw new RetryableException("Objects with selector '%s' not found in %d ms",
                selector, UI_TIMEOUT_MS);
    }

    private UiObject2 findDatasetPicker() {
        return findDatasetPicker(UI_TIMEOUT_MS);
    }

    private UiObject2 findDatasetPicker(long timeout) {
        final UiObject2 picker = waitForObject(By.res("android", RESOURCE_ID_DATASET_PICKER),
                timeout);

        final String expectedTitle = getString(RESOURCE_STRING_DATASET_PICKER_ACCESSIBILITY_TITLE);
        assertAccessibilityTitle(picker, expectedTitle);

        return picker;
    }

    /**
     * Asserts a given object has the expected accessibility title.
     */
    private void assertAccessibilityTitle(UiObject2 object, String expectedTitle) {
        // TODO: ideally it should get the AccessibilityWindowInfo from the object, but UiAutomator
        // does not expose that.
        for (AccessibilityWindowInfo window : mAutoman.getWindows()) {
            final CharSequence title = window.getTitle();
            if (title != null && title.toString().equals(expectedTitle)) {
                return;
            }
        }
        throw new RetryableException("Title '%s' not found for %s", expectedTitle, object);
    }

    /**
     * Sets the the screen orientation.
     *
     * @param orientation typically {@link #LANDSCAPE} or {@link #PORTRAIT}.
     *
     * @throws RetryableException if value didn't change.
     */
    public void setScreenOrientation(int orientation) {
        mAutoman.setRotation(orientation);

        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime <= Helper.UI_SCREEN_ORIENTATION_TIMEOUT_MS) {
            final int actualValue = getScreenOrientation();
            if (actualValue == orientation) {
                return;
            }
            Log.w(TAG, "setScreenOrientation(): sleeping " + Helper.RETRY_MS
                    + "ms until orientation is " + orientation
                    + " (instead of " + actualValue + ")");
            try {
                Thread.sleep(Helper.RETRY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new RetryableException("Screen orientation didn't change to %d in %d ms", orientation,
                Helper.UI_SCREEN_ORIENTATION_TIMEOUT_MS);
    }

    /**
     * Gets the value of the screen orientation.
     *
     * @return typically {@link #LANDSCAPE} or {@link #PORTRAIT}.
     */
    public int getScreenOrientation() {
        return mDevice.getDisplayRotation();
    }

    /**
     * Dumps the current view hierarchy int the output stream.
     */
    public void dumpScreen() throws IOException {
        mDevice.dumpWindowHierarchy(System.out);
    }
}
