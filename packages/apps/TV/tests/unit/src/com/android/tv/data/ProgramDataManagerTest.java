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

package com.android.tv.data;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.HandlerThread;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.mock.MockCursor;
import android.util.Log;
import android.util.SparseArray;

import com.android.tv.testing.Constants;
import com.android.tv.testing.FakeClock;
import com.android.tv.testing.ProgramInfo;
import com.android.tv.util.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test for {@link com.android.tv.data.ProgramDataManager}
 */
@SmallTest
public class ProgramDataManagerTest {
    private static final boolean DEBUG = false;
    private static final String TAG = "ProgramDataManagerTest";

    // Wait time for expected success.
    private static final long WAIT_TIME_OUT_MS = 1000L;
    // Wait time for expected failure.
    private static final long FAILURE_TIME_OUT_MS = 300L;

    // TODO: Use TvContract constants, once they become public.
    private static final String PARAM_CHANNEL = "channel";
    private static final String PARAM_START_TIME = "start_time";
    private static final String PARAM_END_TIME = "end_time";

    private ProgramDataManager mProgramDataManager;
    private FakeClock mClock;
    private HandlerThread mHandlerThread;
    private TestProgramDataManagerListener mListener;
    private FakeContentResolver mContentResolver;
    private FakeContentProvider mContentProvider;

    @Before
    public void setUp() {
        mClock = FakeClock.createWithCurrentTime();
        mListener = new TestProgramDataManagerListener();
        mContentProvider = new FakeContentProvider(getTargetContext());
        mContentResolver = new FakeContentResolver();
        mContentResolver.addProvider(TvContract.AUTHORITY, mContentProvider);
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mProgramDataManager = new ProgramDataManager(
                mContentResolver, mClock, mHandlerThread.getLooper());
        mProgramDataManager.setPrefetchEnabled(true);
        mProgramDataManager.addListener(mListener);
    }

    @After
    public void tearDown() {
        mHandlerThread.quitSafely();
        mProgramDataManager.stop();
    }

    private void startAndWaitForComplete() throws InterruptedException {
        mProgramDataManager.start();
        assertTrue(mListener.programUpdatedLatch.await(WAIT_TIME_OUT_MS, TimeUnit.MILLISECONDS));
    }

    /**
     * Test for {@link ProgramInfo#getIndex} and {@link ProgramInfo#getStartTimeMs}.
     */
    @Test
    public void testProgramUtils() {
        ProgramInfo stub = ProgramInfo.create();
        for (long channelId = 1; channelId < Constants.UNIT_TEST_CHANNEL_COUNT; channelId++) {
            int index = stub.getIndex(mClock.currentTimeMillis(), channelId);
            long startTimeMs = stub.getStartTimeMs(index, channelId);
            ProgramInfo programAt = stub.build(InstrumentationRegistry.getContext(), index);
            assertTrue(startTimeMs <= mClock.currentTimeMillis());
            assertTrue(mClock.currentTimeMillis() < startTimeMs + programAt.durationMs);
        }
    }

