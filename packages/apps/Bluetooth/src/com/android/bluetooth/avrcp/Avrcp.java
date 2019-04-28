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

package com.android.bluetooth.avrcp;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAvrcp;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.MediaSession.QueueItem;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;
import android.view.KeyEvent;

import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.R;
import com.android.bluetooth.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/******************************************************************************
 * support Bluetooth AVRCP profile. support metadata, play status, event
 * notifications, address player selection and browse feature implementation.
 ******************************************************************************/

public final class Avrcp {
    private static final boolean DEBUG = true;
    private static final String TAG = "Avrcp";
    private static final String ABSOLUTE_VOLUME_BLACKLIST = "absolute_volume_blacklist";

    private Context mContext;
    private final AudioManager mAudioManager;
    private AvrcpMessageHandler mHandler;
    private Handler mAudioManagerPlaybackHandler;
    private AudioManagerPlaybackListener mAudioManagerPlaybackCb;
    private MediaSessionManager mMediaSessionManager;
    private @Nullable MediaController mMediaController;
    private MediaControllerListener mMediaControllerCb;
    private MediaAttributes mMediaAttributes;
    private long mLastQueueId;
    private PackageManager mPackageManager;
    private int mTransportControlFlags;
    private @NonNull PlaybackState mCurrentPlayState;
    private int mA2dpState;
    private boolean mAudioManagerIsPlaying;
    private int mPlayStatusChangedNT;
    private byte mReportedPlayStatus;
    private int mTrackChangedNT;
    private int mPlayPosChangedNT;
    private int mAddrPlayerChangedNT;
    private int mReportedPlayerID;
    private int mNowPlayingListChangedNT;
    private long mPlaybackIntervalMs;
    private long mLastReportedPosition;
    private long mNextPosMs;
    private long mPrevPosMs;
    private int mFeatures;
    private int mRemoteVolume;
    private int mLastRemoteVolume;
    private int mInitialRemoteVolume;

    /* Local volume in audio index 0-15 */
    private int mLocalVolume;
    private int mLastLocalVolume;
    private int mAbsVolThreshold;

    private String mAddress;
    private HashMap<Integer, Integer> mVolumeMapping;

    private int mLastDirection;
    private final int mVolumeStep;
    private final int mAudioStreamMax;
    private boolean mVolCmdAdjustInProgress;
    private boolean mVolCmdSetInProgress;
    private int mAbsVolRetryTimes;

    private static final int NO_PLAYER_ID = 0;

    private int mCurrAddrPlayerID;
    private int mCurrBrowsePlayerID;
    private int mLastUsedPlayerID;
    private AvrcpMediaRsp mAvrcpMediaRsp;

    /* UID counter to be shared across different files. */
    static short sUIDCounter = AvrcpConstants.DEFAULT_UID_COUNTER;

    /* BTRC features */
    public static final int BTRC_FEAT_METADATA = 0x01;
    public static final int BTRC_FEAT_ABSOLUTE_VOLUME = 0x02;
    public static final int BTRC_FEAT_BROWSE = 0x04;

    /* AVRC response codes, from avrc_defs */
    private static final int AVRC_RSP_NOT_IMPL = 8;
    private static final int AVRC_RSP_ACCEPT = 9;
    private static final int AVRC_RSP_REJ = 10;
    private static final int AVRC_RSP_IN_TRANS = 11;
    private static final int AVRC_RSP_IMPL_STBL = 12;
    private static final int AVRC_RSP_CHANGED = 13;
    private static final int AVRC_RSP_INTERIM = 15;

    /* AVRC request commands from Native */
    private static final int MSG_NATIVE_REQ_GET_RC_FEATURES = 1;
    private static final int MSG_NATIVE_REQ_GET_PLAY_STATUS = 2;
    private static final int MSG_NATIVE_REQ_GET_ELEM_ATTRS = 3;
    private static final int MSG_NATIVE_REQ_REGISTER_NOTIFICATION = 4;
    private static final int MSG_NATIVE_REQ_VOLUME_CHANGE = 5;
    private static final int MSG_NATIVE_REQ_GET_FOLDER_ITEMS = 6;
    private static final int MSG_NATIVE_REQ_SET_ADDR_PLAYER = 7;
    private static final int MSG_NATIVE_REQ_SET_BR_PLAYER = 8;
    private static final int MSG_NATIVE_REQ_CHANGE_PATH = 9;
    private static final int MSG_NATIVE_REQ_PLAY_ITEM = 10;
    private static final int MSG_NATIVE_REQ_GET_ITEM_ATTR = 11;
    private static final int MSG_NATIVE_REQ_GET_TOTAL_NUM_OF_ITEMS = 12;
    private static final int MSG_NATIVE_REQ_PASS_THROUGH = 13;

    /* other AVRC messages */
    private static final int MSG_PLAY_INTERVAL_TIMEOUT = 14;
    private static final int MSG_ADJUST_VOLUME = 15;
    private static final int MSG_SET_ABSOLUTE_VOLUME = 16;
    private static final int MSG_ABS_VOL_TIMEOUT = 17;
    private static final int MSG_SET_A2DP_AUDIO_STATE = 18;
    private static final int MSG_NOW_PLAYING_CHANGED_RSP = 19;

    private static final int CMD_TIMEOUT_DELAY = 2000;
    private static final int MAX_ERROR_RETRY_TIMES = 6;
    private static final int AVRCP_MAX_VOL = 127;
    private static final int AVRCP_BASE_VOLUME_STEP = 1;

    /* Communicates with MediaPlayer to fetch media content */
    private BrowsedMediaPlayer mBrowsedMediaPlayer;

    /* Addressed player handling */
    private AddressedMediaPlayer mAddressedMediaPlayer;

    /* List of Media player instances, useful for retrieving MediaPlayerList or MediaPlayerInfo */
    private SortedMap<Integer, MediaPlayerInfo> mMediaPlayerInfoList;
    private boolean mAvailablePlayerViewChanged;

    /* List of media players which supports browse */
    private List<BrowsePlayerInfo> mBrowsePlayerInfoList;

    /* Manage browsed players */
    private AvrcpBrowseManager mAvrcpBrowseManager;

    /* Broadcast receiver for device connections intent broadcasts */
    private final BroadcastReceiver mAvrcpReceiver = new AvrcpServiceBroadcastReceiver();
    private final BroadcastReceiver mBootReceiver = new AvrcpServiceBootReceiver();

    /* Recording passthrough key dispatches */
    static private final int PASSTHROUGH_LOG_MAX_SIZE = DEBUG ? 50 : 10;
    private EvictingQueue<MediaKeyLog> mPassthroughLogs; // Passthorugh keys dispatched
    private List<MediaKeyLog> mPassthroughPending; // Passthrough keys sent not dispatched yet
    private int mPassthroughDispatched; // Number of keys dispatched

    private class MediaKeyLog {
        private long mTimeSent;
        private long mTimeProcessed;
        private String mPackage;
        private KeyEvent mEvent;

        public MediaKeyLog(long time, KeyEvent event) {
            mEvent = event;
            mTimeSent = time;
        }

        public boolean addDispatch(long time, KeyEvent event, String packageName) {
            if (mPackage != null) return false;
            if (event.getAction() != mEvent.getAction()) return false;
            if (event.getKeyCode() != mEvent.getKeyCode()) return false;
            mPackage = packageName;
            mTimeProcessed = time;
            return true;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(android.text.format.DateFormat.format("MM-dd HH:mm:ss", mTimeSent));
            sb.append(" " + mEvent.toString());
            if (mPackage == null) {
                sb.append(" (undispatched)");
            } else {
                sb.append(" to " + mPackage);
                sb.append(" in " + (mTimeProcessed - mTimeSent) + "ms");
            }
            return sb.toString();
        }
    }

    static {
        classInitNative();
    }

    private Avrcp(Context context) {
        mMediaAttributes = new MediaAttributes(null);
        mLastQueueId = MediaSession.QueueItem.UNKNOWN_ID;
        mCurrentPlayState = new PlaybackState.Builder().setState(PlaybackState.STATE_NONE, -1L, 0.0f).build();
        mReportedPlayStatus = PLAYSTATUS_ERROR;
        mA2dpState = BluetoothA2dp.STATE_NOT_PLAYING;
        mAudioManagerIsPlaying = false;
        mPlayStatusChangedNT = AvrcpConstants.NOTIFICATION_TYPE_CHANGED;
        mTrackChangedNT = AvrcpConstants.NOTIFICATION_TYPE_CHANGED;
        mPlayPosChangedNT = AvrcpConstants.NOTIFICATION_TYPE_CHANGED;
        mAddrPlayerChangedNT = AvrcpConstants.NOTIFICATION_TYPE_CHANGED;
        mNowPlayingListChangedNT = AvrcpConstants.NOTIFICATION_TYPE_CHANGED;
        mPlaybackIntervalMs = 0L;
        mLastReportedPosition = -1;
        mNextPosMs = -1;
        mPrevPosMs = -1;
        mFeatures = 0;
        mRemoteVolume = -1;
        mInitialRemoteVolume = -1;
        mLastRemoteVolume = -1;
        mLastDirection = 0;
        mVolCmdAdjustInProgress = false;
        mVolCmdSetInProgress = false;
        mAbsVolRetryTimes = 0;
        mLocalVolume = -1;
        mLastLocalVolume = -1;
        mAbsVolThreshold = 0;
        mVolumeMapping = new HashMap<Integer, Integer>();
        mCurrAddrPlayerID = NO_PLAYER_ID;
        mReportedPlayerID = mCurrAddrPlayerID;
        mCurrBrowsePlayerID = 0;
        mContext = context;
        mLastUsedPlayerID = 0;
        mAddressedMediaPlayer = null;

        initNative();

        mMediaSessionManager = (MediaSessionManager) context.getSystemService(
            Context.MEDIA_SESSION_SERVICE);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mAudioStreamMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mVolumeStep = Math.max(AVRCP_BASE_VOLUME_STEP, AVRCP_MAX_VOL/mAudioStreamMax);

        Resources resources = context.getResources();
        if (resources != null) {
            mAbsVolThreshold = resources.getInteger(R.integer.a2dp_absolute_volume_initial_threshold);

            // Update the threshold if the threshold_percent is valid
            int threshold_percent =
                    resources.getInteger(R.integer.a2dp_absolute_volume_initial_threshold_percent);
            if (threshold_percent >= 0 && threshold_percent <= 100) {
                mAbsVolThreshold = (threshold_percent * mAudioStreamMax) / 100;
            }
        }

        // Register for package removal intent broadcasts for media button receiver persistence
        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_DATA_CLEARED);
        pkgFilter.addDataScheme("package");
        context.registerReceiver(mAvrcpReceiver, pkgFilter);

