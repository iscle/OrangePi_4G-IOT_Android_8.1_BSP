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

import android.bluetooth.BluetoothAvrcpController;
import android.bluetooth.BluetoothAvrcpPlayerSettings;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dpsink.A2dpSinkService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Provides Bluetooth AVRCP Controller State Machine responsible for all remote control connections
 * and interactions with a remote controlable device.
 */
class AvrcpControllerStateMachine extends StateMachine {

    // commands from Binder service
    static final int MESSAGE_SEND_PASS_THROUGH_CMD = 1;
    static final int MESSAGE_SEND_GROUP_NAVIGATION_CMD = 3;
    static final int MESSAGE_GET_NOW_PLAYING_LIST = 5;
    static final int MESSAGE_GET_FOLDER_LIST = 6;
    static final int MESSAGE_GET_PLAYER_LIST = 7;
    static final int MESSAGE_CHANGE_FOLDER_PATH = 8;
    static final int MESSAGE_FETCH_ATTR_AND_PLAY_ITEM = 9;
    static final int MESSAGE_SET_BROWSED_PLAYER = 10;

    // commands from native layer
    static final int MESSAGE_PROCESS_SET_ABS_VOL_CMD = 103;
    static final int MESSAGE_PROCESS_REGISTER_ABS_VOL_NOTIFICATION = 104;
    static final int MESSAGE_PROCESS_TRACK_CHANGED = 105;
    static final int MESSAGE_PROCESS_PLAY_POS_CHANGED = 106;
    static final int MESSAGE_PROCESS_PLAY_STATUS_CHANGED = 107;
    static final int MESSAGE_PROCESS_VOLUME_CHANGED_NOTIFICATION = 108;
    static final int MESSAGE_PROCESS_GET_FOLDER_ITEMS = 109;
    static final int MESSAGE_PROCESS_GET_FOLDER_ITEMS_OUT_OF_RANGE = 110;
    static final int MESSAGE_PROCESS_GET_PLAYER_ITEMS = 111;
    static final int MESSAGE_PROCESS_FOLDER_PATH = 112;
    static final int MESSAGE_PROCESS_SET_BROWSED_PLAYER = 113;
    static final int MESSAGE_PROCESS_SET_ADDRESSED_PLAYER = 114;

    // commands from A2DP sink
    static final int MESSAGE_STOP_METADATA_BROADCASTS = 201;
    static final int MESSAGE_START_METADATA_BROADCASTS = 202;

    // commands for connection
    static final int MESSAGE_PROCESS_RC_FEATURES = 301;
    static final int MESSAGE_PROCESS_CONNECTION_CHANGE = 302;
    static final int MESSAGE_PROCESS_BROWSE_CONNECTION_CHANGE = 303;

    // Interal messages
    static final int MESSAGE_INTERNAL_BROWSE_DEPTH_INCREMENT = 401;
    static final int MESSAGE_INTERNAL_MOVE_N_LEVELS_UP = 402;
    static final int MESSAGE_INTERNAL_CMD_TIMEOUT = 403;

    static final int CMD_TIMEOUT_MILLIS = 5000; // 5s
    // Fetch only 5 items at a time.
    static final int GET_FOLDER_ITEMS_PAGINATION_SIZE = 5;

    /*
     * Base value for absolute volume from JNI
     */
    private static final int ABS_VOL_BASE = 127;

    /*
     * Notification types for Avrcp protocol JNI.
     */
    private static final byte NOTIFICATION_RSP_TYPE_INTERIM = 0x00;
    private static final byte NOTIFICATION_RSP_TYPE_CHANGED = 0x01;


    private static final String TAG = "AvrcpControllerSM";
    private static final boolean DBG = true;
    private static final boolean VDBG = true;

    private final Context mContext;
    private final AudioManager mAudioManager;

    private final State mDisconnected;
    private final State mConnected;
    private final SetBrowsedPlayer mSetBrowsedPlayer;
    private final SetAddresedPlayerAndPlayItem mSetAddrPlayer;
    private final ChangeFolderPath mChangeFolderPath;
    private final GetFolderList mGetFolderList;
    private final GetPlayerListing mGetPlayerListing;
    private final MoveToRoot mMoveToRoot;

    private final Object mLock = new Object();
    private static final ArrayList<MediaItem> mEmptyMediaItemList = new ArrayList<>();
    private static final MediaMetadata mEmptyMMD = new MediaMetadata.Builder().build();

    // APIs exist to access these so they must be thread safe
    private Boolean mIsConnected = false;
    private RemoteDevice mRemoteDevice;
    private AvrcpPlayer mAddressedPlayer;

    // Only accessed from State Machine processMessage
    private boolean mAbsoluteVolumeChangeInProgress = false;
    private boolean mBroadcastMetadata = false;
    private int previousPercentageVol = -1;

    // Depth from root of current browsing. This can be used to move to root directly.
    private int mBrowseDepth = 0;

    // Browse tree.
    private BrowseTree mBrowseTree = new BrowseTree();

    AvrcpControllerStateMachine(Context context) {
        super(TAG);
        mContext = context;

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        IntentFilter filter = new IntentFilter(AudioManager.VOLUME_CHANGED_ACTION);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        mDisconnected = new Disconnected();
        mConnected = new Connected();

        // Used to change folder path and fetch the new folder listing.
        mSetBrowsedPlayer = new SetBrowsedPlayer();
        mSetAddrPlayer = new SetAddresedPlayerAndPlayItem();
        mChangeFolderPath = new ChangeFolderPath();
        mGetFolderList = new GetFolderList();
        mGetPlayerListing = new GetPlayerListing();
        mMoveToRoot = new MoveToRoot();

        addState(mDisconnected);
        addState(mConnected);

        // Any action that needs blocking other requests to the state machine will be implemented as
        // a separate substate of the mConnected state. Once transtition to the sub-state we should
        // only handle the messages that are relevant to the sub-action. Everything else should be
        // deferred so that once we transition to the mConnected we can process them hence.
        addState(mSetBrowsedPlayer, mConnected);
        addState(mSetAddrPlayer, mConnected);
        addState(mChangeFolderPath, mConnected);
        addState(mGetFolderList, mConnected);
        addState(mGetPlayerListing, mConnected);
        addState(mMoveToRoot, mConnected);

        setInitialState(mDisconnected);
    }

