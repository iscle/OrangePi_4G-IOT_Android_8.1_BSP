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

import android.annotation.BoolRes;
import android.annotation.NonNull;
import android.annotation.PluralsRes;
import android.annotation.StringRes;
import android.content.res.Resources;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.android.documentsui.R;
import com.android.documentsui.files.QuickViewIntentBuilder;

import org.mockito.Mockito;

import javax.annotation.Nullable;

/**
 * Abstract to avoid having to implement unnecessary Activity stuff.
 * Instances are created using {@link #create()}.
 */
public abstract class TestResources extends Resources {

    public SparseBooleanArray bools;
    public SparseArray<String> strings;
    public SparseArray<String> plurals;

    public TestResources() {
        super(ClassLoader.getSystemClassLoader());
    }

    public static TestResources create() {
        TestResources res = Mockito.mock(
                TestResources.class, Mockito.CALLS_REAL_METHODS);
        res.bools = new SparseBooleanArray();
        res.strings = new SparseArray<>();
        res.plurals = new SparseArray<>();

        res.setProductivityDeviceEnabled(false);

        // quick view package can be set via system property on debug builds.
        // unfortunately that interfers with testing. For that reason we have
        // this little hack....QuickViewIntentBuilder will check for this value
        // and ignore
        res.setQuickViewerPackage(QuickViewIntentBuilder.IGNORE_DEBUG_PROP);
        res.setDefaultDocumentsUri(TestProvidersAccess.DOWNLOADS.getUri().toString());
        return res;
    }

    public void setQuickViewerPackage(String packageName) {
        strings.put(R.string.trusted_quick_viewer_package, packageName);
    }

    public void setDefaultDocumentsUri(String uri) {
        strings.put(R.string.default_root_uri, uri);
    }

    public void setProductivityDeviceEnabled(boolean enabled) {
        bools.put(R.bool.show_documents_root, enabled);
    }

    @Override
    public final boolean getBoolean(@BoolRes int id) throws NotFoundException {
        return bools.get(id);
    }

    @Override
    public final @Nullable String getString(@StringRes int id) throws NotFoundException {
        return strings.get(id);
    }

    @NonNull
    public final String getString(
            @StringRes int id, Object... formatArgs) throws NotFoundException {
        return getString(id);
    }

    @Override
    public final @Nullable String getQuantityString(@PluralsRes int id, int size) {
        return plurals.get(id);
    }

    @Override
    public final @Nullable String getQuantityString(@PluralsRes int id, int size, Object... args) {
        String format = getQuantityString(id, size);
        if (format != null) {
            return String.format(format, args);
        }

        return null;
    }

    public final CharSequence getText(@StringRes int resId) {
        return getString(resId);
    }
}
