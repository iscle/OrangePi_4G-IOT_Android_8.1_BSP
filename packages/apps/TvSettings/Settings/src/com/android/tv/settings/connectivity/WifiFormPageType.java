/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.connectivity;

import com.android.tv.settings.R;
import com.android.tv.settings.connectivity.setup.SelectFromListWizardFragment;
import com.android.tv.settings.connectivity.setup.TextInputWizardFragment;
import com.android.tv.settings.form.FormPage;

import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;

/**
 * Wifi form pages.
 */
public enum WifiFormPageType implements FormPageDisplayer.FormPageInfo {
    CHOOSE_NETWORK(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE, R.string.title_select_wifi_network,
            0, new int[] { R.string.other_network },
            new int[] { R.drawable.ic_wifi_add}),
    ENTER_SSID(FormPageDisplayer.DISPLAY_TYPE_TEXT_INPUT, R.string.title_ssid, 0,
            TextInputWizardFragment.INPUT_TYPE_NO_SUGGESTIONS),
    CHOOSE_SECURITY(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE, R.string.security_type, 0,
            new int[] { R.string.wifi_security_type_none, R.string.wifi_security_type_wep,
            R.string.wifi_security_type_wpa, R.string.wifi_security_type_eap }),
    ENTER_PASSWORD(FormPageDisplayer.DISPLAY_TYPE_PSK_INPUT,
            R.string.wifi_setup_input_password, 0, 0),
    CONNECT(FormPageDisplayer.DISPLAY_TYPE_LOADING, R.string.wifi_connecting, 0),
    CONNECT_FAILED(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE,
            R.string.title_wifi_could_not_connect, 0, new int[] { R.string.wifi_action_try_again,
            R.string.wifi_action_view_available_networks }),
    CONNECT_TIMEOUT(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE,
            R.string.title_wifi_could_not_connect_timeout, 0, new int[] {
            R.string.wifi_action_try_again, R.string.wifi_action_view_available_networks }),
    CONNECT_AUTHENTICATION_FAILURE(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE,
            R.string.title_wifi_could_not_connect_authentication_failure, 0, new int[] {
            R.string.wifi_action_try_again, R.string.wifi_action_view_available_networks }),
    CONNECT_REJECTED_BY_AP(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE,
            R.string.title_wifi_could_not_connect_ap_reject, 0, new int[] {
            R.string.wifi_action_try_again, R.string.wifi_action_view_available_networks }),
    SAVE(FormPageDisplayer.DISPLAY_TYPE_LOADING, R.string.wifi_saving, 0),
    SAVE_FAILED(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE,
            R.string.title_wifi_could_not_save, 0, new int[] { R.string.wifi_action_ok }),
    SAVE_SUCCESS(FormPageDisplayer.DISPLAY_TYPE_LOADING, R.string.wifi_setup_save_success, 0),
    SUCCESS(FormPageDisplayer.DISPLAY_TYPE_LOADING, R.string.wifi_setup_connection_success, 0),
    SUMMARY_CONNECTED_WIFI(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE,
            R.string.wifi_summary_title_connected,
            R.string.wifi_summary_description_connected_to_wifi_network, new int[] {
            R.string.wifi_action_dont_change_network, R.string.wifi_action_change_network }),
    SUMMARY_CONNECTED_NON_WIFI(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE,
            R.string.wifi_summary_title_connected, 0, new int[] { R.string.wifi_action_ok }),
    SUMMARY_NOT_CONNECTED(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE,
            R.string.wifi_summary_title_not_connected, 0, new int[] { R.string.wifi_action_ok }),
    ADVANCED_OPTIONS(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE,
            R.string.title_wifi_advanced_options, 0, new int[] { R.string.wifi_action_advanced_no,
            R.string.wifi_action_advanced_yes }),
    PROXY_SETTINGS(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE, R.string.title_wifi_proxy_settings,
            R.string.proxy_warning_limited_support, new int[] { R.string.wifi_action_proxy_none,
            R.string.wifi_action_proxy_manual }),
    PROXY_HOSTNAME(FormPageDisplayer.DISPLAY_TYPE_TEXT_INPUT, R.string.title_wifi_proxy_hostname,
            R.string.proxy_hostname_description, TextInputWizardFragment.INPUT_TYPE_NO_SUGGESTIONS,
            R.string.proxy_hostname_hint),
    PROXY_PORT(FormPageDisplayer.DISPLAY_TYPE_TEXT_INPUT, R.string.title_wifi_proxy_port,
            R.string.proxy_port_description, TextInputWizardFragment.INPUT_TYPE_NUMERIC,
            R.string.proxy_port_hint),
    PROXY_BYPASS(FormPageDisplayer.DISPLAY_TYPE_TEXT_INPUT, R.string.title_wifi_proxy_bypass,
            R.string.proxy_exclusionlist_description,
            TextInputWizardFragment.INPUT_TYPE_NO_SUGGESTIONS,
            R.string.proxy_exclusionlist_hint),
    IP_SETTINGS(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE, R.string.title_wifi_ip_settings, 0,
            new int[] { R.string.wifi_action_dhcp, R.string.wifi_action_static }),
    IP_ADDRESS(FormPageDisplayer.DISPLAY_TYPE_TEXT_INPUT, R.string.title_wifi_ip_address,
            R.string.wifi_ip_address_description, TextInputWizardFragment.INPUT_TYPE_NO_SUGGESTIONS,
            R.string.wifi_ip_address_hint),
    GATEWAY(FormPageDisplayer.DISPLAY_TYPE_TEXT_INPUT, R.string.title_wifi_gateway,
            R.string.wifi_gateway_description, TextInputWizardFragment.INPUT_TYPE_NO_SUGGESTIONS,
            R.string.wifi_gateway_hint),
    NETWORK_PREFIX_LENGTH(FormPageDisplayer.DISPLAY_TYPE_TEXT_INPUT,
            R.string.title_wifi_network_prefix_length,
            R.string.wifi_network_prefix_length_description,
            TextInputWizardFragment.INPUT_TYPE_NO_SUGGESTIONS,
            R.string.wifi_network_prefix_length_hint),
    DNS1(FormPageDisplayer.DISPLAY_TYPE_TEXT_INPUT, R.string.title_wifi_dns1,
            R.string.wifi_dns1_description, TextInputWizardFragment.INPUT_TYPE_NO_SUGGESTIONS,
            R.string.wifi_dns1_hint),
    DNS2(FormPageDisplayer.DISPLAY_TYPE_TEXT_INPUT, R.string.title_wifi_dns2,
            R.string.wifi_dns2_description, TextInputWizardFragment.INPUT_TYPE_NO_SUGGESTIONS,
            R.string.wifi_dns2_hint),
    PROXY_SETTINGS_INVALID(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE,
            R.string.title_wifi_proxy_settings_invalid, 0, new int[] {
            R.string.wifi_action_try_again }),
    IP_SETTINGS_INVALID(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE,
            R.string.title_wifi_ip_settings_invalid, 0, new int[] {
            R.string.wifi_action_try_again }),
    KNOWN_NETWORK(FormPageDisplayer.DISPLAY_TYPE_LIST_CHOICE, R.string.title_wifi_known_network, 0,
            new int[] { R.string.wifi_connect, R.string.wifi_forget_network }),
    WPS(FormPageDisplayer.DISPLAY_TYPE_LOADING, 0, 0);

