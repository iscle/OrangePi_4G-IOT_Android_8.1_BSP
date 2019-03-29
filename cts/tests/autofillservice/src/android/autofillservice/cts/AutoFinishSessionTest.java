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

import static android.autofillservice.cts.FragmentContainerActivity.FRAGMENT_TAG;
import static android.autofillservice.cts.Helper.findNodeByResourceId;
import static android.autofillservice.cts.Helper.getContext;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;

import static com.google.common.truth.Truth.assertThat;

import android.app.Fragment;
import android.autofillservice.cts.InstrumentedAutoFillService.SaveRequest;
import android.content.Intent;
import android.service.autofill.SaveInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ViewGroup;
import android.widget.EditText;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests that the session finishes when the views and fragments go away
 */
public class AutoFinishSessionTest extends AutoFillServiceTestCase {
    @Rule
    public final AutofillActivityTestRule<FragmentContainerActivity> mActivityRule =
            new AutofillActivityTestRule<>(FragmentContainerActivity.class);
    private FragmentContainerActivity mActivity;
    private EditText mEditText1;
    private EditText mEditText2;
    private Fragment mFragment;
    private ViewGroup mParent;

    @Before
    public void initViews() {
        mActivity = mActivityRule.getActivity();
        mEditText1 = mActivity.findViewById(R.id.editText1);
        mEditText2 = mActivity.findViewById(R.id.editText2);
        mFragment = mActivity.getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        mParent = ((ViewGroup) mEditText1.getParent());

        assertThat(mFragment).isNotNull();
    }

    // firstRemove and secondRemove run in the UI Thread; firstCheck doesn't
    private void removeViewsBaseTest(@NonNull Runnable firstRemove, @Nullable Runnable firstCheck,
            @Nullable Runnable secondRemove, String... viewsToSave)
            throws Exception {
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, viewsToSave).build());

        // Trigger autofill
        mActivity.syncRunOnUiThread(() -> {
            mEditText2.requestFocus();
            mEditText1.requestFocus();
        });

        sReplier.getNextFillRequest();

        sUiBot.assertNoDatasets();

        // remove first set of views
        mActivity.syncRunOnUiThread(() -> {
            mEditText1.setText("editText1-filled");
            mEditText2.setText("editText2-filled");
            firstRemove.run();
        });

        // Check state between remove operations
        if (firstCheck != null) {
            firstCheck.run();
        }

        // remove second set of views
        if (secondRemove != null) {
            mActivity.syncRunOnUiThread(secondRemove);
        }

        // Save should be shows after all remove operations were executed
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_GENERIC);

        SaveRequest saveRequest = sReplier.getNextSaveRequest();
        for (String view : viewsToSave) {
            assertThat(findNodeByResourceId(saveRequest.structure, view)
                    .getAutofillValue().getTextValue().toString()).isEqualTo(view + "-filled");
        }
    }

    @Test
    public void removeBothViewsToFinishSession() throws Exception {
        removeViewsBaseTest(
                () -> ((ViewGroup) mEditText1.getParent()).removeView(mEditText1),
                () -> sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_GENERIC),
                () -> ((ViewGroup) mEditText2.getParent()).removeView(mEditText2),
                "editText1", "editText2");
    }

    @Test
    public void removeOneViewToFinishSession() throws Exception {
        removeViewsBaseTest(
                () -> {
                    // Do not trigger new partition when switching to editText2
                    mEditText2.setFocusable(false);

                    mParent.removeView(mEditText1);
                },
                null,
                null,
                "editText1");
    }

    @Test
    public void hideOneViewToFinishSession() throws Exception {
        removeViewsBaseTest(
                () -> {
                    // Do not trigger new partition when switching to editText2
                    mEditText2.setFocusable(false);

                    mEditText1.setVisibility(ViewGroup.INVISIBLE);
                },
                null,
                null,
                "editText1");
    }

    @Test
    public void removeFragmentToFinishSession() throws Exception {
        removeViewsBaseTest(
                () -> mActivity.getFragmentManager().beginTransaction().remove(
                        mFragment).commitNow(),
                null,
                null,
                "editText1", "editText2");
    }

    @Test
    public void removeParentToFinishSession() throws Exception {
        removeViewsBaseTest(
                () -> ((ViewGroup) mParent.getParent()).removeView(mParent),
                null,
                null,
                "editText1", "editText2");
    }

    @Test
    public void hideParentToFinishSession() throws Exception {
        removeViewsBaseTest(
                () -> mParent.setVisibility(ViewGroup.INVISIBLE),
                null,
                null,
                "editText1", "editText2");
    }

    /**
     * An activity that is currently getting autofilled might go into the background. While the
     * tracked views are not visible on the screen anymore, this should not trigger a save.
     *
     * <p>The {@link Runnable}s are synchronously run in the UI thread.
     */
    private void activityToBackgroundShouldNotTriggerSave(@Nullable Runnable removeInBackGround,
            @Nullable Runnable removeInForeGroup) throws Exception {
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, "editText1").build());

        // Trigger autofill
        mActivity.syncRunOnUiThread(() -> {
            mEditText2.requestFocus();
            mEditText1.requestFocus();
        });

        sReplier.getNextFillRequest();

        sUiBot.assertNoDatasets();

        mActivity.syncRunOnUiThread(() -> {
            mEditText1.setText("editText1-filled");
            mEditText2.setText("editText2-filled");
        });

        // Start activity on top
        mActivity.startActivity(new Intent(getContext(),
                ManualAuthenticationActivity.class));
        mActivity.waitUntilStopped();

        if (removeInBackGround != null) {
            mActivity.syncRunOnUiThread(removeInBackGround);
        }

        sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_GENERIC);

        // Remove previously started activity from top
        sUiBot.selectById("android.autofillservice.cts:id/button");
        mActivity.waitUntilResumed();

        if (removeInForeGroup != null) {
            sUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_GENERIC);

            mActivity.syncRunOnUiThread(removeInForeGroup);
        }

        // Save should be shows after all remove operations were executed
        sUiBot.saveForAutofill(true, SAVE_DATA_TYPE_GENERIC);

        SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertThat(findNodeByResourceId(saveRequest.structure, "editText1")
                .getAutofillValue().getTextValue().toString()).isEqualTo("editText1-filled");
    }

    @Test
    public void removeViewInBackground() throws Exception {
        activityToBackgroundShouldNotTriggerSave(
                () -> mActivity.syncRunOnUiThread(() -> {
                    // Do not trigger new partition when switching to editText2
                    mEditText2.setFocusable(false);

                    mParent.removeView(mEditText1);
                }),
                null);
    }

    @Test
    public void hideViewInBackground() throws Exception {
        activityToBackgroundShouldNotTriggerSave(() -> {
                    // Do not trigger new partition when switching to editText2
                    mEditText2.setFocusable(false);

                    mEditText1.setVisibility(ViewGroup.INVISIBLE);
                },
                null);
    }

    @Test
    public void hideParentInBackground() throws Exception {
        activityToBackgroundShouldNotTriggerSave(() -> mParent.setVisibility(ViewGroup.INVISIBLE),
                null);
    }

    @Test
    public void removeParentInBackground() throws Exception {
        activityToBackgroundShouldNotTriggerSave(
                () -> ((ViewGroup) mParent.getParent()).removeView(mParent),
                null);
    }

    @Test
    public void removeViewAfterBackground() throws Exception {
        activityToBackgroundShouldNotTriggerSave(() -> {
                    // Do not trigger new fill request when closing activity
                    mEditText1.setFocusable(false);
                    mEditText2.setFocusable(false);
                },
                () -> mParent.removeView(mEditText1));
    }
}
