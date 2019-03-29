/*
 * Copyright 2016 The Android Open Source Project
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

package android.graphics.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.UnsupportedEncodingException;

/**
 * Test that the Vulkan loader is present, supports the required extensions, and that system
 * features accurately indicate the capabilities of the Vulkan driver if one exists.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class VulkanFeaturesTest {

    static {
        System.loadLibrary("ctsgraphics_jni");
    }

    private static final String TAG = VulkanFeaturesTest.class.getSimpleName();
    private static final boolean DEBUG = false;

    // Require patch version 3 for Vulkan 1.0: It was the first publicly available version,
    // and there was an important bugfix relative to 1.0.2.
    private static final int VULKAN_1_0 = 0x00400003; // 1.0.3

    private PackageManager mPm;
    private FeatureInfo mVulkanHardwareLevel = null;
    private FeatureInfo mVulkanHardwareVersion = null;
    private FeatureInfo mVulkanHardwareCompute = null;
    private JSONObject mVulkanDevices[];

    @Before
    public void setup() throws Throwable {
        mPm = InstrumentationRegistry.getTargetContext().getPackageManager();
        FeatureInfo features[] = mPm.getSystemAvailableFeatures();
        if (features != null) {
            for (FeatureInfo feature : features) {
                if (PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL.equals(feature.name)) {
                    mVulkanHardwareLevel = feature;
                    if (DEBUG) {
                        Log.d(TAG, feature.name + "=" + feature.version);
                    }
                } else if (PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(feature.name)) {
                    mVulkanHardwareVersion = feature;
                    if (DEBUG) {
                        Log.d(TAG, feature.name + "=0x" + Integer.toHexString(feature.version));
                    }
                } else if (PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE.equals(feature.name)) {
                    mVulkanHardwareCompute = feature;
                    if (DEBUG) {
                        Log.d(TAG, feature.name + "=" + feature.version);
                    }
                }
            }
        }

        mVulkanDevices = getVulkanDevices();
    }

    @Test
    public void testVulkanHardwareFeatures() throws JSONException {
        if (DEBUG) {
            Log.d(TAG, "Inspecting " + mVulkanDevices.length + " devices");
        }
        if (mVulkanDevices.length == 0) {
            assertNull("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL +
                       " is supported, but no Vulkan physical devices are available",
                       mVulkanHardwareLevel);
            assertNull("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_VERSION +
                       " is supported, but no Vulkan physical devices are available",
                       mVulkanHardwareLevel);
            assertNull("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE +
                       " is supported, but no Vulkan physical devices are available",
                       mVulkanHardwareCompute);
            return;
        }
        assertNotNull("Vulkan physical devices are available, but system feature " +
                      PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL + " is not supported",
                      mVulkanHardwareLevel);
        assertNotNull("Vulkan physical devices are available, but system feature " +
                      PackageManager.FEATURE_VULKAN_HARDWARE_VERSION + " is not supported",
                      mVulkanHardwareVersion);
        if (mVulkanHardwareLevel == null || mVulkanHardwareVersion == null) {
            return;
        }

        assertTrue("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL +
                   " version " + mVulkanHardwareLevel.version + " is not one of the defined " +
                   " versions [0..1]",
                   mVulkanHardwareLevel.version >= 0 && mVulkanHardwareLevel.version <= 1);
        assertTrue("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_VERSION +
                   " version 0x" + Integer.toHexString(mVulkanHardwareVersion.version) + " is not" +
                   " one of the versions allowed",
                   isHardwareVersionAllowed(mVulkanHardwareVersion.version));
        if (mVulkanHardwareCompute != null) {
            assertTrue("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE +
                       " version " + mVulkanHardwareCompute.version +
                       " is not one of the versions allowed",
                       mVulkanHardwareCompute.version == 0);
        }

        JSONObject bestDevice = null;
        int bestDeviceLevel = -1;
        int bestComputeLevel = -1;
        int bestDeviceVersion = -1;
        for (JSONObject device : mVulkanDevices) {
            int level = determineHardwareLevel(device);
            int compute = determineHardwareCompute(device);
            int version = determineHardwareVersion(device);
            if (DEBUG) {
                Log.d(TAG, device.getJSONObject("properties").getString("deviceName") +
                    ": level=" + level + " compute=" + compute +
                    " version=0x" + Integer.toHexString(version));
            }
            if (level >= bestDeviceLevel && compute >= bestComputeLevel &&
                    version >= bestDeviceVersion) {
                bestDevice = device;
                bestDeviceLevel = level;
                bestComputeLevel = compute;
                bestDeviceVersion = version;
            }
        }

        assertEquals("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL +
            " version " + mVulkanHardwareLevel.version + " doesn't match best physical device " +
            " hardware level " + bestDeviceLevel,
            bestDeviceLevel, mVulkanHardwareLevel.version);
        assertTrue(
            "System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_VERSION +
            " version 0x" + Integer.toHexString(mVulkanHardwareVersion.version) +
            " isn't close enough (same major and minor version, less or equal patch version)" +
            " to best physical device version 0x" + Integer.toHexString(bestDeviceVersion),
            isVersionCompatible(bestDeviceVersion, mVulkanHardwareVersion.version));
        if (mVulkanHardwareCompute == null) {
            assertEquals("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE +
                " not present, but required features are supported",
                bestComputeLevel, -1);
        } else {
            assertEquals("System feature " + PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE +
                " version " + mVulkanHardwareCompute.version +
                " doesn't match best physical device (version: " + bestComputeLevel + ")",
                bestComputeLevel, mVulkanHardwareCompute.version);
        }
    }

    @Test
    public void testVulkanVersionForVrHighPerformance() {
        if (!mPm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE))
            return;
        assertTrue(
            "VR high-performance devices must support Vulkan 1.0 with Hardware Level 0, " +
            "but this device does not.",
            mVulkanHardwareVersion != null && mVulkanHardwareVersion.version >= VULKAN_1_0 &&
            mVulkanHardwareLevel != null && mVulkanHardwareLevel.version >= 0);
    }

    private int determineHardwareLevel(JSONObject device) throws JSONException {
        JSONObject features = device.getJSONObject("features");
        boolean textureCompressionETC2 = features.getInt("textureCompressionETC2") != 0;
        boolean fullDrawIndexUint32 = features.getInt("fullDrawIndexUint32") != 0;
        boolean imageCubeArray = features.getInt("imageCubeArray") != 0;
        boolean independentBlend = features.getInt("independentBlend") != 0;
        boolean geometryShader = features.getInt("geometryShader") != 0;
        boolean tessellationShader = features.getInt("tessellationShader") != 0;
        boolean sampleRateShading = features.getInt("sampleRateShading") != 0;
        boolean textureCompressionASTC_LDR = features.getInt("textureCompressionASTC_LDR") != 0;
        boolean fragmentStoresAndAtomics = features.getInt("fragmentStoresAndAtomics") != 0;
        boolean shaderImageGatherExtended = features.getInt("shaderImageGatherExtended") != 0;
        boolean shaderUniformBufferArrayDynamicIndexing = features.getInt("shaderUniformBufferArrayDynamicIndexing") != 0;
        boolean shaderSampledImageArrayDynamicIndexing = features.getInt("shaderSampledImageArrayDynamicIndexing") != 0;
        if (!textureCompressionETC2) {
            return -1;
        }
        if (!fullDrawIndexUint32 ||
            !imageCubeArray ||
            !independentBlend ||
            !geometryShader ||
            !tessellationShader ||
            !sampleRateShading ||
            !textureCompressionASTC_LDR ||
            !fragmentStoresAndAtomics ||
            !shaderImageGatherExtended ||
            !shaderUniformBufferArrayDynamicIndexing ||
            !shaderSampledImageArrayDynamicIndexing) {
            return 0;
        }
        return 1;
    }

    private int determineHardwareCompute(JSONObject device) throws JSONException {
        JSONObject variablePointersFeatures = device.getJSONObject("variablePointersFeaturesKHR");
        boolean variablePointers = variablePointersFeatures.getInt("variablePointers") != 0;
        JSONObject limits = device.getJSONObject("properties").getJSONObject("limits");
        int maxPerStageDescriptorStorageBuffers = limits.getInt("maxPerStageDescriptorStorageBuffers");
        if (DEBUG) {
            Log.d(TAG, device.getJSONObject("properties").getString("deviceName") +
                ": variablePointers=" + variablePointers +
                " maxPerStageDescriptorStorageBuffers=" + maxPerStageDescriptorStorageBuffers);
        }
        if (!variablePointers || maxPerStageDescriptorStorageBuffers < 16)
            return -1;
        return 0;
    }

    private int determineHardwareVersion(JSONObject device) throws JSONException {
        return device.getJSONObject("properties").getInt("apiVersion");
    }

    private boolean isVersionCompatible(int expected, int actual) {
        int expectedMajor = (expected >> 22) & 0x3FF;
        int expectedMinor = (expected >> 12) & 0x3FF;
        int expectedPatch = (expected >>  0) & 0xFFF;
        int actualMajor = (actual >> 22) & 0x3FF;
        int actualMinor = (actual >> 12) & 0x3FF;
        int actualPatch = (actual >>  0) & 0xFFF;
        return (actualMajor == expectedMajor) &&
               (actualMinor == expectedMinor) &&
               (actualPatch <= expectedPatch);
    }

    private boolean isHardwareVersionAllowed(int actual) {
        // Limit which system feature hardware versions are allowed. If a new major/minor version
        // is released, we don't want devices claiming support for it until tests for the new
        // version are available. And only claiming support for a base patch level per major/minor
        // pair reduces fragmentation seen by developers. Patch-level changes are supposed to be
        // forwards and backwards compatible; if a developer *really* needs to alter behavior based
        // on the patch version, they can do so at runtime, but must be able to handle previous
        // patch versions.
        final int[] ALLOWED_HARDWARE_VERSIONS = {
            VULKAN_1_0,
        };
        for (int expected : ALLOWED_HARDWARE_VERSIONS) {
            if (actual == expected) {
                return true;
            }
        }
        return false;
    }

    private static native String nativeGetVkJSON();

    private JSONObject[] getVulkanDevices() throws JSONException, UnsupportedEncodingException {
        JSONArray vkjson = new JSONArray(nativeGetVkJSON());
        JSONObject[] devices = new JSONObject[vkjson.length()];
        for (int i = 0; i < vkjson.length(); i++) {
            devices[i] = vkjson.getJSONObject(i);
        }
        return devices;
    }
}
