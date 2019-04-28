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

import android.content.Context;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.IpConfiguration.ProxySettings;
import android.net.LinkAddress;
import android.net.NetworkUtils;
import android.net.ProxyInfo;
import android.net.StaticIpConfiguration;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.text.TextUtils;

import com.android.tv.settings.R;
import com.android.tv.settings.form.FormPage;

import java.net.Inet4Address;
import java.net.InetAddress;

/**
 * Handles the form page flow of setting advanced options.
 */
public class AdvancedWifiOptionsFlow {

    public interface PageHandler {
        void addPage(WifiFormPageType formPageType);

        void removePage(FormPage formPage);

        boolean choiceChosen(FormPage formPage, int choiceResourceId);
    }

    public static final int RESULT_UNKNOWN_PAGE = 0;
    public static final int RESULT_PAGE_HANDLED = 1;
    public static final int RESULT_ALL_PAGES_COMPLETE = 2;

    private final Context mContext;
    private final PageHandler mPageHandler;
    private final boolean mAskFirst;
    private final NetworkConfiguration mInitialConfiguration;
    private FormPage mAdvancedOptionsPage;
    private FormPage mProxySettingsPage;
    private FormPage mIpSettingsPage;
    private FormPage mProxyHostnamePage;
    private FormPage mProxyPortPage;
    private FormPage mProxyBypassPage;
    private FormPage mIpAddressPage;
    private FormPage mGatewayPage;
    private FormPage mNetworkPrefixLengthPage;
    private FormPage mDns1Page;
    private FormPage mDns2Page;
    private final IpConfiguration mIpConfiguration;
    private final boolean mSettingsFlow;

    public AdvancedWifiOptionsFlow(Context context, PageHandler pageHandler,
            NetworkConfiguration initialConfiguration) {
        mContext = context;
        mPageHandler = pageHandler;
        mAskFirst = false;
        mSettingsFlow = true;
        mInitialConfiguration = initialConfiguration;
        mIpConfiguration = (initialConfiguration != null) ?
                mInitialConfiguration.getIpConfiguration() :
                new IpConfiguration();
    }

    public AdvancedWifiOptionsFlow(Context context, PageHandler pageHandler, boolean askFirst,
            NetworkConfiguration initialConfiguration) {
        mContext = context;
        mPageHandler = pageHandler;
        mAskFirst = askFirst;
        mSettingsFlow = false;
        mInitialConfiguration = initialConfiguration;
        mIpConfiguration = (initialConfiguration != null) ?
                mInitialConfiguration.getIpConfiguration() :
                new IpConfiguration();
    }

    public WifiFormPageType getInitialPage() {
        return (mAskFirst) ? WifiFormPageType.ADVANCED_OPTIONS : WifiFormPageType.PROXY_SETTINGS;
    }

    public WifiFormPageType getInitialProxySettingsPage() {
        return WifiFormPageType.PROXY_SETTINGS;
    }

   public WifiFormPageType getInitialIpSettingsPage() {
        return WifiFormPageType.IP_SETTINGS;
    }

