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

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import com.android.tv.settings.connectivity.setup.MessageWizardFragment;

/**
 * Saves a wifi network's configuration.
 */
public class SaveWifiConfigurationFragment extends MessageWizardFragment
        implements WifiManager.ActionListener {

    public interface Listener {
        void onSaveWifiConfigurationCompleted(int result);
    }

    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_FAILURE = 1;

    private static final String EXTRA_NETWORK_TYPE = "networkType";
    private static final String EXTRA_CONFIGURATION = "configuration";

    public static SaveWifiConfigurationFragment newInstance(String title,
            NetworkConfiguration configuration) {
        SaveWifiConfigurationFragment fragment = new SaveWifiConfigurationFragment();
        Bundle args = new Bundle();
        args.putInt(EXTRA_NETWORK_TYPE, configuration.getNetworkType());
        args.putParcelable(EXTRA_CONFIGURATION, configuration.toParcelable());
        addArguments(args, title, true);
        fragment.setArguments(args);
        return fragment;
    }

    private Listener mListener;

    @Override
    public void onAttach(Activity activity) {
        if (activity instanceof Listener) {
            mListener = (Listener) activity;
        } else {
            throw new IllegalArgumentException("Activity must implement "
                    + "SaveWifiConfigurationFragment.Listener to use this fragment.");
        }
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        int networkType = getArguments().getInt(EXTRA_NETWORK_TYPE);
        NetworkConfiguration configuration =
                NetworkConfigurationFactory.createNetworkConfiguration(getActivity(), networkType);
        configuration.fromParcelable(getArguments().getParcelable(EXTRA_CONFIGURATION));
        configuration.save(this);
    }

    @Override
    public void onSuccess() {
        if (mListener != null) {
            mListener.onSaveWifiConfigurationCompleted(RESULT_SUCCESS);
        }
    }

    @Override
    public void onFailure(int reason) {
        if (mListener != null) {
            mListener.onSaveWifiConfigurationCompleted(RESULT_FAILURE);
        }
    }
}
