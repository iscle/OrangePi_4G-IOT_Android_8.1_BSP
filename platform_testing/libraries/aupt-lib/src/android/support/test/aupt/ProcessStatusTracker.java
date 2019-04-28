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

package android.support.test.aupt;

import android.app.UiAutomation;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;

public class ProcessStatusTracker implements IProcessStatusTracker {
    private static final String TAG = "ProcessStatusTracker";

    // Example return line from "adb shell ps" for the pattern below
    // USER     PID     PPID    VSIZE   RSS     WCHAN       PC          NAME
    // root     1       0       2216    804     sys_epoll_  00000000 S  com.android.chrome
    private final String PS_PATTERN =
            "\\w+\\s+(\\d+)\\s+\\w+\\s+\\d+\\s+\\d+\\s+\\w+\\s+[0-9a-f]+\\s+\\w+\\s+(.+)";
    private final Pattern PS_PATTERN_MATCH = Pattern.compile(PS_PATTERN);
    private final int PS_PATTERN_PID_GROUP = 1;
    private final int PS_PATTERN_PKG_GROUP = 2;

    private Map<String, Integer> mPidTracker;
    private Set<String> mPidExclusions;

    public ProcessStatusTracker(String[] processes) {
        mPidTracker = new HashMap<String, Integer>();
        mPidExclusions = new HashSet<String>();
        if (processes == null) return;
        for (String proc : processes) {
            addMonitoredProcess(proc);
        }
    }

    @Override
    public void addMonitoredProcess(String processName) {
        if (mPidTracker.containsKey(processName)) {
            throw new IllegalArgumentException("Process already being monitored: " + processName);
        }
        mPidTracker.put(processName, -1);
        // don't track right away, until told to
        mPidExclusions.add(processName);
    }

    @Override
    public List<ProcessDetails> getProcessDetails() {
        if (mPidTracker == null || mPidTracker.isEmpty()) {
            // nothing to track
            Log.v(TAG, "getProcessDetails - No pids to track, not doing anything");
            return null;
        }
        List<ProcessDetails> ret = new ArrayList<ProcessDetails>();
        List<RunningAppProcessInfo> runningApps = null;
        // Refactored to use shell commands, not ActivityManager
        runningApps = getRunningAppProcesses();
        if (runningApps == null || runningApps.isEmpty()) {
            Log.e(TAG, "Failed to retrieve list of running apps");
            return ret;
        }
        // used for keeping track of which process has died
        Set<String> deadProcesses = new HashSet<String>(mPidTracker.keySet());
        Log.i(TAG, "Got running processes");
        for (RunningAppProcessInfo info : runningApps) {
            if (mPidTracker.containsKey(info.processName)
                    && !mPidExclusions.contains(info.processName)) {
                ProcessDetails detail = new ProcessDetails();
                detail.processName = info.processName;
                detail.pid0 = info.pid;
                // this is a process that we are monitoring
                int pid = mPidTracker.get(info.processName);
                if (pid == -1) {
                    mPidTracker.put(info.processName, info.pid);
                    Log.d(TAG, String.format(
                            "pid detected - %s : %d", info.processName, info.pid));
                    // all good with this process
                    deadProcesses.remove(info.processName);
                    detail.processStatus = ProcessStatus.PROC_STARTED;
                } else if (pid == info.pid) {
                    // pid hasn't changed, all good
                    deadProcesses.remove(info.processName);
                    detail.processStatus = ProcessStatus.PROC_OK;
                } else {
                    //pid changed
                    deadProcesses.remove(info.processName);
                    detail.processStatus = ProcessStatus.PROC_RESTARTED;
                    detail.pid1 = pid;
                }
                ret.add(detail);
            }
        }
        for (String proc : deadProcesses) {
            ProcessDetails detail = new ProcessDetails();
            detail.processName = proc;
            // check the remaining ones are dead, or not started
            int pid = mPidTracker.get(proc);
            if (pid != -1) {
                detail.processStatus = ProcessStatus.PROC_DIED;
            } else {
                detail.processStatus = ProcessStatus.PROC_NOT_STARTED;
            }
            ret.add(detail);
        }
        return ret;
    }

