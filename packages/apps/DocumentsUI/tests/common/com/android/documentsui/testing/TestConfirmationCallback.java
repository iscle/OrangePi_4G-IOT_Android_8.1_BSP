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

package com.android.documentsui.testing;

import com.android.documentsui.base.ConfirmationCallback;

import junit.framework.Assert;

import java.util.function.Predicate;

/**
 * Test {@link Predicate} that can be used to spy on,  control responses from,
 * and make assertions against values tested.
 */
public class TestConfirmationCallback implements ConfirmationCallback {

    private boolean mCalled;
    private int mLastValue;

    @Override
    public void accept(int code) {
        mCalled = true;
        mLastValue = code;
    }

    public void assertConfirmed() {
        Assert.assertEquals(ConfirmationCallback.CONFIRM, mLastValue);
    }

    public void assertRejected() {
        Assert.assertEquals(ConfirmationCallback.REJECT, mLastValue);
    }

    public void assertCalled() {
        Assert.assertTrue(mCalled);
    }

    public void assertNeverCalled() {
        Assert.assertFalse(mCalled);
    }
}
