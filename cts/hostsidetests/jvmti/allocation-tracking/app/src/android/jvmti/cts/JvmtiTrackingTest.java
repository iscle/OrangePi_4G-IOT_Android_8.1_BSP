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

import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;

import art.Main;

/**
 * Check tagging-related functionality.
 */
public class JvmtiTrackingTest extends JvmtiTestBase {

    @Before
    public void setUp() throws Exception {
        // Bind our native methods.
        Main.bindAgentJNI("android/jvmti/cts/JvmtiTrackingTest",
                getClass().getClassLoader());

        prefetchClassNames();
    }

    // Pre-resolve class names so the strings don't have to be allocated as a side effect of
    // callback printing.
    private static void prefetchClassNames() {
        Object.class.getName();
        Integer.class.getName();
        Float.class.getName();
        Short.class.getName();
        Byte.class.getName();
        Double.class.getName();
    }

    private ArrayList<Object> l = new ArrayList<>(100);

    @Test
    public void testTracking() throws Exception {
        // Disable the global registration from OnLoad, to get into a known state.
        enableAllocationTracking(null, false);

        assertEquals(null, getAndResetAllocationTrackingString());

        // Enable actual logging callback.
        setupObjectAllocCallback(true);

        enableAllocationTracking(null, true);

        l.add(new Object());
        l.add(new Integer(1));

        enableAllocationTracking(null, false);

        assertEquals(
                "ObjectAllocated type java.lang.Object/java.lang.Object size 8#"
                        + "ObjectAllocated type java.lang.Integer/java.lang.Integer size 16#",
                        getAndResetAllocationTrackingString());

        l.add(new Float(1.0f));

        assertEquals(null, getAndResetAllocationTrackingString());

        enableAllocationTracking(Thread.currentThread(), true);

        l.add(new Short((short) 0));

        enableAllocationTracking(Thread.currentThread(), false);

        assertEquals("ObjectAllocated type java.lang.Short/java.lang.Short size 16#",
                getAndResetAllocationTrackingString());

        l.add(new Byte((byte) 0));

        assertEquals(null, getAndResetAllocationTrackingString());

        testThread(l, true, true);

        l.add(new Byte((byte) 0));

        assertEquals("ObjectAllocated type java.lang.Double/java.lang.Double size 16#",
                getAndResetAllocationTrackingString());

        testThread(l, true, false);

        assertEquals("ObjectAllocated type java.lang.Double/java.lang.Double size 16#",
                getAndResetAllocationTrackingString());

        System.out.println("Tracking on different thread");

        testThread(l, false, true);

        l.add(new Byte((byte) 0));

        // Disable actual logging callback and re-enable tracking, so we can keep the event enabled
        // and
        // check that shutdown works correctly.
        setupObjectAllocCallback(false);
        enableAllocationTracking(null, true);

        assertEquals(null, getAndResetAllocationTrackingString());
    }

    private static void testThread(final ArrayList<Object> l, final boolean sameThread,
            final boolean disableTracking) throws Exception {
        final SimpleBarrier startBarrier = new SimpleBarrier(1);
        final SimpleBarrier trackBarrier = new SimpleBarrier(1);

        final Thread thisThread = Thread.currentThread();

        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    startBarrier.dec();
                    trackBarrier.waitFor();
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                    System.exit(1);
                }

                l.add(new Double(0.0));

                if (disableTracking) {
                    enableAllocationTracking(sameThread ? this : thisThread, false);
                }
            }
        };

        t.start();
        startBarrier.waitFor();
        enableAllocationTracking(sameThread ? t : Thread.currentThread(), true);
        trackBarrier.dec();

        t.join();
    }

    // Our own little barrier, to avoid behind-the-scenes allocations.
    private static class SimpleBarrier {
        int count;

        public SimpleBarrier(int i) {
            count = i;
        }

        public synchronized void dec() throws Exception {
            count--;
            notifyAll();
        }

        public synchronized void waitFor() throws Exception {
            while (count != 0) {
                wait();
            }
        }
    }

    private static native void setupObjectAllocCallback(boolean enable);

    private static native void enableAllocationTracking(Thread thread, boolean enable);

    private static native String getAndResetAllocationTrackingString();
}
