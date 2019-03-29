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
package com.android.cts.deviceandprofileowner.userrestrictions;

import android.os.UserManager;

public class DeviceOwnerUserRestrictionsTest extends BaseUserRestrictionsTest {
    public static final String[] ALLOWED = new String[] {
            // UserManager.DISALLOW_CONFIG_WIFI, // Has unrecoverable side effects.
            UserManager.DISALLOW_MODIFY_ACCOUNTS,
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_UNINSTALL_APPS,
            // UserManager.DISALLOW_SHARE_LOCATION, // Has unrecoverable side effects.
            // UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, // Has unrecoverable side effects.
            UserManager.DISALLOW_CONFIG_BLUETOOTH,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_CONFIG_CREDENTIALS,
            UserManager.DISALLOW_REMOVE_USER,
            UserManager.DISALLOW_REMOVE_MANAGED_PROFILE,
            // UserManager.DISALLOW_DEBUGGING_FEATURES, // Need for CTS
            UserManager.DISALLOW_CONFIG_VPN,
            UserManager.DISALLOW_CONFIG_TETHERING,
            UserManager.DISALLOW_NETWORK_RESET,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_ADD_MANAGED_PROFILE,
            // UserManager.ENSURE_VERIFY_APPS, // Has unrecoverable side effects.
            UserManager.DISALLOW_CONFIG_CELL_BROADCASTS,
            UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
            UserManager.DISALLOW_APPS_CONTROL,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_UNMUTE_MICROPHONE,
            UserManager.DISALLOW_ADJUST_VOLUME,
            UserManager.DISALLOW_OUTGOING_CALLS,
            UserManager.DISALLOW_SMS,
            UserManager.DISALLOW_FUN,
            UserManager.DISALLOW_CREATE_WINDOWS,
            UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE,
            UserManager.DISALLOW_OUTGOING_BEAM,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.ALLOW_PARENT_PROFILE_APP_LINKING,
            // UserManager.DISALLOW_DATA_ROAMING, // Has unrecoverable side effects.
            UserManager.DISALLOW_SET_USER_ICON,
            UserManager.DISALLOW_BLUETOOTH,
            UserManager.DISALLOW_AUTOFILL
    };

    public static final String[] DISALLOWED = new String[] {
            // DO can set all public restrictions.
    };

    public static final String[] DEFAULT_ENABLED = new String[] {
            UserManager.DISALLOW_ADD_MANAGED_PROFILE
    };

    @Override
    protected String[] getAllowedRestrictions() {
        return ALLOWED;
    }

    @Override
    protected String[] getDisallowedRestrictions() {
        return DISALLOWED;
    }

    @Override
    protected String[] getDefaultEnabledRestrictions() { return DEFAULT_ENABLED; }
}

