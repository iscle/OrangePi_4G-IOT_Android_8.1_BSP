/*
 * Copyright (C) 2010 The Android Open Source Project
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

/*
 * This file references fs_error.png, fs_good.png, fs_indeterminate.png,
 * and fs_warning.png which are licensed under Creative Commons 3.0
 * by fatcow.com.
 * http://www.fatcow.com/free-icons/
 * http://creativecommons.org/licenses/by/3.0/us/
 */

package com.android.cts.verifier.features;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

public class FeatureSummaryActivity extends PassFailButtons.ListActivity {
    /**
     * Simple storage class for data about an Android feature.
     */
    static class Feature {
        /**
         * The name of the feature. Should be one of the PackageManager.FEATURE*
         * constants.
         */
        public String name;

        /**
         * Indicates whether the field is present on the current device.
         */
        public boolean present;

        /**
         * Indicates whether the field is required for the current device.
         */
        public boolean required;

        /**
         * Constructor does not include 'present' because that's a detected
         * value, and not set during creation.
         *
         * @param name value for this.name
         * @param required value for this.required
         */
        public Feature(String name, boolean required) {
            this.name = name;
            this.required = required;
            this.present = false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o == null || !(o instanceof Feature)) {
                return false;
            } else {
                Feature feature = (Feature) o;
                return name.equals(feature.name);
            }
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    public static final Feature[] ALL_ECLAIR_FEATURES = {
            new Feature(PackageManager.FEATURE_CAMERA, true),
            new Feature(PackageManager.FEATURE_CAMERA_AUTOFOCUS, false),
            new Feature(PackageManager.FEATURE_CAMERA_FLASH, false),
            new Feature(PackageManager.FEATURE_LIVE_WALLPAPER, false),
            new Feature(PackageManager.FEATURE_SENSOR_LIGHT, false),
            new Feature(PackageManager.FEATURE_SENSOR_PROXIMITY, false),
            new Feature(PackageManager.FEATURE_TELEPHONY, false),
            new Feature(PackageManager.FEATURE_TELEPHONY_CDMA, false),
            new Feature(PackageManager.FEATURE_TELEPHONY_GSM, false),
    };

    public static final Feature[] ALL_FROYO_FEATURES = {
            new Feature("android.hardware.bluetooth", true),
            new Feature("android.hardware.location", true),
            new Feature("android.hardware.location.gps", true),
            new Feature("android.hardware.location.network", true),
            new Feature("android.hardware.microphone", true),
            new Feature("android.hardware.sensor.accelerometer", true),
            new Feature("android.hardware.sensor.compass", true),
            new Feature("android.hardware.touchscreen", true),
            new Feature("android.hardware.touchscreen.multitouch", false),
            new Feature("android.hardware.touchscreen.multitouch.distinct", false),
            new Feature("android.hardware.wifi", false),
    };

    public static final Feature[] ALL_GINGERBREAD_FEATURES = {
            // Required features in prior releases that became optional in GB
            new Feature("android.hardware.bluetooth", false),
            new Feature("android.hardware.camera", false),
            new Feature("android.hardware.location.gps", false),
            new Feature("android.hardware.microphone", false),
            new Feature("android.hardware.sensor.accelerometer", false),
            new Feature("android.hardware.sensor.compass", false),

            // New features in GB
            new Feature("android.hardware.audio.low_latency", false),
            new Feature("android.hardware.camera.front", false),
            new Feature("android.hardware.nfc", false),
            new Feature("android.hardware.sensor.barometer", false),
            new Feature("android.hardware.sensor.gyroscope", false),
            new Feature("android.hardware.touchscreen.multitouch.jazzhand", false),
            new Feature("android.software.sip", false),
            new Feature("android.software.sip.voip", false),
    };

    public static final Feature[] ALL_GINGERBREAD_MR1_FEATURES = {
            new Feature("android.hardware.usb.accessory", false),
    };

    public static final Feature[] ALL_HONEYCOMB_FEATURES = {
            // Required features in prior releases that became optional in HC
            new Feature("android.hardware.touchscreen", false),

            new Feature("android.hardware.faketouch", true),
    };

