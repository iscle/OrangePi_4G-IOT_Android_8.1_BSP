/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.bluetooth.gatt;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import java.util.UUID;

/**
 * Helper class containing useful tools for GATT service debugging.
 */
/*package*/ class GattDebugUtils {
    private static final String TAG = GattServiceConfig.TAG_PREFIX + "DebugUtils";
    private static final boolean DEBUG_ADMIN = GattServiceConfig.DEBUG_ADMIN;

    private static final String ACTION_GATT_PAIRING_CONFIG =
                                "android.bluetooth.action.GATT_PAIRING_CONFIG";

    private static final String ACTION_GATT_TEST_USAGE =
                                "android.bluetooth.action.GATT_TEST_USAGE";
    private static final String ACTION_GATT_TEST_ENABLE =
                                "android.bluetooth.action.GATT_TEST_ENABLE";
    private static final String ACTION_GATT_TEST_CONNECT =
                                "android.bluetooth.action.GATT_TEST_CONNECT";
    private static final String ACTION_GATT_TEST_DISCONNECT =
                                "android.bluetooth.action.GATT_TEST_DISCONNECT";
    private static final String ACTION_GATT_TEST_DISCOVER =
                                "android.bluetooth.action.GATT_TEST_DISCOVER";

    private static final String EXTRA_ENABLE = "enable";
    private static final String EXTRA_ADDRESS = "address";
    private static final String EXTRA_UUID = "uuid";
    private static final String EXTRA_TYPE = "type";
    private static final String EXTRA_ADDR_TYPE = "addr_type";
    private static final String EXTRA_SHANDLE = "start";
    private static final String EXTRA_EHANDLE = "end";
    private static final String EXTRA_AUTH_REQ = "auth_req";
    private static final String EXTRA_IO_CAP = "io_cap";
    private static final String EXTRA_INIT_KEY = "init_key";
    private static final String EXTRA_RESP_KEY = "resp_key";
    private static final String EXTRA_MAX_KEY = "max_key";

    /**
     * Handles intents passed in via GattService.onStartCommand().
     * This allows sending debug actions to the running service.
     * To trigger a debug action, invoke the following shell command:
     *
     *   adb shell am startservice -a <action> <component>
     *
     * Where <action> represents one of the ACTION_* constants defines above
     * and  <component> identifies the GATT service.
     *
     * For example:
     *   import com.android.bluetooth.gatt.GattService;
     */
    static boolean handleDebugAction(GattService svc, Intent intent) {
        if (!DEBUG_ADMIN) return false;

        String action = intent.getAction();
        Log.d(TAG, "handleDebugAction() action=" + action);

        /*
         * PTS test commands
         */

        if (ACTION_GATT_TEST_USAGE.equals(action)) {
            logUsageInfo();

        } else if (ACTION_GATT_TEST_ENABLE.equals(action)) {
            boolean bEnable = intent.getBooleanExtra(EXTRA_ENABLE, true);
            svc.gattTestCommand( 0x01, null,null, bEnable ? 1 : 0, 0,0,0,0);

        } else if (ACTION_GATT_TEST_CONNECT.equals(action)) {
            String address = intent.getStringExtra(EXTRA_ADDRESS);
            int type = intent.getIntExtra(EXTRA_TYPE, 2 /* LE device */);
            int addr_type = intent.getIntExtra(EXTRA_ADDR_TYPE, 0 /* Static */);
            svc.gattTestCommand( 0x02, null, address, type, addr_type, 0,0,0);

        } else if (ACTION_GATT_TEST_DISCONNECT.equals(action)) {
            svc.gattTestCommand( 0x03, null, null, 0,0,0,0,0);

        } else if (ACTION_GATT_TEST_DISCOVER.equals(action)) {
            UUID uuid = getUuidExtra(intent);
            int type = intent.getIntExtra(EXTRA_TYPE, 1 /* All services */);
            int shdl = getHandleExtra(intent, EXTRA_SHANDLE, 1);
            int ehdl = getHandleExtra(intent, EXTRA_EHANDLE, 0xFFFF);
            svc.gattTestCommand( 0x04, uuid, null, type, shdl, ehdl, 0,0);

        } else if (ACTION_GATT_PAIRING_CONFIG.equals(action)) {
            int auth_req = intent.getIntExtra(EXTRA_AUTH_REQ, 5);
            int io_cap = intent.getIntExtra(EXTRA_IO_CAP, 4);
            int init_key = intent.getIntExtra(EXTRA_INIT_KEY, 7);
            int resp_key = intent.getIntExtra(EXTRA_RESP_KEY, 7);
            int max_key = intent.getIntExtra(EXTRA_MAX_KEY, 16);
            svc.gattTestCommand( 0xF0, null, null, auth_req, io_cap, init_key,
                                 resp_key, max_key);

        } else {
            return false;
        }

        return true;
    }

    /**
     * Attempts to retrieve an extra from an intent first as hex value,
     * then as an ineger.
     * @hide
     */
    static private int getHandleExtra(Intent intent, String extra, int default_value) {
        Bundle extras = intent.getExtras();
        Object uuid = extras != null ? extras.get(extra) : null;
        if (uuid != null && uuid.getClass().getName().equals("java.lang.String")) {
            try
            {
                return Integer.parseInt(extras.getString(extra), 16);
            } catch (NumberFormatException e) {
                return default_value;
            }
        }

        return intent.getIntExtra(extra, default_value);
    }

    /**
     * Retrieves the EXTRA_UUID parameter.
     * If a string of length 4 is detected, a 16-bit hex UUID is assumed and
     * the default Bluetooth UUID is appended.
     * @hide
     */
    static private UUID getUuidExtra(Intent intent) {
        String uuidStr = intent.getStringExtra(EXTRA_UUID);
        if (uuidStr != null && uuidStr.length() == 4) {
            uuidStr = String.format("0000%s-0000-1000-8000-00805f9b34fb", uuidStr);
        }
        return (uuidStr != null) ? UUID.fromString(uuidStr) : null;
    }

    /**
     * Log usage information.
     * @hide
     */
    static private void logUsageInfo() {
        StringBuilder b = new StringBuilder();
        b.append(  "------------ GATT TEST ACTIONS  ----------------");
        b.append("\nGATT_TEST_ENABLE");
        b.append("\n  [--ez enable <bool>] Enable or disable,");
        b.append("\n                       defaults to true (enable).\n");
        b.append("\nGATT_TEST_CONNECT");
        b.append("\n   --es address <bda>");
        b.append("\n  [--ei addr_type <type>] Possible values:");
        b.append("\n                         0 = Static (default)");
        b.append("\n                         1 = Random\n");
        b.append("\n  [--ei type <type>]   Default is 2 (LE Only)\n");
        b.append("\nGATT_TEST_DISCONNECT\n");
        b.append("\nGATT_TEST_DISCOVER");
        b.append("\n  [--ei type <type>]   Possible values:");
        b.append("\n                         1 = Discover all services (default)");
        b.append("\n                         2 = Discover services by UUID");
        b.append("\n                         3 = Discover included services");
        b.append("\n                         4 = Discover characteristics");
        b.append("\n                         5 = Discover descriptors\n");
        b.append("\n  [--es uuid <uuid>]   Optional; Can be either full 128-bit");
        b.append("\n                       UUID hex string, or 4 hex characters");
        b.append("\n                       for 16-bit UUIDs.\n");
        b.append("\n  [--ei start <hdl>]   Start of handle range (default 1)");
        b.append("\n  [--ei end <hdl>]     End of handle range (default 65355)");
        b.append("\n    or");
        b.append("\n  [--es start <hdl>]   Start of handle range (hex format)");
        b.append("\n  [--es end <hdl>]     End of handle range (hex format)\n");
        b.append("\nGATT_PAIRING_CONFIG");
        b.append("\n  [--ei auth_req]      Authentication flag (default 5)");
        b.append("\n  [--ei io_cap]        IO capabilities (default 4)");
        b.append("\n  [--ei init_key]      Initial key size (default 7)");
        b.append("\n  [--ei resp_key]      Response key size (default 7)");
        b.append("\n  [--ei max_key]       Maximum key size (default 16)");
        b.append("\n------------------------------------------------");
        Log.i(TAG, b.toString());
    }
}
