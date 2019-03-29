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

import static android.autofillservice.cts.Helper.runShellCommand;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;

import static com.google.common.truth.Truth.assertThat;

import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.support.test.uiautomator.UiObject2;
import android.view.ViewStructure.HtmlInfo;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class WebViewActivityTest extends AutoFillServiceTestCase {

    // TODO(b/64951517): WebView currently does not trigger the autofill callbacks when values are
    // set using accessibility.
    private static final boolean INJECT_EVENTS = true;

    @Rule
    public final AutofillActivityTestRule<WebViewActivity> mActivityRule =
            new AutofillActivityTestRule<WebViewActivity>(WebViewActivity.class);

    private WebViewActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @BeforeClass
    public static void setReplierMode() {
        sReplier.setIdMode(IdMode.HTML_NAME);
    }

    @AfterClass
    public static void resetReplierMode() {
        sReplier.setIdMode(IdMode.RESOURCE_ID);
    }

    @Test
    public void testAutofillNoDatasets() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(CannedFillResponse.NO_RESPONSE);

        // Trigger autofill.
        mActivity.getUsernameInput(sUiBot).click();
        sReplier.getNextFillRequest();

        // Assert not shown.
        sUiBot.assertNoDatasets();
    }

    @Test
    public void testAutofillOneDataset() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final MyAutofillCallback callback = mActivity.registerCallback();
        sReplier.addResponse(new CannedDataset.Builder()
                .setField("username", "dude")
                .setField("password", "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());

        // Trigger autofill.
        mActivity.getUsernameInput(sUiBot).click();
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        sUiBot.assertDatasets("The Dude");

        // Change focus around.
        final int usernameChildId = callback.assertUiShownEventForVirtualChild(mActivity.mWebView);
        mActivity.getUsernameLabel(sUiBot).click();
        callback.assertUiHiddenEvent(mActivity.mWebView, usernameChildId);
        sUiBot.assertNoDatasets();
        mActivity.getPasswordInput(sUiBot).click();
        final int passwordChildId = callback.assertUiShownEventForVirtualChild(mActivity.mWebView);
        final UiObject2 datasetPicker = sUiBot.assertDatasets("The Dude");

        // Now Autofill it.
        sUiBot.selectDataset(datasetPicker, "The Dude");
        sUiBot.assertNoDatasets();
        callback.assertUiHiddenEvent(mActivity.mWebView, passwordChildId);

        // Make sure screen was autofilled.
        assertThat(mActivity.getUsernameInput(sUiBot).getText()).isEqualTo("dude");
        // TODO: proper way to verify text (which is ..... because it's a password) - ideally it
        // should call passwordInput.isPassword(), but that's not exposed
        final String password = mActivity.getPasswordInput(sUiBot).getText();
        assertThat(password).isNotEqualTo("sweet");
        assertThat(password).hasLength(5);

        // Assert structure passed to service.
        try {
            final ViewNode webViewNode = Helper.findWebViewNode(fillRequest.structure, "FORM AM I");
            // TODO(b/66953802): class name should be android.webkit.WebView, and form name should
            // be inside HtmlInfo, but Chromium 61 does not implement that.
            if (webViewNode.getClassName().equals("android.webkit.WebView")) {
                final HtmlInfo htmlInfo = Helper.assertHasHtmlTag(webViewNode, "form");
                Helper.assertHasAttribute(htmlInfo, "name", "FORM AM I");
            } else {
                assertThat(webViewNode.getClassName()).isEqualTo("FORM AM I");
                assertThat(webViewNode.getHtmlInfo()).isNull();
            }
            assertThat(webViewNode.getWebDomain()).isEqualTo(WebViewActivity.FAKE_DOMAIN);

            final ViewNode usernameNode =
                    Helper.findNodeByHtmlName(fillRequest.structure, "username");
            Helper.assertTextIsSanitized(usernameNode);
            final HtmlInfo usernameHtmlInfo = Helper.assertHasHtmlTag(usernameNode, "input");
            Helper.assertHasAttribute(usernameHtmlInfo, "type", "text");
            Helper.assertHasAttribute(usernameHtmlInfo, "name", "username");
            assertThat(usernameNode.isFocused()).isTrue();
            assertThat(usernameNode.getAutofillHints()).asList().containsExactly("username");
            assertThat(usernameNode.getHint()).isEqualTo("There's no place like a holder");

            final ViewNode passwordNode =
                    Helper.findNodeByHtmlName(fillRequest.structure, "password");
            Helper.assertTextIsSanitized(passwordNode);
            final HtmlInfo passwordHtmlInfo = Helper.assertHasHtmlTag(passwordNode, "input");
            Helper.assertHasAttribute(passwordHtmlInfo, "type", "password");
            Helper.assertHasAttribute(passwordHtmlInfo, "name", "password");
            assertThat(passwordNode.getAutofillHints()).asList()
                    .containsExactly("current-password");
            assertThat(passwordNode.getHint()).isEqualTo("Holder it like it cannnot passer a word");
            assertThat(passwordNode.isFocused()).isFalse();
        } catch (RuntimeException | Error e) {
            Helper.dumpStructure("failed on testAutofillOneDataset()", fillRequest.structure);
            throw e;
        }
    }

    @Test
    public void testSaveOnly() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, "username", "password")
                .build());

        // Trigger autofill.
        mActivity.getUsernameInput(sUiBot).click();
        sReplier.getNextFillRequest();

        // Assert not shown.
        sUiBot.assertNoDatasets();

        // Trigger save.
        if (INJECT_EVENTS) {
            mActivity.getUsernameInput(sUiBot).click();
            runShellCommand("input keyevent KEYCODE_U");
            mActivity.getPasswordInput(sUiBot).click();
            runShellCommand("input keyevent KEYCODE_P");
        } else {
            mActivity.getUsernameInput(sUiBot).setText("DUDE");
            mActivity.getPasswordInput(sUiBot).setText("SWEET");
        }
        mActivity.getLoginButton(sUiBot).click();

        // Assert save UI shown.
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

        // Assert results
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        final ViewNode usernameNode = Helper.findNodeByHtmlName(saveRequest.structure, "username");
        final ViewNode passwordNode = Helper.findNodeByHtmlName(saveRequest.structure, "password");
        if (INJECT_EVENTS) {
            Helper.assertTextAndValue(usernameNode, "u");
            Helper.assertTextAndValue(passwordNode, "p");
        } else {
            Helper.assertTextAndValue(usernameNode, "DUDE");
            Helper.assertTextAndValue(passwordNode, "SWEET");
        }
    }

    @Test
    public void testAutofillAndSave() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final MyAutofillCallback callback = mActivity.registerCallback();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, "username", "password")
                .addDataset(new CannedDataset.Builder()
                        .setField("username", "dude")
                        .setField("password", "sweet")
                        .setPresentation(createPresentation("The Dude"))
                        .build())
                .build());

        // Trigger autofill.
        mActivity.getUsernameInput(sUiBot).click();
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        sUiBot.assertDatasets("The Dude");
        final int usernameChildId = callback.assertUiShownEventForVirtualChild(mActivity.mWebView);

        // Assert structure passed to service.
        final ViewNode usernameNode = Helper.findNodeByHtmlName(fillRequest.structure, "username");
        Helper.assertTextIsSanitized(usernameNode);
        assertThat(usernameNode.isFocused()).isTrue();
        assertThat(usernameNode.getAutofillHints()).asList().containsExactly("username");
        final ViewNode passwordNode = Helper.findNodeByHtmlName(fillRequest.structure, "password");
        Helper.assertTextIsSanitized(passwordNode);
        assertThat(passwordNode.getAutofillHints()).asList().containsExactly("current-password");
        assertThat(passwordNode.isFocused()).isFalse();

        // Autofill it.
        sUiBot.selectDataset("The Dude");
        callback.assertUiHiddenEvent(mActivity.mWebView, usernameChildId);

        // Make sure screen was autofilled.
        assertThat(mActivity.getUsernameInput(sUiBot).getText()).isEqualTo("dude");
        // TODO: proper way to verify text (which is ..... because it's a password) - ideally it
        // should call passwordInput.isPassword(), but that's not exposed
        final String password = mActivity.getPasswordInput(sUiBot).getText();
        assertThat(password).isNotEqualTo("sweet");
        assertThat(password).hasLength(5);

        // Now trigger save.
        if (INJECT_EVENTS) {
            mActivity.getUsernameInput(sUiBot).click();
            runShellCommand("input keyevent KEYCODE_U");
            mActivity.getPasswordInput(sUiBot).click();
            runShellCommand("input keyevent KEYCODE_P");
        } else {
            mActivity.getUsernameInput(sUiBot).setText("DUDE");
            mActivity.getPasswordInput(sUiBot).setText("SWEET");
        }
        mActivity.getLoginButton(sUiBot).click();

        // Assert save UI shown.
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_PASSWORD);

        // Assert results
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        final ViewNode usernameNode2 = Helper.findNodeByHtmlName(saveRequest.structure, "username");
        final ViewNode passwordNode2 = Helper.findNodeByHtmlName(saveRequest.structure, "password");
        if (INJECT_EVENTS) {
            Helper.assertTextAndValue(usernameNode2, "dudeu");
            Helper.assertTextAndValue(passwordNode2, "sweetp");
        } else {
            Helper.assertTextAndValue(usernameNode2, "DUDE");
            Helper.assertTextAndValue(passwordNode2, "SWEET");
        }
    }
}
