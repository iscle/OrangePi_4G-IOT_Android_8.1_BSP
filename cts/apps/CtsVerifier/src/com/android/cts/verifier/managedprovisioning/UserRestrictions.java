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

package com.android.cts.verifier.managedprovisioning;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.provider.Settings;
import android.util.ArrayMap;

import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UserRestrictions {
    private static final String[] RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY = new String[] {
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_ADJUST_VOLUME,
        UserManager.DISALLOW_APPS_CONTROL,
        UserManager.DISALLOW_CONFIG_CELL_BROADCASTS,
        UserManager.DISALLOW_CONFIG_CREDENTIALS,
        UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
        UserManager.DISALLOW_CONFIG_TETHERING,
        UserManager.DISALLOW_CONFIG_WIFI,
        UserManager.DISALLOW_DEBUGGING_FEATURES,
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_FUN,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        UserManager.DISALLOW_MODIFY_ACCOUNTS,
        UserManager.DISALLOW_NETWORK_RESET,
        UserManager.DISALLOW_OUTGOING_BEAM,
        UserManager.DISALLOW_REMOVE_MANAGED_PROFILE,
        UserManager.DISALLOW_REMOVE_USER,
        UserManager.DISALLOW_SHARE_LOCATION,
        UserManager.DISALLOW_UNINSTALL_APPS
    };

    private static final ArrayMap<String, UserRestrictionItem> USER_RESTRICTION_ITEMS;
    static {
        final int[] restrictionLabels = new int[] {
            R.string.disallow_add_user,
            R.string.disallow_adjust_volume,
            R.string.disallow_apps_control,
            R.string.disallow_config_cell_broadcasts,
            R.string.disallow_config_credentials,
            R.string.disallow_config_mobile_networks,
            R.string.disallow_config_tethering,
            R.string.disallow_config_wifi,
            R.string.disallow_debugging_features,
            R.string.disallow_factory_reset,
            R.string.disallow_fun,
            R.string.disallow_install_unknown_sources,
            R.string.disallow_modify_accounts,
            R.string.disallow_network_reset,
            R.string.disallow_outgoing_beam,
            R.string.disallow_remove_managed_profile,
            R.string.disallow_remove_user,
            R.string.disallow_share_location,
            R.string.disallow_uninstall_apps
        };

        final int[] restrictionActions = new int[] {
            R.string.disallow_add_user_action,
            R.string.disallow_adjust_volume_action,
            R.string.disallow_apps_control_action,
            R.string.disallow_config_cell_broadcasts_action,
            R.string.disallow_config_credentials_action,
            R.string.disallow_config_mobile_networks_action,
            R.string.disallow_config_tethering_action,
            R.string.disallow_config_wifi_action,
            R.string.disallow_debugging_features_action,
            R.string.disallow_factory_reset_action,
            R.string.disallow_fun_action,
            R.string.disallow_install_unknown_sources_action,
            R.string.disallow_modify_accounts_action,
            R.string.disallow_network_reset_action,
            R.string.disallow_outgoing_beam_action,
            R.string.disallow_remove_managed_profile_action,
            R.string.disallow_remove_user_action,
            R.string.disallow_share_location_action,
            R.string.disallow_uninstall_apps_action
        };

        final String[] settingsIntentActions = new String[] {
            Settings.ACTION_SETTINGS,
            Settings.ACTION_SOUND_SETTINGS,
            Settings.ACTION_APPLICATION_SETTINGS,
            Settings.ACTION_SOUND_SETTINGS,
            Settings.ACTION_SECURITY_SETTINGS,
            Settings.ACTION_WIRELESS_SETTINGS,
            Settings.ACTION_WIRELESS_SETTINGS,
            Settings.ACTION_WIFI_SETTINGS,
            Settings.ACTION_DEVICE_INFO_SETTINGS,
            Settings.ACTION_SETTINGS,
            Settings.ACTION_DEVICE_INFO_SETTINGS,
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Settings.ACTION_SYNC_SETTINGS,
            Settings.ACTION_SETTINGS,
            Settings.ACTION_NFC_SETTINGS,
            Settings.ACTION_SETTINGS,
            Settings.ACTION_SETTINGS,
            Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            Settings.ACTION_APPLICATION_SETTINGS,
        };

        if (RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY.length != restrictionLabels.length
                || RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY.length != restrictionActions.length
                || RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY.length != settingsIntentActions.length) {
            throw new AssertionError("Number of items in restrictionIds, restrictionLabels, "
                    + "restrictionActions, and settingsIntentActions do not match");
        }
        USER_RESTRICTION_ITEMS = new ArrayMap<>(RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY.length);
        for (int i = 0; i < RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY.length; ++i) {
            USER_RESTRICTION_ITEMS.put(RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY[i],
                    new UserRestrictionItem(
                            restrictionLabels[i],
                            restrictionActions[i],
                            settingsIntentActions[i]));
        }
    }

    private static final ArrayList<String> ALSO_VALID_FOR_PO_POLICY_TRANSPARENCY =
            new ArrayList<String>();
    static {
        ALSO_VALID_FOR_PO_POLICY_TRANSPARENCY.add(UserManager.DISALLOW_APPS_CONTROL);
        ALSO_VALID_FOR_PO_POLICY_TRANSPARENCY.add(UserManager.DISALLOW_UNINSTALL_APPS);
        ALSO_VALID_FOR_PO_POLICY_TRANSPARENCY.add(UserManager.DISALLOW_MODIFY_ACCOUNTS);
        ALSO_VALID_FOR_PO_POLICY_TRANSPARENCY.add(UserManager.DISALLOW_SHARE_LOCATION);
    }

    public static String getRestrictionLabel(Context context, String restriction) {
        final UserRestrictionItem item = findRestrictionItem(restriction);
        return context.getString(item.label);
    }

    public static String getUserAction(Context context, String restriction) {
        final UserRestrictionItem item = findRestrictionItem(restriction);
        return context.getString(item.userAction);
    }

    private static UserRestrictionItem findRestrictionItem(String restriction) {
        final UserRestrictionItem item = USER_RESTRICTION_ITEMS.get(restriction);
        if (item == null) {
            throw new IllegalArgumentException("Unknown restriction: " + restriction);
        }
        return item;
    }

    public static List<String> getUserRestrictionsForPolicyTransparency(int mode) {
        if (mode == PolicyTransparencyTestListActivity.MODE_DEVICE_OWNER) {
            ArrayList<String> result = new ArrayList<String>();
            // They are all valid except for DISALLOW_REMOVE_MANAGED_PROFILE
            for (String st : RESTRICTION_IDS_FOR_POLICY_TRANSPARENCY) {
                if (!st.equals(UserManager.DISALLOW_REMOVE_MANAGED_PROFILE)) {
                    result.add(st);
                }
            }
            return result;
        } else if (mode == PolicyTransparencyTestListActivity.MODE_COMP) {
            return Arrays.asList(UserManager.DISALLOW_REMOVE_MANAGED_PROFILE);
        } else if (mode == PolicyTransparencyTestListActivity.MODE_PROFILE_OWNER) {
            return ALSO_VALID_FOR_PO_POLICY_TRANSPARENCY;
        }
        throw new RuntimeException("Invalid mode " + mode);
    }

    public static Intent getUserRestrictionTestIntent(Context context, String restriction) {
        final UserRestrictionItem item = USER_RESTRICTION_ITEMS.get(restriction);
        return new Intent(PolicyTransparencyTestActivity.ACTION_SHOW_POLICY_TRANSPARENCY_TEST)
                .putExtra(PolicyTransparencyTestActivity.EXTRA_TEST,
                        PolicyTransparencyTestActivity.TEST_CHECK_USER_RESTRICTION)
                .putExtra(CommandReceiverActivity.EXTRA_USER_RESTRICTION, restriction)
                .putExtra(PolicyTransparencyTestActivity.EXTRA_TITLE, context.getString(item.label))
                .putExtra(PolicyTransparencyTestActivity.EXTRA_SETTINGS_INTENT_ACTION,
                        item.intentAction);
    }

    public static boolean isRestrictionValid(Context context, String restriction) {
        final PackageManager pm = context.getPackageManager();
        switch (restriction) {
            case UserManager.DISALLOW_ADD_USER:
            case UserManager.DISALLOW_REMOVE_USER:
                return UserManager.supportsMultipleUsers();
            case UserManager.DISALLOW_ADJUST_VOLUME:
                return pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
            case UserManager.DISALLOW_CONFIG_CELL_BROADCASTS:
                // Get com.android.internal.R.bool.config_cellBroadcastAppLinks
                final int resId = context.getResources().getIdentifier(
                        "config_cellBroadcastAppLinks", "bool", "android");
                boolean isCellBroadcastAppLinkEnabled = context.getResources().getBoolean(resId);
                try {
                    if (isCellBroadcastAppLinkEnabled) {
                        if (pm.getApplicationEnabledSetting("com.android.cellbroadcastreceiver")
                                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                            isCellBroadcastAppLinkEnabled = false;  // CMAS app disabled
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    isCellBroadcastAppLinkEnabled = false;  // CMAS app not installed
                }
                return isCellBroadcastAppLinkEnabled;
            case UserManager.DISALLOW_FUN:
                // Easter egg is not available on watch
                return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
            case UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS:
                return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
            case UserManager.DISALLOW_CONFIG_WIFI:
                return pm.hasSystemFeature(PackageManager.FEATURE_WIFI);
            case UserManager.DISALLOW_NETWORK_RESET:
                // This test should not run on watch
                return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
            case UserManager.DISALLOW_OUTGOING_BEAM:
                return pm.hasSystemFeature(PackageManager.FEATURE_NFC);
            case UserManager.DISALLOW_SHARE_LOCATION:
                return pm.hasSystemFeature(PackageManager.FEATURE_LOCATION);
            case UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES:
                return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
            case UserManager.DISALLOW_CONFIG_CREDENTIALS:
                return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
            default:
                return true;
        }
    }

    private static class UserRestrictionItem {
        final int label;
        final int userAction;
        final String intentAction;
        public UserRestrictionItem(int label, int userAction, String intentAction) {
            this.label = label;
            this.userAction = userAction;
            this.intentAction = intentAction;
        }
    }
}
