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

import static android.autofillservice.cts.CheckoutActivity.ID_ADDRESS;
import static android.autofillservice.cts.CheckoutActivity.ID_CC_EXPIRATION;
import static android.autofillservice.cts.CheckoutActivity.ID_CC_NUMBER;
import static android.autofillservice.cts.CheckoutActivity.ID_HOME_ADDRESS;
import static android.autofillservice.cts.CheckoutActivity.ID_SAVE_CC;
import static android.autofillservice.cts.CheckoutActivity.ID_WORK_ADDRESS;
import static android.autofillservice.cts.CheckoutActivity.INDEX_ADDRESS_WORK;
import static android.autofillservice.cts.CheckoutActivity.INDEX_CC_EXPIRATION_NEVER;
import static android.autofillservice.cts.CheckoutActivity.INDEX_CC_EXPIRATION_TODAY;
import static android.autofillservice.cts.CheckoutActivity.INDEX_CC_EXPIRATION_TOMORROW;
import static android.autofillservice.cts.Helper.assertListValue;
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.assertTextIsSanitized;
import static android.autofillservice.cts.Helper.assertToggleIsSanitized;
import static android.autofillservice.cts.Helper.assertToggleValue;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.Helper.getContext;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD;
import static android.view.View.AUTOFILL_TYPE_LIST;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure.ViewNode;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.service.autofill.CharSequenceTransformation;
import android.service.autofill.CustomDescription;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.widget.ArrayAdapter;
import android.widget.RemoteViews;
import android.widget.Spinner;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Test case for an activity containing non-TextField views.
 */
public class CheckoutActivityTest extends AutoFillServiceTestCase {

    @Rule
    public final AutofillActivityTestRule<CheckoutActivity> mActivityRule =
        new AutofillActivityTestRule<CheckoutActivity>(CheckoutActivity.class);

    private CheckoutActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @After
    public void finishWelcomeActivity() {
        WelcomeActivity.finishIt();
    }

