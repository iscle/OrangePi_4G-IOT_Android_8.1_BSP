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
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.text.TextUtils;

import com.android.settingslib.wifi.AccessPoint;
import com.android.tv.settings.R;
import com.android.tv.settings.form.FormPage;
import com.android.tv.settings.form.FormPageResultListener;

/**
 * Add a wifi network where we already know the ssid/security; normal post-install settings.
 */
public class WifiConnectionActivity extends WifiMultiPagedFormActivity
        implements ConnectToWifiFragment.Listener, TimedMessageWizardFragment.Listener {

    private static final String EXTRA_WIFI_SSID = "wifi_ssid";
    private static final String EXTRA_WIFI_SECURITY_NAME = "wifi_security_name";

    public static Intent createIntent(Context context, AccessPoint result, WifiSecurity security) {
        return new Intent(context, WifiConnectionActivity.class)
                .putExtra(EXTRA_WIFI_SSID, result.getSsidStr())
                .putExtra(EXTRA_WIFI_SECURITY_NAME, security.name());
    }

    public static Intent createIntent(Context context, AccessPoint result) {
        final WifiSecurity security = WifiSecurity.getSecurity(result);
        return createIntent(context, result, security);
    }

    public static Intent createIntent(Context context, WifiConfiguration configuration) {
        final WifiSecurity security = WifiSecurity.getSecurity(configuration);
        final String ssid = configuration.getPrintableSsid();
        return new Intent(context, WifiConnectionActivity.class)
                .putExtra(EXTRA_WIFI_SSID, ssid)
                .putExtra(EXTRA_WIFI_SECURITY_NAME, security.name());
    }

    private AdvancedWifiOptionsFlow mAdvancedWifiOptionsFlow;
    private WifiConfiguration mConfiguration;
    private WifiSecurity mWifiSecurity;
    private FormPage mPasswordPage;
    private FormPage mConnectPage;
    private FormPage mSuccessPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mWifiSecurity = WifiSecurity.valueOf(getIntent().getStringExtra(EXTRA_WIFI_SECURITY_NAME));

        mConfiguration = WifiConfigHelper.getConfiguration(
                this, getIntent().getStringExtra(EXTRA_WIFI_SSID), mWifiSecurity);

        if (WifiConfigHelper.isNetworkSaved(mConfiguration)) {
            addPage(WifiFormPageType.KNOWN_NETWORK);
        } else {
            addStartPage();
        }
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
            case KNOWN_NETWORK:
                if (choiceChosen(formPage, R.string.wifi_connect)) {
                    addStartPage();
                } else if (choiceChosen(formPage, R.string.wifi_forget_network)) {
                    WifiConfigHelper.forgetConfiguration(this, mConfiguration);
                    setResult(RESULT_OK);
                    finish();
                }
                break;
            case ENTER_PASSWORD:
                mPasswordPage = formPage;
                String password = formPage.getDataSummary();
                setWifiConfigurationPassword(mConfiguration, mWifiSecurity, password);
                optionsOrConnect();
                break;
            case CONNECT:
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
                        WifiConfigHelper.forgetConfiguration(this, mConfiguration);
                        addPage(WifiFormPageType.CONNECT_AUTHENTICATION_FAILURE);
                        break;
                    case ConnectToWifiFragment.RESULT_SUCCESS:
                        WifiConfigHelper.saveConfiguration(this, mConfiguration);
                        addPage(WifiFormPageType.SUCCESS);
                        break;
                    default:
                        break;
                }
                break;
            case CONNECT_FAILED:
                // Fall through
            case CONNECT_TIMEOUT:
                mAdvancedWifiOptionsFlow = new AdvancedWifiOptionsFlow(this, this, true, null);
                // Fall through
            case CONNECT_REJECTED_BY_AP:
                if (choiceChosen(formPage, R.string.wifi_action_try_again)) {
                    clear();
                    optionsOrConnect();
                }
                break;
            case CONNECT_AUTHENTICATION_FAILURE:
                if (choiceChosen(formPage, R.string.wifi_action_try_again)) {
                    clear();
                    if (mWifiSecurity.isOpen()) {
                        optionsOrConnect();
                    } else {
                        addPage(WifiFormPageType.ENTER_PASSWORD);
                    }
                }
                break;
            case SUCCESS:
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
    protected void displayPage(FormPage formPage, FormPageResultListener listener,
            boolean forward) {
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
                    getPreviousPage(formPageType), null, formPageType != WifiFormPageType.SUCCESS,
                    formPage, listener, forward, (mAdvancedWifiOptionsFlow != null) &&
                            mAdvancedWifiOptionsFlow.isEmptyTextAllowed(formPageType));
        }
    }

    private FormPage getPreviousPage(WifiFormPageType formPageType) {
        switch (formPageType) {
            case ENTER_PASSWORD:
                return mPasswordPage;
            default:
                return (mAdvancedWifiOptionsFlow != null) ? mAdvancedWifiOptionsFlow
                        .getPreviousPage(formPageType)
                        : null;
        }
    }

    private void addStartPage() {
        /**
         * WEP connections use the wepKeys for authentication.  Other networks use preSharedKey.
         * If the network isn't open or doesn't have its authentication info present, ask for it.
         * Otherwise, go straight to connecting.
         */
        if ((mWifiSecurity == WifiSecurity.WEP && TextUtils.isEmpty(mConfiguration.wepKeys[0]))
                || (!mWifiSecurity.isOpen() && TextUtils.isEmpty(mConfiguration.preSharedKey))) {
            addPage(WifiFormPageType.ENTER_PASSWORD);
        } else {
            connect();
        }
    }

    private void connect() {
        if (!WifiConfigHelper.isNetworkSaved(mConfiguration) &&
            mAdvancedWifiOptionsFlow != null) {
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
