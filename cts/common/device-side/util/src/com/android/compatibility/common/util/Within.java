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
 * limitations under the License.
 */
package com.android.compatibility.common.util;

import android.os.SystemClock;

import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoAssertionError;
import org.mockito.internal.verification.api.VerificationData;
import org.mockito.invocation.Invocation;
import org.mockito.verification.VerificationMode;

import java.util.List;

/**
 * Custom verification mode that allows waiting for the specific invocation to happen within
 * a certain time interval. Not that unlike {@link Mockito#timeout(int)}, this mode will not
 * return early and throw exception if the expected method was called with a different set of
 * parameters before the call that we're waiting for.
 */
public class Within implements VerificationMode {
    private static final long TIME_SLICE = 50;
    private final long mTimeout;

    public Within(long timeout) {
        mTimeout = timeout;
    }

    @Override
    public void verify(VerificationData data) {
        long timeout = mTimeout;
        MockitoAssertionError errorToRethrow = null;
        // Loop in the same way we do in PollingCheck, sleeping and then testing for the target
        // invocation
        while (timeout > 0) {
            SystemClock.sleep(TIME_SLICE);

            try {
                final List<Invocation> actualInvocations = data.getAllInvocations();
                // Iterate over all invocations so far to see if we have a match
                for (Invocation invocation : actualInvocations) {
                    if (data.getWanted().matches(invocation)) {
                        // Found our match within our timeout. Mark all invocations as verified
                        markAllInvocationsAsVerified(data);
                        // and return
                        return;
                    }
                }
            } catch (MockitoAssertionError assertionError) {
                errorToRethrow = assertionError;
            }

            timeout -= TIME_SLICE;
        }

        if (errorToRethrow != null) {
            throw errorToRethrow;
        }

        throw new MockitoAssertionError(
                "Timed out while waiting " + mTimeout + "ms for " + data.getWanted().toString());
    }

    // TODO: Uncomment once upgraded to 2.7.13
    // @Override
    public VerificationMode description(String description) {
        // Return this for now.
        // TODO: Return wrapper once upgraded to 2.7.13
        return this;
    }

    private void markAllInvocationsAsVerified(VerificationData data) {
        for (Invocation invocation : data.getAllInvocations()) {
            invocation.markVerified();
            data.getWanted().captureArgumentsFrom(invocation);
        }
    }
}
