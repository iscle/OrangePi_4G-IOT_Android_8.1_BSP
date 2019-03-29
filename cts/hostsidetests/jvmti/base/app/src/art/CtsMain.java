/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package art;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * An entrypoint for the CTS JVMTI agent to signal to the Java side that it has attached.
 */
public class CtsMain {

    private static CountDownLatch sStartWaiter = new CountDownLatch(1);

    /**
     * This method will be called by the agent to inform the test that JNI methods are bound. The
     * counterpart is waitFor.
     */
    @SuppressWarnings("unused")
    private static void startup() {
        sStartWaiter.countDown();
    }

    public static void waitFor() {
        try {
            if (!sStartWaiter.await(15, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timed out waiting for the agent");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Got interrupted waiting for agent.");
        }
    }
}
