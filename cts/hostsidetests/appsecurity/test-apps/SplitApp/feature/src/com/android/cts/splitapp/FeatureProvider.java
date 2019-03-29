/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.splitapp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.lang.reflect.Field;

public class FeatureProvider extends ContentProvider {
    private static final String TAG = "FeatureProvider";

    public static boolean sCreated = false;

    @Override
    public boolean onCreate() {
        Log.d(TAG, "FeatureProvider.onCreate()");

        sCreated = true;

        try {
            // Just reach out and touch
            final Class<?> test = Class.forName("com.android.cts.splitapp.SplitAppTest");
            final Field touched = test.getDeclaredField("sFeatureTouched");
            touched.set(null, true);

            // Also make sure we can read a resource from the base; we just
            // stash the value we saw over on the test for them to verify.
            final Class<?> baseR = Class.forName("com.android.cts.splitapp.BaseR");
            final int stringId = (int) baseR.getDeclaredField("my_string1").get(null);
            final Field value = test.getDeclaredField("sFeatureValue");
            value.set(null, getContext().getResources().getString(stringId));

        } catch (Throwable t) {
            // We're okay if anything above fails, since the test later verifies
            // that we actually touched the boolean.
            Log.e(TAG, "Failed to communicate back to base", t);
        }

        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
