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
package com.android.managedprovisioning.preprovisioning;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Color;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.test.ActivityUnitTestCase;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.android.managedprovisioning.common.CustomizationVerifier;

import java.util.concurrent.atomic.AtomicReference;

@SmallTest
public class WebActivityTest extends ActivityUnitTestCase<WebActivity> {
    private static final String TEST_URL = "http://www.test.com/support";
    private static final int STATUS_BAR_COLOR = Color.parseColor("#BDBDBD"); // any color would do

    public WebActivityTest() {
        super(WebActivity.class);
    }

    public void testNoUrl() {
        Intent intent = WebActivity.createIntent(getInstrumentation().getTargetContext(), null,
                STATUS_BAR_COLOR);
        assertThat(intent, nullValue());
    }

    public void testUrlLaunched() {
        startActivityOnMainSync(WebActivity.createIntent(getInstrumentation().getTargetContext(),
                TEST_URL, STATUS_BAR_COLOR));
        assertFalse(isFinishCalled());
        final AtomicReference<String> urlRef = new AtomicReference<>(null);
        getInstrumentation().runOnMainSync(() ->
                urlRef.set(((WebView) ((ViewGroup) getActivity()
                        .findViewById(android.R.id.content)).getChildAt(0)).getUrl()));
        assertEquals(TEST_URL, urlRef.get());
        new CustomizationVerifier(getActivity()).assertStatusBarColorCorrect(STATUS_BAR_COLOR);
    }

    private void startActivityOnMainSync(final Intent intent) {
        getInstrumentation().runOnMainSync(() -> startActivity(intent, null, null));
    }

    @Override
    public Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }
}