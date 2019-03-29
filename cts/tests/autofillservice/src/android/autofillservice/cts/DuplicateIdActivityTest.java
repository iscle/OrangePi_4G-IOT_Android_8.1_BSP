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

import static android.autofillservice.cts.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.DuplicateIdActivity.DUPLICATE_ID;
import static android.autofillservice.cts.Helper.runShellCommand;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilConnected;
import static android.autofillservice.cts.InstrumentedAutoFillService.waitUntilDisconnected;

import static com.google.common.truth.Truth.assertThat;

import android.app.assist.AssistStructure;
import android.util.Log;
import android.view.autofill.AutofillId;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * This is the test case covering most scenarios - other test cases will cover characteristics
 * specific to that test's activity (for example, custom views).
 */
public class DuplicateIdActivityTest extends AutoFillServiceTestCase {
    private static final String LOG_TAG = DuplicateIdActivityTest.class.getSimpleName();
    @Rule
    public final AutofillActivityTestRule<DuplicateIdActivity> mActivityRule = new AutofillActivityTestRule<>(
            DuplicateIdActivity.class);

    private DuplicateIdActivity mActivity;

    @Before
    public void setup() {
        Helper.disableAutoRotation(sUiBot);
        sUiBot.setScreenOrientation(0);

        mActivity = mActivityRule.getActivity();
    }

    @After
    public void teardown() {
        mActivity.finish();

        Helper.allowAutoRotation();
    }

    /**
     * Find the views that are tested from the structure in the request
     *
     * @param request The request
     *
     * @return An array containing the two tested views
     */
    private AssistStructure.ViewNode[] findViews(InstrumentedAutoFillService.FillRequest request) {
        assertThat(request.structure.getWindowNodeCount()).isEqualTo(1);
        AssistStructure.WindowNode windowNode = request.structure.getWindowNodeAt(0);

        AssistStructure.ViewNode rootNode = windowNode.getRootViewNode();

        assertThat(rootNode.getChildCount()).isEqualTo(2);
        return new AssistStructure.ViewNode[]{rootNode.getChildAt(0), rootNode.getChildAt(1)};
    }

    @Test
    public void testDoNotRestoreDuplicateAutofillIds() throws Exception {
        enableService();

        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(DUPLICATE_ID, "value")
                        .setPresentation(createPresentation("dataset"))
                        .build())
                .build());

        // Select field to start autofill
        runShellCommand("input keyevent KEYCODE_TAB");

        waitUntilConnected();
        InstrumentedAutoFillService.FillRequest request = sReplier.getNextFillRequest();

        AssistStructure.ViewNode[] views = findViews(request);
        AssistStructure.ViewNode view1 = views[0];
        AssistStructure.ViewNode view2 = views[1];
        AutofillId id1 = view1.getAutofillId();
        AutofillId id2 = view2.getAutofillId();

        Log.i(LOG_TAG, "view1=" + id1);
        Log.i(LOG_TAG, "view2=" + id2);

        // Both checkboxes use the same id
        assertThat(view1.getId()).isEqualTo(view2.getId());

        // They got different autofill ids though
        assertThat(id1).isNotEqualTo(id2);

        sReplier.addResponse(NO_RESPONSE);

        // Force rotation to force onDestroy->onCreate cycle
        sUiBot.setScreenOrientation(1);

        // Select other field to trigger new partition
        runShellCommand("input keyevent KEYCODE_TAB");

        request = sReplier.getNextFillRequest();

        views = findViews(request);
        AutofillId recreatedId1 = views[0].getAutofillId();
        AutofillId recreatedId2 = views[1].getAutofillId();

        Log.i(LOG_TAG, "restored view1=" + recreatedId1);
        Log.i(LOG_TAG, "restored view2=" + recreatedId2);

        // For the restoring logic the two views are the same. Hence it might happen that the first
        // view is restored with the id of the second view or the other way round.
        // We just need
        // - to restore as many views as we can (i.e. one)
        // - make sure the autofill ids are still unique after
        boolean view1WasRestored = (recreatedId1.equals(id1) || recreatedId1.equals(id2));
        boolean view2WasRestored = (recreatedId2.equals(id1) || recreatedId2.equals(id2));

        // One id was restored
        assertThat(view1WasRestored || view2WasRestored).isTrue();

        // The views still have different autofill ids
        assertThat(recreatedId1).isNotEqualTo(recreatedId2);

        waitUntilDisconnected();
    }
}
