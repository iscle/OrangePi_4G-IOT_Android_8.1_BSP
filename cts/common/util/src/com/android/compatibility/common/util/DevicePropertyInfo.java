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

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for collecting device information. This is used to enforce
 * consistent property collection host-side and device-side for CTS reports.
 *
 * Note that properties across sources can differ, e.g. {@code android.os.Build}
 * properties sometimes deviate from the read-only properties that they're based
 * on.
 */
public final class DevicePropertyInfo {

    private final String mAbi;
    private final String mAbi2;
    private final String mAbis;
    private final String mAbis32;
    private final String mAbis64;
    private final String mBoard;
    private final String mBrand;
    private final String mDevice;
    private final String mFingerprint;
    private final String mId;
    private final String mManufacturer;
    private final String mModel;
    private final String mProduct;
    private final String mReferenceFingerprint;
    private final String mSerial;
    private final String mTags;
    private final String mType;
    private final String mVersionBaseOs;
    private final String mVersionRelease;
    private final String mVersionSdk;
    private final String mVersionSecurityPatch;
    private final String mVersionIncremental;

    public DevicePropertyInfo(String abi, String abi2, String abis, String abis32, String abis64,
            String board, String brand, String device, String fingerprint, String id,
            String manufacturer, String model, String product, String referenceFigerprint,
            String serial, String tags, String type, String versionBaseOs, String versionRelease,
            String versionSdk, String versionSecurityPatch, String versionIncremental) {
        mAbi = abi;
        mAbi2 = abi2;
        mAbis = abis;
        mAbis32 = abis32;
        mAbis64 = abis64;
        mBoard = board;
        mBrand = brand;
        mDevice = device;
        mFingerprint = fingerprint;
        mId = id;
        mManufacturer = manufacturer;
        mModel = model;
        mProduct = product;
        mReferenceFingerprint = referenceFigerprint;
        mSerial = serial;
        mTags = tags;
        mType = type;
        mVersionBaseOs = versionBaseOs;
        mVersionRelease = versionRelease;
        mVersionSdk = versionSdk;
        mVersionSecurityPatch = versionSecurityPatch;
        mVersionIncremental = versionIncremental;
    }

    /**
     * Return a {@code Map} with property keys prepended with a given prefix
     * string. This is intended to be used to generate entries for
     * {@code} Build tag attributes in CTS test results.
     */
    public Map<String, String> getPropertytMapWithPrefix(String prefix) {
        Map<String, String> propertyMap = new HashMap<>();

        propertyMap.put(prefix + "abi", mAbi);
        propertyMap.put(prefix + "abi2", mAbi2);
        propertyMap.put(prefix + "abis", mAbis);
        propertyMap.put(prefix + "abis_32", mAbis32);
        propertyMap.put(prefix + "abis_64", mAbis64);
        propertyMap.put(prefix + "board", mBoard);
        propertyMap.put(prefix + "brand", mBrand);
        propertyMap.put(prefix + "device", mDevice);
        propertyMap.put(prefix + "fingerprint", mFingerprint);
        propertyMap.put(prefix + "id", mId);
        propertyMap.put(prefix + "manufacturer", mManufacturer);
        propertyMap.put(prefix + "model", mModel);
        propertyMap.put(prefix + "product", mProduct);
        propertyMap.put(prefix + "reference_fingerprint", mReferenceFingerprint);
        propertyMap.put(prefix + "serial", mSerial);
        propertyMap.put(prefix + "tags", mTags);
        propertyMap.put(prefix + "type", mType);
        propertyMap.put(prefix + "version_base_os", mVersionBaseOs);
        propertyMap.put(prefix + "version_release", mVersionRelease);
        propertyMap.put(prefix + "version_sdk", mVersionSdk);
        propertyMap.put(prefix + "version_security_patch", mVersionSecurityPatch);
        propertyMap.put(prefix + "version_incremental", mVersionIncremental);

        return propertyMap;
    }

}
