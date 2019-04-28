/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.bluetooth.btservice;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothDevice;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.hid.HidService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.a2dpsink.A2dpSinkService;
import com.android.bluetooth.hfpclient.HeadsetClientService;
import com.android.bluetooth.pbapclient.PbapClientService;

import android.bluetooth.OobData;
import android.content.Intent;
import android.os.Message;
import android.os.UserHandle;
import android.util.Log;

import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.RemoteDevices.DeviceProperties;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.util.ArrayList;

/**
 * This state machine handles Bluetooth Adapter State.
 * States:
 *      {@link StableState} :  No device is in bonding / unbonding state.
 *      {@link PendingCommandState} : Some device is in bonding / unbonding state.
 * TODO(BT) This class can be removed and this logic moved to the stack.
 */

final class BondStateMachine extends StateMachine {
    private static final boolean DBG = false;
    private static final String TAG = "BluetoothBondStateMachine";

    static final int CREATE_BOND = 1;
    static final int CANCEL_BOND = 2;
    static final int REMOVE_BOND = 3;
    static final int BONDING_STATE_CHANGE = 4;
    static final int SSP_REQUEST = 5;
    static final int PIN_REQUEST = 6;
    static final int BOND_STATE_NONE = 0;
    static final int BOND_STATE_BONDING = 1;
    static final int BOND_STATE_BONDED = 2;

    private AdapterService mAdapterService;
    private AdapterProperties mAdapterProperties;
    private RemoteDevices mRemoteDevices;
    private BluetoothAdapter mAdapter;

    private PendingCommandState mPendingCommandState = new PendingCommandState();
    private StableState mStableState = new StableState();

    public static final String OOBDATA = "oobdata";
    // M: ALPS02512786: send SM_QUIT_CMD to the end of message queue
    private boolean mStateMachineInitiated = false;

    private BondStateMachine(AdapterService service,
            AdapterProperties prop, RemoteDevices remoteDevices) {
        super("BondStateMachine:");
        addState(mStableState);
        addState(mPendingCommandState);
        mRemoteDevices = remoteDevices;
        mAdapterService = service;
        mAdapterProperties = prop;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        setInitialState(mStableState);
    }

    public static BondStateMachine make(AdapterService service,
            AdapterProperties prop, RemoteDevices remoteDevices) {
        Log.d(TAG, "make");
        BondStateMachine bsm = new BondStateMachine(service, prop, remoteDevices);
        bsm.start();
        return bsm;
    }

    public void doQuit() {
        // M: ALPS02512786: send SM_QUIT_CMD to the end of message queue @{
        if (mStateMachineInitiated) {
            // State Machine is initiated.
            // We can send SM_QUIT_CMD to the front of message queue.
            quitNow();
        } else {
            // State machine is not initiated.
            // We have to send SM_QUIT_CMD to the end of message queue.
            quit();
        }
        // @}
    }

    public void cleanup() {
        mAdapterService = null;
        mRemoteDevices = null;
        mAdapterProperties = null;
    }

    private class StableState extends State {
        @Override
        public void enter() {
            infoLog("StableState(): Entering Off State");
            // M: ALPS02512786: send SM_QUIT_CMD to the end of message queue @{
            // State machine is completely initiated after the enter function of initial state
            // is involked.
            mStateMachineInitiated = true;
            // @}
        }

        @Override
        public boolean processMessage(Message msg) {

            BluetoothDevice dev = (BluetoothDevice)msg.obj;

            switch(msg.what) {

              case CREATE_BOND:
                  OobData oobData = null;
                  if (msg.getData() != null)
                      oobData = msg.getData().getParcelable(OOBDATA);

                  createBond(dev, msg.arg1, oobData, true);
                  break;
              case REMOVE_BOND:
                  removeBond(dev, true);
                  break;
              case BONDING_STATE_CHANGE:
                int newState = msg.arg1;
                /* if incoming pairing, transition to pending state */
                if (newState == BluetoothDevice.BOND_BONDING)
                {
                    sendIntent(dev, newState, 0);
                    transitionTo(mPendingCommandState);
                }
                else if (newState == BluetoothDevice.BOND_NONE)
                {
                    /* if the link key was deleted by the stack */
                    sendIntent(dev, newState, 0);
                }
                else
                {
                    Log.e(TAG, "In stable state, received invalid newState: " + newState);
                }
                break;

              case CANCEL_BOND:
              default:
                   Log.e(TAG, "Received unhandled state: " + msg.what);
                   return false;
            }
            return true;
        }
    }


