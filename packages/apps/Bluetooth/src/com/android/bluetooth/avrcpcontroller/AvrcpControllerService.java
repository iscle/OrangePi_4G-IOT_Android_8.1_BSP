/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.bluetooth.avrcpcontroller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAvrcpPlayerSettings;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothAvrcpController;
import android.content.Context;
import android.media.AudioManager;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.ProfileService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provides Bluetooth AVRCP Controller profile, as a service in the Bluetooth application.
 */
public class AvrcpControllerService extends ProfileService {
    static final String TAG = "AvrcpControllerService";
    static final boolean DBG = true;
    static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);
    /*
     *  Play State Values from JNI
     */
    private static final byte JNI_PLAY_STATUS_STOPPED = 0x00;
    private static final byte JNI_PLAY_STATUS_PLAYING = 0x01;
    private static final byte JNI_PLAY_STATUS_PAUSED  = 0x02;
    private static final byte JNI_PLAY_STATUS_FWD_SEEK = 0x03;
    private static final byte JNI_PLAY_STATUS_REV_SEEK = 0x04;
    private static final byte JNI_PLAY_STATUS_ERROR    = -1;

    /*
     * Browsing Media Item Attribute IDs
     * This should be kept in sync with BTRC_MEDIA_ATTR_ID_* in bt_rc.h
     */
    private static final int JNI_MEDIA_ATTR_ID_INVALID = -1;
    private static final int JNI_MEDIA_ATTR_ID_TITLE = 0x00000001;
    private static final int JNI_MEDIA_ATTR_ID_ARTIST = 0x00000002;
    private static final int JNI_MEDIA_ATTR_ID_ALBUM = 0x00000003;
    private static final int JNI_MEDIA_ATTR_ID_TRACK_NUM = 0x00000004;
    private static final int JNI_MEDIA_ATTR_ID_NUM_TRACKS = 0x00000005;
    private static final int JNI_MEDIA_ATTR_ID_GENRE = 0x00000006;
    private static final int JNI_MEDIA_ATTR_ID_PLAYING_TIME = 0x00000007;

    /*
     * Browsing folder types
     * This should be kept in sync with BTRC_FOLDER_TYPE_* in bt_rc.h
     */
    private static final int JNI_FOLDER_TYPE_TITLES = 0x01;
    private static final int JNI_FOLDER_TYPE_ALBUMS = 0x02;
    private static final int JNI_FOLDER_TYPE_ARTISTS = 0x03;
    private static final int JNI_FOLDER_TYPE_GENRES = 0x04;
    private static final int JNI_FOLDER_TYPE_PLAYLISTS = 0x05;
    private static final int JNI_FOLDER_TYPE_YEARS = 0x06;

    /*
     * AVRCP Error types as defined in spec. Also they should be in sync with btrc_status_t.
     * NOTE: Not all may be defined.
     */
    private static final int JNI_AVRC_STS_NO_ERROR = 0x04;
    private static final int JNI_AVRC_INV_RANGE = 0x0b;

    /**
     * Intent used to broadcast the change in browse connection state of the AVRCP Controller
     * profile.
     *
     * <p>This intent will have 2 extras:
     * <ul>
     *   <li> {@link BluetoothProfile#EXTRA_STATE} - The current state of the profile. </li>
     *   <li> {@link BluetoothDevice#EXTRA_DEVICE} - The remote device. </li>
     * </ul>
     *
     * <p>{@link #EXTRA_STATE} can be any of
     * {@link #STATE_DISCONNECTED}, {@link #STATE_CONNECTING},
     * {@link #STATE_CONNECTED}, {@link #STATE_DISCONNECTING}.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission to
     * receive.
     */
    public static final String ACTION_BROWSE_CONNECTION_STATE_CHANGED =
        "android.bluetooth.avrcp-controller.profile.action.BROWSE_CONNECTION_STATE_CHANGED";

    /**
     * intent used to broadcast the change in metadata state of playing track on the avrcp
     * ag.
     *
     * <p>this intent will have the two extras:
     * <ul>
     *    <li> {@link #extra_metadata} - {@link mediametadata} containing the current metadata.</li>
     *    <li> {@link #extra_playback} - {@link playbackstate} containing the current playback
     *    state. </li>
     * </ul>
     */
    public static final String ACTION_TRACK_EVENT =
        "android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT";

    /**
     * Intent used to broadcast the change of folder list.
     *
     * <p>This intent will have the one extra:
     * <ul>
     *    <li> {@link #EXTRA_FOLDER_LIST} - array of {@link MediaBrowser#MediaItem}
     *    containing the folder listing of currently selected folder.
     * </ul>
     */
    public static final String ACTION_FOLDER_LIST =
        "android.bluetooth.avrcp-controller.profile.action.FOLDER_LIST";

    public static final String EXTRA_FOLDER_LIST =
            "android.bluetooth.avrcp-controller.profile.extra.FOLDER_LIST";

    public static final String EXTRA_FOLDER_ID = "com.android.bluetooth.avrcp.EXTRA_FOLDER_ID";
    public static final String EXTRA_FOLDER_BT_ID =
        "com.android.bluetooth.avrcp-controller.EXTRA_FOLDER_BT_ID";

    public static final String EXTRA_METADATA =
            "android.bluetooth.avrcp-controller.profile.extra.METADATA";

    public static final String EXTRA_PLAYBACK =
            "android.bluetooth.avrcp-controller.profile.extra.PLAYBACK";

    public static final String MEDIA_ITEM_UID_KEY = "media-item-uid-key";

    /*
     * KeyCoded for Pass Through Commands
     */
    public static final int PASS_THRU_CMD_ID_PLAY = 0x44;
    public static final int PASS_THRU_CMD_ID_PAUSE = 0x46;
    public static final int PASS_THRU_CMD_ID_VOL_UP = 0x41;
    public static final int PASS_THRU_CMD_ID_VOL_DOWN = 0x42;
    public static final int PASS_THRU_CMD_ID_STOP = 0x45;
    public static final int PASS_THRU_CMD_ID_FF = 0x49;
    public static final int PASS_THRU_CMD_ID_REWIND = 0x48;
    public static final int PASS_THRU_CMD_ID_FORWARD = 0x4B;
    public static final int PASS_THRU_CMD_ID_BACKWARD = 0x4C;

    /* Key State Variables */
    public static final int KEY_STATE_PRESSED = 0;
    public static final int KEY_STATE_RELEASED = 1;

    /* Group Navigation Key Codes */
    public static final int PASS_THRU_CMD_ID_NEXT_GRP = 0x00;
    public static final int PASS_THRU_CMD_ID_PREV_GRP = 0x01;

    /* Folder navigation directions
     * This is borrowed from AVRCP 1.6 spec and must be kept with same values
     */
    public static final int FOLDER_NAVIGATION_DIRECTION_UP = 0x00;
    public static final int FOLDER_NAVIGATION_DIRECTION_DOWN = 0x01;

    /* Folder/Media Item scopes.
     * Keep in sync with AVRCP 1.6 sec. 6.10.1
     */
    public static final int BROWSE_SCOPE_PLAYER_LIST = 0x00;
    public static final int BROWSE_SCOPE_VFS = 0x01;
    public static final int BROWSE_SCOPE_SEARCH = 0x02;
    public static final int BROWSE_SCOPE_NOW_PLAYING = 0x03;

    private AvrcpControllerStateMachine mAvrcpCtSm;
    private static AvrcpControllerService sAvrcpControllerService;
    // UID size is 8 bytes (AVRCP 1.6 spec)
    private static final byte[] EMPTY_UID = {0, 0, 0, 0, 0, 0, 0, 0};

    // We only support one device.
    private BluetoothDevice mConnectedDevice = null;
    // If browse is supported (only valid if mConnectedDevice != null).
    private boolean mBrowseConnected = false;
    // Caches the current browse folder. If this is null then root is the currently browsed folder
    // (which also has no UID).
    private String mCurrentBrowseFolderUID = null;

    static {
        classInitNative();
    }

    public AvrcpControllerService() {
        initNative();
    }

    protected String getName() {
        return TAG;
    }

    protected IProfileServiceBinder initBinder() {
        return new BluetoothAvrcpControllerBinder(this);
    }

    protected boolean start() {
        HandlerThread thread = new HandlerThread("BluetoothAvrcpHandler");
        thread.start();
        mAvrcpCtSm = new AvrcpControllerStateMachine(this);
        mAvrcpCtSm.start();

        setAvrcpControllerService(this);
        return true;
    }

    protected boolean stop() {
        if (mAvrcpCtSm != null) {
            mAvrcpCtSm.doQuit();
        }
        return true;
    }

    //API Methods

    public static synchronized AvrcpControllerService getAvrcpControllerService() {
        if (sAvrcpControllerService != null && sAvrcpControllerService.isAvailable()) {
            if (DBG) {
                Log.d(TAG, "getAvrcpControllerService(): returning "
                    + sAvrcpControllerService);
            }
            return sAvrcpControllerService;
        }
        if (DBG) {
            if (sAvrcpControllerService == null) {
                Log.d(TAG, "getAvrcpControllerService(): service is NULL");
            } else if (!(sAvrcpControllerService.isAvailable())) {
                Log.d(TAG, "getAvrcpControllerService(): service is not available");
            }
        }
        return null;
    }

    private static synchronized void setAvrcpControllerService(AvrcpControllerService instance) {
        if (instance != null && instance.isAvailable()) {
            if (DBG) {
                Log.d(TAG, "setAvrcpControllerService(): set to: " + sAvrcpControllerService);
            }
            sAvrcpControllerService = instance;
        } else {
            if (DBG) {
                if (instance == null) {
                    Log.d(TAG, "setAvrcpControllerService(): service not available");
                } else if (!instance.isAvailable()) {
                    Log.d(TAG, "setAvrcpControllerService(): service is cleaning up");
                }
            }
        }
    }

    private static synchronized void clearAvrcpControllerService() {
        sAvrcpControllerService = null;
    }

    public synchronized List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        if (mConnectedDevice != null) {
            devices.add(mConnectedDevice);
        }
        return devices;
    }

    /**
     * This function only supports STATE_CONNECTED
     */
    public synchronized List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        for (int i = 0; i < states.length; i++) {
            if (states[i] == BluetoothProfile.STATE_CONNECTED && mConnectedDevice != null) {
                devices.add(mConnectedDevice);
            }
        }
        return devices;
    }

    public synchronized int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return (mConnectedDevice != null ? BluetoothProfile.STATE_CONNECTED :
            BluetoothProfile.STATE_DISCONNECTED);
    }

    public synchronized void sendGroupNavigationCmd(BluetoothDevice device, int keyCode, int keyState) {
        Log.v(TAG, "sendGroupNavigationCmd keyCode: " + keyCode + " keyState: " + keyState);
        if (device == null) {
            Log.e(TAG, "sendGroupNavigationCmd device is null");
        }

        if (!(device.equals(mConnectedDevice))) {
            Log.e(TAG, " Device does not match " + device + " connected " + mConnectedDevice);
            return;
        }
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message msg = mAvrcpCtSm.obtainMessage(AvrcpControllerStateMachine.
            MESSAGE_SEND_GROUP_NAVIGATION_CMD, keyCode, keyState, device);
        mAvrcpCtSm.sendMessage(msg);
    }

    public synchronized void sendPassThroughCmd(BluetoothDevice device, int keyCode, int keyState) {
        Log.v(TAG, "sendPassThroughCmd keyCode: " + keyCode + " keyState: " + keyState);
        if (device == null) {
            Log.e(TAG, "sendPassThroughCmd Device is null");
            return;
        }

        if (!device.equals(mConnectedDevice)) {
            Log.w(TAG, " Device does not match device " + device + " conn " + mConnectedDevice);
            return;
        }

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Message msg = mAvrcpCtSm
            .obtainMessage(AvrcpControllerStateMachine.MESSAGE_SEND_PASS_THROUGH_CMD,
                keyCode, keyState, device);
        mAvrcpCtSm.sendMessage(msg);
    }

    public void startAvrcpUpdates() {
        mAvrcpCtSm.obtainMessage(
            AvrcpControllerStateMachine.MESSAGE_START_METADATA_BROADCASTS).sendToTarget();
    }

    public void stopAvrcpUpdates() {
        mAvrcpCtSm.obtainMessage(
            AvrcpControllerStateMachine.MESSAGE_STOP_METADATA_BROADCASTS).sendToTarget();
    }

    public synchronized MediaMetadata getMetaData(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "getMetaData");
        }
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (device == null) {
            Log.e(TAG, "getMetadata device is null");
            return null;
        }

        if (!device.equals(mConnectedDevice)) {
            return null;
        }
        return mAvrcpCtSm.getCurrentMetaData();
    }

    public PlaybackState getPlaybackState(BluetoothDevice device) {
        // Get the cached state by default.
        return getPlaybackState(device, true);
    }

    // cached can be used to force a getPlaybackState command. Useful for PTS testing.
    public synchronized PlaybackState getPlaybackState(BluetoothDevice device, boolean cached) {
        if (DBG) {
            Log.d(TAG, "getPlayBackState device = " + device);
        }

        if (device == null) {
            Log.e(TAG, "getPlaybackState device is null");
            return null;
        }

        if (!device.equals(mConnectedDevice)) {
            Log.e(TAG, "Device " + device + " does not match connected deivce " + mConnectedDevice);
            return null;

        }
        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return mAvrcpCtSm.getCurrentPlayBackState(cached);
    }

    public synchronized BluetoothAvrcpPlayerSettings getPlayerSettings(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "getPlayerApplicationSetting ");
        }

        if (device == null) {
            Log.e(TAG, "getPlayerSettings device is null");
            return null;
        }

        if (!device.equals(mConnectedDevice)) {
            Log.e(TAG, "device " + device + " does not match connected device " + mConnectedDevice);
            return null;
        }

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        /* Do nothing */
        return null;
    }

    public boolean setPlayerApplicationSetting(BluetoothAvrcpPlayerSettings plAppSetting) {
        if (DBG) {
            Log.d(TAG, "getPlayerApplicationSetting");
        }

        /* Do nothing */
        return false;
    }

    /**
     * Fetches the list of children for the parentID node.
     *
     * This function manages the overall tree for browsing structure.
     *
     * Arguments:
     * device - Device to browse content for.
     * parentMediaId - ID of the parent that we need to browse content for. Since most
     * of the players are database unware, fetching a root invalidates all the children.
     * start - number of item to start scanning from
     * items - number of items to fetch
     */
    public synchronized boolean getChildren(
            BluetoothDevice device, String parentMediaId, int start, int items) {
        if (DBG) {
            Log.d(TAG, "getChildren device = " + device + " parent " + parentMediaId);
        }

        if (device == null) {
            Log.e(TAG, "getChildren device is null");
            return false;
        }

        if (!device.equals(mConnectedDevice)) {
            Log.e(TAG, "getChildren device " + device + " does not match " +
                mConnectedDevice);
            return false;
        }

        if (!mBrowseConnected) {
            Log.e(TAG, "getChildren browse not yet connected");
            return false;
        }

        if (!mAvrcpCtSm.isConnected()) {
            return false;
        }
        mAvrcpCtSm.getChildren(parentMediaId, start, items);
        return true;
    }

    public synchronized boolean getNowPlayingList(
            BluetoothDevice device, String id, int start, int items) {
        if (DBG) {
            Log.d(TAG, "getNowPlayingList device = " + device + " start = " + start +
                "items = " + items);
        }

        if (device == null) {
            Log.e(TAG, "getNowPlayingList device is null");
            return false;
        }

        if (!device.equals(mConnectedDevice)) {
            Log.e(TAG, "getNowPlayingList device " + device + " does not match " +
                mConnectedDevice);
            return false;
        }

        if (!mBrowseConnected) {
            Log.e(TAG, "getNowPlayingList browse not yet connected");
            return false;
        }

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        Message msg = mAvrcpCtSm.obtainMessage(
            AvrcpControllerStateMachine.MESSAGE_GET_NOW_PLAYING_LIST, start, items, id);
        mAvrcpCtSm.sendMessage(msg);
        return true;
    }

    public synchronized boolean getFolderList(
            BluetoothDevice device, String id, int start, int items) {
        if (DBG) {
            Log.d(TAG, "getFolderListing device = " + device + " start = " + start +
                "items = " + items);
        }

        if (device == null) {
            Log.e(TAG, "getFolderListing device is null");
            return false;
        }

        if (!device.equals(mConnectedDevice)) {
            Log.e(TAG, "getFolderListing device " + device + " does not match " + mConnectedDevice);
            return false;
        }

        if (!mBrowseConnected) {
            Log.e(TAG, "getFolderListing browse not yet connected");
            return false;
        }

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        Message msg = mAvrcpCtSm.obtainMessage(
            AvrcpControllerStateMachine.MESSAGE_GET_FOLDER_LIST, start, items, id);
        mAvrcpCtSm.sendMessage(msg);
        return true;
    }

    public synchronized boolean getPlayerList(BluetoothDevice device, int start, int items) {
        if (DBG) {
            Log.d(TAG, "getPlayerList device = " + device + " start = " + start +
                "items = " + items);
        }

        if (device == null) {
            Log.e(TAG, "getPlayerList device is null");
            return false;
        }

        if (!device.equals(mConnectedDevice)) {
            Log.e(TAG, "getPlayerList device " + device + " does not match " + mConnectedDevice);
            return false;
        }

        if (!mBrowseConnected) {
            Log.e(TAG, "getPlayerList browse not yet connected");
            return false;
        }

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        Message msg = mAvrcpCtSm.obtainMessage(
            AvrcpControllerStateMachine.MESSAGE_GET_PLAYER_LIST, start, items);
        mAvrcpCtSm.sendMessage(msg);
        return true;
    }

    public synchronized boolean changeFolderPath(
            BluetoothDevice device, int direction, String uid, String fid) {
        if (DBG) {
            Log.d(TAG, "changeFolderPath device = " + device + " direction " +
                direction + " uid " + uid);
        }

        if (device == null) {
            Log.e(TAG, "changeFolderPath device is null");
            return false;
        }

        if (!device.equals(mConnectedDevice)) {
            Log.e(TAG, "changeFolderPath device " + device + " does not match " +
                mConnectedDevice);
            return false;
        }

        if (!mBrowseConnected) {
            Log.e(TAG, "changeFolderPath browse not yet connected");
            return false;
        }

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        Bundle b = new Bundle();
        b.putString(EXTRA_FOLDER_ID, fid);
        b.putString(EXTRA_FOLDER_BT_ID, uid);
        Message msg = mAvrcpCtSm.obtainMessage(
            AvrcpControllerStateMachine.MESSAGE_CHANGE_FOLDER_PATH, direction, 0, b);
        mAvrcpCtSm.sendMessage(msg);
        return true;
    }

    public synchronized boolean setBrowsedPlayer(BluetoothDevice device, int id, String fid) {
        if (DBG) {
            Log.d(TAG, "setBrowsedPlayer device = " + device + " id" + id + " fid " + fid);
        }

        if (device == null) {
            Log.e(TAG, "setBrowsedPlayer device is null");
            return false;
        }

        if (!device.equals(mConnectedDevice)) {
            Log.e(TAG, "changeFolderPath device " + device + " does not match " +
                mConnectedDevice);
            return false;
        }

        if (!mBrowseConnected) {
            Log.e(TAG, "setBrowsedPlayer browse not yet connected");
            return false;
        }

        enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");

        Message msg = mAvrcpCtSm.obtainMessage(
            AvrcpControllerStateMachine.MESSAGE_SET_BROWSED_PLAYER, id, 0, fid);
        mAvrcpCtSm.sendMessage(msg);
        return true;
    }

    public synchronized void fetchAttrAndPlayItem(BluetoothDevice device, String uid) {
        if (DBG) {
            Log.d(TAG, "fetchAttrAndPlayItem device = " + device + " uid " + uid);
        }

        if (device == null) {
            Log.e(TAG, "fetchAttrAndPlayItem device is null");
            return;
        }

        if (!device.equals(mConnectedDevice)) {
            Log.e(TAG, "fetchAttrAndPlayItem device " + device + " does not match " +
                mConnectedDevice);
            return;
        }

        if (!mBrowseConnected) {
            Log.e(TAG, "fetchAttrAndPlayItem browse not yet connected");
            return;
        }
        mAvrcpCtSm.fetchAttrAndPlayItem(uid);
    }

    //Binder object: Must be static class or memory leak may occur
    private static class BluetoothAvrcpControllerBinder extends IBluetoothAvrcpController.Stub
        implements IProfileServiceBinder {

        private AvrcpControllerService mService;

        private AvrcpControllerService getService() {
            if (!Utils.checkCaller()) {
                Log.w(TAG, "AVRCP call not allowed for non-active user");
                return null;
            }

            if (mService != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }

        BluetoothAvrcpControllerBinder(AvrcpControllerService svc) {
            mService = svc;
        }

        public boolean cleanup() {
            mService = null;
            return true;
        }

        @Override
        public List<BluetoothDevice> getConnectedDevices() {
            AvrcpControllerService service = getService();
            if (service == null) {
                return new ArrayList<BluetoothDevice>(0);
            }
            return service.getConnectedDevices();
        }

        @Override
        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            AvrcpControllerService service = getService();
            if (service == null) {
                return new ArrayList<BluetoothDevice>(0);
            }
            return service.getDevicesMatchingConnectionStates(states);
        }

        @Override
        public int getConnectionState(BluetoothDevice device) {
            AvrcpControllerService service = getService();
            if (service == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }

            if (device == null) {
              throw new IllegalStateException("Device cannot be null!");
            }

            return service.getConnectionState(device);
        }

        @Override
        public void sendGroupNavigationCmd(BluetoothDevice device, int keyCode, int keyState) {
            Log.v(TAG, "Binder Call: sendGroupNavigationCmd");
            AvrcpControllerService service = getService();
            if (service == null) {
                return;
            }

            if (device == null) {
              throw new IllegalStateException("Device cannot be null!");
            }

            service.sendGroupNavigationCmd(device, keyCode, keyState);
        }

        @Override
        public BluetoothAvrcpPlayerSettings getPlayerSettings(BluetoothDevice device) {
            Log.v(TAG, "Binder Call: getPlayerApplicationSetting ");
            AvrcpControllerService service = getService();
            if (service == null) {
                return null;
            }

            if (device == null) {
              throw new IllegalStateException("Device cannot be null!");
            }

            return service.getPlayerSettings(device);
        }

        @Override
        public boolean setPlayerApplicationSetting(BluetoothAvrcpPlayerSettings plAppSetting) {
            Log.v(TAG, "Binder Call: setPlayerApplicationSetting ");
            AvrcpControllerService service = getService();
            if (service == null) {
                return false;
            }
            return service.setPlayerApplicationSetting(plAppSetting);
        }
    }

    // Called by JNI when a passthrough key was received.
    private void handlePassthroughRsp(int id, int keyState, byte[] address) {
        Log.d(TAG, "passthrough response received as: key: " + id + " state: " + keyState +
            "address:" + address);
    }

    private void handleGroupNavigationRsp(int id, int keyState) {
        Log.d(TAG, "group navigation response received as: key: " + id + " state: " + keyState);
    }

    // Called by JNI when a device has connected or disconnected.
    private synchronized void onConnectionStateChanged(
            boolean rc_connected, boolean br_connected, byte[] address) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        Log.d(TAG, "onConnectionStateChanged " + rc_connected + " " + br_connected +
            device + " conn device " + mConnectedDevice);
        if (device == null) {
            Log.e(TAG, "onConnectionStateChanged Device is null");
            return;
        }

        // Adjust the AVRCP connection state.
        int oldState = (device.equals(mConnectedDevice) ? BluetoothProfile.STATE_CONNECTED :
            BluetoothProfile.STATE_DISCONNECTED);
        int newState = (rc_connected ? BluetoothProfile.STATE_CONNECTED :
            BluetoothProfile.STATE_DISCONNECTED);

        if (rc_connected && oldState == BluetoothProfile.STATE_DISCONNECTED) {
            /* AVRCPControllerService supports single connection */
            if (mConnectedDevice != null) {
                Log.d(TAG, "A Connection already exists, returning");
                return;
            }
            mConnectedDevice = device;
            Message msg = mAvrcpCtSm.obtainMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_CONNECTION_CHANGE, newState,
                oldState, device);
            mAvrcpCtSm.sendMessage(msg);
        } else if (!rc_connected && oldState == BluetoothProfile.STATE_CONNECTED) {
            mConnectedDevice = null;
            Message msg = mAvrcpCtSm.obtainMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_CONNECTION_CHANGE, newState,
                oldState, device);
            mAvrcpCtSm.sendMessage(msg);
        }

        // Adjust the browse connection state. If RC is connected we should have already sent the
        // connection status out.
        if (rc_connected && br_connected) {
            mBrowseConnected = true;
            Message msg = mAvrcpCtSm.obtainMessage(
               AvrcpControllerStateMachine.MESSAGE_PROCESS_BROWSE_CONNECTION_CHANGE);
            msg.arg1 = 1;
            msg.obj = device;
            mAvrcpCtSm.sendMessage(msg);
        }
    }

    // Called by JNI to notify Avrcp of features supported by the Remote device.
    private void getRcFeatures(byte[] address, int features) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        Message msg = mAvrcpCtSm.obtainMessage(
            AvrcpControllerStateMachine.MESSAGE_PROCESS_RC_FEATURES, features, 0, device);
        mAvrcpCtSm.sendMessage(msg);
    }

    // Called by JNI
    private void setPlayerAppSettingRsp(byte[] address, byte accepted) {
              /* Do Nothing. */
    }

    // Called by JNI when remote wants to receive absolute volume notifications.
    private synchronized void handleRegisterNotificationAbsVol(byte[] address, byte label) {
        Log.d(TAG, "handleRegisterNotificationAbsVol ");
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (device != null && !device.equals(mConnectedDevice)) {
            Log.e(TAG, "handleRegisterNotificationAbsVol device not found " + address);
            return;
        }
        Message msg = mAvrcpCtSm.obtainMessage(AvrcpControllerStateMachine.
            MESSAGE_PROCESS_REGISTER_ABS_VOL_NOTIFICATION, (int) label, 0);
        mAvrcpCtSm.sendMessage(msg);
    }

    // Called by JNI when remote wants to set absolute volume.
    private synchronized void handleSetAbsVolume(byte[] address, byte absVol, byte label) {
        Log.d(TAG, "handleSetAbsVolume ");
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (device != null && !device.equals(mConnectedDevice)) {
            Log.e(TAG, "handleSetAbsVolume device not found " + address);
            return;
        }
        Message msg = mAvrcpCtSm.obtainMessage(
            AvrcpControllerStateMachine.MESSAGE_PROCESS_SET_ABS_VOL_CMD, absVol, label);
        mAvrcpCtSm.sendMessage(msg);
    }

    // Called by JNI when a track changes and local AvrcpController is registered for updates.
    private synchronized void onTrackChanged(byte[] address, byte numAttributes, int[] attributes,
        String[] attribVals) {
        if (DBG) {
            Log.d(TAG, "onTrackChanged");
        }
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (device != null && !device.equals(mConnectedDevice)) {
            Log.e(TAG, "onTrackChanged device not found " + address);
            return;
        }

        List<Integer> attrList = new ArrayList<>();
        for (int attr : attributes) {
            attrList.add(attr);
        }
        List<String> attrValList = Arrays.asList(attribVals);
        TrackInfo trackInfo = new TrackInfo(attrList, attrValList);
        if (DBG) {
            Log.d(TAG, "onTrackChanged " + trackInfo);
        }
        Message msg = mAvrcpCtSm.obtainMessage(AvrcpControllerStateMachine.
            MESSAGE_PROCESS_TRACK_CHANGED, trackInfo);
        mAvrcpCtSm.sendMessage(msg);
    }

    // Called by JNI periodically based upon timer to update play position
    private synchronized void onPlayPositionChanged(byte[] address, int songLen, int currSongPosition) {
        if (DBG) {
            Log.d(TAG, "onPlayPositionChanged pos " + currSongPosition);
        }
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (device != null && !device.equals(mConnectedDevice)) {
            Log.e(TAG, "onPlayPositionChanged not found device not found " + address);
            return;
        }
        Message msg = mAvrcpCtSm.obtainMessage(AvrcpControllerStateMachine.
            MESSAGE_PROCESS_PLAY_POS_CHANGED, songLen, currSongPosition);
        mAvrcpCtSm.sendMessage(msg);
    }

    // Called by JNI on changes of play status
    private synchronized void onPlayStatusChanged(byte[] address, byte playStatus) {
        if (DBG) {
            Log.d(TAG, "onPlayStatusChanged " + playStatus);
        }
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (device != null && !device.equals(mConnectedDevice)) {
            Log.e(TAG, "onPlayStatusChanged not found device not found " + address);
            return;
        }
        int playbackState = PlaybackState.STATE_NONE;
        switch (playStatus) {
            case JNI_PLAY_STATUS_STOPPED:
                playbackState =  PlaybackState.STATE_STOPPED;
                break;
            case JNI_PLAY_STATUS_PLAYING:
                playbackState =  PlaybackState.STATE_PLAYING;
                break;
            case JNI_PLAY_STATUS_PAUSED:
                playbackState = PlaybackState.STATE_PAUSED;
                break;
            case JNI_PLAY_STATUS_FWD_SEEK:
                playbackState = PlaybackState.STATE_FAST_FORWARDING;
                break;
            case JNI_PLAY_STATUS_REV_SEEK:
                playbackState = PlaybackState.STATE_FAST_FORWARDING;
                break;
            default:
                playbackState = PlaybackState.STATE_NONE;
        }
        Message msg = mAvrcpCtSm.obtainMessage(AvrcpControllerStateMachine.
            MESSAGE_PROCESS_PLAY_STATUS_CHANGED, playbackState);
        mAvrcpCtSm.sendMessage(msg);
    }

    // Called by JNI to report remote Player's capabilities
    private synchronized void handlePlayerAppSetting(byte[] address, byte[] playerAttribRsp, int rspLen) {
        if (DBG) {
            Log.d(TAG, "handlePlayerAppSetting rspLen = " + rspLen);
        }
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (device != null && !device.equals(mConnectedDevice)) {
            Log.e(TAG, "handlePlayerAppSetting not found device not found " + address);
            return;
        }
        PlayerApplicationSettings supportedSettings = PlayerApplicationSettings.
            makeSupportedSettings(playerAttribRsp);
        /* Do nothing */
    }

    private synchronized void onPlayerAppSettingChanged(byte[] address, byte[] playerAttribRsp, int rspLen) {
        if (DBG) {
            Log.d(TAG, "onPlayerAppSettingChanged ");
        }
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (device != null && !device.equals(mConnectedDevice)) {
            Log.e(TAG, "onPlayerAppSettingChanged not found device not found " + address);
            return;
        }
        PlayerApplicationSettings desiredSettings = PlayerApplicationSettings.
            makeSettings(playerAttribRsp);
        /* Do nothing */
    }

    // Browsing related JNI callbacks.
    void handleGetFolderItemsRsp(int status, MediaItem[] items) {
        if (DBG) {
            Log.d(TAG, "handleGetFolderItemsRsp called with status " + status +
                " items "  + items.length + " items.");
        }

        if (status == JNI_AVRC_INV_RANGE) {
            Log.w(TAG, "Sending out of range message.");
            // Send a special message since this could be used by state machine
            // to take as a signal that fetch is finished.
            Message msg = mAvrcpCtSm.obtainMessage(AvrcpControllerStateMachine.
                MESSAGE_PROCESS_GET_FOLDER_ITEMS_OUT_OF_RANGE);
            mAvrcpCtSm.sendMessage(msg);
            return;
        }

        for (MediaItem item : items) {
            if (DBG) {
                Log.d(TAG, "media item: " + item + " uid: " + item.getDescription().getMediaId());
            }
        }
        ArrayList<MediaItem> itemsList = new ArrayList<>();
        for (MediaItem item : items) {
            itemsList.add(item);
        }
        Message msg = mAvrcpCtSm.obtainMessage(AvrcpControllerStateMachine.
            MESSAGE_PROCESS_GET_FOLDER_ITEMS, itemsList);
        mAvrcpCtSm.sendMessage(msg);
    }

    void handleGetPlayerItemsRsp(AvrcpPlayer[] items) {
        if (DBG) {
            Log.d(TAG, "handleGetFolderItemsRsp called with " + items.length + " items.");
        }
        for (AvrcpPlayer item : items) {
            if (DBG) {
                Log.d(TAG, "bt player item: " + item);
            }
        }
        List<AvrcpPlayer> itemsList = new ArrayList<>();
        for (AvrcpPlayer p : items) {
            itemsList.add(p);
        }

        Message msg = mAvrcpCtSm.obtainMessage(AvrcpControllerStateMachine.
            MESSAGE_PROCESS_GET_PLAYER_ITEMS, itemsList);
        mAvrcpCtSm.sendMessage(msg);
    }

    // JNI Helper functions to convert native objects to java.
    MediaItem createFromNativeMediaItem(
            byte[] uid, int type, String name, int[] attrIds, String[] attrVals) {
        if (DBG) {
            Log.d(TAG, "createFromNativeMediaItem uid: " + uid + " type " + type + " name " +
                name + " attrids " + attrIds + " attrVals " + attrVals);
        }
        MediaDescription.Builder mdb = new MediaDescription.Builder();

        Bundle mdExtra = new Bundle();
        mdExtra.putString(MEDIA_ITEM_UID_KEY, byteUIDToHexString(uid));
        mdb.setExtras(mdExtra);

        // Generate a random UUID. We do this since database unaware TGs can send multiple
        // items with same MEDIA_ITEM_UID_KEY.
        mdb.setMediaId(UUID.randomUUID().toString());

        // Concise readable name.
        mdb.setTitle(name);

        // We skip the attributes since we can query them using UID for the item above
        // Also MediaDescription does not give an easy way to provide this unless we pass
        // it as an MediaMetadata which is put inside the extras.
        return new MediaItem(mdb.build(), MediaItem.FLAG_PLAYABLE);
    }

    MediaItem createFromNativeFolderItem(
            byte[] uid, int type, String name, int playable) {
        if (DBG) {
            Log.d(TAG, "createFromNativeFolderItem uid: " + uid + " type " + type +
                " name " + name + " playable " + playable);
        }
        MediaDescription.Builder mdb = new MediaDescription.Builder();

        // Covert the byte to a hex string. The coversion can be done back here to a
        // byte array when needed.
        Bundle mdExtra = new Bundle();
        mdExtra.putString(MEDIA_ITEM_UID_KEY, byteUIDToHexString(uid));
        mdb.setExtras(mdExtra);

        // Generate a random UUID. We do this since database unaware TGs can send multiple
        // items with same MEDIA_ITEM_UID_KEY.
        mdb.setMediaId(UUID.randomUUID().toString());

        // Concise readable name.
        mdb.setTitle(name);

        return new MediaItem(mdb.build(), MediaItem.FLAG_BROWSABLE);
    }

    AvrcpPlayer createFromNativePlayerItem(
            int id, String name, byte[] transportFlags, int playStatus, int playerType) {
        if (DBG) {
            Log.d(TAG, "createFromNativePlayerItem name: " + name + " transportFlags " +
                transportFlags + " play status " + playStatus + " player type " +
                playerType);
        }
        AvrcpPlayer player = new AvrcpPlayer(id, name, 0, playStatus, playerType);
        return player;
    }

    private void handleChangeFolderRsp(int count) {
        if (DBG) {
            Log.d(TAG, "handleChangeFolderRsp count: " + count);
        }
        Message msg = mAvrcpCtSm.obtainMessage(
            AvrcpControllerStateMachine.MESSAGE_PROCESS_FOLDER_PATH, count);
        mAvrcpCtSm.sendMessage(msg);
    }

    private void handleSetBrowsedPlayerRsp(int items, int depth) {
        if (DBG) {
            Log.d(TAG, "handleSetBrowsedPlayerRsp depth: " + depth);
        }
        Message msg = mAvrcpCtSm.obtainMessage(
            AvrcpControllerStateMachine.MESSAGE_PROCESS_SET_BROWSED_PLAYER, items, depth);
        mAvrcpCtSm.sendMessage(msg);
    }

    private void handleSetAddressedPlayerRsp(int status) {
        if (DBG) {
            Log.d(TAG, "handleSetAddressedPlayerRsp status: " + status);
        }
        Message msg = mAvrcpCtSm.obtainMessage(
            AvrcpControllerStateMachine.MESSAGE_PROCESS_SET_ADDRESSED_PLAYER);
        mAvrcpCtSm.sendMessage(msg);
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        mAvrcpCtSm.dump(sb);
    }

    public static String byteUIDToHexString(byte[] uid) {
        StringBuilder sb = new StringBuilder();
        for (byte b : uid) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static byte[] hexStringToByteUID(String uidStr) {
        if (uidStr == null) {
            Log.e(TAG, "Null hex string.");
            return EMPTY_UID;
        } else if (uidStr.length() % 2 == 1) {
            // Odd length strings should not be possible.
            Log.e(TAG, "Odd length hex string " + uidStr);
            return EMPTY_UID;
        }
        int len = uidStr.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
          data[i / 2] = (byte) ((Character.digit(uidStr.charAt(i), 16) << 4)
              + Character.digit(uidStr.charAt(i + 1), 16));
        }
        return data;
    }

    private native static void classInitNative();

    private native void initNative();

    private native void cleanupNative();

    native static boolean sendPassThroughCommandNative(byte[] address, int keyCode, int keyState);

    native static boolean sendGroupNavigationCommandNative(byte[] address, int keyCode,
        int keyState);

    native static void setPlayerApplicationSettingValuesNative(byte[] address, byte numAttrib,
        byte[] atttibIds, byte[] attribVal);

    /* This api is used to send response to SET_ABS_VOL_CMD */
    native static void sendAbsVolRspNative(byte[] address, int absVol, int label);

    /* This api is used to inform remote for any volume level changes */
    native static void sendRegisterAbsVolRspNative(byte[] address, byte rspType, int absVol,
        int label);

    /* API used to fetch the playback state */
    native static void getPlaybackStateNative(byte[] address);
    /* API used to fetch the current now playing list */
    native static void getNowPlayingListNative(byte[] address, byte start, byte end);
    /* API used to fetch the current folder's listing */
    native static void getFolderListNative(byte[] address, byte start, byte end);
    /* API used to fetch the listing of players */
    native static void getPlayerListNative(byte[] address, byte start, byte end);
    /* API used to change the folder */
    native static void changeFolderPathNative(byte[] address, byte direction, byte[] uid);
    native static void playItemNative(
        byte[] address, byte scope, byte[] uid, int uidCounter);
    native static void setBrowsedPlayerNative(byte[] address, int playerId);
    native static void setAddressedPlayerNative(byte[] address, int playerId);
}