    /**
     * @param formPageType the type of page that completed.
     * @param formPage the page that complete.
     * @return RESULT_PAGE_HANDLED if the page has been handled.
     *         RESULT_UNKNOWN_PAGE if the page is unrecognized and was ignored.
     *         RESULT_ALL_PAGES_COMPLETE if all pages have been completed.
     */
    public int handlePageComplete(WifiFormPageType formPageType, FormPage formPage) {

        switch (formPageType) {
            case ADVANCED_OPTIONS:
                mAdvancedOptionsPage = formPage;
                if (mPageHandler.choiceChosen(formPage, R.string.wifi_action_advanced_no)) {
                    processProxySettings();
                    processIpSettings();
                    return RESULT_ALL_PAGES_COMPLETE;
                } else {
                    mPageHandler.addPage(WifiFormPageType.PROXY_SETTINGS);
                }
                break;
            case PROXY_SETTINGS:
                mProxySettingsPage = formPage;
                if (mPageHandler.choiceChosen(formPage, R.string.wifi_action_proxy_none)) {
                    processProxySettings();
                    if (mSettingsFlow) {
                        return RESULT_ALL_PAGES_COMPLETE;
                    } else {
                        mPageHandler.addPage(WifiFormPageType.IP_SETTINGS);
                    }
                } else {
                    mPageHandler.addPage(WifiFormPageType.PROXY_HOSTNAME);
                }
                break;
            case PROXY_HOSTNAME:
                mProxyHostnamePage = formPage;
                mPageHandler.addPage(WifiFormPageType.PROXY_PORT);
                break;
            case PROXY_PORT:
                mProxyPortPage = formPage;
                mPageHandler.addPage(WifiFormPageType.PROXY_BYPASS);
                break;
            case PROXY_BYPASS:
                mProxyBypassPage = formPage;
                int proxySettingsResult = processProxySettings();
                if (proxySettingsResult == 0) {
                    if (mSettingsFlow) {
                        return RESULT_ALL_PAGES_COMPLETE;
                    } else {
                        mPageHandler.addPage(WifiFormPageType.IP_SETTINGS);
                    }
                } else {
                    mPageHandler.addPage(WifiFormPageType.PROXY_SETTINGS_INVALID);
                }
                break;
            case PROXY_SETTINGS_INVALID:
                mPageHandler.removePage(mProxySettingsPage);
                mPageHandler.removePage(mProxyHostnamePage);
                mPageHandler.removePage(mProxyPortPage);
                mPageHandler.removePage(mProxyBypassPage);
                mPageHandler.addPage(WifiFormPageType.PROXY_SETTINGS);
                break;
            case IP_SETTINGS:
                mIpSettingsPage = formPage;
                if (mPageHandler.choiceChosen(formPage, R.string.wifi_action_dhcp)) {
                    processIpSettings();
                    return RESULT_ALL_PAGES_COMPLETE;
                } else {
                    mPageHandler.addPage(WifiFormPageType.IP_ADDRESS);
                }
                break;
            case IP_ADDRESS:
                mIpAddressPage = formPage;
                mPageHandler.addPage(WifiFormPageType.GATEWAY);
                break;
            case GATEWAY:
                mGatewayPage = formPage;
                mPageHandler.addPage(WifiFormPageType.NETWORK_PREFIX_LENGTH);
                break;
            case NETWORK_PREFIX_LENGTH:
                mNetworkPrefixLengthPage = formPage;
                mPageHandler.addPage(WifiFormPageType.DNS1);
                break;
            case DNS1:
                mDns1Page = formPage;
                mPageHandler.addPage(WifiFormPageType.DNS2);
                break;
            case DNS2:
                mDns2Page = formPage;
                int ipSettingsResult = processIpSettings();
                if (ipSettingsResult == 0) {
                    return RESULT_ALL_PAGES_COMPLETE;
                } else {
                    mPageHandler.addPage(WifiFormPageType.IP_SETTINGS_INVALID);
                }
                break;
            case IP_SETTINGS_INVALID:
                mPageHandler.removePage(mIpSettingsPage);
                mPageHandler.removePage(mIpAddressPage);
                mPageHandler.removePage(mGatewayPage);
                mPageHandler.removePage(mDns1Page);
                mPageHandler.removePage(mDns2Page);
                mPageHandler.addPage(WifiFormPageType.IP_SETTINGS);
                break;
            default:
                return RESULT_UNKNOWN_PAGE;
        }
        return RESULT_PAGE_HANDLED;
    }