    public static final Feature[] ALL_HONEYCOMB_MR1_FEATURES = {
            new Feature("android.hardware.usb.host", false),
            new Feature("android.hardware.usb.accessory", false),
    };

    public static final Feature[] ALL_HONEYCOMB_MR2_FEATURES = {
            new Feature("android.hardware.faketouch.multitouch.distinct", false),
            new Feature("android.hardware.faketouch.multitouch.jazzhand", false),
            new Feature("android.hardware.screen.landscape", false),
            new Feature("android.hardware.screen.portrait", false),
    };

    public static final Feature[] ALL_ICE_CREAM_SANDWICH_FEATURES = {
            new Feature(PackageManager.FEATURE_WIFI_DIRECT, false),
    };

    public static final Feature[] ALL_JELLY_BEAN_FEATURES = {
            // Required features in prior releases that became optional
            new Feature(PackageManager.FEATURE_FAKETOUCH, false),

            //new feature in JB
            new Feature(PackageManager.FEATURE_TELEVISION, false),
    };

    public static final Feature[] ALL_JELLY_BEAN_MR2_FEATURES = {
            new Feature("android.software.app_widgets", false),
            new Feature("android.software.input_methods", false),
            new Feature("android.software.home_screen", false),
            new Feature("android.hardware.bluetooth_le", false),
            new Feature("android.hardware.camera.any", false),
    };

    public static final Feature[] ALL_KITKAT_FEATURES = {
            new Feature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION, false),
            new Feature(PackageManager.FEATURE_CONSUMER_IR, false),
            new Feature(PackageManager.FEATURE_DEVICE_ADMIN, false),
            new Feature(PackageManager.FEATURE_SENSOR_STEP_COUNTER, false),
            new Feature(PackageManager.FEATURE_SENSOR_STEP_DETECTOR, false),
    };

    public static final Feature[] ALL_KITKAT_WATCH_FEATURES = {
            new Feature(PackageManager.FEATURE_SENSOR_HEART_RATE, false),
            new Feature(PackageManager.FEATURE_BACKUP, false),
            new Feature(PackageManager.FEATURE_PRINTING, false),
            new Feature(PackageManager.FEATURE_WATCH, false),
            new Feature(PackageManager.FEATURE_WEBVIEW, false),
            new Feature(PackageManager.FEATURE_CAMERA_EXTERNAL, false),
    };

    public static final Feature[] ALL_LOLLIPOP_FEATURES = {
            // New features in L
            new Feature(PackageManager.FEATURE_AUDIO_OUTPUT, false),
            new Feature(PackageManager.FEATURE_CAMERA_CAPABILITY_MANUAL_POST_PROCESSING, false),
            new Feature(PackageManager.FEATURE_CAMERA_CAPABILITY_MANUAL_SENSOR, false),
            new Feature(PackageManager.FEATURE_CAMERA_CAPABILITY_RAW, false),
            new Feature(PackageManager.FEATURE_CAMERA_LEVEL_FULL, false),
            new Feature(PackageManager.FEATURE_CONNECTION_SERVICE, false),
            new Feature(PackageManager.FEATURE_GAMEPAD, false),
            new Feature(PackageManager.FEATURE_LEANBACK, false),
            new Feature(PackageManager.FEATURE_LIVE_TV, false),
            new Feature(PackageManager.FEATURE_MANAGED_USERS, false),
            new Feature(PackageManager.FEATURE_OPENGLES_EXTENSION_PACK, false),
            new Feature(PackageManager.FEATURE_SECURELY_REMOVES_USERS, false),
            new Feature(PackageManager.FEATURE_SENSOR_AMBIENT_TEMPERATURE, false),
            new Feature(PackageManager.FEATURE_SENSOR_HEART_RATE_ECG, false),
            new Feature(PackageManager.FEATURE_SENSOR_RELATIVE_HUMIDITY, false),
            new Feature(PackageManager.FEATURE_VERIFIED_BOOT, false),

            // Features explicitly made optional in L
            new Feature(PackageManager.FEATURE_LOCATION_NETWORK, false),

            // New hidden features in L
            new Feature("android.hardware.ethernet", false),
            new Feature("android.hardware.hdmi.cec", false),
            new Feature("android.software.leanback_only", false),
            new Feature("android.software.voice_recognizers", false),
    };

