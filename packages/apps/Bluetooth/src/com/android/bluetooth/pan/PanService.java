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

package com.android.bluetooth.pan;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothPan;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.net.ConnectivityManager;
import android.net.InterfaceConfiguration;
import android.net.LinkAddress;
import android.net.NetworkUtils;
import android.os.Handler;
import android.os.IBinder;
import android.os.Binder;
import android.os.INetworkManagementService;
import android.os.Message;
import android.os.ServiceManager;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.Utils;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Provides Bluetooth Pan Device profile, as a service in
 * the Bluetooth application.
 * @hide
 */
public class PanService extends ProfileService {
    private static final String TAG = "PanService";
    private static final boolean DBG = false;
    private static PanService sPanService;

    private static final String BLUETOOTH_IFACE_ADDR_START= "192.168.44.1";
    private static final int BLUETOOTH_MAX_PAN_CONNECTIONS = 5;
    private static final int BLUETOOTH_PREFIX_LENGTH        = 24;

    private HashMap<BluetoothDevice, BluetoothPanDevice> mPanDevices;
    private ArrayList<String> mBluetoothIfaceAddresses;
    private int mMaxPanDevices;
    private String mPanIfName;
    private String mNapIfaceAddr;
    private boolean mNativeAvailable;

    private static final int MESSAGE_CONNECT = 1;
    private static final int MESSAGE_DISCONNECT = 2;
    private static final int MESSAGE_CONNECT_STATE_CHANGED = 11;
    private boolean mTetherOn = false;

    private BluetoothTetheringNetworkFactory mNetworkFactory;


    static {
        classInitNative();
    }

    protected String getName() {
        return TAG;
    }

    public IProfileServiceBinder initBinder() {
        return new BluetoothPanBinder(this);
    }

    public static synchronized PanService getPanService() {
        if (sPanService != null && sPanService.isAvailable()) {
            if (DBG) {
                Log.d(TAG, "getPanService(): returning " + sPanService);
            }
            return sPanService;
        }
        if (DBG) {
            if (sPanService == null) {
                Log.d(TAG, "getPanService(): service is NULL");
            } else if (!sPanService.isAvailable()) {
                Log.d(TAG, "getPanService(): service is not available");
            }
        }
        return null;
    }

    private static synchronized void setPanService(PanService instance) {
        if (instance != null && instance.isAvailable()) {
            if (DBG) {
                Log.d(TAG, "setPanService(): set to: " + instance);
            }
            sPanService = instance;
        } else {
            if (DBG) {
                if (instance == null) {
                    Log.d(TAG, "setPanService(): service not available");
                } else if (!instance.isAvailable()) {
                    Log.d(TAG, "setPanService(): service is cleaning up");
                }
            }
        }
    }

    protected boolean start() {
        mPanDevices = new HashMap<BluetoothDevice, BluetoothPanDevice>();
        mBluetoothIfaceAddresses = new ArrayList<String>();
        try {
            mMaxPanDevices = getResources().getInteger(
                                 com.android.internal.R.integer.config_max_pan_devices);
        } catch (NotFoundException e) {
            mMaxPanDevices = BLUETOOTH_MAX_PAN_CONNECTIONS;
        }
        initializeNative();
        mNativeAvailable=true;

        mNetworkFactory = new BluetoothTetheringNetworkFactory(getBaseContext(), getMainLooper(),
                this);
        setPanService(this);

        return true;
    }

    protected boolean stop() {
        mHandler.removeCallbacksAndMessages(null);
        return true;
    }