    private class PendingCommandState extends State {
        private final ArrayList<BluetoothDevice> mDevices =
            new ArrayList<BluetoothDevice>();

        @Override
        public void enter() {
            infoLog("Entering PendingCommandState State");
            BluetoothDevice dev = (BluetoothDevice)getCurrentMessage().obj;
        }

        @Override
        public boolean processMessage(Message msg) {
            BluetoothDevice dev = (BluetoothDevice)msg.obj;
            DeviceProperties devProp = mRemoteDevices.getDeviceProperties(dev);
            boolean result = false;
             if (mDevices.contains(dev) && msg.what != CANCEL_BOND &&
                   msg.what != BONDING_STATE_CHANGE && msg.what != SSP_REQUEST &&
                   msg.what != PIN_REQUEST) {
                 deferMessage(msg);
                 return true;
             }

            switch (msg.what) {
                case CREATE_BOND:
                    OobData oobData = null;
                    if (msg.getData() != null)
                        oobData = msg.getData().getParcelable(OOBDATA);

                    result = createBond(dev, msg.arg1, oobData, false);
                    break;
                case REMOVE_BOND:
                    result = removeBond(dev, false);
                    break;
                case CANCEL_BOND:
                    result = cancelBond(dev);
                    break;
                case BONDING_STATE_CHANGE:
                    int newState = msg.arg1;
                    int reason = getUnbondReasonFromHALCode(msg.arg2);
                    sendIntent(dev, newState, reason);
                    if(newState != BluetoothDevice.BOND_BONDING )
                    {
                        /* this is either none/bonded, remove and transition */
                        result = !mDevices.remove(dev);
                        if (mDevices.isEmpty()) {
                            // Whenever mDevices is empty, then we need to
                            // set result=false. Else, we will end up adding
                            // the device to the list again. This prevents us
                            // from pairing with a device that we just unpaired
                            result = false;
                            transitionTo(mStableState);
                        }
                        if (newState == BluetoothDevice.BOND_NONE)
                        {
                            mAdapterService.setPhonebookAccessPermission(dev,
                                    BluetoothDevice.ACCESS_UNKNOWN);
                            mAdapterService.setMessageAccessPermission(dev,
                                    BluetoothDevice.ACCESS_UNKNOWN);
                            mAdapterService.setSimAccessPermission(dev,
                                    BluetoothDevice.ACCESS_UNKNOWN);
                            // Set the profile Priorities to undefined
                            clearProfilePriority(dev);
                        }
                    }
                    else if(!mDevices.contains(dev))
                        result=true;
                    break;
                case SSP_REQUEST:
                    int passkey = msg.arg1;
                    int variant = msg.arg2;
                    sendDisplayPinIntent(devProp.getAddress(), passkey, variant);
                    break;
                case PIN_REQUEST:
                    BluetoothClass btClass = dev.getBluetoothClass();
                    int btDeviceClass = btClass.getDeviceClass();
                    if (btDeviceClass == BluetoothClass.Device.PERIPHERAL_KEYBOARD ||
                         btDeviceClass == BluetoothClass.Device.PERIPHERAL_KEYBOARD_POINTING) {
                        // Its a keyboard. Follow the HID spec recommendation of creating the
                        // passkey and displaying it to the user. If the keyboard doesn't follow
                        // the spec recommendation, check if the keyboard has a fixed PIN zero
                        // and pair.
                        //TODO: Maintain list of devices that have fixed pin
                        // Generate a variable 6-digit PIN in range of 100000-999999
                        // This is not truly random but good enough.
                        int pin = 100000 + (int)Math.floor((Math.random() * (999999 - 100000)));
                        sendDisplayPinIntent(devProp.getAddress(), pin,
                                 BluetoothDevice.PAIRING_VARIANT_DISPLAY_PIN);
                        break;
                    }

                    if (msg.arg2 == 1) { // Minimum 16 digit pin required here
                        sendDisplayPinIntent(devProp.getAddress(), 0,
                                BluetoothDevice.PAIRING_VARIANT_PIN_16_DIGITS);
                    } else {
                        // In PIN_REQUEST, there is no passkey to display.So do not send the
                        // EXTRA_PAIRING_KEY type in the intent( 0 in SendDisplayPinIntent() )
                        sendDisplayPinIntent(devProp.getAddress(), 0,
                                              BluetoothDevice.PAIRING_VARIANT_PIN);
                    }

                    break;
                default:
                    Log.e(TAG, "Received unhandled event:" + msg.what);
                    return false;
            }
            if (result) mDevices.add(dev);

            return true;
        }
    }