    public static final Feature[] ALL_MNC_FEATURES = {
            new Feature(PackageManager.FEATURE_MIDI, false),
            new Feature(PackageManager.FEATURE_AUDIO_PRO, false),
            new Feature(PackageManager.FEATURE_AUTOMOTIVE, false),
            new Feature(PackageManager.FEATURE_HIFI_SENSORS, false),
            new Feature(PackageManager.FEATURE_FINGERPRINT, false),
    };

    public static final Feature[] ALL_NYC_FEATURES = {
            new Feature(PackageManager.FEATURE_VR_MODE, false),
            new Feature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE, false),
            new Feature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, false),
            new Feature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, false),
            new Feature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF, false),
            new Feature(PackageManager.FEATURE_PICTURE_IN_PICTURE, false),
            new Feature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT, false),
            // FEATURE_FILE_BASED_ENCRYPTION is hide
            new Feature("android.software.file_based_encryption", false),
    };

    public static final Feature[] ALL_O_FEATURES = {
            new Feature(PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE, false),
            // FEATURE_TELEPHONY_CARRIERLOCK is SystemApi
            new Feature("android.hardware.telephony.carrierlock", false),
            new Feature(PackageManager.FEATURE_WIFI_AWARE, false),
            new Feature(PackageManager.FEATURE_EMBEDDED, false),
            new Feature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP, false),
            new Feature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS, false),
            new Feature(PackageManager.FEATURE_VR_HEADTRACKING, false),
            // FEATURE_CTS is hide
            new Feature("android.software.cts", false),
            new Feature(PackageManager.FEATURE_WIFI_AWARE, false),
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fs_main);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.feature_summary, R.string.feature_summary_info, R.layout.fs_info);

        // some values used to detect warn-able conditions involving multiple
        // features
        boolean hasWifi = false;
        boolean hasTelephony = false;
        boolean hasBluetooth = false;
        boolean hasIllegalFeature = false;

        // get list of all features device thinks it has, & store in a HashMap
        // for fast lookups
        HashMap<String, String> actualFeatures = new HashMap<String, String>();
        for (FeatureInfo fi : getPackageManager().getSystemAvailableFeatures()) {
            actualFeatures.put(fi.name, fi.name);
        }

        // data structure that the SimpleAdapter will use to populate ListView
        ArrayList<HashMap<String, Object>> listViewData = new ArrayList<HashMap<String, Object>>();

        // roll over all known features & check whether device reports them
        boolean present = false;
        int statusIcon;
        Set<Feature> features = new LinkedHashSet<Feature>();

        // add features from latest to last so that the latest requirements are put in the set first
        int apiVersion = Build.VERSION.SDK_INT;
        if (apiVersion >= Build.VERSION_CODES.O) {
            Collections.addAll(features, ALL_O_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.N) {
            Collections.addAll(features, ALL_NYC_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.M) {
            Collections.addAll(features, ALL_MNC_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.LOLLIPOP) {
            Collections.addAll(features, ALL_LOLLIPOP_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.KITKAT_WATCH) {
            Collections.addAll(features, ALL_KITKAT_WATCH_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.KITKAT) {
            Collections.addAll(features, ALL_KITKAT_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Collections.addAll(features, ALL_JELLY_BEAN_MR2_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.JELLY_BEAN) {
            Collections.addAll(features, ALL_JELLY_BEAN_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Collections.addAll(features, ALL_ICE_CREAM_SANDWICH_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            Collections.addAll(features, ALL_HONEYCOMB_MR2_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.HONEYCOMB_MR1) {
            Collections.addAll(features, ALL_HONEYCOMB_MR1_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.HONEYCOMB) {
            Collections.addAll(features, ALL_HONEYCOMB_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.GINGERBREAD_MR1) {
            Collections.addAll(features, ALL_GINGERBREAD_MR1_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.GINGERBREAD) {
            Collections.addAll(features, ALL_GINGERBREAD_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.FROYO) {
            Collections.addAll(features, ALL_FROYO_FEATURES);
        }
        if (apiVersion >= Build.VERSION_CODES.ECLAIR_MR1) {
            Collections.addAll(features, ALL_ECLAIR_FEATURES);
        }
        for (Feature f : features) {
            HashMap<String, Object> row = new HashMap<String, Object>();
            listViewData.add(row);
            present = actualFeatures.containsKey(f.name);
            if (present) {
                // device reports it -- yay! set the happy icon
                hasWifi = hasWifi || PackageManager.FEATURE_WIFI.equals(f.name);
                hasTelephony = hasTelephony || PackageManager.FEATURE_TELEPHONY.equals(f.name);
                hasBluetooth = hasBluetooth || PackageManager.FEATURE_BLUETOOTH.equals(f.name);
                statusIcon = R.drawable.fs_good;
                actualFeatures.remove(f.name);
            } else if (!present && f.required) {
                // it's required, but device doesn't report it. Boo, set the
                // bogus icon
                statusIcon = R.drawable.fs_error;
            } else {
                // device doesn't report it, but it's not req'd, so can't tell
                // if there's a problem
                statusIcon = R.drawable.fs_indeterminate;
            }
            row.put("feature", f.name);
            row.put("icon", statusIcon);
        }

        // now roll over any remaining features (which are non-standard)
        for (String feature : actualFeatures.keySet()) {
            if (feature == null || "".equals(feature))
                continue;
            HashMap<String, Object> row = new HashMap<String, Object>();
            listViewData.add(row);
            row.put("feature", feature);
            if (feature.startsWith("android")) { // intentionally not "android."
                // sorry, you're not allowed to squat in the official namespace;
                // set bogus icon
                row.put("icon", R.drawable.fs_error);
                hasIllegalFeature = true;
            } else {
                // non-standard features are okay, but flag them just in case
                row.put("icon", R.drawable.fs_warning);
            }
        }

        // sort the ListView's data to group by icon type, for easier reading by
        // humans
        final HashMap<Integer, Integer> idMap = new HashMap<Integer, Integer>();
        idMap.put(R.drawable.fs_error, 0);
        idMap.put(R.drawable.fs_warning, 1);
        idMap.put(R.drawable.fs_indeterminate, 2);
        idMap.put(R.drawable.fs_good, 3);
        Collections.sort(listViewData, new Comparator<HashMap<String, Object>>() {
            public int compare(HashMap<String, Object> left, HashMap<String, Object> right) {
                int leftId = idMap.get(left.get("icon"));
                int rightId = idMap.get(right.get("icon"));
                if (leftId == rightId) {
                    return ((String) left.get("feature")).compareTo((String) right.get("feature"));
                }
                if (leftId < rightId)
                    return -1;
                return 1;
            }
        });

        // Set up the SimpleAdapter used to populate the ListView
        SimpleAdapter adapter = new SimpleAdapter(this, listViewData, R.layout.fs_row,
                new String[] {
                        "feature", "icon"
                }, new int[] {
                        R.id.fs_feature, R.id.fs_icon
                });
        adapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            public boolean setViewValue(View view, Object data, String repr) {
                try {
                    if (view instanceof ImageView) {
                        ((ImageView) view).setImageResource((Integer) data);
                    } else if (view instanceof TextView) {
                        ((TextView) view).setText((String) data);
                    } else {
                        return false;
                    }
                    return true;
                } catch (ClassCastException e) {
                    return false;
                }
            }
        });
        setListAdapter(adapter);

        // finally, check for our second-order error cases and set warning text
        // if necessary
        StringBuffer sb = new StringBuffer();
        if (hasIllegalFeature) {
            sb.append(getResources().getString(R.string.fs_disallowed)).append("\n");
        }

        if (!hasWifi && !hasTelephony && !hasBluetooth) {
            sb.append(getResources().getString(R.string.fs_missing_wifi_telephony)).append("\n");
        }

        String warnings = sb.toString().trim();
        if (warnings == null || "".equals(warnings)) {
            ((TextView) (findViewById(R.id.fs_warnings))).setVisibility(View.GONE);
        } else {
            ((TextView) (findViewById(R.id.fs_warnings))).setText(warnings);
        }
    }
}
