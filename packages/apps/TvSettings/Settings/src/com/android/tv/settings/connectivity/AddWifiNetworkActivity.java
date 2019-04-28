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

import android.app.Fragment;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;

import com.android.tv.settings.R;
import com.android.tv.settings.form.FormPage;
import com.android.tv.settings.form.FormPageResultListener;

/**
 * Manual-style add wifi network (the kind you'd use for adding a hidden or out-of-range network.)
 */
public class AddWifiNetworkActivity extends WifiMultiPagedFormActivity
        implements ConnectToWifiFragment.Listener, TimedMessageWizardFragment.Listener {

    private AdvancedWifiOptionsFlow mAdvancedWifiOptionsFlow;
    private WifiConfiguration mConfiguration;
    private WifiSecurity mWifiSecurity;
    private FormPage mSsidPage;
    private FormPage mSecurityPage;
    private FormPage mPasswordPage;
    private FormPage mConnectPage;
    private FormPage mSuccessPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mConfiguration = new WifiConfiguration();
        mConfiguration.hiddenSSID = true;
        addPage(WifiFormPageType.ENTER_SSID);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onConnectToWifiCompleted(int reason) {
        Bundle result = new Bundle();
        result.putString(FormPage.DATA_KEY_SUMMARY_STRING, Integer.toString(reason));
        onBundlePageResult(mConnectPage, result);
    }

    @Override
    public void onTimedMessageCompleted() {
        Bundle result = new Bundle();
        result.putString(FormPage.DATA_KEY_SUMMARY_STRING, "");
        onBundlePageResult(mSuccessPage, result);
    }

    @Override
    protected boolean onPageComplete(WifiFormPageType formPageType, FormPage formPage) {

        switch (formPageType) {
            case ENTER_SSID:
                mSsidPage = formPage;
                String ssid = formPage.getDataSummary();
                WifiConfigHelper.setConfigSsid(mConfiguration, ssid);
                addPage(WifiFormPageType.CHOOSE_SECURITY);
                break;
            case CHOOSE_SECURITY:
                mSecurityPage = formPage;
                if (choiceChosen(formPage, R.string.wifi_security_type_none)) {
                    mWifiSecurity = WifiSecurity.NONE;
                } else if (choiceChosen(formPage, R.string.wifi_security_type_wep)) {
                    mWifiSecurity = WifiSecurity.WEP;
                } else if (choiceChosen(formPage, R.string.wifi_security_type_wpa)) {
                    mWifiSecurity = WifiSecurity.PSK;
                } else if (choiceChosen(formPage, R.string.wifi_security_type_eap)) {
                    mWifiSecurity = WifiSecurity.EAP;
                }
                WifiConfigHelper.setConfigKeyManagementBySecurity(mConfiguration, mWifiSecurity);
                if (mWifiSecurity == WifiSecurity.NONE) {
                    optionsOrConnect();
                } else {
                    addPage(WifiFormPageType.ENTER_PASSWORD);
                }
                break;
            case ENTER_PASSWORD:
                mPasswordPage = formPage;
                String password = formPage.getDataSummary();
                setWifiConfigurationPassword(mConfiguration, mWifiSecurity, password);
                optionsOrConnect();
                break;
            case CONNECT:
                WifiConfigHelper.saveConfiguration(this, mConfiguration);
                switch (Integer.valueOf(formPage.getDataSummary())) {
                    case ConnectToWifiFragment.RESULT_REJECTED_BY_AP:
                        addPage(WifiFormPageType.CONNECT_REJECTED_BY_AP);
                        break;
                    case ConnectToWifiFragment.RESULT_UNKNOWN_ERROR:
                        addPage(WifiFormPageType.CONNECT_FAILED);
                        break;
                    case ConnectToWifiFragment.RESULT_TIMEOUT:
                        addPage(WifiFormPageType.CONNECT_TIMEOUT);
                        break;
                    case ConnectToWifiFragment.RESULT_BAD_AUTHENTICATION:
                        addPage(WifiFormPageType.CONNECT_AUTHENTICATION_FAILURE);
                        break;
                    case ConnectToWifiFragment.RESULT_SUCCESS:
                        addPage(WifiFormPageType.SUCCESS);
                        break;
                    default:
                        break;
                }
                break;
            case CONNECT_FAILED:
                // fall through
            case CONNECT_TIMEOUT:
                mAdvancedWifiOptionsFlow = new AdvancedWifiOptionsFlow(this, this, true, null);
                // fall through
            case CONNECT_REJECTED_BY_AP:
                if (choiceChosen(formPage, R.string.wifi_action_try_again)) {
                    optionsOrConnect();
                }
                break;
            case CONNECT_AUTHENTICATION_FAILURE:
                if (choiceChosen(formPage, R.string.wifi_action_try_again)) {
                    if (mAdvancedWifiOptionsFlow != null) {
                        addPage(mAdvancedWifiOptionsFlow.getInitialPage());
                    } else {
                        addPage(WifiFormPageType.ENTER_SSID);
                    }
                }
                break;
            default:
                if (mAdvancedWifiOptionsFlow != null) {
                    switch (mAdvancedWifiOptionsFlow.handlePageComplete(formPageType, formPage)) {
                        case AdvancedWifiOptionsFlow.RESULT_ALL_PAGES_COMPLETE:
                            connect();
                            break;
                        case AdvancedWifiOptionsFlow.RESULT_UNKNOWN_PAGE:
                        case AdvancedWifiOptionsFlow.RESULT_PAGE_HANDLED:
                        default:
                            break;
                    }
                }
                break;
        }
        return true;
    }

    @Override
    protected void displayPage(FormPage formPage, FormPageResultListener listener, boolean forward) {
        WifiFormPageType formPageType = getFormPageType(formPage);

        if (formPageType == WifiFormPageType.CONNECT) {
            mConnectPage = formPage;
            Fragment fragment = ConnectToWifiFragment.newInstance(
                    getString(formPageType.getTitleResourceId(), mConfiguration.getPrintableSsid()),
                    true, mConfiguration);
            displayFragment(fragment, forward);
        } else if (formPageType == WifiFormPageType.SUCCESS) {
            mSuccessPage = formPage;
            Fragment fragment = TimedMessageWizardFragment.newInstance(
                    getString(formPageType.getTitleResourceId()));
            displayFragment(fragment, forward);
        } else {
            displayPage(formPageType, mConfiguration.getPrintableSsid(), null, null,
                    getLastPage(formPageType), null, formPageType != WifiFormPageType.SUCCESS,
                    formPage, listener, forward, (mAdvancedWifiOptionsFlow != null) ?
                            mAdvancedWifiOptionsFlow.isEmptyTextAllowed(formPageType) : false);
        }
    }

    private FormPage getLastPage(WifiFormPageType formPageType) {
        switch (formPageType) {
            case CHOOSE_SECURITY:
                return mSecurityPage;
            case ENTER_PASSWORD:
                return mPasswordPage;
            case ENTER_SSID:
                return mSsidPage;
            default:
                return (mAdvancedWifiOptionsFlow != null) ? mAdvancedWifiOptionsFlow
                        .getPreviousPage(formPageType)
                        : null;
        }
    }

    private void connect() {
        if (mAdvancedWifiOptionsFlow != null) {
            mAdvancedWifiOptionsFlow.updateConfiguration(mConfiguration);
        }
        addPage(WifiFormPageType.CONNECT);
    }

    private void optionsOrConnect() {
        if (mAdvancedWifiOptionsFlow != null) {
            addPage(mAdvancedWifiOptionsFlow.getInitialPage());
        } else {
            connect();
        }
    }
}
