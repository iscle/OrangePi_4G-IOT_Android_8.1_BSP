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

package com.android.internal.telephony.ims;

import android.app.PendingIntent;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.ims.feature.ImsFeature;

import com.android.ims.ImsCallProfile;
import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsCallSessionListener;
import com.android.ims.internal.IImsConfig;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsFeatureStatusCallback;
import com.android.ims.internal.IImsMultiEndpoint;
import com.android.ims.internal.IImsRegistrationListener;
import com.android.ims.internal.IImsServiceController;
import com.android.ims.internal.IImsUt;

import static org.mockito.Mockito.spy;

/**
 * Test base implementation of the ImsServiceController, which is used as a mockito spy.
 */

public class TestImsServiceControllerAdapter {

    public IImsFeatureStatusCallback mStatusCallback;

    public class ImsServiceControllerBinder extends IImsServiceController.Stub {

        @Override
        public void createImsFeature(int slotId, int feature, IImsFeatureStatusCallback c)
                throws RemoteException {
            TestImsServiceControllerAdapter.this.createImsFeature(slotId, feature);
            mStatusCallback = c;
        }

        @Override
        public void removeImsFeature(int slotId, int feature, IImsFeatureStatusCallback c)
                throws RemoteException {
            TestImsServiceControllerAdapter.this.removeImsFeature(slotId, feature);
        }

        @Override
        public int startSession(int slotId, int featureType, PendingIntent incomingCallIntent,
                IImsRegistrationListener listener) throws RemoteException {
            return 0;
        }

        @Override
        public void endSession(int slotId, int featureType, int sessionId) throws RemoteException {

        }

        @Override
        public boolean isConnected(int slotId, int featureType, int callSessionType, int callType)
                throws RemoteException {
            return false;
        }

        @Override
        public boolean isOpened(int slotId, int featureType) throws RemoteException {
            return false;
        }

        @Override
        public int getFeatureStatus(int slotId, int featureType) throws RemoteException {
            return ImsFeature.STATE_NOT_AVAILABLE;
        }

        @Override
        public void addRegistrationListener(int slotId, int featureType,
                IImsRegistrationListener listener) throws RemoteException {

        }

        @Override
        public void removeRegistrationListener(int slotId, int featureType,
                IImsRegistrationListener listener) throws RemoteException {

        }

        @Override
        public ImsCallProfile createCallProfile(int slotId, int featureType, int sessionId,
                int callSessionType, int callType) throws RemoteException {
            return null;
        }

        @Override
        public IImsCallSession createCallSession(int slotId, int featureType, int sessionId,
                ImsCallProfile profile, IImsCallSessionListener listener) throws RemoteException {
            return null;
        }

        @Override
        public IImsCallSession getPendingCallSession(int slotId, int featureType, int sessionId,
                String callId) throws RemoteException {
            return null;
        }

        @Override
        public IImsUt getUtInterface(int slotId, int featureType)
                throws RemoteException {
            return null;
        }

        @Override
        public IImsConfig getConfigInterface(int slotId, int featureType)
                throws RemoteException {
            return null;
        }

        @Override
        public void turnOnIms(int slotId, int featureType)
                throws RemoteException {

        }

        @Override
        public void turnOffIms(int slotId, int featureType) throws RemoteException {

        }

        @Override
        public IImsEcbm getEcbmInterface(int slotId, int featureType)
                throws RemoteException {
            return null;
        }

        @Override
        public void setUiTTYMode(int slotId, int featureType, int uiTtyMode, Message onComplete)
                throws RemoteException {

        }

        @Override
        public IImsMultiEndpoint getMultiEndpointInterface(int slotId, int featureType)
                throws RemoteException {
            return null;
        }
    }

    private ImsServiceControllerBinder mBinder;

    public IImsServiceController getBinder() {
        if (mBinder == null) {
            mBinder = spy(new ImsServiceControllerBinder());
        }

        return mBinder;
    }

    // Used by Mockito for verification that this method is being called in spy
    public void createImsFeature(int subId, int feature) throws RemoteException {
    }

    // Used by Mockito for verification that this method is being called in spy
    public void removeImsFeature(int subId, int feature) throws RemoteException {
    }
}