        IntentFilter bootFilter = new IntentFilter();
        bootFilter.addAction(Intent.ACTION_USER_UNLOCKED);
        context.registerReceiver(mBootReceiver, bootFilter);
    }

    private synchronized void start() {
        HandlerThread thread = new HandlerThread("BluetoothAvrcpHandler");
        thread.start();
        Looper looper = thread.getLooper();
        mHandler = new AvrcpMessageHandler(looper);
        mAudioManagerPlaybackHandler = new Handler(looper);
        mAudioManagerPlaybackCb = new AudioManagerPlaybackListener();
        mMediaControllerCb = new MediaControllerListener();
        mAvrcpMediaRsp = new AvrcpMediaRsp();
        mMediaPlayerInfoList = new TreeMap<Integer, MediaPlayerInfo>();
        mAvailablePlayerViewChanged = false;
        mBrowsePlayerInfoList = Collections.synchronizedList(new ArrayList<BrowsePlayerInfo>());
        mPassthroughDispatched = 0;
        mPassthroughLogs = new EvictingQueue<MediaKeyLog>(PASSTHROUGH_LOG_MAX_SIZE);
        mPassthroughPending = Collections.synchronizedList(new ArrayList<MediaKeyLog>());
        if (mMediaSessionManager != null) {
            mMediaSessionManager.addOnActiveSessionsChangedListener(mActiveSessionListener, null,
                    mHandler);
            mMediaSessionManager.setCallback(mButtonDispatchCallback, null);
        }
        mPackageManager = mContext.getApplicationContext().getPackageManager();

        /* create object to communicate with addressed player */
        mAddressedMediaPlayer = new AddressedMediaPlayer(mAvrcpMediaRsp);

        /* initialize BrowseMananger which manages Browse commands and response */
        mAvrcpBrowseManager = new AvrcpBrowseManager(mContext, mAvrcpMediaRsp);

        initMediaPlayersList();

        UserManager manager = UserManager.get(mContext);
        if (manager == null || manager.isUserUnlocked()) {
            if (DEBUG) Log.d(TAG, "User already unlocked, initializing player lists");
            // initialize browsable player list and build media player list
            buildBrowsablePlayerList();
        }

        mAudioManager.registerAudioPlaybackCallback(
                mAudioManagerPlaybackCb, mAudioManagerPlaybackHandler);
    }

    public static Avrcp make(Context context) {
        if (DEBUG) Log.v(TAG, "make");
        Avrcp ar = new Avrcp(context);
        ar.start();
        return ar;
    }

    public synchronized void doQuit() {
        if (DEBUG) Log.d(TAG, "doQuit");
        if (mAudioManager != null) {
            mAudioManager.unregisterAudioPlaybackCallback(mAudioManagerPlaybackCb);
        }
        if (mMediaController != null) mMediaController.unregisterCallback(mMediaControllerCb);
        if (mMediaSessionManager != null) {
            mMediaSessionManager.setCallback(null, null);
            mMediaSessionManager.removeOnActiveSessionsChangedListener(mActiveSessionListener);
        }

        mAudioManagerPlaybackHandler.removeCallbacksAndMessages(null);
        mHandler.removeCallbacksAndMessages(null);
        Looper looper = mHandler.getLooper();
        if (looper != null) {
            looper.quit();
        }

        mAudioManagerPlaybackHandler = null;
///M: Do not release mHandler to avoid JE @{
//        mHandler = null;
/// @}
        mContext.unregisterReceiver(mAvrcpReceiver);
        mContext.unregisterReceiver(mBootReceiver);

        mAddressedMediaPlayer.cleanup();
        mAvrcpBrowseManager.cleanup();
    }

    public void cleanup() {
        if (DEBUG) Log.d(TAG, "cleanup");
        cleanupNative();
        if (mVolumeMapping != null)
            mVolumeMapping.clear();
    }

    private class AudioManagerPlaybackListener extends AudioManager.AudioPlaybackCallback {
        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            super.onPlaybackConfigChanged(configs);
            boolean isPlaying = false;
            for (AudioPlaybackConfiguration config : configs) {
                if (DEBUG) {
                    Log.d(TAG,
                            "AudioManager Player: "
                                    + AudioPlaybackConfiguration.toLogFriendlyString(config));
                }
                if (config.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                    isPlaying = true;
                    break;
                }
            }
            if (DEBUG) Log.d(TAG, "AudioManager isPlaying: " + isPlaying);
            if (mAudioManagerIsPlaying != isPlaying) {
                mAudioManagerIsPlaying = isPlaying;
                updateCurrentMediaState();
            }
        }
    }

    private class MediaControllerListener extends MediaController.Callback {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            if (DEBUG) Log.v(TAG, "onMetadataChanged");
            updateCurrentMediaState();
        }
        @Override
        public synchronized void onPlaybackStateChanged(PlaybackState state) {
            if (DEBUG) Log.v(TAG, "onPlaybackStateChanged: state " + state.toString());

            updateCurrentMediaState();
        }

        @Override
        public void onSessionDestroyed() {
            Log.v(TAG, "MediaController session destroyed");
            synchronized (Avrcp.this) {
                if (mMediaController != null)
                    removeMediaController(mMediaController.getWrappedInstance());
            }
        }

        @Override
        public void onQueueChanged(List<MediaSession.QueueItem> queue) {
            if (queue == null) {
                Log.v(TAG, "onQueueChanged: received null queue");
                return;
            }

            Log.v(TAG, "onQueueChanged: NowPlaying list changed, Queue Size = "+ queue.size());
            mHandler.sendEmptyMessage(MSG_NOW_PLAYING_CHANGED_RSP);
        }
    }

    /** Handles Avrcp messages. */
    private final class AvrcpMessageHandler extends Handler {
        private AvrcpMessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_NATIVE_REQ_GET_RC_FEATURES:
            {
                String address = (String) msg.obj;
                mFeatures = msg.arg1;
                mFeatures = modifyRcFeatureFromBlacklist(mFeatures, address);
                if (DEBUG) {
                    Log.v(TAG,
                            "MSG_NATIVE_REQ_GET_RC_FEATURES: address=" + address
                                    + ", features=" + msg.arg1 + ", mFeatures=" + mFeatures);
                }
                mAudioManager.avrcpSupportsAbsoluteVolume(address, isAbsoluteVolumeSupported());
                mLastLocalVolume = -1;
                mRemoteVolume = -1;
                mLocalVolume = -1;
                mInitialRemoteVolume = -1;
                mAddress = address;
                if (mVolumeMapping != null)
                    mVolumeMapping.clear();
                break;
            }

            case MSG_NATIVE_REQ_GET_PLAY_STATUS:
            {
                byte[] address = (byte[]) msg.obj;
                int btstate = getBluetoothPlayState(mCurrentPlayState);
                int length = (int) mMediaAttributes.getLength();
                int position = (int) getPlayPosition();
                /// M: Don't not replay songPositon=0xFFFFFFFF in GetPlaybackStaus.
                if (position == PlaybackState.PLAYBACK_POSITION_UNKNOWN) {
                    position = 0;
                }
                /// @}
                if (DEBUG)
                    Log.v(TAG, "MSG_NATIVE_REQ_GET_PLAY_STATUS, responding with state " + btstate
                                    + " len " + length + " pos " + position);
                getPlayStatusRspNative(address, btstate, length, position);
                break;
            }

            case MSG_NATIVE_REQ_GET_ELEM_ATTRS:
            {
                String[] textArray;
                AvrcpCmd.ElementAttrCmd elem = (AvrcpCmd.ElementAttrCmd) msg.obj;
                byte numAttr = elem.mNumAttr;
                int[] attrIds = elem.mAttrIDs;
                if (DEBUG) Log.v(TAG, "MSG_NATIVE_REQ_GET_ELEM_ATTRS:numAttr=" + numAttr);
                textArray = new String[numAttr];
                StringBuilder responseDebug = new StringBuilder();
                responseDebug.append("getElementAttr response: ");
                for (int i = 0; i < numAttr; ++i) {
                    textArray[i] = mMediaAttributes.getString(attrIds[i]);
                    responseDebug.append("[" + attrIds[i] + "=");
                    if (attrIds[i] == AvrcpConstants.ATTRID_TITLE
                            || attrIds[i] == AvrcpConstants.ATTRID_ARTIST
                            || attrIds[i] == AvrcpConstants.ATTRID_ALBUM) {
                        responseDebug.append(Utils.ellipsize(textArray[i]) + "] ");
                    } else {
                        responseDebug.append(textArray[i] + "] ");
                    }
                }
                Log.v(TAG, responseDebug.toString());
                byte[] bdaddr = elem.mAddress;
                getElementAttrRspNative(bdaddr, numAttr, attrIds, textArray);
                break;
            }

            case MSG_NATIVE_REQ_REGISTER_NOTIFICATION:
                if (DEBUG) Log.v(TAG, "MSG_NATIVE_REQ_REGISTER_NOTIFICATION:event=" + msg.arg1 +
                        " param=" + msg.arg2);
                processRegisterNotification((byte[]) msg.obj, msg.arg1, msg.arg2);
                break;

            case MSG_NOW_PLAYING_CHANGED_RSP:
                if (DEBUG) Log.v(TAG, "MSG_NOW_PLAYING_CHANGED_RSP");
                removeMessages(MSG_NOW_PLAYING_CHANGED_RSP);
                updateCurrentMediaState();
                break;

            case MSG_PLAY_INTERVAL_TIMEOUT:
                sendPlayPosNotificationRsp(false);
                break;

            case MSG_NATIVE_REQ_VOLUME_CHANGE:
                if (!isAbsoluteVolumeSupported()) {
                    if (DEBUG) Log.v(TAG, "MSG_NATIVE_REQ_VOLUME_CHANGE ignored, not supported");
                    break;
                }
                byte absVol = (byte) ((byte) msg.arg1 & 0x7f); // discard MSB as it is RFD
                if (DEBUG)
                    Log.v(TAG, "MSG_NATIVE_REQ_VOLUME_CHANGE: volume=" + absVol + " ctype="
                                    + msg.arg2);

                boolean volAdj = false;
                if (msg.arg2 == AVRC_RSP_ACCEPT || msg.arg2 == AVRC_RSP_REJ) {
                    if (mVolCmdAdjustInProgress == false && mVolCmdSetInProgress == false) {
                        Log.e(TAG, "Unsolicited response, ignored");
                        break;
                    }
                    removeMessages(MSG_ABS_VOL_TIMEOUT);

                    volAdj = mVolCmdAdjustInProgress;
                    mVolCmdAdjustInProgress = false;
                    mVolCmdSetInProgress = false;
                    mAbsVolRetryTimes = 0;
                }

                // convert remote volume to local volume
                int volIndex = convertToAudioStreamVolume(absVol);
                if (mInitialRemoteVolume == -1) {
                    mInitialRemoteVolume = absVol;
                    if (mAbsVolThreshold > 0 && mAbsVolThreshold < mAudioStreamMax && volIndex > mAbsVolThreshold) {
                        if (DEBUG) Log.v(TAG, "remote inital volume too high " + volIndex + ">" + mAbsVolThreshold);
                        Message msg1 = mHandler.obtainMessage(MSG_SET_ABSOLUTE_VOLUME, mAbsVolThreshold , 0);
                        mHandler.sendMessage(msg1);
                        mRemoteVolume = absVol;
                        mLocalVolume = volIndex;
                        break;
                    }
                }

                if (mLocalVolume != volIndex && (msg.arg2 == AVRC_RSP_ACCEPT ||
                                                 msg.arg2 == AVRC_RSP_CHANGED ||
                                                 msg.arg2 == AVRC_RSP_INTERIM)) {
                    /* If the volume has successfully changed */
                    mLocalVolume = volIndex;
                    if (mLastLocalVolume != -1 && msg.arg2 == AVRC_RSP_ACCEPT) {
                        if (mLastLocalVolume != volIndex) {
                            /* remote volume changed more than requested due to
                             * local and remote has different volume steps */
                            if (DEBUG) Log.d(TAG, "Remote returned volume does not match desired volume "
                                    + mLastLocalVolume + " vs " + volIndex);
                            mLastLocalVolume = mLocalVolume;
                        }
                    }
                    // remember the remote volume value, as it's the one supported by remote
                    if (volAdj) {
                        synchronized (mVolumeMapping) {
                            mVolumeMapping.put(volIndex, (int) absVol);
                            if (DEBUG) Log.v(TAG, "remember volume mapping " +volIndex+ "-"+absVol);
                        }
                    }

                    notifyVolumeChanged(mLocalVolume);
                    mRemoteVolume = absVol;
                    long pecentVolChanged = ((long) absVol * 100) / 0x7f;
                    Log.e(TAG, "percent volume changed: " + pecentVolChanged + "%");
                } else if (msg.arg2 == AVRC_RSP_REJ) {
                    Log.e(TAG, "setAbsoluteVolume call rejected");
                } else if (volAdj && mLastRemoteVolume > 0 && mLastRemoteVolume < AVRCP_MAX_VOL &&
                        mLocalVolume == volIndex &&
                        (msg.arg2 == AVRC_RSP_ACCEPT)) {
                    /* oops, the volume is still same, remote does not like the value
                     * retry a volume one step up/down */
                    if (DEBUG) Log.d(TAG, "Remote device didn't tune volume, let's try one more step.");
                    int retry_volume = Math.min(AVRCP_MAX_VOL,
                            Math.max(0, mLastRemoteVolume + mLastDirection));
                    if (setVolumeNative(retry_volume)) {
                        mLastRemoteVolume = retry_volume;
                        sendMessageDelayed(obtainMessage(MSG_ABS_VOL_TIMEOUT), CMD_TIMEOUT_DELAY);
                        mVolCmdAdjustInProgress = true;
                    }
                }
                break;

            case MSG_ADJUST_VOLUME:
                if (!isAbsoluteVolumeSupported()) {
                    if (DEBUG) Log.v(TAG, "ignore MSG_ADJUST_VOLUME");
                    break;
                }

                if (DEBUG) Log.d(TAG, "MSG_ADJUST_VOLUME: direction=" + msg.arg1);

                if (mVolCmdAdjustInProgress || mVolCmdSetInProgress) {
                    if (DEBUG) Log.w(TAG, "There is already a volume command in progress.");
                    break;
                }

                // Remote device didn't set initial volume. Let's black list it
                if (mInitialRemoteVolume == -1) {
                    Log.d(TAG, "remote " + mAddress + " never tell us initial volume, black list it.");
                    blackListCurrentDevice();
                    break;
                }

                // Wait on verification on volume from device, before changing the volume.
                if (mRemoteVolume != -1 && (msg.arg1 == -1 || msg.arg1 == 1)) {
                    int setVol = -1;
                    int targetVolIndex = -1;
                    if (mLocalVolume == 0 && msg.arg1 == -1) {
                        if (DEBUG) Log.w(TAG, "No need to Vol down from 0.");
                        break;
                    }
                    if (mLocalVolume == mAudioStreamMax && msg.arg1 == 1) {
                        if (DEBUG) Log.w(TAG, "No need to Vol up from max.");
                        break;
                    }

                    targetVolIndex = mLocalVolume + msg.arg1;
                    if (DEBUG) Log.d(TAG, "Adjusting volume to  " + targetVolIndex);

                    Integer i;
                    synchronized (mVolumeMapping) {
                        i = mVolumeMapping.get(targetVolIndex);
                    }

                    if (i != null) {
                        /* if we already know this volume mapping, use it */
                        setVol = i.byteValue();
                        if (setVol == mRemoteVolume) {
                            if (DEBUG) Log.d(TAG, "got same volume from mapping for " + targetVolIndex + ", ignore.");
                            setVol = -1;
                        }
                        if (DEBUG) Log.d(TAG, "set volume from mapping " + targetVolIndex + "-" + setVol);
                    }

                    if (setVol == -1) {
                        /* otherwise use phone steps */
                        setVol = Math.min(AVRCP_MAX_VOL,
                                convertToAvrcpVolume(Math.max(0, targetVolIndex)));
                        if (DEBUG) Log.d(TAG, "set volume from local volume "+ targetVolIndex+"-"+ setVol);
                    }

                    if (setVolumeNative(setVol)) {
                        sendMessageDelayed(obtainMessage(MSG_ABS_VOL_TIMEOUT), CMD_TIMEOUT_DELAY);
                        mVolCmdAdjustInProgress = true;
                        mLastDirection = msg.arg1;
                        mLastRemoteVolume = setVol;
                        mLastLocalVolume = targetVolIndex;
                    } else {
                         if (DEBUG) Log.d(TAG, "setVolumeNative failed");
                    }
                } else {
                    Log.e(TAG, "Unknown direction in MSG_ADJUST_VOLUME");
                }
                break;

            case MSG_SET_ABSOLUTE_VOLUME:
                if (!isAbsoluteVolumeSupported()) {
                    if (DEBUG) Log.v(TAG, "ignore MSG_SET_ABSOLUTE_VOLUME");
                    break;
                }

                if (DEBUG) Log.v(TAG, "MSG_SET_ABSOLUTE_VOLUME");

                if (mVolCmdSetInProgress || mVolCmdAdjustInProgress) {
                    if (DEBUG) Log.w(TAG, "There is already a volume command in progress.");
                    break;
                }

                // Remote device didn't set initial volume. Let's black list it
                if (mInitialRemoteVolume == -1) {
                    if (DEBUG) Log.d(TAG, "remote " + mAddress + " never tell us initial volume, black list it.");
                    blackListCurrentDevice();
                    break;
                }

                int avrcpVolume = convertToAvrcpVolume(msg.arg1);
                avrcpVolume = Math.min(AVRCP_MAX_VOL, Math.max(0, avrcpVolume));
                if (DEBUG) Log.d(TAG, "Setting volume to " + msg.arg1 + "-" + avrcpVolume);
                if (setVolumeNative(avrcpVolume)) {
                    sendMessageDelayed(obtainMessage(MSG_ABS_VOL_TIMEOUT), CMD_TIMEOUT_DELAY);
                    mVolCmdSetInProgress = true;
                    mLastRemoteVolume = avrcpVolume;
                    mLastLocalVolume = msg.arg1;
                } else {
                     if (DEBUG) Log.d(TAG, "setVolumeNative failed");
                }
                break;

            case MSG_ABS_VOL_TIMEOUT:
                if (DEBUG) Log.v(TAG, "MSG_ABS_VOL_TIMEOUT: Volume change cmd timed out.");
                mVolCmdAdjustInProgress = false;
                mVolCmdSetInProgress = false;
                if (mAbsVolRetryTimes >= MAX_ERROR_RETRY_TIMES) {
                    mAbsVolRetryTimes = 0;
                    /* too many volume change failures, black list the device */
                    blackListCurrentDevice();
                } else {
                    mAbsVolRetryTimes += 1;
                    if (setVolumeNative(mLastRemoteVolume)) {
                        sendMessageDelayed(obtainMessage(MSG_ABS_VOL_TIMEOUT), CMD_TIMEOUT_DELAY);
                        mVolCmdSetInProgress = true;
                    }
                }
                break;

            case MSG_SET_A2DP_AUDIO_STATE:
                if (DEBUG) Log.v(TAG, "MSG_SET_A2DP_AUDIO_STATE:" + msg.arg1);
                mA2dpState = msg.arg1;
                updateCurrentMediaState();
                break;

            case MSG_NATIVE_REQ_GET_FOLDER_ITEMS: {
                AvrcpCmd.FolderItemsCmd folderObj = (AvrcpCmd.FolderItemsCmd) msg.obj;
                if (DEBUG) Log.v(TAG, "MSG_NATIVE_REQ_GET_FOLDER_ITEMS " + folderObj);
                switch (folderObj.mScope) {
                    case AvrcpConstants.BTRC_SCOPE_PLAYER_LIST:
                        handleMediaPlayerListRsp(folderObj);
                        break;
                    case AvrcpConstants.BTRC_SCOPE_FILE_SYSTEM:
                    case AvrcpConstants.BTRC_SCOPE_NOW_PLAYING:
                        handleGetFolderItemBrowseResponse(folderObj, folderObj.mAddress);
                        break;
                    default:
                        Log.e(TAG, "unknown scope for getfolderitems. scope = "
                                + folderObj.mScope);
                        getFolderItemsRspNative(folderObj.mAddress,
                                AvrcpConstants.RSP_INV_SCOPE, (short) 0, (byte) 0, 0,
                                null, null, null, null, null, null, null, null);
                }
                break;
            }

            case MSG_NATIVE_REQ_SET_ADDR_PLAYER:
                // object is bdaddr, argument 1 is the selected player id
                if (DEBUG) Log.v(TAG, "MSG_NATIVE_REQ_SET_ADDR_PLAYER id=" + msg.arg1);
                setAddressedPlayer((byte[]) msg.obj, msg.arg1);
                break;

            case MSG_NATIVE_REQ_GET_ITEM_ATTR:
                // msg object contains the item attribute object
                AvrcpCmd.ItemAttrCmd cmd = (AvrcpCmd.ItemAttrCmd) msg.obj;
                if (DEBUG) Log.v(TAG, "MSG_NATIVE_REQ_GET_ITEM_ATTR " + cmd);
                handleGetItemAttr(cmd);
                break;

            case MSG_NATIVE_REQ_SET_BR_PLAYER:
                // argument 1 is the selected player id
                if (DEBUG) Log.v(TAG, "MSG_NATIVE_REQ_SET_BR_PLAYER id=" + msg.arg1);
                setBrowsedPlayer((byte[]) msg.obj, msg.arg1);
                break;

            case MSG_NATIVE_REQ_CHANGE_PATH:
            {
                if (DEBUG) Log.v(TAG, "MSG_NATIVE_REQ_CHANGE_PATH");
                Bundle data = msg.getData();
                byte[] bdaddr = data.getByteArray("BdAddress");
                byte[] folderUid = data.getByteArray("folderUid");
                byte direction = data.getByte("direction");
                if (mAvrcpBrowseManager.getBrowsedMediaPlayer(bdaddr) != null) {
                        mAvrcpBrowseManager.getBrowsedMediaPlayer(bdaddr).changePath(folderUid,
                        direction);
                } else {
                    Log.e(TAG, "Remote requesting change path before setbrowsedplayer");
                    changePathRspNative(bdaddr, AvrcpConstants.RSP_BAD_CMD, 0);
                }
                break;
            }

            case MSG_NATIVE_REQ_PLAY_ITEM:
            {
                Bundle data = msg.getData();
                byte[] bdaddr = data.getByteArray("BdAddress");
                byte[] uid = data.getByteArray("uid");
                byte scope = data.getByte("scope");
                if (DEBUG)
                    Log.v(TAG, "MSG_NATIVE_REQ_PLAY_ITEM scope=" + scope + " id="
                                    + Utils.byteArrayToString(uid));
                handlePlayItemResponse(bdaddr, uid, scope);
                break;
            }

            case MSG_NATIVE_REQ_GET_TOTAL_NUM_OF_ITEMS:
                if (DEBUG) Log.v(TAG, "MSG_NATIVE_REQ_GET_TOTAL_NUM_OF_ITEMS scope=" + msg.arg1);
                // argument 1 is scope, object is bdaddr
                handleGetTotalNumOfItemsResponse((byte[]) msg.obj, (byte) msg.arg1);
                break;

            case MSG_NATIVE_REQ_PASS_THROUGH:
                if (DEBUG)
                    Log.v(TAG, "MSG_NATIVE_REQ_PASS_THROUGH: id=" + msg.arg1 + " st=" + msg.arg2);
                // argument 1 is id, argument 2 is keyState
                handlePassthroughCmd(msg.arg1, msg.arg2);
                break;

            default:
                Log.e(TAG, "unknown message! msg.what=" + msg.what);
                break;
            }
        }
    }

    private PlaybackState updatePlaybackState() {
        PlaybackState newState = new PlaybackState.Builder()
                                         .setState(PlaybackState.STATE_NONE,
                                                 PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0.0f)
                                         .build();
        synchronized (this) {
            PlaybackState controllerState = null;
            if (mMediaController != null) {
                controllerState = mMediaController.getPlaybackState();
            }

            if (controllerState != null) {
                newState = controllerState;
            }
            // Use the AudioManager to update the playback state.
            // NOTE: We cannot use the
            //    (mA2dpState == BluetoothA2dp.STATE_PLAYING)
            // check, because after Pause, the A2DP state remains in
            // STATE_PLAYING for 3 more seconds.
            // As a result of that, if we pause the music, on carkits the
            // Play status indicator will continue to display "Playing"
            // for 3 more seconds which can be confusing.
            if ((mAudioManagerIsPlaying && newState.getState() != PlaybackState.STATE_PLAYING)
                    || (controllerState == null && mAudioManager != null
                               && mAudioManager.isMusicActive())) {
                // Use AudioManager playback state if we don't have the state
                // from MediaControlller
                PlaybackState.Builder builder = new PlaybackState.Builder();
                if (mAudioManagerIsPlaying) {
                    builder.setState(PlaybackState.STATE_PLAYING,
                            PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f);
                } else {
                    builder.setState(PlaybackState.STATE_PAUSED,
                            PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0.0f);
                }
                newState = builder.build();
            }
        }

        byte newPlayStatus = getBluetoothPlayState(newState);

        /* update play status in global media player list */
        MediaPlayerInfo player = getAddressedPlayerInfo();
        if (player != null) {
            player.setPlayStatus(newPlayStatus);
        }

        if (DEBUG) {
            Log.v(TAG, "updatePlaybackState (" + mPlayStatusChangedNT + "): " + mReportedPlayStatus
                            + "➡" + newPlayStatus + "(" + newState + ")");
        }

        if (newState != null) mCurrentPlayState = newState;

        return mCurrentPlayState;
    }

    private void sendPlaybackStatus(int playStatusChangedNT, byte playbackState) {
        registerNotificationRspPlayStatusNative(playStatusChangedNT, playbackState);
        mPlayStatusChangedNT = playStatusChangedNT;
        mReportedPlayStatus = playbackState;
    }

    private void updateTransportControls(int transportControlFlags) {
        mTransportControlFlags = transportControlFlags;
    }

    class MediaAttributes {
        private boolean exists;
        private String title;
        private String artistName;
        private String albumName;
        private String mediaNumber;
        private String mediaTotalNumber;
        private String genre;
        private long playingTimeMs;

        private static final int ATTR_TITLE = 1;
        private static final int ATTR_ARTIST_NAME = 2;
        private static final int ATTR_ALBUM_NAME = 3;
        private static final int ATTR_MEDIA_NUMBER = 4;
        private static final int ATTR_MEDIA_TOTAL_NUMBER = 5;
        private static final int ATTR_GENRE = 6;
        private static final int ATTR_PLAYING_TIME_MS = 7;


        public MediaAttributes(MediaMetadata data) {
            exists = data != null;
            if (!exists)
                return;

            artistName = stringOrBlank(data.getString(MediaMetadata.METADATA_KEY_ARTIST));
            albumName = stringOrBlank(data.getString(MediaMetadata.METADATA_KEY_ALBUM));
            mediaNumber = longStringOrBlank(data.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER));
            mediaTotalNumber = longStringOrBlank(data.getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS));
            genre = stringOrBlank(data.getString(MediaMetadata.METADATA_KEY_GENRE));
            playingTimeMs = data.getLong(MediaMetadata.METADATA_KEY_DURATION);

            // Try harder for the title.
            title = data.getString(MediaMetadata.METADATA_KEY_TITLE);

            if (title == null) {
                MediaDescription desc = data.getDescription();
                if (desc != null) {
                    CharSequence val = desc.getDescription();
                    if (val != null)
                        title = val.toString();
                }
            }

            if (title == null)
                title = new String();
        }

        public long getLength() {
            if (!exists) return 0L;
            return playingTimeMs;
        }

        public boolean equals(MediaAttributes other) {
            if (other == null)
                return false;

            if (exists != other.exists)
                return false;

            if (exists == false)
                return true;

            return (title.equals(other.title)) && (artistName.equals(other.artistName))
                    && (albumName.equals(other.albumName))
                    && (mediaNumber.equals(other.mediaNumber))
                    && (mediaTotalNumber.equals(other.mediaTotalNumber))
                    && (genre.equals(other.genre)) && (playingTimeMs == other.playingTimeMs);
        }

        public String getString(int attrId) {
            if (!exists)
                return new String();

            switch (attrId) {
                case ATTR_TITLE:
                    return title;
                case ATTR_ARTIST_NAME:
                    return artistName;
                case ATTR_ALBUM_NAME:
                    return albumName;
                case ATTR_MEDIA_NUMBER:
                    return mediaNumber;
                case ATTR_MEDIA_TOTAL_NUMBER:
                    return mediaTotalNumber;
                case ATTR_GENRE:
                    return genre;
                case ATTR_PLAYING_TIME_MS:
                    return Long.toString(playingTimeMs);
                default:
                    return new String();
            }
        }

        private String stringOrBlank(String s) {
            return s == null ? new String() : s;
        }

        private String longStringOrBlank(Long s) {
            return s == null ? new String() : s.toString();
        }

        public String toString() {
            if (!exists) {
                return "[MediaAttributes: none]";
            }

            return "[MediaAttributes: " + title + " - " + albumName + " by " + artistName + " ("
                    + playingTimeMs + " " + mediaNumber + "/" + mediaTotalNumber + ") " + genre
                    + "]";
        }

        public String toRedactedString() {
            if (!exists) {
                return "[MediaAttributes: none]";
            }

            return "[MediaAttributes: " + Utils.ellipsize(title) + " - "
                    + Utils.ellipsize(albumName) + " by " + Utils.ellipsize(artistName) + " ("
                    + playingTimeMs + " " + mediaNumber + "/" + mediaTotalNumber + ") " + genre
                    + "]";
        }
    }

    private void updateCurrentMediaState() {
        // Only do player updates when we aren't registering for track changes.
        MediaAttributes currentAttributes;
        PlaybackState newState = updatePlaybackState();

        synchronized (this) {
            if (mMediaController == null) {
                currentAttributes = new MediaAttributes(null);
            } else {
                currentAttributes = new MediaAttributes(mMediaController.getMetadata());
            }
        }

        byte newPlayStatus = getBluetoothPlayState(newState);

        if (newState.getState() != PlaybackState.STATE_BUFFERING
                && newState.getState() != PlaybackState.STATE_NONE) {
            long newQueueId = MediaSession.QueueItem.UNKNOWN_ID;
            if (newState != null) newQueueId = newState.getActiveQueueItemId();
            Log.v(TAG, "Media update: id " + mLastQueueId + "➡" + newQueueId + "? "
                            + currentAttributes.toRedactedString() + " : "
                            + mMediaAttributes.toRedactedString());

            if (mAvailablePlayerViewChanged) {
                registerNotificationRspAvalPlayerChangedNative(
                        AvrcpConstants.NOTIFICATION_TYPE_CHANGED);
                mAvailablePlayerViewChanged = false;
                return;
            }

            if (mAddrPlayerChangedNT == AvrcpConstants.NOTIFICATION_TYPE_INTERIM
                    && mReportedPlayerID != mCurrAddrPlayerID) {
                registerNotificationRspAvalPlayerChangedNative(
                        AvrcpConstants.NOTIFICATION_TYPE_CHANGED);
                registerNotificationRspAddrPlayerChangedNative(
                        AvrcpConstants.NOTIFICATION_TYPE_CHANGED, mCurrAddrPlayerID, sUIDCounter);

                mAvailablePlayerViewChanged = false;
                mAddrPlayerChangedNT = AvrcpConstants.NOTIFICATION_TYPE_CHANGED;
                mReportedPlayerID = mCurrAddrPlayerID;

                // Update the now playing list without sending the notification
                mNowPlayingListChangedNT = AvrcpConstants.NOTIFICATION_TYPE_CHANGED;
                mAddressedMediaPlayer.updateNowPlayingList(mMediaController);
                mNowPlayingListChangedNT = AvrcpConstants.NOTIFICATION_TYPE_INTERIM;
            }

            // Dont send now playing list changed if the player doesn't support browsing
            MediaPlayerInfo info = getAddressedPlayerInfo();
            if (info != null && info.isBrowseSupported()) {
                Log.v(TAG, "Check if NowPlayingList is updated");
                mAddressedMediaPlayer.updateNowPlayingList(mMediaController);
            }

            // Notify track changed if:
            //  - The CT is registered for the notification
            //  - Queue ID is UNKNOWN and MediaMetadata is different
            //  - Queue ID is valid and different from last Queue ID sent
            if ((newQueueId == -1 || newQueueId != mLastQueueId)
                    && !currentAttributes.equals(mMediaAttributes)) {
                Log.v(TAG, "Send track changed");
                mMediaAttributes = currentAttributes;
                mLastQueueId = newQueueId;
                sendTrackChangedRsp(false);
            }
        } else {
            Log.i(TAG, "Skipping update due to invalid playback state");
        }

        // still send the updated play state if the playback state is none or buffering
        Log.e(TAG,
                "play status change " + mReportedPlayStatus + "➡" + newPlayStatus
                        + " mPlayStatusChangedNT: " + mPlayStatusChangedNT);
        if (mPlayStatusChangedNT == AvrcpConstants.NOTIFICATION_TYPE_INTERIM
                || (mReportedPlayStatus != newPlayStatus)) {
            sendPlaybackStatus(AvrcpConstants.NOTIFICATION_TYPE_CHANGED, newPlayStatus);
        }

        sendPlayPosNotificationRsp(false);
    }

    private void getRcFeaturesRequestFromNative(byte[] address, int features) {
        Message msg = mHandler.obtainMessage(MSG_NATIVE_REQ_GET_RC_FEATURES, features, 0,
                Utils.getAddressStringFromByte(address));
        mHandler.sendMessage(msg);
    }

    private void getPlayStatusRequestFromNative(byte[] address) {
        Message msg = mHandler.obtainMessage(MSG_NATIVE_REQ_GET_PLAY_STATUS);
        msg.obj = address;
        mHandler.sendMessage(msg);
    }

    private void getElementAttrRequestFromNative(byte[] address, byte numAttr, int[] attrs) {
        AvrcpCmd avrcpCmdobj = new AvrcpCmd();
        AvrcpCmd.ElementAttrCmd elemAttr = avrcpCmdobj.new ElementAttrCmd(address, numAttr, attrs);
        Message msg = mHandler.obtainMessage(MSG_NATIVE_REQ_GET_ELEM_ATTRS);
        msg.obj = elemAttr;
        mHandler.sendMessage(msg);
    }

    private void registerNotificationRequestFromNative(byte[] address,int eventId, int param) {
        Message msg = mHandler.obtainMessage(MSG_NATIVE_REQ_REGISTER_NOTIFICATION, eventId, param);
        msg.obj = address;
        mHandler.sendMessage(msg);
    }

    private void processRegisterNotification(byte[] address, int eventId, int param) {
        switch (eventId) {
            case EVT_PLAY_STATUS_CHANGED:
                mPlayStatusChangedNT = AvrcpConstants.NOTIFICATION_TYPE_CHANGED;
                updatePlaybackState();
                sendPlaybackStatus(AvrcpConstants.NOTIFICATION_TYPE_INTERIM, mReportedPlayStatus);
                break;

            case EVT_TRACK_CHANGED:
                Log.v(TAG, "Track changed notification enabled");
                mTrackChangedNT = AvrcpConstants.NOTIFICATION_TYPE_INTERIM;
                sendTrackChangedRsp(true);
                break;

            case EVT_PLAY_POS_CHANGED:
                mPlayPosChangedNT = AvrcpConstants.NOTIFICATION_TYPE_INTERIM;
                mPlaybackIntervalMs = (long) param * 1000L;
                sendPlayPosNotificationRsp(true);
                break;

            case EVT_AVBL_PLAYERS_CHANGED:
                /* Notify remote available players changed */
                if (DEBUG) Log.d(TAG, "Available Players notification enabled");
                registerNotificationRspAvalPlayerChangedNative(
                        AvrcpConstants.NOTIFICATION_TYPE_INTERIM);
                break;

            case EVT_ADDR_PLAYER_CHANGED:
                /* Notify remote addressed players changed */
                if (DEBUG) Log.d(TAG, "Addressed Player notification enabled");
                registerNotificationRspAddrPlayerChangedNative(
                        AvrcpConstants.NOTIFICATION_TYPE_INTERIM,
                        mCurrAddrPlayerID, sUIDCounter);
                mAddrPlayerChangedNT = AvrcpConstants.NOTIFICATION_TYPE_INTERIM;
                mReportedPlayerID = mCurrAddrPlayerID;
                break;

            case EVENT_UIDS_CHANGED:
                if (DEBUG) Log.d(TAG, "UIDs changed notification enabled");
                registerNotificationRspUIDsChangedNative(
                        AvrcpConstants.NOTIFICATION_TYPE_INTERIM, sUIDCounter);
                break;

            case EVENT_NOW_PLAYING_CONTENT_CHANGED:
                if (DEBUG) Log.d(TAG, "Now Playing List changed notification enabled");
                /* send interim response to remote device */
                mNowPlayingListChangedNT = AvrcpConstants.NOTIFICATION_TYPE_INTERIM;
                if (!registerNotificationRspNowPlayingChangedNative(
                        AvrcpConstants.NOTIFICATION_TYPE_INTERIM)) {
                    Log.e(TAG, "EVENT_NOW_PLAYING_CONTENT_CHANGED: " +
                            "registerNotificationRspNowPlayingChangedNative for Interim rsp failed!");
                }
                break;
        }
    }

    private void handlePassthroughCmdRequestFromNative(byte[] address, int id, int keyState) {
        Message msg = mHandler.obtainMessage(MSG_NATIVE_REQ_PASS_THROUGH, id, keyState);
        mHandler.sendMessage(msg);
    }

    private void sendTrackChangedRsp(boolean registering) {
        if (!registering && mTrackChangedNT != AvrcpConstants.NOTIFICATION_TYPE_INTERIM) {
            if (DEBUG) Log.d(TAG, "sendTrackChangedRsp: Not registered or registering.");
            return;
        }

        mTrackChangedNT = AvrcpConstants.NOTIFICATION_TYPE_CHANGED;
        if (registering) mTrackChangedNT = AvrcpConstants.NOTIFICATION_TYPE_INTERIM;

        MediaPlayerInfo info = getAddressedPlayerInfo();
        // for non-browsable players or no player
        if (info != null && !info.isBrowseSupported()) {
            byte[] track = AvrcpConstants.TRACK_IS_SELECTED;
            if (!mMediaAttributes.exists) track = AvrcpConstants.NO_TRACK_SELECTED;
            registerNotificationRspTrackChangeNative(mTrackChangedNT, track);
            return;
        }

        mAddressedMediaPlayer.sendTrackChangeWithId(mTrackChangedNT, mMediaController);
    }

    private long getPlayPosition() {
        if (mCurrentPlayState == null) {
            return -1L;
        }

        if (mCurrentPlayState.getPosition() == PlaybackState.PLAYBACK_POSITION_UNKNOWN) {
            return -1L;
        }

        if (isPlayingState(mCurrentPlayState)) {
            long sinceUpdate =
                    (SystemClock.elapsedRealtime() - mCurrentPlayState.getLastPositionUpdateTime());
            return sinceUpdate + mCurrentPlayState.getPosition();
        }

        return mCurrentPlayState.getPosition();
    }

    private boolean isPlayingState(@Nullable PlaybackState state) {
        if (state == null) return false;
        return (state != null) && (state.getState() == PlaybackState.STATE_PLAYING);
    }

    /**
     * Sends a play position notification, or schedules one to be
     * sent later at an appropriate time. If |requested| is true,
     * does both because this was called in reponse to a request from the
     * TG.
     */
    private void sendPlayPosNotificationRsp(boolean requested) {
        if (!requested && mPlayPosChangedNT != AvrcpConstants.NOTIFICATION_TYPE_INTERIM) {
            if (DEBUG) Log.d(TAG, "sendPlayPosNotificationRsp: Not registered or requesting.");
            return;
        }

        long playPositionMs = getPlayPosition();
        String debugLine = "sendPlayPosNotificationRsp: ";

        // mNextPosMs is set to -1 when the previous position was invalid
        // so this will be true if the new position is valid & old was invalid.
        // mPlayPositionMs is set to -1 when the new position is invalid,
        // and the old mPrevPosMs is >= 0 so this is true when the new is invalid
        // and the old was valid.
        if (DEBUG) {
            debugLine += "(" + requested + ") " + mPrevPosMs + " <=? " + playPositionMs + " <=? "
                    + mNextPosMs;
            if (isPlayingState(mCurrentPlayState)) debugLine += " Playing";
            debugLine += " State: " + mCurrentPlayState.getState();
        }
        if (requested || ((mLastReportedPosition != playPositionMs) &&
                (playPositionMs >= mNextPosMs) || (playPositionMs <= mPrevPosMs))) {
            if (!requested) mPlayPosChangedNT = AvrcpConstants.NOTIFICATION_TYPE_CHANGED;
            /// M: The PlayPosition notification value should be 0 instead of -1(0xFFFFFFFF)
            if (playPositionMs == PlaybackState.PLAYBACK_POSITION_UNKNOWN) {
                registerNotificationRspPlayPosNative(mPlayPosChangedNT, 0);
            } else {
                registerNotificationRspPlayPosNative(mPlayPosChangedNT, (int) playPositionMs);
            }
            /// @}
            mLastReportedPosition = playPositionMs;
            if (playPositionMs != PlaybackState.PLAYBACK_POSITION_UNKNOWN) {
                mNextPosMs = playPositionMs + mPlaybackIntervalMs;
                mPrevPosMs = playPositionMs - mPlaybackIntervalMs;
                /// M: mPrevPosMs should greater than or equal to 0.
                if (mPrevPosMs < 0) {
                    mPrevPosMs = 0;
                }
                /// @}
            } else {
                mNextPosMs = -1;
                mPrevPosMs = -1;
            }
        }

        mHandler.removeMessages(MSG_PLAY_INTERVAL_TIMEOUT);
        if (mPlayPosChangedNT == AvrcpConstants.NOTIFICATION_TYPE_INTERIM && isPlayingState(mCurrentPlayState)) {
            Message msg = mHandler.obtainMessage(MSG_PLAY_INTERVAL_TIMEOUT);
            long delay = mPlaybackIntervalMs;
            if (mNextPosMs != -1) {
                delay = mNextPosMs - (playPositionMs > 0 ? playPositionMs : 0);
            }
            if (DEBUG) debugLine += " Timeout " + delay + "ms";
            mHandler.sendMessageDelayed(msg, delay);
        }
        if (DEBUG) Log.d(TAG, debugLine);
    }

    /**
     * This is called from AudioService. It will return whether this device supports abs volume.
     * NOT USED AT THE MOMENT.
     */
    public boolean isAbsoluteVolumeSupported() {
        return ((mFeatures & BTRC_FEAT_ABSOLUTE_VOLUME) != 0);
    }

    /**
     * We get this call from AudioService. This will send a message to our handler object,
     * requesting our handler to call setVolumeNative()
     */
    public void adjustVolume(int direction) {
        Message msg = mHandler.obtainMessage(MSG_ADJUST_VOLUME, direction, 0);
        mHandler.sendMessage(msg);
    }

    public void setAbsoluteVolume(int volume) {
        if (volume == mLocalVolume) {
            if (DEBUG) Log.v(TAG, "setAbsoluteVolume is setting same index, ignore "+volume);
            return;
        }

        mHandler.removeMessages(MSG_ADJUST_VOLUME);
        Message msg = mHandler.obtainMessage(MSG_SET_ABSOLUTE_VOLUME, volume, 0);
        mHandler.sendMessage(msg);
    }

    /* Called in the native layer as a btrc_callback to return the volume set on the carkit in the
     * case when the volume is change locally on the carkit. This notification is not called when
     * the volume is changed from the phone.
     *
     * This method will send a message to our handler to change the local stored volume and notify
     * AudioService to update the UI
     */
    private void volumeChangeRequestFromNative(byte[] address, int volume, int ctype) {
        Message msg = mHandler.obtainMessage(MSG_NATIVE_REQ_VOLUME_CHANGE, volume, ctype);
        Bundle data = new Bundle();
        data.putByteArray("BdAddress" , address);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    private void getFolderItemsRequestFromNative(
            byte[] address, byte scope, long startItem, long endItem, byte numAttr, int[] attrIds) {
        AvrcpCmd avrcpCmdobj = new AvrcpCmd();
        AvrcpCmd.FolderItemsCmd folderObj = avrcpCmdobj.new FolderItemsCmd(address, scope,
                startItem, endItem, numAttr, attrIds);
        Message msg = mHandler.obtainMessage(MSG_NATIVE_REQ_GET_FOLDER_ITEMS, 0, 0);
        msg.obj = folderObj;
        mHandler.sendMessage(msg);
    }

    private void setAddressedPlayerRequestFromNative(byte[] address, int playerId) {
        Message msg = mHandler.obtainMessage(MSG_NATIVE_REQ_SET_ADDR_PLAYER, playerId, 0);
        msg.obj = address;
        mHandler.sendMessage(msg);
    }

    private void setBrowsedPlayerRequestFromNative(byte[] address, int playerId) {
        Message msg = mHandler.obtainMessage(MSG_NATIVE_REQ_SET_BR_PLAYER, playerId, 0);
        msg.obj = address;
        mHandler.sendMessage(msg);
    }

    private void changePathRequestFromNative(byte[] address, byte direction, byte[] folderUid) {
        Bundle data = new Bundle();
        Message msg = mHandler.obtainMessage(MSG_NATIVE_REQ_CHANGE_PATH);
        data.putByteArray("BdAddress" , address);
        data.putByteArray("folderUid" , folderUid);
        data.putByte("direction" , direction);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    private void getItemAttrRequestFromNative(byte[] address, byte scope, byte[] itemUid, int uidCounter,
            byte numAttr, int[] attrs) {
        AvrcpCmd avrcpCmdobj = new AvrcpCmd();
        AvrcpCmd.ItemAttrCmd itemAttr = avrcpCmdobj.new ItemAttrCmd(address, scope,
                itemUid, uidCounter, numAttr, attrs);
        Message msg = mHandler.obtainMessage(MSG_NATIVE_REQ_GET_ITEM_ATTR);
        msg.obj = itemAttr;
        mHandler.sendMessage(msg);
    }

    private void searchRequestFromNative(byte[] address, int charsetId, byte[] searchStr) {
        /* Search is not supported */
        Log.w(TAG, "searchRequestFromNative: search is not supported");
        searchRspNative(address, AvrcpConstants.RSP_SRCH_NOT_SPRTD, 0, 0);
    }

    private void playItemRequestFromNative(byte[] address, byte scope, int uidCounter, byte[] uid) {
        Bundle data = new Bundle();
        Message msg = mHandler.obtainMessage(MSG_NATIVE_REQ_PLAY_ITEM);
        data.putByteArray("BdAddress" , address);
        data.putByteArray("uid" , uid);
        data.putInt("uidCounter" , uidCounter);
        data.putByte("scope" , scope);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    private void addToPlayListRequestFromNative(byte[] address, byte scope, byte[] uid, int uidCounter) {
        /* add to NowPlaying not supported */
        Log.w(TAG, "addToPlayListRequestFromNative: not supported! scope=" + scope);
        addToNowPlayingRspNative(address, AvrcpConstants.RSP_INTERNAL_ERR);
    }

    private void getTotalNumOfItemsRequestFromNative(byte[] address, byte scope) {
        Bundle data = new Bundle();
        Message msg = mHandler.obtainMessage(MSG_NATIVE_REQ_GET_TOTAL_NUM_OF_ITEMS);
        msg.arg1 = scope;
        msg.obj = address;
        mHandler.sendMessage(msg);
    }

    private void notifyVolumeChanged(int volume) {
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume,
                      AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_BLUETOOTH_ABS_VOLUME);
    }

    private int convertToAudioStreamVolume(int volume) {
        // Rescale volume to match AudioSystem's volume
        return (int) Math.floor((double) volume*mAudioStreamMax/AVRCP_MAX_VOL);
    }

    private int convertToAvrcpVolume(int volume) {
        return (int) Math.ceil((double) volume*AVRCP_MAX_VOL/mAudioStreamMax);
    }

    private void blackListCurrentDevice() {
        mFeatures &= ~BTRC_FEAT_ABSOLUTE_VOLUME;
        mAudioManager.avrcpSupportsAbsoluteVolume(mAddress, isAbsoluteVolumeSupported());

        SharedPreferences pref = mContext.getSharedPreferences(ABSOLUTE_VOLUME_BLACKLIST,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(mAddress, true);
        editor.apply();
    }

    private int modifyRcFeatureFromBlacklist(int feature, String address) {
        SharedPreferences pref = mContext.getSharedPreferences(ABSOLUTE_VOLUME_BLACKLIST,
                Context.MODE_PRIVATE);
        if (!pref.contains(address)) {
            return feature;
        }
        if (pref.getBoolean(address, false)) {
            feature &= ~BTRC_FEAT_ABSOLUTE_VOLUME;
        }
        return feature;
    }

    public void resetBlackList(String address) {
        SharedPreferences pref = mContext.getSharedPreferences(ABSOLUTE_VOLUME_BLACKLIST,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.remove(address);
        editor.apply();
    }

    /**
     * This is called from A2dpStateMachine to set A2dp audio state.
     */
    public void setA2dpAudioState(int state) {
        Message msg = mHandler.obtainMessage(MSG_SET_A2DP_AUDIO_STATE, state, 0);
        mHandler.sendMessage(msg);
    }

    private class AvrcpServiceBootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_USER_UNLOCKED)) {
                if (DEBUG) Log.d(TAG, "User unlocked, initializing player lists");
                /* initializing media player's list */
                buildBrowsablePlayerList();
            }
        }
    }

    private class AvrcpServiceBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DEBUG) Log.d(TAG, "AvrcpServiceBroadcastReceiver-> Action: " + action);

            if (action.equals(Intent.ACTION_PACKAGE_REMOVED)
                    || action.equals(Intent.ACTION_PACKAGE_DATA_CLEARED)) {
                if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    // a package is being removed, not replaced
                    String packageName = intent.getData().getSchemeSpecificPart();
                    if (packageName != null) {
                        handlePackageModified(packageName, true);
                    }
                }

            } else if (action.equals(Intent.ACTION_PACKAGE_ADDED)
                    || action.equals(Intent.ACTION_PACKAGE_CHANGED)) {
                String packageName = intent.getData().getSchemeSpecificPart();
                if (DEBUG) Log.d(TAG,"AvrcpServiceBroadcastReceiver-> packageName: "
                        + packageName);
                if (packageName != null) {
                    handlePackageModified(packageName, false);
                }
            }
        }
    }

    private void handlePackageModified(String packageName, boolean removed) {
        if (DEBUG) Log.d(TAG, "packageName: " + packageName + " removed: " + removed);

        if (removed) {
            removeMediaPlayerInfo(packageName);
            // old package is removed, updating local browsable player's list
            if (isBrowseSupported(packageName)) {
                removePackageFromBrowseList(packageName);
            }
        } else {
            // new package has been added.
            if (isBrowsableListUpdated(packageName)) {
                // Rebuilding browsable players list
                buildBrowsablePlayerList();
            }
        }
    }

    private boolean isBrowsableListUpdated(String newPackageName) {
        // getting the browsable media players list from package manager
        Intent intent = new Intent("android.media.browse.MediaBrowserService");
        List<ResolveInfo> resInfos = mPackageManager.queryIntentServices(intent,
                                         PackageManager.MATCH_ALL);
        for (ResolveInfo resolveInfo : resInfos) {
            if (resolveInfo.serviceInfo.packageName.equals(newPackageName)) {
                if (DEBUG)
                    Log.d(TAG,
                            "isBrowsableListUpdated: package includes MediaBrowserService, true");
                return true;
            }
        }

        // if list has different size
        if (resInfos.size() != mBrowsePlayerInfoList.size()) {
            if (DEBUG) Log.d(TAG, "isBrowsableListUpdated: browsable list size mismatch, true");
            return true;
        }

        Log.d(TAG, "isBrowsableListUpdated: false");
        return false;
    }

    private void removePackageFromBrowseList(String packageName) {
        if (DEBUG) Log.d(TAG, "removePackageFromBrowseList: " + packageName);
        synchronized (mBrowsePlayerInfoList) {
            int browseInfoID = getBrowseId(packageName);
            if (browseInfoID != -1) {
                mBrowsePlayerInfoList.remove(browseInfoID);
            }
        }
    }

    /*
     * utility function to get the browse player index from global browsable
     * list. It may return -1 if specified package name is not in the list.
     */
    private int getBrowseId(String packageName) {
        boolean response = false;
        int browseInfoID = 0;
        synchronized (mBrowsePlayerInfoList) {
            for (BrowsePlayerInfo info : mBrowsePlayerInfoList) {
                if (info.packageName.equals(packageName)) {
                    response = true;
                    break;
                }
                browseInfoID++;
            }
        }

        if (!response) {
            browseInfoID = -1;
        }

        if (DEBUG) Log.d(TAG, "getBrowseId for packageName: " + packageName +
                " , browseInfoID: " + browseInfoID);
        return browseInfoID;
    }

    private void setAddressedPlayer(byte[] bdaddr, int selectedId) {
        String functionTag = "setAddressedPlayer(" + selectedId + "): ";

        synchronized (mMediaPlayerInfoList) {
            if (mMediaPlayerInfoList.isEmpty()) {
                Log.w(TAG, functionTag + "no players, send no available players");
                setAddressedPlayerRspNative(bdaddr, AvrcpConstants.RSP_NO_AVBL_PLAY);
                return;
            }
            if (!mMediaPlayerInfoList.containsKey(selectedId)) {
                Log.w(TAG, functionTag + "invalid id, sending response back ");
                setAddressedPlayerRspNative(bdaddr, AvrcpConstants.RSP_INV_PLAYER);
                return;
            }

            if (isPlayerAlreadyAddressed(selectedId)) {
                MediaPlayerInfo info = getAddressedPlayerInfo();
                Log.i(TAG, functionTag + "player already addressed: " + info);
                setAddressedPlayerRspNative(bdaddr, AvrcpConstants.RSP_NO_ERROR);
                return;
            }
            // register new Media Controller Callback and update the current IDs
            if (!updateCurrentController(selectedId, mCurrBrowsePlayerID)) {
                Log.e(TAG, functionTag + "updateCurrentController failed!");
                setAddressedPlayerRspNative(bdaddr, AvrcpConstants.RSP_INTERNAL_ERR);
                return;
            }
            // If we don't have a controller, try to launch the player
            MediaPlayerInfo info = getAddressedPlayerInfo();
            if (info.getMediaController() == null) {
                Intent launch = mPackageManager.getLaunchIntentForPackage(info.getPackageName());
                Log.i(TAG, functionTag + "launching player " + launch);
                mContext.startActivity(launch);
            }
        }
        setAddressedPlayerRspNative(bdaddr, AvrcpConstants.RSP_NO_ERROR);
    }

    private void setBrowsedPlayer(byte[] bdaddr, int selectedId) {
        int status = AvrcpConstants.RSP_NO_ERROR;

        // checking for error cases
        if (mMediaPlayerInfoList.isEmpty()) {
            status = AvrcpConstants.RSP_NO_AVBL_PLAY;
            Log.w(TAG, "setBrowsedPlayer: No available players! ");
        } else {
            // Workaround for broken controllers selecting ID 0
            // Seen at least on Ford, Chevrolet MyLink
            if (selectedId == 0) {
                Log.w(TAG, "setBrowsedPlayer: workaround invalid id 0");
                selectedId = mCurrAddrPlayerID;
            }

            // update current browse player id and start browsing service
            updateNewIds(mCurrAddrPlayerID, selectedId);
            String browsedPackage = getPackageName(selectedId);

            if (!isPackageNameValid(browsedPackage)) {
                Log.w(TAG, " Invalid package for id:" + mCurrBrowsePlayerID);
                status = AvrcpConstants.RSP_INV_PLAYER;
            } else if (!isBrowseSupported(browsedPackage)) {
                Log.w(TAG, "Browse unsupported for id:" + mCurrBrowsePlayerID
                        + ", packagename : " + browsedPackage);
                status = AvrcpConstants.RSP_PLAY_NOT_BROW;
            } else if (!startBrowseService(bdaddr, browsedPackage)) {
                Log.e(TAG, "service cannot be started for browse player id:" + mCurrBrowsePlayerID
                        + ", packagename : " + browsedPackage);
                status = AvrcpConstants.RSP_INTERNAL_ERR;
            }
        }

        if (status != AvrcpConstants.RSP_NO_ERROR) {
            setBrowsedPlayerRspNative(bdaddr, status, (byte) 0x00, 0, null);
        }

        if (DEBUG) Log.d(TAG, "setBrowsedPlayer for selectedId: " + selectedId +
                " , status: " + status);
    }

    private MediaSessionManager.OnActiveSessionsChangedListener mActiveSessionListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {

                @Override
                public void onActiveSessionsChanged(
                        List<android.media.session.MediaController> newControllers) {
                    Set<String> updatedPackages = new HashSet<String>();
                    // Update the current players
                    for (android.media.session.MediaController controller : newControllers) {
                        String packageName = controller.getPackageName();
                        if (DEBUG) Log.v(TAG, "ActiveSession: " + MediaController.wrap(controller));
                        // Only use the first (highest priority) controller from each package
                        if (updatedPackages.contains(packageName)) continue;
                        addMediaPlayerController(controller);
                        updatedPackages.add(packageName);
                    }

                    if (newControllers.size() > 0 && getAddressedPlayerInfo() == null) {
                        if (DEBUG)
                            Log.v(TAG, "No addressed player but active sessions, taking first.");
                        setAddressedMediaSessionPackage(newControllers.get(0).getPackageName());
                    }
                    updateCurrentMediaState();
                }
            };

    private void setAddressedMediaSessionPackage(@Nullable String packageName) {
        if (packageName == null) {
            // Should only happen when there's no media players, reset to no available player.
            updateCurrentController(0, mCurrBrowsePlayerID);
            return;
        }
        if (packageName.equals("com.android.server.telecom")) {
            Log.d(TAG, "Ignore addressed media session change to telecom");
            return;
        }
        // No change.
        if (getPackageName(mCurrAddrPlayerID).equals(packageName)) return;
        if (DEBUG) Log.v(TAG, "Changing addressed media session to " + packageName);
        // If the player doesn't exist, we need to add it.
        if (getMediaPlayerInfo(packageName) == null) {
            addMediaPlayerPackage(packageName);
            updateCurrentMediaState();
        }
        synchronized (mMediaPlayerInfoList) {
            for (Map.Entry<Integer, MediaPlayerInfo> entry : mMediaPlayerInfoList.entrySet()) {
                if (entry.getValue().getPackageName().equals(packageName)) {
                    int newAddrID = entry.getKey();
                    if (DEBUG) Log.v(TAG, "Set addressed #" + newAddrID + " " + entry.getValue());
                    updateCurrentController(newAddrID, mCurrBrowsePlayerID);
                    updateCurrentMediaState();
                    return;
                }
            }
        }
        // We shouldn't ever get here.
        Log.e(TAG, "Player info for " + packageName + " doesn't exist!");
    }

    private void setActiveMediaSession(MediaSession.Token token) {
        android.media.session.MediaController activeController =
                new android.media.session.MediaController(mContext, token);
        if (activeController.getPackageName().equals("com.android.server.telecom")) {
            Log.d(TAG, "Ignore active media session change to telecom");
            return;
        }
        if (DEBUG) Log.v(TAG, "Set active media session " + activeController.getPackageName());
        addMediaPlayerController(activeController);
        setAddressedMediaSessionPackage(activeController.getPackageName());
    }

    private boolean startBrowseService(byte[] bdaddr, String packageName) {
        boolean status = true;

        /* creating new instance for Browse Media Player */
        String browseService = getBrowseServiceName(packageName);
        if (!browseService.isEmpty()) {
            mAvrcpBrowseManager.getBrowsedMediaPlayer(bdaddr).setBrowsed(
                    packageName, browseService);
        } else {
            Log.w(TAG, "No Browser service available for " + packageName);
            status = false;
        }

        if (DEBUG) Log.d(TAG, "startBrowseService for packageName: " + packageName +
                ", status = " + status);
        return status;
    }

    private String getBrowseServiceName(String packageName) {
        String browseServiceName = "";

        // getting the browse service name from browse player info
        synchronized (mBrowsePlayerInfoList) {
            int browseInfoID = getBrowseId(packageName);
            if (browseInfoID != -1) {
                browseServiceName = mBrowsePlayerInfoList.get(browseInfoID).serviceClass;
            }
        }

        if (DEBUG) Log.d(TAG, "getBrowseServiceName for packageName: " + packageName +
                ", browseServiceName = " + browseServiceName);
        return browseServiceName;
    }

    void buildBrowsablePlayerList() {
        synchronized (mBrowsePlayerInfoList) {
            mBrowsePlayerInfoList.clear();
            Intent intent = new Intent(android.service.media.MediaBrowserService.SERVICE_INTERFACE);
            List<ResolveInfo> playerList =
                    mPackageManager.queryIntentServices(intent, PackageManager.MATCH_ALL);

            for (ResolveInfo info : playerList) {
                String displayableName = info.loadLabel(mPackageManager).toString();
                String serviceName = info.serviceInfo.name;
                String packageName = info.serviceInfo.packageName;

                if (DEBUG) Log.d(TAG, "Adding " + serviceName + " to list of browsable players");
                BrowsePlayerInfo currentPlayer =
                        new BrowsePlayerInfo(packageName, displayableName, serviceName);
                mBrowsePlayerInfoList.add(currentPlayer);
                MediaPlayerInfo playerInfo = getMediaPlayerInfo(packageName);
                MediaController controller =
                        (playerInfo == null) ? null : playerInfo.getMediaController();
                // Refresh the media player entry so it notices we can browse
                if (controller != null) {
                    addMediaPlayerController(controller.getWrappedInstance());
                } else {
                    addMediaPlayerPackage(packageName);
                }
            }
            updateCurrentMediaState();
        }
    }

    /* Initializes list of media players identified from session manager active sessions */
    private void initMediaPlayersList() {
        synchronized (mMediaPlayerInfoList) {
            // Clearing old browsable player's list
            mMediaPlayerInfoList.clear();

            if (mMediaSessionManager == null) {
                if (DEBUG) Log.w(TAG, "initMediaPlayersList: no media session manager!");
                return;
            }

            List<android.media.session.MediaController> controllers =
                    mMediaSessionManager.getActiveSessions(null);
            if (DEBUG)
                Log.v(TAG, "initMediaPlayerInfoList: " + controllers.size() + " controllers");
            /* Initializing all media players */
            for (android.media.session.MediaController controller : controllers) {
                addMediaPlayerController(controller);
            }

            updateCurrentMediaState();

            if (mMediaPlayerInfoList.size() > 0) {
                // Set the first one as the Addressed Player
                updateCurrentController(mMediaPlayerInfoList.firstKey(), -1);
            }
        }
    }

    private List<android.media.session.MediaController> getMediaControllers() {
        List<android.media.session.MediaController> controllers =
                new ArrayList<android.media.session.MediaController>();
        synchronized (mMediaPlayerInfoList) {
            for (MediaPlayerInfo info : mMediaPlayerInfoList.values()) {
                MediaController controller = info.getMediaController();
                if (controller != null) {
                    controllers.add(controller.getWrappedInstance());
                }
            }
        }
        return controllers;
    }

    /** Add (or update) a player to the media player list without a controller */
    private boolean addMediaPlayerPackage(String packageName) {
        MediaPlayerInfo info = new MediaPlayerInfo(null, AvrcpConstants.PLAYER_TYPE_AUDIO,
                AvrcpConstants.PLAYER_SUBTYPE_NONE, PLAYSTATUS_STOPPED,
                getFeatureBitMask(packageName), packageName, getAppLabel(packageName));
        return addMediaPlayerInfo(info);
    }

    /** Add (or update) a player to the media player list given an active controller */
    private boolean addMediaPlayerController(android.media.session.MediaController controller) {
        String packageName = controller.getPackageName();
        MediaPlayerInfo info = new MediaPlayerInfo(MediaController.wrap(controller),
                AvrcpConstants.PLAYER_TYPE_AUDIO, AvrcpConstants.PLAYER_SUBTYPE_NONE,
                getBluetoothPlayState(controller.getPlaybackState()),
                getFeatureBitMask(packageName), controller.getPackageName(),
                getAppLabel(packageName));
        return addMediaPlayerInfo(info);
    }

    /** Add or update a player to the media player list given the MediaPlayerInfo object.
     *  @return true if an item was updated, false if it was added instead
     */
    private boolean addMediaPlayerInfo(MediaPlayerInfo info) {
        int updateId = -1;
        boolean updated = false;
        boolean currentRemoved = false;
        if (info.getPackageName().equals("com.android.server.telecom")) {
            Log.d(TAG, "Skip adding telecom to the media player info list");
            return updated;
        }
        synchronized (mMediaPlayerInfoList) {
            for (Map.Entry<Integer, MediaPlayerInfo> entry : mMediaPlayerInfoList.entrySet()) {
                MediaPlayerInfo current = entry.getValue();
                int id = entry.getKey();
                if (info.getPackageName().equals(current.getPackageName())) {
                    if (!current.equalView(info)) {
                        // If we would present a different player, make it a new player
                        // so that controllers know whether a player is browsable or not.
                        mMediaPlayerInfoList.remove(id);
                        currentRemoved = (mCurrAddrPlayerID == id);
                        break;
                    }
                    updateId = id;
                    updated = true;
                    break;
                }
            }
            if (updateId == -1) {
                // New player
                mLastUsedPlayerID++;
                updateId = mLastUsedPlayerID;
                mAvailablePlayerViewChanged = true;
            }
            mMediaPlayerInfoList.put(updateId, info);
        }
        if (DEBUG) Log.d(TAG, (updated ? "update #" : "add #") + updateId + ":" + info.toString());
        if (currentRemoved || updateId == mCurrAddrPlayerID) {
            updateCurrentController(updateId, mCurrBrowsePlayerID);
        }
        return updated;
    }

    /** Remove all players related to |packageName| from the media player info list */
    private MediaPlayerInfo removeMediaPlayerInfo(String packageName) {
        synchronized (mMediaPlayerInfoList) {
            int removeKey = -1;
            for (Map.Entry<Integer, MediaPlayerInfo> entry : mMediaPlayerInfoList.entrySet()) {
                if (entry.getValue().getPackageName().equals(packageName)) {
                    removeKey = entry.getKey();
                    break;
                }
            }
            if (removeKey != -1) {
                if (DEBUG)
                    Log.d(TAG, "remove #" + removeKey + ":" + mMediaPlayerInfoList.get(removeKey));
                mAvailablePlayerViewChanged = true;
                return mMediaPlayerInfoList.remove(removeKey);
            }

            return null;
        }
    }

    /** Remove the controller referenced by |controller| from any player in the list */
    private void removeMediaController(@Nullable android.media.session.MediaController controller) {
        if (controller == null) return;
        synchronized (mMediaPlayerInfoList) {
            for (Map.Entry<Integer, MediaPlayerInfo> entry : mMediaPlayerInfoList.entrySet()) {
                MediaPlayerInfo info = entry.getValue();
                MediaController c = info.getMediaController();
                if (c != null && c.equals(controller)) {
                    info.setMediaController(null);
                    if (entry.getKey() == mCurrAddrPlayerID) {
                        updateCurrentController(mCurrAddrPlayerID, mCurrBrowsePlayerID);
                    }
                }
            }
        }
    }

    /*
     * utility function to get the playback state of any media player through
     * media controller APIs.
     */
    private byte getBluetoothPlayState(PlaybackState pbState) {
        if (pbState == null) {
            Log.w(TAG, "playState object null, sending STOPPED");
            return PLAYSTATUS_STOPPED;
        }

        switch (pbState.getState()) {
            case PlaybackState.STATE_PLAYING:
                return PLAYSTATUS_PLAYING;

            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_STOPPED:
            case PlaybackState.STATE_NONE:
            case PlaybackState.STATE_CONNECTING:
                return PLAYSTATUS_STOPPED;

            case PlaybackState.STATE_PAUSED:
                return PLAYSTATUS_PAUSED;

            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return PLAYSTATUS_FWD_SEEK;

            case PlaybackState.STATE_REWINDING:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
                return PLAYSTATUS_REV_SEEK;

            case PlaybackState.STATE_ERROR:
            default:
                return PLAYSTATUS_ERROR;
        }
    }

    /*
     * utility function to get the feature bit mask of any media player through
     * package name
     */
    private short[] getFeatureBitMask(String packageName) {

        ArrayList<Short> featureBitsList = new ArrayList<Short>();

        /* adding default feature bits */
        featureBitsList.add(AvrcpConstants.AVRC_PF_PLAY_BIT_NO);
        featureBitsList.add(AvrcpConstants.AVRC_PF_STOP_BIT_NO);
        featureBitsList.add(AvrcpConstants.AVRC_PF_PAUSE_BIT_NO);
        featureBitsList.add(AvrcpConstants.AVRC_PF_REWIND_BIT_NO);
        featureBitsList.add(AvrcpConstants.AVRC_PF_FAST_FWD_BIT_NO);
        featureBitsList.add(AvrcpConstants.AVRC_PF_FORWARD_BIT_NO);
        featureBitsList.add(AvrcpConstants.AVRC_PF_BACKWARD_BIT_NO);
        featureBitsList.add(AvrcpConstants.AVRC_PF_ADV_CTRL_BIT_NO);

        /* Add/Modify browse player supported features. */
        if (isBrowseSupported(packageName)) {
            featureBitsList.add(AvrcpConstants.AVRC_PF_BROWSE_BIT_NO);
            featureBitsList.add(AvrcpConstants.AVRC_PF_UID_UNIQUE_BIT_NO);
            featureBitsList.add(AvrcpConstants.AVRC_PF_NOW_PLAY_BIT_NO);
            featureBitsList.add(AvrcpConstants.AVRC_PF_GET_NUM_OF_ITEMS_BIT_NO);
        }

        // converting arraylist to array for response
        short[] featureBitsArray = new short[featureBitsList.size()];

        for (int i = 0; i < featureBitsList.size(); i++) {
            featureBitsArray[i] = featureBitsList.get(i).shortValue();
        }

        return featureBitsArray;
    }

    /**
     * Checks the Package name if it supports Browsing or not.
     *
     * @param packageName - name of the package to get the Id.
     * @return true if it supports browsing, else false.
     */
    private boolean isBrowseSupported(String packageName) {
        synchronized (mBrowsePlayerInfoList) {
            /* check if Browsable Player's list contains this package name */
            for (BrowsePlayerInfo info : mBrowsePlayerInfoList) {
                if (info.packageName.equals(packageName)) {
                    if (DEBUG) Log.v(TAG, "isBrowseSupported for " + packageName + ": true");
                    return true;
                }
            }
        }

        if (DEBUG) Log.v(TAG, "isBrowseSupported for " + packageName + ": false");
        return false;
    }

    private String getPackageName(int id) {
        MediaPlayerInfo player = null;
        synchronized (mMediaPlayerInfoList) {
            player = mMediaPlayerInfoList.getOrDefault(id, null);
        }

        if (player == null) {
            Log.w(TAG, "No package name for player (" + id + " not valid)");
            return "";
        }

        String packageName = player.getPackageName();
        if (DEBUG) Log.v(TAG, "Player " + id + " package: " + packageName);
        return packageName;
    }

    /* from the global object, getting the current browsed player's package name */
    private String getCurrentBrowsedPlayer(byte[] bdaddr) {
        String browsedPlayerPackage = "";

        Map<String, BrowsedMediaPlayer> connList = mAvrcpBrowseManager.getConnList();
        String bdaddrStr = new String(bdaddr);
        if(connList.containsKey(bdaddrStr)){
            browsedPlayerPackage = connList.get(bdaddrStr).getPackageName();
        }
        if (DEBUG) Log.v(TAG, "getCurrentBrowsedPlayerPackage: " + browsedPlayerPackage);
        return browsedPlayerPackage;
    }

    /* Returns the MediaPlayerInfo for the currently addressed media player */
    private MediaPlayerInfo getAddressedPlayerInfo() {
        synchronized (mMediaPlayerInfoList) {
            return mMediaPlayerInfoList.getOrDefault(mCurrAddrPlayerID, null);
        }
    }

    /*
     * Utility function to get the Media player info from package name returns
     * null if package name not found in media players list
     */
    private MediaPlayerInfo getMediaPlayerInfo(String packageName) {
        synchronized (mMediaPlayerInfoList) {
            if (mMediaPlayerInfoList.isEmpty()) {
                if (DEBUG) Log.v(TAG, "getMediaPlayerInfo: Media players list empty");
                return null;
            }

            for (MediaPlayerInfo info : mMediaPlayerInfoList.values()) {
                if (packageName.equals(info.getPackageName())) {
                    if (DEBUG) Log.v(TAG, "getMediaPlayerInfo: Found " + packageName);
                    return info;
                }
            }
            if (DEBUG) Log.w(TAG, "getMediaPlayerInfo: " + packageName + " not found");
            return null;
        }
    }

    /* prepare media list & return the media player list response object */
    private MediaPlayerListRsp prepareMediaPlayerRspObj() {
        synchronized (mMediaPlayerInfoList) {
            // TODO(apanicke): This hack will go away as soon as a developer
            // option to enable or disable player selection is created. Right
            // now this is needed to fix BMW i3 carkits and any other carkits
            // that might try to connect to a player that isnt the current
            // player based on this list
            int numPlayers = 1;

            int[] playerIds = new int[numPlayers];
            byte[] playerTypes = new byte[numPlayers];
            int[] playerSubTypes = new int[numPlayers];
            String[] displayableNameArray = new String[numPlayers];
            byte[] playStatusValues = new byte[numPlayers];
            short[] featureBitMaskValues =
                    new short[numPlayers * AvrcpConstants.AVRC_FEATURE_MASK_SIZE];

            // Reserve the first spot for the currently addressed player if
            // we have one
            int players = mMediaPlayerInfoList.containsKey(mCurrAddrPlayerID) ? 1 : 0;
            for (Map.Entry<Integer, MediaPlayerInfo> entry : mMediaPlayerInfoList.entrySet()) {
                int idx = players;
                if (entry.getKey() == mCurrAddrPlayerID)
                    idx = 0;
                else
                    continue; // TODO(apanicke): Remove, see above note
                MediaPlayerInfo info = entry.getValue();
                playerIds[idx] = entry.getKey();
                playerTypes[idx] = info.getMajorType();
                playerSubTypes[idx] = info.getSubType();
                displayableNameArray[idx] = info.getDisplayableName();
                playStatusValues[idx] = info.getPlayStatus();

                short[] featureBits = info.getFeatureBitMask();
                for (int numBit = 0; numBit < featureBits.length; numBit++) {
                    /* gives which octet this belongs to */
                    byte octet = (byte) (featureBits[numBit] / 8);
                    /* gives the bit position within the octet */
                    byte bit = (byte) (featureBits[numBit] % 8);
                    featureBitMaskValues[(idx * AvrcpConstants.AVRC_FEATURE_MASK_SIZE) + octet] |=
                            (1 << bit);
                }

                /* printLogs */
                if (DEBUG) {
                    Log.d(TAG, "Player " + playerIds[idx] + ": " + displayableNameArray[idx]
                                    + " type: " + playerTypes[idx] + ", " + playerSubTypes[idx]
                                    + " status: " + playStatusValues[idx]);
                }

                if (idx != 0) players++;
            }

            if (DEBUG) Log.d(TAG, "prepareMediaPlayerRspObj: numPlayers = " + numPlayers);

            return new MediaPlayerListRsp(AvrcpConstants.RSP_NO_ERROR, sUIDCounter, numPlayers,
                    AvrcpConstants.BTRC_ITEM_PLAYER, playerIds, playerTypes, playerSubTypes,
                    playStatusValues, featureBitMaskValues, displayableNameArray);
        }
    }

     /* build media player list and send it to remote. */
    private void handleMediaPlayerListRsp(AvrcpCmd.FolderItemsCmd folderObj) {
        MediaPlayerListRsp rspObj = null;
        synchronized (mMediaPlayerInfoList) {
            int numPlayers = mMediaPlayerInfoList.size();
            if (numPlayers == 0) {
                mediaPlayerListRspNative(folderObj.mAddress, AvrcpConstants.RSP_NO_AVBL_PLAY,
                        (short) 0, (byte) 0, 0, null, null, null, null, null, null);
                return;
            }
            if (folderObj.mStartItem >= numPlayers) {
                Log.i(TAG, "handleMediaPlayerListRsp: start = " + folderObj.mStartItem
                                + " > num of items = " + numPlayers);
                mediaPlayerListRspNative(folderObj.mAddress, AvrcpConstants.RSP_INV_RANGE,
                        (short) 0, (byte) 0, 0, null, null, null, null, null, null);
                return;
            }
            rspObj = prepareMediaPlayerRspObj();
        }
        if (DEBUG) Log.d(TAG, "handleMediaPlayerListRsp: sending " + rspObj.mNumItems + " players");
        mediaPlayerListRspNative(folderObj.mAddress, rspObj.mStatus, rspObj.mUIDCounter,
                rspObj.itemType, rspObj.mNumItems, rspObj.mPlayerIds, rspObj.mPlayerTypes,
                rspObj.mPlayerSubTypes, rspObj.mPlayStatusValues, rspObj.mFeatureBitMaskValues,
                rspObj.mPlayerNameList);
    }

    /* unregister to the old controller, update new IDs and register to the new controller */
    private boolean updateCurrentController(int addrId, int browseId) {
        boolean registerRsp = true;

        updateNewIds(addrId, browseId);

        MediaController newController = null;
        MediaPlayerInfo info = getAddressedPlayerInfo();
        if (info != null) newController = info.getMediaController();

        if (DEBUG)
            Log.d(TAG, "updateCurrentController: " + mMediaController + " to " + newController);
        synchronized (this) {
            if (mMediaController == null || (!mMediaController.equals(newController))) {
                if (mMediaController != null) {
                    mMediaController.unregisterCallback(mMediaControllerCb);
                }
                mMediaController = newController;
                if (mMediaController != null) {
                    mMediaController.registerCallback(mMediaControllerCb, mHandler);
                } else {
                    registerRsp = false;
                }
            }
        }
        updateCurrentMediaState();
        return registerRsp;
    }

    /* Handle getfolderitems for scope = VFS, Search, NowPlayingList */
    private void handleGetFolderItemBrowseResponse(AvrcpCmd.FolderItemsCmd folderObj, byte[] bdaddr) {
        int status = AvrcpConstants.RSP_NO_ERROR;

        /* Browsed player is already set */
        if (folderObj.mScope == AvrcpConstants.BTRC_SCOPE_FILE_SYSTEM) {
            if (mAvrcpBrowseManager.getBrowsedMediaPlayer(bdaddr) == null) {
                Log.e(TAG, "handleGetFolderItemBrowseResponse: no browsed player set for "
                                + Utils.getAddressStringFromByte(bdaddr));
                getFolderItemsRspNative(bdaddr, AvrcpConstants.RSP_INTERNAL_ERR, (short) 0,
                        (byte) 0x00, 0, null, null, null, null, null, null, null, null);
                return;
            }
            mAvrcpBrowseManager.getBrowsedMediaPlayer(bdaddr).getFolderItemsVFS(folderObj);
            return;
        }
        if (folderObj.mScope == AvrcpConstants.BTRC_SCOPE_NOW_PLAYING) {
            mAddressedMediaPlayer.getFolderItemsNowPlaying(bdaddr, folderObj, mMediaController);
            return;
        }

        /* invalid scope */
        Log.e(TAG, "handleGetFolderItemBrowseResponse: unknown scope " + folderObj.mScope);
        getFolderItemsRspNative(bdaddr, AvrcpConstants.RSP_INV_SCOPE, (short) 0, (byte) 0x00, 0,
                null, null, null, null, null, null, null, null);
    }

    /* utility function to update the global values of current Addressed and browsed player */
    private void updateNewIds(int addrId, int browseId) {
        if (DEBUG)
            Log.v(TAG, "updateNewIds: Addressed:" + mCurrAddrPlayerID + " to " + addrId
                            + ", Browse:" + mCurrBrowsePlayerID + " to " + browseId);
        mCurrAddrPlayerID = addrId;
        mCurrBrowsePlayerID = browseId;
    }

    /* Getting the application's displayable name from package name */
    private String getAppLabel(String packageName) {
        ApplicationInfo appInfo = null;
        try {
            appInfo = mPackageManager.getApplicationInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        return (String) (appInfo != null ? mPackageManager
                .getApplicationLabel(appInfo) : "Unknown");
    }

    private void handlePlayItemResponse(byte[] bdaddr, byte[] uid, byte scope) {
        if (scope == AvrcpConstants.BTRC_SCOPE_NOW_PLAYING) {
            mAddressedMediaPlayer.playItem(bdaddr, uid, mMediaController);
        }
        else {
            if(!isAddrPlayerSameAsBrowsed(bdaddr)) {
                Log.w(TAG, "Remote requesting play item on uid which may not be recognized by" +
                        "current addressed player");
                playItemRspNative(bdaddr, AvrcpConstants.RSP_INV_ITEM);
            }

            if (mAvrcpBrowseManager.getBrowsedMediaPlayer(bdaddr) != null) {
                mAvrcpBrowseManager.getBrowsedMediaPlayer(bdaddr).playItem(uid, scope);
            } else {
                Log.e(TAG, "handlePlayItemResponse: Remote requested playitem " +
                        "before setbrowsedplayer");
                playItemRspNative(bdaddr, AvrcpConstants.RSP_INTERNAL_ERR);
            }
        }
    }

    private void handleGetItemAttr(AvrcpCmd.ItemAttrCmd itemAttr) {
        if (itemAttr.mUidCounter != sUIDCounter) {
            Log.e(TAG, "handleGetItemAttr: invaild uid counter.");
            getItemAttrRspNative(
                    itemAttr.mAddress, AvrcpConstants.RSP_UID_CHANGED, (byte) 0, null, null);
            return;
        }
        if (itemAttr.mScope == AvrcpConstants.BTRC_SCOPE_NOW_PLAYING) {
            if (mCurrAddrPlayerID == NO_PLAYER_ID) {
                getItemAttrRspNative(
                        itemAttr.mAddress, AvrcpConstants.RSP_NO_AVBL_PLAY, (byte) 0, null, null);
                return;
            }
            mAddressedMediaPlayer.getItemAttr(itemAttr.mAddress, itemAttr, mMediaController);
            return;
        }
        // All other scopes use browsed player
        if (mAvrcpBrowseManager.getBrowsedMediaPlayer(itemAttr.mAddress) != null) {
            mAvrcpBrowseManager.getBrowsedMediaPlayer(itemAttr.mAddress).getItemAttr(itemAttr);
        } else {
            Log.e(TAG, "Could not get attributes. mBrowsedMediaPlayer is null");
            getItemAttrRspNative(
                    itemAttr.mAddress, AvrcpConstants.RSP_INTERNAL_ERR, (byte) 0, null, null);
        }
    }

    private void handleGetTotalNumOfItemsResponse(byte[] bdaddr, byte scope) {
        // for scope as media player list
        if (scope == AvrcpConstants.BTRC_SCOPE_PLAYER_LIST) {
            int numPlayers = 0;
            synchronized (mMediaPlayerInfoList) {
                numPlayers = mMediaPlayerInfoList.size();
            }
            if (DEBUG) Log.d(TAG, "handleGetTotalNumOfItemsResponse: " + numPlayers + " players.");
            getTotalNumOfItemsRspNative(bdaddr, AvrcpConstants.RSP_NO_ERROR, 0, numPlayers);
        } else if (scope == AvrcpConstants.BTRC_SCOPE_NOW_PLAYING) {
            mAddressedMediaPlayer.getTotalNumOfItems(bdaddr, mMediaController);
        } else {
            // for FileSystem browsing scopes as VFS, Now Playing
            if (mAvrcpBrowseManager.getBrowsedMediaPlayer(bdaddr) != null) {
                mAvrcpBrowseManager.getBrowsedMediaPlayer(bdaddr).getTotalNumOfItems(scope);
            } else {
                Log.e(TAG, "Could not get Total NumOfItems. mBrowsedMediaPlayer is null");
                getTotalNumOfItemsRspNative(bdaddr, AvrcpConstants.RSP_INTERNAL_ERR, 0, 0);
            }
        }

    }

    /* check if browsed player and addressed player are same */
    private boolean isAddrPlayerSameAsBrowsed(byte[] bdaddr) {
        String browsedPlayer = getCurrentBrowsedPlayer(bdaddr);

        if (!isPackageNameValid(browsedPlayer)) {
            Log.w(TAG, "Browsed player name empty");
            return false;
        }

        MediaPlayerInfo info = getAddressedPlayerInfo();
        String packageName = (info == null) ? "<none>" : info.getPackageName();
        if (info == null || !packageName.equals(browsedPlayer)) {
            if (DEBUG) Log.d(TAG, browsedPlayer + " is not addressed player " + packageName);
            return false;
        }
        return true;
    }

    /* checks if package name is not null or empty */
    private boolean isPackageNameValid(String browsedPackage) {
        boolean isValid = (browsedPackage != null && browsedPackage.length() > 0);
        if (DEBUG) Log.d(TAG, "isPackageNameValid: browsedPackage = " + browsedPackage +
                "isValid = " + isValid);
        return isValid;
    }

    /* checks if selected addressed player is already addressed */
    private boolean isPlayerAlreadyAddressed(int selectedId) {
        // checking if selected ID is same as the current addressed player id
        boolean isAddressed = (mCurrAddrPlayerID == selectedId);
        if (DEBUG) Log.d(TAG, "isPlayerAlreadyAddressed: isAddressed = " + isAddressed);
        return isAddressed;
    }

    public void dump(StringBuilder sb) {
        sb.append("AVRCP:\n");
        ProfileService.println(sb, "mMediaAttributes: " + mMediaAttributes.toRedactedString());
        ProfileService.println(sb, "mTransportControlFlags: " + mTransportControlFlags);
        ProfileService.println(sb, "mCurrentPlayState: " + mCurrentPlayState);
        ProfileService.println(sb, "mPlayStatusChangedNT: " + mPlayStatusChangedNT);
        ProfileService.println(sb, "mTrackChangedNT: " + mTrackChangedNT);
        ProfileService.println(sb, "mPlaybackIntervalMs: " + mPlaybackIntervalMs);
        ProfileService.println(sb, "mPlayPosChangedNT: " + mPlayPosChangedNT);
        ProfileService.println(sb, "mNextPosMs: " + mNextPosMs);
        ProfileService.println(sb, "mPrevPosMs: " + mPrevPosMs);
        ProfileService.println(sb, "mFeatures: " + mFeatures);
        ProfileService.println(sb, "mRemoteVolume: " + mRemoteVolume);
        ProfileService.println(sb, "mLastRemoteVolume: " + mLastRemoteVolume);
        ProfileService.println(sb, "mLastDirection: " + mLastDirection);
        ProfileService.println(sb, "mVolumeStep: " + mVolumeStep);
        ProfileService.println(sb, "mAudioStreamMax: " + mAudioStreamMax);
        ProfileService.println(sb, "mVolCmdAdjustInProgress: " + mVolCmdAdjustInProgress);
        ProfileService.println(sb, "mVolCmdSetInProgress: " + mVolCmdSetInProgress);
        ProfileService.println(sb, "mAbsVolRetryTimes: " + mAbsVolRetryTimes);
        ProfileService.println(sb, "mVolumeMapping: " + mVolumeMapping.toString());
        synchronized (this) {
            if (mMediaController != null)
                ProfileService.println(sb, "mMediaController: "
                                + mMediaController.getWrappedInstance() + " pkg "
                                + mMediaController.getPackageName());
        }
        ProfileService.println(sb, "");
        ProfileService.println(sb, "Media Players:");
        synchronized (mMediaPlayerInfoList) {
            for (Map.Entry<Integer, MediaPlayerInfo> entry : mMediaPlayerInfoList.entrySet()) {
                int key = entry.getKey();
                ProfileService.println(sb, ((mCurrAddrPlayerID == key) ? " *#" : "  #")
                                + entry.getKey() + ": " + entry.getValue());
            }
        }

        ProfileService.println(sb, "");
        mAddressedMediaPlayer.dump(sb, mMediaController);

        ProfileService.println(sb, "");
        ProfileService.println(sb, mPassthroughDispatched + " passthrough operations: ");
        if (mPassthroughDispatched > mPassthroughLogs.size())
            ProfileService.println(sb, "  (last " + mPassthroughLogs.size() + ")");
        synchronized (mPassthroughLogs) {
            for (MediaKeyLog log : mPassthroughLogs) {
                ProfileService.println(sb, "  " + log);
            }
        }
        synchronized (mPassthroughPending) {
            for (MediaKeyLog log : mPassthroughPending) {
                ProfileService.println(sb, "  " + log);
            }
        }

        // Print the blacklisted devices (for absolute volume control)
        SharedPreferences pref =
                mContext.getSharedPreferences(ABSOLUTE_VOLUME_BLACKLIST, Context.MODE_PRIVATE);
        Map<String, ?> allKeys = pref.getAll();
        ProfileService.println(sb, "");
        ProfileService.println(sb, "Runtime Blacklisted Devices (absolute volume):");
        if (allKeys.isEmpty()) {
            ProfileService.println(sb, "  None");
        } else {
            for (String key : allKeys.keySet()) {
                ProfileService.println(sb, "  " + key);
            }
        }
    }

    public class AvrcpBrowseManager {
        Map<String, BrowsedMediaPlayer> connList = new HashMap<String, BrowsedMediaPlayer>();
        private AvrcpMediaRspInterface mMediaInterface;
        private Context mContext;

        public AvrcpBrowseManager(Context context, AvrcpMediaRspInterface mediaInterface) {
            mContext = context;
            mMediaInterface = mediaInterface;
        }

        public void cleanup() {
            Iterator entries = connList.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry entry = (Map.Entry) entries.next();
                BrowsedMediaPlayer browsedMediaPlayer = (BrowsedMediaPlayer) entry.getValue();
                if (browsedMediaPlayer != null) {
                    browsedMediaPlayer.cleanup();
                }
            }
            // clean up the map
            connList.clear();
        }

        // get the a free media player interface based on the passed bd address
        // if the no items is found for the passed media player then it assignes a
        // available media player interface
        public BrowsedMediaPlayer getBrowsedMediaPlayer(byte[] bdaddr) {
            BrowsedMediaPlayer mediaPlayer;
            String bdaddrStr = new String(bdaddr);
            if (connList.containsKey(bdaddrStr)) {
                mediaPlayer = connList.get(bdaddrStr);
            } else {
                mediaPlayer = new BrowsedMediaPlayer(bdaddr, mContext, mMediaInterface);
                connList.put(bdaddrStr, mediaPlayer);
            }
            return mediaPlayer;
        }

        // clears the details pertaining to passed bdaddres
        public boolean clearBrowsedMediaPlayer(byte[] bdaddr) {
            String bdaddrStr = new String(bdaddr);
            if (connList.containsKey(bdaddrStr)) {
                connList.remove(bdaddrStr);
                return true;
            }
            return false;
        }

        public Map<String, BrowsedMediaPlayer> getConnList() {
            return connList;
        }

        /* Helper function to convert colon separated bdaddr to byte string */
        private byte[] hexStringToByteArray(String s) {
            int len = s.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                        + Character.digit(s.charAt(i+1), 16));
            }
            return data;
        }
    }

    /*
     * private class which handles responses from AvrcpMediaManager. Maps responses to native
     * responses. This class implements the AvrcpMediaRspInterface interface.
     */
    private class AvrcpMediaRsp implements AvrcpMediaRspInterface {
        private static final String TAG = "AvrcpMediaRsp";

        public void setAddrPlayerRsp(byte[] address, int rspStatus) {
            if (!setAddressedPlayerRspNative(address, rspStatus)) {
                Log.e(TAG, "setAddrPlayerRsp failed!");
            }
        }

        public void setBrowsedPlayerRsp(byte[] address, int rspStatus, byte depth, int numItems,
                String[] textArray) {
            if (!setBrowsedPlayerRspNative(address, rspStatus, depth, numItems, textArray)) {
                Log.e(TAG, "setBrowsedPlayerRsp failed!");
            }
        }

        public void mediaPlayerListRsp(byte[] address, int rspStatus, MediaPlayerListRsp rspObj) {
            if (rspObj != null && rspStatus == AvrcpConstants.RSP_NO_ERROR) {
                if (!mediaPlayerListRspNative(address, rspStatus, sUIDCounter, rspObj.itemType,
                            rspObj.mNumItems, rspObj.mPlayerIds, rspObj.mPlayerTypes,
                            rspObj.mPlayerSubTypes, rspObj.mPlayStatusValues,
                            rspObj.mFeatureBitMaskValues, rspObj.mPlayerNameList))
                    Log.e(TAG, "mediaPlayerListRsp failed!");
            } else {
                Log.e(TAG, "mediaPlayerListRsp: rspObj is null");
                if (!mediaPlayerListRspNative(address, rspStatus, sUIDCounter, (byte) 0x00, 0, null,
                            null, null, null, null, null))
                    Log.e(TAG, "mediaPlayerListRsp failed!");
            }
        }

        public void folderItemsRsp(byte[] address, int rspStatus, FolderItemsRsp rspObj) {
            if (rspObj != null && rspStatus == AvrcpConstants.RSP_NO_ERROR) {
                if (!getFolderItemsRspNative(address, rspStatus, sUIDCounter, rspObj.mScope,
                        rspObj.mNumItems, rspObj.mFolderTypes, rspObj.mPlayable, rspObj.mItemTypes,
                        rspObj.mItemUid, rspObj.mDisplayNames, rspObj.mAttributesNum,
                        rspObj.mAttrIds, rspObj.mAttrValues))
                    Log.e(TAG, "getFolderItemsRspNative failed!");
            } else {
                Log.e(TAG, "folderItemsRsp: rspObj is null or rspStatus is error:" + rspStatus);
                if (!getFolderItemsRspNative(address, rspStatus, sUIDCounter, (byte) 0x00, 0,
                        null, null, null, null, null, null, null, null))
                    Log.e(TAG, "getFolderItemsRspNative failed!");
            }

        }

        public void changePathRsp(byte[] address, int rspStatus, int numItems) {
            if (!changePathRspNative(address, rspStatus, numItems))
                Log.e(TAG, "changePathRspNative failed!");
        }

        public void getItemAttrRsp(byte[] address, int rspStatus, ItemAttrRsp rspObj) {
            if (rspObj != null && rspStatus == AvrcpConstants.RSP_NO_ERROR) {
                if (!getItemAttrRspNative(address, rspStatus, rspObj.mNumAttr,
                        rspObj.mAttributesIds, rspObj.mAttributesArray))
                    Log.e(TAG, "getItemAttrRspNative failed!");
            } else {
                Log.e(TAG, "getItemAttrRsp: rspObj is null or rspStatus is error:" + rspStatus);
                if (!getItemAttrRspNative(address, rspStatus, (byte) 0x00, null, null))
                    Log.e(TAG, "getItemAttrRspNative failed!");
            }
        }

        public void playItemRsp(byte[] address, int rspStatus) {
            if (!playItemRspNative(address, rspStatus)) {
                Log.e(TAG, "playItemRspNative failed!");
            }
        }

        public void getTotalNumOfItemsRsp(byte[] address, int rspStatus, int uidCounter,
                int numItems) {
            if (!getTotalNumOfItemsRspNative(address, rspStatus, sUIDCounter, numItems)) {
                Log.e(TAG, "getTotalNumOfItemsRspNative failed!");
            }
        }

        public void addrPlayerChangedRsp(int type, int playerId, int uidCounter) {
            if (!registerNotificationRspAddrPlayerChangedNative(type, playerId, sUIDCounter)) {
                Log.e(TAG, "registerNotificationRspAddrPlayerChangedNative failed!");
            }
        }

        public void avalPlayerChangedRsp(byte[] address, int type) {
            if (!registerNotificationRspAvalPlayerChangedNative(type)) {
                Log.e(TAG, "registerNotificationRspAvalPlayerChangedNative failed!");
            }
        }

        public void uidsChangedRsp(int type) {
            if (!registerNotificationRspUIDsChangedNative(type, sUIDCounter)) {
                Log.e(TAG, "registerNotificationRspUIDsChangedNative failed!");
            }
        }

        public void nowPlayingChangedRsp(int type) {
            if (mNowPlayingListChangedNT != AvrcpConstants.NOTIFICATION_TYPE_INTERIM) {
                if (DEBUG) Log.d(TAG, "NowPlayingListChanged: Not registered or requesting.");
                return;
            }

            if (!registerNotificationRspNowPlayingChangedNative(type)) {
                Log.e(TAG, "registerNotificationRspNowPlayingChangedNative failed!");
            }
            mNowPlayingListChangedNT = AvrcpConstants.NOTIFICATION_TYPE_CHANGED;
        }

        public void trackChangedRsp(int type, byte[] uid) {
            if (!registerNotificationRspTrackChangeNative(type, uid)) {
                Log.e(TAG, "registerNotificationRspTrackChangeNative failed!");
            }
        }
    }

    /* getters for some private variables */
    public AvrcpBrowseManager getAvrcpBrowseManager() {
        return mAvrcpBrowseManager;
    }

    /* PASSTHROUGH COMMAND MANAGEMENT */

    void handlePassthroughCmd(int op, int state) {
        int code = avrcpPassthroughToKeyCode(op);
        if (code == KeyEvent.KEYCODE_UNKNOWN) {
            Log.w(TAG, "Ignoring passthrough of unknown key " + op + " state " + state);
            return;
        }
        int action = KeyEvent.ACTION_DOWN;
        if (state == AvrcpConstants.KEY_STATE_RELEASE) action = KeyEvent.ACTION_UP;
        KeyEvent event = new KeyEvent(action, code);
        if (!KeyEvent.isMediaKey(code)) {
            Log.w(TAG, "Passthrough non-media key " + op + " (code " + code + ") state " + state);
        }

        mMediaSessionManager.dispatchMediaKeyEvent(event);
        addKeyPending(event);
    }

    private int avrcpPassthroughToKeyCode(int operation) {
        switch (operation) {
            case BluetoothAvrcp.PASSTHROUGH_ID_UP:
                return KeyEvent.KEYCODE_DPAD_UP;
            case BluetoothAvrcp.PASSTHROUGH_ID_DOWN:
                return KeyEvent.KEYCODE_DPAD_DOWN;
            case BluetoothAvrcp.PASSTHROUGH_ID_LEFT:
                return KeyEvent.KEYCODE_DPAD_LEFT;
            case BluetoothAvrcp.PASSTHROUGH_ID_RIGHT:
                return KeyEvent.KEYCODE_DPAD_RIGHT;
            case BluetoothAvrcp.PASSTHROUGH_ID_RIGHT_UP:
                return KeyEvent.KEYCODE_DPAD_UP_RIGHT;
            case BluetoothAvrcp.PASSTHROUGH_ID_RIGHT_DOWN:
                return KeyEvent.KEYCODE_DPAD_DOWN_RIGHT;
            case BluetoothAvrcp.PASSTHROUGH_ID_LEFT_UP:
                return KeyEvent.KEYCODE_DPAD_UP_LEFT;
            case BluetoothAvrcp.PASSTHROUGH_ID_LEFT_DOWN:
                return KeyEvent.KEYCODE_DPAD_DOWN_LEFT;
            case BluetoothAvrcp.PASSTHROUGH_ID_0:
                return KeyEvent.KEYCODE_NUMPAD_0;
            case BluetoothAvrcp.PASSTHROUGH_ID_1:
                return KeyEvent.KEYCODE_NUMPAD_1;
            case BluetoothAvrcp.PASSTHROUGH_ID_2:
                return KeyEvent.KEYCODE_NUMPAD_2;
            case BluetoothAvrcp.PASSTHROUGH_ID_3:
                return KeyEvent.KEYCODE_NUMPAD_3;
            case BluetoothAvrcp.PASSTHROUGH_ID_4:
                return KeyEvent.KEYCODE_NUMPAD_4;
            case BluetoothAvrcp.PASSTHROUGH_ID_5:
                return KeyEvent.KEYCODE_NUMPAD_5;
            case BluetoothAvrcp.PASSTHROUGH_ID_6:
                return KeyEvent.KEYCODE_NUMPAD_6;
            case BluetoothAvrcp.PASSTHROUGH_ID_7:
                return KeyEvent.KEYCODE_NUMPAD_7;
            case BluetoothAvrcp.PASSTHROUGH_ID_8:
                return KeyEvent.KEYCODE_NUMPAD_8;
            case BluetoothAvrcp.PASSTHROUGH_ID_9:
                return KeyEvent.KEYCODE_NUMPAD_9;
            case BluetoothAvrcp.PASSTHROUGH_ID_DOT:
                return KeyEvent.KEYCODE_NUMPAD_DOT;
            case BluetoothAvrcp.PASSTHROUGH_ID_ENTER:
                return KeyEvent.KEYCODE_NUMPAD_ENTER;
            case BluetoothAvrcp.PASSTHROUGH_ID_CLEAR:
                return KeyEvent.KEYCODE_CLEAR;
            case BluetoothAvrcp.PASSTHROUGH_ID_CHAN_UP:
                return KeyEvent.KEYCODE_CHANNEL_UP;
            case BluetoothAvrcp.PASSTHROUGH_ID_CHAN_DOWN:
                return KeyEvent.KEYCODE_CHANNEL_DOWN;
            case BluetoothAvrcp.PASSTHROUGH_ID_PREV_CHAN:
                return KeyEvent.KEYCODE_LAST_CHANNEL;
            case BluetoothAvrcp.PASSTHROUGH_ID_INPUT_SEL:
                return KeyEvent.KEYCODE_TV_INPUT;
            case BluetoothAvrcp.PASSTHROUGH_ID_DISP_INFO:
                return KeyEvent.KEYCODE_INFO;
            case BluetoothAvrcp.PASSTHROUGH_ID_HELP:
                return KeyEvent.KEYCODE_HELP;
            case BluetoothAvrcp.PASSTHROUGH_ID_PAGE_UP:
                return KeyEvent.KEYCODE_PAGE_UP;
            case BluetoothAvrcp.PASSTHROUGH_ID_PAGE_DOWN:
                return KeyEvent.KEYCODE_PAGE_DOWN;
            case BluetoothAvrcp.PASSTHROUGH_ID_POWER:
                return KeyEvent.KEYCODE_POWER;
            case BluetoothAvrcp.PASSTHROUGH_ID_VOL_UP:
                return KeyEvent.KEYCODE_VOLUME_UP;
            case BluetoothAvrcp.PASSTHROUGH_ID_VOL_DOWN:
                return KeyEvent.KEYCODE_VOLUME_DOWN;
            case BluetoothAvrcp.PASSTHROUGH_ID_MUTE:
                return KeyEvent.KEYCODE_MUTE;
            case BluetoothAvrcp.PASSTHROUGH_ID_PLAY:
                return KeyEvent.KEYCODE_MEDIA_PLAY;
            case BluetoothAvrcp.PASSTHROUGH_ID_STOP:
                return KeyEvent.KEYCODE_MEDIA_STOP;
            case BluetoothAvrcp.PASSTHROUGH_ID_PAUSE:
                return KeyEvent.KEYCODE_MEDIA_PAUSE;
            case BluetoothAvrcp.PASSTHROUGH_ID_RECORD:
                return KeyEvent.KEYCODE_MEDIA_RECORD;
            case BluetoothAvrcp.PASSTHROUGH_ID_REWIND:
                return KeyEvent.KEYCODE_MEDIA_REWIND;
            case BluetoothAvrcp.PASSTHROUGH_ID_FAST_FOR:
                return KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
            case BluetoothAvrcp.PASSTHROUGH_ID_EJECT:
                return KeyEvent.KEYCODE_MEDIA_EJECT;
            case BluetoothAvrcp.PASSTHROUGH_ID_FORWARD:
                return KeyEvent.KEYCODE_MEDIA_NEXT;
            case BluetoothAvrcp.PASSTHROUGH_ID_BACKWARD:
                return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            case BluetoothAvrcp.PASSTHROUGH_ID_F1:
                return KeyEvent.KEYCODE_F1;
            case BluetoothAvrcp.PASSTHROUGH_ID_F2:
                return KeyEvent.KEYCODE_F2;
            case BluetoothAvrcp.PASSTHROUGH_ID_F3:
                return KeyEvent.KEYCODE_F3;
            case BluetoothAvrcp.PASSTHROUGH_ID_F4:
                return KeyEvent.KEYCODE_F4;
            case BluetoothAvrcp.PASSTHROUGH_ID_F5:
                return KeyEvent.KEYCODE_F5;
            // Fallthrough for all unknown key mappings
            case BluetoothAvrcp.PASSTHROUGH_ID_SELECT:
            case BluetoothAvrcp.PASSTHROUGH_ID_ROOT_MENU:
            case BluetoothAvrcp.PASSTHROUGH_ID_SETUP_MENU:
            case BluetoothAvrcp.PASSTHROUGH_ID_CONT_MENU:
            case BluetoothAvrcp.PASSTHROUGH_ID_FAV_MENU:
            case BluetoothAvrcp.PASSTHROUGH_ID_EXIT:
            case BluetoothAvrcp.PASSTHROUGH_ID_SOUND_SEL:
            case BluetoothAvrcp.PASSTHROUGH_ID_ANGLE:
            case BluetoothAvrcp.PASSTHROUGH_ID_SUBPICT:
            case BluetoothAvrcp.PASSTHROUGH_ID_VENDOR:
            default:
                return KeyEvent.KEYCODE_UNKNOWN;
        }
    }

    private void addKeyPending(KeyEvent event) {
        mPassthroughPending.add(new MediaKeyLog(System.currentTimeMillis(), event));
    }

    private void recordKeyDispatched(KeyEvent event, String packageName) {
        long time = System.currentTimeMillis();
        Log.v(TAG, "recordKeyDispatched: " + event + " dispatched to " + packageName);
        setAddressedMediaSessionPackage(packageName);
        synchronized (mPassthroughPending) {
            Iterator<MediaKeyLog> pending = mPassthroughPending.iterator();
            while (pending.hasNext()) {
                MediaKeyLog log = pending.next();
                if (log.addDispatch(time, event, packageName)) {
                    mPassthroughDispatched++;
                    mPassthroughLogs.add(log);
                    pending.remove();
                    return;
                }
            }
            Log.w(TAG, "recordKeyDispatch: can't find matching log!");
        }
    }

    private final MediaSessionManager.Callback mButtonDispatchCallback =
            new MediaSessionManager.Callback() {
                @Override
                public void onMediaKeyEventDispatched(KeyEvent event, MediaSession.Token token) {
                    // Get the package name
                    android.media.session.MediaController controller =
                            new android.media.session.MediaController(mContext, token);
                    String targetPackage = controller.getPackageName();
                    recordKeyDispatched(event, targetPackage);
                }

                @Override
                public void onMediaKeyEventDispatched(KeyEvent event, ComponentName receiver) {
                    recordKeyDispatched(event, receiver.getPackageName());
                }

                @Override
                public void onAddressedPlayerChanged(MediaSession.Token token) {
                    setActiveMediaSession(token);
                }

                @Override
                public void onAddressedPlayerChanged(ComponentName receiver) {
                    if (receiver == null) {
                        // No active sessions, and no session to revive, give up.
                        setAddressedMediaSessionPackage(null);
                        return;
                    }
                    // We can still get a passthrough which will revive this player.
                    setAddressedMediaSessionPackage(receiver.getPackageName());
                }
            };

    // Do not modify without updating the HAL bt_rc.h files.

    // match up with btrc_play_status_t enum of bt_rc.h
    final static byte PLAYSTATUS_STOPPED = 0;
    final static byte PLAYSTATUS_PLAYING = 1;
    final static byte PLAYSTATUS_PAUSED = 2;
    final static byte PLAYSTATUS_FWD_SEEK = 3;
    final static byte PLAYSTATUS_REV_SEEK = 4;
    final static byte PLAYSTATUS_ERROR = (byte) 255;

    // match up with btrc_media_attr_t enum of bt_rc.h
    final static int MEDIA_ATTR_TITLE = 1;
    final static int MEDIA_ATTR_ARTIST = 2;
    final static int MEDIA_ATTR_ALBUM = 3;
    final static int MEDIA_ATTR_TRACK_NUM = 4;
    final static int MEDIA_ATTR_NUM_TRACKS = 5;
    final static int MEDIA_ATTR_GENRE = 6;
    final static int MEDIA_ATTR_PLAYING_TIME = 7;

    // match up with btrc_event_id_t enum of bt_rc.h
    final static int EVT_PLAY_STATUS_CHANGED = 1;
    final static int EVT_TRACK_CHANGED = 2;
    final static int EVT_TRACK_REACHED_END = 3;
    final static int EVT_TRACK_REACHED_START = 4;
    final static int EVT_PLAY_POS_CHANGED = 5;
    final static int EVT_BATT_STATUS_CHANGED = 6;
    final static int EVT_SYSTEM_STATUS_CHANGED = 7;
    final static int EVT_APP_SETTINGS_CHANGED = 8;
    final static int EVENT_NOW_PLAYING_CONTENT_CHANGED = 9;
    final static int EVT_AVBL_PLAYERS_CHANGED = 0xa;
    final static int EVT_ADDR_PLAYER_CHANGED = 0xb;
    final static int EVENT_UIDS_CHANGED = 0x0c;

    private native static void classInitNative();
    private native void initNative();
    private native void cleanupNative();
    private native boolean getPlayStatusRspNative(byte[] address, int playStatus, int songLen,
            int songPos);
    private native boolean getElementAttrRspNative(byte[] address, byte numAttr, int[] attrIds,
            String[] textArray);
    private native boolean registerNotificationRspPlayStatusNative(int type, int playStatus);
    private native boolean registerNotificationRspTrackChangeNative(int type, byte[] track);
    private native boolean registerNotificationRspPlayPosNative(int type, int playPos);
    private native boolean setVolumeNative(int volume);
    private native boolean sendPassThroughCommandNative(int keyCode, int keyState);
    private native boolean setAddressedPlayerRspNative(byte[] address, int rspStatus);
    private native boolean setBrowsedPlayerRspNative(byte[] address, int rspStatus, byte depth,
            int numItems, String[] textArray);
    private native boolean mediaPlayerListRspNative(byte[] address, int rsStatus, int uidCounter,
            byte item_type, int numItems, int[] playerIds, byte[] playerTypes, int[] playerSubTypes,
            byte[] playStatusValues, short[] featureBitMaskValues, String[] textArray);
    private native boolean getFolderItemsRspNative(byte[] address, int rspStatus, short uidCounter,
            byte scope, int numItems, byte[] folderTypes, byte[] playable, byte[] itemTypes,
            byte[] itemUidArray, String[] textArray, int[] AttributesNum, int[] AttributesIds,
            String[] attributesArray);
    private native boolean changePathRspNative(byte[] address, int rspStatus, int numItems);
    private native boolean getItemAttrRspNative(byte[] address, int rspStatus, byte numAttr,
            int[] attrIds, String[] textArray);
    private native boolean playItemRspNative(byte[] address, int rspStatus);
    private native boolean getTotalNumOfItemsRspNative(byte[] address, int rspStatus,
            int uidCounter, int numItems);
    private native boolean searchRspNative(byte[] address, int rspStatus, int uidCounter,
            int numItems);
    private native boolean addToNowPlayingRspNative(byte[] address, int rspStatus);
    private native boolean registerNotificationRspAddrPlayerChangedNative(int type,
        int playerId, int uidCounter);
    private native boolean registerNotificationRspAvalPlayerChangedNative(int type);
    private native boolean registerNotificationRspUIDsChangedNative(int type, int uidCounter);
    private native boolean registerNotificationRspNowPlayingChangedNative(int type);

}
