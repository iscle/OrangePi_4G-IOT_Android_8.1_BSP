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

package com.android.car.obd2;

import android.os.SystemClock;
import android.util.JsonWriter;
import android.util.Log;
import com.android.car.obd2.Obd2Command.LiveFrameCommand;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Obd2LiveFrameGenerator {
    public static final String FRAME_TYPE_LIVE = "live";
    public static final String TAG = Obd2LiveFrameGenerator.class.getSimpleName();

    private final Obd2Connection mConnection;
    private final List<LiveFrameCommand<Integer>> mIntegerCommands = new ArrayList<>();
    private final List<LiveFrameCommand<Float>> mFloatCommands = new ArrayList<>();

    public Obd2LiveFrameGenerator(Obd2Connection connection)
            throws IOException, InterruptedException {
        mConnection = connection;
        Set<Integer> connectionPids = connection.getSupportedPIDs();
        Set<Integer> apiIntegerPids = Obd2Command.getSupportedIntegerCommands();
        Set<Integer> apiFloatPids = Obd2Command.getSupportedFloatCommands();
        apiIntegerPids
                .stream()
                .filter(connectionPids::contains)
                .forEach(
                        (Integer pid) ->
                                mIntegerCommands.add(
                                        Obd2Command.getLiveFrameCommand(
                                                Obd2Command.getIntegerCommand(pid))));
        apiFloatPids
                .stream()
                .filter(connectionPids::contains)
                .forEach(
                        (Integer pid) ->
                                mFloatCommands.add(
                                        Obd2Command.getLiveFrameCommand(
                                                Obd2Command.getFloatCommand(pid))));
        Log.i(
                TAG,
                String.format(
                        "connectionPids = %s\napiIntegerPids=%s\napiFloatPids = %s\n"
                                + "mIntegerCommands = %s\nmFloatCommands = %s\n",
                        connectionPids,
                        apiIntegerPids,
                        apiFloatPids,
                        mIntegerCommands,
                        mFloatCommands));
    }

    public JsonWriter generate(JsonWriter jsonWriter) throws IOException {
        return generate(jsonWriter, SystemClock.elapsedRealtimeNanos());
    }

    public JsonWriter generate(JsonWriter jsonWriter, long timestamp) throws IOException {
        jsonWriter.beginObject();
        jsonWriter.name("type").value(FRAME_TYPE_LIVE);
        jsonWriter.name("timestamp").value(timestamp);
        jsonWriter.name("intValues").beginArray();
        for (LiveFrameCommand<Integer> command : mIntegerCommands) {
            try {
                Optional<Integer> result = command.run(mConnection);
                if (result.isPresent()) {
                    jsonWriter.beginObject();
                    jsonWriter.name("id").value(command.getPid());
                    jsonWriter.name("value").value(result.get());
                    jsonWriter.endObject();
                }
            } catch (IOException | InterruptedException e) {
                Log.w(
                        TAG,
                        String.format(
                                "unable to retrieve OBD2 pid %d due to exception: %s",
                                command.getPid(), e));
                // skip this entry
            }
        }
        jsonWriter.endArray();

        jsonWriter.name("floatValues").beginArray();
        for (LiveFrameCommand<Float> command : mFloatCommands) {
            try {
                Optional<Float> result = command.run(mConnection);
                if (result.isPresent()) {
                    jsonWriter.beginObject();
                    jsonWriter.name("id").value(command.getPid());
                    jsonWriter.name("value").value(result.get());
                    jsonWriter.endObject();
                }
            } catch (IOException | InterruptedException e) {
                Log.w(
                        TAG,
                        String.format(
                                "unable to retrieve OBD2 pid %d due to exception: %s",
                                command.getPid(), e));
                // skip this entry
            }
        }
        jsonWriter.endArray();

        return jsonWriter.endObject();
    }
}
