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

import static android.autofillservice.cts.Helper.findNodeByResourceId;

import static com.google.common.truth.Truth.assertThat;

import android.app.assist.AssistStructure;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.view.autofill.AutofillValue;
import android.widget.EditText;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class ViewAttributesTest extends AutoFillServiceTestCase {
    @Rule
    public final AutofillActivityTestRule<ViewAttributesTestActivity> mActivityRule =
            new AutofillActivityTestRule<>(ViewAttributesTestActivity.class);

    private ViewAttributesTestActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @Nullable private String[] getHintsFromView(@IdRes int resId) {
        return mActivity.findViewById(resId).getAutofillHints();
    }

    private void checkEditTextNoHint(@Nullable String[] hints) {
        assertThat(hints).isNull();
    }

    private void checkEditTextHintCustom(@Nullable String[] hints) {
        assertThat(hints).isEqualTo(
                new String[] {mActivity.getString(R.string.new_password_label)});
    }

    private void checkEditTextPassword(@Nullable String[] hints) {
        assertThat(hints).isEqualTo(new String[] {View.AUTOFILL_HINT_PASSWORD});
    }

    private void checkEditTextPhoneName(@Nullable String[] hints) {
        assertThat(hints).isEqualTo(
                new String[] {View.AUTOFILL_HINT_PHONE, View.AUTOFILL_HINT_USERNAME});
    }

    private void checkEditTextHintsFromArray(@Nullable String[] hints) {
        assertThat(hints).isEqualTo(new String[] {"yesterday", "today", "tomorrow", "never"});
    }

    @Test
    public void checkViewHints() {
        checkEditTextNoHint(getHintsFromView(R.id.editTextNoHint));
        checkEditTextHintCustom(getHintsFromView(R.id.editTextHintCustom));
        checkEditTextPassword(getHintsFromView(R.id.editTextPassword));
        checkEditTextPhoneName(getHintsFromView(R.id.editTextPhoneName));
        checkEditTextHintsFromArray(getHintsFromView(R.id.editTextHintsFromArray));
    }

    @Test
    public void checkSetAutoFill() {
        View v = mActivity.findViewById(R.id.editTextNoHint);

        v.setAutofillHints((String[]) null);
        assertThat(v.getAutofillHints()).isNull();

        v.setAutofillHints(new String[0]);
        assertThat(v.getAutofillHints()).isNull();

        v.setAutofillHints(new String[]{View.AUTOFILL_HINT_PASSWORD});
        assertThat(v.getAutofillHints()).isEqualTo(new String[]{View.AUTOFILL_HINT_PASSWORD});

        v.setAutofillHints(new String[]{"custom", "value"});
        assertThat(v.getAutofillHints()).isEqualTo(new String[]{"custom", "value"});

        v.setAutofillHints("more", "values");
        assertThat(v.getAutofillHints()).isEqualTo(new String[]{"more", "values"});

        v.setAutofillHints(
                new String[]{View.AUTOFILL_HINT_PASSWORD, View.AUTOFILL_HINT_EMAIL_ADDRESS});
        assertThat(v.getAutofillHints()).isEqualTo(new String[]{View.AUTOFILL_HINT_PASSWORD,
                View.AUTOFILL_HINT_EMAIL_ADDRESS});
    }

    /**
     * Wait for autofill to be initialized and trigger autofill on a view.
     *
     * @param view The view to trigger the autofill on
     *
     * @return The {@link InstrumentedAutoFillService.FillRequest triggered}
     */
    private InstrumentedAutoFillService.FillRequest startAutoFill(boolean forceAutofill,
            @NonNull View view) throws Exception {
        if (forceAutofill) {
            mActivity.getAutofillManager().requestAutofill(view);
        } else {
            mActivity.syncRunOnUiThread(() -> {
                view.clearFocus();
                view.requestFocus();
            });
        }

        InstrumentedAutoFillService.waitUntilConnected();

        return sReplier.getNextFillRequest();
    }

    @Nullable private String[] getHintsFromStructure(@NonNull AssistStructure structure,
            @NonNull String resName) {
        return findNodeByResourceId(structure, resName).getAutofillHints();
    }

    private void onAssistStructure(boolean forceAutofill, @NonNull Consumer<AssistStructure> test)
            throws Exception {
        EditText editTextNoHint = mActivity.findViewById(R.id.editTextNoHint);
        mActivity.syncRunOnUiThread(() -> editTextNoHint.setVisibility(View.VISIBLE));

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField("editTextNoHint", AutofillValue.forText("filled"))
                .setPresentation(createPresentation("dataset"))
                .build());

        // Trigger autofill.
        InstrumentedAutoFillService.FillRequest request = startAutoFill(forceAutofill,
                editTextNoHint);

        assertThat(request.contexts.size()).isEqualTo(1);
        test.accept(request.contexts.get(0).getStructure());
    }

    @Test
    public void checkAssistStructureHints() throws Exception {
        onAssistStructure(false, (structure) -> {
                    // Check autofill hints analogue to #checkViewHints
                    checkEditTextNoHint(getHintsFromStructure(structure, "editTextNoHint"));
                    checkEditTextHintCustom(getHintsFromStructure(structure, "editTextHintCustom"));
                    checkEditTextPassword(getHintsFromStructure(structure, "editTextPassword"));
                    checkEditTextPhoneName(getHintsFromStructure(structure, "editTextPhoneName"));
                    checkEditTextHintsFromArray(getHintsFromStructure(structure,
                            "editTextHintsFromArray"));
                }
        );
    }

    @Test
    public void checkViewLocationInAssistStructure() throws Exception {
        onAssistStructure(false, (structure) -> {
                    // check size of outerView
                    AssistStructure.ViewNode outerView = findNodeByResourceId(structure,
                            "outerView");

                    // The size of the view should include all paddings and size of all children
                    assertThat(outerView.getHeight()).isEqualTo(
                            2             // outerView.top
                                    + 11  // nestedView.top
                                    + 23  // doubleNestedView.top
                                    + 41  // tripleNestedView.height
                                    + 47  // secondDoubleNestedView.height
                                    + 31  // doubleNestedView.bottom
                                    + 17  // nestedView.bottom
                                    + 5); // outerView.bottom
                    assertThat(outerView.getWidth()).isEqualTo(
                            7                     // outerView.left
                                    + 19          // nestedView.left
                                    + Math.max(37 // doubleNestedView.left
                                            + 43  // tripleNestedView.width
                                            + 29, // doubleNestedView.right
                                    53)           // secondDoubleNestedView.width
                                    + 13          // nestedView.right
                                    + 3);         // outerView.right


                    // The nestedView is suppressed, hence the structure should be
                    //
                    // outerView
                    //  doubleNestedView left=26 top=13
                    //   tripleNestedView left=37 top=23
                    //  secondDoubleNestedView left=26 top=108

                    assertThat(outerView.getChildCount()).isEqualTo(2);
                    AssistStructure.ViewNode doubleNestedView;
                    AssistStructure.ViewNode secondDoubleNestedView;
                    if (outerView.getChildAt(0).getIdEntry().equals("doubleNestedView")) {
                        doubleNestedView = outerView.getChildAt(0);
                        secondDoubleNestedView = outerView.getChildAt(1);
                    } else {
                        secondDoubleNestedView = outerView.getChildAt(0);
                        doubleNestedView = outerView.getChildAt(1);
                    }
                    assertThat(doubleNestedView.getIdEntry()).isEqualTo("doubleNestedView");
                    assertThat(secondDoubleNestedView.getIdEntry()).isEqualTo
                            ("secondDoubleNestedView");

                    // The location of the doubleNestedView should include all suppressed parent's
                    // offset
                    assertThat(doubleNestedView.getLeft()).isEqualTo(
                            7              // outerView.left
                                    + 19); // nestedView.left
                    assertThat(doubleNestedView.getTop()).isEqualTo(
                            2              // outerView.top
                                    + 11); // nestedView.top

                    // The location of the tripleNestedView should be relative to it's parent
                    assertThat(doubleNestedView.getChildCount()).isEqualTo(1);
                    AssistStructure.ViewNode tripleNestedView = doubleNestedView.getChildAt(0);
                    assertThat(doubleNestedView.getIdEntry()).isEqualTo("doubleNestedView");

                    assertThat(tripleNestedView.getLeft()).isEqualTo(37); // doubleNestedView.left
                    assertThat(tripleNestedView.getTop()).isEqualTo(23); // doubleNestedView.top
                }
        );
    }

    @Test
    public void checkViewLocationInAssistStructureAfterForceAutofill() throws Exception {
        onAssistStructure(true, (structure) -> {
                    AssistStructure.ViewNode outerView = findNodeByResourceId(structure,
                            "outerView");

                    // The structure should be
                    //
                    // outerView
                    //  nestedView left=7 top=2
                    //   doubleNestedView left=19 top=11
                    //    tripleNestedView left=37 top=23
                    //   secondDoubleNestedView left=19 top=106

                    // Test only what is different from #checkViewLocationInAssistStructure
                    assertThat(outerView.getChildCount()).isEqualTo(1);

                    AssistStructure.ViewNode nestedView = outerView.getChildAt(0);
                    assertThat(nestedView.getIdEntry()).isEqualTo("nestedView");
                    assertThat(nestedView.getLeft()).isEqualTo(7); // outerView.left
                    assertThat(nestedView.getTop()).isEqualTo(2); // outerView.top

                    assertThat(nestedView.getChildCount()).isEqualTo(2);
                    AssistStructure.ViewNode doubleNestedView;
                    if (nestedView.getChildAt(0).getIdEntry().equals("doubleNestedView")) {
                        doubleNestedView = nestedView.getChildAt(0);
                    } else {
                        doubleNestedView = nestedView.getChildAt(1);
                    }
                    assertThat(doubleNestedView.getIdEntry()).isEqualTo("doubleNestedView");

                    assertThat(doubleNestedView.getLeft()).isEqualTo(19); // nestedView.left
                    assertThat(doubleNestedView.getTop()).isEqualTo(11); // nestedView.top
                }
        );
    }
}
