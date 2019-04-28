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
 * limitations under the License
 */

package com.android.server.telecom;

/**
 * Defines handover state constants for calls undergoing handover.
 */
public class HandoverState {
    private HandoverState() {
        // Can't instantiate.
    }

    public static final int HANDOVER_NONE = 1;
    public static final int HANDOVER_TO_STARTED = 2;
    public static final int HANDOVER_FROM_STARTED = 3;
    public static final int HANDOVER_ACCEPTED = 4;
    public static final int HANDOVER_COMPLETE = 5;
    public static final int HANDOVER_FAILED = 6;

    private static final String HANDOVER_NONE_STR = "NONE";
    private static final String HANDOVER_TO_STARTED_STR = "HANDOVER_TO_STARTED";
    private static final String HANDOVER_FROM_STARTED_STR = "HANDOVER_FROM_STARTED";
    private static final String HANDOVER_ACCEPTED_STR = "HANDOVER_ACCEPTED";
    private static final String HANDOVER_COMPLETE_STR = "HANDOVER_COMPLETE";
    private static final String HANDOVER_FAILED_STR = "HANDOVER_FAILED";

    public static String stateToString(int state) {
        switch (state) {
            case HANDOVER_NONE:
                return HANDOVER_NONE_STR;
            case HANDOVER_TO_STARTED:
                return HANDOVER_TO_STARTED_STR;
            case HANDOVER_FROM_STARTED:
                return HANDOVER_FROM_STARTED_STR;
            case HANDOVER_ACCEPTED:
                return HANDOVER_ACCEPTED_STR;
            case HANDOVER_COMPLETE:
                return HANDOVER_COMPLETE_STR;
            case HANDOVER_FAILED:
                return HANDOVER_FAILED_STR;
        }
        return "";
    }
}