    public FormPage getPreviousPage(WifiFormPageType formPageType) {
        switch (formPageType) {
            case ADVANCED_OPTIONS:
                return mAdvancedOptionsPage;
            case PROXY_SETTINGS:
                if (mProxySettingsPage == null && getInitialProxyInfo() != null) {
                    return createFormPage(R.string.wifi_action_proxy_manual);
                }
                return mProxySettingsPage;
            case PROXY_HOSTNAME:
                if (mProxyHostnamePage == null && getInitialProxyInfo() != null) {
                    return createFormPage(getInitialProxyInfo().getHost());
                }
                return mProxyHostnamePage;
            case PROXY_PORT:
                if (mProxyPortPage == null && getInitialProxyInfo() != null) {
                    return createFormPage(Integer.toString(getInitialProxyInfo().getPort()));
                }
                return mProxyPortPage;
            case PROXY_BYPASS:
                if (mProxyBypassPage == null && getInitialProxyInfo() != null) {
                    return createFormPage(getInitialProxyInfo().getExclusionListAsString());
                }
                return mProxyBypassPage;
            case IP_SETTINGS:
                if (mIpSettingsPage == null && getInitialLinkAddress() != null) {
                    return createFormPage(R.string.wifi_action_static);
                }
                return mIpSettingsPage;
            case IP_ADDRESS:
                if (mIpAddressPage == null && getInitialLinkAddress() != null) {
                    return createFormPage(getInitialLinkAddress().getAddress().getHostAddress());
                }
                return mIpAddressPage;
            case GATEWAY:
                if (mGatewayPage == null && getInitialGateway() != null) {
                    return createFormPage(getInitialGateway().getHostAddress());
                }
                return mGatewayPage;
            case NETWORK_PREFIX_LENGTH:
                if (mNetworkPrefixLengthPage == null && getInitialLinkAddress() != null) {
                    return createFormPage(
                            Integer.toString(getInitialLinkAddress().getNetworkPrefixLength()));
                }
                return mNetworkPrefixLengthPage;
            case DNS1:
                if (mDns1Page == null && getInitialDns(0) != null) {
                    return createFormPage(getInitialDns(0).getHostAddress());
                }
                return mDns1Page;
            case DNS2:
                if (mDns2Page == null && getInitialDns(1) != null) {
                    return createFormPage(getInitialDns(1).getHostAddress());
                }
                return mDns2Page;
            case IP_SETTINGS_INVALID:
            case PROXY_SETTINGS_INVALID:
            default:
                return null;
        }
    }

    public boolean isEmptyTextAllowed(WifiFormPageType formPageType) {
        switch (formPageType) {
            case PROXY_BYPASS:
            case DNS1:
            case DNS2:
            case GATEWAY:
                return true;
            default:
                return false;
        }
    }

    private IpConfiguration getCurrentIpConfiguration() {
        return mIpConfiguration;
    }

    public void updateConfiguration(WifiConfiguration configuration) {
        configuration.setIpConfiguration(mIpConfiguration);
    }

    public void updateConfiguration(NetworkConfiguration configuration) {
        configuration.setIpConfiguration(mIpConfiguration);
    }

    private InetAddress getInitialDns(int index) {
        try {
            return mInitialConfiguration.getIpConfiguration().getStaticIpConfiguration()
                    .dnsServers.get(index);
        } catch (IndexOutOfBoundsException|NullPointerException e) {
            return null;
        }
    }

    private InetAddress getInitialGateway() {
        try {
            return mInitialConfiguration.getIpConfiguration().getStaticIpConfiguration().gateway;
        } catch (NullPointerException e) {
            return null;
        }
    }

    private LinkAddress getInitialLinkAddress() {
        try {
            return mInitialConfiguration.getIpConfiguration().getStaticIpConfiguration().ipAddress;
        } catch (NullPointerException e) {
            return null;
        }
    }

    private ProxyInfo getInitialProxyInfo() {
        try {
            return mInitialConfiguration.getIpConfiguration().getHttpProxy();
        } catch (NullPointerException e) {
            return null;
        }
    }

    private FormPage createFormPage(int resultStringResourceId) {
        return createFormPage(mContext.getString(resultStringResourceId));
    }

    private FormPage createFormPage(String resultString) {
        Bundle result = new Bundle();
        result.putString(FormPage.DATA_KEY_SUMMARY_STRING, resultString);
        FormPage formPage = FormPage.createTextInputForm(resultString);
        formPage.complete(result);
        return formPage;
    }

