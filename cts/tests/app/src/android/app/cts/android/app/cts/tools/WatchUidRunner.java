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

package android.app.cts.android.app.cts.tools;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * bit CtsAppTestCases:ActivityManagerProcessStateTest
 */
public class WatchUidRunner {
    public static final int CMD_PROCSTATE = 0;
    public static final int CMD_ACTIVE = 1;
    public static final int CMD_IDLE = 2;
    public static final int CMD_UNCACHED = 3;
    public static final int CMD_CACHED = 4;
    public static final int CMD_GONE = 5;

    static final String[] COMMAND_TO_STRING = new String[] {
            "procstate", "active", "idle", "uncached", "cached", "gone"
    };

    final Instrumentation mInstrumentation;
    final int mUid;
    final String mUidStr;
    final Pattern mSpaceSplitter;
    final ParcelFileDescriptor mReadFd;
    final FileInputStream mReadStream;
    final BufferedReader mReadReader;
    final ParcelFileDescriptor mWriteFd;
    final FileOutputStream mWriteStream;
    final PrintWriter mWritePrinter;
    final Thread mReaderThread;

    // Shared state is protected by this.
    final ArrayList<String[]> mPendingLines = new ArrayList<>();

    boolean mStopping;

    public WatchUidRunner(Instrumentation instrumentation, int uid) {
        mInstrumentation = instrumentation;
        mUid = uid;
        mUidStr = Integer.toString(uid);
        mSpaceSplitter = Pattern.compile("\\s+");
        ParcelFileDescriptor[] pfds = instrumentation.getUiAutomation().executeShellCommandRw(
                "am watch-uids");
        mReadFd = pfds[0];
        mReadStream = new ParcelFileDescriptor.AutoCloseInputStream(mReadFd);
        mReadReader = new BufferedReader(new InputStreamReader(mReadStream));
        mWriteFd = pfds[1];
        mWriteStream = new ParcelFileDescriptor.AutoCloseOutputStream(mWriteFd);
        mWritePrinter = new PrintWriter(new BufferedOutputStream(mWriteStream));
        // Executing a shell command is asynchronous but we can't proceed further with the test
        // until the 'watch-uids' cmd is executed.
        waitUntilUidObserverReady();
        mReaderThread = new ReaderThread();
        mReaderThread.start();
    }

    private void waitUntilUidObserverReady() {
        try {
            final String line = mReadReader.readLine();
            assertTrue("Unexpected output: " + line, line.startsWith("Watching uid states"));
        } catch (IOException e) {
            fail("Error occurred " + e);
        }
    }

    public void expect(int cmd, String procState, long timeout) {
        long waitUntil = SystemClock.uptimeMillis() + timeout;
        String[] line = waitForNextLine(waitUntil);
        if (!COMMAND_TO_STRING[cmd].equals(line[1])) {
            throw new IllegalStateException("Expected cmd " + COMMAND_TO_STRING[cmd]
                    + " but next report was " + Arrays.toString(line));
        }
        if (procState != null && (line.length < 3 || !procState.equals(line[2]))) {
            throw new IllegalStateException("Expected procstate " + procState
                    + " but next report was " + Arrays.toString(line));
        }
    }

    public void waitFor(int cmd, String procState, long timeout) {
        long waitUntil = SystemClock.uptimeMillis() + timeout;
        while (true) {
            String[] line = waitForNextLine(waitUntil);
            if (COMMAND_TO_STRING[cmd].equals(line[1])) {
                if (procState == null) {
                    return;
                }
                if (line.length >= 3 && procState.equals(line[2])) {
                    return;
                } else {
                    Log.d("XXXX", "Skipping because procstate not " + procState + ": "
                            + Arrays.toString(line));
                }
            } else {
                Log.d("XXXX", "Skipping because not " + COMMAND_TO_STRING[cmd] + ": "
                        + Arrays.toString(line));
            }
        }
    }

    String[] waitForNextLine(long waitUntil) {
        synchronized (mPendingLines) {
            while (mPendingLines.size() == 0) {
                long now = SystemClock.uptimeMillis();
                if (now >= waitUntil) {
                    throw new IllegalStateException("Timed out waiting for next line");
                }
                try {
                    mPendingLines.wait(waitUntil - now);
                } catch (InterruptedException e) {
                }
            }
            return mPendingLines.remove(0);
        }
    }

    public void finish() {
        synchronized (mPendingLines) {
            mStopping = true;
        }
        mWritePrinter.println("q");
        try {
            mWriteStream.close();
        } catch (IOException e) {
        }
        try {
            mReadStream.close();
        } catch (IOException e) {
        }
    }

    final class ReaderThread extends Thread {
        String mLastReadLine;

        @Override
        public void run() {
            String[] line;
            try {
                while ((line = readNextLine()) != null) {
                    if (line.length < 2) {
                        Log.d("XXXXX", "Skipping: " + mLastReadLine);
                        continue;
                    }
                    if (!line[0].equals(mUidStr)) {
                        Log.d("XXXXX", "Skipping: " + mLastReadLine);
                        continue;
                    }
                    Log.d("XXXXX", "Enqueueing: " + mLastReadLine);
                    synchronized (mPendingLines) {
                        if (mStopping) {
                            return;
                        }
                        mPendingLines.add(line);
                        mPendingLines.notifyAll();
                    }
                }
            } catch (IOException e) {
                Log.w("WatchUidRunner", "Failed reading", e);
            }
        }

        String[] readNextLine() throws IOException {
            mLastReadLine = mReadReader.readLine();
            if (mLastReadLine == null) {
                return null;
            }
            return mSpaceSplitter.split(mLastReadLine);
        }
    }
}
