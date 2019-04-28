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

package com.android.server.telecom.callfiltering;

public class CallFilteringResult {
    public boolean shouldAllowCall;
    public boolean shouldReject;
    public boolean shouldAddToCallLog;
    public boolean shouldShowNotification;

    public CallFilteringResult(boolean shouldAllowCall, boolean shouldReject, boolean
            shouldAddToCallLog, boolean shouldShowNotification) {
        this.shouldAllowCall = shouldAllowCall;
        this.shouldReject = shouldReject;
        this.shouldAddToCallLog = shouldAddToCallLog;
        this.shouldShowNotification = shouldShowNotification;
    }

    /**
     * Combine this CallFilteringResult with another, returning a CallFilteringResult with
     * the more restrictive properties of the two.
     */
    public CallFilteringResult combine(CallFilteringResult other) {
        if (other == null) {
            return this;
        }

        return new CallFilteringResult(
                shouldAllowCall && other.shouldAllowCall,
                shouldReject || other.shouldReject,
                shouldAddToCallLog && other.shouldAddToCallLog,
                shouldShowNotification && other.shouldShowNotification);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CallFilteringResult that = (CallFilteringResult) o;

        if (shouldAllowCall != that.shouldAllowCall) return false;
        if (shouldReject != that.shouldReject) return false;
        if (shouldAddToCallLog != that.shouldAddToCallLog) return false;
        return shouldShowNotification == that.shouldShowNotification;
    }

    @Override
    public int hashCode() {
        int result = (shouldAllowCall ? 1 : 0);
        result = 31 * result + (shouldReject ? 1 : 0);
        result = 31 * result + (shouldAddToCallLog ? 1 : 0);
        result = 31 * result + (shouldShowNotification ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        if (shouldAllowCall) {
            sb.append("Allow");
        } else if (shouldReject) {
            sb.append("Reject");
        } else {
            sb.append("Ignore");
        }

        if (shouldAddToCallLog) {
            sb.append(", logged");
        }

        if (shouldShowNotification) {
            sb.append(", notified");
        }
        sb.append("]");

        return sb.toString();
    }
}
