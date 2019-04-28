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

package com.android.documentsui.picker;

import android.app.Activity;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.PairedTask;
import com.android.documentsui.base.State;
import com.android.documentsui.roots.ProvidersAccess;

import java.util.function.Consumer;

import javax.annotation.Nullable;

/**
 * Loads the last used path (stack) from Recents (history).
 * The path selected is based on the calling package name. So the last
 * path for an app like Gmail can be different than the last path
 * for an app like DropBox.
 */
final class LoadLastAccessedStackTask<T extends Activity & CommonAddons>
        extends PairedTask<T, Void, DocumentStack> {

    private final LastAccessedStorage mLastAccessed;
    private final State mState;
    private final ProvidersAccess mProviders;
    private final Consumer<DocumentStack> mCallback;

    LoadLastAccessedStackTask(
            T activity,
            LastAccessedStorage lastAccessed,
            State state,
            ProvidersAccess providers,
            Consumer<DocumentStack> callback) {
        super(activity);
        mLastAccessed = lastAccessed;
        mProviders = providers;
        mState = state;
        mCallback = callback;
    }

    @Override
    protected DocumentStack run(Void... params) {
        return mLastAccessed.getLastAccessed(mOwner, mProviders, mState);
    }

    @Override
    protected void finish(@Nullable DocumentStack stack) {
        mCallback.accept(stack);
    }
}