    protected boolean cleanup() {
        if (mNativeAvailable) {
            cleanupNative();
            mNativeAvailable=false;
        }
        if(mPanDevices != null) {
            List<BluetoothDevice> DevList = getConnectedDevices();
            for(BluetoothDevice dev : DevList) {
                handlePanDeviceStateChange(dev, mPanIfName, BluetoothProfile.STATE_DISCONNECTED,
                        BluetoothPan.LOCAL_PANU_ROLE, BluetoothPan.REMOTE_NAP_ROLE);
            }
            mPanDevices.clear();
        }
        return true;
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_CONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    if (!connectPanNative(Utils.getByteAddress(device),
                            BluetoothPan.LOCAL_PANU_ROLE, BluetoothPan.REMOTE_NAP_ROLE)) {
                        handlePanDeviceStateChange(device, null, BluetoothProfile.STATE_CONNECTING,
                                BluetoothPan.LOCAL_PANU_ROLE, BluetoothPan.REMOTE_NAP_ROLE);
                        handlePanDeviceStateChange(device, null,
                                BluetoothProfile.STATE_DISCONNECTED, BluetoothPan.LOCAL_PANU_ROLE,
                                BluetoothPan.REMOTE_NAP_ROLE);
                        break;
                    }
                }
                    break;
                case MESSAGE_DISCONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    if (!disconnectPanNative(Utils.getByteAddress(device)) ) {
                        handlePanDeviceStateChange(device, mPanIfName,
                                BluetoothProfile.STATE_DISCONNECTING, BluetoothPan.LOCAL_PANU_ROLE,
                                BluetoothPan.REMOTE_NAP_ROLE);
                        handlePanDeviceStateChange(device, mPanIfName,
                                BluetoothProfile.STATE_DISCONNECTED, BluetoothPan.LOCAL_PANU_ROLE,
                                BluetoothPan.REMOTE_NAP_ROLE);
                        break;
                    }
                }
                    break;
                case MESSAGE_CONNECT_STATE_CHANGED:
                {
                    ConnectState cs = (ConnectState)msg.obj;
                    BluetoothDevice device = getDevice(cs.addr);
                    // TBD get iface from the msg
                    if (DBG) {
                        log("MESSAGE_CONNECT_STATE_CHANGED: " + device + " state: " + cs.state);
                    }
                    handlePanDeviceStateChange(device, mPanIfName /* iface */,
                            convertHalState(cs.state), cs.local_role,  cs.remote_role);
                }
                break;
            }
        }
    };

    /**
     * Handlers for incoming service calls
     */
    private static class BluetoothPanBinder extends IBluetoothPan.Stub
            implements IProfileServiceBinder {
        private PanService mService;
        public BluetoothPanBinder(PanService svc) {
            mService = svc;
        }
        public boolean cleanup() {
            mService = null;
            return true;
        }
        private PanService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG,"Pan call not allowed for non-active user");
                return null;
            }

            if (mService  != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }
        public boolean connect(BluetoothDevice device) {
            PanService service = getService();
            if (service == null) return false;
            return service.connect(device);
        }
        public boolean disconnect(BluetoothDevice device) {
            PanService service = getService();
            if (service == null) return false;
            return service.disconnect(device);
        }
        public int getConnectionState(BluetoothDevice device) {
            PanService service = getService();
            if (service == null) return BluetoothPan.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }
        private boolean isPanNapOn() {
            PanService service = getService();
            if (service == null) return false;
            return service.isPanNapOn();
        }
        private boolean isPanUOn() {
            if(DBG) Log.d(TAG, "isTetheringOn call getPanLocalRoleNative");
            PanService service = getService();
            if (service == null) return false;
            return service.isPanUOn();
        }
        public boolean isTetheringOn() {
            // TODO(BT) have a variable marking the on/off state
            PanService service = getService();
            if (service == null) return false;
            return service.isTetheringOn();
        }
        public void setBluetoothTethering(boolean value) {
            PanService service = getService();
            if (service == null) return;
            Log.d(TAG, "setBluetoothTethering: " + value +", mTetherOn: " + service.mTetherOn);
            service.setBluetoothTethering(value);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            PanService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            PanService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }
    };

    public boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (getConnectionState(device) != BluetoothProfile.STATE_DISCONNECTED) {
            Log.e(TAG, "Pan Device not disconnected: " + device);
            return false;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECT,device);
        mHandler.sendMessage(msg);
        return true;
    }

    public boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message msg = mHandler.obtainMessage(MESSAGE_DISCONNECT,device);
        mHandler.sendMessage(msg);
        return true;
    }

    public int getConnectionState(BluetoothDevice device) {
        BluetoothPanDevice panDevice = mPanDevices.get(device);
        if (panDevice == null) {
            return BluetoothPan.STATE_DISCONNECTED;
        }
        return panDevice.mState;
    }

    boolean isPanNapOn() {
        if(DBG) Log.d(TAG, "isTetheringOn call getPanLocalRoleNative");
        return (getPanLocalRoleNative() & BluetoothPan.LOCAL_NAP_ROLE) != 0;
    }
    boolean isPanUOn() {
        if(DBG) Log.d(TAG, "isTetheringOn call getPanLocalRoleNative");
        return (getPanLocalRoleNative() & BluetoothPan.LOCAL_PANU_ROLE) != 0;
    }
    public boolean isTetheringOn() {
        // TODO(BT) have a variable marking the on/off state
        return mTetherOn;
    }

    void setBluetoothTethering(boolean value) {
        if(DBG) Log.d(TAG, "setBluetoothTethering: " + value +", mTetherOn: " + mTetherOn);
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        final Context context = getBaseContext();
        String pkgName = context.getOpPackageName();

        // Clear caller identity temporarily so enforceTetherChangePermission UID checks work
        // correctly
        final long identityToken = Binder.clearCallingIdentity();
        try {
            ConnectivityManager.enforceTetherChangePermission(context, pkgName);
        } finally {
            Binder.restoreCallingIdentity(identityToken);
        }

        UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
        if (um.hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING)) {
            throw new SecurityException("DISALLOW_CONFIG_TETHERING is enabled for this user.");
        }
        if(mTetherOn != value) {
            //drop any existing panu or pan-nap connection when changing the tethering state
            mTetherOn = value;
            List<BluetoothDevice> DevList = getConnectedDevices();
            for(BluetoothDevice dev : DevList)
                disconnect(dev);
        }
    }

    public boolean setPriority(BluetoothDevice device, int priority) {
        if (device == null) {
            throw new IllegalArgumentException("Null device");
        }
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        Settings.Global.putInt(getContentResolver(),
                Settings.Global.getBluetoothPanPriorityKey(device.getAddress()),
                priority);
        if (DBG) {
            Log.d(TAG,"Saved priority " + device + " = " + priority);
        }
        return true;
    }

    public int getPriority(BluetoothDevice device) {
        if (device == null) throw new IllegalArgumentException("Null device");
        enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
        return Settings.Global.getInt(getContentResolver(),
                Settings.Global.getBluetoothPanPriorityKey(device.getAddress()),
                BluetoothProfile.PRIORITY_UNDEFINED);
    }

    public List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> devices = getDevicesMatchingConnectionStates(
                new int[] {BluetoothProfile.STATE_CONNECTED});
        return devices;
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
         enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> panDevices = new ArrayList<BluetoothDevice>();

        for (BluetoothDevice device: mPanDevices.keySet()) {
            int panDeviceState = getConnectionState(device);
            for (int state : states) {
                if (state == panDeviceState) {
                    panDevices.add(device);
                    break;
                }
            }
        }
        return panDevices;
    }

    static protected class ConnectState {
        public ConnectState(byte[] address, int state, int error, int local_role, int remote_role) {
            this.addr = address;
            this.state = state;
            this.error = error;
            this.local_role = local_role;
            this.remote_role = remote_role;
        }
        byte[] addr;
        int state;
        int error;
        int local_role;
        int remote_role;
    };
    private void onConnectStateChanged(byte[] address, int state, int error, int local_role,
            int remote_role) {
        if (DBG) {
            log("onConnectStateChanged: " + state + ", local role:" + local_role +
                    ", remote_role: " + remote_role);
        }
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECT_STATE_CHANGED);
        msg.obj = new ConnectState(address, state, error, local_role, remote_role);
        mHandler.sendMessage(msg);
    }
    private void onControlStateChanged(int local_role, int state, int error, String ifname) {
        if (DBG)
            log("onControlStateChanged: " + state + ", error: " + error + ", ifname: " + ifname);
        if(error == 0)
            mPanIfName = ifname;
    }

    private static int convertHalState(int halState) {
        switch (halState) {
            case CONN_STATE_CONNECTED:
                return BluetoothProfile.STATE_CONNECTED;
            case CONN_STATE_CONNECTING:
                return BluetoothProfile.STATE_CONNECTING;
            case CONN_STATE_DISCONNECTED:
                return BluetoothProfile.STATE_DISCONNECTED;
            case CONN_STATE_DISCONNECTING:
                return BluetoothProfile.STATE_DISCONNECTING;
            default:
                Log.e(TAG, "bad pan connection state: " + halState);
                return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    void handlePanDeviceStateChange(BluetoothDevice device,
                                    String iface, int state, int local_role, int remote_role) {
        if(DBG) {
            Log.d(TAG, "handlePanDeviceStateChange: device: " + device + ", iface: " + iface +
                    ", state: " + state + ", local_role:" + local_role + ", remote_role:" +
                    remote_role);
        }
        int prevState;

        BluetoothPanDevice panDevice = mPanDevices.get(device);
        if (panDevice == null) {
            Log.i(TAG, "state " + state + " Num of connected pan devices: " + mPanDevices.size());
            prevState = BluetoothProfile.STATE_DISCONNECTED;
            panDevice = new BluetoothPanDevice(state, iface, local_role);
            mPanDevices.put(device, panDevice);
        } else {
            prevState = panDevice.mState;
            panDevice.mState = state;
            panDevice.mLocalRole = local_role;
            panDevice.mIface = iface;
        }

        // Avoid race condition that gets this class stuck in STATE_DISCONNECTING. While we
        // are in STATE_CONNECTING, if a BluetoothPan#disconnect call comes in, the original
        // connect call will put us in STATE_DISCONNECTED. Then, the disconnect completes and
        // changes the state to STATE_DISCONNECTING. All future calls to BluetoothPan#connect
        // will fail until the caller explicitly calls BluetoothPan#disconnect.
        if (prevState == BluetoothProfile.STATE_DISCONNECTED && state == BluetoothProfile.STATE_DISCONNECTING) {
            Log.d(TAG, "Ignoring state change from " + prevState + " to " + state);
            mPanDevices.remove(device);
            return;
        }

        Log.d(TAG, "handlePanDeviceStateChange preState: " + prevState + " state: " + state);
        if (prevState == state) return;
        if (remote_role == BluetoothPan.LOCAL_PANU_ROLE) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                if ((!mTetherOn) || (local_role == BluetoothPan.LOCAL_PANU_ROLE)) {
                    Log.d(TAG, "handlePanDeviceStateChange BT tethering is off/Local role"
                            + " is PANU drop the connection");
                    mPanDevices.remove(device);
                    disconnectPanNative(Utils.getByteAddress(device));
                    return;
                }
                Log.d(TAG, "handlePanDeviceStateChange LOCAL_NAP_ROLE:REMOTE_PANU_ROLE");
                if (mNapIfaceAddr == null) {
                    mNapIfaceAddr = startTethering(iface);
                    if (mNapIfaceAddr == null) {
                        Log.e(TAG, "Error seting up tether interface");
                        mPanDevices.remove(device);
                        disconnectPanNative(Utils.getByteAddress(device));
                        return;
                    }
                }
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                mPanDevices.remove(device);
                Log.i(TAG, "remote(PANU) is disconnected, Remaining connected PANU devices: "
                        + mPanDevices.size());
                if (mNapIfaceAddr != null && mPanDevices.size() == 0) {
                    stopTethering(iface);
                    mNapIfaceAddr = null;
                }
            }
        } else if (mNetworkFactory != null) {
            // PANU Role = reverse Tether
            Log.d(TAG, "handlePanDeviceStateChange LOCAL_PANU_ROLE:REMOTE_NAP_ROLE state = " +
                    state + ", prevState = " + prevState);
            if (state == BluetoothProfile.STATE_CONNECTED) {
                mNetworkFactory.startReverseTether(iface);
           } else if (state == BluetoothProfile.STATE_DISCONNECTED &&
                   (prevState == BluetoothProfile.STATE_CONNECTED ||
                   prevState == BluetoothProfile.STATE_DISCONNECTING)) {
                mNetworkFactory.stopReverseTether();
                mPanDevices.remove(device);
            }
        }

        /* Notifying the connection state change of the profile before sending the intent for
           connection state change, as it was causing a race condition, with the UI not being
           updated with the correct connection state. */
        Log.d(TAG, "Pan Device state : device: " + device + " State:" +
                       prevState + "->" + state);
        Intent intent = new Intent(BluetoothPan.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothPan.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothPan.EXTRA_STATE, state);
        intent.putExtra(BluetoothPan.EXTRA_LOCAL_ROLE, local_role);
        sendBroadcast(intent, BLUETOOTH_PERM);
    }

    private String startTethering(String iface) {
        return configureBtIface(true, iface);
    }

    private String stopTethering(String iface) {
        return configureBtIface(false, iface);
    }

    private String configureBtIface(boolean enable, String iface) {
        Log.i(TAG, "configureBtIface: " + iface + " enable: " + enable);

        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        INetworkManagementService service = INetworkManagementService.Stub.asInterface(b);
        ConnectivityManager cm =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        String[] bluetoothRegexs = cm.getTetherableBluetoothRegexs();

        // bring toggle the interfaces
        String[] currentIfaces = new String[0];
        try {
            currentIfaces = service.listInterfaces();
        } catch (Exception e) {
            Log.e(TAG, "Error listing Interfaces :" + e);
            return null;
        }

        boolean found = false;
        for (String currIface: currentIfaces) {
            if (currIface.equals(iface)) {
                found = true;
                break;
            }
        }

        if (!found) return null;

        InterfaceConfiguration ifcg = null;
        String address = null;
        try {
            ifcg = service.getInterfaceConfig(iface);
            if (ifcg != null) {
                InetAddress addr = null;
                LinkAddress linkAddr = ifcg.getLinkAddress();
                if (linkAddr == null || (addr = linkAddr.getAddress()) == null ||
                        addr.equals(NetworkUtils.numericToInetAddress("0.0.0.0")) ||
                        addr.equals(NetworkUtils.numericToInetAddress("::0"))) {
                    address = BLUETOOTH_IFACE_ADDR_START;
                    addr = NetworkUtils.numericToInetAddress(address);
                }

                ifcg.setLinkAddress(new LinkAddress(addr, BLUETOOTH_PREFIX_LENGTH));
                if (enable) {
                    ifcg.setInterfaceUp();
                } else {
                    ifcg.setInterfaceDown();
                }

                ifcg.clearFlag("running");
                service.setInterfaceConfig(iface, ifcg);

                if (enable) {
                    int tetherStatus = cm.tether(iface);
                    if (tetherStatus != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                        Log.e(TAG, "Error tethering "+ iface + " tetherStatus: " + tetherStatus);
                        return null;
                    }
                } else {
                    int untetherStatus = cm.untether(iface);
                    Log.i(TAG, "Untethered: "+ iface + " untetherStatus: " + untetherStatus);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error configuring interface " + iface + ", :" + e);
            return null;
        }
        return address;
    }

    private List<BluetoothDevice> getConnectedPanDevices() {
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();

        for (BluetoothDevice device: mPanDevices.keySet()) {
            if (getPanDeviceConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
                devices.add(device);
            }
        }
        return devices;
    }

    private int getPanDeviceConnectionState(BluetoothDevice device) {
        BluetoothPanDevice panDevice = mPanDevices.get(device);
        if (panDevice == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        return panDevice.mState;
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        println(sb, "mMaxPanDevices: " + mMaxPanDevices);
        println(sb, "mPanIfName: " + mPanIfName);
        println(sb, "mTetherOn: " + mTetherOn);
        println(sb, "mPanDevices:");
        for (BluetoothDevice device : mPanDevices.keySet()) {
            println(sb, "  " + device + " : " + mPanDevices.get(device));
        }
    }

    private class BluetoothPanDevice {
        private int mState;
        private String mIface;
        private int mLocalRole; // Which local role is this PAN device bound to

        BluetoothPanDevice(int state, String iface, int localRole) {
            mState = state;
            mIface = iface;
            mLocalRole = localRole;
        }
    }

    // Constants matching Hal header file bt_hh.h
    // bthh_connection_state_t
    private final static int CONN_STATE_CONNECTED = 0;
    private final static int CONN_STATE_CONNECTING = 1;
    private final static int CONN_STATE_DISCONNECTED = 2;
    private final static int CONN_STATE_DISCONNECTING = 3;

    private native static void classInitNative();
    private native void initializeNative();
    private native void cleanupNative();
    private native boolean connectPanNative(byte[] btAddress, int local_role, int remote_role);
    private native boolean disconnectPanNative(byte[] btAddress);
    private native boolean enablePanNative(int local_role);
    private native int getPanLocalRoleNative();

}
