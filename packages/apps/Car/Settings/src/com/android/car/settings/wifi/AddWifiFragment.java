/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.car.settings.wifi;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.car.settings.R;
import com.android.car.settings.common.EditTextLineItem;
import com.android.car.settings.common.ListSettingsFragment;
import com.android.car.settings.common.PasswordLineItem;
import com.android.car.settings.common.SpinnerLineItem;
import com.android.car.settings.common.TypedPagedListAdapter;
import com.android.settingslib.wifi.AccessPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Adds a wifi network, the network can be public or private. If ADD_NETWORK_MODE is not specified
 * in the intent, then it needs to contain AccessPoint information, which is be use that to
 * render UI, e.g. show SSID etc.
 */
public class AddWifiFragment extends ListSettingsFragment implements
        AdapterView.OnItemSelectedListener{
    public static final String EXTRA_AP_STATE = "extra_ap_state";

    private static final String TAG = "AddWifiFragment";
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9A-F]+$");
    private static final Pattern VALID_SSID_PATTERN =
            Pattern.compile("^[A-Za-z]+[\\w\\-\\:\\.]*$");
    @Nullable private AccessPoint mAccessPoint;
    @Nullable private SpinnerLineItem<AccessPointSecurity> mSpinnerLineItem;
    private WifiManager mWifiManager;
    private TextView mAddWifiButton;
    private final WifiManager.ActionListener mConnectionListener =
            new WifiManager.ActionListener() {
        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure(int reason) {
            Toast.makeText(getContext(),
                    R.string.wifi_failed_connect_message,
                    Toast.LENGTH_SHORT).show();
        }
    };
    private EditTextLineItem mWifiNameInput;
    private EditTextLineItem mWifiPasswordInput;

    private int mSelectedPosition = AccessPointSecurity.SECURITY_NONE_POSITION;

    public static AddWifiFragment getInstance(AccessPoint accessPoint) {
        AddWifiFragment addWifiFragment = new AddWifiFragment();
        Bundle bundle = ListSettingsFragment.getBundle();
        bundle.putInt(EXTRA_TITLE_ID, R.string.wifi_setup_add_network);
        bundle.putInt(EXTRA_ACTION_BAR_LAYOUT, R.layout.action_bar_with_button);
        Bundle accessPointState = new Bundle();
        if (accessPoint != null) {
            accessPoint.saveWifiState(accessPointState);
            bundle.putBundle(EXTRA_AP_STATE, accessPointState);
        }
        addWifiFragment.setArguments(bundle);
        return addWifiFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments().keySet().contains(EXTRA_AP_STATE)) {
            mAccessPoint = new AccessPoint(getContext(), getArguments().getBundle(EXTRA_AP_STATE));
        }
        mWifiManager = getContext().getSystemService(WifiManager.class);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mAddWifiButton = getActivity().findViewById(R.id.action_button1);
        mAddWifiButton.setText(R.string.wifi_setup_connect);
        mAddWifiButton.setOnClickListener(v -> {
            connectToAccessPoint();
            mFragmentController.goBack();
        });
        mAddWifiButton.setEnabled(mAccessPoint != null) ;
    }

    @Override
    public ArrayList<TypedPagedListAdapter.LineItem> getLineItems() {
        ArrayList<TypedPagedListAdapter.LineItem> lineItems = new ArrayList<>();
        if (mAccessPoint != null) {
            mWifiNameInput = new EditTextLineItem(
                    getContext().getText(R.string.wifi_ssid), mAccessPoint.getSsid());
            mWifiNameInput.setTextType(EditTextLineItem.TextType.NONE);
        } else {
            mWifiNameInput = new EditTextLineItem(
                    getContext().getText(R.string.wifi_ssid));
            mWifiNameInput.setTextType(EditTextLineItem.TextType.TEXT);
            mWifiNameInput.setTextChangeListener(s ->
                    mAddWifiButton.setEnabled(VALID_SSID_PATTERN.matcher(s).matches()));
        }
        lineItems.add(mWifiNameInput);

        if (mAccessPoint == null) {
            List<AccessPointSecurity> securities =
                    AccessPointSecurity.getSecurityTypes(getContext());
            mSpinnerLineItem = new SpinnerLineItem<>(
                    getContext(),
                    this,
                    securities,
                    getContext().getText(R.string.wifi_security),
                    mSelectedPosition);
            lineItems.add(mSpinnerLineItem);
        }

        if (mAccessPoint!= null
                || mSelectedPosition != AccessPointSecurity.SECURITY_NONE_POSITION) {
            mWifiPasswordInput = new PasswordLineItem(getContext().getText(R.string.wifi_password));
            lineItems.add(mWifiPasswordInput);
        }
        return lineItems;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position == mSelectedPosition) {
            return;
        }
        mSelectedPosition = position;
        mPagedListAdapter.updateList(getLineItems());
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    private void connectToAccessPoint() {
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = String.format("\"%s\"", getSsId());
        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        int security;
        if (mAccessPoint == null) {
            security = mSpinnerLineItem.getItem(mSelectedPosition).getSecurityType();
            wifiConfig.hiddenSSID = true;
        } else {
            security = mAccessPoint.getSecurity();
        }
        switch (security) {
            case AccessPoint.SECURITY_NONE:
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wifiConfig.allowedAuthAlgorithms.clear();
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                break;
            case AccessPoint.SECURITY_WEP:
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                String password = mWifiPasswordInput.getInput();
                wifiConfig.wepKeys[0] = isHexString(password) ? password
                        : "\"" + password + "\"";
                wifiConfig.wepTxKeyIndex = 0;
                break;
            case AccessPoint.SECURITY_PSK:
            case AccessPoint.SECURITY_EAP:
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                wifiConfig.preSharedKey = String.format(
                        "\"%s\"", mWifiPasswordInput.getInput());
                break;
            default:
                Log.w(TAG, "invalid security type: " + security);
                break;
        }
        int netId = mWifiManager.addNetwork(wifiConfig);
        if (netId == -1) {
            Toast.makeText(getContext(),
                    R.string.wifi_failed_connect_message,
                    Toast.LENGTH_SHORT).show();
        } else {
            mWifiManager.enableNetwork(netId, true);
        }
    }

    private boolean isHexString(String password) {
        return HEX_PATTERN.matcher(password).matches();
    }

    // TODO: handle null case, show warning message etc.
    private String getSsId() {
        if (mAccessPoint == null) {
            return mWifiNameInput.getInput();
        } else {
            return mAccessPoint.getSsid().toString();
        }
    }
}
