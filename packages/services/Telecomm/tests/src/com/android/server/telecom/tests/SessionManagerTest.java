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

import android.telecom.Logging.Session;
import android.telecom.Logging.SessionManager;
import android.test.suitebuilder.annotation.SmallTest;

import java.lang.ref.WeakReference;

/**
 * Unit tests for android.telecom.Logging.SessionManager
 */

public class SessionManagerTest extends TelecomTestCase {

    private static final String TEST_PARENT_NAME = "testParent";
    private static final int TEST_PARENT_THREAD_ID = 0;
    private static final String TEST_CHILD_NAME = "testChild";
    private static final int TEST_CHILD_THREAD_ID = 1;
    private static final int TEST_DELAY_TIME = 100; // ms

    private SessionManager mTestSessionManager;
    // Used to verify sessionComplete callback
    private long mfullSessionCompleteTime = Session.UNDEFINED;
    private String mFullSessionMethodName = "";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTestSessionManager = new SessionManager();
        mTestSessionManager.registerSessionListener(((sessionName, timeMs) -> {
            mfullSessionCompleteTime = timeMs;
            mFullSessionMethodName = sessionName;
        }));
        // Remove automatic stale session cleanup for testing
        mTestSessionManager.mCleanStaleSessions = null;
    }

    @Override
    public void tearDown() throws Exception {
        mFullSessionMethodName = "";
        mfullSessionCompleteTime = Session.UNDEFINED;
        mTestSessionManager = null;
        super.tearDown();
    }

    /**
     * Starts a Session on the current thread and verifies that it exists in the HashMap
     */
    @SmallTest
    public void testStartSession() {
        assertTrue(mTestSessionManager.mSessionMapper.isEmpty());

        // Set the thread Id to 0
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);

        Session testSession = mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID);
        assertEquals(TEST_PARENT_NAME, testSession.getShortMethodName());
        assertFalse(testSession.isSessionCompleted());
        assertFalse(testSession.isStartedFromActiveSession());
    }

    /**
     * Starts two sessions in the same thread. The first session will be parented to the second
     * session and the second session will be attached to that thread ID.
     */
    @SmallTest
    public void testStartInvisibleChildSession() {
        assertTrue(mTestSessionManager.mSessionMapper.isEmpty());

        // Set the thread Id to 0 for the parent
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);
        // Create invisible child session - same Thread ID as parent
        mTestSessionManager.startSession(TEST_CHILD_NAME, null);

        // There should only be one session in the mapper (the child)
        assertEquals(1, mTestSessionManager.mSessionMapper.size());
        Session testChildSession = mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID);
        assertEquals( TEST_CHILD_NAME, testChildSession.getShortMethodName());
        assertTrue(testChildSession.isStartedFromActiveSession());
        assertNotNull(testChildSession.getParentSession());
        assertEquals(TEST_PARENT_NAME, testChildSession.getParentSession().getShortMethodName());
        assertFalse(testChildSession.isSessionCompleted());
        assertFalse(testChildSession.getParentSession().isSessionCompleted());
    }

    /**
     * End the active Session and verify that it is completed and removed from mSessionMapper.
     */
    @SmallTest
    public void testEndSession() {
        assertTrue(mTestSessionManager.mSessionMapper.isEmpty());
        // Set the thread Id to 0
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);
        Session testSession = mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID);

        assertEquals(1, mTestSessionManager.mSessionMapper.size());
        try {
            // Make sure execution time is > 0
            Thread.sleep(1);
        } catch (InterruptedException ignored) {}
        mTestSessionManager.endSession();

        assertTrue(testSession.isSessionCompleted());
        assertTrue(testSession.getLocalExecutionTime() > 0);
        assertTrue(mTestSessionManager.mSessionMapper.isEmpty());
    }

    /**
     * Ends an active invisible child session and verifies that the parent session is moved back
     * into mSessionMapper.
     */
    @SmallTest
    public void testEndInvisibleChildSession() {
        assertTrue(mTestSessionManager.mSessionMapper.isEmpty());
        // Set the thread Id to 0 for the parent
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);
        // Create invisible child session - same Thread ID as parent
        mTestSessionManager.startSession(TEST_CHILD_NAME, null);
        Session testChildSession = mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID);

        mTestSessionManager.endSession();

        // There should only be one session in the mapper (the parent)
        assertEquals(1, mTestSessionManager.mSessionMapper.size());
        Session testParentSession = mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID);
        assertEquals(TEST_PARENT_NAME, testParentSession.getShortMethodName());
        assertFalse(testParentSession.isStartedFromActiveSession());
        assertTrue(testChildSession.isSessionCompleted());
        assertFalse(testParentSession.isSessionCompleted());
    }

    /**
     * Creates a subsession (child Session) of the current session and prepares it to be continued
     * in a different thread.
     */
    @SmallTest
    public void testCreateSubsession() {
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);

        Session testSession = mTestSessionManager.createSubsession();

        assertEquals(1, mTestSessionManager.mSessionMapper.size());
        Session parentSession = mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID);
        assertNotNull(testSession.getParentSession());
        assertEquals(TEST_PARENT_NAME, testSession.getParentSession().getShortMethodName());
        assertEquals(TEST_PARENT_NAME, parentSession.getShortMethodName());
        assertTrue(parentSession.getChildSessions().contains(testSession));
        assertFalse(testSession.isSessionCompleted());
        assertFalse(testSession.isStartedFromActiveSession());
        assertTrue(testSession.getChildSessions().isEmpty());
    }

    /**
     * Cancels a subsession that was started before it was continued and verifies that it is
     * marked as completed and never added to mSessionMapper.
     */
    @SmallTest
    public void testCancelSubsession() {
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);
        Session parentSession = mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID);
        Session testSession = mTestSessionManager.createSubsession();

        mTestSessionManager.cancelSubsession(testSession);

        assertTrue(testSession.isSessionCompleted());
        assertFalse(parentSession.isSessionCompleted());
        assertEquals(Session.UNDEFINED, testSession.getLocalExecutionTime());
        assertNull(testSession.getParentSession());
    }


    /**
     * Continues a subsession in a different thread and verifies that both the new subsession and
     * its parent are in mSessionMapper.
     */
    @SmallTest
    public void testContinueSubsession() {
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);
        Session parentSession = mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID);
        Session testSession = mTestSessionManager.createSubsession();

        mTestSessionManager.mCurrentThreadId = () -> TEST_CHILD_THREAD_ID;
        mTestSessionManager.continueSession(testSession, TEST_CHILD_NAME);

        assertEquals(2, mTestSessionManager.mSessionMapper.size());
        assertEquals(testSession, mTestSessionManager.mSessionMapper.get(TEST_CHILD_THREAD_ID));
        assertEquals(parentSession, testSession.getParentSession());
        assertFalse(parentSession.isStartedFromActiveSession());
        assertFalse(parentSession.isSessionCompleted());
        assertFalse(testSession.isSessionCompleted());
        assertFalse(testSession.isStartedFromActiveSession());
    }

    /**
     * Ends a subsession that exists in a different thread and verifies that it is completed and
     * no longer exists in mSessionMapper.
     */
    @SmallTest
    public void testEndSubsession() {
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);
        Session parentSession = mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID);
        Session testSession = mTestSessionManager.createSubsession();
        mTestSessionManager.mCurrentThreadId = () -> TEST_CHILD_THREAD_ID;
        mTestSessionManager.continueSession(testSession, TEST_CHILD_NAME);

        mTestSessionManager.endSession();

        assertTrue(testSession.isSessionCompleted());
        assertNull(mTestSessionManager.mSessionMapper.get(TEST_CHILD_THREAD_ID));
        assertFalse(parentSession.isSessionCompleted());
        assertEquals(parentSession, mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID));
    }

    /**
     * When there are subsessions in multiple threads, the parent session may end before the
     * subsessions themselves. When the subsession ends, we need to recursively clean up the parent
     * sessions that are complete as well and note the completion time of the entire chain.
     */
    @SmallTest
    public void testEndSubsessionWithParentComplete() {
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);
        Session parentSession = mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID);
        Session childSession = mTestSessionManager.createSubsession();
        mTestSessionManager.mCurrentThreadId = () -> TEST_CHILD_THREAD_ID;
        mTestSessionManager.continueSession(childSession, TEST_CHILD_NAME);
        // Switch to the parent session ID and end the session.
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.endSession();
        assertTrue(parentSession.isSessionCompleted());
        assertFalse(childSession.isSessionCompleted());

        mTestSessionManager.mCurrentThreadId = () -> TEST_CHILD_THREAD_ID;
        try {
            Thread.sleep(TEST_DELAY_TIME);
        } catch (InterruptedException ignored) {}
        mTestSessionManager.endSession();

        assertEquals(0, mTestSessionManager.mSessionMapper.size());
        assertTrue(parentSession.getChildSessions().isEmpty());
        assertNull(childSession.getParentSession());
        assertTrue(childSession.isSessionCompleted());
        assertEquals(TEST_PARENT_NAME, mFullSessionMethodName);
        // Reduce flakiness by assuming that the true completion time is within a threshold of
        // +-10 ms
        assertTrue(mfullSessionCompleteTime >= TEST_DELAY_TIME - 10);
        assertTrue(mfullSessionCompleteTime <= TEST_DELAY_TIME + 10);
    }

    /**
     * Tests that starting an external session packages up the parent session information and
     * correctly generates the child session.
     */
    @SmallTest
    public void testStartExternalSession() {
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);
        Session.Info sessionInfo =
                mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID).getInfo();
        mTestSessionManager.mCurrentThreadId = () -> TEST_CHILD_THREAD_ID;

        mTestSessionManager.startExternalSession(sessionInfo, TEST_CHILD_NAME);

        Session externalSession = mTestSessionManager.mSessionMapper.get(TEST_CHILD_THREAD_ID);
        assertNotNull(externalSession);
        assertFalse(externalSession.isSessionCompleted());
        assertEquals(TEST_CHILD_NAME, externalSession.getShortMethodName());
        // First subsession of the parent external Session, so the session will be _0.
        assertEquals("0", externalSession.getSessionId());
    }

    /**
     * Verifies that ending an external session tears down the session correctly and removes the
     * external session from mSessionMapper.
     */
    @SmallTest
    public void testEndExternalSession() {
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);
        Session.Info sessionInfo =
                mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID).getInfo();
        mTestSessionManager.mCurrentThreadId = () -> TEST_CHILD_THREAD_ID;
        mTestSessionManager.startExternalSession(sessionInfo, TEST_CHILD_NAME);
        Session externalSession = mTestSessionManager.mSessionMapper.get(TEST_CHILD_THREAD_ID);

        try {
            // Make sure execution time is > 0
            Thread.sleep(1);
        } catch (InterruptedException ignored) {}
        mTestSessionManager.endSession();

        assertTrue(externalSession.isSessionCompleted());
        assertTrue(externalSession.getLocalExecutionTime() > 0);
        assertNull(mTestSessionManager.mSessionMapper.get(TEST_CHILD_THREAD_ID));
    }

    /**
     * Verifies that the callback to inform that the top level parent Session has completed is not
     * the external Session, but the one subsession underneath.
     */
    @SmallTest
    public void testEndExternalSessionListenerCallback() {
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);
        Session.Info sessionInfo =
                mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID).getInfo();
        mTestSessionManager.mCurrentThreadId = () -> TEST_CHILD_THREAD_ID;
        mTestSessionManager.startExternalSession(sessionInfo, TEST_CHILD_NAME);

        try {
            // Make sure execution time is recorded correctly
            Thread.sleep(TEST_DELAY_TIME);
        } catch (InterruptedException ignored) {}
        mTestSessionManager.endSession();

        assertEquals(TEST_CHILD_NAME, mFullSessionMethodName);
        assertTrue(mfullSessionCompleteTime >= TEST_DELAY_TIME - 10);
        assertTrue(mfullSessionCompleteTime <= TEST_DELAY_TIME + 10);
    }

    /**
     * Verifies that the recursive method for getting the full ID works correctly.
     */
    @SmallTest
    public void testFullMethodPath() {
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);
        Session testSession = mTestSessionManager.createSubsession();
        mTestSessionManager.mCurrentThreadId = () -> TEST_CHILD_THREAD_ID;
        mTestSessionManager.continueSession(testSession, TEST_CHILD_NAME);

        String fullId = mTestSessionManager.getSessionId();

        assertTrue(fullId.contains(TEST_PARENT_NAME + Session.SUBSESSION_SEPARATION_CHAR
                + TEST_CHILD_NAME));
    }

    /**
     * Make sure that the cleanup timer runs correctly and the GC collects the stale sessions
     * correctly to ensure that there are no dangling sessions.
     */
    @SmallTest
    public void testStaleSessionCleanupTimer() {
        mTestSessionManager.mCurrentThreadId = () -> TEST_PARENT_THREAD_ID;
        mTestSessionManager.startSession(TEST_PARENT_NAME, null);
        WeakReference<Session> sessionRef = new WeakReference<>(
                mTestSessionManager.mSessionMapper.get(TEST_PARENT_THREAD_ID));
        try {
            // Make sure that the sleep time is always > delay time.
            Thread.sleep(2 * TEST_DELAY_TIME);
            mTestSessionManager.cleanupStaleSessions(TEST_DELAY_TIME);
            Runtime.getRuntime().gc();
            // Give it a second for GC to run.
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}

        assertTrue(mTestSessionManager.mSessionMapper.isEmpty());
        assertNull(sessionRef.get());
    }
}
