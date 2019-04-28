/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips.ipp;

import android.text.TextUtils;

import com.android.bips.R;
import com.android.bips.jni.BackendConstants;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class JobStatus {
    public static final int ID_UNKNOWN = -1;

    /** Maps backend blocked reason codes to string resource IDs */
    private static final Map<String, Integer> sBlockReasonsMap = new HashMap<>();

    static {
        sBlockReasonsMap.put(BackendConstants.BLOCKED_REASON__DOOR_OPEN,
                R.string.printer_door_open);
        sBlockReasonsMap.put(BackendConstants.BLOCKED_REASON__JAMMED, R.string.printer_jammed);
        sBlockReasonsMap.put(BackendConstants.BLOCKED_REASON__OUT_OF_PAPER,
                R.string.printer_out_of_paper);
        sBlockReasonsMap.put(BackendConstants.BLOCKED_REASON__SERVICE_REQUEST,
                R.string.printer_check);
        sBlockReasonsMap.put(BackendConstants.BLOCKED_REASON__OUT_OF_INK,
                R.string.printer_out_of_ink);
        sBlockReasonsMap.put(BackendConstants.BLOCKED_REASON__OUT_OF_TONER,
                R.string.printer_out_of_toner);
        sBlockReasonsMap.put(BackendConstants.BLOCKED_REASON__LOW_ON_INK,
                R.string.printer_low_on_ink);
        sBlockReasonsMap.put(BackendConstants.BLOCKED_REASON__REALLY_LOW_ON_INK,
                R.string.printer_low_on_ink);
        sBlockReasonsMap.put(BackendConstants.BLOCKED_REASON__LOW_ON_TONER,
                R.string.printer_low_on_toner);
        sBlockReasonsMap.put(BackendConstants.BLOCKED_REASON__BUSY, R.string.printer_busy);
        sBlockReasonsMap.put(BackendConstants.BLOCKED_REASON__OFFLINE, R.string.printer_offline);
    }

    private int mId;
    private String mJobState;
    private String mJobResult;
    private final Set<String> mBlockedReasons;

    /** Create a new, blank job status */
    public JobStatus() {
        mId = ID_UNKNOWN;
        mBlockedReasons = new LinkedHashSet<>();
    }

    /** Create a copy of another object */
    private JobStatus(JobStatus other) {
        mId = other.mId;
        mJobState = other.mJobState;
        mJobResult = other.mJobResult;
        mBlockedReasons = other.mBlockedReasons;
    }

    /** Returns a string resource ID corresponding to a blocked reason, or 0 if none found */
    public int getBlockedReasonId() {
        for (String reason : mBlockedReasons) {
            if (sBlockReasonsMap.containsKey(reason)) {
                return sBlockReasonsMap.get(reason);
            }
        }
        return 0;
    }

    /** Returns a job state (see {@link BackendConstants} JOB_DONE_*}) or null if not known */
    public String getJobState() {
        return mJobState;
    }

    /** Returns a job result (see {@link BackendConstants} JOB_RESULT_*}) or null if not known */
    public String getJobResult() {
        return mJobResult;
    }

    /** Return the job's identifier or ID_UNKNOWN */
    public int getId() {
        return mId;
    }

    /** Return true if the job is in a completion state */
    boolean isJobDone() {
        return !TextUtils.isEmpty(mJobResult);
    }

    @Override
    public String toString() {
        return "JobStatus{id=" + mId +
                ", jobState=" + mJobState +
                ", jobResult=" + mJobResult +
                ", blockedReasons=" + mBlockedReasons +
                "}";
    }

    static class Builder {
        final JobStatus mPrototype;

        Builder() {
            mPrototype = new JobStatus();
        }

        Builder(JobStatus from) {
            mPrototype = new JobStatus(from);
        }

        public Builder setId(int id) {
            mPrototype.mId = id;
            return this;
        }

        Builder setJobState(String jobState) {
            mPrototype.mJobState = jobState;
            return this;
        }

        Builder setJobResult(String jobResult) {
            mPrototype.mJobResult = jobResult;
            return this;
        }

        Builder clearBlockedReasons() {
            mPrototype.mBlockedReasons.clear();
            return this;
        }

        Builder addBlockedReason(String blockedReason) {
            mPrototype.mBlockedReasons.add(blockedReason);
            return this;
        }

        public JobStatus build() {
            return new JobStatus(mPrototype);
        }
    }
}