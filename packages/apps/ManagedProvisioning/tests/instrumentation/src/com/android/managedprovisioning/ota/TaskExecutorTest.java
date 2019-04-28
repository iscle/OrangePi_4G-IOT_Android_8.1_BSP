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

package com.android.managedprovisioning.ota;

import android.content.Context;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.task.AbstractProvisioningTask;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link TaskExecutor}.
 */
@SmallTest
public class TaskExecutorTest {
    private final int TEST_USER_ID = 123;

    @Mock private Context mContext;
    @Mock private AbstractProvisioningTask mTask1;
    @Mock private AbstractProvisioningTask mTask2;

    private TaskExecutor mExecutor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mExecutor = new TaskExecutor();
    }

    @Test
    public void testExecute_firstTask() {
        // WHEN executing the first task
        mExecutor.execute(TEST_USER_ID, mTask1);

        // THEN run method of the task should be called
        verify(mTask1).run(TEST_USER_ID);
    }

    @Test
    public void testExecute_twoTask() {
        // WHEN executing the first task
        mExecutor.execute(TEST_USER_ID, mTask1);

        // THEN run method of the task should be called
        verify(mTask1).run(TEST_USER_ID);

        // WHEN executing a second task
        mExecutor.execute(TEST_USER_ID, mTask2);

        // THEN run method of the task should be called
        verify(mTask2).run(TEST_USER_ID);
    }
}