    private boolean cancelBond(BluetoothDevice dev) {
        if (dev.getBondState() == BluetoothDevice.BOND_BONDING) {
            byte[] addr = Utils.getBytesFromAddress(dev.getAddress());
            if (!mAdapterService.cancelBondNative(addr)) {
               Log.e(TAG, "Unexpected error while cancelling bond:");
            } else {
                return true;
            }
        }
        return false;
    }

    private boolean removeBond(BluetoothDevice dev, boolean transition) {
        if (dev.getBondState() == BluetoothDevice.BOND_BONDED) {
            byte[] addr = Utils.getBytesFromAddress(dev.getAddress());
            if (!mAdapterService.removeBondNative(addr)) {
               Log.e(TAG, "Unexpected error while removing bond:");
            } else {
                if (transition) transitionTo(mPendingCommandState);
                return true;
            }

        }
        return false;
    }

    private boolean createBond(BluetoothDevice dev, int transport, OobData oobData,
                               boolean transition) {
        if (dev.getBondState() == BluetoothDevice.BOND_NONE) {
            infoLog("Bond address is:" + dev);
            byte[] addr = Utils.getBytesFromAddress(dev.getAddress());
            boolean result;
            if (oobData != null) {
                result = mAdapterService.createBondOutOfBandNative(addr, transport, oobData);
            } else {
                result = mAdapterService.createBondNative(addr, transport);
            }

            if (!result) {
                sendIntent(dev, BluetoothDevice.BOND_NONE,
                           BluetoothDevice.UNBOND_REASON_REMOVED);
                return false;
            } else if (transition) {
                transitionTo(mPendingCommandState);
            }
            return true;
        }
        return false;
    }

