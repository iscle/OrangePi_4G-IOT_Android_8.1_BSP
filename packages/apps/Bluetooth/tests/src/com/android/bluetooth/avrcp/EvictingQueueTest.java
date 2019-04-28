package com.android.bluetooth.avrcp;

import android.test.AndroidTestCase;

import junit.framework.Assert;

/** Unit tests for {@link EvictingQueue}. */
public class EvictingQueueTest extends AndroidTestCase {
    public void testEvictingQueue_canAddItems() {
        EvictingQueue<Integer> e = new EvictingQueue<Integer>(10);

        e.add(1);

        assertEquals((long) e.size(), (long) 1);
    }

    public void testEvictingQueue_maxItems() {
        EvictingQueue<Integer> e = new EvictingQueue<Integer>(5);

        e.add(1);
        e.add(2);
        e.add(3);
        e.add(4);
        e.add(5);
        e.add(6);

        assertEquals((long) e.size(), (long) 5);
        // Items drop off the front
        assertEquals((long) e.peek(), (long) 2);
    }

    public void testEvictingQueue_frontDrop() {
        EvictingQueue<Integer> e = new EvictingQueue<Integer>(5);

        e.add(1);
        e.add(2);
        e.add(3);
        e.add(4);
        e.add(5);

        assertEquals((long) e.size(), (long) 5);

        e.addFirst(6);

        assertEquals((long) e.size(), (long) 5);
        assertEquals((long) e.peek(), (long) 1);
    }
}
