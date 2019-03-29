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

import static android.autofillservice.cts.Helper.ID_PASSWORD;
import static android.autofillservice.cts.Helper.ID_USERNAME;
import static android.autofillservice.cts.Helper.assertNoDanglingSessions;
import static android.autofillservice.cts.Helper.getContext;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.service.autofill.CharSequenceTransformation;
import android.service.autofill.CustomDescription;
import android.service.autofill.ImageTransformation;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.view.View;
import android.view.autofill.AutofillId;
import android.widget.RemoteViews;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class CustomDescriptionTest extends AutoFillServiceTestCase {
    @Rule
    public final AutofillActivityTestRule<LoginActivity> mActivityRule =
        new AutofillActivityTestRule<>(LoginActivity.class);

    private LoginActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @After
    public void finishWelcomeActivity() {
        WelcomeActivity.finishIt();
    }

    /**
     * Base test
     *
     * @param descriptionBuilder method to build a custom description
     * @param uiVerifier         Ran when the custom description is shown
     */
    private void testCustomDescription(
            @NonNull BiFunction<AutofillId, AutofillId, CustomDescription> descriptionBuilder,
            @Nullable Runnable uiVerifier) throws Exception {
        enableService();

        final AutofillId usernameId = mActivity.getUsername().getAutofillId();
        final AutofillId passwordId = mActivity.getPassword().getAutofillId();

        // Set response with custom description
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_USERNAME, ID_PASSWORD)
                .setCustomDescription(descriptionBuilder.apply(usernameId, passwordId))
                .build());

        // Trigger autofill with custom description
        mActivity.onPassword(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();

        // Trigger save.
        mActivity.onUsername((v) -> v.setText("usernm"));
        mActivity.onPassword((v) -> v.setText("passwd"));
        mActivity.tapLogin();

        if (uiVerifier != null) {
            uiVerifier.run();
        }

        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_GENERIC);
        sReplier.getNextSaveRequest();

        assertNoDanglingSessions();
    }

    @Test
    public void validTransformation() throws Exception {
        testCustomDescription((usernameId, passwordId) -> {
            RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                    R.layout.two_horizontal_text_fields);

            CharSequenceTransformation trans1 = new CharSequenceTransformation
                    .Builder(usernameId, Pattern.compile("(.*)"), "$1")
                    .addField(passwordId, Pattern.compile(".*(..)"), "..$1")
                    .build();
            ImageTransformation trans2 = new ImageTransformation
                    .Builder(usernameId, Pattern.compile(".*"),
                    R.drawable.android).build();

            return new CustomDescription.Builder(presentation)
                    .addChild(R.id.first, trans1)
                    .addChild(R.id.img, trans2)
                    .build();
        }, () -> assertSaveUiWithCustomDescriptionIsShown("usernm..wd"));
    }

    @Test
    public void badImageTransformation() throws Exception {
        testCustomDescription((usernameId, passwordId) -> {
            RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                    R.layout.two_horizontal_text_fields);

            ImageTransformation trans = new ImageTransformation
                    .Builder(usernameId, Pattern.compile(".*"), 1)
                    .build();

            return new CustomDescription.Builder(presentation)
                    .addChild(R.id.img, trans)
                    .build();
        }, () -> assertSaveUiWithCustomDescriptionIsShown() );
    }

    @Test
    public void unusedImageTransformation() throws Exception {
        testCustomDescription((usernameId, passwordId) -> {
            RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                    R.layout.two_horizontal_text_fields);

            ImageTransformation trans = new ImageTransformation
                    .Builder(usernameId, Pattern.compile("invalid"), R.drawable.android)
                    .build();

            return new CustomDescription.Builder(presentation)
                    .addChild(R.id.img, trans)
                    .build();
        }, () -> assertSaveUiWithCustomDescriptionIsShown());
    }

    @Test
    public void applyImageTransformationToTextView() throws Exception {
        testCustomDescription((usernameId, passwordId) -> {
            RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                    R.layout.two_horizontal_text_fields);

            ImageTransformation trans = new ImageTransformation
                    .Builder(usernameId, Pattern.compile(".*"), R.drawable.android)
                    .build();

            return new CustomDescription.Builder(presentation)
                    .addChild(R.id.first, trans)
                    .build();
        }, () -> assertSaveUiWithoutCustomDescriptionIsShown());
    }

    @Test
    public void failFirstFailAll() throws Exception {
        testCustomDescription((usernameId, passwordId) -> {
            RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                    R.layout.two_horizontal_text_fields);

            CharSequenceTransformation trans = new CharSequenceTransformation
                    .Builder(usernameId, Pattern.compile("(.*)"), "$42")
                    .addField(passwordId, Pattern.compile(".*(..)"), "..$1")
                    .build();

            return new CustomDescription.Builder(presentation)
                    .addChild(R.id.first, trans)
                    .build();
        }, () -> assertSaveUiWithoutCustomDescriptionIsShown());
    }

    @Test
    public void failSecondFailAll() throws Exception {
        testCustomDescription((usernameId, passwordId) -> {
            RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                    R.layout.two_horizontal_text_fields);

            CharSequenceTransformation trans = new CharSequenceTransformation
                    .Builder(usernameId, Pattern.compile("(.*)"), "$1")
                    .addField(passwordId, Pattern.compile(".*(..)"), "..$42")
                    .build();

            return new CustomDescription.Builder(presentation)
                    .addChild(R.id.first, trans)
                    .build();
        }, () -> assertSaveUiWithoutCustomDescriptionIsShown());
    }

    @Test
    public void applyCharSequenceTransformationToImageView() throws Exception {
        testCustomDescription((usernameId, passwordId) -> {
            RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                    R.layout.two_horizontal_text_fields);

            CharSequenceTransformation trans = new CharSequenceTransformation
                    .Builder(usernameId, Pattern.compile("(.*)"), "$1")
                    .build();

            return new CustomDescription.Builder(presentation)
                    .addChild(R.id.img, trans)
                    .build();
        }, () -> assertSaveUiWithoutCustomDescriptionIsShown());
    }

    private void multipleTransformationsForSameFieldTest(boolean matchFirst) throws Exception {
        enableService();

        // Set response with custom description
        final AutofillId usernameId = mActivity.getUsername().getAutofillId();
        final CharSequenceTransformation firstTrans = new CharSequenceTransformation
                .Builder(usernameId, Pattern.compile("(marco)"), "polo")
                .build();
        final CharSequenceTransformation secondTrans = new CharSequenceTransformation
                .Builder(usernameId, Pattern.compile("(MARCO)"), "POLO")
                .build();
        final RemoteViews presentation = new RemoteViews(getContext().getPackageName(),
                R.layout.two_horizontal_text_fields);
        final CustomDescription customDescription = new CustomDescription.Builder(presentation)
                .addChild(R.id.first, firstTrans)
                .addChild(R.id.first, secondTrans)
                .build();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_USERNAME)
                .setCustomDescription(customDescription)
                .build());

        // Trigger autofill with custom description
        mActivity.onPassword(View::requestFocus);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();

        // Trigger save.
        final String username = matchFirst ? "marco" : "MARCO";
        mActivity.onUsername((v) -> v.setText(username));
        mActivity.onPassword((v) -> v.setText(LoginActivity.BACKDOOR_PASSWORD_SUBSTRING));
        mActivity.tapLogin();

        final String expectedText = matchFirst ? "polo" : "POLO";
        assertSaveUiWithCustomDescriptionIsShown(expectedText);
    }

    @Test
    public void applyMultipleTransformationsForSameField_matchFirst() throws Exception {
        multipleTransformationsForSameFieldTest(true);
    }

    @Test
    public void applyMultipleTransformationsForSameField_matchSecond() throws Exception {
        multipleTransformationsForSameFieldTest(false);
    }

    private void assertSaveUiWithoutCustomDescriptionIsShown() {
        // First make sure the UI is shown...
        final UiObject2 saveUi = sUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);

        // Then make sure it does not have the custom view on it.
        assertWithMessage("found static_text on SaveUI (%s)", sUiBot.getChildrenAsText(saveUi))
            .that(saveUi.findObject(By.res(mPackageName, "static_text"))).isNull();
    }

    private UiObject2 assertSaveUiWithCustomDescriptionIsShown() {
        // First make sure the UI is shown...
        final UiObject2 saveUi = sUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);

        // Then make sure it does have the custom view on it...
        final UiObject2 staticText = saveUi.findObject(By.res(mPackageName, "static_text"));
        assertThat(staticText).isNotNull();
        assertThat(staticText.getText()).isEqualTo("YO:");

        return saveUi;
    }

    private void assertSaveUiWithCustomDescriptionIsShown(String expectedText) {
        final UiObject2 saveUi = assertSaveUiWithCustomDescriptionIsShown();
        assertWithMessage("didn't find '%s' on SaveUI (%s)", expectedText,
                sUiBot.getChildrenAsText(saveUi))
                        .that(saveUi.findObject(By.text(expectedText))).isNotNull();
    }
}
