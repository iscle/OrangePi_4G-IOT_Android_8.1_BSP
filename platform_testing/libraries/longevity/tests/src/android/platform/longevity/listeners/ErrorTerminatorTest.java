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
package android.platform.longevity.listeners;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.support.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.JUnit4;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link ErrorTerminator}.
 */
@RunWith(JUnit4.class)
public class ErrorTerminatorTest {
    private ErrorTerminator mListener;
    @Mock private RunNotifier mNotifier;

    @Before
    public void setupListener() {
        MockitoAnnotations.initMocks(this);
        mListener = new ErrorTerminator(mNotifier);
    }

    /**
     * Unit test the listener's kill logic.
     */
    @Test
    @SmallTest
    public void testErrorTerminator_errors() throws Exception {
        mListener.testFailure(new Failure(Description.EMPTY, new Throwable()));
        verify(mNotifier).pleaseStop();
    }

    /**
     * Unit test the listener's kill logic.
     */
    @Test
    @SmallTest
    public void testErrorTerminator_none() throws Exception {
        mListener.testStarted(Description.EMPTY);
        mListener.testFinished(Description.EMPTY);
        mListener.testFinished(Description.EMPTY);
        mListener.testIgnored(Description.EMPTY);
        verify(mNotifier, never()).pleaseStop();
    }
}
