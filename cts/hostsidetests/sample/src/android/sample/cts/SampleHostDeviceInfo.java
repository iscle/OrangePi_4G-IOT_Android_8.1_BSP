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
package android.sample.cts;

import com.android.compatibility.common.util.DeviceInfo;
import com.android.compatibility.common.util.HostInfoStore;
import com.android.tradefed.device.ITestDevice;

import java.util.Arrays;

public class SampleHostDeviceInfo extends DeviceInfo {

    private ITestDevice mDevice;

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {

        mDevice = getDevice();

        store.startGroup("product");
        store.addResult("model", getProperty("ro.product.model"));
        store.addResult("brand", getProperty("ro.product.brand"));
        store.addResult("name", getProperty("ro.product.name"));
        store.addResult("device", getProperty("ro.product.device"));
        store.addResult("board", getProperty("ro.product.board"));

        String abi = getProperty("ro.product.cpu.abilist");
        store.addListResult("abi", Arrays.asList(abi.split(",")));
        store.endGroup(); // product

        store.startGroup("version");
        store.addResult("sdk", getProperty("ro.build.version.sdk"));
        store.addResult("codename", getProperty("ro.build.version.codename"));
        store.addResult("security_patch", getProperty("ro.build.version.security_patch"));
        store.addResult("base_os", getProperty("ro.build.version.base_os"));
        store.endGroup(); // version
    }

    private String getProperty(String prop) throws Exception {
        return mDevice.executeShellCommand("getprop " + prop).replace("\n", "");
    }
}