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

package com.android.car.vehiclehal;

import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.util.JsonReader;
import android.util.Log;
import android.util.SparseArray;
import com.android.car.vehiclehal.Utils.SparseArrayIterator;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class DiagnosticJson {
    public final String type;
    public final long timestamp;
    public final SparseArray<Integer> intValues;
    public final SparseArray<Float> floatValues;
    public final String dtc;

    DiagnosticJson(
            String type,
            long timestamp,
            SparseArray<Integer> intValues,
            SparseArray<Float> floatValues,
            String dtc) {
        this.type = type;
        this.timestamp = timestamp;
        this.intValues = Objects.requireNonNull(intValues);
        this.floatValues = Objects.requireNonNull(floatValues);
        this.dtc = dtc;
    }

    VehiclePropValue build(DiagnosticEventBuilder builder) {
        new SparseArrayIterator<>(intValues)
                .forEach(
                        (SparseArrayIterator.SparseArrayEntry<Integer> entry) ->
                                builder.addIntSensor(entry.key, entry.value));
        new SparseArrayIterator<>(floatValues)
                .forEach(
                        (SparseArrayIterator.SparseArrayEntry<Float> entry) ->
                                builder.addFloatSensor(entry.key, entry.value));
        builder.setDTC(dtc);
        VehiclePropValue vehiclePropValue = builder.build(timestamp);
        builder.clear();
        return vehiclePropValue;
    }

    static class Builder {
        public static final String TAG = Builder.class.getSimpleName();

        static class WriteOnce<T> {
            private Optional<T> mValue = Optional.empty();

            void write(T value) {
                if (mValue.isPresent()) throw new IllegalStateException("WriteOnce already stored");
                mValue = Optional.of(value);
            }

            T get() {
                if (!mValue.isPresent()) throw new IllegalStateException("WriteOnce never stored");
                return mValue.get();
            }

            T get(T defaultValue) {
                return mValue.isPresent() ? mValue.get() : defaultValue;
            }
        }

        final WriteOnce<String> mType = new WriteOnce<>();
        final WriteOnce<Long> mTimestamp = new WriteOnce<>();
        final SparseArray<Integer> mIntValues = new SparseArray<>();
        final SparseArray<Float> mFloatValues = new SparseArray<>();
        final WriteOnce<String> mDtc = new WriteOnce<>();

        private void readIntValues(JsonReader jsonReader) throws IOException {
            while (jsonReader.hasNext()) {
                int id = 0;
                int value = 0;
                jsonReader.beginObject();
                while (jsonReader.hasNext()) {
                    String name = jsonReader.nextName();
                    if (name.equals("id")) id = jsonReader.nextInt();
                    else if (name.equals("value")) value = jsonReader.nextInt();
                }
                jsonReader.endObject();
                mIntValues.put(id, value);
            }
        }

        private void readFloatValues(JsonReader jsonReader) throws IOException {
            while (jsonReader.hasNext()) {
                int id = 0;
                float value = 0.0f;
                jsonReader.beginObject();
                while (jsonReader.hasNext()) {
                    String name = jsonReader.nextName();
                    if (name.equals("id")) id = jsonReader.nextInt();
                    else if (name.equals("value")) value = (float) jsonReader.nextDouble();
                }
                jsonReader.endObject();
                mFloatValues.put(id, value);
            }
        }

        Builder(JsonReader jsonReader) throws IOException {
            jsonReader.beginObject();
            while (jsonReader.hasNext()) {
                String name = jsonReader.nextName();
                switch (name) {
                    case "type":
                        mType.write(jsonReader.nextString());
                        break;
                    case "timestamp":
                        mTimestamp.write(jsonReader.nextLong());
                        break;
                    case "intValues":
                        jsonReader.beginArray();
                        readIntValues(jsonReader);
                        jsonReader.endArray();
                        break;
                    case "floatValues":
                        jsonReader.beginArray();
                        readFloatValues(jsonReader);
                        jsonReader.endArray();
                        break;
                    case "stringValue":
                        mDtc.write(jsonReader.nextString());
                        break;
                    default:
                        Log.w(TAG, "Unknown name in diagnostic JSON: " + name);
                }
            }
            jsonReader.endObject();
        }

        DiagnosticJson build() {
            return new DiagnosticJson(
                    mType.get(), mTimestamp.get(), mIntValues, mFloatValues, mDtc.get(null));
        }
    }

    public static DiagnosticJson build(JsonReader jsonReader) throws IOException {
        return new Builder(jsonReader).build();
    }
}
