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
package com.android.compatibility.common.deviceinfo;

import android.os.Bundle;

import com.android.compatibility.common.util.DeviceInfoStore;

import java.io.IOException;

import java.util.List;
import java.util.Set;

/**
 * Graphics device info collector.
 */
public final class GraphicsDeviceInfo extends DeviceInfo {

    private static final String LOG_TAG = "GraphicsDeviceInfo";

    // Java generics won't handle basic types, can't simplify
    private static void storeValue(DeviceInfoStore store, String name, float[] valueArray,
                                   boolean dynamicArray) throws IOException {
        if (valueArray.length == 1 && !dynamicArray) {
            store.addResult(name, valueArray[0]);
        } else {
            store.addArrayResult(name, valueArray);
        }
    }

    private static void storeValue(DeviceInfoStore store, String name, int[] valueArray,
                                   boolean dynamicArray) throws IOException {
        if (valueArray.length == 1 && !dynamicArray) {
            store.addResult(name, valueArray[0]);
        } else {
            store.addArrayResult(name, valueArray);
        }
    }

    private static void storeValue(DeviceInfoStore store, String name, long[] valueArray,
                                   boolean dynamicArray) throws IOException {
        if (valueArray.length == 1 && !dynamicArray) {
            store.addResult(name, valueArray[0]);
        } else {
            store.addArrayResult(name, valueArray);
        }
    }

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        GlesStubActivity stubActivity = GraphicsDeviceInfo.this.launchActivity(
                "com.android.compatibility.common.deviceinfo",
                GlesStubActivity.class,
                new Bundle());
        stubActivity.waitForActivityToFinish();

        store.addResult("gl_version", stubActivity.getGlVersion());
        store.addResult("vendor", stubActivity.getVendor());
        store.addResult("renderer", stubActivity.getRenderer());

        store.addListResult("gl_texture", stubActivity.getCompressedTextureFormats());
        store.addListResult("gl_extension", stubActivity.getOpenGlExtensions());

        Set<String> variables = stubActivity.getImplementationVariableNames();
        for (String name : variables) {
            Object value = stubActivity.getImplementationVariable(name);
            String lowerCaseName = name.toLowerCase();
            if (lowerCaseName.equals("gl_version")) {
                lowerCaseName = "gl_version_real";
            }

            if (value != null) {
                boolean dynamicArray = stubActivity.isDynamicArrayVariable(name);
                if (value instanceof String) {
                    store.addResult(lowerCaseName, (String)value);
                } else if (value instanceof float[]) {
                    storeValue(store, lowerCaseName, (float[])value, dynamicArray);
                } else if (value instanceof long[]) {
                    storeValue(store, lowerCaseName, (long[])value, dynamicArray);
                } else {
                    storeValue(store, lowerCaseName, (int[])value, dynamicArray);
                }
            }
        }

        store.addListResult("egl_extension", stubActivity.getEglExtensions());
    }
}
