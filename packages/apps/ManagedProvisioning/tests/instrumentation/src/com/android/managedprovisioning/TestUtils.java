/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.managedprovisioning;

import android.content.Intent;
import android.os.BaseBundle;
import android.os.PersistableBundle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.Objects;
import java.util.Set;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class TestUtils extends AndroidTestCase {
    @SmallTest
    public void testIntentWithActionEquals() {
        Intent i = new Intent("aa");
        assertTrue(intentEquals(i, i));
    }

    @SmallTest
    public void testIntentWithExtraEquals() {
        Intent i = new Intent().putExtra("bb", "cc");
        assertTrue(intentEquals(i, i));
    }

    @SmallTest
    public void testIntentActionNotEqual() {
        Intent i1 = new Intent("aa");
        Intent i2 = new Intent("bb");
        assertFalse(intentEquals(i1, i2));
    }

    @SmallTest
    public void testIntentExtraNotEqual() {
        Intent i1 = new Intent().putExtra("aa", "bb");
        Intent i2 = new Intent().putExtra("aa", "cc");
        assertFalse(intentEquals(i1, i2));
    }

    @SmallTest
    public void testIntentNotSameExtra() {
        Intent i1 = new Intent().putExtra("aa", "bb");
        Intent i2 = new Intent().putExtra("dd", "cc");
        assertFalse(intentEquals(i1, i2));
    }

    /**
     * This method uses Object.equals to compare the extras.
     * Which means that it will always return false if one of the intents has an extra with an
     * embedded bundle.
     */
    public static boolean intentEquals(Intent intent1, Intent intent2) {
        // both are null? return true
        if (intent1 == null && intent2 == null) {
            return true;
        }
        // Only one is null? return false
        if (intent1 == null || intent2 == null) {
            return false;
        }
        return intent1.filterEquals(intent2) && bundleEquals(intent1.getExtras(),
                intent2.getExtras());
    }

    public static boolean bundleEquals(BaseBundle bundle1, BaseBundle bundle2) {
        // both are null? return true
        if (bundle1 == null && bundle2 == null) {
            return true;
        }
        // Only one is null? return false
        if (bundle1 == null || bundle2 == null) {
            return false;
        }
        if (bundle1.size() != bundle2.size()) {
            return false;
        }
        Set<String> keys = bundle1.keySet();
        for (String key : keys) {
            Object value1 = bundle1.get(key);
            Object value2 = bundle2.get(key);
            if (!Objects.equals(value1, value2)) {
                return false;
            }
        }
        return true;
    }

    public static void assertIntentEquals(Intent i1, Intent i2) {
        if (!intentEquals(i1, i2)) {
            failIntentsNotEqual(i1, i2);
        }
    }

    public static void failIntentsNotEqual(Intent i1, Intent i2) {
        fail("Intent " + intentToString(i1) + " is not equal to " + intentToString(i2));
    }

    public static String intentToString(Intent i) {
        return i.toString() + " with extras " + i.getExtras();
    }

    public static PersistableBundle createTestAdminExtras() {
        PersistableBundle adminExtras = new PersistableBundle();
        adminExtras.putBoolean("boolean", true);
        adminExtras.putBooleanArray("boolean_array", new boolean[] { true, false });
        adminExtras.putDouble("double", 1.1);
        adminExtras.putDoubleArray("double_array", new double[] { 1.1, 2.2 });
        adminExtras.putInt("int", 1);
        adminExtras.putIntArray("int_array", new int[] { 1, 2 } );
        adminExtras.putLong("long", 1L);
        adminExtras.putLongArray("long_array", new long[] { 1L, 2L });
        adminExtras.putString("string", "Hello");
        adminExtras.putStringArray("string_array", new String[] { "Hello", "World" } );

        PersistableBundle nestedBundle = new PersistableBundle();
        nestedBundle.putInt("int", 1);
        nestedBundle.putStringArray("string_array", new String[] { "Hello", "World" } );
        adminExtras.putPersistableBundle("persistable_bundle", nestedBundle);
        return adminExtras;
    }
}
