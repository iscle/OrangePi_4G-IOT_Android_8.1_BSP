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

package com.android.documentsui.picker;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.roots.ProvidersAccess;

import libcore.io.IoUtils;

import java.io.IOException;

import javax.annotation.Nullable;

/**
 * An interface that stores the last accessed stack of the caller
 */
public interface LastAccessedStorage {

    @Nullable DocumentStack getLastAccessed(
            Activity activity, ProvidersAccess providers, State state);

    void setLastAccessed(Activity activity, DocumentStack stack);

    void setLastAccessedToExternalApp(Activity activity);

    static LastAccessedStorage create() {
        return new RuntimeLastAccessedStorage();
    }

    class RuntimeLastAccessedStorage implements LastAccessedStorage {

        private static final String TAG = "LastAccessedStorage";

        private RuntimeLastAccessedStorage() {}

        @Override
        public @Nullable DocumentStack getLastAccessed(
                Activity activity, ProvidersAccess providers, State state) {
            final String packageName = Shared.getCallingPackageName(activity);
            final Uri resumeUri = LastAccessedProvider.buildLastAccessed(packageName);
            final ContentResolver resolver = activity.getContentResolver();
            Cursor cursor = resolver.query(resumeUri, null, null, null, null);
            try {
                return DocumentStack.fromLastAccessedCursor(
                        cursor, providers.getMatchingRootsBlocking(state), resolver);
            } catch (IOException e) {
                Log.w(TAG, "Failed to resume: ", e);
            } finally {
                IoUtils.closeQuietly(cursor);
            }

            return null;
        }

        @Override
        public void setLastAccessed(Activity activity, DocumentStack stack) {
            String packageName = Shared.getCallingPackageName(activity);
            LastAccessedProvider.setLastAccessed(activity.getContentResolver(), packageName, stack);
        }

        @Override
        public void setLastAccessedToExternalApp(Activity activity) {
            final String packageName = Shared.getCallingPackageName(activity);
            final ContentValues values = new ContentValues();
            values.put(LastAccessedProvider.Columns.EXTERNAL, 1);
            activity.getContentResolver().insert(
                    LastAccessedProvider.buildLastAccessed(packageName), values);
        }
    }
}
