/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.bluetooth.a2dpsink.mbs;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAvrcpController;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.service.media.MediaBrowserService;
import android.util.Pair;
import android.util.Log;

import com.android.bluetooth.R;
import com.android.bluetooth.avrcpcontroller.AvrcpControllerService;
import com.android.bluetooth.avrcpcontroller.BrowseTree;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the MediaBrowserService interface to AVRCP and A2DP
 *
 * This service provides a means for external applications to access A2DP and AVRCP.
 * The applications are expected to use MediaBrowser (see API) and all the music
 * browsing/playback/metadata can be controlled via MediaBrowser and MediaController.
 *
 * The current behavior of MediaSession exposed by this service is as follows:
 * 1. MediaSession is active (i.e. SystemUI and other overview UIs can see updates) when device is
 * connected and first starts playing. Before it starts playing we do not active the session.
 * 1.1 The session is active throughout the duration of connection.
 * 2. The session is de-activated when the device disconnects. It will be connected again when (1)
 * happens.
 */
public class A2dpMediaBrowserService extends MediaBrowserService {
    private static final String TAG = "A2dpMediaBrowserService";
    private static final String UNKNOWN_BT_AUDIO = "__UNKNOWN_BT_AUDIO__";
    private static final float PLAYBACK_SPEED = 1.0f;

    // Message sent when A2DP device is disconnected.
    private static final int MSG_DEVICE_DISCONNECT = 0;
    // Message sent when A2DP device is connected.
    private static final int MSG_DEVICE_CONNECT = 2;
    // Message sent when we recieve a TRACK update from AVRCP profile over a connected A2DP device.
    private static final int MSG_TRACK = 4;
    // Internal message sent to trigger a AVRCP action.
    private static final int MSG_AVRCP_PASSTHRU = 5;
    // Internal message to trigger a getplaystatus command to remote.
    private static final int MSG_AVRCP_GET_PLAY_STATUS_NATIVE = 6;
    // Message sent when AVRCP browse is connected.
    private static final int MSG_DEVICE_BROWSE_CONNECT = 7;
    // Message sent when AVRCP browse is disconnected.
    private static final int MSG_DEVICE_BROWSE_DISCONNECT = 8;
    // Message sent when folder list is fetched.
    private static final int MSG_FOLDER_LIST = 9;

    // Custom actions for PTS testing.
    private String CUSTOM_ACTION_VOL_UP = "com.android.bluetooth.a2dpsink.mbs.CUSTOM_ACTION_VOL_UP";
    private String CUSTOM_ACTION_VOL_DN = "com.android.bluetooth.a2dpsink.mbs.CUSTOM_ACTION_VOL_DN";
    private String CUSTOM_ACTION_GET_PLAY_STATUS_NATIVE =
        "com.android.bluetooth.a2dpsink.mbs.CUSTOM_ACTION_GET_PLAY_STATUS_NATIVE";

    private MediaSession mSession;
    private MediaMetadata mA2dpMetadata;

    private AvrcpControllerService mAvrcpCtrlSrvc;
    private boolean mBrowseConnected = false;
    private BluetoothDevice mA2dpDevice = null;
    private Handler mAvrcpCommandQueue;
    private final Map<String, Result<List<MediaItem>>> mParentIdToRequestMap = new HashMap<>();
    private static final List<MediaItem> mEmptyList = new ArrayList<MediaItem>();

    // Browsing related structures.
    private List<MediaItem> mNowPlayingList = null;

    private long mTransportControlFlags = PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY
            | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

    private static final class AvrcpCommandQueueHandler extends Handler {
        WeakReference<A2dpMediaBrowserService> mInst;

        AvrcpCommandQueueHandler(Looper looper, A2dpMediaBrowserService sink) {
            super(looper);
            mInst = new WeakReference<A2dpMediaBrowserService>(sink);
        }

