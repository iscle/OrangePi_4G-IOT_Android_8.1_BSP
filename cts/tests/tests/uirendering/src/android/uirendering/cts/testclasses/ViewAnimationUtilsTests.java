/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.uirendering.cts.testclasses;

import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.ViewInitializer;
import android.view.ViewAnimationUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ViewAnimationUtilsTests extends ActivityTestBase {
    @Test
    public void testCreateCircularReveal() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, (ViewInitializer) view -> {
                    ViewAnimationUtils.createCircularReveal(view, 45, 45, 45, 45)
                            .setDuration(10000) // 10 sec, longer than animation
                            .start();
                }, true)
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                        R.drawable.golden_blue_circle, new MSSIMComparer(0.99)));
    }
}