    /**
     * Test for following methods.
     *
     * <p>
     * {@link ProgramDataManager#getCurrentProgram(long)},
     * {@link ProgramDataManager#getPrograms(long, long)},
     * {@link ProgramDataManager#setPrefetchTimeRange(long)}.
     * </p>
     */
    @Test
    public void testGetPrograms() throws InterruptedException {
        // Initial setup to test {@link ProgramDataManager#setPrefetchTimeRange(long)}.
        long preventSnapDelayMs = ProgramDataManager.PROGRAM_GUIDE_SNAP_TIME_MS * 2;
        long prefetchTimeRangeStartMs = System.currentTimeMillis() + preventSnapDelayMs;
        mClock.setCurrentTimeMillis(prefetchTimeRangeStartMs + preventSnapDelayMs);
        mProgramDataManager.setPrefetchTimeRange(prefetchTimeRangeStartMs);

        startAndWaitForComplete();

        for (long channelId = 1; channelId <= Constants.UNIT_TEST_CHANNEL_COUNT; channelId++) {
            Program currentProgram = mProgramDataManager.getCurrentProgram(channelId);
            // Test {@link ProgramDataManager#getCurrentProgram(long)}.
            assertTrue(currentProgram.getStartTimeUtcMillis() <= mClock.currentTimeMillis()
                    && mClock.currentTimeMillis() <= currentProgram.getEndTimeUtcMillis());

            // Test {@link ProgramDataManager#getPrograms(long)}.
            // Case #1: Normal case
            List<Program> programs =
                    mProgramDataManager.getPrograms(channelId, mClock.currentTimeMillis());
            ProgramInfo stub = ProgramInfo.create();
            int index = stub.getIndex(mClock.currentTimeMillis(), channelId);
            for (Program program : programs) {
                ProgramInfo programInfoAt = stub.build(InstrumentationRegistry.getContext(), index);
                long startTimeMs = stub.getStartTimeMs(index, channelId);
                assertProgramEquals(startTimeMs, programInfoAt, program);
                index++;
            }
            // Case #2: Corner cases where there's a program that starts at the start of the range.
            long startTimeMs = programs.get(0).getStartTimeUtcMillis();
            programs = mProgramDataManager.getPrograms(channelId, startTimeMs);
            assertEquals(startTimeMs, programs.get(0).getStartTimeUtcMillis());

            // Test {@link ProgramDataManager#setPrefetchTimeRange(long)}.
            programs = mProgramDataManager.getPrograms(channelId,
                    prefetchTimeRangeStartMs - TimeUnit.HOURS.toMillis(1));
            for (Program program : programs) {
                assertTrue(program.getEndTimeUtcMillis() >= prefetchTimeRangeStartMs);
            }
        }
    }

