/*
 * Copyright (C) 2009 The Android Open Source Project
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Process;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.EventLog;
import android.util.EventLog.Event;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class EventLogTest {
    private static final int ANSWER_TAG = 42;
    private static final int PI_TAG = 314;
    private static final int E_TAG = 2718;

    @Test
    public void testWriteEvent() throws Exception {
        long markerData = System.currentTimeMillis();
        EventLog.writeEvent(ANSWER_TAG, markerData);
        EventLog.writeEvent(ANSWER_TAG, 12345);
        EventLog.writeEvent(ANSWER_TAG, 23456L);
        EventLog.writeEvent(ANSWER_TAG, 42.4242f);
        EventLog.writeEvent(ANSWER_TAG, "Test");
        EventLog.writeEvent(ANSWER_TAG, 12345, 23456L, 42.4242f, "Test");

        List<EventLog.Event> events = getEventsAfterMarker(markerData, ANSWER_TAG);
        assertEquals(5, events.size());
        assertEquals(ANSWER_TAG, events.get(0).getTag());
        assertEquals(12345, events.get(0).getData());
        assertEquals(23456L, events.get(1).getData());
        assertEquals(42.4242f, events.get(2).getData());
        assertEquals("Test", events.get(3).getData());

        Object[] arr = (Object[]) events.get(4).getData();
        assertEquals(4, arr.length);
        assertEquals(12345, arr[0]);
        assertEquals(23456L, arr[1]);
        assertEquals(42.4242f, arr[2]);
        assertEquals("Test", arr[3]);
    }

    @Test
    public void testWriteEventWithOversizeValueLimitElision() throws Exception {
        // make sure big events are postsed and only elided to no less than about 4K.
        StringBuilder longString = new StringBuilder();
        for (int i = 0; i < 1000; i++) longString.append("xyzzy");

        Object[] longArray = new Object[1000];
        for (int i = 0; i < 1000; i++) longArray[i] = 12345;

        Long markerData = System.currentTimeMillis();
        EventLog.writeEvent(ANSWER_TAG, markerData);
        EventLog.writeEvent(ANSWER_TAG, longString.toString());
        EventLog.writeEvent(ANSWER_TAG, "hi", longString.toString());
        EventLog.writeEvent(ANSWER_TAG, 12345, longString.toString());
        EventLog.writeEvent(ANSWER_TAG, 12345L, longString.toString());
        EventLog.writeEvent(ANSWER_TAG, 42.4242f, longString.toString());
        EventLog.writeEvent(ANSWER_TAG, longString.toString(), longString.toString());
        EventLog.writeEvent(ANSWER_TAG, longArray);
        List<Event> events = getEventsAfterMarker(markerData, ANSWER_TAG);
        assertEquals(7, events.size());

        final int big = 4000; // expect at least this many bytes to get through.

        // subtract: string header (type + length)
        String val0 = (String) events.get(0).getData();
        assertNull("getData on object 0 raised a WTF", events.get(0).getLastError());
        assertTrue("big string 0 seems short", big < val0.length());

        // subtract: array header, "hi" header, "hi", string header
        Object[] arr1 = (Object[]) events.get(1).getData();
        assertNull("getData on object 1 raised a WTF", events.get(1).getLastError());
        assertEquals(2, arr1.length);
        assertEquals("hi", arr1[0]);
        assertTrue("big string 1 seems short", big < ((String) arr1[1]).length());

        // subtract: array header, int (type + value), string header
        Object[] arr2 = (Object[]) events.get(2).getData();
        assertNull("getData on object 2 raised a WTF", events.get(2).getLastError());
        assertEquals(2, arr2.length);
        assertEquals(12345, arr2[0]);
        assertTrue("big string 2 seems short", big < ((String) arr2[1]).length());

        // subtract: array header, long, string header
        Object[] arr3 = (Object[]) events.get(3).getData();
        assertNull("getData on object 3 raised a WTF", events.get(3).getLastError());
        assertEquals(2, arr3.length);
        assertEquals(12345L, arr3[0]);
        assertTrue("big string 3 seems short", big < ((String) arr3[1]).length());

        // subtract: array header, float, string header
        Object[] arr4 = (Object[]) events.get(4).getData();
        assertNull("getData on object 4 raised a WTF", events.get(4).getLastError());
        assertEquals(2, arr4.length);
        assertEquals(42.4242f, arr4[0]);
        assertTrue("big string 4 seems short", big < ((String) arr4[1]).length());

        // subtract: array header, string header (second string is dropped entirely)
        String string5 = (String) events.get(5).getData();
        assertNull("getData on object 5 raised a WTF", events.get(5).getLastError());
        assertTrue("big string 5 seems short", big < string5.length());

        Object[] arr6 = (Object[]) events.get(6).getData();
        assertNull("getData on object 6 raised a WTF", events.get(6).getLastError());
        assertEquals(255, arr6.length);
        assertEquals(12345, arr6[0]);
        assertEquals(12345, arr6[arr6.length - 1]);
    }

    @Test
    public void testOversizeStringMayBeTruncated() throws Exception {
        // make sure big events elide from the end, not the  from the front or middle.
        StringBuilder longBuilder = new StringBuilder();

        // build a long string where the prefix is never repeated
        for (int step = 1; step < 256; step += 2) { // all odds are relatively prime to 256
            for (int i = 0; i < 255; i++) {
                longBuilder.append(String.valueOf((char) (((step * i) % 256) + 1))); // never emit 0
            }
        }
        String longString = longBuilder.toString(); // 32K

        Long markerData = System.currentTimeMillis();
        EventLog.writeEvent(ANSWER_TAG, markerData);
        EventLog.writeEvent(ANSWER_TAG, longString);

        List<Event> events = getEventsAfterMarker(markerData, ANSWER_TAG);
        assertEquals(1, events.size());

        // subtract: string header (type + length)
        String out = (String) events.get(0).getData();
        assertNull("getData on big string raised a WTF", events.get(0).getLastError());
        assertEquals("output is not a prefix of the input", 0, longString.indexOf(out), 0);
    }

    @Test
    public void testWriteNullEvent() throws Exception {
        Long markerData = System.currentTimeMillis();
        EventLog.writeEvent(ANSWER_TAG, markerData);
        EventLog.writeEvent(ANSWER_TAG, (String) null);
        EventLog.writeEvent(ANSWER_TAG, 12345, null);

        List<EventLog.Event> events = getEventsAfterMarker(markerData, ANSWER_TAG);
        assertEquals(2, events.size());
        assertEquals("NULL", events.get(0).getData());

        Object[] arr = (Object[]) events.get(1).getData();
        assertEquals(2, arr.length);
        assertEquals(12345, arr[0]);
        assertEquals("NULL", arr[1]);
    }

    @Test
    public void testReadDataWhenNone() throws Exception {
        Long markerData = System.currentTimeMillis();
        EventLog.writeEvent(ANSWER_TAG, markerData);
        EventLog.writeEvent(ANSWER_TAG);

        List<EventLog.Event> events = getEventsAfterMarker(markerData, ANSWER_TAG);
        assertEquals(1, events.size());
        assertEquals("getData on empty data did not return null", null, events.get(0).getData());
        assertNull("getData on object 0 raised a WTF", events.get(0).getLastError());
    }

    @Test
    public void testReadEvents() throws Exception {
        Long markerData = System.currentTimeMillis();
        EventLog.writeEvent(ANSWER_TAG, markerData);

        Long data0 = markerData + 1;
        EventLog.writeEvent(ANSWER_TAG, data0);

        Long data1 = data0 + 1;
        EventLog.writeEvent(PI_TAG, data1);

        Long data2 = data1 + 1;
        EventLog.writeEvent(E_TAG, data2);

        List<Event> events = getEventsAfterMarker(markerData, ANSWER_TAG, PI_TAG, E_TAG);
        assertEquals(3, events.size());
        verifyEvent(events.get(0), ANSWER_TAG, data0);
        verifyEvent(events.get(1), PI_TAG, data1);
        verifyEvent(events.get(2), E_TAG, data2);

        events = getEventsAfterMarker(markerData, ANSWER_TAG, E_TAG);
        assertEquals(2, events.size());
        verifyEvent(events.get(0), ANSWER_TAG, data0);
        verifyEvent(events.get(1), E_TAG, data2);

        events = getEventsAfterMarker(markerData, ANSWER_TAG);
        assertEquals(1, events.size());
        verifyEvent(events.get(0), ANSWER_TAG, data0);
    }

    /** Return elements after and the event that has the marker data and matching tag. */
    private List<Event> getEventsAfterMarker(Object marker, int... tags)
            throws IOException, InterruptedException {
        List<Event> events = new ArrayList<>();
        // Give the message some time to show up in the log
        Thread.sleep(20);
        EventLog.readEvents(tags, events);

        for (Iterator<Event> itr = events.iterator(); itr.hasNext(); ) {
            Event event = itr.next();
            itr.remove();
            if (marker.equals(event.getData())) {
                break;
            }
        }

        verifyEventTimes(events);

        return events;
    }

    private void verifyEvent(Event event, int expectedTag, Object expectedData) {
        assertEquals(Process.myPid(), event.getProcessId());
        assertEquals(Process.myTid(), event.getThreadId());
        assertEquals(expectedTag, event.getTag());
        assertEquals(expectedData, event.getData());
    }

    private void verifyEventTimes(List<Event> events) {
        for (int i = 0; i + 1 < events.size(); i++) {
            long time = events.get(i).getTimeNanos();
            long nextTime = events.get(i).getTimeNanos();
            assertTrue(time <= nextTime);
        }
    }

    @Test
    public void testGetTagName() throws Exception {
        assertEquals("answer", EventLog.getTagName(ANSWER_TAG));
        assertEquals("pi", EventLog.getTagName(PI_TAG));
        assertEquals("e", EventLog.getTagName(E_TAG));
        assertEquals(null, EventLog.getTagName(999999999));
    }

    @Test
    public void testGetTagCode() throws Exception {
        assertEquals(ANSWER_TAG, EventLog.getTagCode("answer"));
        assertEquals(PI_TAG, EventLog.getTagCode("pi"));
        assertEquals(E_TAG, EventLog.getTagCode("e"));
        assertEquals(-1, EventLog.getTagCode("does_not_exist"));
    }
}
