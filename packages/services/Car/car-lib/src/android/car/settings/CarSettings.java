/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package android.car.settings;

/**
 * System level car related settings.
 */
public class CarSettings {

    /**
     * Global car settings, containing preferences that always apply identically
     * to all defined users.  Applications can read these but are not allowed to write;
     * like the "Secure" settings, these are for preferences that the user must
     * explicitly modify through the system UI or specialized APIs for those values.
     *
     * To read/write the global car settings, use {@link android.provider.Settings.Global}
     * with the keys defined here.
     */
    public static final class Global {
        /**
         * Key for when to wake up to run garage mode.
         */
        public static final String KEY_GARAGE_MODE_WAKE_UP_TIME =
                "android.car.GARAGE_MODE_WAKE_UP_TIME";
        /**
         * Key for whether garage mode is enabled.
         */
        public static final String KEY_GARAGE_MODE_ENABLED = "android.car.GARAGE_MODE_ENABLED";
        /**
         * Key for garage mode maintenance window.
         */
        public static final String KEY_GARAGE_MODE_MAINTENANCE_WINDOW =
                "android.car.GARAGE_MODE_MAINTENANCE_WINDOW";
        /**
         * Key for music volume. This is used internally, changing this value will not change the
         * volume.
         *
         * @hide
         */
        public static final String KEY_VOLUME_MUSIC = "android.car.VOLUME_MUSIC";
        /**
         * Key for navigation volume. This is used internally, changing this value will not change
         * the volume.
         *
         * @hide
         */
        public static final String KEY_VOLUME_NAVIGATION = "android.car.VOLUME_NAVIGATION";
        /**
         * Key for voice command volume. This is used internally, changing this value will
         * not change the volume.
         *
         * @hide
         */
        public static final String KEY_VOLUME_VOICE_COMMAND = "android.car.VOLUME_VOICE_COMMAND";
        /**
         * Key for call volume. This is used internally, changing this value will not change the
         * volume.
         *
         * @hide
         */
        public static final String KEY_VOLUME_CALL = "android.car.VOLUME_CALL";
        /**
         * Key for phone ring volume. This is used internally, changing this value will not change
         * the volume.
         *
         * @hide
         */
        public static final String KEY_VOLUME_RINGTONE = "android.car.VOLUME_RINGTONE";
        /**
         * Key for alarm volume. This is used internally, changing this value will not change the
         * volume.
         *
         * @hide
         */
        public static final String KEY_VOLUME_ALARM = "android.car.VOLUME_ALARM";
        /**
         * Key for notification volume. This is used internally, changing this value will not change
         * the volume.
         *
         * @hide
         */
        public static final String KEY_VOLUME_NOTIFICATION = "android.car.VOLUME_NOTIFICATION";
        /**
         * Key for safety alert volume. This is used internally, changing this value will not
         * change the volume.
         *
         * @hide
         */
        public static final String KEY_VOLUME_SAFETY_ALERT = "android.car.VOLUME_SAFETY_ALERT";
        /**
         * Key for cd volume. This is used internally, changing this value will not change the
         * volume.
         *
         * @hide
         */
        public static final String KEY_VOLUME_CD_ROM = "android.car.VOLUME_CD_ROM";
        /**
         * Key for aux volume. This is used internally, changing this value will not change the
         * volume.
         *
         * @hide
         */
        public static final String KEY_VOLUME_AUX = "android.car.VOLUME_AUX";
        /**
         * Key for system volume. This is used internally, changing this value will not change the
         * volume.
         *
         * @hide
         */
        public static final String KEY_VOLUME_SYSTEM_SOUND = "android.car.VOLUME_SYSTEM";
        /**
         * Key for radio volume. This is used internally, changing this value will not change the
         * volume.
         *
         * @hide
         */
        public static final String KEY_VOLUME_RADIO = "android.car.VOLUME_RADIO";
    }

    /**
     * Default garage mode wake up time 00:00
     *
     * @hide
     */
    public static final int[] DEFAULT_GARAGE_MODE_WAKE_UP_TIME = {0, 0};

    /**
     * Default garage mode maintenance window 10 mins.
     *
     * @hide
     */
    public static final int DEFAULT_GARAGE_MODE_MAINTENANCE_WINDOW = 10 * 60 * 1000; // 10 mins

    /**
     * @hide
     */
    public static final class Secure {