    class Disconnected extends State {

        @Override
        public boolean processMessage(Message msg) {
            Log.d(TAG, " HandleMessage: " + dumpMessageString(msg.what));
            switch (msg.what) {
                case MESSAGE_PROCESS_CONNECTION_CHANGE:
                    if (msg.arg1 == BluetoothProfile.STATE_CONNECTED) {
                        mBrowseTree.init();
                        transitionTo(mConnected);
                        BluetoothDevice rtDevice = (BluetoothDevice) msg.obj;
                        synchronized(mLock) {
                            mRemoteDevice = new RemoteDevice(rtDevice);
                            mAddressedPlayer = new AvrcpPlayer();
                            mIsConnected = true;
                        }
                        Intent intent = new Intent(
                            BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED);
                        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE,
                            BluetoothProfile.STATE_DISCONNECTED);
                        intent.putExtra(BluetoothProfile.EXTRA_STATE,
                            BluetoothProfile.STATE_CONNECTED);
                        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, rtDevice);
                        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                    }
                    break;

                default:
                    Log.w(TAG,"Currently Disconnected not handling " + dumpMessageString(msg.what));
                    return false;
            }
            return true;
        }
    }

    class Connected extends State {
        @Override
        public boolean processMessage(Message msg) {
            Log.d(TAG, " HandleMessage: " + dumpMessageString(msg.what));
            A2dpSinkService a2dpSinkService = A2dpSinkService.getA2dpSinkService();
            synchronized (mLock) {
                switch (msg.what) {
                    case MESSAGE_STOP_METADATA_BROADCASTS:
                        mBroadcastMetadata = false;
                        broadcastPlayBackStateChanged(new PlaybackState.Builder().setState(
                            PlaybackState.STATE_PAUSED, mAddressedPlayer.getPlayTime(),
                            0).build());
                        break;

                    case MESSAGE_START_METADATA_BROADCASTS:
                        mBroadcastMetadata = true;
                        broadcastPlayBackStateChanged(mAddressedPlayer.getPlaybackState());
                        if (mAddressedPlayer.getCurrentTrack() != null) {
                            broadcastMetaDataChanged(
                                mAddressedPlayer.getCurrentTrack().getMediaMetaData());
                        }
                        break;

                    case MESSAGE_SEND_PASS_THROUGH_CMD:
                        BluetoothDevice device = (BluetoothDevice) msg.obj;
                        AvrcpControllerService
                            .sendPassThroughCommandNative(Utils.getByteAddress(device), msg.arg1,
                                msg.arg2);
                        if (a2dpSinkService != null) {
                            Log.d(TAG, " inform AVRCP Commands to A2DP Sink ");
                            a2dpSinkService.informAvrcpPassThroughCmd(device, msg.arg1, msg.arg2);
                        }
                        break;

                    case MESSAGE_SEND_GROUP_NAVIGATION_CMD:
                        AvrcpControllerService.sendGroupNavigationCommandNative(
                            mRemoteDevice.getBluetoothAddress(), msg.arg1, msg.arg2);
                        break;

                    case MESSAGE_GET_NOW_PLAYING_LIST:
                        mGetFolderList.setFolder((String) msg.obj);
                        mGetFolderList.setBounds((int) msg.arg1, (int) msg.arg2);
                        mGetFolderList.setScope(AvrcpControllerService.BROWSE_SCOPE_NOW_PLAYING);
                        transitionTo(mGetFolderList);
                        break;

                    case MESSAGE_GET_FOLDER_LIST:
                        // Whenever we transition we set the information for folder we need to
                        // return result.
                        mGetFolderList.setBounds(msg.arg1, msg.arg2);
                        mGetFolderList.setFolder((String) msg.obj);
                        mGetFolderList.setScope(AvrcpControllerService.BROWSE_SCOPE_VFS);
                        transitionTo(mGetFolderList);
                        break;

                    case MESSAGE_GET_PLAYER_LIST:
                        AvrcpControllerService.getPlayerListNative(
                            mRemoteDevice.getBluetoothAddress(), (byte) msg.arg1,
                            (byte) msg.arg2);
                        transitionTo(mGetPlayerListing);
                        sendMessageDelayed(MESSAGE_INTERNAL_CMD_TIMEOUT, CMD_TIMEOUT_MILLIS);
                        break;

                    case MESSAGE_CHANGE_FOLDER_PATH: {
                        int direction = msg.arg1;
                        Bundle b = (Bundle) msg.obj;
                        String uid = b.getString(AvrcpControllerService.EXTRA_FOLDER_BT_ID);
                        String fid = b.getString(AvrcpControllerService.EXTRA_FOLDER_ID);

                        // String is encoded as a Hex String (mostly for display purposes)
                        // hence convert this back to real byte string.
                        AvrcpControllerService.changeFolderPathNative(
                            mRemoteDevice.getBluetoothAddress(), (byte) msg.arg1,
                            AvrcpControllerService.hexStringToByteUID(uid));
                        mChangeFolderPath.setFolder(fid);
                        transitionTo(mChangeFolderPath);
                        sendMessage(MESSAGE_INTERNAL_BROWSE_DEPTH_INCREMENT, (byte) msg.arg1);
                        sendMessageDelayed(MESSAGE_INTERNAL_CMD_TIMEOUT, CMD_TIMEOUT_MILLIS);
                        break;
                    }

                    case MESSAGE_FETCH_ATTR_AND_PLAY_ITEM: {
                        int scope = msg.arg1;
                        String playItemUid = (String) msg.obj;
                        BrowseTree.BrowseNode currBrPlayer =
                            mBrowseTree.getCurrentBrowsedPlayer();
                        BrowseTree.BrowseNode currAddrPlayer =
                            mBrowseTree.getCurrentAddressedPlayer();
                        if (DBG) {
                            Log.d(TAG, "currBrPlayer " + currBrPlayer +
                                " currAddrPlayer " + currAddrPlayer);
                        }

                        if (currBrPlayer == null || currBrPlayer.equals(currAddrPlayer)) {
                            // String is encoded as a Hex String (mostly for display purposes)
                            // hence convert this back to real byte string.
                            // NOTE: It may be possible that sending play while the same item is
                            // playing leads to reset of track.
                            AvrcpControllerService.playItemNative(
                                mRemoteDevice.getBluetoothAddress(), (byte) scope,
                                AvrcpControllerService.hexStringToByteUID(playItemUid), (int) 0);
                        } else {
                            // Send out the request for setting addressed player.
                            AvrcpControllerService.setAddressedPlayerNative(
                                mRemoteDevice.getBluetoothAddress(),
                                currBrPlayer.getPlayerID());
                            mSetAddrPlayer.setItemAndScope(
                                currBrPlayer.getID(), playItemUid, scope);
                            transitionTo(mSetAddrPlayer);
                        }
                        break;
                    }

                    case MESSAGE_SET_BROWSED_PLAYER: {
                        AvrcpControllerService.setBrowsedPlayerNative(
                            mRemoteDevice.getBluetoothAddress(), (int) msg.arg1);
                        mSetBrowsedPlayer.setFolder((String) msg.obj);
                        transitionTo(mSetBrowsedPlayer);
                        break;
                    }

                    case MESSAGE_PROCESS_CONNECTION_CHANGE:
                        if (msg.arg1 == BluetoothProfile.STATE_DISCONNECTED) {
                            synchronized (mLock) {
                                mIsConnected = false;
                                mRemoteDevice = null;
                            }
                            mBrowseTree.clear();
                            transitionTo(mDisconnected);
                            BluetoothDevice rtDevice = (BluetoothDevice) msg.obj;
                            Intent intent = new Intent(
                                BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED);
                            intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE,
                                BluetoothProfile.STATE_CONNECTED);
                            intent.putExtra(BluetoothProfile.EXTRA_STATE,
                                BluetoothProfile.STATE_DISCONNECTED);
                            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, rtDevice);
                            mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                        }
                        break;

                    case MESSAGE_PROCESS_BROWSE_CONNECTION_CHANGE:
                        // Service tells us if the browse is connected or disconnected.
                        // This is useful only for deciding whether to send browse commands rest of
                        // the connection state handling should be done via the message
                        // MESSAGE_PROCESS_CONNECTION_CHANGE.
                        Intent intent = new Intent(
                            AvrcpControllerService.ACTION_BROWSE_CONNECTION_STATE_CHANGED);
                        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, (BluetoothDevice) msg.obj);
                        if (DBG) {
                            Log.d(TAG, "Browse connection state " + msg.arg1);
                        }
                        if (msg.arg1 == 1) {
                            intent.putExtra(
                                BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_CONNECTED);
                        } else if (msg.arg1 == 0) {
                            intent.putExtra(
                                BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
                            // If browse is disconnected, the next time we connect we should
                            // be at the ROOT.
                            mBrowseDepth = 0;
                        } else {
                            Log.w(TAG, "Incorrect browse state " + msg.arg1);
                        }

                        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
                        break;

                    case MESSAGE_PROCESS_RC_FEATURES:
                        mRemoteDevice.setRemoteFeatures(msg.arg1);
                        break;

                    case MESSAGE_PROCESS_SET_ABS_VOL_CMD:
                        mAbsoluteVolumeChangeInProgress = true;
                        setAbsVolume(msg.arg1, msg.arg2);
                        break;

                    case MESSAGE_PROCESS_REGISTER_ABS_VOL_NOTIFICATION: {
                        mRemoteDevice.setNotificationLabel(msg.arg1);
                        mRemoteDevice.setAbsVolNotificationRequested(true);
                        int percentageVol = getVolumePercentage();
                        Log.d(TAG,
                            " Sending Interim Response = " + percentageVol + " label " + msg.arg1);
                        AvrcpControllerService
                            .sendRegisterAbsVolRspNative(mRemoteDevice.getBluetoothAddress(),
                                NOTIFICATION_RSP_TYPE_INTERIM,
                                percentageVol,
                                mRemoteDevice.getNotificationLabel());
                    }
                    break;

                    case MESSAGE_PROCESS_VOLUME_CHANGED_NOTIFICATION: {
                        if (mAbsoluteVolumeChangeInProgress) {
                            mAbsoluteVolumeChangeInProgress = false;
                        } else {
                            if (mRemoteDevice.getAbsVolNotificationRequested()) {
                                int percentageVol = getVolumePercentage();
                                if (percentageVol != previousPercentageVol) {
                                    AvrcpControllerService.sendRegisterAbsVolRspNative(
                                        mRemoteDevice.getBluetoothAddress(),
                                        NOTIFICATION_RSP_TYPE_CHANGED,
                                        percentageVol, mRemoteDevice.getNotificationLabel());
                                    previousPercentageVol = percentageVol;
                                    mRemoteDevice.setAbsVolNotificationRequested(false);
                                }
                            }
                        }
                    }
                    break;

                    case MESSAGE_PROCESS_TRACK_CHANGED:
                        mAddressedPlayer.updateCurrentTrack((TrackInfo) msg.obj);
                        if (mBroadcastMetadata) {
                            broadcastMetaDataChanged(mAddressedPlayer.getCurrentTrack().
                                getMediaMetaData());
                        }
                        break;

                    case MESSAGE_PROCESS_PLAY_POS_CHANGED:
                        mAddressedPlayer.setPlayTime(msg.arg2);
                        if (mBroadcastMetadata) {
                            broadcastPlayBackStateChanged(getCurrentPlayBackState());
                        }
                        break;

                    case MESSAGE_PROCESS_PLAY_STATUS_CHANGED:
                        int status = msg.arg1;
                        mAddressedPlayer.setPlayStatus(status);
                        if (status == PlaybackState.STATE_PLAYING) {
                            a2dpSinkService.informTGStatePlaying(mRemoteDevice.mBTDevice, true);
                        } else if (status == PlaybackState.STATE_PAUSED ||
                            status == PlaybackState.STATE_STOPPED) {
                            a2dpSinkService.informTGStatePlaying(mRemoteDevice.mBTDevice, false);
                        }
                        break;

                    default:
                        return false;
                }
            }
            return true;
        }
    }

    // Handle the change folder path meta-action.
    // a) Send Change folder command
    // b) Once successful transition to folder fetch state.
    class ChangeFolderPath extends CmdState {
        private String STATE_TAG = "AVRCPSM.ChangeFolderPath";
        private int mTmpIncrDirection;
        private String mID = "";

        public void setFolder(String id) {
            mID = id;
        }

        @Override
        public void enter() {
            super.enter();
            mTmpIncrDirection = -1;
        }

        @Override
        public boolean processMessage(Message msg) {
            Log.d(STATE_TAG, "processMessage " + msg);
            switch (msg.what) {
                case MESSAGE_INTERNAL_BROWSE_DEPTH_INCREMENT:
                    mTmpIncrDirection = msg.arg1;
                    break;

                case MESSAGE_PROCESS_FOLDER_PATH: {
                    // Fetch the listing of objects in this folder.
                    Log.d(STATE_TAG, "MESSAGE_PROCESS_FOLDER_PATH returned " + msg.arg1 +
                        " elements");

                    // Update the folder depth.
                    if (mTmpIncrDirection ==
                        AvrcpControllerService.FOLDER_NAVIGATION_DIRECTION_UP) {
                        mBrowseDepth -= 1;;
                    } else if (mTmpIncrDirection ==
                        AvrcpControllerService.FOLDER_NAVIGATION_DIRECTION_DOWN) {
                        mBrowseDepth += 1;
                    } else {
                        throw new IllegalStateException("incorrect nav " + mTmpIncrDirection);
                    }
                    Log.d(STATE_TAG, "New browse depth " + mBrowseDepth);

                    if (msg.arg1 > 0) {
                        sendMessage(MESSAGE_GET_FOLDER_LIST, 0, msg.arg1 -1, mID);
                    } else {
                        // Return an empty response to the upper layer.
                        broadcastFolderList(mID, mEmptyMediaItemList);
                    }
                    mBrowseTree.setCurrentBrowsedFolder(mID);
                    transitionTo(mConnected);
                    break;
                }

                case MESSAGE_INTERNAL_CMD_TIMEOUT:
                    // We timed out changing folders. It is imperative we tell
                    // the upper layers that we failed by giving them an empty list.
                    Log.e(STATE_TAG, "change folder failed, sending empty list.");
                    broadcastFolderList(mID, mEmptyMediaItemList);
                    transitionTo(mConnected);
                    break;

                default:
                    Log.d(STATE_TAG, "deferring message " + msg + " to Connected state.");
                    deferMessage(msg);
            }
            return true;
        }
    }

    // Handle the get folder listing action
    // a) Fetch the listing of folders
    // b) Once completed return the object listing
    class GetFolderList extends CmdState {
        private String STATE_TAG = "AVRCPSM.GetFolderList";

        String mID = "";
        int mStartInd;
        int mEndInd;
        int mCurrInd;
        int mScope;
        private ArrayList<MediaItem> mFolderList = new ArrayList<>();

        @Override
        public void enter() {
            mCurrInd = 0;
            mFolderList.clear();

            callNativeFunctionForScope(
                mStartInd, Math.min(mEndInd, mStartInd + GET_FOLDER_ITEMS_PAGINATION_SIZE - 1));
        }

        public void setScope(int scope) {
            mScope = scope;
        }

        public void setFolder(String id) {
            Log.d(STATE_TAG, "Setting folder to " + id);
            mID = id;
        }

        public void setBounds(int startInd, int endInd) {
            if (DBG) {
                Log.d(STATE_TAG, "startInd " + startInd + " endInd " + endInd);
            }
            mStartInd = startInd;
            mEndInd = endInd;
        }

        @Override
        public boolean processMessage(Message msg) {
            Log.d(STATE_TAG, "processMessage " + msg);
            switch (msg.what) {
                case MESSAGE_PROCESS_GET_FOLDER_ITEMS:
                    ArrayList<MediaItem> folderList = (ArrayList<MediaItem>) msg.obj;
                    mFolderList.addAll(folderList);
                    if (DBG) {
                        Log.d(STATE_TAG, "Start " + mStartInd + " End " + mEndInd + " Curr " +
                            mCurrInd + " received " + folderList.size());
                    }
                    mCurrInd += folderList.size();

                    // Always update the node so that the user does not wait forever
                    // for the list to populate.
                    sendFolderBroadcastAndUpdateNode();

                    if (mCurrInd > mEndInd || folderList.size() == 0) {
                        // If we have fetched all the elements or if the remotes sends us 0 elements
                        // (which can lead us into a loop since mCurrInd does not proceed) we simply
                        // abort.
                        transitionTo(mConnected);
                    } else {
                        // Fetch the next set of items.
                        callNativeFunctionForScope(
                            (byte) mCurrInd,
                            (byte) Math.min(
                                mEndInd, mCurrInd + GET_FOLDER_ITEMS_PAGINATION_SIZE - 1));
                        // Reset the timeout message since we are doing a new fetch now.
                        removeMessages(MESSAGE_INTERNAL_CMD_TIMEOUT);
                        sendMessageDelayed(MESSAGE_INTERNAL_CMD_TIMEOUT, CMD_TIMEOUT_MILLIS);
                    }
                    break;

                case MESSAGE_INTERNAL_CMD_TIMEOUT:
                    // We have timed out to execute the request, we should simply send
                    // whatever listing we have gotten until now.
                    sendFolderBroadcastAndUpdateNode();
                    transitionTo(mConnected);
                    break;

                case MESSAGE_PROCESS_GET_FOLDER_ITEMS_OUT_OF_RANGE:
                    // If we have gotten an error for OUT OF RANGE we have
                    // already sent all the items to the client hence simply
                    // transition to Connected state here.
                    transitionTo(mConnected);
                    break;

                default:
                    Log.d(STATE_TAG, "deferring message " + msg + " to connected!");
                    deferMessage(msg);
            }
            return true;
        }

        private void sendFolderBroadcastAndUpdateNode() {
            BrowseTree.BrowseNode bn = mBrowseTree.findBrowseNodeByID(mID);
            if (bn.isPlayer()) {
                // Add the now playing folder.
                MediaDescription.Builder mdb = new MediaDescription.Builder();
                mdb.setMediaId(BrowseTree.NOW_PLAYING_PREFIX + ":" +
                    bn.getPlayerID());
                mdb.setTitle(BrowseTree.NOW_PLAYING_PREFIX);
                Bundle mdBundle = new Bundle();
                mdBundle.putString(
                    AvrcpControllerService.MEDIA_ITEM_UID_KEY,
                    BrowseTree.NOW_PLAYING_PREFIX + ":" + bn.getID());
                mdb.setExtras(mdBundle);
                mFolderList.add(new MediaItem(mdb.build(), MediaItem.FLAG_BROWSABLE));
            }
            mBrowseTree.refreshChildren(bn, mFolderList);
            broadcastFolderList(mID, mFolderList);

            // For now playing we need to set the current browsed folder here.
            // For normal folders it is set after ChangeFolderPath.
            if (mScope == AvrcpControllerService.BROWSE_SCOPE_NOW_PLAYING) {
                mBrowseTree.setCurrentBrowsedFolder(mID);
            }
        }

        private void callNativeFunctionForScope(int start, int end) {
            switch (mScope) {
                case AvrcpControllerService.BROWSE_SCOPE_NOW_PLAYING:
                    AvrcpControllerService.getNowPlayingListNative(
                        mRemoteDevice.getBluetoothAddress(), (byte) start, (byte) end);
                    break;
                case AvrcpControllerService.BROWSE_SCOPE_VFS:
                    AvrcpControllerService.getFolderListNative(
                        mRemoteDevice.getBluetoothAddress(), (byte) start, (byte) end);
                    break;
                default:
                    Log.e(STATE_TAG, "Scope " + mScope + " cannot be handled here.");
            }
        }
    }

    // Handle the get player listing action
    // a) Fetch the listing of players
    // b) Once completed return the object listing
    class GetPlayerListing extends CmdState {
        private String STATE_TAG = "AVRCPSM.GetPlayerList";

        @Override
        public boolean processMessage(Message msg) {
            Log.d(STATE_TAG, "processMessage " + msg);
            switch (msg.what) {
                case MESSAGE_PROCESS_GET_PLAYER_ITEMS:
                    List<AvrcpPlayer> playerList =
                        (List<AvrcpPlayer>) msg.obj;
                    mBrowseTree.refreshChildren(BrowseTree.ROOT, playerList);
                    ArrayList<MediaItem> mediaItemList = new ArrayList<>();
                    for (BrowseTree.BrowseNode c :
                            mBrowseTree.findBrowseNodeByID(BrowseTree.ROOT).getChildren()) {
                        mediaItemList.add(c.getMediaItem());
                    }
                    broadcastFolderList(BrowseTree.ROOT, mediaItemList);
                    mBrowseTree.setCurrentBrowsedFolder(BrowseTree.ROOT);
                    transitionTo(mConnected);
                    break;

                case MESSAGE_INTERNAL_CMD_TIMEOUT:
                    // We have timed out to execute the request.
                    // Send an empty list here.
                    broadcastFolderList(BrowseTree.ROOT, mEmptyMediaItemList);
                    transitionTo(mConnected);
                    break;

                default:
                    Log.d(STATE_TAG, "deferring message " + msg + " to connected!");
                    deferMessage(msg);
            }
            return true;
        }
    }

    class MoveToRoot extends CmdState {
        private String STATE_TAG = "AVRCPSM.MoveToRoot";
        private String mID = "";

        public void setFolder(String id) {
            Log.d(STATE_TAG, "setFolder " + id);
            mID = id;
        }

        @Override
        public void enter() {
            // Setup the timeouts.
            super.enter();

            // We need to move mBrowseDepth levels up. The following message is
            // completely internal to this state.
            sendMessage(MESSAGE_INTERNAL_MOVE_N_LEVELS_UP);
        }

        @Override
        public boolean processMessage(Message msg) {
            Log.d(STATE_TAG, "processMessage " + msg + " browse depth " + mBrowseDepth);
            switch (msg.what) {
                case MESSAGE_INTERNAL_MOVE_N_LEVELS_UP:
                    if (mBrowseDepth == 0) {
                        Log.w(STATE_TAG, "Already in root!");
                        transitionTo(mConnected);
                        sendMessage(MESSAGE_GET_FOLDER_LIST, 0, 0xff, mID);
                    } else {
                        AvrcpControllerService.changeFolderPathNative(
                            mRemoteDevice.getBluetoothAddress(),
                            (byte) AvrcpControllerService.FOLDER_NAVIGATION_DIRECTION_UP,
                            AvrcpControllerService.hexStringToByteUID(null));
                    }
                    break;

                case MESSAGE_PROCESS_FOLDER_PATH:
                    mBrowseDepth -= 1;
                    Log.d(STATE_TAG, "New browse depth " + mBrowseDepth);
                    if (mBrowseDepth < 0) {
                        throw new IllegalArgumentException("Browse depth negative!");
                    }

                    sendMessage(MESSAGE_INTERNAL_MOVE_N_LEVELS_UP);
                    break;

                default:
                    Log.d(STATE_TAG, "deferring message " + msg + " to connected!");
                    deferMessage(msg);
            }
            return true;
        }
    }

    class SetBrowsedPlayer extends CmdState {
        private String STATE_TAG = "AVRCPSM.SetBrowsedPlayer";
        String mID = "";

        public void setFolder(String id) {
            mID = id;
        }

        @Override
        public boolean processMessage(Message msg) {
            Log.d(STATE_TAG, "processMessage " + msg);
            switch (msg.what) {
                case MESSAGE_PROCESS_SET_BROWSED_PLAYER:
                    // Set the new depth.
                    Log.d(STATE_TAG, "player depth " + msg.arg2);
                    mBrowseDepth = msg.arg2;

                    // If we already on top of player and there is no content.
                    // This should very rarely happen.
                    if (mBrowseDepth == 0 && msg.arg1 == 0) {
                        broadcastFolderList(mID, mEmptyMediaItemList);
                        transitionTo(mConnected);
                    } else {
                        // Otherwise move to root and fetch the listing.
                        // the MoveToRoot#enter() function takes care of fetch.
                        mMoveToRoot.setFolder(mID);
                        transitionTo(mMoveToRoot);
                    }
                    mBrowseTree.setCurrentBrowsedFolder(mID);
                    // Also set the browsed player here.
                    mBrowseTree.setCurrentBrowsedPlayer(mID);
                    break;

                case MESSAGE_INTERNAL_CMD_TIMEOUT:
                    broadcastFolderList(mID, mEmptyMediaItemList);
                    transitionTo(mConnected);
                    break;

                default:
                    Log.d(STATE_TAG, "deferring message " + msg + " to connected!");
                    deferMessage(msg);
            }
            return true;
        }
    }

    class SetAddresedPlayerAndPlayItem extends CmdState {
        private String STATE_TAG = "AVRCPSM.SetAddresedPlayerAndPlayItem";
        int mScope;
        String mPlayItemId;
        String mAddrPlayerId;

        public void setItemAndScope(String addrPlayerId, String playItemId, int scope) {
            mAddrPlayerId = addrPlayerId;
            mPlayItemId = playItemId;
            mScope = scope;
        }

        @Override
        public boolean processMessage(Message msg) {
            Log.d(STATE_TAG, "processMessage " + msg);
            switch (msg.what) {
                case MESSAGE_PROCESS_SET_ADDRESSED_PLAYER:
                    // Set the new addressed player.
                    mBrowseTree.setCurrentAddressedPlayer(mAddrPlayerId);

                    // And now play the item.
                    AvrcpControllerService.playItemNative(
                        mRemoteDevice.getBluetoothAddress(), (byte) mScope,
                        AvrcpControllerService.hexStringToByteUID(mPlayItemId), (int) 0);

                    // Transition to connected state here.
                    transitionTo(mConnected);
                    break;

                case MESSAGE_INTERNAL_CMD_TIMEOUT:
                    transitionTo(mConnected);
                    break;

                default:
                    Log.d(STATE_TAG, "deferring message " + msg + " to connected!");
                    deferMessage(msg);
            }
            return true;
        }
    }

    // Class template for commands. Each state should do the following:
    // (a) In enter() send a timeout message which could be tracked in the
    // processMessage() stage.
    // (b) In exit() remove all the timeouts.
    //
    // Essentially the lifecycle of a timeout should be bounded to a CmdState always.
    abstract class CmdState extends State {
        @Override
        public void enter() {
            sendMessageDelayed(MESSAGE_INTERNAL_CMD_TIMEOUT, CMD_TIMEOUT_MILLIS);
        }

        @Override
        public void exit() {
            removeMessages(MESSAGE_INTERNAL_CMD_TIMEOUT);
        }
    }

    // Interface APIs
    boolean isConnected() {
        synchronized (mLock) {
            return mIsConnected;
        }
    }

    void doQuit() {
        try {
            mContext.unregisterReceiver(mBroadcastReceiver);
        } catch (IllegalArgumentException expected) {
            // If the receiver was never registered unregister will throw an
            // IllegalArgumentException.
        }
        quit();
    }

    void dump(StringBuilder sb) {
        ProfileService.println(sb, "StateMachine: " + this.toString());
    }

    MediaMetadata getCurrentMetaData() {
        synchronized (mLock) {
            if (mAddressedPlayer != null && mAddressedPlayer.getCurrentTrack() != null) {
                MediaMetadata mmd = mAddressedPlayer.getCurrentTrack().getMediaMetaData();
                if (DBG) {
                    Log.d(TAG, "getCurrentMetaData mmd " + mmd);
                }
            }
            return mEmptyMMD;
        }
    }

    PlaybackState getCurrentPlayBackState() {
        return getCurrentPlayBackState(true);
    }

    PlaybackState getCurrentPlayBackState(boolean cached) {
        if (cached) {
            synchronized (mLock) {
                if (mAddressedPlayer == null) {
                    return new PlaybackState.Builder().setState(PlaybackState.STATE_ERROR,
                        PlaybackState.PLAYBACK_POSITION_UNKNOWN,0).build();
                }
                return mAddressedPlayer.getPlaybackState();
            }
        } else {
            // Issue a native request, we return NULL since this is only for PTS.
            AvrcpControllerService.getPlaybackStateNative(mRemoteDevice.getBluetoothAddress());
            return null;
        }
    }

    // Entry point to the state machine where the services should call to fetch children
    // for a specific node. It checks if the currently browsed node is the same as the one being
    // asked for, in that case it returns the currently cached children. This saves bandwidth and
    // also if we are already fetching elements for a current folder (since we need to batch
    // fetches) then we should not submit another request but simply return what we have fetched
    // until now.
    //
    // It handles fetches to all VFS, Now Playing and Media Player lists.
    void getChildren(String parentMediaId, int start, int items) {
        BrowseTree.BrowseNode bn = mBrowseTree.findBrowseNodeByID(parentMediaId);
        if (bn == null) {
            Log.e(TAG, "Invalid folder to browse " + mBrowseTree);
            broadcastFolderList(parentMediaId, mEmptyMediaItemList);
            return;
        }

        if (DBG) {
            Log.d(TAG, "To Browse folder " + bn + " is cached " + bn.isCached() +
                " current folder " + mBrowseTree.getCurrentBrowsedFolder());
        }
        if (bn.equals(mBrowseTree.getCurrentBrowsedFolder()) && bn.isCached()) {
            if (DBG) {
                Log.d(TAG, "Same cached folder -- returning existing children.");
            }
            BrowseTree.BrowseNode n = mBrowseTree.findBrowseNodeByID(parentMediaId);
            ArrayList<MediaItem> childrenList = new ArrayList<MediaItem>();
            for (BrowseTree.BrowseNode cn : n.getChildren()) {
                childrenList.add(cn.getMediaItem());
            }
            broadcastFolderList(parentMediaId, childrenList);
            return;
        }

        Message msg = null;
        int btDirection = mBrowseTree.getDirection(parentMediaId);
        BrowseTree.BrowseNode currFol = mBrowseTree.getCurrentBrowsedFolder();
        if (DBG) {
            Log.d(TAG, "Browse direction parent " + mBrowseTree.getCurrentBrowsedFolder() +
                " req " + parentMediaId + " direction " + btDirection);
        }
        if (BrowseTree.ROOT.equals(parentMediaId)) {
            // Root contains the list of players.
            msg = obtainMessage(AvrcpControllerStateMachine.MESSAGE_GET_PLAYER_LIST, start, items);
        } else if (bn.isPlayer() && btDirection != BrowseTree.DIRECTION_SAME) {
            // Set browsed (and addressed player) as the new player.
            // This should fetch the list of folders.
            msg = obtainMessage(AvrcpControllerStateMachine.MESSAGE_SET_BROWSED_PLAYER,
                bn.getPlayerID(), 0, bn.getID());
        } else if (bn.isNowPlaying()) {
            // Issue a request to fetch the items.
            msg = obtainMessage(
                AvrcpControllerStateMachine.MESSAGE_GET_NOW_PLAYING_LIST,
                start, items, parentMediaId);
        } else {
            // Only change folder if desired. If an app refreshes a folder
            // (because it resumed etc) and current folder does not change
            // then we can simply fetch list.

            // We exempt two conditions from change folder:
            // a) If the new folder is the same as current folder (refresh of UI)
            // b) If the new folder is ROOT and current folder is NOW_PLAYING (or vice-versa)
            // In this condition we 'fake' child-parent hierarchy but it does not exist in
            // bluetooth world.
            boolean isNowPlayingToRoot =
                currFol.isNowPlaying() && bn.getID().equals(BrowseTree.ROOT);
            if (!isNowPlayingToRoot) {
                // Find the direction of traversal.
                int direction = -1;
                Log.d(TAG, "Browse direction " + currFol + " " + bn + " = " + btDirection);
                if (btDirection == BrowseTree.DIRECTION_UNKNOWN) {
                    Log.w(TAG, "parent " + bn + " is not a direct " +
                        "successor or predeccessor of current folder " + currFol);
                    broadcastFolderList(parentMediaId, mEmptyMediaItemList);
                    return;
                }

                if (btDirection == BrowseTree.DIRECTION_DOWN) {
                    direction = AvrcpControllerService.FOLDER_NAVIGATION_DIRECTION_DOWN;
                } else if (btDirection == BrowseTree.DIRECTION_UP) {
                    direction = AvrcpControllerService.FOLDER_NAVIGATION_DIRECTION_UP;
                }

                Bundle b = new Bundle();
                b.putString(AvrcpControllerService.EXTRA_FOLDER_ID, bn.getID());
                b.putString(AvrcpControllerService.EXTRA_FOLDER_BT_ID, bn.getFolderUID());
                msg = obtainMessage(
                    AvrcpControllerStateMachine.MESSAGE_CHANGE_FOLDER_PATH, direction, 0, b);
            } else {
                // Fetch the listing without changing paths.
                msg = obtainMessage(
                    AvrcpControllerStateMachine.MESSAGE_GET_FOLDER_LIST,
                    start, items, bn.getFolderUID());
            }
        }

        if (msg != null) {
            sendMessage(msg);
        }
    }

    public void fetchAttrAndPlayItem(String uid) {
        BrowseTree.BrowseNode currItem = mBrowseTree.findFolderByIDLocked(uid);
        BrowseTree.BrowseNode currFolder = mBrowseTree.getCurrentBrowsedFolder();
        Log.d(TAG, "fetchAttrAndPlayItem mediaId=" + uid + " node=" + currItem);
        if (currItem != null) {
            int scope = currFolder.isNowPlaying() ?
                AvrcpControllerService.BROWSE_SCOPE_NOW_PLAYING :
                AvrcpControllerService.BROWSE_SCOPE_VFS;
            Message msg = obtainMessage(
                AvrcpControllerStateMachine.MESSAGE_FETCH_ATTR_AND_PLAY_ITEM,
                scope, 0, currItem.getFolderUID());
            sendMessage(msg);
        }
    }

    private void broadcastMetaDataChanged(MediaMetadata metadata) {
        Intent intent = new Intent(AvrcpControllerService.ACTION_TRACK_EVENT);
        intent.putExtra(AvrcpControllerService.EXTRA_METADATA, metadata);
        if (DBG) {
            Log.d(TAG, " broadcastMetaDataChanged = " + metadata.getDescription());
        }
        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    private void broadcastFolderList(String id, ArrayList<MediaItem> items) {
        Intent intent = new Intent(AvrcpControllerService.ACTION_FOLDER_LIST);
        Log.d(TAG, "broadcastFolderList id " + id + " items " + items);
        intent.putExtra(AvrcpControllerService.EXTRA_FOLDER_ID, id);
        intent.putParcelableArrayListExtra(
            AvrcpControllerService.EXTRA_FOLDER_LIST, items);
        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    private void broadcastPlayBackStateChanged(PlaybackState state) {
        Intent intent = new Intent(AvrcpControllerService.ACTION_TRACK_EVENT);
        intent.putExtra(AvrcpControllerService.EXTRA_PLAYBACK, state);
        if (DBG) {
            Log.d(TAG, " broadcastPlayBackStateChanged = " + state.toString());
        }
        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
    }

    private void setAbsVolume(int absVol, int label) {
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currIndex = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        // Ignore first volume command since phone may not know difference between stream volume
        // and amplifier volume.
        if (mRemoteDevice.getFirstAbsVolCmdRecvd()) {
            int newIndex = (maxVolume * absVol) / ABS_VOL_BASE;
            Log.d(TAG,
                " setAbsVolume =" + absVol + " maxVol = " + maxVolume + " cur = " + currIndex +
                    " new = " + newIndex);
            /*
             * In some cases change in percentage is not sufficient enough to warrant
             * change in index values which are in range of 0-15. For such cases
             * no action is required
             */
            if (newIndex != currIndex) {
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newIndex,
                    AudioManager.FLAG_SHOW_UI);
            }
        } else {
            mRemoteDevice.setFirstAbsVolCmdRecvd();
            absVol = (currIndex * ABS_VOL_BASE) / maxVolume;
            Log.d(TAG, " SetAbsVol recvd for first time, respond with " + absVol);
        }
        AvrcpControllerService.sendAbsVolRspNative(
            mRemoteDevice.getBluetoothAddress(), absVol, label);
    }

    private int getVolumePercentage() {
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currIndex = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int percentageVol = ((currIndex * ABS_VOL_BASE) / maxVolume);
        return percentageVol;
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(AudioManager.VOLUME_CHANGED_ACTION)) {
                int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                if (streamType == AudioManager.STREAM_MUSIC) {
                    sendMessage(MESSAGE_PROCESS_VOLUME_CHANGED_NOTIFICATION);
                }
            }
        }
    };

    public static String dumpMessageString(int message) {
        String str = "UNKNOWN";
        switch (message) {
            case MESSAGE_SEND_PASS_THROUGH_CMD:
                str = "REQ_PASS_THROUGH_CMD";
                break;
            case MESSAGE_SEND_GROUP_NAVIGATION_CMD:
                str = "REQ_GRP_NAV_CMD";
                break;
            case MESSAGE_PROCESS_SET_ABS_VOL_CMD:
                str = "CB_SET_ABS_VOL_CMD";
                break;
            case MESSAGE_PROCESS_REGISTER_ABS_VOL_NOTIFICATION:
                str = "CB_REGISTER_ABS_VOL";
                break;
            case MESSAGE_PROCESS_TRACK_CHANGED:
                str = "CB_TRACK_CHANGED";
                break;
            case MESSAGE_PROCESS_PLAY_POS_CHANGED:
                str = "CB_PLAY_POS_CHANGED";
                break;
            case MESSAGE_PROCESS_PLAY_STATUS_CHANGED:
                str = "CB_PLAY_STATUS_CHANGED";
                break;
            case MESSAGE_PROCESS_RC_FEATURES:
                str = "CB_RC_FEATURES";
                break;
            case MESSAGE_PROCESS_CONNECTION_CHANGE:
                str = "CB_CONN_CHANGED";
                break;
            default:
                str = Integer.toString(message);
                break;
        }
        return str;
    }

    public static String displayBluetoothAvrcpSettings(BluetoothAvrcpPlayerSettings mSett) {
        StringBuffer sb =  new StringBuffer();
        int supportedSetting = mSett.getSettings();
        if(VDBG) Log.d(TAG," setting: " + supportedSetting);
        if((supportedSetting & BluetoothAvrcpPlayerSettings.SETTING_EQUALIZER) != 0) {
            sb.append(" EQ : ");
            sb.append(Integer.toString(mSett.getSettingValue(BluetoothAvrcpPlayerSettings.
                                                             SETTING_EQUALIZER)));
        }
        if((supportedSetting & BluetoothAvrcpPlayerSettings.SETTING_REPEAT) != 0) {
            sb.append(" REPEAT : ");
            sb.append(Integer.toString(mSett.getSettingValue(BluetoothAvrcpPlayerSettings.
                                                             SETTING_REPEAT)));
        }
        if((supportedSetting & BluetoothAvrcpPlayerSettings.SETTING_SHUFFLE) != 0) {
            sb.append(" SHUFFLE : ");
            sb.append(Integer.toString(mSett.getSettingValue(BluetoothAvrcpPlayerSettings.
                                                             SETTING_SHUFFLE)));
        }
        if((supportedSetting & BluetoothAvrcpPlayerSettings.SETTING_SCAN) != 0) {
            sb.append(" SCAN : ");
            sb.append(Integer.toString(mSett.getSettingValue(BluetoothAvrcpPlayerSettings.
                                                             SETTING_SCAN)));
        }
        return sb.toString();
    }
}
