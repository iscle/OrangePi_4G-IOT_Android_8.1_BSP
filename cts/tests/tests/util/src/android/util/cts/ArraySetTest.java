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

package android.util.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.ArraySet;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

// As is the case with ArraySet itself, ArraySetTest borrows heavily from ArrayMapTest.

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ArraySetTest {
    private static final String TAG = "ArraySetTest";

    private static final boolean DEBUG = false;

    private static final int OP_ADD = 1;
    private static final int OP_REM = 2;

    private static int[] OPS = new int[] {
            OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD,
            OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD,
            OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM,
            OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM,

            OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD,
            OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM,

            OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD,
            OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM,

            OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD,
            OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM,

            OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD,
            OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD, OP_ADD,
            OP_ADD, OP_ADD, OP_ADD,
            OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM,
            OP_REM, OP_REM, OP_REM,
            OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM, OP_REM,
    };

    private static int[] KEYS = new int[] {
            // General adding and removing.
              -1,   1900,    600,    200,   1200,   1500,   1800,    100,   1900,
            2100,    300,    800,    600,   1100,   1300,   2000,   1000,   1400,
             600,     -1,   1900,    600,    300,   2100,    200,    800,    800,
            1800,   1500,   1300,   1100,   2000,   1400,   1000,   1200,   1900,

            // Shrink when removing item from end.
             100,    200,    300,    400,    500,    600,    700,    800,    900,
             900,    800,    700,    600,    500,    400,    300,    200,    100,

            // Shrink when removing item from middle.
             100,    200,    300,    400,    500,    600,    700,    800,    900,
             900,    800,    700,    600,    500,    400,    200,    300,    100,

            // Shrink when removing item from front.
             100,    200,    300,    400,    500,    600,    700,    800,    900,
             900,    800,    700,    600,    500,    400,    100,    200,    300,

            // Test hash collisions.
             105,    106,    108,    104,    102,    102,    107,      5,    205,
               4,    202,    203,      3,      5,    101,    109,    200,    201,
               0,     -1,    100,
             106,    108,    104,    102,    103,    105,    107,    101,    109,
              -1,    100,      0,
               4,      5,      3,      5,    200,    203,    202,    201,    205,
    };

    public static class ControlledHash {
        final int mValue;

        ControlledHash(int value) {
            mValue = value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            return mValue == ((ControlledHash)o).mValue;
        }

        @Override
        public final int hashCode() {
            return mValue/100;
        }

        @Override
        public final String toString() {
            return Integer.toString(mValue);
        }
    }

    private static boolean compare(Object v1, Object v2) {
        if (v1 == null) {
            return v2 == null;
        }
        if (v2 == null) {
            return false;
        }
        return v1.equals(v2);
    }

    private static <E> void compareSets(HashSet<E> set, ArraySet<E> array) {
        assertEquals("Bad size", set.size(), array.size());

        // Check that every entry in HashSet is in ArraySet.
        for (E entry : set) {
            assertTrue("ArraySet missing value: " + entry, array.contains(entry));
        }

        // Check that every entry in ArraySet is in HashSet using ArraySet.iterator().
        for (E entry : array) {
            assertTrue("ArraySet (via iterator) has unexpected value: " + entry,
                    set.contains(entry));
        }

        // Check that every entry in ArraySet is in HashSet using ArraySet.valueAt().
        for (int i = 0; i < array.size(); ++i) {
            E entry = array.valueAt(i);
            assertTrue("ArraySet (via valueAt) has unexpected value: " + entry,
                    set.contains(entry));
        }

        if (set.hashCode() != array.hashCode()) {
            assertEquals("Set hash codes differ", set.hashCode(), array.hashCode());
        }

        assertTrue("HashSet.equals(ArraySet) failed", set.equals(array));
        assertTrue("ArraySet.equals(HashSet) failed", array.equals(set));

        assertTrue("HashSet.containsAll(ArraySet) failed", set.containsAll(array));
        assertTrue("ArraySet.containsAll(HashSet) failed", array.containsAll(set));
    }

    private static <E> void compareArraySetAndRawArray(ArraySet<E> arraySet, Object[] rawArray) {
        assertEquals("Bad size", arraySet.size(), rawArray.length);
        for (int i = 0; i < rawArray.length; ++i) {
            assertEquals("ArraySet<E> and raw array unequal at index " + i,
                    arraySet.valueAt(i), rawArray[i]);
        }
    }

    private static <E> void validateArraySet(ArraySet<E> array) {
        int index = 0;
        Iterator<E> iter = array.iterator();
        while (iter.hasNext()) {
            E value = iter.next();
            E realValue = array.valueAt(index);
            if (!compare(realValue, value)) {
                fail("Bad array set entry: expected " + realValue
                        + ", got " + value + " at index " + index);
            }
            index++;
        }

        assertEquals("Length of iteration was unequal to size()", array.size(), index);
    }

    private static <E> void dump(HashSet<E> set, ArraySet<E> array) {
        Log.e(TAG, "HashSet of " + set.size() + " entries:");
        for (E entry : set) {
            Log.e(TAG, "    " + entry);
        }
        Log.e(TAG, "ArraySet of " + array.size() + " entries:");
        for (int i = 0; i < array.size(); i++) {
            Log.e(TAG, "    " + array.valueAt(i));
        }
    }

    private static void dump(ArraySet set1, ArraySet set2) {
        Log.e(TAG, "ArraySet of " + set1.size() + " entries:");
        for (int i = 0; i < set1.size(); i++) {
            Log.e(TAG, "    " + set1.valueAt(i));
        }
        Log.e(TAG, "ArraySet of " + set2.size() + " entries:");
        for (int i = 0; i < set2.size(); i++) {
            Log.e(TAG, "    " + set2.valueAt(i));
        }
    }

    @Test
    public void testTest() {
        assertEquals("OPS and KEYS must be equal length", OPS.length, KEYS.length);
    }

    @Test
    public void testBasicArraySet() {
        HashSet<ControlledHash> hashSet = new HashSet<>();
        ArraySet<ControlledHash> arraySet = new ArraySet<>();

        for (int i = 0; i < OPS.length; i++) {
            ControlledHash key = KEYS[i] < 0 ? null : new ControlledHash(KEYS[i]);
            switch (OPS[i]) {
                case OP_ADD:
                    if (DEBUG) Log.i(TAG, "Adding key: " + key);
                    boolean hashAdded = hashSet.add(key);
                    boolean arrayAdded = arraySet.add(key);
                    assertEquals("Adding key " + key + " was not symmetric in HashSet and "
                            + "ArraySet", hashAdded, arrayAdded);
                    break;
                case OP_REM:
                    if (DEBUG) Log.i(TAG, "Removing key: " + key);
                    boolean hashRemoved = hashSet.remove(key);
                    boolean arrayRemoved = arraySet.remove(key);
                    assertEquals("Removing key " + key + " was not symmetric in HashSet and "
                            + "ArraySet", hashRemoved, arrayRemoved);
                    break;
                default:
                    fail("Bad operation " + OPS[i] + " @ " + i);
                    return;
            }
            if (DEBUG) dump(hashSet, arraySet);

            try {
                validateArraySet(arraySet);
            } catch (Throwable e) {
                Log.e(TAG, e.getMessage());
                dump(hashSet, arraySet);
                throw e;
            }

            try {
                compareSets(hashSet, arraySet);
            } catch (Throwable e) {
                Log.e(TAG, e.getMessage());
                dump(hashSet, arraySet);
                throw e;
            }
        }

        // Check to see if HashSet.iterator().remove() works as expected.
        arraySet.add(new ControlledHash(50000));
        ControlledHash lookup = new ControlledHash(50000);
        Iterator<ControlledHash> it = arraySet.iterator();
        while (it.hasNext()) {
            if (it.next().equals(lookup)) {
                it.remove();
            }
        }
        if (arraySet.contains(lookup)) {
            String msg = "Bad ArraySet iterator: didn't remove test key";
            Log.e(TAG, msg);
            dump(hashSet, arraySet);
            fail(msg);
        }

        Log.e(TAG, "Test successful; printing final map.");
        dump(hashSet, arraySet);
    }

    @Test
    public void testCopyArraySet() {
        // set copy constructor test
        ArraySet newSet = new ArraySet<Integer>();
        for (int i = 0; i < 10; ++i) {
            newSet.add(i);
        }

        ArraySet copySet = new ArraySet(newSet);
        if (!compare(copySet, newSet)) {
            String msg = "ArraySet copy constructor failure: expected " +
                    newSet + ", got " + copySet;
            Log.e(TAG, msg);
            dump(newSet, copySet);
            fail(msg);
            return;
        }
    }

    @Test
    public void testEqualsArrayMap() {
        ArraySet<Integer> set1 = new ArraySet<>();
        ArraySet<Integer> set2 = new ArraySet<>();
        HashSet<Integer> set3 = new HashSet<>();
        if (!compare(set1, set2) || !compare(set1, set3) || !compare(set3, set2)) {
            fail("ArraySet equals failure for empty sets " + set1 + ", " +
                    set2 + ", " + set3);
        }

        for (int i = 0; i < 10; ++i) {
            set1.add(i);
            set2.add(i);
            set3.add(i);
        }
        if (!compare(set1, set2) || !compare(set1, set3) || !compare(set3, set2)) {
            fail("ArraySet equals failure for populated sets " + set1 + ", " +
                    set2 + ", " + set3);
        }

        set1.remove(0);
        if (compare(set1, set2) || compare(set1, set3) || compare(set3, set1)) {
            fail("ArraySet equals failure for set size " + set1 + ", " +
                    set2 + ", " + set3);
        }
    }

    @Test
    public void testIsEmpty() {
        ArraySet<Integer> set = new ArraySet<>();
        assertEquals("New ArraySet should have size==0", 0, set.size());
        assertTrue("New ArraySet should be isEmptry", set.isEmpty());

        set.add(3);
        assertEquals("ArraySet has incorrect size", 1, set.size());
        assertFalse("ArraySet should not be isEmptry", set.isEmpty());

        set.remove(3);
        assertEquals("ArraySet should have size==0", 0, set.size());
        assertTrue("ArraySet should be isEmptry", set.isEmpty());
    }

    @Test
    public void testRemoveAt() {
        ArraySet<Integer> set = new ArraySet<>();

        for (int i = 0; i < 10; ++i) {
            set.add(i * 10);
        }

        int indexToDelete = 6;
        assertEquals(10, set.size());
        assertEquals(indexToDelete * 10, set.valueAt(indexToDelete).intValue());
        assertEquals(indexToDelete * 10, set.removeAt(indexToDelete).intValue());
        assertEquals(9, set.size());

        for (int i = 0; i < 9; ++i) {
            int expectedValue = ((i >= indexToDelete) ? (i + 1) : i) * 10;
            assertEquals(expectedValue, set.valueAt(i).intValue());
        }

        for (int i = 9; i > 0; --i) {
            set.removeAt(0);
            assertEquals(i - 1, set.size());
        }

        assertTrue(set.isEmpty());

        try {
            set.removeAt(0);
            fail("Expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException expected) {
            // expected
        }
    }

    @Test
    public void testIndexOf() {
        ArraySet<Integer> set = new ArraySet<>();

        for (int i = 0; i < 10; ++i) {
            set.add(i * 10);
        }

        for (int i = 0; i < 10; ++i) {
            assertEquals("indexOf(" + (i * 10) + ")", i, set.indexOf(i * 10));
        }
    }

    @Test
    public void testAddAll() {
        ArraySet<Integer> arraySet = new ArraySet<>();
        ArraySet<Integer> testArraySet = new ArraySet<>();
        ArrayList<Integer> testArrayList = new ArrayList<>();

        for (int i = 0; i < 10; ++i) {
            testArraySet.add(i * 10);
            testArrayList.add(i * 10);
        }

        assertTrue(arraySet.isEmpty());

        // addAll(ArraySet) has no return value.
        arraySet.addAll(testArraySet);
        assertTrue("ArraySet.addAll(ArraySet) failed", arraySet.containsAll(testArraySet));

        arraySet.clear();
        assertTrue(arraySet.isEmpty());

        // addAll(Collection) returns true if any items were added.
        assertTrue(arraySet.addAll(testArrayList));
        assertTrue("ArraySet.addAll(Container) failed", arraySet.containsAll(testArrayList));
        assertTrue("ArraySet.addAll(Container) failed", arraySet.containsAll(testArraySet));
        // Adding the same Collection should return false.
        assertFalse(arraySet.addAll(testArrayList));
        assertTrue("ArraySet.addAll(Container) failed", arraySet.containsAll(testArrayList));
    }

    @Test
    public void testRemoveAll() {
        ArraySet<Integer> arraySet = new ArraySet<>();
        ArraySet<Integer> arraySetToRemove = new ArraySet<>();
        ArrayList<Integer> arrayListToRemove = new ArrayList<>();

        for (int i = 0; i < 10; ++i) {
            arraySet.add(i * 10);
        }

        for (int i = 6; i < 15; ++i) {
            arraySetToRemove.add(i * 10);
        }

        for (int i = 3; i > -3; --i) {
            arrayListToRemove.add(i * 10);
        }

        assertEquals(10, arraySet.size());

        // Remove [6,14] (really [6,9]) via another ArraySet.
        assertTrue(arraySet.removeAll(arraySetToRemove));
        assertEquals(6, arraySet.size());
        assertFalse(arraySet.removeAll(arraySetToRemove));
        assertEquals(6, arraySet.size());

        // Remove [-2,3] (really [0,3]) via an ArrayList (ie Collection).
        assertTrue(arraySet.removeAll(arrayListToRemove));
        assertEquals(2, arraySet.size());
        assertFalse(arraySet.removeAll(arrayListToRemove));
        assertEquals(2, arraySet.size());

        // Remove the rest of the items.
        ArraySet<Integer> copy = new ArraySet<Integer>(arraySet);
        assertTrue(arraySet.removeAll(copy));
        assertEquals(0, arraySet.size());
        assertFalse(arraySet.removeAll(copy));
        assertEquals(0, arraySet.size());
    }

    @Test
    public void testRetainAll() {
        ArraySet<Integer> arraySet = new ArraySet<>();
        ArrayList<Integer> arrayListToRetain = new ArrayList<>();

        for (int i = 0; i < 10; ++i) {
            arraySet.add(i * 10);
        }

        arrayListToRetain.add(30);
        arrayListToRetain.add(50);
        arrayListToRetain.add(51); // bogus value

        assertEquals(10, arraySet.size());

        assertTrue(arraySet.retainAll(arrayListToRetain));
        assertEquals(2, arraySet.size());

        assertTrue(arraySet.contains(30));
        assertTrue(arraySet.contains(50));
        assertFalse(arraySet.contains(51));

        // Nothing should change.
        assertFalse(arraySet.retainAll(arrayListToRetain));
        assertEquals(2, arraySet.size());
    }

    @Test
    public void testToArray() {
        ArraySet<Integer> arraySet = new ArraySet<>();
        for (int i = 0; i < 10; ++i) {
            arraySet.add(i * 10);
        }

        // Allocate a new array with the right type given a zero-length ephemeral array.
        Integer[] copiedArray = arraySet.toArray(new Integer[0]);
        compareArraySetAndRawArray(arraySet, copiedArray);

        // Allocate a new array with the right type given an undersized array.
        Integer[] undersizedArray = new Integer[5];
        copiedArray = arraySet.toArray(undersizedArray);
        compareArraySetAndRawArray(arraySet, copiedArray);
        assertNotSame(undersizedArray, copiedArray);

        // Use the passed array that is large enough to hold the ArraySet.
        Integer[] rightSizedArray = new Integer[10];
        copiedArray = arraySet.toArray(rightSizedArray);
        compareArraySetAndRawArray(arraySet, copiedArray);
        assertSame(rightSizedArray, copiedArray);

        // Create a new Object[] array.
        Object[] objectArray = arraySet.toArray();
        compareArraySetAndRawArray(arraySet, objectArray);
    }

    @Test
    public void testCanNotIteratePastEnd() {
        ArraySet<String> set = new ArraySet<>();
        set.add("value");
        Iterator<String> iterator = set.iterator();

        assertTrue(iterator.hasNext());
        assertEquals("value", iterator.next());
        assertFalse(iterator.hasNext());

        try {
            iterator.next();
            fail();
        } catch (NoSuchElementException expected) {
        }
    }

}
