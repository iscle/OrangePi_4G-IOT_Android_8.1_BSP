/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package android.support.test.aupt;

import java.util.List;

/**
 * Interface that defines contract of tracking process ids
 */
public interface IProcessStatusTracker {

    public enum ProcessStatus {PROC_DIED, PROC_RESTARTED, PROC_NOT_STARTED, PROC_STARTED, PROC_OK};

    public class ProcessDetails {
        public ProcessStatus processStatus;
        public String processName;
        public int pid0, pid1;
    }

    /**
     * add the named process to watch list
     * @param processName name of the application process
     */
    public void addMonitoredProcess(String processName);
    /**
     * get the details of processes on the watch list
     * @return a list of {@link ProcessDetails} describing each process on the watch list
     */
    public List<ProcessDetails> getProcessDetails();
    /**
     * Enable monitoring of process id changes of the named process
     *
     * Initially all process should be disabled for monitoring pid changes, since it may be running
     * as a bg service and may be disposed of at anytime. Once an app has been launched into
     * foreground, it should be enabled for pid monitoring
     * @param processName
     */
    public void setAllowProcessTracking(String processName);

    /**
     * Perform a check of all running processes.
     *
     * Optionally throw exceptions if one of watched processes has died
     */
    public void verifyRunningProcess();
}