    /**
     * Test for following methods.
     *
     * <p>
     * {@link ProgramDataManager#addOnCurrentProgramUpdatedListener},
     * {@link ProgramDataManager#removeOnCurrentProgramUpdatedListener}.
     * </p>
     */
    @Test
    public void testCurrentProgramListener() throws InterruptedException {
        final long testChannelId = 1;
        ProgramInfo stub = ProgramInfo.create();
        int index = stub.getIndex(mClock.currentTimeMillis(), testChannelId);
        // Set current time to few seconds before the current program ends,
        // so we can see if callback is called as expected.
        long nextProgramStartTimeMs = stub.getStartTimeMs(index + 1, testChannelId);
        ProgramInfo nextProgramInfo = stub.build(InstrumentationRegistry.getContext(), index + 1);
        mClock.setCurrentTimeMillis(nextProgramStartTimeMs - (WAIT_TIME_OUT_MS / 2));

        startAndWaitForComplete();
        // Note that changing current time doesn't affect the current program
        // because current program is updated after waiting for the program's duration.
        // See {@link ProgramDataManager#updateCurrentProgram}.
        mClock.setCurrentTimeMillis(mClock.currentTimeMillis() + WAIT_TIME_OUT_MS);
        TestProgramDataManagerOnCurrentProgramUpdatedListener listener =
                new TestProgramDataManagerOnCurrentProgramUpdatedListener();
        mProgramDataManager.addOnCurrentProgramUpdatedListener(testChannelId, listener);
        assertTrue(
                listener.currentProgramUpdatedLatch.await(WAIT_TIME_OUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(testChannelId, listener.updatedChannelId);
        Program currentProgram = mProgramDataManager.getCurrentProgram(testChannelId);
        assertProgramEquals(nextProgramStartTimeMs, nextProgramInfo, currentProgram);
        assertEquals(listener.updatedProgram, currentProgram);
    }

    /**
     * Test if program data is refreshed after the program insertion.
     */
    @Test
    public void testContentProviderUpdate() throws InterruptedException {
        final long testChannelId = 1;
        startAndWaitForComplete();
        // Force program data manager to update program data whenever it's changes.
        mProgramDataManager.setProgramPrefetchUpdateWait(0);
        mListener.reset();
        List<Program> programList =
                mProgramDataManager.getPrograms(testChannelId, mClock.currentTimeMillis());
        assertNotNull(programList);
        long lastProgramEndTime = programList.get(programList.size() - 1).getEndTimeUtcMillis();
        // Make change in content provider
        mContentProvider.simulateAppend(testChannelId);
        assertTrue(mListener.programUpdatedLatch.await(WAIT_TIME_OUT_MS, TimeUnit.MILLISECONDS));
        programList = mProgramDataManager.getPrograms(testChannelId, mClock.currentTimeMillis());
        assertTrue(
                lastProgramEndTime < programList.get(programList.size() - 1).getEndTimeUtcMillis());
    }

    /**
     * Test for {@link ProgramDataManager#setPauseProgramUpdate(boolean)}.
     */
    @Test
    public void testSetPauseProgramUpdate() throws InterruptedException {
        final long testChannelId = 1;
        startAndWaitForComplete();
        // Force program data manager to update program data whenever it's changes.
        mProgramDataManager.setProgramPrefetchUpdateWait(0);
        mListener.reset();
        mProgramDataManager.setPauseProgramUpdate(true);
        mContentProvider.simulateAppend(testChannelId);
        assertFalse(mListener.programUpdatedLatch.await(FAILURE_TIME_OUT_MS,
                TimeUnit.MILLISECONDS));
    }

    public static void assertProgramEquals(long expectedStartTime, ProgramInfo expectedInfo,
            Program actualProgram) {
        assertEquals("title", expectedInfo.title, actualProgram.getTitle());
        assertEquals("episode", expectedInfo.episode, actualProgram.getEpisodeTitle());
        assertEquals("description", expectedInfo.description, actualProgram.getDescription());
        assertEquals("startTime", expectedStartTime, actualProgram.getStartTimeUtcMillis());
        assertEquals("endTime", expectedStartTime + expectedInfo.durationMs,
                actualProgram.getEndTimeUtcMillis());
    }

    private class FakeContentResolver extends MockContentResolver {
        @Override
        public void notifyChange(Uri uri, ContentObserver observer, boolean syncToNetwork) {
            super.notifyChange(uri, observer, syncToNetwork);
            if (DEBUG) {
                Log.d(TAG, "onChanged(uri=" + uri + ")");
            }
            if (observer != null) {
                observer.dispatchChange(false, uri);
            } else {
                mProgramDataManager.getContentObserver().dispatchChange(false, uri);
            }
        }
    }

    private static class ProgramInfoWrapper {
        private final int index;
        private final long startTimeMs;
        private final ProgramInfo programInfo;

        public ProgramInfoWrapper(int index, long startTimeMs, ProgramInfo programInfo) {
            this.index = index;
            this.startTimeMs = startTimeMs;
            this.programInfo = programInfo;
        }
    }

    // This implements the minimal methods in content resolver
    // and detailed assumptions are written in each method.
    private class FakeContentProvider extends MockContentProvider {
        private final SparseArray<List<ProgramInfoWrapper>> mProgramInfoList = new SparseArray<>();

        /**
         * Constructor for FakeContentProvider
         * <p>
         * This initializes program info assuming that
         * channel IDs are 1, 2, 3, ... {@link Constants#UNIT_TEST_CHANNEL_COUNT}.
         * </p>
         */
        public FakeContentProvider(Context context) {
            super(context);
            long startTimeMs = Utils.floorTime(
                    mClock.currentTimeMillis() - ProgramDataManager.PROGRAM_GUIDE_SNAP_TIME_MS,
                    ProgramDataManager.PROGRAM_GUIDE_SNAP_TIME_MS);
            long endTimeMs = startTimeMs + (ProgramDataManager.PROGRAM_GUIDE_MAX_TIME_RANGE / 2);
            for (int i = 1; i <= Constants.UNIT_TEST_CHANNEL_COUNT; i++) {
                List<ProgramInfoWrapper> programInfoList = new ArrayList<>();
                ProgramInfo stub = ProgramInfo.create();
                int index = stub.getIndex(startTimeMs, i);
                long programStartTimeMs = stub.getStartTimeMs(index, i);
                while (programStartTimeMs < endTimeMs) {
                    ProgramInfo programAt = stub.build(InstrumentationRegistry.getContext(), index);
                    programInfoList.add(
                            new ProgramInfoWrapper(index, programStartTimeMs, programAt));
                    index++;
                    programStartTimeMs += programAt.durationMs;
                }
                mProgramInfoList.put(i, programInfoList);
            }
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            if (DEBUG) {
                Log.d(TAG, "dump query");
                Log.d(TAG, "  uri=" + uri);
                Log.d(TAG, "  projection=" + Arrays.toString(projection));
                Log.d(TAG, "  selection=" + selection);
            }
            long startTimeMs = Long.parseLong(uri.getQueryParameter(PARAM_START_TIME));
            long endTimeMs = Long.parseLong(uri.getQueryParameter(PARAM_END_TIME));
            if (startTimeMs == 0 || endTimeMs == 0) {
                throw new UnsupportedOperationException();
            }
            assertProgramUri(uri);
            long channelId;
            try {
                channelId = Long.parseLong(uri.getQueryParameter(PARAM_CHANNEL));
            } catch (NumberFormatException e) {
                channelId = -1;
            }
            return new FakeCursor(projection, channelId, startTimeMs, endTimeMs);
        }

        /**
         * Simulate program data appends at the end of the existing programs.
         * This appends programs until the maximum program query range
         * ({@link ProgramDataManager#PROGRAM_GUIDE_MAX_TIME_RANGE})
         * where we started with the inserting half of it.
         */
        public void simulateAppend(long channelId) {
            long endTimeMs =
                    mClock.currentTimeMillis() + ProgramDataManager.PROGRAM_GUIDE_MAX_TIME_RANGE;
            List<ProgramInfoWrapper> programList = mProgramInfoList.get((int) channelId);
            if (mProgramInfoList == null) {
                return;
            }
            ProgramInfo stub = ProgramInfo.create();
            ProgramInfoWrapper last = programList.get(programList.size() - 1);
            while (last.startTimeMs < endTimeMs) {
                ProgramInfo nextProgramInfo = stub.build(InstrumentationRegistry.getContext(),
                        last.index + 1);
                ProgramInfoWrapper next = new ProgramInfoWrapper(last.index + 1,
                        last.startTimeMs + last.programInfo.durationMs, nextProgramInfo);
                programList.add(next);
                last = next;
            }
            mContentResolver.notifyChange(TvContract.Programs.CONTENT_URI, null);
        }

        private void assertProgramUri(Uri uri) {
            assertTrue("Uri(" + uri + ") isn't channel uri",
                    uri.toString().startsWith(TvContract.Programs.CONTENT_URI.toString()));
        }

        public ProgramInfoWrapper get(long channelId, int position) {
            List<ProgramInfoWrapper> programList = mProgramInfoList.get((int) channelId);
            if (programList == null || position >= programList.size()) {
                return null;
            }
            return programList.get(position);
        }
    }

    private class FakeCursor extends MockCursor {
        private final String[] ALL_COLUMNS =  {
                TvContract.Programs.COLUMN_CHANNEL_ID,
                TvContract.Programs.COLUMN_TITLE,
                TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
                TvContract.Programs.COLUMN_EPISODE_TITLE,
                TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
                TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS};
        private final String[] mColumns;
        private final boolean mIsQueryForSingleChannel;
        private final long mStartTimeMs;
        private final long mEndTimeMs;
        private final int mCount;
        private long mChannelId;
        private int mProgramPosition;
        private ProgramInfoWrapper mCurrentProgram;

        /**
         * Constructor
         * @param columns the same as projection passed from {@link FakeContentProvider#query}.
         *                Can be null for query all.
         * @param channelId channel ID to query programs belongs to the specified channel.
         *                  Can be negative to indicate all channels.
         * @param startTimeMs start of the time range to query programs.
         * @param endTimeMs end of the time range to query programs.
         */
        public FakeCursor(String[] columns, long channelId, long startTimeMs, long endTimeMs) {
            mColumns = (columns == null) ? ALL_COLUMNS : columns;
            mIsQueryForSingleChannel = (channelId > 0);
            mChannelId = channelId;
            mProgramPosition = -1;
            mStartTimeMs = startTimeMs;
            mEndTimeMs = endTimeMs;
            int count = 0;
            while (moveToNext()) {
                count++;
            }
            mCount = count;
            // Rewind channel Id and program index.
            mChannelId = channelId;
            mProgramPosition = -1;
            if (DEBUG) {
                Log.d(TAG, "FakeCursor(columns=" + Arrays.toString(columns)
                        + ", channelId=" + channelId + ", startTimeMs=" + startTimeMs
                        + ", endTimeMs=" + endTimeMs + ") has mCount=" + mCount);
            }
        }

        @Override
        public String getColumnName(int columnIndex) {
            return mColumns[columnIndex];
        }

        @Override
        public int getColumnIndex(String columnName) {
            for (int i = 0; i < mColumns.length; i++) {
                if (mColumns[i].equalsIgnoreCase(columnName)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int getInt(int columnIndex) {
            if (DEBUG) {
                Log.d(TAG, "Column (" + getColumnName(columnIndex) + ") is ignored in getInt()");
            }
            return 0;
        }

        @Override
        public long getLong(int columnIndex) {
            String columnName = getColumnName(columnIndex);
            switch (columnName) {
                case TvContract.Programs.COLUMN_CHANNEL_ID:
                    return mChannelId;
                case TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS:
                    return mCurrentProgram.startTimeMs;
                case TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS:
                    return mCurrentProgram.startTimeMs + mCurrentProgram.programInfo.durationMs;
            }
            if (DEBUG) {
                Log.d(TAG, "Column (" + columnName + ") is ignored in getLong()");
            }
            return 0;
        }

        @Override
        public String getString(int columnIndex) {
            String columnName = getColumnName(columnIndex);
            switch (columnName) {
                case TvContract.Programs.COLUMN_TITLE:
                    return mCurrentProgram.programInfo.title;
                case TvContract.Programs.COLUMN_SHORT_DESCRIPTION:
                    return mCurrentProgram.programInfo.description;
                case TvContract.Programs.COLUMN_EPISODE_TITLE:
                    return mCurrentProgram.programInfo.episode;
            }
            if (DEBUG) {
                Log.d(TAG, "Column (" + columnName + ") is ignored in getString()");
            }
            return null;
        }

        @Override
        public int getCount() {
            return mCount;
        }

        @Override
        public boolean moveToNext() {
            while (true) {
                ProgramInfoWrapper program = mContentProvider.get(mChannelId, ++mProgramPosition);
                if (program == null || program.startTimeMs >= mEndTimeMs) {
                    if (mIsQueryForSingleChannel) {
                        return false;
                    } else {
                        if (++mChannelId > Constants.UNIT_TEST_CHANNEL_COUNT) {
                            return false;
                        }
                        mProgramPosition = -1;
                    }
                } else if (program.startTimeMs + program.programInfo.durationMs >= mStartTimeMs) {
                    mCurrentProgram = program;
                    break;
                }
            }
            return true;
        }

        @Override
        public void close() {
            // No-op.
        }
    }

    private class TestProgramDataManagerListener implements ProgramDataManager.Listener {
        public CountDownLatch programUpdatedLatch = new CountDownLatch(1);

        @Override
        public void onProgramUpdated() {
            programUpdatedLatch.countDown();
        }

        public void reset() {
            programUpdatedLatch = new CountDownLatch(1);
        }
    }

    private class TestProgramDataManagerOnCurrentProgramUpdatedListener implements
            OnCurrentProgramUpdatedListener {
        public final CountDownLatch currentProgramUpdatedLatch = new CountDownLatch(1);
        public long updatedChannelId = -1;
        public Program updatedProgram = null;

        @Override
        public void onCurrentProgramUpdated(long channelId, Program program) {
            updatedChannelId = channelId;
            updatedProgram = program;
            currentProgramUpdatedLatch.countDown();
        }
    }
}
