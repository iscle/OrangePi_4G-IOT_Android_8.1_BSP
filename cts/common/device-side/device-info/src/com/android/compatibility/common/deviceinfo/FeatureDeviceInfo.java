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

import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.android.compatibility.common.util.DeviceInfoStore;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Feature device info collector.
 */
public final class FeatureDeviceInfo extends DeviceInfo {

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        PackageManager packageManager =
                getInstrumentation().getContext().getPackageManager();
        store.startArray("feature");

        List<String> sdkFeatures = getPackageManagerFeatures();

        FeatureInfo[] featureInfos = packageManager.getSystemAvailableFeatures();
        if (featureInfos != null) {
            for (FeatureInfo featureInfo : featureInfos) {
                if (featureInfo.name != null) {
                    // Check if this feature is a "sdk" feature.
                    String type = "other";
                    if (sdkFeatures.contains(featureInfo.name)) {
                        type = "sdk";
                        sdkFeatures.remove(featureInfo.name);
                    }
                    // Add the feature version if avaiable.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        int version = featureInfo.version;
                        addFeature(store, featureInfo.name, type, true, version);
                    } else {
                        addFeature(store, featureInfo.name, type, true);
                    }
                }
            }
        }

        // Store the remaining "sdk" features not avaiable on this device.
        for (String featureName : sdkFeatures) {
            boolean hasFeature = packageManager.hasSystemFeature(featureName);
            addFeature(store, featureName, "sdk", hasFeature);
        }

        store.endArray();
    }

    /**
     * Use reflection to get the features defined by the SDK. If there are
     * features that do not fit the convention of starting with "FEATURE_"
     * then they will still be shown under the "Other Features" section.
     *
     * @return list of feature names from sdk
     */
    private List<String> getPackageManagerFeatures() {
        try {
            List<String> features = new ArrayList<String>();
            Field[] fields = PackageManager.class.getFields();
            for (Field field : fields) {
                if (field.getName().startsWith("FEATURE_")) {
                    String feature = (String) field.get(null);
                    features.add(feature);
                }
            }
            return features;
        } catch (IllegalAccessException illegalAccess) {
            throw new RuntimeException(illegalAccess);
        }
    }

    private void addFeature(
            DeviceInfoStore store,
            String name,
            String type,
            boolean available) throws Exception {
        store.startGroup();
        store.addResult("name", name);
        store.addResult("type", type);
        store.addResult("available", available);
        store.endGroup();
    }

    private void addFeature(
            DeviceInfoStore store,
            String name,
            String type,
            boolean available,
            int version) throws Exception {
        store.startGroup();
        store.addResult("name", name);
        store.addResult("type", type);
        store.addResult("available", available);
        store.addResult("version", version);
        store.endGroup();
    }
}