        @Override
        public void handleMessage(Message msg) {
            A2dpMediaBrowserService inst = mInst.get();
            if (inst == null) {
                Log.e(TAG, "Parent class has died; aborting.");
                return;
            }

            switch (msg.what) {
                case MSG_DEVICE_CONNECT:
                    inst.msgDeviceConnect((BluetoothDevice) msg.obj);
                    break;
                case MSG_DEVICE_DISCONNECT:
                    inst.msgDeviceDisconnect((BluetoothDevice) msg.obj);
                    break;
                case MSG_TRACK:
                    Pair<PlaybackState, MediaMetadata> pair =
                        (Pair<PlaybackState, MediaMetadata>) (msg.obj);
                    inst.msgTrack(pair.first, pair.second);
                    break;
                case MSG_AVRCP_PASSTHRU:
                    inst.msgPassThru((int) msg.obj);
                    break;
                case MSG_AVRCP_GET_PLAY_STATUS_NATIVE:
                    inst.msgGetPlayStatusNative();
                    break;
                case MSG_DEVICE_BROWSE_CONNECT:
                    inst.msgDeviceBrowseConnect((BluetoothDevice) msg.obj);
                    break;
                case MSG_DEVICE_BROWSE_DISCONNECT:
                    inst.msgDeviceBrowseDisconnect((BluetoothDevice) msg.obj);
                    break;
                case MSG_FOLDER_LIST:
                    inst.msgFolderList((Intent) msg.obj);
                    break;
                default:
                    Log.e(TAG, "Message not handled " + msg);
            }
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();

        mSession = new MediaSession(this, TAG);
        setSessionToken(mSession.getSessionToken());
        mSession.setCallback(mSessionCallbacks);
        mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mSession.setActive(true);
        mAvrcpCommandQueue = new AvrcpCommandQueueHandler(Looper.getMainLooper(), this);

        refreshInitialPlayingState();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(AvrcpControllerService.ACTION_BROWSE_CONNECTION_STATE_CHANGED);
        filter.addAction(AvrcpControllerService.ACTION_TRACK_EVENT);
        filter.addAction(AvrcpControllerService.ACTION_FOLDER_LIST);
        registerReceiver(mBtReceiver, filter);

        synchronized (this) {
            mParentIdToRequestMap.clear();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mSession.release();
        unregisterReceiver(mBtReceiver);
        super.onDestroy();
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        return new BrowserRoot(BrowseTree.ROOT, null);
    }

    @Override
    public synchronized void onLoadChildren(
            final String parentMediaId, final Result<List<MediaItem>> result) {
        if (mAvrcpCtrlSrvc == null) {
            Log.e(TAG, "AVRCP not yet connected.");
            result.sendResult(mEmptyList);
            return;
        }

        Log.d(TAG, "onLoadChildren parentMediaId=" + parentMediaId);
        if (!mAvrcpCtrlSrvc.getChildren(mA2dpDevice, parentMediaId, 0, 0xff)) {
            result.sendResult(mEmptyList);
            return;
        }

        // Since we are using this thread from a binder thread we should make sure that
        // we synchronize against other such asynchronous calls.
        synchronized (this) {
            mParentIdToRequestMap.put(parentMediaId, result);
        }
        result.detach();
    }

    @Override
    public void onLoadItem(String itemId, Result<MediaBrowser.MediaItem> result) {
    }

    // Media Session Stuff.
    private MediaSession.Callback mSessionCallbacks = new MediaSession.Callback() {
        @Override
        public void onPlay() {
            Log.d(TAG, "onPlay");
            mAvrcpCommandQueue.obtainMessage(
                MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_PLAY).sendToTarget();
            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        @Override
        public void onPause() {
            Log.d(TAG, "onPause");
            mAvrcpCommandQueue.obtainMessage(
                MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE).sendToTarget();
            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        @Override
        public void onSkipToNext() {
            Log.d(TAG, "onSkipToNext");
            mAvrcpCommandQueue.obtainMessage(
                MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_FORWARD)
                .sendToTarget();
            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        @Override
        public void onSkipToPrevious() {
            Log.d(TAG, "onSkipToPrevious");

            mAvrcpCommandQueue.obtainMessage(
                MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_BACKWARD)
                .sendToTarget();
            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        @Override
        public void onStop() {
            Log.d(TAG, "onStop");
            mAvrcpCommandQueue.obtainMessage(
                    MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_STOP)
                    .sendToTarget();
        }

        @Override
        public void onRewind() {
            Log.d(TAG, "onRewind");
            mAvrcpCommandQueue.obtainMessage(
                MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_REWIND).sendToTarget();
            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        @Override
        public void onFastForward() {
            Log.d(TAG, "onFastForward");
            mAvrcpCommandQueue.obtainMessage(
                MSG_AVRCP_PASSTHRU, AvrcpControllerService.PASS_THRU_CMD_ID_FF).sendToTarget();
            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            synchronized (A2dpMediaBrowserService.this) {
                // Play the item if possible.
                mAvrcpCtrlSrvc.fetchAttrAndPlayItem(mA2dpDevice, mediaId);

                // Since we request explicit playback here we should start the updates to UI.
                mAvrcpCtrlSrvc.startAvrcpUpdates();
            }

            // TRACK_EVENT should be fired eventually and the UI should be hence updated.
        }

        // Support VOL UP and VOL DOWN events for PTS testing.
        @Override
        public void onCustomAction(String action, Bundle extras) {
            Log.d(TAG, "onCustomAction " + action);
            if (CUSTOM_ACTION_VOL_UP.equals(action)) {
                mAvrcpCommandQueue.obtainMessage(
                    MSG_AVRCP_PASSTHRU,
                    AvrcpControllerService.PASS_THRU_CMD_ID_VOL_UP).sendToTarget();
            } else if (CUSTOM_ACTION_VOL_DN.equals(action)) {
                mAvrcpCommandQueue.obtainMessage(
                    MSG_AVRCP_PASSTHRU,
                    AvrcpControllerService.PASS_THRU_CMD_ID_VOL_DOWN).sendToTarget();
            } else if (CUSTOM_ACTION_GET_PLAY_STATUS_NATIVE.equals(action)) {
                mAvrcpCommandQueue.obtainMessage(
                    MSG_AVRCP_GET_PLAY_STATUS_NATIVE).sendToTarget();
            }else {
                Log.w(TAG, "Custom action " + action + " not supported.");
            }
        }
    };

    private BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive intent=" + intent);
            String action = intent.getAction();
            BluetoothDevice btDev =
                    (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);

            if (BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                Log.d(TAG, "handleConnectionStateChange: newState="
                        + state + " btDev=" + btDev);

                // Connected state will be handled when AVRCP BluetoothProfile gets connected.
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    mAvrcpCommandQueue.obtainMessage(MSG_DEVICE_CONNECT, btDev).sendToTarget();
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    // Set the playback state to unconnected.
                    mAvrcpCommandQueue.obtainMessage(MSG_DEVICE_DISCONNECT, btDev).sendToTarget();
                    // If we have been pushing updates via the session then stop sending them since
                    // we are not connected anymore.
                    if (mSession.isActive()) {
                        mSession.setActive(false);
                    }
                }
            } else if (AvrcpControllerService.ACTION_BROWSE_CONNECTION_STATE_CHANGED.equals(
                action)) {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    mAvrcpCommandQueue.obtainMessage(
                        MSG_DEVICE_BROWSE_CONNECT, btDev).sendToTarget();
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    mAvrcpCommandQueue.obtainMessage(
                        MSG_DEVICE_BROWSE_DISCONNECT, btDev).sendToTarget();
                }
            } else if (AvrcpControllerService.ACTION_TRACK_EVENT.equals(action)) {
                PlaybackState pbb =
                        intent.getParcelableExtra(AvrcpControllerService.EXTRA_PLAYBACK);
                MediaMetadata mmd =
                        intent.getParcelableExtra(AvrcpControllerService.EXTRA_METADATA);
                mAvrcpCommandQueue
                        .obtainMessage(MSG_TRACK, new Pair<PlaybackState, MediaMetadata>(pbb, mmd))
                        .sendToTarget();
            } else if (AvrcpControllerService.ACTION_FOLDER_LIST.equals(action)) {
                mAvrcpCommandQueue.obtainMessage(MSG_FOLDER_LIST, intent).sendToTarget();
            }
        }
    };

    private synchronized void msgDeviceConnect(BluetoothDevice device) {
        Log.d(TAG, "msgDeviceConnect");
        // We are connected to a new device via A2DP now.
        mA2dpDevice = device;
        mAvrcpCtrlSrvc = AvrcpControllerService.getAvrcpControllerService();
        if (mAvrcpCtrlSrvc == null) {
            Log.e(TAG, "!!!AVRCP Controller cannot be null");
            return;
        }
        refreshInitialPlayingState();
    }


    // Refresh the UI if we have a connected device and AVRCP is initialized.
    private synchronized void refreshInitialPlayingState() {
        if (mA2dpDevice == null) {
            Log.d(TAG, "device " + mA2dpDevice);
            return;
        }

        List<BluetoothDevice> devices = mAvrcpCtrlSrvc.getConnectedDevices();
        if (devices.size() == 0) {
            Log.w(TAG, "No devices connected yet");
            return;
        }

        if (mA2dpDevice != null && !mA2dpDevice.equals(devices.get(0))) {
            Log.e(TAG, "A2dp device : " + mA2dpDevice + " avrcp device " + devices.get(0));
            return;
        }
        mA2dpDevice = devices.get(0);

        PlaybackState playbackState = mAvrcpCtrlSrvc.getPlaybackState(mA2dpDevice);
        // Add actions required for playback and rebuild the object.
        PlaybackState.Builder pbb = new PlaybackState.Builder(playbackState);
        playbackState = pbb.setActions(mTransportControlFlags).build();

        MediaMetadata mediaMetadata = mAvrcpCtrlSrvc.getMetaData(mA2dpDevice);
        Log.d(TAG, "Media metadata " + mediaMetadata + " playback state " + playbackState);
        mSession.setMetadata(mAvrcpCtrlSrvc.getMetaData(mA2dpDevice));
        mSession.setPlaybackState(playbackState);
    }

    private void msgDeviceDisconnect(BluetoothDevice device) {
        Log.d(TAG, "msgDeviceDisconnect");
        if (mA2dpDevice == null) {
            Log.w(TAG, "Already disconnected - nothing to do here.");
            return;
        } else if (!mA2dpDevice.equals(device)) {
            Log.e(TAG, "Not the right device to disconnect current " +
                mA2dpDevice + " dc " + device);
            return;
        }

        // Unset the session.
        PlaybackState.Builder pbb = new PlaybackState.Builder();
        pbb = pbb.setState(PlaybackState.STATE_ERROR, PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    PLAYBACK_SPEED)
                .setActions(mTransportControlFlags)
                .setErrorMessage(getString(R.string.bluetooth_disconnected));
        mSession.setPlaybackState(pbb.build());

        // Set device to null.
        mA2dpDevice = null;
        mBrowseConnected = false;
        // update playerList.
        notifyChildrenChanged("__ROOT__");
    }

    private void msgTrack(PlaybackState pb, MediaMetadata mmd) {
        Log.d(TAG, "msgTrack: playback: " + pb + " mmd: " + mmd);
        // Log the current track position/content.
        MediaController controller = mSession.getController();
        PlaybackState prevPS = controller.getPlaybackState();
        MediaMetadata prevMM = controller.getMetadata();

        if (prevPS != null) {
            Log.d(TAG, "prevPS " + prevPS);
        }

        if (prevMM != null) {
            String title = prevMM.getString(MediaMetadata.METADATA_KEY_TITLE);
            long trackLen = prevMM.getLong(MediaMetadata.METADATA_KEY_DURATION);
            Log.d(TAG, "prev MM title " + title + " track len " + trackLen);
        }

        if (mmd != null) {
            Log.d(TAG, "msgTrack() mmd " + mmd.getDescription());
            mSession.setMetadata(mmd);
        }

        if (pb != null) {
            Log.d(TAG, "msgTrack() playbackstate " + pb);
            PlaybackState.Builder pbb = new PlaybackState.Builder(pb);
            pb = pbb.setActions(mTransportControlFlags).build();
            mSession.setPlaybackState(pb);

            // If we are now playing then we should start pushing updates via MediaSession so that
            // external UI (such as SystemUI) can show the currently playing music.
            if (pb.getState() == PlaybackState.STATE_PLAYING && !mSession.isActive()) {
                mSession.setActive(true);
            }
        }
    }

    private synchronized void msgPassThru(int cmd) {
        Log.d(TAG, "msgPassThru " + cmd);
        if (mA2dpDevice == null) {
            // We should have already disconnected - ignore this message.
            Log.e(TAG, "Already disconnected ignoring.");
            return;
        }

        // Send the pass through.
        mAvrcpCtrlSrvc.sendPassThroughCmd(
            mA2dpDevice, cmd, AvrcpControllerService.KEY_STATE_PRESSED);
        mAvrcpCtrlSrvc.sendPassThroughCmd(
            mA2dpDevice, cmd, AvrcpControllerService.KEY_STATE_RELEASED);
    }

    private synchronized void msgGetPlayStatusNative() {
        Log.d(TAG, "msgGetPlayStatusNative");
        if (mA2dpDevice == null) {
            // We should have already disconnected - ignore this message.
            Log.e(TAG, "Already disconnected ignoring.");
            return;
        }

        // Ask for a non cached version.
        mAvrcpCtrlSrvc.getPlaybackState(mA2dpDevice, false);
    }

    private void msgDeviceBrowseConnect(BluetoothDevice device) {
        Log.d(TAG, "msgDeviceBrowseConnect device " + device);
        // We should already be connected to this device over A2DP.
        if (!device.equals(mA2dpDevice)) {
            Log.e(TAG, "Browse connected over different device a2dp " + mA2dpDevice +
                " browse " + device);
            return;
        }
        mBrowseConnected = true;
        // update playerList
        notifyChildrenChanged("__ROOT__");
    }

    private void msgFolderList(Intent intent) {
        // Parse the folder list for children list and id.
        List<Parcelable> extraParcelableList =
            (ArrayList<Parcelable>) intent.getParcelableArrayListExtra(
                AvrcpControllerService.EXTRA_FOLDER_LIST);
        List<MediaItem> folderList = new ArrayList<MediaItem>();
        for (Parcelable p : extraParcelableList) {
            folderList.add((MediaItem) p);
        }

        String id = intent.getStringExtra(AvrcpControllerService.EXTRA_FOLDER_ID);
        Log.d(TAG, "Parent: " + id + " Folder list: " + folderList);
        synchronized (this) {
            // If we have a result object then we should send the result back
            // to client since it is blocking otherwise we may have gotten more items
            // from remote device, hence let client know to fetch again.
            Result<List<MediaItem>> results = mParentIdToRequestMap.remove(id);
            if (results == null) {
                Log.w(TAG, "Request no longer exists, notifying that children changed.");
                notifyChildrenChanged(id);
            } else {
                results.sendResult(folderList);
            }
        }
    }

    private void msgDeviceBrowseDisconnect(BluetoothDevice device) {
        Log.d(TAG, "msgDeviceBrowseDisconnect device " + device);
        // Disconnect only if mA2dpDevice is non null
        if (!device.equals(mA2dpDevice)) {
            Log.w(TAG, "Browse disconnecting from different device a2dp " + mA2dpDevice +
                " browse " + device);
            return;
        }
        mBrowseConnected = false;
    }
}
