/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.support.test.metricshelper;

import static junit.framework.Assert.assertTrue;

import android.metrics.LogMaker;
import android.metrics.MetricsReader;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Useful test utilities for metrics tests.
 */
public class MetricsAsserts {

    /**
     * Assert unless there is a log with the matching category and with ACTION type.
     */
    public static void assertHasActionLog(String message, MetricsReader reader, int view) {
        reader.read(0);
        assertHasActionLog(message, new ReaderQueue(reader), view);
    }
    /**
     * Assert unless there is a log with the matching category and with ACTION type.
     */
    public static void assertHasActionLog(String message, Queue<LogMaker> queue, int view) {
        Queue<LogMaker> logs = findMatchingLogs(queue,
                new LogMaker(view)
                        .setType(MetricsEvent.TYPE_ACTION));
        assertTrue(message, !logs.isEmpty());
    }

    /**
     * Assert unless there is a log with the matching category and with visibility type.
     */
    public static void assertHasVisibilityLog(String message, MetricsReader reader,
            int view, boolean visible) {
        reader.read(0);
        assertHasVisibilityLog(message, new ReaderQueue(reader), view, visible);
    }

    /**
     * Assert unless there is a log with the matching category and with visibility type.
     */
    public static void assertHasVisibilityLog(String message, Queue<LogMaker> queue,
            int view, boolean visible) {
        Queue<LogMaker> logs = findMatchingLogs(queue,
                new LogMaker(view)
                        .setType(visible ? MetricsEvent.TYPE_OPEN : MetricsEvent.TYPE_CLOSE));
        assertTrue(message, !logs.isEmpty());
    }

    /**
     * @returns logs that have at least all the matching fields in the template.
     */
    public static Queue<LogMaker> findMatchingLogs(MetricsReader reader, LogMaker template) {
        reader.read(0);
        return findMatchingLogs(new ReaderQueue(reader), template);
    }

    /**
     * @returns logs that have at least all the matching fields in the template.
     */
    public static Queue<LogMaker> findMatchingLogs(Queue<LogMaker> queue, LogMaker template) {
        LinkedList<LogMaker> logs = new LinkedList<>();
        if (template == null) {
            return logs;
        }
        while (!queue.isEmpty()) {
            LogMaker b = queue.poll();
            if (template.isSubsetOf(b)) {
                logs.push(b);
            }
        }
        return logs;
    }

    /**
     * Assert unless there is at least one  log that matches the template.
     */
    public static void assertHasLog(String message, MetricsReader reader, LogMaker expected) {
        reader.read(0);
        assertHasLog(message, new ReaderQueue(reader), expected);
    }

    /**
     * Assert unless there is at least one  log that matches the template.
     */
    public static void assertHasLog(String message, Queue<LogMaker> queue, LogMaker expected) {
        assertTrue(message, !findMatchingLogs(queue, expected).isEmpty());
    }

    private static class ReaderQueue implements Queue<LogMaker> {

        private final MetricsReader mMetricsReader;

        ReaderQueue(MetricsReader metricsReader) {
            mMetricsReader = metricsReader;
        }

        @Override
        public boolean isEmpty() {
            return !mMetricsReader.hasNext();
        }

        @Override
        public LogMaker poll() {
            return mMetricsReader.next();
        }

        @Override
        public boolean add(LogMaker logMaker) {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public boolean addAll(Collection<? extends LogMaker> collection) {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public boolean contains(Object object) {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public boolean containsAll(Collection<?> collection) {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public Iterator<LogMaker> iterator() {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public boolean remove(Object object) {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public boolean removeAll(Collection<?> collection) {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public boolean retainAll(Collection<?> collection) {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public Object[] toArray() {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public <T> T[] toArray(T[] array) {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public boolean offer(LogMaker logMaker) {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public LogMaker remove() {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public LogMaker element() {
            throw new UnsupportedOperationException("unimplemented fake method");
        }

        @Override
        public LogMaker peek() {
            throw new UnsupportedOperationException("unimplemented fake method");
        }
    }
}
