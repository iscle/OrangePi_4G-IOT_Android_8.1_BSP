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
package android.edi.cts;

import com.android.compatibility.common.util.DeviceInfo;
import com.android.compatibility.common.util.HostInfoStore;
import com.android.ddmlib.FileListingService.FileEntry;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;

import java.util.Arrays;

public class LibraryDeviceInfo extends DeviceInfo {

    private ITestDevice mDevice;

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {

        mDevice = getDevice();

        collectSystemLibs(store);
        collectVendorLibs(store);
        collectFrameworkJars(store);
    }

    private void collectSystemLibs(HostInfoStore store) throws Exception {
        store.startArray("lib");
        collectFileDetails(store, "/system/lib", ".so");
        store.endArray();
    }

    private void collectVendorLibs(HostInfoStore store) throws Exception {
        store.startArray("vendor_lib");
        collectFileDetails(store, "/system/vendor/lib", ".so");
        store.endArray();
    }

    private void collectFrameworkJars(HostInfoStore store) throws Exception {
        store.startArray("framework_jar");
        collectFileDetails(store, "/system/framework", ".jar");
        store.endArray();
    }

    private void collectFileDetails(HostInfoStore store, String path, String suffix)
            throws Exception {
        IFileEntry dir = mDevice.getFileEntry(path);

        if(dir == null || !dir.isDirectory()) {
            return;
        }

        for (IFileEntry file : dir.getChildren(false)) {
            String name = file.getName();
            if (!file.isDirectory() && name.endsWith(suffix)) {
                String sha1 = getSha1(file.getFullPath());
                store.startGroup();
                store.addResult("name", name);
                store.addResult("sha1", sha1);
                store.endGroup();
            }
        }
    }

    private String getSha1(String filePath) {
        String sha1 = "unknown";
        try {
            String out = mDevice.executeShellCommand("sha1sum " + filePath);
            sha1 = out.split(" ", 2)[0].toUpperCase();
        } catch (Exception e) {
            // Do nothing.
        }
        return sha1;
    }
}