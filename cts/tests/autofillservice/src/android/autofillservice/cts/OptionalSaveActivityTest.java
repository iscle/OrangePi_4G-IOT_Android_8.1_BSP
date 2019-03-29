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

import static android.autofillservice.cts.Helper.assertNoDanglingSessions;
import static android.autofillservice.cts.Helper.assertTextAndValue;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.OptionalSaveActivity.ID_ADDRESS1;
import static android.autofillservice.cts.OptionalSaveActivity.ID_ADDRESS2;
import static android.autofillservice.cts.OptionalSaveActivity.ID_CITY;
import static android.autofillservice.cts.OptionalSaveActivity.ID_FAVORITE_COLOR;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_ADDRESS;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure;
import android.autofillservice.cts.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test case for an activity that contains 4 fields, but the service is only interested in 2-3 of
 * them for Save:
 *
 * <ul>
 *   <li>Address 1: required
 *   <li>Address 2: required
 *   <li>City: optional
 *   <li>Favorite Color: don't care - LOL
 * </ul>
 */
public class OptionalSaveActivityTest extends AutoFillServiceTestCase {

    private static final boolean EXPECT_NO_SAVE_UI = false;
    private static final boolean EXPECT_SAVE_UI = false;

    @Rule
    public final AutofillActivityTestRule<OptionalSaveActivity> mActivityRule =
        new AutofillActivityTestRule<OptionalSaveActivity>(OptionalSaveActivity.class);

    private OptionalSaveActivity mActivity;

    @Before
    public void setActivity() {
        mActivity = mActivityRule.getActivity();
    }

    @After
    public void finishWelcomeActivity() {
        WelcomeActivity.finishIt();
    }