    private int processProxySettings() {
        boolean hasProxySettings = (mAdvancedOptionsPage == null || !mPageHandler.choiceChosen(
                mAdvancedOptionsPage, R.string.wifi_action_advanced_no))
                && !mPageHandler.choiceChosen(mProxySettingsPage, R.string.wifi_action_proxy_none);
        mIpConfiguration.setProxySettings(hasProxySettings ?
                                          ProxySettings.STATIC : ProxySettings.NONE);
        if (hasProxySettings) {
            String host = mProxyHostnamePage.getDataSummary();
            String portStr = mProxyPortPage.getDataSummary();
            String exclusionList = mProxyBypassPage.getDataSummary();
            int port = 0;
            int result = 0;
            try {
                port = Integer.parseInt(portStr);
                result = WifiConfigHelper.validate(host, portStr, exclusionList);
            } catch (NumberFormatException e) {
                result = R.string.proxy_error_invalid_port;
            }
            if (result == 0) {
                mIpConfiguration.setHttpProxy(new ProxyInfo(host, port, exclusionList));
            } else {
                return result;
            }
        } else {
            mIpConfiguration.setHttpProxy(null);
        }

        return 0;
    }

    private int processIpSettings() {
        boolean hasIpSettings = (mAdvancedOptionsPage == null || !mPageHandler.choiceChosen(
                mAdvancedOptionsPage, R.string.wifi_action_advanced_no))
                && !mPageHandler.choiceChosen(mIpSettingsPage, R.string.wifi_action_dhcp);
        mIpConfiguration.setIpAssignment(hasIpSettings ? IpAssignment.STATIC : IpAssignment.DHCP);

        if (hasIpSettings) {
            StaticIpConfiguration staticConfig = new StaticIpConfiguration();
            mIpConfiguration.setStaticIpConfiguration(staticConfig);

            String ipAddr = mIpAddressPage.getDataSummary();
            if (TextUtils.isEmpty(ipAddr))
                return R.string.wifi_ip_settings_invalid_ip_address;

            Inet4Address inetAddr = null;
            try {
                inetAddr = (Inet4Address) NetworkUtils.numericToInetAddress(ipAddr);
            } catch (IllegalArgumentException|ClassCastException e) {
                return R.string.wifi_ip_settings_invalid_ip_address;
            }

            int networkPrefixLength = -1;
            try {
                networkPrefixLength = Integer.parseInt(mNetworkPrefixLengthPage.getDataSummary());
                if (networkPrefixLength < 0 || networkPrefixLength > 32) {
                    return R.string.wifi_ip_settings_invalid_network_prefix_length;
                }
                staticConfig.ipAddress = new LinkAddress(inetAddr, networkPrefixLength);
            } catch (NumberFormatException e) {
                return R.string.wifi_ip_settings_invalid_ip_address;
            }

            String gateway = mGatewayPage.getDataSummary();
            if (!TextUtils.isEmpty(gateway)) {
                try {
                    staticConfig.gateway =
                            (Inet4Address) NetworkUtils.numericToInetAddress(gateway);
                } catch (IllegalArgumentException|ClassCastException e) {
                    return R.string.wifi_ip_settings_invalid_gateway;
                }
            }

            String dns1 = mDns1Page.getDataSummary();
            if (!TextUtils.isEmpty(dns1)) {
                try {
                    staticConfig.dnsServers.add(
                            (Inet4Address) NetworkUtils.numericToInetAddress(dns1));
                } catch (IllegalArgumentException|ClassCastException e) {
                    return R.string.wifi_ip_settings_invalid_dns;
                }
            }

            String dns2 = mDns2Page.getDataSummary();
            if (!TextUtils.isEmpty(dns2)) {
                try {
                    staticConfig.dnsServers.add(
                            (Inet4Address) NetworkUtils.numericToInetAddress(dns2));
                } catch (IllegalArgumentException|ClassCastException e) {
                    return R.string.wifi_ip_settings_invalid_dns;
                }
            }
        } else {
            mIpConfiguration.setStaticIpConfiguration(null);
        }
        return 0;
    }
}