    private final int mDisplayType;
    private final int mTitleResource;
    private final int mDescriptionResource;
    private final int mInputType;
    private final int[] mDefaultListItemTitles;
    private final int[] mDefaultListItemIcons;
    private final int mDefaultPrefillResource;

    WifiFormPageType(int displayType, int titleResource, int descriptionResource) {
        this(displayType, titleResource, descriptionResource,
                TextInputWizardFragment.INPUT_TYPE_NORMAL);
    }

    WifiFormPageType(int displayType, int titleResource, int descriptionResource,
            int textType) {
        this(displayType, titleResource, descriptionResource, textType, 0);
    }

    WifiFormPageType(int displayType, int titleResource, int descriptionResource,
            int textType, int defaultPrefillResource) {
        mDisplayType = displayType;
        mTitleResource = titleResource;
        mDescriptionResource = descriptionResource;
        mInputType = textType;
        mDefaultListItemTitles = null;
        mDefaultListItemIcons = null;
        mDefaultPrefillResource = defaultPrefillResource;
    }

    WifiFormPageType(int displayType, int titleResource, int descriptionResource,
            int[] defaultListItemTitles) {
        this(displayType, titleResource, descriptionResource, defaultListItemTitles, null);
    }

    WifiFormPageType(int displayType, int titleResource, int descriptionResource,
            int[] defaultListItemTitles, int[] defaultListItemIcons) {
        mDisplayType = displayType;
        mTitleResource = titleResource;
        mDescriptionResource = descriptionResource;
        mInputType = TextInputWizardFragment.INPUT_TYPE_NORMAL;
        mDefaultListItemTitles = defaultListItemTitles;
        mDefaultListItemIcons = defaultListItemIcons;
        if (mDefaultListItemTitles != null && mDefaultListItemIcons != null
                && mDefaultListItemTitles.length != mDefaultListItemIcons.length) {
            throw new IllegalArgumentException("Form page type " + name()
                    + " had title and icon arrays that we'ren't the same length! "
                    + "The title array had length " + mDefaultListItemTitles.length
                    + " but the icon array had length " + mDefaultListItemIcons.length + "!");
        }
        mDefaultPrefillResource = 0;
    }

