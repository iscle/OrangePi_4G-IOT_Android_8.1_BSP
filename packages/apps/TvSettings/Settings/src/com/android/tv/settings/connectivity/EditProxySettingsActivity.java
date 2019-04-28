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
import android.util.Log;

import com.android.tv.settings.form.FormPage;
import com.android.tv.settings.form.FormPageResultListener;

/**
 * Allows the modification of advanced Wi-Fi settings
 */
public class EditProxySettingsActivity extends WifiMultiPagedFormActivity
        implements SaveWifiConfigurationFragment.Listener, TimedMessageWizardFragment.Listener {

    private static final String TAG = "EditProxySettings";

    public static final int NETWORK_ID_ETHERNET = WifiConfiguration.INVALID_NETWORK_ID;
    private static final String EXTRA_NETWORK_ID = "network_id";

    public static Intent createIntent(Context context, int networkId) {
        return new Intent(context, EditProxySettingsActivity.class)
                .putExtra(EXTRA_NETWORK_ID, networkId);
    }

    private NetworkConfiguration mConfiguration;
    private AdvancedWifiOptionsFlow mAdvancedWifiOptionsFlow;
    private FormPage mSavePage;
    private FormPage mSuccessPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int networkId = getIntent().getIntExtra(EXTRA_NETWORK_ID, -1);
        if (networkId == NETWORK_ID_ETHERNET) {
            mConfiguration = NetworkConfigurationFactory.createNetworkConfiguration(this,
                    NetworkConfigurationFactory.TYPE_ETHERNET);
            ((EthernetConfig) mConfiguration).load();
        } else {
            mConfiguration = NetworkConfigurationFactory.createNetworkConfiguration(this,
                    NetworkConfigurationFactory.TYPE_WIFI);
            ((WifiConfig) mConfiguration).load(networkId);
        }
        if (mConfiguration != null) {
            mAdvancedWifiOptionsFlow = new AdvancedWifiOptionsFlow(this, this, mConfiguration);
            addPage(mAdvancedWifiOptionsFlow.getInitialProxySettingsPage());
        } else {
            Log.e(TAG, "Could not find existing configuration for network id: " + networkId);
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onSaveWifiConfigurationCompleted(int reason) {
        Bundle result = new Bundle();
        result.putString(FormPage.DATA_KEY_SUMMARY_STRING, Integer.toString(reason));
        onBundlePageResult(mSavePage, result);
    }

    @Override
    public void onTimedMessageCompleted() {
        Bundle result = new Bundle();
        result.putString(FormPage.DATA_KEY_SUMMARY_STRING, "");
        onBundlePageResult(mSuccessPage, result);
    }

    @Override
    protected boolean onPageComplete(WifiFormPageType formPageType, FormPage formPage) {

        switch(formPageType) {
            case SAVE:
                switch (Integer.valueOf(formPage.getDataSummary())) {
                    case SaveWifiConfigurationFragment.RESULT_FAILURE:
                        addPage(WifiFormPageType.SAVE_FAILED);
                        break;
                    case SaveWifiConfigurationFragment.RESULT_SUCCESS:
                        addPage(WifiFormPageType.SAVE_SUCCESS);
                        break;
                    default:
                        break;
                }
                break;
            case SAVE_FAILED:
                break;
            case SAVE_SUCCESS:
                break;
            default:
                switch (mAdvancedWifiOptionsFlow.handlePageComplete(formPageType, formPage)) {
                    case AdvancedWifiOptionsFlow.RESULT_UNKNOWN_PAGE:
                        break;
                    case AdvancedWifiOptionsFlow.RESULT_PAGE_HANDLED:
                        break;
                    case AdvancedWifiOptionsFlow.RESULT_ALL_PAGES_COMPLETE:
                        save();
                        break;
                    default:
                        break;
                }
                break;
        }
        return true;
    }

    @Override
    protected void displayPage(FormPage formPage, FormPageResultListener listener,
            boolean forward) {
        WifiFormPageType formPageType = getFormPageType(formPage);
        if (formPageType == WifiFormPageType.SAVE) {
            mSavePage = formPage;
            Fragment fragment = SaveWifiConfigurationFragment.newInstance(
                    getString(formPageType.getTitleResourceId(), mConfiguration.getPrintableName()),
                    mConfiguration);
            displayFragment(fragment, forward);
        } else if (formPageType == WifiFormPageType.SAVE_SUCCESS) {
            mSuccessPage = formPage;
            Fragment fragment = TimedMessageWizardFragment.newInstance(
                    getString(formPageType.getTitleResourceId()));
            displayFragment(fragment, forward);
        } else {
            displayPage(formPageType, mConfiguration.getPrintableName(), null, null,
                    mAdvancedWifiOptionsFlow.getPreviousPage(formPageType), null,
                    formPageType != WifiFormPageType.SAVE_SUCCESS, formPage, listener, forward,
                    mAdvancedWifiOptionsFlow.isEmptyTextAllowed(formPageType));
        }
    }

    private FormPage getPreviousPage(WifiFormPageType formPageType) {
        return mAdvancedWifiOptionsFlow.getPreviousPage(formPageType);
    }

    private void save() {
        mAdvancedWifiOptionsFlow.updateConfiguration(mConfiguration);
        addPage(WifiFormPageType.SAVE);
    }
}
