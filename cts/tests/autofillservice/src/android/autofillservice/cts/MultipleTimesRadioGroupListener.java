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

import static android.autofillservice.cts.Helper.FILL_TIMEOUT_MS;

import static com.google.common.truth.Truth.assertWithMessage;

import android.widget.RadioGroup;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Custom {@link RadioGroup.OnCheckedChangeListener} used to assert an
 * {@link RadioGroup} was auto-filled properly.
 */
final class MultipleTimesRadioGroupListener implements RadioGroup.OnCheckedChangeListener {
    private final String mName;
    private final CountDownLatch mLatch;
    private final RadioGroup mRadioGroup;
    private final int mExpected;

    MultipleTimesRadioGroupListener(String name, int times, RadioGroup radioGroup,
            int expectedAutoFilledValue) {
        mName = name;
        mRadioGroup = radioGroup;
        mExpected = expectedAutoFilledValue;
        mLatch = new CountDownLatch(times);
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        mLatch.countDown();
    }

    void assertAutoFilled() throws Exception {
        final boolean set = mLatch.await(FILL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertWithMessage("Timeout (%s ms) on RadioGroup %s", FILL_TIMEOUT_MS, mName)
            .that(set).isTrue();
        final int actual = mRadioGroup.getAutofillValue().getListValue();
        assertWithMessage("Wrong auto-fill value on RadioGroup %s", mName)
            .that(actual).isEqualTo(mExpected);
    }
}