    @Override
    public void setAllowProcessTracking(String processName) {
        if (mPidTracker == null || mPidTracker.isEmpty()) {
            // nothing to track
            return;
        }
        // ignore those not under monitoring
        if (mPidExclusions.contains(processName)) {
            mPidExclusions.remove(processName);
            Log.v(TAG, "Started tracking pid changes: " + processName);
        }
        verifyRunningProcess();
    }

    @Override
    public void verifyRunningProcess() {
        Log.i(TAG, "Getting process details");
        List<ProcessDetails> details = getProcessDetails();
        if (details == null) {
            return;
        }
        for (ProcessDetails d :  details) {
            Log.v(TAG, String.format("verifyRunningProcess - proc: %s, state: %s",
                    d.processName, d.processStatus.toString()));
            switch (d.processStatus) {
                case PROC_OK:
                case PROC_NOT_STARTED:
                    // no op
                    break;
                case PROC_STARTED:
                    Log.v(TAG, String.format("Process started: %s - %d", d.processName, d.pid0));
                    break;
                case PROC_DIED: {
                    String msg = String.format("Process %s has died.", d.processName);
                    throw new AuptTerminator(msg);
                }
                case PROC_RESTARTED: {
                    String msg = String.format("Process %s restarted: %d -> %d", d.processName,
                            d.pid1, d.pid0);
                    throw new AuptTerminator(msg);
                }
                default:
                    break;
            }
        }
    }

    /**
     * Enumerates the list of processes to be tracked and returns a list of RunningAppProcessInfo
     * objects with pkg name and pid's set to the process's current information
     * @result a List of RunningAppProcessInfo objects with pkg and pid set
     */
    public List<RunningAppProcessInfo> getRunningAppProcesses() {
        List<RunningAppProcessInfo> results = new ArrayList<RunningAppProcessInfo>();

        Set<String> procSet = mPidTracker.keySet();
        // Enumerate status for all currently tracked processes
        for (String proc : procSet) {
            // Execute shell command and parse results
            BufferedReader stream = executeShellCommand("ps");
            try {
                String line;
                while ((line = stream.readLine()) != null) {
                    Matcher matcher = PS_PATTERN_MATCH.matcher(line);
                    // Find a line that matches the process name exactly
                    if (matcher.matches() && matcher.group(PS_PATTERN_PKG_GROUP).equals(proc)) {
                        int pid = Integer.valueOf(matcher.group(PS_PATTERN_PID_GROUP));
                        results.add(new RunningAppProcessInfo(proc, pid, null));
                    }
                }
            } catch (IOException exception) {
                Log.e(TAG, "Error with buffered reader", exception);
                return null;
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (IOException exception) {
                    Log.e(TAG, "Error with closing the stream", exception);
                }
            }
        }

        return results;
    }

    // TODO: Create subclass for shell commands used by this and GraphicsStatsMonitor

    /**
     * UiAutomation is included solely for the purpose of executing shell commands
     */
    private UiAutomation mUiAutomation;

    /**
     * Executes a shell command through UiAutomation and puts the results in an
     * InputStreamReader that is returned inside a BufferedReader.
     * @param command the command to be executed in the adb shell
     * @result a BufferedReader that reads the command output
     */
    public BufferedReader executeShellCommand (String command) {
        ParcelFileDescriptor stdout = getUiAutomation().executeShellCommand(command);

        BufferedReader stream = new BufferedReader(new InputStreamReader(
                new ParcelFileDescriptor.AutoCloseInputStream(stdout)));
        return stream;
    }

    /**
     * Sets the UiAutomation member for shell execution
     */
    public void setUiAutomation (UiAutomation uiAutomation) {
        mUiAutomation = uiAutomation;
    }

    /**
     * @return UiAutomation instance from Aupt instrumentation
     */
    public UiAutomation getUiAutomation () {
        return mUiAutomation;
    }
}
