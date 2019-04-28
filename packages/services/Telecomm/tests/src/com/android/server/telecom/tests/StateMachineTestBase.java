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

package com.android.server.telecom.tests;

import com.android.internal.util.StateMachine;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public abstract class StateMachineTestBase<T extends StateMachine> extends TelecomTestCase {
    abstract static class TestParameters {}

    protected final void waitForStateMachineActionCompletion(T stateMachine, int runnableCode) {
        final CountDownLatch lock = new CountDownLatch(1);
        Runnable actionComplete = new Runnable() {
            @Override
            public void run() {
                lock.countDown();
            }
        };
        stateMachine.sendMessage(runnableCode, actionComplete);
        while (lock.getCount() > 0) {
            try {
                lock.await();
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }

    protected final void parametrizedTestStateMachine(
            List<? extends TestParameters> paramList) throws Throwable {
        for (TestParameters params : paramList) {
            try {
                runParametrizedTestCase(params);
            } catch (Throwable e) {
                String newMessage = "Failed at parameters: \n" + params.toString() + '\n'
                        + e.getMessage();
                Throwable t = new Throwable(newMessage, e);
                t.setStackTrace(e.getStackTrace());
                throw t;
            }
        }
    }

    protected abstract void runParametrizedTestCase(TestParameters params) throws Throwable;
}