    /**
     * Creates a standard builder common to all tests.
     */
    private CannedFillResponse.Builder newResponseBuilder() {
        return new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_ADDRESS, ID_ADDRESS1, ID_CITY)
                .setOptionalSavableIds(ID_ADDRESS2);
    }

    @Test
    public void testNoAutofillSaveAll() throws Exception {
        noAutofillSaveOnChangeTest(() -> {
            mActivity.mAddress1.setText("742 Evergreen Terrace"); // required
            mActivity.mAddress2.setText("Simpsons House"); // not required
            mActivity.mCity.setText("Springfield"); // required
            mActivity.mFavoriteColor.setText("Yellow"); // lol
        }, (s) -> {
            assertTextAndValue(findNodeByResourceId(s, ID_ADDRESS1), "742 Evergreen Terrace");
            assertTextAndValue(findNodeByResourceId(s, ID_ADDRESS2), "Simpsons House");
            assertTextAndValue(findNodeByResourceId(s, ID_CITY), "Springfield");
            assertTextAndValue(findNodeByResourceId(s, ID_FAVORITE_COLOR), "Yellow");
        });
    }

    @Test
    public void testNoAutofillSaveRequiredOnly() throws Exception {
        noAutofillSaveOnChangeTest(() -> {
            mActivity.mAddress1.setText("742 Evergreen Terrace"); // required
            mActivity.mCity.setText("Springfield"); // required
        }, (s) -> {
            assertTextAndValue(findNodeByResourceId(s, ID_ADDRESS1), "742 Evergreen Terrace");
            assertTextAndValue(findNodeByResourceId(s, ID_ADDRESS2), "");
            assertTextAndValue(findNodeByResourceId(s, ID_CITY), "Springfield");
            assertTextAndValue(findNodeByResourceId(s, ID_FAVORITE_COLOR), "");
        });
    }

    /**
     * Tests the scenario where the service didn't have any data to autofill, and the user filled
     * all fields, even the favorite color (LOL).
     */
    private void noAutofillSaveOnChangeTest(Runnable changes, Visitor<AssistStructure> assertions)
            throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(newResponseBuilder().build());

        // Trigger auto-fill.
        mActivity.syncRunOnUiThread(() -> mActivity.mAddress1.requestFocus());

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started.
        sReplier.getNextFillRequest();

        // Manually fill fields...
        mActivity.syncRunOnUiThread(changes);

        // ...then tap save.
        mActivity.save();

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_ADDRESS);

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertWithMessage("onSave() not called").that(saveRequest).isNotNull();

        // Assert value of fields
        assertions.visit(saveRequest.structure);

        // Once saved, the session should be finsihed.
        assertNoDanglingSessions();
    }

    @Test
    public void testNoAutofillFirstRequiredFieldMissing() throws Exception {
        noAutofillNoChangeNoSaveTest(() -> {
            // address1 is missing
            mActivity.mAddress2.setText("Simpsons House"); // not required
            mActivity.mCity.setText("Springfield"); // required
            mActivity.mFavoriteColor.setText("Yellow"); // lol
        });
    }

    @Test
    public void testNoAutofillSecondRequiredFieldMissing() throws Exception {
        noAutofillNoChangeNoSaveTest(() -> {
            mActivity.mAddress1.setText("742 Evergreen Terrace"); // required
            mActivity.mAddress2.setText("Simpsons House"); // not required
            // city is missing
            mActivity.mFavoriteColor.setText("Yellow"); // lol
        });
    }

    /**
     * Tests the scenario where the service didn't have any data to autofill, and the user filled
     * didn't fill all required changes.
     */
    private void noAutofillNoChangeNoSaveTest(Runnable changes) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(newResponseBuilder().build());

        // Trigger auto-fill.
        mActivity.syncRunOnUiThread(() -> mActivity.mAddress1.requestFocus());

        // Sanity check.
        sUiBot.assertNoDatasets();

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started.
        sReplier.getNextFillRequest();

        // Manually fill fields...
        mActivity.syncRunOnUiThread(changes);

        // ...then tap save.
        mActivity.save();

        // Assert the snack bar is shown and tap "Save".
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_ADDRESS);

        // Once saved, the session should be finsihed.
        assertNoDanglingSessions();
    }

    @Test
    public void testAutofillAllChangedAllSaveAll() throws Exception {
        mActivity.expectAutoFill("Shelbyville Nuclear Power Plant", "Shelbyville Bluffs",
                "Shelbyville", "Lemon");
        autofillAndSaveOnChangeTest(new CannedDataset.Builder()
                // Initial dataset
                .setField(ID_ADDRESS1, "Shelbyville Nuclear Power Plant")
                .setField(ID_ADDRESS2, "Shelbyville Bluffs")
                .setField(ID_CITY, "Shelbyville")
                .setField(ID_FAVORITE_COLOR, "Lemon"),
                // Changes
                () -> {
                    mActivity.mAddress1.setText("742 Evergreen Terrace"); // required
                    mActivity.mAddress2.setText("Simpsons House"); // not required
                    mActivity.mCity.setText("Springfield"); // required
                    mActivity.mFavoriteColor.setText("Yellow"); // lol
                }, (s) -> {
                    assertTextAndValue(findNodeByResourceId(s, ID_ADDRESS1),
                            "742 Evergreen Terrace");
                    assertTextAndValue(findNodeByResourceId(s, ID_ADDRESS2), "Simpsons House");
                    assertTextAndValue(findNodeByResourceId(s, ID_CITY), "Springfield");
                    assertTextAndValue(findNodeByResourceId(s, ID_FAVORITE_COLOR), "Yellow");
                });
    }

    @Test
    public void testAutofillAllChangedFirstRequiredSaveAll() throws Exception {
        mActivity.expectAutoFill("Shelbyville Nuclear Power Plant", "Shelbyville Bluffs",
                "Shelbyville", "Lemon");
        autofillAndSaveOnChangeTest(new CannedDataset.Builder()
                // Initial dataset
                .setField(ID_ADDRESS1, "Shelbyville Nuclear Power Plant")
                .setField(ID_ADDRESS2, "Shelbyville Bluffs")
                .setField(ID_CITY, "Shelbyville")
                .setField(ID_FAVORITE_COLOR, "Lemon"),
                // Changes
                () -> {
                    mActivity.mAddress1.setText("742 Evergreen Terrace"); // required
                },
                // Final state
                (s) -> {
                    assertTextAndValue(findNodeByResourceId(s, ID_ADDRESS1),
                            "742 Evergreen Terrace");
                    assertTextAndValue(findNodeByResourceId(s, ID_ADDRESS2), "Shelbyville Bluffs");
                    assertTextAndValue(findNodeByResourceId(s, ID_CITY), "Shelbyville");
                    assertTextAndValue(findNodeByResourceId(s, ID_FAVORITE_COLOR), "Lemon");
                });
    }

    @Test
    public void testAutofillAllChangedSecondRequiredSaveAll() throws Exception {
        mActivity.expectAutoFill("Shelbyville Nuclear Power Plant", "Shelbyville Bluffs",
                "Shelbyville", "Lemon");
        autofillAndSaveOnChangeTest(new CannedDataset.Builder()
                // Initial dataset
                .setField(ID_ADDRESS1, "Shelbyville Nuclear Power Plant")
                .setField(ID_ADDRESS2, "Shelbyville Bluffs")
                .setField(ID_CITY, "Shelbyville")
                .setField(ID_FAVORITE_COLOR, "Lemon"),
                // Changes
                () -> {
                    mActivity.mCity.setText("Springfield"); // required
                },
                // Final state
                (s) -> {
                    assertTextAndValue(findNodeByResourceId(s, ID_ADDRESS1),
                            "Shelbyville Nuclear Power Plant");
                    assertTextAndValue(findNodeByResourceId(s, ID_ADDRESS2), "Shelbyville Bluffs");
                    assertTextAndValue(findNodeByResourceId(s, ID_CITY), "Springfield");
                    assertTextAndValue(findNodeByResourceId(s, ID_FAVORITE_COLOR), "Lemon");
                });
    }

    @Test
    public void testAutofillAllChangedOptionalSaveAll() throws Exception {
        mActivity.expectAutoFill("Shelbyville Nuclear Power Plant", "Shelbyville Bluffs",
                "Shelbyville", "Lemon");
        autofillAndSaveOnChangeTest(new CannedDataset.Builder()
                // Initial dataset
                .setField(ID_ADDRESS1, "Shelbyville Nuclear Power Plant")
                .setField(ID_ADDRESS2, "Shelbyville Bluffs")
                .setField(ID_CITY, "Shelbyville")
                .setField(ID_FAVORITE_COLOR, "Lemon"),
                // Changes
                () -> {
                    mActivity.mAddress2.setText("Simpsons House"); // not required
                },
                // Final state
                (s) -> {
                    assertTextAndValue(findNodeByResourceId(s, ID_ADDRESS1),
                            "Shelbyville Nuclear Power Plant");
                    assertTextAndValue(findNodeByResourceId(s, ID_ADDRESS2), "Simpsons House");
                    assertTextAndValue(findNodeByResourceId(s, ID_CITY), "Shelbyville");
                    assertTextAndValue(findNodeByResourceId(s, ID_FAVORITE_COLOR), "Lemon");
                });
    }

    /**
     * Tests the scenario where the service autofilled the activity but the user changed fields
     * that triggered Save.
     */
    private void autofillAndSaveOnChangeTest(CannedDataset.Builder dataset, Runnable changes,
            Visitor<AssistStructure> assertions) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(newResponseBuilder()
                .addDataset(dataset.setPresentation(createPresentation("Da Dataset")).build())
                .build());

        // Trigger auto-fill.
        mActivity.syncRunOnUiThread(() -> { mActivity.mAddress1.requestFocus(); });

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started.
        sReplier.getNextFillRequest();

        // Auto-fill it.
        sUiBot.selectDataset("Da Dataset");

        // Check the results.
        mActivity.assertAutoFilled();

        // Manually fill fields...
        mActivity.syncRunOnUiThread(changes);

        // ...then tap save.
        mActivity.save();

        // Assert the snack bar is shown and tap "Save".
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_ADDRESS);

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertWithMessage("onSave() not called").that(saveRequest).isNotNull();

        // Assert value of fields
        assertions.visit(saveRequest.structure);

        // Once saved, the session should be finsihed.
        assertNoDanglingSessions();
    }

    @Test
    public void testAutofillAllChangedIgnored() throws Exception {
        mActivity.expectAutoFill("Shelbyville Nuclear Power Plant", "Shelbyville Bluffs",
                "Shelbyville", "Lemon");
        autofillNoChangeNoSaveTest(new CannedDataset.Builder()
                .setField(ID_ADDRESS1, "Shelbyville Nuclear Power Plant")
                .setField(ID_ADDRESS2, "Shelbyville Bluffs")
                .setField(ID_CITY, "Shelbyville")
                .setField(ID_FAVORITE_COLOR, "Lemon"), () -> {
                    mActivity.mFavoriteColor.setText("Yellow"); // lol
                });
    }

    @Test
    public void testAutofillAllFirstRequiredChangedToEmpty() throws Exception {
        mActivity.expectAutoFill("Shelbyville Nuclear Power Plant", "Shelbyville Bluffs",
                "Shelbyville", "Lemon");
        autofillNoChangeNoSaveTest(new CannedDataset.Builder()
                .setField(ID_ADDRESS1, "Shelbyville Nuclear Power Plant")
                .setField(ID_ADDRESS2, "Shelbyville Bluffs")
                .setField(ID_CITY, "Shelbyville")
                .setField(ID_FAVORITE_COLOR, "Lemon"), () -> {
                    mActivity.mAddress1.setText("");
                });
    }

    @Test
    public void testAutofillAllSecondRequiredChangedToNull() throws Exception {
        mActivity.expectAutoFill("Shelbyville Nuclear Power Plant", "Shelbyville Bluffs",
                "Shelbyville", "Lemon");
        autofillNoChangeNoSaveTest(new CannedDataset.Builder()
                .setField(ID_ADDRESS1, "Shelbyville Nuclear Power Plant")
                .setField(ID_ADDRESS2, "Shelbyville Bluffs")
                .setField(ID_CITY, "Shelbyville")
                .setField(ID_FAVORITE_COLOR, "Lemon"), () -> {
                    mActivity.mCity.setText(null);
                });
    }

    @Test
    public void testAutofillAllFirstRequiredChangedBackToInitialState() throws Exception {
        mActivity.expectAutoFill("Shelbyville Nuclear Power Plant", "Shelbyville Bluffs",
                "Shelbyville", "Lemon");
        autofillNoChangeNoSaveTest(new CannedDataset.Builder()
                .setField(ID_ADDRESS1, "Shelbyville Nuclear Power Plant")
                .setField(ID_ADDRESS2, "Shelbyville Bluffs")
                .setField(ID_CITY, "Shelbyville")
                .setField(ID_FAVORITE_COLOR, "Lemon"), () -> {
                    mActivity.mAddress1.setText("I'm different");
                    mActivity.mAddress1.setText("Shelbyville Nuclear Power Plant");
                });
    }

    /**
     * Tests the scenario where the service autofilled the activity and the user changed fields,
     * but it did not triggered Save.
     */
    private void autofillNoChangeNoSaveTest(CannedDataset.Builder dataset, Runnable changes)
            throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(newResponseBuilder()
                .addDataset(dataset.setPresentation(createPresentation("Da Dataset")).build())
                .build());

        // Trigger auto-fill.
        mActivity.syncRunOnUiThread(() -> mActivity.mAddress1.requestFocus());

        // Wait for onFill() before proceeding, otherwise the fields might be changed before
        // the session started.
        sReplier.getNextFillRequest();

        // Auto-fill it.
        sUiBot.selectDataset("Da Dataset");

        // Check the results.
        mActivity.assertAutoFilled();

        // Manually fill fields...
        mActivity.syncRunOnUiThread(changes);

        // ...then tap save.
        mActivity.save();

        // Assert the snack bar is not shown.
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_ADDRESS);

        // Once saved, the session should be finsihed.
        assertNoDanglingSessions();
    }

    @Test
    public void testDontShowSaveUiWhenUserManuallyFilled_oneDatasetAllRequiredFields()
            throws Exception {
        saveWhenUserFilledDatasetFields(
                new String[] {ID_ADDRESS1, ID_ADDRESS2},
                null,
                () -> {
                    mActivity.mAddress1.setText("742 Evergreen Terrace");
                    mActivity.mAddress2.setText("Simpsons House");
                },
                EXPECT_NO_SAVE_UI,
                new CannedDataset.Builder()
                    .setPresentation(createPresentation("SF"))
                    .setField(ID_ADDRESS1, "742 Evergreen Terrace")
                    .setField(ID_ADDRESS2, "Simpsons House")
                    .build()
        );
    }

    @Test
    public void testDontShowSaveUiWhenUserManuallyFilled_oneDatasetRequiredAndOptionalFields()
            throws Exception {
        saveWhenUserFilledDatasetFields(
                new String[] {ID_ADDRESS1},
                new String[] {ID_ADDRESS2},
                () -> {
                    mActivity.mAddress1.setText("742 Evergreen Terrace");
                    mActivity.mAddress2.setText("Simpsons House");
                },
                EXPECT_NO_SAVE_UI,
                new CannedDataset.Builder()
                    .setPresentation(createPresentation("SF"))
                    .setField(ID_ADDRESS1, "742 Evergreen Terrace")
                    .setField(ID_ADDRESS2, "Simpsons House")
                    .build()
        );
    }

    @Test
    public void testDontShowSaveUiWhenUserManuallyFilled_multipleDatasetsDataOnFirst()
            throws Exception {
        saveWhenUserFilledDatasetFields(
                new String[] {ID_ADDRESS1},
                new String[] {ID_ADDRESS2},
                () -> {
                    mActivity.mAddress1.setText("742 Evergreen Terrace");
                    mActivity.mAddress2.setText("Simpsons House");
                },
                EXPECT_NO_SAVE_UI,
                new CannedDataset.Builder()
                    .setPresentation(createPresentation("SF"))
                    .setField(ID_ADDRESS1, "742 Evergreen Terrace")
                    .setField(ID_ADDRESS2, "Simpsons House")
                    .build(),
                new CannedDataset.Builder()
                    .setPresentation(createPresentation("SV"))
                    .setField(ID_ADDRESS1, "Shelbyville Nuclear Power Plant")
                    .setField(ID_ADDRESS2, "Shelbyville Bluffs")
                    .build()
        );
    }

    @Test
    public void testDontShowSaveUiWhenUserManuallyFilled_multipleDatasetsDataOnSecond()
            throws Exception {
        saveWhenUserFilledDatasetFields(
                new String[] {ID_ADDRESS1},
                new String[] {ID_ADDRESS2},
                () -> {
                    mActivity.mAddress1.setText("Shelbyville Nuclear Power Plant");
                    mActivity.mAddress2.setText("Shelbyville Bluffs");
                },
                EXPECT_NO_SAVE_UI,
                new CannedDataset.Builder()
                    .setPresentation(createPresentation("SF"))
                    .setField(ID_ADDRESS1, "742 Evergreen Terrace")
                    .setField(ID_ADDRESS2, "Simpsons House")
                    .build(),
                new CannedDataset.Builder()
                    .setPresentation(createPresentation("SV"))
                    .setField(ID_ADDRESS1, "Shelbyville Nuclear Power Plant")
                    .setField(ID_ADDRESS2, "Shelbyville Bluffs")
                    .build()
        );
    }

    @Test
    public void testShowSaveUiWhenUserManuallyFilled_requiredOnly()
            throws Exception {
        saveWhenUserFilledDatasetFields(
                new String[] {ID_ADDRESS1},
                new String[] {ID_ADDRESS2},
                () -> {
                    mActivity.mAddress1.setText("742 Evergreen Terrace");
                },
                EXPECT_SAVE_UI,
                new CannedDataset.Builder()
                    .setPresentation(createPresentation("SF"))
                    .setField(ID_ADDRESS1, "742 Evergreen Terrace")
                    .setField(ID_ADDRESS2, "Simpsons House")
                    .build()
        );
    }

    @Test
    public void testShowSaveUiWhenUserManuallyFilled_optionalOnly()
            throws Exception {
        saveWhenUserFilledDatasetFields(
                new String[] {ID_ADDRESS1},
                new String[] {ID_ADDRESS2},
                () -> {
                    mActivity.mAddress2.setText("Simpsons House");
                },
                EXPECT_SAVE_UI,
                new CannedDataset.Builder()
                    .setPresentation(createPresentation("SF"))
                    .setField(ID_ADDRESS1, "742 Evergreen Terrace")
                    .setField(ID_ADDRESS2, "Simpsons House")
                    .build()
        );
    }

    private void saveWhenUserFilledDatasetFields(@NonNull String[] requiredIds,
            @Nullable String[] optionalIds, @NonNull Runnable changes, boolean expectSaveUi,
            @NonNull CannedDataset...datasets) throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        final CannedFillResponse.Builder response = new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_ADDRESS, requiredIds);
        if (optionalIds != null) {
            response.setOptionalSavableIds(optionalIds);
        }
        for (CannedDataset dataset : datasets) {
            response.addDataset(dataset);
        }
        sReplier.addResponse(response.build());

        // Trigger auto-fill.
        mActivity.syncRunOnUiThread(() -> mActivity.mAddress1.requestFocus());
        sReplier.getNextFillRequest();

        // Manually fill it.
        mActivity.syncRunOnUiThread(changes);

        // Make sure the snack bar is not shown.
        if (expectSaveUi) {
            sUiBot.assertSaveShowing(SAVE_DATA_TYPE_ADDRESS);
        } else {
            sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_ADDRESS);
        }

        // ...then tap save.
        mActivity.save();

        // Assert the snack bar is not shown.
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_ADDRESS);
    }

    @Test
    public void testDontShowSaveUiWhenUserClearedAutofilledFieldThatIsRequired() throws Exception {
        // Set service.
        enableService();

        mActivity.expectAutoFill("742 Evergreen Terrace", "Simpsons House",
                "Springfield", "Yellow");
        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_ADDRESS, ID_ADDRESS1, ID_ADDRESS2)
                .setOptionalSavableIds(ID_CITY)
                .addDataset(new CannedDataset.Builder()
                        .setPresentation(createPresentation("SF"))
                        .setField(ID_ADDRESS1, "742 Evergreen Terrace")
                        .setField(ID_ADDRESS2, "Simpsons House")
                        .setField(ID_CITY, "Springfield")
                        .setField(ID_FAVORITE_COLOR, "Yellow")
                        .build())
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mAddress1.requestFocus());
        sReplier.getNextFillRequest();

        sUiBot.selectDataset("SF");
        mActivity.assertAutoFilled();

        // Clear the field.
        mActivity.syncRunOnUiThread(() -> mActivity.mAddress2.setText(""));

        // Trigger save...
        mActivity.save();

        // ...and make sure the snack bar is not shown.
        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_ADDRESS);
    }

    @Test
    public void testShowSaveUiWhenUserClearedAutofilledFieldThatIsOptional() throws Exception {
        // Set service.
        enableService();

        mActivity.expectAutoFill("742 Evergreen Terrace", "Simpsons House",
                "Springfield", "Yellow");
        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_ADDRESS, ID_ADDRESS1, ID_ADDRESS2)
                .setOptionalSavableIds(ID_CITY)
                .addDataset(new CannedDataset.Builder()
                        .setPresentation(createPresentation("SF"))
                        .setField(ID_ADDRESS1, "742 Evergreen Terrace")
                        .setField(ID_ADDRESS2, "Simpsons House")
                        .setField(ID_CITY, "Springfield")
                        .setField(ID_FAVORITE_COLOR, "Yellow")
                        .build())
                .build());

        // Trigger autofill.
        mActivity.syncRunOnUiThread(() -> mActivity.mAddress1.requestFocus());
        sReplier.getNextFillRequest();

        sUiBot.selectDataset("SF");
        mActivity.assertAutoFilled();

        // Clear the field.
        mActivity.syncRunOnUiThread(() -> mActivity.mCity.setText(""));

        // Trigger save...
        mActivity.save();

        // ...and make sure the snack bar is shown.
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_ADDRESS);

        // Finally, assert values.
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_ADDRESS1),
                "742 Evergreen Terrace");
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_ADDRESS2),
                "Simpsons House");
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_CITY), "");
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_FAVORITE_COLOR),
                "Yellow");
    }
}
