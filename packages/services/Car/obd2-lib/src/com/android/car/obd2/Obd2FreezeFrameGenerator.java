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
import com.android.car.obd2.Obd2Command.FreezeFrameCommand;
import com.android.car.obd2.Obd2Command.OutputSemanticHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Obd2FreezeFrameGenerator {
    public static final String FRAME_TYPE_FREEZE = "freeze";
    public static final String TAG = Obd2FreezeFrameGenerator.class.getSimpleName();

    private final Obd2Connection mConnection;
    private final List<OutputSemanticHandler<Integer>> mIntegerCommands = new ArrayList<>();
    private final List<OutputSemanticHandler<Float>> mFloatCommands = new ArrayList<>();

    private List<String> mPreviousDtcs = new ArrayList<>();

    public Obd2FreezeFrameGenerator(Obd2Connection connection)
            throws IOException, InterruptedException {
        mConnection = connection;
        Set<Integer> connectionPids = connection.getSupportedPIDs();
        Set<Integer> apiIntegerPids = Obd2Command.getSupportedIntegerCommands();
        Set<Integer> apiFloatPids = Obd2Command.getSupportedFloatCommands();
        apiIntegerPids
                .stream()
                .filter(connectionPids::contains)
                .forEach((Integer pid) -> mIntegerCommands.add(Obd2Command.getIntegerCommand(pid)));
        apiFloatPids
                .stream()
                .filter(connectionPids::contains)
                .forEach((Integer pid) -> mFloatCommands.add(Obd2Command.getFloatCommand(pid)));
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

    public JsonWriter generate(JsonWriter jsonWriter) throws IOException, InterruptedException {
        return generate(jsonWriter, SystemClock.elapsedRealtimeNanos());
    }

    // OBD2 does not have a notion of timestamping the fault codes
    // As such, we need to perform additional magic in order to figure out
    // whether a fault code we retrieved is the same as a fault code we already
    // saw in a past iteration. The logic goes as follows:
    // for every position i in currentDtcs, if mPreviousDtcs[i] is the same
    // fault code, then assume they are identical. If they are not the same fault code,
    // then everything in currentDtcs[i...size()) is assumed to be a new fault code as
    // something in the list must have moved around; if currentDtcs is shorter than
    // mPreviousDtcs then obviously exit at the end of currentDtcs; if currentDtcs
    // is longer, however, anything in currentDtcs past the end of mPreviousDtcs is a new
    // fault code and will be included
    private final class FreezeFrameIdentity {
        public final String dtc;
        public final int id;

        FreezeFrameIdentity(String dtc, int id) {
            this.dtc = dtc;
            this.id = id;
        }
    }

    private List<FreezeFrameIdentity> discoverNewDtcs(List<String> currentDtcs) {
        List<FreezeFrameIdentity> newDtcs = new ArrayList<>();
        int currentIndex = 0;
        boolean inCopyAllMode = false;

        for (; currentIndex < currentDtcs.size(); ++currentIndex) {
            if (currentIndex == mPreviousDtcs.size()) {
                // we have more current DTCs than previous DTCs, copy everything
                inCopyAllMode = true;
                break;
            }
            if (!currentDtcs.get(currentIndex).equals(mPreviousDtcs.get(currentIndex))) {
                // we found a different DTC, copy everything
                inCopyAllMode = true;
                break;
            }
            // same DTC, not at end of either list yet, keep looping
        }

        if (inCopyAllMode) {
            for (; currentIndex < currentDtcs.size(); ++currentIndex) {
                newDtcs.add(new FreezeFrameIdentity(currentDtcs.get(currentIndex), currentIndex));
            }
        }

        return newDtcs;
    }

    public JsonWriter generate(JsonWriter jsonWriter, long timestamp)
            throws IOException, InterruptedException {
        List<String> currentDtcs = mConnection.getDiagnosticTroubleCodes();
        List<FreezeFrameIdentity> newDtcs = discoverNewDtcs(currentDtcs);
        mPreviousDtcs = currentDtcs;
        for (FreezeFrameIdentity freezeFrame : newDtcs) {
            jsonWriter.beginObject();
            jsonWriter.name("type").value(FRAME_TYPE_FREEZE);
            jsonWriter.name("timestamp").value(timestamp);
            jsonWriter.name("stringValue").value(freezeFrame.dtc);
            jsonWriter.name("intValues").beginArray();
            for (OutputSemanticHandler<Integer> handler : mIntegerCommands) {
                FreezeFrameCommand<Integer> command =
                        Obd2Command.getFreezeFrameCommand(handler, freezeFrame.id);
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
            for (OutputSemanticHandler<Float> handler : mFloatCommands) {
                FreezeFrameCommand<Float> command =
                        Obd2Command.getFreezeFrameCommand(handler, freezeFrame.id);
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
            jsonWriter.endObject();
        }
        return jsonWriter;
    }
}
