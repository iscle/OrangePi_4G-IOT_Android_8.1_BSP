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

package com.android.documentsui.testing;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;

import android.app.Activity;

import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.State;
import com.android.documentsui.picker.LastAccessedStorage;
import com.android.documentsui.roots.ProvidersAccess;

import javax.annotation.Nullable;

/**
 * A test double of {@link LastAccessedStorage}.
 */
public class TestLastAccessedStorage implements LastAccessedStorage {

    private DocumentStack mLastAccessedStack;
    private boolean mIsExternal = false;

    @Override
    public @Nullable DocumentStack getLastAccessed(Activity activity, ProvidersAccess roots, State state) {
        return mLastAccessedStack;
    }

    @Override
    public void setLastAccessed(Activity activity, DocumentStack stack) {
        mLastAccessedStack = stack;
        mIsExternal = false;
    }

    @Override
    public void setLastAccessedToExternalApp(Activity activity) {
        mIsExternal = true;
    }

    public void assertSameStack(DocumentStack expected) {
        assertSame(expected, mLastAccessedStack);
    }

    public void assertExternal() {
        assertTrue(mIsExternal);
    }

    public void assertNotExternal() {
        assertFalse(mIsExternal);
    }
}