    private void sendDisplayPinIntent(byte[] address, int pin, int variant) {
        Intent intent = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevices.getDevice(address));
        if (pin != 0) {
            intent.putExtra(BluetoothDevice.EXTRA_PAIRING_KEY, pin);
        }
        intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, variant);
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        // Workaround for Android Auto until pre-accepting pairing requests is added.
        intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        mAdapterService.sendOrderedBroadcast(intent, mAdapterService.BLUETOOTH_ADMIN_PERM);
    }

    private void sendIntent(BluetoothDevice device, int newState, int reason) {
        DeviceProperties devProp = mRemoteDevices.getDeviceProperties(device);
        int oldState = BluetoothDevice.BOND_NONE;
        if (devProp != null) {
            oldState = devProp.getBondState();
        }
        if (oldState == newState) return;
        mAdapterProperties.onBondStateChanged(device, newState);

        Intent intent = new Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothDevice.EXTRA_BOND_STATE, newState);
        intent.putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, oldState);
        if (newState == BluetoothDevice.BOND_NONE)
            intent.putExtra(BluetoothDevice.EXTRA_REASON, reason);
        mAdapterService.sendBroadcastAsUser(intent, UserHandle.ALL,
                AdapterService.BLUETOOTH_PERM);
        infoLog("Bond State Change Intent:" + device + " OldState: " + oldState
                + " NewState: " + newState);
    }

    void bondStateChangeCallback(int status, byte[] address, int newState) {
        BluetoothDevice device = mRemoteDevices.getDevice(address);

        if (device == null) {
            infoLog("No record of the device:" + device);
            // This device will be added as part of the BONDING_STATE_CHANGE intent processing
            // in sendIntent above
            device = mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
        }

        infoLog("bondStateChangeCallback: Status: " + status + " Address: " + device
                + " newState: " + newState);

        Message msg = obtainMessage(BONDING_STATE_CHANGE);
        msg.obj = device;

        if (newState == BOND_STATE_BONDED)
            msg.arg1 = BluetoothDevice.BOND_BONDED;
        else if (newState == BOND_STATE_BONDING)
            msg.arg1 = BluetoothDevice.BOND_BONDING;
        else
            msg.arg1 = BluetoothDevice.BOND_NONE;
        msg.arg2 = status;

        sendMessage(msg);
    }

    void sspRequestCallback(byte[] address, byte[] name, int cod, int pairingVariant,
            int passkey) {
        //TODO(BT): Get wakelock and update name and cod
        BluetoothDevice bdDevice = mRemoteDevices.getDevice(address);
        if (bdDevice == null) {
            mRemoteDevices.addDeviceProperties(address);
        }
        infoLog("sspRequestCallback: " + address + " name: " + name + " cod: " +
                cod + " pairingVariant " + pairingVariant + " passkey: " + passkey);
        int variant;
        boolean displayPasskey = false;
        switch(pairingVariant) {

            case AbstractionLayer.BT_SSP_VARIANT_PASSKEY_CONFIRMATION :
                variant = BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION;
                displayPasskey = true;
            break;

            case AbstractionLayer.BT_SSP_VARIANT_CONSENT :
                variant = BluetoothDevice.PAIRING_VARIANT_CONSENT;
            break;

            case AbstractionLayer.BT_SSP_VARIANT_PASSKEY_ENTRY :
                variant = BluetoothDevice.PAIRING_VARIANT_PASSKEY;
            break;

            case AbstractionLayer.BT_SSP_VARIANT_PASSKEY_NOTIFICATION :
                variant = BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY;
                displayPasskey = true;
            break;

            default:
                errorLog("SSP Pairing variant not present");
                return;
        }
        BluetoothDevice device = mRemoteDevices.getDevice(address);
        if (device == null) {
           warnLog("Device is not known for:" + Utils.getAddressStringFromByte(address));
           mRemoteDevices.addDeviceProperties(address);
           device = mRemoteDevices.getDevice(address);
        }

        Message msg = obtainMessage(SSP_REQUEST);
        msg.obj = device;
        if(displayPasskey)
            msg.arg1 = passkey;
        msg.arg2 = variant;
        sendMessage(msg);
    }

    void pinRequestCallback(byte[] address, byte[] name, int cod, boolean min16Digits) {
        //TODO(BT): Get wakelock and update name and cod

        BluetoothDevice bdDevice = mRemoteDevices.getDevice(address);
        if (bdDevice == null) {
            mRemoteDevices.addDeviceProperties(address);
        }
        infoLog("pinRequestCallback: " + address + " name:" + name + " cod:" +
                cod);

        Message msg = obtainMessage(PIN_REQUEST);
        msg.obj = bdDevice;
        msg.arg2 = min16Digits ? 1 : 0; // Use arg2 to pass the min16Digit boolean

        sendMessage(msg);
    }

    private void clearProfilePriority(BluetoothDevice device) {
        HidService hidService = HidService.getHidService();
        A2dpService a2dpService = A2dpService.getA2dpService();
        HeadsetService headsetService = HeadsetService.getHeadsetService();
        HeadsetClientService headsetClientService = HeadsetClientService.getHeadsetClientService();
        A2dpSinkService a2dpSinkService = A2dpSinkService.getA2dpSinkService();
        PbapClientService pbapClientService = PbapClientService.getPbapClientService();

        if (hidService != null)
            hidService.setPriority(device, BluetoothProfile.PRIORITY_UNDEFINED);
        if (a2dpService != null)
            a2dpService.setPriority(device, BluetoothProfile.PRIORITY_UNDEFINED);
        if (headsetService != null)
            headsetService.setPriority(device, BluetoothProfile.PRIORITY_UNDEFINED);
        if (headsetClientService != null)
            headsetClientService.setPriority(device, BluetoothProfile.PRIORITY_UNDEFINED);
        if (a2dpSinkService != null)
            a2dpSinkService.setPriority(device, BluetoothProfile.PRIORITY_UNDEFINED);
        if (pbapClientService != null)
            pbapClientService.setPriority(device, BluetoothProfile.PRIORITY_UNDEFINED);

        // Clear Absolute Volume black list
        if(a2dpService != null)
            a2dpService.resetAvrcpBlacklist(device);
    }

    private void infoLog(String msg) {
        Log.i(TAG, msg);
    }

    private void errorLog(String msg) {
        Log.e(TAG, msg);
    }

    private void warnLog(String msg) {
        Log.w(TAG, msg);
    }

    private int getUnbondReasonFromHALCode (int reason) {
        if (reason == AbstractionLayer.BT_STATUS_SUCCESS)
            return BluetoothDevice.BOND_SUCCESS;
        else if (reason == AbstractionLayer.BT_STATUS_RMT_DEV_DOWN)
            return BluetoothDevice.UNBOND_REASON_REMOTE_DEVICE_DOWN;
        else if (reason == AbstractionLayer.BT_STATUS_AUTH_FAILURE)
            return BluetoothDevice.UNBOND_REASON_AUTH_FAILED;
        else if (reason == AbstractionLayer.BT_STATUS_AUTH_REJECTED)
            return BluetoothDevice.UNBOND_REASON_AUTH_REJECTED;
        else if (reason == AbstractionLayer.BT_STATUS_AUTH_TIMEOUT)
            return BluetoothDevice.UNBOND_REASON_AUTH_TIMEOUT;

        /* default */
        return BluetoothDevice.UNBOND_REASON_REMOVED;
    }
}
