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
package com.google.android.car.diagnosticverifier;

import android.car.diagnostic.CarDiagnosticEvent;
import android.util.JsonWriter;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * DiagVerifier implements verification logic for car diagnostic events.
 *
 * The main idea for the verification is similar to a "diff" command on two files, whereas here
 * is a diff on two event lists. The available diff operations are: "add", "delete", "modify"
 *
 * For example, think about doing a diff on two sequences:
 *
 *   Truth:     A B C D E
 *   Received:  A C D E F
 *
 * The goal is to find the minimal number of diff operations applied on Truth list in order to
 * become Received list. It is the same problem to find edit distance between two sequences and keep
 * track of the corresponding edit operations. This verifier applies dynamic programming algorithm
 * to find the minimal set of diff operations. And the result would be:
 *
 *   Truth:     A - C D E +
 *   Received:  A   C D E F
 *
 * It means in order to become Received list, "B" will be missing and an extra "F" will be added
 * at the end of list.
 */
public class DiagnosticVerifier {

    private static final String TAG = "DiagnosticVerifier";
    /**
     * Below are 4 diff operations when comparing two event lists
     */
    private static final int DELETE = 0;
    private static final int ADD = 1;
    private static final int MODIFY = 2;
    private static final int KEEP = 3;

    /**
     * A list of truth diagnostic events for comparison.
     */
    private final List<CarDiagnosticEvent> mTruthEventList = new ArrayList<>();
    /**
     * A list of received diagnostic events from car service.
     */
    private final List<CarDiagnosticEvent> mReceivedEventList = new ArrayList<>();

    /**
     * Definition of the verification result
     */
    static class VerificationResult {
        public final String testCase;
        public final boolean success;
        public final String errorMessage;

        private VerificationResult(String testCase, boolean success, String errorMessage) {
            this.testCase = testCase;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static VerificationResult fromMessage(String testCase, String message) {
            return new VerificationResult(testCase, message.length() == 0, message);
        }

        public void writeToJson(JsonWriter jsonWriter) throws IOException {
            jsonWriter.beginObject();

            jsonWriter.name("testCase");
            jsonWriter.value(this.testCase);

            jsonWriter.name("success");
            jsonWriter.value(this.success);

            jsonWriter.name("errorMessage");
            jsonWriter.value(this.errorMessage);

            jsonWriter.endObject();
        }
    }

    public DiagnosticVerifier(List<CarDiagnosticEvent> truthEvents) {
        if (truthEvents != null) {
            for (CarDiagnosticEvent event : truthEvents) {
                CarDiagnosticEvent canonicalEvent = canonicalize(event);
                mTruthEventList.add(canonicalEvent);
            }
        }
    }

    public void receiveEvent(CarDiagnosticEvent event) {
        CarDiagnosticEvent newEvent = canonicalize(event);
        mReceivedEventList.add(newEvent);
    }

    public List<VerificationResult> verify() {
        List<Integer> diff = calculateDiffOperations();
        StringBuilder missingEventMsgBuilder = new StringBuilder();
        StringBuilder extraEventMsgBuilder = new StringBuilder();
        StringBuilder mismatchEventMsgBuilder = new StringBuilder();
        for (int i = 0, j = 0, k = diff.size() - 1; k >= 0; k--) {
            if (diff.get(k) == DELETE) {
                missingEventMsgBuilder.append(String.format(
                        "Missing event at position %d: %s\n", i, mTruthEventList.get(i)));
                i++;
            } else if (diff.get(k) == ADD) {
                extraEventMsgBuilder.append(String.format(
                        "Extra event at position %d: %s\n", i, mReceivedEventList.get(j)));
                j++;
            } else if (diff.get(k) == MODIFY) {
                mismatchEventMsgBuilder.append(String.format(
                        "Mismatched event pair at position %d:\n" +
                        "True event -- %s\nWrong event -- %s\n",
                        i, mTruthEventList.get(i), mReceivedEventList.get(j)));
                i++;
                j++;
            } else {
                i++;
                j++;
            }
        }
        List<VerificationResult> results = new ArrayList<>();
        results.add(VerificationResult.fromMessage(
                "test_mismatched_event", mismatchEventMsgBuilder.toString()));
        results.add(VerificationResult.fromMessage(
                "test_missing_event", missingEventMsgBuilder.toString()));
        results.add(VerificationResult.fromMessage(
                "test_extra_event", extraEventMsgBuilder.toString()));
        return results;
    }

    /**
     * The function applies a dynamic programming algorithm to find the minimal set of diff
     * operations that applied on truth event list in order to become received event list
     */
    private List<Integer> calculateDiffOperations() {
        final int n = mTruthEventList.size();
        final int m = mReceivedEventList.size();

        int[][] diffTable = new int[n + 1][m + 1];
        int[][] costTable = new int[n + 1][m + 1];

        for (int i = 1; i <= n; i++) {
            costTable[i][0] = i;
            diffTable[i][0] = DELETE;
        }

        for (int i = 1; i <= m; i++) {
            costTable[0][i] = i;
            diffTable[0][i] = ADD;
        }

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int deleteCost = costTable[i - 1][j] + 1;
                int addCost = costTable[i][j - 1] + 1;
                int modifyCost = costTable[i - 1][j - 1];

                CarDiagnosticEvent trueEvent = mTruthEventList.get(i - 1);
                CarDiagnosticEvent receivedEvent = mReceivedEventList.get(j - 1);

                //TODO: Use a more meaningful comparison. Instead of strict object level equality,
                //can check logical equality and allow an acceptable difference.
                boolean isEqual = trueEvent.equals(receivedEvent);
                modifyCost += isEqual ? 0 : 1;

                int minCost = modifyCost;
                int move = isEqual ? KEEP : MODIFY;
                if (minCost > addCost) {
                    minCost = addCost;
                    move = ADD;
                }
                if (minCost > deleteCost) {
                    minCost = deleteCost;
                    move = DELETE;
                }

                costTable[i][j] = minCost;
                diffTable[i][j] = move;
            }
        }
        List<Integer> diff = new ArrayList<>();

        for (int i = n, j = m; i > 0 || j > 0; ) {
            diff.add(diffTable[i][j]);
            if (diffTable[i][j] == DELETE) {
                i--;
            } else if (diffTable[i][j] == ADD) {
                j--;
            } else {
                i--;
                j--;
            }
        }
        return diff;
    }

    /**
     * The function will canonicalize a given event by using JSON converter which will reset event
     * timestamp to 0 and set DTC field with empty string to null. Doing JSON conversion is because
     * CarDiagnosticEvent does not provide direct accessor for intValues and floatValues.
     */
    private CarDiagnosticEvent canonicalize(CarDiagnosticEvent event) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(out));
        CarDiagnosticEvent newEvent = event;
        try {
            event.writeToJson(writer);
            writer.flush();
            writer.close();
            byte[] rawJson = out.toByteArray();
            ByteArrayInputStream in = new ByteArrayInputStream(rawJson);
            newEvent = DiagnosticJsonConverter.readEventAndCanonicalize(in);
            in.close();
            out.close();
        } catch (IOException e) {
            Log.w(TAG, "Failed to clear timestamp ");
        }
        return newEvent;
    }
}