        /**
         * Key for a list of devices to automatically connect on Bluetooth A2dp/Avrcp profiles
         * Written to and read by {@link com.android.car.BluetoothDeviceConnectionPolicy}
         * @hide
         */
        public static final String KEY_BLUETOOTH_AUTOCONNECT_MUSIC_DEVICES =
                "android.car.BLUETOOTH_AUTOCONNECT_MUSIC_DEVICES";
        /**
         * Key for a list of devices to automatically connect on Bluetooth HFP & PBAP profiles
         * Written to and read by {@link com.android.car.BluetoothDeviceConnectionPolicy}
         *
         * @hide
         */
        public static final String KEY_BLUETOOTH_AUTOCONNECT_PHONE_DEVICES =
                "android.car.BLUETOOTH_AUTOCONNECT_PHONE_DEVICES";

        /**
         * Key for a list of devices to automatically connect on Bluetooth MAP profile
         * Written to and read by {@link com.android.car.BluetoothDeviceConnectionPolicy}
         * @hide
         */
        public static final String KEY_BLUETOOTH_AUTOCONNECT_MESSAGING_DEVICES =
                "android.car.BLUETOOTH_AUTOCONNECT_MESSAGING_DEVICES";

        /**
         * Key for setting primary Music Device
         * Written to by a client with {@link com.android.car.Manifest.permission.BLUETOOTH_ADMIN}
         * Read by {@link com.android.car.BluetoothDeviceConnectionPolicy}
         * @hide
         */
        public static final String KEY_BLUETOOTH_AUTOCONNECT_MUSIC_DEVICE_PRIORITY_0 =
                "android.car.BLUETOOTH_AUTOCONNECT_MUSIC_DEVICE_PRIORITY_0";

        /**
         * Key for setting secondary Music Device
         * Written to by a client with {@link com.android.car.Manifest.permission.BLUETOOTH_ADMIN}
         * Read by {@link com.android.car.BluetoothDeviceConnectionPolicy}
         * @hide
         */
        public static final String KEY_BLUETOOTH_AUTOCONNECT_MUSIC_DEVICE_PRIORITY_1 =
                "android.car.BLUETOOTH_AUTOCONNECT_MUSIC_DEVICE_PRIORITY_1";

        /**
         * Key for setting Primary Phone Device
         * Written to by a client with {@link com.android.car.Manifest.permission.BLUETOOTH_ADMIN}
         * Read by {@link com.android.car.BluetoothDeviceConnectionPolicy}
         * @hide
         */
        public static final String KEY_BLUETOOTH_AUTOCONNECT_PHONE_DEVICE_PRIORITY_0 =
                "android.car.BLUETOOTH_AUTOCONNECT_PHONE_DEVICE_PRIORITY_0";

        /**
         * Key for setting Secondary Phone Device
         * Written to by a client with {@link com.android.car.Manifest.permission.BLUETOOTH_ADMIN}
         * Read by {@link com.android.car.BluetoothDeviceConnectionPolicy}
         * @hide
         */
        public static final String KEY_BLUETOOTH_AUTOCONNECT_PHONE_DEVICE_PRIORITY_1 =
                "android.car.BLUETOOTH_AUTOCONNECT_PHONE_DEVICE_PRIORITY_1";

        /**
         * Key for setting Primary Messaging Device
         * Written to by a client with {@link com.android.car.Manifest.permission.BLUETOOTH_ADMIN}
         * Read by {@link com.android.car.BluetoothDeviceConnectionPolicy}
         * @hide
         */
        public static final String KEY_BLUETOOTH_AUTOCONNECT_MESSAGING_DEVICE_PRIORITY_0 =
                "android.car.BLUETOOTH_AUTOCONNECT_MESSAGING_DEVICE_PRIORITY_0";

        /**
         * Key for setting Secondary Messaging Device
         * Written to by a client with {@link com.android.car.Manifest.permission.BLUETOOTH_ADMIN}
         * Read by {@link com.android.car.BluetoothDeviceConnectionPolicy}
         * @hide
         */
        public static final String KEY_BLUETOOTH_AUTOCONNECT_MESSAGING_DEVICE_PRIORITY_1 =
                "android.car.BLUETOOTH_AUTOCONNECT_MESSAGING_DEVICE_PRIORITY_1";

    }
}
