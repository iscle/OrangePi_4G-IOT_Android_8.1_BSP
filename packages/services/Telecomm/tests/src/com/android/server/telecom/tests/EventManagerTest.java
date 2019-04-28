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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.telecom.Logging.EventManager;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * Unit tests for android.telecom.Logging.EventManager.
 */

public class EventManagerTest extends TelecomTestCase {

    private EventManager mTestEventManager;
    // A reference to the recently added event record, populated from the eventRecordAdded callback
    private EventManager.EventRecord mAddedEventRecord;

    private static final String TEST_EVENT = "testEvent";
    private static final String TEST_START_EVENT = "testStartEvent";
    private static final String TEST_END_EVENT = "testEndEvent";
    private static final String TEST_TIMED_EVENT = "TimedEvent";
    private static final int TEST_DELAY_TIME = 100; // ms

    private class TestRecord implements EventManager.Loggable {
        private String mId;
        private String mDescription;

        TestRecord(String id, String description) {
            mId = id;
            mDescription = description;
        }

        @Override
        public String getId() {
            return mId;
        }

        @Override
        public String getDescription() {
            return mDescription;
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTestEventManager = new EventManager(() -> "");
        mTestEventManager.registerEventListener((e) -> mAddedEventRecord = e);
    }

    @Override
    public void tearDown() throws Exception {
        mTestEventManager = null;
        mAddedEventRecord = null;
        super.tearDown();
    }

    /**
     * Tests EventManager#addEventRecord to make sure that new events are added properly and that
     * the eventRecordAdded callback is working.
     */
    @SmallTest
    public void testAddEventRecord() throws Exception {
        TestRecord testRecord = new TestRecord("testId", "testDescription");
        mTestEventManager.event(testRecord, TEST_EVENT, null);

        assertNotNull(mAddedEventRecord);
        assertEquals(testRecord, mAddedEventRecord.getRecordEntry());
        assertTrue(mTestEventManager.getEventRecords().contains(mAddedEventRecord));
        assertTrue(mTestEventManager.getCallEventRecordMap().containsKey(
                mAddedEventRecord.getRecordEntry()));
    }

    /**
     * Tests EventManager#addEventRecord for the case when we overflow the cached record entries and
     * the oldest entry is dropped.
     */
    @SmallTest
    public void testAddEventRecordOverflowMaxEvents() throws Exception {
        TestRecord oldestRecordEntry = new TestRecord("id0", "desc0");
        // Add the oldest record separately so that we can verify it is dropped later
        mTestEventManager.event(oldestRecordEntry, TEST_EVENT, null);
        // Record the EventRecord created by the oldest event
        assertNotNull(mAddedEventRecord);
        EventManager.EventRecord oldestRecord = mAddedEventRecord;
        for (int i = 1; i < EventManager.DEFAULT_EVENTS_TO_CACHE; i++) {
            mTestEventManager.event(new TestRecord("id" + i, "desc" + i), TEST_EVENT, null);
        }

        // Add a new event that overflows the cache
        TestRecord overflowRecord = new TestRecord("newestId", "newestDesc");
        // Add the oldest record separately so that we can verify it is dropped later
        mTestEventManager.event(overflowRecord, TEST_EVENT, null);

        assertFalse(mTestEventManager.getEventRecords().contains(oldestRecord));
        assertTrue(mTestEventManager.getEventRecords().contains(mAddedEventRecord));
    }

    /**
     * Tests the restructuring of the record entry queue when it is changed (usually in debugging).
     * If the queue is resized to be smaller, the oldest records are dropped.
     */
    @SmallTest
    public void testChangeQueueSize() throws Exception {
        TestRecord oldestRecordEntry = new TestRecord("id0", "desc0");
        // Add the oldest record separately so that we can verify it is dropped later
        mTestEventManager.event(oldestRecordEntry, TEST_EVENT, null);
        // Record the EventRecord created by the oldest event
        assertNotNull(mAddedEventRecord);
        EventManager.EventRecord oldestRecord = mAddedEventRecord;
        for (int i = 1; i < EventManager.DEFAULT_EVENTS_TO_CACHE; i++) {
            mTestEventManager.event(new TestRecord("id" + i, "desc" + i), TEST_EVENT, null);
        }

        mTestEventManager.changeEventCacheSize(EventManager.DEFAULT_EVENTS_TO_CACHE - 1);

        assertFalse(mTestEventManager.getEventRecords().contains(oldestRecord));
        // Check to make sure the other event records are there (id1-9)
        LinkedBlockingQueue<EventManager.EventRecord> eventRecords =
                mTestEventManager.getEventRecords();
        for (int i = 1; i < EventManager.DEFAULT_EVENTS_TO_CACHE; i++) {
            final int index = i;
            List<EventManager.EventRecord> filteredEvent = eventRecords.stream()
                    .filter(e -> e.getRecordEntry().getId().equals("id" + index))
                    .collect(Collectors.toList());
            assertEquals(1, filteredEvent.size());
            assertEquals("desc" + index, filteredEvent.get(0).getRecordEntry().getDescription());
        }
    }

    /**
     * Tests adding TimedEventPairs and generating the paired events as well as verifies that the
     * timing response is correct.
     */
    @SmallTest
    public void testExtractEventTimings() throws Exception {
        TestRecord testRecord = new TestRecord("testId", "testDesc");
        // Add unassociated event
        mTestEventManager.event(testRecord, TEST_EVENT, null);
        mTestEventManager.addRequestResponsePair(new EventManager.TimedEventPair(TEST_START_EVENT,
                TEST_END_EVENT, TEST_TIMED_EVENT));

        // Add Start/End Event
        mTestEventManager.event(testRecord, TEST_START_EVENT, null);
        try {
            Thread.sleep(TEST_DELAY_TIME);
        } catch (InterruptedException ignored) { }
        mTestEventManager.event(testRecord, TEST_END_EVENT, null);

        // Verify that the events were captured and that the timing is correct.
        List<EventManager.EventRecord.EventTiming> timings =
                mAddedEventRecord.extractEventTimings();
        assertEquals(1, timings.size());
        assertEquals(TEST_TIMED_EVENT, timings.get(0).name);
        // Verify that the timing is correct with a +-10 ms buffer
        assertTrue(timings.get(0).time >= TEST_DELAY_TIME - 10);
        assertTrue(timings.get(0).time <= TEST_DELAY_TIME + 10);
    }

    /**
     * Verify that adding events to different records does not create a valid TimedEventPair
     */
    @SmallTest
    public void testExtractEventTimingsDifferentRecords() throws Exception {
        TestRecord testRecord = new TestRecord("testId", "testDesc");
        TestRecord testRecord2 = new TestRecord("testId2", "testDesc2");
        mTestEventManager.addRequestResponsePair(new EventManager.TimedEventPair(TEST_START_EVENT,
                TEST_END_EVENT, TEST_TIMED_EVENT));

        // Add Start event for two separate records
        mTestEventManager.event(testRecord, TEST_START_EVENT, null);
        EventManager.EventRecord eventRecord1 = mAddedEventRecord;
        mTestEventManager.event(testRecord2, TEST_END_EVENT, null);
        EventManager.EventRecord eventRecord2 = mAddedEventRecord;

        // Verify that the events were captured and that the timing is correct.
        List<EventManager.EventRecord.EventTiming> timings1 =
                eventRecord1.extractEventTimings();
        List<EventManager.EventRecord.EventTiming> timings2 =
                eventRecord2.extractEventTimings();
        assertEquals(0, timings1.size());
        assertEquals(0, timings2.size());
    }
}