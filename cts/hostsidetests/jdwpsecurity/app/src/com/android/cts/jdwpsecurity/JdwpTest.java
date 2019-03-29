/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.jdwpsecurity;

public class JdwpTest {
    private static final long LOOP_TIMEOUT_MS = 60 * 1000;

    public static void main(String[] args) throws Exception {
        // Print pid so the test knows who we are.
        int pid = android.os.Process.myPid();
        System.out.println(pid);

        // Loop to keep alive so the host test has the time to check whether we have a JDWP
        // connection.
        // Note: we use a timeout to avoid indefinite loop in case something wrong happens
        // with the test harness.
        long start = System.currentTimeMillis();
        while(getElapsedTime(start) < LOOP_TIMEOUT_MS) {
            Thread.sleep(100);
        }
    }

    private static long getElapsedTime(long start) {
        return System.currentTimeMillis() - start;
    }
}
