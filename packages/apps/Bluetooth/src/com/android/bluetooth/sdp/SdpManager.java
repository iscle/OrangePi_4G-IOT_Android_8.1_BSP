/*
* Copyright (C) 2015 Samsung System LSI
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
package com.android.bluetooth.sdp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.SdpMasRecord;
import android.bluetooth.SdpMnsRecord;
import android.bluetooth.SdpOppOpsRecord;
import android.bluetooth.SdpPseRecord;
import android.bluetooth.SdpSapsRecord;
import android.bluetooth.SdpRecord;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AbstractionLayer;
import com.android.bluetooth.btservice.AdapterService;

import java.util.ArrayList;
import java.util.Arrays;

public class SdpManager {

    private static final boolean D = true;
    private static final boolean V = false;
    private static final String TAG="SdpManager";

    // TODO: When changing PBAP to use this new API.
    //       Move the defines to the profile (PBAP already have the feature bits)
    /* PBAP repositories */
    public static final byte PBAP_REPO_LOCAL        = 0x01<<0;
    public static final byte PBAP_REPO_SIM          = 0x01<<1;
    public static final byte PBAP_REPO_SPEED_DAIL   = 0x01<<2;
    public static final byte PBAP_REPO_FAVORITES    = 0x01<<3;

    /* Variables to keep track of ongoing and queued search requests.
     * mTrackerLock must be held, when using/changing sSdpSearchTracker
     * and mSearchInProgress. */
    static SdpSearchTracker sSdpSearchTracker;
    static boolean mSearchInProgress = false;
    static Object mTrackerLock = new Object();

    /* The timeout to wait for reply from native. Should never fire. */
    private static final int SDP_INTENT_DELAY = 11000;
    private static final int MESSAGE_SDP_INTENT = 2;

    // We need a reference to the adapter service, to be able to send intents
    private static AdapterService sAdapterService;
    private static boolean sNativeAvailable;

    // This object is a singleton
    private static SdpManager sSdpManager = null;

    static {
        classInitNative();
    }

    private native static void classInitNative();
    private native void initializeNative();
    private native void cleanupNative();
    private native boolean sdpSearchNative(byte[] address, byte[] uuid);

    private native int sdpCreateMapMasRecordNative(String serviceName, int masId,
            int rfcommChannel, int l2capPsm, int version, int msgTypes, int features);

    private native int sdpCreateMapMnsRecordNative(String serviceName,
            int rfcommChannel, int l2capPsm, int version, int features);

    private native int sdpCreatePbapPseRecordNative(String serviceName, int rfcommChannel,
            int l2capPsm, int version, int repositories, int features);

    private native int sdpCreateOppOpsRecordNative(String serviceName,
            int rfcommChannel, int l2capPsm, int version, byte[] formats_list);

    private native int sdpCreateSapsRecordNative(String serviceName, int rfcommChannel,
            int version);

    private native boolean sdpRemoveSdpRecordNative(int record_id);


    /* Inner class used for wrapping sdp search instance data */
    private class SdpSearchInstance {
        private final BluetoothDevice mDevice;
        private final ParcelUuid mUuid;
        private int mStatus = 0;
        private boolean mSearching;
        /* TODO: If we change the API to use another mechanism than intents for
         *       delivering the results, this would be the place to keep a list
         *       of the objects to deliver the results to. */
        public SdpSearchInstance(int status, BluetoothDevice device, ParcelUuid uuid){
            this.mDevice = device;
            this.mUuid = uuid;
            this.mStatus = status;
            mSearching = true;
        }
        public BluetoothDevice getDevice() {
            return mDevice;
        }
        public ParcelUuid getUuid() {
            return mUuid;
        }
        public int getStatus(){
            return mStatus;
        }

        public void setStatus(int status) {
            this.mStatus = status;
        }

        public void startSearch() {
            mSearching = true;
            Message message = mHandler.obtainMessage(MESSAGE_SDP_INTENT, this);
            mHandler.sendMessageDelayed(message, SDP_INTENT_DELAY);
        }

        public void stopSearch() {
            if(mSearching) {
                mHandler.removeMessages(MESSAGE_SDP_INTENT, this);
            }
            mSearching = false;
        }
        public boolean isSearching() {
            return mSearching;
        }
    }


    /* We wrap the ArrayList class to decorate with functionality to
     * find an instance based on UUID AND device address.
     * As we use a mix of byte[] and object instances, this is more
     * efficient than implementing comparable. */
    class SdpSearchTracker {
        private final ArrayList<SdpSearchInstance> list = new ArrayList<SdpSearchInstance>();

        void clear() {
            list.clear();
        }

        boolean add(SdpSearchInstance inst){
            return list.add(inst);
        }

        boolean remove(SdpSearchInstance inst) {
            return list.remove(inst);
        }

        SdpSearchInstance getNext() {
            if(list.size() > 0) {
                return list.get(0);
            }
            return null;
        }

        SdpSearchInstance getSearchInstance(byte[] address, byte[] uuidBytes) {
            String addressString = Utils.getAddressStringFromByte(address);
            ParcelUuid uuid = Utils.byteArrayToUuid(uuidBytes)[0];
            for (SdpSearchInstance inst : list) {
                if (inst.getDevice().getAddress().equals(addressString)
                        && inst.getUuid().equals(uuid)) {
                    return inst;
                }
            }
            return null;
        }

        boolean isSearching(BluetoothDevice device, ParcelUuid uuid) {
            String addressString = device.getAddress();
            for (SdpSearchInstance inst : list) {
                if (inst.getDevice().getAddress().equals(addressString)
                        && inst.getUuid().equals(uuid)) {
                    return inst.isSearching();
                }
            }
            return false;
        }
    }


    private SdpManager(AdapterService adapterService) {
        sSdpSearchTracker = new SdpSearchTracker();

        /* This is only needed until intents are no longer used */
        sAdapterService = adapterService;
        initializeNative();
        sNativeAvailable=true;
    }


    public static SdpManager init(AdapterService adapterService) {
        sSdpManager = new SdpManager(adapterService);
        return sSdpManager;
    }

    public static SdpManager getDefaultManager() {
        return sSdpManager;
    }

    public void cleanup() {
        if (sSdpSearchTracker !=null) {
            synchronized(mTrackerLock) {
                sSdpSearchTracker.clear();
            }
        }

        if (sNativeAvailable) {
            cleanupNative();
            sNativeAvailable=false;
        }
        sSdpManager = null;
    }


    void sdpMasRecordFoundCallback(int status, byte[] address, byte[] uuid,
            int masInstanceId,
            int l2capPsm,
            int rfcommCannelNumber,
            int profileVersion,
            int supportedFeatures,
            int supportedMessageTypes,
            String serviceName,
            boolean moreResults) {

        synchronized(mTrackerLock) {
            SdpSearchInstance inst = sSdpSearchTracker.getSearchInstance(address, uuid);
            SdpMasRecord sdpRecord = null;
            if (inst == null) {
                Log.e(TAG, "sdpRecordFoundCallback: Search instance is NULL");
                return;
            }
            inst.setStatus(status);
            if(status == AbstractionLayer.BT_STATUS_SUCCESS) {
                sdpRecord = new SdpMasRecord(masInstanceId,
                                             l2capPsm,
                                             rfcommCannelNumber,
                                             profileVersion,
                                             supportedFeatures,
                                             supportedMessageTypes,
                                             serviceName);
            }
            if(D) Log.d(TAG, "UUID: " + Arrays.toString(uuid));
            if(D) Log.d(TAG, "UUID in parcel: " + ((Utils.byteArrayToUuid(uuid))[0]).toString());
            sendSdpIntent(inst, sdpRecord, moreResults);
        }
    }

    void sdpMnsRecordFoundCallback(int status, byte[] address, byte[] uuid,
            int l2capPsm,
            int rfcommCannelNumber,
            int profileVersion,
            int supportedFeatures,
            String serviceName,
            boolean moreResults) {
        synchronized(mTrackerLock) {

            SdpSearchInstance inst = sSdpSearchTracker.getSearchInstance(address, uuid);
            SdpMnsRecord sdpRecord = null;
            if (inst == null) {
                Log.e(TAG, "sdpRecordFoundCallback: Search instance is NULL");
                return;
            }
            inst.setStatus(status);
            if(status == AbstractionLayer.BT_STATUS_SUCCESS) {
                sdpRecord = new SdpMnsRecord(l2capPsm,
                                             rfcommCannelNumber,
                                             profileVersion,
                                             supportedFeatures,
                                             serviceName);
            }
            if(D) Log.d(TAG, "UUID: " + Arrays.toString(uuid));
            if(D) Log.d(TAG, "UUID in parcel: " + ((Utils.byteArrayToUuid(uuid))[0]).toString());
            sendSdpIntent(inst, sdpRecord, moreResults);
        }
    }

    void sdpPseRecordFoundCallback(int status, byte[] address, byte[] uuid,
                                        int l2capPsm,
                                        int rfcommCannelNumber,
                                        int profileVersion,
                                        int supportedFeatures,
                                        int supportedRepositories,
                                        String serviceName,
                                        boolean moreResults) {
        synchronized(mTrackerLock) {
            SdpSearchInstance inst = sSdpSearchTracker.getSearchInstance(address, uuid);
            SdpPseRecord sdpRecord = null;
            if (inst == null) {
                Log.e(TAG, "sdpRecordFoundCallback: Search instance is NULL");
                return;
            }
            inst.setStatus(status);
            if(status == AbstractionLayer.BT_STATUS_SUCCESS) {
                sdpRecord = new SdpPseRecord(l2capPsm,
                                             rfcommCannelNumber,
                                             profileVersion,
                                             supportedFeatures,
                                             supportedRepositories,
                                             serviceName);
            }
            if(D) Log.d(TAG, "UUID: " + Arrays.toString(uuid));
            if(D) Log.d(TAG, "UUID in parcel: " + ((Utils.byteArrayToUuid(uuid))[0]).toString());
            sendSdpIntent(inst, sdpRecord, moreResults);
        }
    }

    void sdpOppOpsRecordFoundCallback(int status, byte[] address, byte[] uuid,
            int l2capPsm,
            int rfcommCannelNumber,
            int profileVersion,
            String serviceName,
            byte[] formatsList,
            boolean moreResults) {

        synchronized(mTrackerLock) {
            SdpSearchInstance inst = sSdpSearchTracker.getSearchInstance(address, uuid);
            SdpOppOpsRecord sdpRecord = null;

            if (inst == null) {
                Log.e(TAG, "sdpOppOpsRecordFoundCallback: Search instance is NULL");
                return;
            }
            inst.setStatus(status);
            if(status == AbstractionLayer.BT_STATUS_SUCCESS) {
                sdpRecord = new SdpOppOpsRecord(serviceName,
                                                rfcommCannelNumber,
                                                l2capPsm,
                                                profileVersion,
                                                formatsList);
            }
            if(D) Log.d(TAG, "UUID: " + Arrays.toString(uuid));
            if(D) Log.d(TAG, "UUID in parcel: " + ((Utils.byteArrayToUuid(uuid))[0]).toString());
            sendSdpIntent(inst, sdpRecord, moreResults);
        }
    }

    void sdpSapsRecordFoundCallback(int status, byte[] address, byte[] uuid,
            int rfcommCannelNumber,
            int profileVersion,
            String serviceName,
            boolean moreResults) {

        synchronized(mTrackerLock) {
            SdpSearchInstance inst = sSdpSearchTracker.getSearchInstance(address, uuid);
            SdpSapsRecord sdpRecord = null;
            if (inst == null) {
                Log.e(TAG, "sdpSapsRecordFoundCallback: Search instance is NULL");
                return;
            }
            inst.setStatus(status);
            if (status == AbstractionLayer.BT_STATUS_SUCCESS) {
                sdpRecord = new SdpSapsRecord(rfcommCannelNumber,
                                             profileVersion,
                                             serviceName);
            }
            if (D) Log.d(TAG, "UUID: " + Arrays.toString(uuid));
            if (D) Log.d(TAG, "UUID in parcel: " + ((Utils.byteArrayToUuid(uuid))[0]).toString());
            sendSdpIntent(inst, sdpRecord, moreResults);
        }
    }

    /* TODO: Test or remove! */
    void sdpRecordFoundCallback(int status, byte[] address, byte[] uuid,
            int size_record, byte[] record) {
        synchronized(mTrackerLock) {

            SdpSearchInstance inst = sSdpSearchTracker.getSearchInstance(address, uuid);
            SdpRecord sdpRecord = null;
            if (inst == null) {
                Log.e(TAG, "sdpRecordFoundCallback: Search instance is NULL");
                return;
            }
            inst.setStatus(status);
            if(status == AbstractionLayer.BT_STATUS_SUCCESS) {
                if(D) Log.d(TAG, "sdpRecordFoundCallback: found a sdp record of size "
                        + size_record );
                if(D) Log.d(TAG, "Record:"+ Arrays.toString(record));
                sdpRecord = new SdpRecord(size_record, record);
            }
            if(D) Log.d(TAG, "UUID: " + Arrays.toString(uuid));
            if(D) Log.d(TAG, "UUID in parcel: " + ((Utils.byteArrayToUuid(uuid))[0]).toString());
            sendSdpIntent(inst, sdpRecord, false);
        }
    }

    public void sdpSearch(BluetoothDevice device, ParcelUuid uuid) {
        if (sNativeAvailable == false) {
            Log.e(TAG, "Native not initialized!");
            return;
        }
        synchronized (mTrackerLock) {
            if (sSdpSearchTracker.isSearching(device, uuid)) {
                /* Search already in progress */
                return;
            }

            SdpSearchInstance inst = new SdpSearchInstance(0, device, uuid);
            sSdpSearchTracker.add(inst); // Queue the request

            startSearch(); // Start search if not busy
        }

    }

    /* Caller must hold the mTrackerLock */
    private void startSearch() {

        SdpSearchInstance inst = sSdpSearchTracker.getNext();

        if((inst != null) && (mSearchInProgress == false)) {
            if(D) Log.d(TAG, "Starting search for UUID: "+ inst.getUuid());
            mSearchInProgress = true;

            inst.startSearch(); // Trigger timeout message

            sdpSearchNative(Utils.getBytesFromAddress(inst.getDevice().getAddress()),
                                            Utils.uuidToByteArray(inst.getUuid()));
        } // Else queue is empty.
        else {
            if(D) Log.d(TAG, "startSearch(): nextInst = " + inst +
                    " mSearchInProgress = " + mSearchInProgress
                    + " - search busy or queue empty.");
        }
    }

    /* Caller must hold the mTrackerLock */
    private void sendSdpIntent(SdpSearchInstance inst,
            Parcelable record, boolean moreResults) {

        inst.stopSearch();

        Intent intent = new Intent(BluetoothDevice.ACTION_SDP_RECORD);

        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, inst.getDevice());
        intent.putExtra(BluetoothDevice.EXTRA_SDP_SEARCH_STATUS, inst.getStatus());
        if (record != null)  intent.putExtra(BluetoothDevice.EXTRA_SDP_RECORD, record);
        intent.putExtra(BluetoothDevice.EXTRA_UUID, inst.getUuid());
        /* TODO:  BLUETOOTH_ADMIN_PERM was private... change to callback interface.
         * Keep in mind that the MAP client needs to use this as well,
         * hence to make it call-backs, the MAP client profile needs to be
         * part of the Bluetooth APK. */
        sAdapterService.sendBroadcast(intent, AdapterService.BLUETOOTH_ADMIN_PERM);

        if(moreResults == false) {
            //Remove the outstanding UUID request
            sSdpSearchTracker.remove(inst);
            mSearchInProgress = false;
            startSearch();
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_SDP_INTENT:
                SdpSearchInstance msgObj = (SdpSearchInstance)msg.obj;
                Log.w(TAG, "Search timedout for UUID " + msgObj.getUuid());
                synchronized (mTrackerLock) {
                    sendSdpIntent(msgObj, null, false);
                }
                break;
            }
        }
    };

    /**
     * Create a server side Message Access Profile Service Record.
     * Create the record once, and reuse it for all connections.
     * If changes to a record is needed remove the old record using {@link removeSdpRecord}
     * and then create a new one.
     * @param serviceName   The textual name of the service
     * @param masId         The MAS ID to associate with this SDP record
     * @param rfcommChannel The RFCOMM channel that clients can connect to
     *                      (obtain from BluetoothServerSocket)
     * @param l2capPsm      The L2CAP PSM channel that clients can connect to
     *                      (obtain from BluetoothServerSocket)
     *                      Supply -1 to omit the L2CAP PSM from the record.
     * @param version       The Profile version number (As specified in the Bluetooth
     *                      MAP specification)
     * @param msgTypes      The supported message types bit mask (As specified in
     *                      the Bluetooth MAP specification)
     * @param features      The feature bit mask (As specified in the Bluetooth
     *                       MAP specification)
     * @return a handle to the record created. The record can be removed again
     *          using {@link removeSdpRecord}(). The record is not linked to the
     *          creation/destruction of BluetoothSockets, hence SDP record cleanup
     *          is a separate process.
     */
    public int createMapMasRecord(String serviceName, int masId,
            int rfcommChannel, int l2capPsm, int version,
            int msgTypes, int features) {
        if(sNativeAvailable == false) {
            throw new RuntimeException(TAG + " sNativeAvailable == false - native not initialized");
        }
        return sdpCreateMapMasRecordNative(serviceName, masId, rfcommChannel,
                l2capPsm, version, msgTypes, features);
    }

    /**
     * Create a client side Message Access Profile Service Record.
     * Create the record once, and reuse it for all connections.
     * If changes to a record is needed remove the old record using {@link removeSdpRecord}
     * and then create a new one.
     * @param serviceName   The textual name of the service
     * @param rfcommChannel The RFCOMM channel that clients can connect to
     *                      (obtain from BluetoothServerSocket)
     * @param l2capPsm      The L2CAP PSM channel that clients can connect to
     *                      (obtain from BluetoothServerSocket)
     *                      Supply -1 to omit the L2CAP PSM from the record.
     * @param version       The Profile version number (As specified in the Bluetooth
     *                      MAP specification)
     * @param features      The feature bit mask (As specified in the Bluetooth
     *                       MAP specification)
     * @return a handle to the record created. The record can be removed again
     *          using {@link removeSdpRecord}(). The record is not linked to the
     *          creation/destruction of BluetoothSockets, hence SDP record cleanup
     *          is a separate process.
     */
    public int createMapMnsRecord(String serviceName, int rfcommChannel,
            int l2capPsm, int version, int features) {
        if(sNativeAvailable == false) {
            throw new RuntimeException(TAG + " sNativeAvailable == false - native not initialized");
        }
        return sdpCreateMapMnsRecordNative(serviceName, rfcommChannel,
                l2capPsm, version, features);
    }

    /**
     * Create a Server side Phone Book Access Profile Service Record.
     * Create the record once, and reuse it for all connections.
     * If changes to a record is needed remove the old record using {@link removeSdpRecord}
     * and then create a new one.
     * @param serviceName   The textual name of the service
     * @param rfcommChannel The RFCOMM channel that clients can connect to
     *                      (obtain from BluetoothServerSocket)
     * @param l2capPsm      The L2CAP PSM channel that clients can connect to
     *                      (obtain from BluetoothServerSocket)
     *                      Supply -1 to omit the L2CAP PSM from the record.
     * @param version       The Profile version number (As specified in the Bluetooth
     *                      PBAP specification)
     * @param repositories  The supported repositories bit mask (As specified in
     *                      the Bluetooth PBAP specification)
     * @param features      The feature bit mask (As specified in the Bluetooth
     *                      PBAP specification)
     * @return a handle to the record created. The record can be removed again
     *          using {@link removeSdpRecord}(). The record is not linked to the
     *          creation/destruction of BluetoothSockets, hence SDP record cleanup
     *          is a separate process.
     */
    public int createPbapPseRecord(String serviceName, int rfcommChannel, int l2capPsm,
                                   int version, int repositories, int features) {
        if(sNativeAvailable == false) {
            throw new RuntimeException(TAG + " sNativeAvailable == false - native not initialized");
        }
        return sdpCreatePbapPseRecordNative(serviceName, rfcommChannel,
                l2capPsm, version, repositories, features);
    }

    /**
     * Create a Server side Object Push Profile Service Record.
     * Create the record once, and reuse it for all connections.
     * If changes to a record is needed remove the old record using {@link removeSdpRecord}
     * and then create a new one.
     * @param serviceName   The textual name of the service
     * @param rfcommChannel The RFCOMM channel that clients can connect to
     *                      (obtain from BluetoothServerSocket)
     * @param l2capPsm      The L2CAP PSM channel that clients can connect to
     *                      (obtain from BluetoothServerSocket)
     *                      Supply -1 to omit the L2CAP PSM from the record.
     * @param version       The Profile version number (As specified in the Bluetooth
     *                      OPP specification)
     * @param formatsList  A list of the supported formats (As specified in
     *                      the Bluetooth OPP specification)
     * @return a handle to the record created. The record can be removed again
     *          using {@link removeSdpRecord}(). The record is not linked to the
     *          creation/destruction of BluetoothSockets, hence SDP record cleanup
     *          is a separate process.
     */
    public int createOppOpsRecord(String serviceName, int rfcommChannel, int l2capPsm,
                                  int version, byte[] formatsList) {
        if(sNativeAvailable == false) {
            throw new RuntimeException(TAG + " sNativeAvailable == false - native not initialized");
        }
        return sdpCreateOppOpsRecordNative(serviceName, rfcommChannel,
                 l2capPsm, version, formatsList);
    }

    /**
     * Create a server side Sim Access Profile Service Record.
     * Create the record once, and reuse it for all connections.
     * If changes to a record is needed remove the old record using {@link removeSdpRecord}
     * and then create a new one.
     * @param serviceName   The textual name of the service
     * @param rfcommChannel The RFCOMM channel that clients can connect to
     *                      (obtain from BluetoothServerSocket)
     * @param version       The Profile version number (As specified in the Bluetooth
     *                      SAP specification)
     * @return a handle to the record created. The record can be removed again
     *          using {@link removeSdpRecord}(). The record is not linked to the
     *          creation/destruction of BluetoothSockets, hence SDP record cleanup
     *          is a separate process.
     */
    public int createSapsRecord(String serviceName, int rfcommChannel, int version) {
        if (sNativeAvailable == false) {
            throw new RuntimeException(TAG + " sNativeAvailable == false - native not initialized");
        }
        return sdpCreateSapsRecordNative(serviceName, rfcommChannel, version);
    }

     /**
      * Remove a SDP record.
      * When Bluetooth is disabled all records will be deleted, hence there
      * is no need to call this function when bluetooth is disabled.
      * @param recordId The Id returned by on of the createXxxXxxRecord() functions.
      * @return TRUE if the record removal was initiated successfully. FALSE if the record
      *         handle is not known/have already been removed.
      */
    public boolean removeSdpRecord(int recordId){
        if(sNativeAvailable == false) {
            throw new RuntimeException(TAG + " sNativeAvailable == false - native not initialized");
        }
        return sdpRemoveSdpRecordNative(recordId);
    }
}