    @Test
    public void testAutofill() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setPresentation(createPresentation("ACME CC"))
                .setField(ID_CC_NUMBER, "4815162342")
                .setField(ID_CC_EXPIRATION, INDEX_CC_EXPIRATION_NEVER)
                .setField(ID_ADDRESS, 1)
                .setField(ID_SAVE_CC, true)
                .build());
        mActivity.expectAutoFill("4815162342", INDEX_CC_EXPIRATION_NEVER, R.id.work_address,
                true);

        // Trigger auto-fill.
        mActivity.onCcNumber((v) -> v.requestFocus());
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        // Assert properties of Spinner field.
        final ViewNode ccExpirationNode =
                assertTextIsSanitized(fillRequest.structure, ID_CC_EXPIRATION);
        assertThat(ccExpirationNode.getClassName()).isEqualTo(Spinner.class.getName());
        assertThat(ccExpirationNode.getAutofillType()).isEqualTo(AUTOFILL_TYPE_LIST);
        final CharSequence[] options = ccExpirationNode.getAutofillOptions();
        assertWithMessage("ccExpirationNode.getAutoFillOptions()").that(options).isNotNull();
        assertWithMessage("Wrong auto-fill options for spinner").that(options).asList()
                .containsExactly((Object [])
                        getContext().getResources().getStringArray(R.array.cc_expiration_values))
                .inOrder();

        // Auto-fill it.
        sUiBot.selectDataset("ACME CC");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testAutofillDynamicAdapter() throws Exception {
        // Set activity.
        mActivity.onCcExpiration((v) -> v.setAdapter(new ArrayAdapter<String>(getContext(),
                android.R.layout.simple_spinner_item,
                Arrays.asList("YESTERDAY", "TODAY", "TOMORROW", "NEVER"))));

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setPresentation(createPresentation("ACME CC"))
                .setField(ID_CC_NUMBER, "4815162342")
                .setField(ID_CC_EXPIRATION, INDEX_CC_EXPIRATION_NEVER)
                .setField(ID_ADDRESS, 1)
                .setField(ID_SAVE_CC, true)
                .build());
        mActivity.expectAutoFill("4815162342", INDEX_CC_EXPIRATION_NEVER, R.id.work_address,
                true);

        // Trigger auto-fill.
        mActivity.onCcNumber((v) -> v.requestFocus());
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        // Assert properties of Spinner field.
        final ViewNode ccExpirationNode =
                assertTextIsSanitized(fillRequest.structure, ID_CC_EXPIRATION);
        assertThat(ccExpirationNode.getClassName()).isEqualTo(Spinner.class.getName());
        assertThat(ccExpirationNode.getAutofillType()).isEqualTo(AUTOFILL_TYPE_LIST);
        final CharSequence[] options = ccExpirationNode.getAutofillOptions();
        assertWithMessage("ccExpirationNode.getAutoFillOptions()").that(options).isNull();

        // Auto-fill it.
        sUiBot.selectDataset("ACME CC");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    // TODO: this should be a pure unit test exercising onProvideAutofillStructure(),
    // but that would require creating a custom ViewStructure.
    @Test
    public void testGetAutofillOptionsSorted() throws Exception {
        // Set service.
        enableService();

        // Set activity.
        mActivity.onCcExpirationAdapter((adapter) -> adapter.sort((a, b) -> {
            return ((String) a).compareTo((String) b);
        }));

        // Set expectations.
        sReplier.addResponse(new CannedDataset.Builder()
                .setPresentation(createPresentation("ACME CC"))
                .setField(ID_CC_NUMBER, "4815162342")
                .setField(ID_CC_EXPIRATION, INDEX_CC_EXPIRATION_NEVER)
                .setField(ID_ADDRESS, 1)
                .setField(ID_SAVE_CC, true)
                .build());
        mActivity.expectAutoFill("4815162342", INDEX_CC_EXPIRATION_NEVER, R.id.work_address,
                true);

        // Trigger auto-fill.
        mActivity.onCcNumber((v) -> v.requestFocus());
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        // Assert properties of Spinner field.
        final ViewNode ccExpirationNode =
                assertTextIsSanitized(fillRequest.structure, ID_CC_EXPIRATION);
        assertThat(ccExpirationNode.getClassName()).isEqualTo(Spinner.class.getName());
        assertThat(ccExpirationNode.getAutofillType()).isEqualTo(AUTOFILL_TYPE_LIST);
        final CharSequence[] options = ccExpirationNode.getAutofillOptions();
        assertWithMessage("Wrong auto-fill options for spinner").that(options).asList()
                .containsExactly("never", "today", "tomorrow", "yesterday").inOrder();

        // Auto-fill it.
        sUiBot.selectDataset("ACME CC");

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testSanitization() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_CREDIT_CARD,
                        ID_CC_NUMBER, ID_CC_EXPIRATION, ID_ADDRESS, ID_SAVE_CC)
                .build());

        // Dynamically change view contents
        mActivity.onCcNumber((v) -> v.setText("108"));
        mActivity.onCcExpiration((v) -> v.setSelection(INDEX_CC_EXPIRATION_TOMORROW, true));
        mActivity.onHomeAddress((v) -> v.setChecked(true));
        mActivity.onSaveCc((v) -> v.setChecked(true));

        // Trigger auto-fill.
        mActivity.onCcNumber((v) -> v.requestFocus());

        // Assert sanitization on fill request: everything should be sanitized!
        final FillRequest fillRequest = sReplier.getNextFillRequest();

        assertTextIsSanitized(fillRequest.structure, ID_CC_NUMBER);
        assertTextIsSanitized(fillRequest.structure, ID_CC_EXPIRATION);
        assertToggleIsSanitized(fillRequest.structure, ID_HOME_ADDRESS);
        assertToggleIsSanitized(fillRequest.structure, ID_SAVE_CC);

        // Trigger save.
        mActivity.onCcNumber((v) -> v.setText("4815162342"));
        mActivity.onCcExpiration((v) -> v.setSelection(INDEX_CC_EXPIRATION_TODAY));
        mActivity.onAddress((v) -> v.check(R.id.work_address));
        mActivity.onSaveCc((v) -> v.setChecked(false));
        mActivity.tapBuy();
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_CREDIT_CARD);
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();

        // Assert sanitization on save: everything should be available!
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_CC_NUMBER), "4815162342");
        assertListValue(findNodeByResourceId(saveRequest.structure, ID_CC_EXPIRATION),
                INDEX_CC_EXPIRATION_TODAY);
        assertListValue(findNodeByResourceId(saveRequest.structure, ID_ADDRESS),
                INDEX_ADDRESS_WORK);
        assertToggleValue(findNodeByResourceId(saveRequest.structure, ID_HOME_ADDRESS), false);
        assertToggleValue(findNodeByResourceId(saveRequest.structure, ID_WORK_ADDRESS), true);
        assertToggleValue(findNodeByResourceId(saveRequest.structure, ID_SAVE_CC), false);
    }

    /**
     * Tests that a spinner can be used on custom save descriptions.
     */
    @Test
    public void testCustomizedSaveUi() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final String packageName = getContext().getPackageName();

        final RemoteViews presentation = new RemoteViews(packageName,
                R.layout.two_horizontal_text_fields);
        final CharSequenceTransformation trans1 = new CharSequenceTransformation
                .Builder(mActivity.getCcNumber().getAutofillId(), Pattern.compile("(.*)"), "$1")
                .build();
        final CharSequenceTransformation trans2 = new CharSequenceTransformation
                .Builder(mActivity.getCcExpiration().getAutofillId(), Pattern.compile("(.*)"), "$1")
                .build();
        final CustomDescription customDescription = new CustomDescription.Builder(presentation)
                .addChild(R.id.first, trans1)
                .addChild(R.id.second, trans2)
                .build();

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_CREDIT_CARD, ID_CC_NUMBER, ID_CC_EXPIRATION)
                .setCustomDescription(customDescription)
                .build());

        // Dynamically change view contents
        mActivity.onCcExpiration((v) -> v.setSelection(INDEX_CC_EXPIRATION_TOMORROW, true));

        // Trigger auto-fill.
        mActivity.onCcNumber((v) -> v.requestFocus());
        sReplier.getNextFillRequest();

        // Trigger save.
        mActivity.onCcNumber((v) -> v.setText("4815162342"));
        mActivity.onCcExpiration((v) -> v.setSelection(INDEX_CC_EXPIRATION_TODAY));
        mActivity.tapBuy();

        // First make sure the UI is shown...
        final UiObject2 saveUi = sUiBot.assertSaveShowing(SAVE_DATA_TYPE_CREDIT_CARD);

        // Then make sure it does have the custom views on it...
        final UiObject2 staticText = saveUi.findObject(By.res(packageName, "static_text"));
        assertThat(staticText).isNotNull();
        assertThat(staticText.getText()).isEqualTo("YO:");

        final UiObject2 number = saveUi.findObject(By.res(packageName, "first"));
        assertThat(number).isNotNull();
        assertThat(number.getText()).isEqualTo("4815162342");

        final UiObject2 expiration = saveUi.findObject(By.res(packageName, "second"));
        assertThat(expiration).isNotNull();
        assertThat(expiration.getText()).isEqualTo("today");
    }

    /**
     * Tests that a custom save description is ignored when the selected spinner element is not
     * available in the autofill options.
     */
    @Test
    public void testCustomizedSaveUiWhenListResolutionFails() throws Exception {
        // Set service.
        enableService();

        // Change spinner to return just one item so the transformation throws an exception when
        // fetching it.
        mActivity.getCcExpirationAdapter().setAutofillOptions("D'OH!");

        // Set expectations.
        final String packageName = getContext().getPackageName();
        final RemoteViews presentation = new RemoteViews(packageName,
                R.layout.two_horizontal_text_fields);
        final CharSequenceTransformation trans1 = new CharSequenceTransformation
                .Builder(mActivity.getCcNumber().getAutofillId(), Pattern.compile("(.*)"), "$1")
                .build();
        final CharSequenceTransformation trans2 = new CharSequenceTransformation
                .Builder(mActivity.getCcExpiration().getAutofillId(), Pattern.compile("(.*)"), "$1")
                .build();
        final CustomDescription customDescription = new CustomDescription.Builder(presentation)
                .addChild(R.id.first, trans1)
                .addChild(R.id.second, trans2)
                .build();

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_CREDIT_CARD, ID_CC_NUMBER, ID_CC_EXPIRATION)
                .setCustomDescription(customDescription)
                .build());

        // Dynamically change view contents
        mActivity.onCcExpiration((v) -> v.setSelection(INDEX_CC_EXPIRATION_TOMORROW, true));

        // Trigger auto-fill.
        mActivity.onCcNumber((v) -> v.requestFocus());
        sReplier.getNextFillRequest();

        // Trigger save.
        mActivity.onCcNumber((v) -> v.setText("4815162342"));
        mActivity.onCcExpiration((v) -> v.setSelection(INDEX_CC_EXPIRATION_TODAY));
        mActivity.tapBuy();

        // First make sure the UI is shown...
        final UiObject2 saveUi = sUiBot.assertSaveShowing(SAVE_DATA_TYPE_CREDIT_CARD);

        // Then make sure it does not have the custom views on it...
        assertThat(saveUi.findObject(By.res(packageName, "static_text"))).isNull();
    }
}
