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

package android.hardware.cts.helpers.sensorverification;

import android.hardware.cts.helpers.TestSensorEvent;

import java.util.Collection;
import java.util.List;

/**
 * Abstract class that deals with the synchronization of the sensor verifications.
 */
public abstract class AbstractSensorVerification implements ISensorVerification {

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void addSensorEvents(Collection<TestSensorEvent> events) {
        for (TestSensorEvent event : events) {
            addSensorEventInternal(event);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract ISensorVerification clone();

    /**
     * Used by implementing classes to add a sensor event.
     */
    protected abstract void addSensorEventInternal(TestSensorEvent event);

    protected <TEvent extends IndexedEvent> int[] getIndexArray(List<TEvent> indexedEvents) {
        int eventsCount = indexedEvents.size();
        int[] indices = new int[eventsCount];
        for (int i = 0; i < eventsCount; i++) {
            indices[i] = indexedEvents.get(i).index;
        }
        return indices;
    }

    /**
     * Helper class to store the index and current event.
     * Events are added to the verification in the order they are generated, the index represents
     * the position of the given event, in the list of added events.
     */
    protected class IndexedEvent {
        public final int index;
        public final TestSensorEvent event;

        public IndexedEvent(int index, TestSensorEvent event) {
            this.index = index;
            this.event = event;
        }
    }

    /**
     * Helper class to store the index, previous event, and current event.
     */
    protected class IndexedEventPair extends IndexedEvent {
        public final TestSensorEvent previousEvent;

        public IndexedEventPair(int index, TestSensorEvent event, TestSensorEvent previousEvent) {
            super(index, event);
            this.previousEvent = previousEvent;
        }
    }

    protected double nanosToMillis(long nanos) {
        return nanos/(1000.0 * 1000.0);
    }
}