    @Override
    public int getTitleResourceId() {
        return mTitleResource;
    }

    @Override
    public int getDescriptionResourceId() {
        return mDescriptionResource;
    }

    @Override
    public int getInputType() {
        return mInputType;
    }

    @Override
    public int getDisplayType() {
        return mDisplayType;
    }

    @Override
    public int getDefaultPrefillResourceId() {
        return mDefaultPrefillResource;
    }


    @Override
    public ArrayList<SelectFromListWizardFragment.ListItem> getChoices(
            Context context, ArrayList<SelectFromListWizardFragment.ListItem> extraChoices) {
        ArrayList<SelectFromListWizardFragment.ListItem> choices = new ArrayList<>();
        if (extraChoices != null) {
            choices.addAll(extraChoices);
        }

        if (mDefaultListItemTitles != null) {
            // Find the largest priority of the items placed at the end of the list and place
            // default items after.
            int largestLastPriority = Integer.MIN_VALUE;
            if (extraChoices != null) {
                for (SelectFromListWizardFragment.ListItem item : extraChoices) {
                    if (item.getPinnedPosition()
                            == SelectFromListWizardFragment.PinnedListItem.LAST) {
                        SelectFromListWizardFragment.PinnedListItem pinnedItem =
                                (SelectFromListWizardFragment.PinnedListItem) item;
                        largestLastPriority = java.lang.Math.max(
                                largestLastPriority, pinnedItem.getPinnedPriority());
                    }
                }
            }

            for (int i = 0; i < mDefaultListItemTitles.length; i++) {
                choices.add(new SelectFromListWizardFragment.PinnedListItem(
                        context.getString(mDefaultListItemTitles[i]),
                        mDefaultListItemIcons == null ? 0 : mDefaultListItemIcons[i],
                        SelectFromListWizardFragment.PinnedListItem.LAST, i + largestLastPriority));
            }
        }
        return choices;
    }

    public FormPage create() {
        return FormPage.createTextInputForm(name());
    }

    public FormPage create(Intent intent) {
        if (mDisplayType != FormPageDisplayer.DISPLAY_TYPE_LOADING) {
            throw new IllegalArgumentException("Form page type " + name() + " had display type "
                    + mDisplayType + " but " + FormPageDisplayer.DISPLAY_TYPE_LOADING
                    + " expected!");
        }
        return FormPage.createIntentForm(name(), intent);
    }
}
