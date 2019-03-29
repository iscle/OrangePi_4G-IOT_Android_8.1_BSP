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

package android.media.cts;

import android.media.cts.R;


import android.content.res.AssetFileDescriptor;
import android.media.JetPlayer;
import android.media.JetPlayer.OnJetEventListener;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.test.AndroidTestCase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class JetPlayerTest extends AndroidTestCase {
    private OnJetEventListener mOnJetEventListener;
    private boolean mOnJetUserIdUpdateCalled;
    private boolean mOnJetPauseUpdateCalled;
    private boolean mOnJetNumQueuedSegmentUpdateCalled;
    private boolean mOnJetEventCalled;
    private String mJetFile;
    /* JetPlayer and Handler will be on the Main Looper */
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private final JetPlayer mJetPlayer = JetPlayer.getJetPlayer();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mOnJetEventListener  = new MockOnJetEventListener();
        mJetFile =
            new File(Environment.getExternalStorageDirectory(), "test.jet").getAbsolutePath();
        assertTrue(JetPlayer.getMaxTracks() > 0);
    }

    @Override
    protected void tearDown() throws Exception {
        // Prevent tests from failing with EAS_ERROR_FILE_ALREADY_OPEN
        // after a previous test fails.
        mJetPlayer.closeJetFile();

        File jetFile = new File(mJetFile);
        if (jetFile.exists()) {
            jetFile.delete();
        }
        super.tearDown();
    }

    public void testLoadJetFromPath() throws Throwable {
        assertTrue(mJetPlayer.clearQueue());
        prepareFile();
        mJetPlayer.setEventListener(mOnJetEventListener);
        assertTrue(mJetPlayer.loadJetFile(mJetFile));
        runJet();
    }

    public void testLoadJetFromFd() throws Throwable {
        assertTrue(mJetPlayer.clearQueue());
        mJetPlayer.setEventListener(mOnJetEventListener, mHandler);
        assertTrue(mJetPlayer.loadJetFile(mContext.getResources().openRawResourceFd(R.raw.test_jet)));
        runJet();
    }

    public void testQueueJetSegmentMuteArray() throws Throwable {
        assertTrue(mJetPlayer.clearQueue());
        mJetPlayer.setEventListener(mOnJetEventListener, mHandler);
        assertTrue(mJetPlayer.loadJetFile(mContext.getResources().openRawResourceFd(R.raw.test_jet)));
        byte userID = 0;
        int segmentNum = 3;
        int libNum = -1;
        int repeatCount = 0;
        int transpose = 0;
        boolean[] muteFlags = new boolean[32];
        assertTrue(mJetPlayer.queueJetSegmentMuteArray(segmentNum, libNum,
                repeatCount, transpose,
                muteFlags, userID));
        assertTrue(mJetPlayer.play());
        for (int i = 0; i < muteFlags.length; i++) {
            muteFlags[i] = true;
        }
        muteFlags[8] = false;
        muteFlags[9] = false;
        muteFlags[10] = false;
        assertTrue(mJetPlayer.queueJetSegmentMuteArray(segmentNum, libNum,
                repeatCount, transpose,
                muteFlags, userID));
        Thread.sleep(20000);
        assertTrue(mJetPlayer.pause());
        assertTrue(mJetPlayer.clearQueue());
        assertFalse(mJetPlayer.play());
        assertTrue(mJetPlayer.closeJetFile());
    }

    private void runJet() throws Throwable {
        byte userID = 0;
        int segmentNum = 3;
        int libNum = -1;
        int repeatCount = 1;
        int transpose = 0;
        int muteFlags = 0;
        assertTrue(mJetPlayer.queueJetSegment(segmentNum, libNum, repeatCount,
                transpose, muteFlags, userID));

        segmentNum = 6;
        repeatCount = 1;
        transpose = -1;
        assertTrue(mJetPlayer.queueJetSegment(segmentNum, libNum, repeatCount,
                transpose, muteFlags, userID));

        segmentNum = 7;
        transpose = 0;
        assertTrue(mJetPlayer.queueJetSegment(segmentNum, libNum, repeatCount,
                transpose, muteFlags, userID));

        for (int i = 0; i < 7; i++) {
            assertTrue(mJetPlayer.triggerClip(i));
        }
        assertTrue(mJetPlayer.play());
        Thread.sleep(10000);
        assertTrue(mJetPlayer.pause());
        assertFalse(mJetPlayer.setMuteArray(new boolean[40], false));
        boolean[] muteArray = new boolean[32];
        for (int i = 0; i < muteArray.length; i++) {
            muteArray[i] = true;
        }
        muteArray[8] = false;
        muteArray[9] = false;
        muteArray[10] = false;
        assertTrue(mJetPlayer.setMuteArray(muteArray, true));
        Thread.sleep(1000);
        assertTrue(mJetPlayer.play());
        Thread.sleep(1000);
        assertTrue(mJetPlayer.setMuteFlag(9, true, true));
        Thread.sleep(1000);
        assertTrue(mJetPlayer.setMuteFlags(0, false));
        Thread.sleep(1000);
        assertTrue(mJetPlayer.setMuteFlags(0xffffffff, false));
        Thread.sleep(1000);
        assertTrue(mJetPlayer.setMuteFlags(0, false));
        Thread.sleep(30000);
        assertTrue(mJetPlayer.pause());
        assertTrue(mJetPlayer.closeJetFile());
        assertTrue(mOnJetEventCalled);
        assertTrue(mOnJetPauseUpdateCalled);
        assertTrue(mOnJetNumQueuedSegmentUpdateCalled);
        assertTrue(mOnJetUserIdUpdateCalled);
    }

    public void testClone() throws Exception {
        try {
            mJetPlayer.clone();
            fail("should throw CloneNotSupportedException");
        } catch (CloneNotSupportedException e) {
            // expect here
        }
    }

    private void prepareFile() throws IOException {
        InputStream source = null;
        OutputStream target = null;
        try {
            source = mContext.getResources().openRawResource(R.raw.test_jet);
            target = new FileOutputStream(mJetFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = source.read(buffer)) != -1) {
                target.write(buffer, 0, length);
            }
        } finally {
            if (source != null) {
                source.close();
            }
            if (target != null) {
                target.close();
            }
        }
    }

    private class MockOnJetEventListener implements OnJetEventListener {

        public void onJetEvent(JetPlayer player, short segment, byte track, byte channel,
                byte controller, byte value) {
            mOnJetEventCalled = true;
        }

        public void onJetNumQueuedSegmentUpdate(JetPlayer player, int nbSegments) {
            mOnJetNumQueuedSegmentUpdateCalled = true;
        }

        public void onJetPauseUpdate(JetPlayer player, int paused) {
            mOnJetPauseUpdateCalled = true;
        }

        public void onJetUserIdUpdate(JetPlayer player, int userId, int repeatCount) {
            mOnJetUserIdUpdateCalled = true;
        }
    }
}
