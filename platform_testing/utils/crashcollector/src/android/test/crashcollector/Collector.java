/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.test.crashcollector;

import android.app.IActivityController;
import android.app.IActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;

/**
 * Main class for the crash collector that installs an activity controller to monitor app errors
 */
public class Collector {

    private static final String LOG_TAG = "CrashCollector";
    private static final long CHECK_AM_INTERVAL_MS = 5 * 1000;
    private static final long MAX_CHECK_AM_TIMEOUT_MS = 30 * 1000;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH.mm.ss");
    private static final File TOMBSTONES_PATH = new File("/data/tombstones");
    private HashSet<String> mTombstones = null;

    /**
     * Command-line entry point.
     *
     * @param args The command-line arguments
     */
    public static void main(String[] args) {
        // Set the process name showing in "ps" or "top"
        Process.setArgV0("android.test.crashcollector");

        int resultCode = (new Collector()).run(args);
        System.exit(resultCode);
    }

    /**
     * Command execution entry point
     * @param args
     * @return
     * @throws RemoteException
     */
    public int run(String[] args) {
        // recipient for activity manager death so that command can survive runtime restart
        final IBinder.DeathRecipient death = new DeathRecipient() {
            @Override
            public void binderDied() {
                synchronized (this) {
                    notifyAll();
                }
            }
        };
        IBinder am = blockUntilSystemRunning(MAX_CHECK_AM_TIMEOUT_MS);
        if (am == null) {
            print("FATAL: Cannot get activity manager, is system running?");
            return -1;
        }
        IActivityController controller = new CrashCollector();
        do {
            try {
                // set activity controller
                IActivityManager iam = IActivityManager.Stub.asInterface(am);
                iam.setActivityController(controller, false);
                // register death recipient for activity manager
                am.linkToDeath(death, 0);
            } catch (RemoteException re) {
                print("FATAL: cannot set activity controller, is system running?");
                re.printStackTrace();
                return -1;
            }
            // monitor runtime restart (crash/kill of system server)
            synchronized (death) {
                while (am.isBinderAlive()) {
                    try {
                        Log.d(LOG_TAG, "Monitoring death of system server.");
                        death.wait();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
                Log.w(LOG_TAG, "Detected crash of system server.");
                am = blockUntilSystemRunning(MAX_CHECK_AM_TIMEOUT_MS);
            }
        } while (true);
        // for now running indefinitely, until a better mechanism is found to signal shutdown
    }

    private void print(String line) {
        System.err.println(String.format("%s %s", TIME_FORMAT.format(new Date()), line));
    }

    /**
     * Blocks until system server is running, or timeout has reached
     * @param timeout
     * @return
     */
    private IBinder blockUntilSystemRunning(long timeout) {
        // waiting for activity manager to come back
        long start = SystemClock.uptimeMillis();
        IBinder am = null;
        while (SystemClock.uptimeMillis() - start < MAX_CHECK_AM_TIMEOUT_MS) {
            am = ServiceManager.checkService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                break;
            } else {
                Log.d(LOG_TAG, "activity manager not ready yet, continue waiting.");
                try {
                    Thread.sleep(CHECK_AM_INTERVAL_MS);
                } catch (InterruptedException e) {
                    // break out of current loop upon interruption
                    break;
                }
            }
        }
        return am;
    }

    private boolean checkNativeCrashes() {
        String[] tombstones = TOMBSTONES_PATH.list();

        // shortcut path for usually empty directory, so we don't waste even
        // more objects
        if ((tombstones == null) || (tombstones.length == 0)) {
            mTombstones = null;
            return false;
        }

        // use set logic to look for new files
        HashSet<String> newStones = new HashSet<String>();
        for (String x : tombstones) {
            newStones.add(x);
        }

        boolean result = (mTombstones == null) || !mTombstones.containsAll(newStones);

        // keep the new list for the next time
        mTombstones = newStones;

        return result;
    }

    private class CrashCollector extends IActivityController.Stub {

        @Override
        public boolean activityStarting(Intent intent, String pkg) throws RemoteException {
            // check native crashes when we have a chance
            if (checkNativeCrashes()) {
                print("NATIVE: new tombstones");
            }
            return true;
        }

        @Override
        public boolean activityResuming(String pkg) throws RemoteException {
            // check native crashes when we have a chance
            if (checkNativeCrashes()) {
                print("NATIVE: new tombstones");
            }
            return true;
        }

        @Override
        public boolean appCrashed(String processName, int pid, String shortMsg, String longMsg,
                long timeMillis, String stackTrace) throws RemoteException {
            if (processName == null) {
                print("CRASH: null process name, assuming system");
            } else {
                print("CRASH: " + processName);
            }
            return false;
        }

        @Override
        public int appEarlyNotResponding(String processName, int pid, String annotation)
                throws RemoteException {
            // ignore
            return 0;
        }

        @Override
        public int appNotResponding(String processName, int pid, String processStats)
                throws RemoteException {
            print("ANR: " + processName);
            return -1;
        }

        @Override
        public int systemNotResponding(String msg) throws RemoteException {
            print("WATCHDOG: " + msg);
            return -1;
        }
    }
}
