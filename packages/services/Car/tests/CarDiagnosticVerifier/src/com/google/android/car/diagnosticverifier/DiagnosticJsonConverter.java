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
import android.util.JsonReader;

import com.android.car.vehiclehal.DiagnosticJson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * This class provides method to convert JSON into car diagnostic event object.
 */
public class DiagnosticJsonConverter {

    public static List<CarDiagnosticEvent> readFromJson(InputStream in) throws IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));

        try {
            return readEventsArray(reader);
        } finally {
            reader.close();
        }
    }

    private static List<CarDiagnosticEvent> readEventsArray(JsonReader reader) throws IOException {
        List<CarDiagnosticEvent> events = new ArrayList<>();

        reader.beginArray();
        while (reader.hasNext()) {
            events.add(readEventAndCanonicalize(reader));
        }
        reader.endArray();
        return events;
    }

    public static CarDiagnosticEvent readEventAndCanonicalize(InputStream in) throws IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
        return readEventAndCanonicalize(reader);
    }

    /**
     * This method convert JSON to a car diagnostic event object.
     * Note: it will always set timestamp to 0 and set dtc to null if it is empty string.
     */
    private static CarDiagnosticEvent readEventAndCanonicalize(JsonReader reader)
            throws IOException {
        DiagnosticJson diagnosticJson = DiagnosticJson.build(reader);
        //Build event
        CarDiagnosticEvent.Builder builder = "freeze".equals(diagnosticJson.type) ?
                CarDiagnosticEvent.Builder.newFreezeFrameBuilder() :
                CarDiagnosticEvent.Builder.newLiveFrameBuilder();
        //Always skip timestamp because it is not useful for test
        builder.atTimestamp(0);
        for (int i = 0; i < diagnosticJson.intValues.size(); i++) {
            builder.withIntValue(diagnosticJson.intValues.keyAt(i),
                    diagnosticJson.intValues.valueAt(i));
        }
        for (int i = 0; i < diagnosticJson.floatValues.size(); i++) {
            builder.withFloatValue(diagnosticJson.floatValues.keyAt(i),
                    diagnosticJson.floatValues.valueAt(i));
        }
        //Always set dtc to null if it is empty string
        builder.withDtc("".equals(diagnosticJson.dtc) ? null : diagnosticJson.dtc);

        return builder.build();
    }
}
