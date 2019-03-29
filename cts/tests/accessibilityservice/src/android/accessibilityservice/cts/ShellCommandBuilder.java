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

package android.accessibilityservice.cts;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

public class ShellCommandBuilder {
    private final LinkedList<String> mCommands = new LinkedList<>();

    private final Instrumentation mInstrumentation;

    public static ShellCommandBuilder create(Instrumentation instrumentation) {
        return new ShellCommandBuilder(instrumentation);
    }

    private ShellCommandBuilder(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }

    public void run() {
        final UiAutomation automation = mInstrumentation.getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        for (String command : mCommands) {
            execShellCommand(automation, command);
        }
    }

    public ShellCommandBuilder deleteSecureSetting(String name) {
        mCommands.add("settings delete secure " + name);
        return this;
    }

    public ShellCommandBuilder putSecureSetting(String name, String value) {
        mCommands.add("settings put secure " + name + " " + value);
        return this;
    }

    public ShellCommandBuilder grantPermission(String packageName, String permission) {
        mCommands.add("pm grant " + packageName + " " + permission);
        return this;
    }

    public ShellCommandBuilder addCommand(String command) {
        mCommands.add(command);
        return this;
    }

    private static void execShellCommand(UiAutomation automation, String command) {
        try (ParcelFileDescriptor fd = automation.executeShellCommand(command)) {
            try (InputStream inputStream = new FileInputStream(fd.getFileDescriptor())) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    while (reader.readLine() != null) {
                        // Keep reading.
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to exec shell command", e);
        }
    }
}
