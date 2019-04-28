/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.mocks;

import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Message;
import android.util.LocalLog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.dataconnection.DcTracker;

import java.util.ArrayList;

public class DcTrackerMock extends DcTracker {
    public DcTrackerMock() {
    }
    @Override
    public void registerServiceStateTrackerEvents() {
        throw new RuntimeException("registerServiceStateTrackerEvents not implemented");
    }
    @Override
    public void unregisterServiceStateTrackerEvents() {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public void setDataEnabled(boolean enable) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public long getSubId() {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public DctConstants.Activity getActivity() {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public boolean isApnSupported(String name) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public int getApnPriority(String name) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public LinkProperties getLinkProperties(String apnType) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public String[] getActiveApnTypes() {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public String getActiveApnString(String apnType) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public DctConstants.State getState(String apnType) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public DctConstants.State getOverallState() {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public boolean hasMatchedTetherApnSetting() {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public boolean getAutoAttachOnCreation() {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public boolean getDataEnabled() {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public void setDataRoamingEnabledByUser(boolean enabled) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public boolean getDataRoamingEnabled() {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public boolean isDisconnected() {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public void update() {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public void cleanUpAllConnections(String cause) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public void updateRecords() {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public void cleanUpAllConnections(String cause, Message disconnectAllCompleteMsg) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public void unregisterForAllDataDisconnected(Handler h) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public boolean setInternalDataEnabled(boolean enable) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public String[] getPcscfAddress(String apnType) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public void sendStartNetStatPoll(DctConstants.Activity activity) {
        throw new RuntimeException("Not Implemented");
    }
    @Override
    public void sendStopNetStatPoll(DctConstants.Activity activity) {
        throw new RuntimeException("Not Implemented");
    }

    private final ArrayList<NetworkRequest> mRequestedNetworks = new ArrayList<NetworkRequest>();

    @Override
    public void requestNetwork(NetworkRequest networkRequest, LocalLog log) {
        synchronized (mRequestedNetworks) {
            mRequestedNetworks.add(networkRequest);
        }
    }

    @Override
    public void releaseNetwork(NetworkRequest networkRequest, LocalLog log) {
        synchronized (mRequestedNetworks) {
            mRequestedNetworks.remove(networkRequest);
        }
    }

    @VisibleForTesting
    public int getNumberOfLiveRequests() {
        synchronized (mRequestedNetworks) {
            return mRequestedNetworks.size();
        }
    }
}
