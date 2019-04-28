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

package com.google.android.car.obd2app;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

interface StatusNotification {
    void notify(String status);

    default void notifyNoDongle() {
        notify("No OBD2 dongle paired. Go to Settings.");
    }

    default void notifyPaired(String deviceAddress) {
        notify("Paired to " + deviceAddress + ". Ready to capture data.");
    }

    default void notifyConnectionFailed() {
        notify("Unable to connect.");
    }

    default void notifyConnected(String deviceAddress) {
        notify("Connected to " + deviceAddress + ". Starting data capture.");
    }

    default void notifyDataCapture() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MMMM dd yyyy hh:mm:ssa");
        notify("Successfully captured data at " + now.format(dateTimeFormatter));
    }

    default void notifyException(Exception e) {
        StringWriter stringWriter = new StringWriter(1024);
        e.printStackTrace(new PrintWriter(stringWriter));
        notify("Exception occurred.\n" + stringWriter.toString());
    }

    default void notifyDisconnected() {
        notify("Lost connection to remote end. Will try to reconnect.");
    }
}
