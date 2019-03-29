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
 * limitations under the License.
 */
package com.android.compatibility.common.util;

import com.android.json.stream.JsonWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HostInfoStore extends InfoStore {

    protected File mJsonFile;
    protected JsonWriter mJsonWriter = null;

    public HostInfoStore() {
        mJsonFile = null;
    }

    public HostInfoStore(File file) throws Exception {
        mJsonFile = file;
    }

    /**
     * Opens the file for storage and creates the writer.
     */
    @Override
    public void open() throws IOException {
        FileOutputStream out = new FileOutputStream(mJsonFile);
        mJsonWriter = new JsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        // TODO(agathaman): remove to make json output less pretty
        mJsonWriter.setIndent("  ");
        mJsonWriter.beginObject();
    }

    /**
     * Closes the writer.
     */
    @Override
    public void close() throws IOException {
        mJsonWriter.endObject();
        mJsonWriter.close();
    }

    /**
     * Start a new group of result.
     */
    @Override
    public void startGroup() throws IOException {
        mJsonWriter.beginObject();
    }

    /**
     * Start a new group of result with specified name.
     */
    @Override
    public void startGroup(String name) throws IOException {
        mJsonWriter.name(name);
        mJsonWriter.beginObject();
    }

    /**
     * Complete adding result to the last started group.
     */
    @Override
    public void endGroup() throws IOException {
        mJsonWriter.endObject();
    }

    /**
     * Start a new array of result.
     */
    @Override
    public void startArray() throws IOException {
        mJsonWriter.beginArray();
    }

    /**
     * Start a new array of result with specified name.
     */
    @Override
    public void startArray(String name) throws IOException {
        checkName(name);
        mJsonWriter.name(name);
        mJsonWriter.beginArray();
    }

    /**
     * Complete adding result to the last started array.
     */
    @Override
    public void endArray() throws IOException {
        mJsonWriter.endArray();
    }

    /**
     * Adds a int value to the InfoStore
     */
    @Override
    public void addResult(String name, int value) throws IOException {
        checkName(name);
        mJsonWriter.name(name);
        mJsonWriter.value(value);
    }

    /**
     * Adds a long value to the InfoStore
     */
    @Override
    public void addResult(String name, long value) throws IOException {
        checkName(name);
        mJsonWriter.name(name);
        mJsonWriter.value(value);
    }

    /**
     * Adds a float value to the InfoStore
     */
    @Override
    public void addResult(String name, float value) throws IOException {
        checkName(name);
        mJsonWriter.name(name);
        mJsonWriter.value(value);
    }

    /**
     * Adds a double value to the InfoStore
     */
    @Override
    public void addResult(String name, double value) throws IOException {
        checkName(name);
        mJsonWriter.name(name);
        mJsonWriter.value(value);
    }

    /**
     * Adds a boolean value to the InfoStore
     */
    @Override
    public void addResult(String name, boolean value) throws IOException {
        checkName(name);
        mJsonWriter.name(name);
        mJsonWriter.value(value);
    }

    /**
     * Adds a String value to the InfoStore
     */
    @Override
    public void addResult(String name, String value) throws IOException {
        checkName(name);
        mJsonWriter.name(name);
        mJsonWriter.value(checkString(value));
    }

    /**
     * Adds a int array to the InfoStore
     */
    @Override
    public void addArrayResult(String name, int[] array) throws IOException {
        checkName(name);
        mJsonWriter.name(name);
        mJsonWriter.beginArray();
        for (int value : checkArray(array)) {
            mJsonWriter.value(value);
        }
        mJsonWriter.endArray();
    }

    /**
     * Adds a long array to the InfoStore
     */
    @Override
    public void addArrayResult(String name, long[] array) throws IOException {
        checkName(name);
        mJsonWriter.name(name);
        mJsonWriter.beginArray();
        for (long value : checkArray(array)) {
            mJsonWriter.value(value);
        }
        mJsonWriter.endArray();
    }

    /**
     * Adds a float array to the InfoStore
     */
    @Override
    public void addArrayResult(String name, float[] array) throws IOException {
        checkName(name);
        mJsonWriter.name(name);
        mJsonWriter.beginArray();
        for (float value : checkArray(array)) {
            mJsonWriter.value(value);
        }
        mJsonWriter.endArray();
    }

    /**
     * Adds a double array to the InfoStore
     */
    @Override
    public void addArrayResult(String name, double[] array) throws IOException {
        checkName(name);
        mJsonWriter.name(name);
        mJsonWriter.beginArray();
        for (double value : checkArray(array)) {
            mJsonWriter.value(value);
        }
        mJsonWriter.endArray();
    }

    /**
     * Adds a boolean array to the InfoStore
     */
    @Override
    public void addArrayResult(String name, boolean[] array) throws IOException {
        checkName(name);
        mJsonWriter.name(name);
        mJsonWriter.beginArray();
        for (boolean value : checkArray(array)) {
            mJsonWriter.value(value);
        }
        mJsonWriter.endArray();
    }

    /**
     * Adds a List of String to the InfoStore
     */
    @Override
    public void addListResult(String name, List<String> list) throws IOException {
        checkName(name);
        mJsonWriter.name(name);
        mJsonWriter.beginArray();
        for (String value : checkStringList(list)) {
            mJsonWriter.value(checkString(value));
        }
        mJsonWriter.endArray();
    }
}
