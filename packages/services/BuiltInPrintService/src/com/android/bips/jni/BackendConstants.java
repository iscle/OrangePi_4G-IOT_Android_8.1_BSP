/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2014-2106 Mopria Alliance, Inc.
 * Copyright (C) 2013 Hewlett-Packard Development Company, L.P.
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
package com.android.bips.jni;

import android.view.Gravity;

public class BackendConstants {
    public static final String RESOLUTION_300_DPI = "resolution-300-dpi";
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";
    public static final String WPRINT_APPLICATION_ID = "Android";
    public static final String WPRINT_LIBRARY_PREFIX = "wfds";

    public static final int STATUS_OK = 0;

    public static final String PRINT_DOCUMENT_CATEGORY__DOCUMENT = "Doc";
    public static final String PRINT_DOCUMENT_CATEGORY__PHOTO = "Photo";

    public static final String ALIGNMENT = "alignment";

    /** Center horizontally based on the orientation */
    public static final int ALIGN_CENTER_HORIZONTAL_ON_ORIENTATION =
            Gravity.HORIZONTAL_GRAVITY_MASK;

    public static final int ALIGN_CENTER_HORIZONTAL = Gravity.CENTER_HORIZONTAL;
    public static final int ALIGN_CENTER_VERTICIAL = Gravity.CENTER_VERTICAL;

    /** Center horizontally & vertically */
    public static final int ALIGN_CENTER = Gravity.CENTER;

    public static final String JOB_STATE_QUEUED = "print-job-queued";
    public static final String JOB_STATE_RUNNING = "print-job-running";
    public static final String JOB_STATE_BLOCKED = "print-job-blocked";
    public static final String JOB_STATE_DONE = "print-job-complete";
    public static final String JOB_STATE_OTHER = "print-job-unknown";

    public static final String JOB_DONE_OK = "job-success";
    public static final String JOB_DONE_ERROR = "job-failed";
    public static final String JOB_DONE_CANCELLED = "job-cancelled";
    public static final String JOB_DONE_CORRUPT = "job-corrupt";
    public static final String JOB_DONE_OTHER = "job-result-unknown";

    public static final String BLOCKED_REASON__OFFLINE = "device-offline";
    public static final String BLOCKED_REASON__BUSY = "device-busy";
    public static final String BLOCKED_REASON__CANCELLED = "print-job-cancelled";
    public static final String BLOCKED_REASON__OUT_OF_PAPER = "input-media-supply-empty";
    public static final String BLOCKED_REASON__OUT_OF_INK = "marker-ink-empty";
    public static final String BLOCKED_REASON__OUT_OF_TONER = "marker-toner-empty";
    public static final String BLOCKED_REASON__JAMMED = "jam";
    public static final String BLOCKED_REASON__DOOR_OPEN = "cover-open";
    public static final String BLOCKED_REASON__SERVICE_REQUEST = "service-request";
    public static final String BLOCKED_REASON__LOW_ON_INK = "marker-ink-almost-empty";
    public static final String BLOCKED_REASON__LOW_ON_TONER = "marker-toner-almost-empty";
    public static final String BLOCKED_REASON__REALLY_LOW_ON_INK = "marker-ink-really-low";
    public static final String BLOCKED_REASON__UNKNOWN = "unknown";
}