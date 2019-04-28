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

import static android.content.Intent.EXTRA_INTENT;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Handy-dandy Junit asserts covering Intents.
 */
public final class IntentAsserts {

    private IntentAsserts() {}

    public static void assertHasAction(Intent intent, String expected) {
        assertEquals(expected, intent.getAction());
    }

    public static Intent assertHasExtraIntent(Intent intent) {
        Intent extra = (Intent) intent.getExtra(EXTRA_INTENT);
        assertNotNull(extra);
        return extra;
    }

    public static Uri assertHasExtraUri(Intent intent, String key) {
        Object value = intent.getExtra(key);
        assertNotNull(value);
        assertTrue(value instanceof Uri);
        return (Uri) value;
    }

    public static List<Parcelable> assertHasExtraList(Intent intent, String key) {
        ArrayList<Parcelable> list = intent.getParcelableArrayListExtra(key);
        assertNotNull(list);
        return list;
    }

    public static List<Parcelable> assertHasExtraList(Intent intent, String key, int size) {
        List<Parcelable> list = assertHasExtraList(intent, key);
        Assert.assertEquals(size, list.size());
        return list;
    }
}
