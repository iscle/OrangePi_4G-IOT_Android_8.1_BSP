/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package android.jvmti.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import art.Main;

/**
 * Check tagging-related functionality.
 */
public class JvmtiTaggingTest extends JvmtiTestBase {

    private static WeakReference<Object> test() {
        Object o1 = new Object();
        Main.setTag(o1, 1);

        Object o2 = new Object();
        Main.setTag(o2, 2);

        assertEquals(1, Main.getTag(o1));
        assertEquals(2, Main.getTag(o2));

        Runtime.getRuntime().gc();
        Runtime.getRuntime().gc();

        assertEquals(1, Main.getTag(o1));
        assertEquals(2, Main.getTag(o2));

        Runtime.getRuntime().gc();
        Runtime.getRuntime().gc();

        Main.setTag(o1, 10);
        Main.setTag(o2, 20);

        assertEquals(10, Main.getTag(o1));
        assertEquals(20, Main.getTag(o2));

        return new WeakReference<Object>(o1);
    }

    // Very simplistic tagging.
    @Test
    public void testTagging() throws Exception {
        test();
    }

    @Test
    public void testTaggingGC() {
        WeakReference<Object> weak = test();

        Runtime.getRuntime().gc();
        Runtime.getRuntime().gc();

        if (weak.get() != null) {
            throw new RuntimeException("WeakReference not cleared");
        }
    }

    private ArrayList<Object> l;

    @Test
    public void testGetTaggedObjects() {
        // Use an array list to ensure that the objects stay live for a bit. Also gives us a source
        // to compare to. We use index % 10 as the tag.
        l = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            Integer o = new Integer(i);
            l.add(o);
            if (i % 10 != 0) {
                Main.setTag(o, i % 10);
            }
        }

        GetTaggedObjectsExpectation exp1 = new GetTaggedObjectsExpectation(18);
        getTaggedObjectsRun(null, false, false, exp1);

        GetTaggedObjectsExpectation exp2 = new GetTaggedObjectsExpectation(18);
        exp2.add(l.get(1), 1).add(l.get(11), 1).add(l.get(2), 2).add(l.get(12), 2).add(l.get(3), 3)
                .add(l.get(13), 3).add(l.get(4), 4).add(l.get(14), 4).add(l.get(5), 5)
                .add(l.get(15), 5).add(l.get(6), 6).add(l.get(16), 6).add(l.get(7), 7)
                .add(l.get(17), 7).add(l.get(8), 8).add(l.get(18), 8).add(l.get(9), 9)
                .add(l.get(19), 9);
        getTaggedObjectsRun(null, true, true, exp2);

        GetTaggedObjectsExpectation exp3 = new GetTaggedObjectsExpectation(4);
        exp3.add(l.get(2), 2).add(l.get(12), 2).add(l.get(5), 5).add(l.get(15), 5);
        getTaggedObjectsRun(new long[] {2, 5}, true, true, exp3);

        GetTaggedObjectsExpectation exp4 = new GetTaggedObjectsExpectation(18);
        exp4.add(null, 1).add(null, 1).add(null, 2).add(null, 2).add(null, 3).add(null, 3)
                .add(null, 4).add(null, 4).add(null, 5).add(null, 5).add(null, 6).add(null, 6)
                .add(null, 7).add(null, 7).add(null, 8).add(null, 8).add(null, 9).add(null, 9);
        getTaggedObjectsRun(null, false, true, exp4);

        GetTaggedObjectsExpectation exp5 = new GetTaggedObjectsExpectation(18);
        for (int i = 0; i < l.size(); i++) {
            if (i % 10 != 0) {
                exp5.add(l.get(i), 0);
            }
        }
        getTaggedObjectsRun(null, true, false, exp5);

        l = null;
        Runtime.getRuntime().gc();
        Runtime.getRuntime().gc();
    }

    private static void getTaggedObjectsRun(long[] searchTags, boolean returnObjects,
            boolean returnTags, GetTaggedObjectsExpectation exp) {
        Object[] result = getTaggedObjects(searchTags, returnObjects, returnTags);

        Object[] objects = (Object[]) result[0];
        long[] tags = (long[]) result[1];
        int count = (int) result[2];

        exp.check(count, objects, tags);
    }

    private static class GetTaggedObjectsExpectation {
        List<Pair> expectations = new LinkedList<>();
        int count;

        public GetTaggedObjectsExpectation(int c) {
            count = c;
        }

        public void check(int count, Object[] objects, long[] tags) {
            assertEquals(this.count, count);

            if (objects == null && tags == null) {
                assertTrue(expectations.isEmpty());
                return;
            }

            int l1 = objects == null ? 0 : objects.length;
            int l2 = tags == null ? 0 : tags.length;
            int l = Math.max(l1, l2);
            List<Pair> tmp = new ArrayList<>(l);
            for (int i = 0; i < l; i++) {
                tmp.add(new Pair(objects == null ? null : objects[i], tags == null ? 0 : tags[i]));
            }
            Collections.sort(tmp);
            Collections.sort(expectations);

            if (!expectations.equals(tmp)) {
                for (int i = 0; i < expectations.size(); i++) {
                    Pair p1 = expectations.get(i);
                    Pair p2 = tmp.get(i);
                    if (!p1.equals(p2)) {
                        String s = "Not equal: " + p1 + "[" + System.identityHashCode(p1.obj) +
                                "] vs " + p2 + "[" + System.identityHashCode(p2.obj);
                        throw new RuntimeException(s);
                    }
                }
            }

            assertEquals(expectations, tmp);
        }

        public GetTaggedObjectsExpectation add(Object o, long l) {
            expectations.add(new Pair(o, l));
            return this;
        }
    }

    private static class Pair implements Comparable<Pair> {
        Object obj;
        long tag;

        public Pair(Object o, long t) {
            obj = o;
            tag = t;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Pair) {
                Pair p = (Pair)obj;
                return tag == p.tag && (p.obj == null ? this.obj == null : p.obj.equals(this.obj));
            }
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public int compareTo(Pair p) {
            if (tag != p.tag) {
                return Long.compare(tag, p.tag);
            }

            if ((obj instanceof Comparable) && (p.obj instanceof Comparable)) {
                // It's not really correct, but w/e, best effort.
                int result = ((Comparable<Object>) obj).compareTo(p.obj);
                if (result != 0) {
                    return result;
                }
            }

            if (obj != null && p.obj != null) {
                return obj.hashCode() - p.obj.hashCode();
            }

            if (obj != null) {
                return 1;
            }

            if (p.obj != null) {
                return -1;
            }

            return hashCode() - p.hashCode();
        }

        @Override
        public String toString() {
            return "<" + obj + ";" + tag + ">";
        }
    }

    private static native Object[] getTaggedObjects(long[] searchTags, boolean returnObjects,
            boolean returnTags);
}
