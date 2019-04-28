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
import android.bluetooth.BluetoothAvrcp;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.session.MediaSession.QueueItem;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.os.Bundle;
import android.util.Log;

import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.Utils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

/*************************************************************************************************
 * Provides functionality required for Addressed Media Player, like Now Playing List related
 * browsing commands, control commands to the current addressed player(playItem, play, pause, etc)
 * Acts as an Interface to communicate with media controller APIs for NowPlayingItems.
 ************************************************************************************************/

public class AddressedMediaPlayer {
    static private final String TAG = "AddressedMediaPlayer";
    static private final Boolean DEBUG = false;

    static private final long SINGLE_QID = 1;
    static private final String UNKNOWN_TITLE = "(unknown)";

    static private final String GPM_BUNDLE_METADATA_KEY =
            "com.google.android.music.mediasession.music_metadata";

    private AvrcpMediaRspInterface mMediaInterface;
    private @NonNull List<MediaSession.QueueItem> mNowPlayingList;

    private final List<MediaSession.QueueItem> mEmptyNowPlayingList;

    private long mLastTrackIdSent;

    public AddressedMediaPlayer(AvrcpMediaRspInterface mediaInterface) {
        mEmptyNowPlayingList = new ArrayList<MediaSession.QueueItem>();
        mNowPlayingList = mEmptyNowPlayingList;
        mMediaInterface = mediaInterface;
        mLastTrackIdSent = MediaSession.QueueItem.UNKNOWN_ID;
    }

    void cleanup() {
        if (DEBUG) Log.v(TAG, "cleanup");
        mNowPlayingList = mEmptyNowPlayingList;
///M: Do not release mMediaInterface to avoid JE @{
//        mMediaInterface = null;
/// @}
        mLastTrackIdSent = MediaSession.QueueItem.UNKNOWN_ID;
    }

    /* get now playing list from addressed player */
    void getFolderItemsNowPlaying(byte[] bdaddr, AvrcpCmd.FolderItemsCmd reqObj,
            @Nullable MediaController mediaController) {
        if (mediaController == null) {
            // No players (if a player exists, we would have selected it)
            Log.e(TAG, "mediaController = null, sending no available players response");
            mMediaInterface.folderItemsRsp(bdaddr, AvrcpConstants.RSP_NO_AVBL_PLAY, null);
            return;
        }
        List<MediaSession.QueueItem> items = updateNowPlayingList(mediaController);
        getFolderItemsFilterAttr(bdaddr, reqObj, items, AvrcpConstants.BTRC_SCOPE_NOW_PLAYING,
                reqObj.mStartItem, reqObj.mEndItem, mediaController);
    }

    /* get item attributes for item in now playing list */
    void getItemAttr(byte[] bdaddr, AvrcpCmd.ItemAttrCmd itemAttr,
            @Nullable MediaController mediaController) {
        int status = AvrcpConstants.RSP_NO_ERROR;
        long mediaId = ByteBuffer.wrap(itemAttr.mUid).getLong();
        List<MediaSession.QueueItem> items = updateNowPlayingList(mediaController);

        // NOTE: this is out-of-spec (AVRCP 1.6.1 sec 6.10.4.3, p90) but we answer it anyway
        // because some CTs ask for it.
        if (Arrays.equals(itemAttr.mUid, AvrcpConstants.TRACK_IS_SELECTED)) {
            mediaId = getActiveQueueItemId(mediaController);
            if (DEBUG) {
                Log.d(TAG,
                        "getItemAttr: Remote requests for now playing contents, sending UID: "
                                + mediaId);
            }
        }

        if (DEBUG) Log.d(TAG, "getItemAttr-UID: 0x" + Utils.byteArrayToString(itemAttr.mUid));
        for (MediaSession.QueueItem item : items) {
            if (item.getQueueId() == mediaId) {
                getItemAttrFilterAttr(bdaddr, itemAttr, item, mediaController);
                return;
            }
        }

        // Couldn't find it, so the id is invalid
        mMediaInterface.getItemAttrRsp(bdaddr, AvrcpConstants.RSP_INV_ITEM, null);
    }

    /* Refresh and get the queue of now playing.
     */
    @NonNull
    List<MediaSession.QueueItem> updateNowPlayingList(@Nullable MediaController mediaController) {
        if (mediaController == null) return mEmptyNowPlayingList;
        List<MediaSession.QueueItem> items = mediaController.getQueue();
        if (items == null) {
            Log.i(TAG, "null queue from " + mediaController.getPackageName()
                            + ", constructing single-item list");

            // Because we are database-unaware, we can just number the item here whatever we want
            // because they have to re-poll it every time.
            MediaMetadata metadata = mediaController.getMetadata();
            if (metadata == null) {
                Log.w(TAG, "Controller has no metadata!? Making an empty one");
                metadata = (new MediaMetadata.Builder()).build();
            }

            MediaDescription.Builder bob = new MediaDescription.Builder();
            MediaDescription desc = metadata.getDescription();

            // set the simple ones that MediaMetadata builds for us
            bob.setMediaId(desc.getMediaId());
            bob.setTitle(desc.getTitle());
            bob.setSubtitle(desc.getSubtitle());
            bob.setDescription(desc.getDescription());
            // fill the ones that we use later
            bob.setExtras(fillBundle(metadata, desc.getExtras()));

            // build queue item with the new metadata
            MediaSession.QueueItem current = new QueueItem(bob.build(), SINGLE_QID);

            items = new ArrayList<MediaSession.QueueItem>();
            items.add(current);
        }

        if (!items.equals(mNowPlayingList)) sendNowPlayingListChanged();
        mNowPlayingList = items;

        return mNowPlayingList;
    }

    private void sendNowPlayingListChanged() {
        if (mMediaInterface == null) return;
        if (DEBUG) Log.d(TAG, "sendNowPlayingListChanged()");
        mMediaInterface.nowPlayingChangedRsp(AvrcpConstants.NOTIFICATION_TYPE_CHANGED);
    }

    private Bundle fillBundle(MediaMetadata metadata, Bundle currentExtras) {
        if (metadata == null) {
            Log.i(TAG, "fillBundle: metadata is null");
            return currentExtras;
        }

        Bundle bundle = currentExtras;
        if (bundle == null) bundle = new Bundle();

        String[] stringKeys = {MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_GENRE};
        for (String key : stringKeys) {
            String current = bundle.getString(key);
            if (current == null) bundle.putString(key, metadata.getString(key));
        }

        String[] longKeys = {MediaMetadata.METADATA_KEY_TRACK_NUMBER,
                MediaMetadata.METADATA_KEY_NUM_TRACKS, MediaMetadata.METADATA_KEY_DURATION};
        for (String key : longKeys) {
            if (!bundle.containsKey(key)) bundle.putLong(key, metadata.getLong(key));
        }
        return bundle;
    }

    /* Instructs media player to play particular media item */
    void playItem(byte[] bdaddr, byte[] uid, @Nullable MediaController mediaController) {
        long qid = ByteBuffer.wrap(uid).getLong();
        List<MediaSession.QueueItem> items = updateNowPlayingList(mediaController);

        if (mediaController == null) {
            Log.e(TAG, "No mediaController when PlayItem " + qid + " requested");
            mMediaInterface.playItemRsp(bdaddr, AvrcpConstants.RSP_INTERNAL_ERR);
            return;
        }

        MediaController.TransportControls mediaControllerCntrl =
                mediaController.getTransportControls();

        if (items == null) {
            Log.w(TAG, "nowPlayingItems is null");
            mMediaInterface.playItemRsp(bdaddr, AvrcpConstants.RSP_INTERNAL_ERR);
            return;
        }

        for (MediaSession.QueueItem item : items) {
            if (qid == item.getQueueId()) {
                if (DEBUG) Log.d(TAG, "Skipping to ID " + qid);
                mediaControllerCntrl.skipToQueueItem(qid);
                mMediaInterface.playItemRsp(bdaddr, AvrcpConstants.RSP_NO_ERROR);
                return;
            }
        }

        Log.w(TAG, "Invalid now playing Queue ID " + qid);
        mMediaInterface.playItemRsp(bdaddr, AvrcpConstants.RSP_INV_ITEM);
    }

    void getTotalNumOfItems(byte[] bdaddr, @Nullable MediaController mediaController) {
        List<MediaSession.QueueItem> items = updateNowPlayingList(mediaController);
        if (DEBUG) Log.d(TAG, "getTotalNumOfItems: " + items.size() + " items.");
        mMediaInterface.getTotalNumOfItemsRsp(bdaddr, AvrcpConstants.RSP_NO_ERROR, 0, items.size());
    }

    void sendTrackChangeWithId(int type, @Nullable MediaController mediaController) {
        Log.d(TAG, "sendTrackChangeWithId (" + type + "): controller " + mediaController);
        long qid = getActiveQueueItemId(mediaController);
        byte[] track = ByteBuffer.allocate(AvrcpConstants.UID_SIZE).putLong(qid).array();
        // The nowPlayingList changed: the new list has the full data for the current item
        mMediaInterface.trackChangedRsp(type, track);
        mLastTrackIdSent = qid;
    }

    /*
     * helper method to check if startItem and endItem index is with range of
     * MediaItem list. (Resultset containing all items in current path)
     */
    private @Nullable List<MediaSession.QueueItem> getQueueSubset(
            @NonNull List<MediaSession.QueueItem> items, long startItem, long endItem) {
        if (endItem > items.size()) endItem = items.size() - 1;
        if (startItem > Integer.MAX_VALUE) startItem = Integer.MAX_VALUE;
        try {
            List<MediaSession.QueueItem> selected =
                    items.subList((int) startItem, (int) Math.min(items.size(), endItem + 1));
            if (selected.isEmpty()) {
                Log.i(TAG, "itemsSubList is empty.");
                return null;
            }
            return selected;
        } catch (IndexOutOfBoundsException ex) {
            Log.i(TAG, "Range (" + startItem + ", " + endItem + ") invalid");
        } catch (IllegalArgumentException ex) {
            Log.i(TAG, "Range start " + startItem + " > size (" + items.size() + ")");
        }
        return null;
    }

    /*
     * helper method to filter required attibutes before sending GetFolderItems
     * response
     */
    private void getFolderItemsFilterAttr(byte[] bdaddr, AvrcpCmd.FolderItemsCmd folderItemsReqObj,
            @NonNull List<MediaSession.QueueItem> items, byte scope, long startItem, long endItem,
            @NonNull MediaController mediaController) {
        if (DEBUG) Log.d(TAG, "getFolderItemsFilterAttr: startItem =" + startItem + ", endItem = "
                + endItem);

        List<MediaSession.QueueItem> result_items = getQueueSubset(items, startItem, endItem);
        /* check for index out of bound errors */
        if (result_items == null) {
            Log.w(TAG, "getFolderItemsFilterAttr: result_items is empty");
            mMediaInterface.folderItemsRsp(bdaddr, AvrcpConstants.RSP_INV_RANGE, null);
            return;
        }

        FolderItemsData folderDataNative = new FolderItemsData(result_items.size());

        /* variables to accumulate attrs */
        ArrayList<String> attrArray = new ArrayList<String>();
        ArrayList<Integer> attrId = new ArrayList<Integer>();

        for (int itemIndex = 0; itemIndex < result_items.size(); itemIndex++) {
            MediaSession.QueueItem item = result_items.get(itemIndex);
            // get the queue id
            long qid = item.getQueueId();
            byte[] uid = ByteBuffer.allocate(AvrcpConstants.UID_SIZE).putLong(qid).array();

            // get the array of uid from 2d to array 1D array
            for (int idx = 0; idx < AvrcpConstants.UID_SIZE; idx++) {
                folderDataNative.mItemUid[itemIndex * AvrcpConstants.UID_SIZE + idx] = uid[idx];
            }

            /* Set display name for current item */
            folderDataNative.mDisplayNames[itemIndex] =
                    getAttrValue(AvrcpConstants.ATTRID_TITLE, item, mediaController);

            int maxAttributesRequested = 0;
            boolean isAllAttribRequested = false;
            /* check if remote requested for attributes */
            if (folderItemsReqObj.mNumAttr != AvrcpConstants.NUM_ATTR_NONE) {
                int attrCnt = 0;

                /* add requested attr ids to a temp array */
                if (folderItemsReqObj.mNumAttr == AvrcpConstants.NUM_ATTR_ALL) {
                    isAllAttribRequested = true;
                    maxAttributesRequested = AvrcpConstants.MAX_NUM_ATTR;
                } else {
                    /* get only the requested attribute ids from the request */
                    maxAttributesRequested = folderItemsReqObj.mNumAttr;
                }

                /* lookup and copy values of attributes for ids requested above */
                for (int idx = 0; idx < maxAttributesRequested; idx++) {
                    /* check if media player provided requested attributes */
                    String value = null;

                    int attribId =
                            isAllAttribRequested ? (idx + 1) : folderItemsReqObj.mAttrIDs[idx];
                    value = getAttrValue(attribId, item, mediaController);
                    if (value != null) {
                        attrArray.add(value);
                        attrId.add(attribId);
                        attrCnt++;
                    }
                }
                /* add num attr actually received from media player for a particular item */
                folderDataNative.mAttributesNum[itemIndex] = attrCnt;
            }
        }

        /* copy filtered attr ids and attr values to response parameters */
        if (folderItemsReqObj.mNumAttr != AvrcpConstants.NUM_ATTR_NONE) {
            folderDataNative.mAttrIds = new int[attrId.size()];
            for (int attrIndex = 0; attrIndex < attrId.size(); attrIndex++)
                folderDataNative.mAttrIds[attrIndex] = attrId.get(attrIndex);
            folderDataNative.mAttrValues = attrArray.toArray(new String[attrArray.size()]);
        }
        for (int attrIndex = 0; attrIndex < folderDataNative.mAttributesNum.length; attrIndex++)
            if (DEBUG)
                Log.d(TAG, "folderDataNative.mAttributesNum"
                                + folderDataNative.mAttributesNum[attrIndex] + " attrIndex "
                                + attrIndex);

        /* create rsp object and send response to remote device */
        FolderItemsRsp rspObj = new FolderItemsRsp(AvrcpConstants.RSP_NO_ERROR, Avrcp.sUIDCounter,
                scope, folderDataNative.mNumItems, folderDataNative.mFolderTypes,
                folderDataNative.mPlayable, folderDataNative.mItemTypes, folderDataNative.mItemUid,
                folderDataNative.mDisplayNames, folderDataNative.mAttributesNum,
                folderDataNative.mAttrIds, folderDataNative.mAttrValues);
        mMediaInterface.folderItemsRsp(bdaddr, AvrcpConstants.RSP_NO_ERROR, rspObj);
    }

    private String getAttrValue(
            int attr, MediaSession.QueueItem item, @Nullable MediaController mediaController) {
        String attrValue = null;
        if (item == null) {
            if (DEBUG) Log.d(TAG, "getAttrValue received null item");
            return null;
        }
        try {
            MediaDescription desc = item.getDescription();
            Bundle extras = desc.getExtras();
            boolean isCurrentTrack = item.getQueueId() == getActiveQueueItemId(mediaController);
            MediaMetadata data = null;
            if (isCurrentTrack) {
                if (DEBUG) Log.d(TAG, "getAttrValue: item is active, using current data");
                data = mediaController.getMetadata();
                if (data == null)
                    Log.e(TAG, "getMetadata didn't give us any metadata for the current track");
            }

            if (data == null) {
                // TODO: This code can be removed when b/63117921 is resolved
                data = (MediaMetadata) extras.get(GPM_BUNDLE_METADATA_KEY);
                extras = null; // We no longer need the data in here
            }

            extras = fillBundle(data, extras);

            if (DEBUG) Log.d(TAG, "getAttrValue: item " + item + " : " + desc);
            switch (attr) {
                case AvrcpConstants.ATTRID_TITLE:
                    /* Title is mandatory attribute */
                    if (isCurrentTrack) {
                        attrValue = extras.getString(MediaMetadata.METADATA_KEY_TITLE);
                    } else {
                        attrValue = desc.getTitle().toString();
                    }
                    break;

                case AvrcpConstants.ATTRID_ARTIST:
                    attrValue = extras.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    break;

                case AvrcpConstants.ATTRID_ALBUM:
                    attrValue = extras.getString(MediaMetadata.METADATA_KEY_ALBUM);
                    break;

                case AvrcpConstants.ATTRID_TRACK_NUM:
                    attrValue =
                            Long.toString(extras.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER));
                    break;

                case AvrcpConstants.ATTRID_NUM_TRACKS:
                    attrValue =
                            Long.toString(extras.getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS));
                    break;

                case AvrcpConstants.ATTRID_GENRE:
                    attrValue = extras.getString(MediaMetadata.METADATA_KEY_GENRE);
                    break;

                case AvrcpConstants.ATTRID_PLAY_TIME:
                    attrValue = Long.toString(extras.getLong(MediaMetadata.METADATA_KEY_DURATION));
                    break;

                case AvrcpConstants.ATTRID_COVER_ART:
                    Log.e(TAG, "getAttrValue: Cover art attribute not supported");
                    return null;

                default:
                    Log.e(TAG, "getAttrValue: Unknown attribute ID requested: " + attr);
                    return null;
            }
        } catch (NullPointerException ex) {
            Log.w(TAG, "getAttrValue: attr id not found in result");
            /* checking if attribute is title, then it is mandatory and cannot send null */
            if (attr == AvrcpConstants.ATTRID_TITLE) {
                attrValue = "<Unknown Title>";
            } else {
                return null;
            }
        }
        if (DEBUG) Log.d(TAG, "getAttrValue: attrvalue = " + attrValue + ", attr id:" + attr);
        return attrValue;
    }

    private void getItemAttrFilterAttr(byte[] bdaddr, AvrcpCmd.ItemAttrCmd mItemAttrReqObj,
            MediaSession.QueueItem mediaItem, @Nullable MediaController mediaController) {
        /* Response parameters */
        int[] attrIds = null; /* array of attr ids */
        String[] attrValues = null; /* array of attr values */

        /* variables to temperorily add attrs */
        ArrayList<String> attrArray = new ArrayList<String>();
        ArrayList<Integer> attrId = new ArrayList<Integer>();
        ArrayList<Integer> attrTempId = new ArrayList<Integer>();

        /* check if remote device has requested for attributes */
        if (mItemAttrReqObj.mNumAttr != AvrcpConstants.NUM_ATTR_NONE) {
            if (mItemAttrReqObj.mNumAttr == AvrcpConstants.NUM_ATTR_ALL) {
                for (int idx = 1; idx < AvrcpConstants.MAX_NUM_ATTR; idx++) {
                    attrTempId.add(idx); /* attr id 0x00 is unused */
                }
            } else {
                /* get only the requested attribute ids from the request */
                for (int idx = 0; idx < mItemAttrReqObj.mNumAttr; idx++) {
                    if (DEBUG)
                        Log.d(TAG, "getItemAttrFilterAttr: attr id[" + idx + "] :"
                                        + mItemAttrReqObj.mAttrIDs[idx]);
                    attrTempId.add(mItemAttrReqObj.mAttrIDs[idx]);
                }
            }
        }

        if (DEBUG) Log.d(TAG, "getItemAttrFilterAttr: attr id list size:" + attrTempId.size());
        /* lookup and copy values of attributes for ids requested above */
        for (int idx = 0; idx < attrTempId.size(); idx++) {
            /* check if media player provided requested attributes */
            String value = getAttrValue(attrTempId.get(idx), mediaItem, mediaController);
            if (value != null) {
                attrArray.add(value);
                attrId.add(attrTempId.get(idx));
            }
        }

        /* copy filtered attr ids and attr values to response parameters */
        if (mItemAttrReqObj.mNumAttr != AvrcpConstants.NUM_ATTR_NONE) {
            attrIds = new int[attrId.size()];

            for (int attrIndex = 0; attrIndex < attrId.size(); attrIndex++)
                attrIds[attrIndex] = attrId.get(attrIndex);

            attrValues = attrArray.toArray(new String[attrId.size()]);

            /* create rsp object and send response */
            ItemAttrRsp rspObj = new ItemAttrRsp(AvrcpConstants.RSP_NO_ERROR, attrIds, attrValues);
            mMediaInterface.getItemAttrRsp(bdaddr, AvrcpConstants.RSP_NO_ERROR, rspObj);
            return;
        }
    }

    private long getActiveQueueItemId(@Nullable MediaController controller) {
        if (controller == null) return MediaSession.QueueItem.UNKNOWN_ID;
        PlaybackState state = controller.getPlaybackState();
        if (state == null || state.getState() == PlaybackState.STATE_BUFFERING
                || state.getState() == PlaybackState.STATE_NONE)
            return MediaSession.QueueItem.UNKNOWN_ID;
        long qid = state.getActiveQueueItemId();
        if (qid != MediaSession.QueueItem.UNKNOWN_ID) return qid;
        // Check if we're presenting a "one item queue"
        if (controller.getMetadata() != null) return SINGLE_QID;
        return MediaSession.QueueItem.UNKNOWN_ID;
    }

    String displayMediaItem(MediaSession.QueueItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("#");
        sb.append(item.getQueueId());
        sb.append(": ");
        sb.append(Utils.ellipsize(getAttrValue(AvrcpConstants.ATTRID_TITLE, item, null)));
        sb.append(" - ");
        sb.append(Utils.ellipsize(getAttrValue(AvrcpConstants.ATTRID_ALBUM, item, null)));
        sb.append(" by ");
        sb.append(Utils.ellipsize(getAttrValue(AvrcpConstants.ATTRID_ARTIST, item, null)));
        sb.append(" (");
        sb.append(getAttrValue(AvrcpConstants.ATTRID_PLAY_TIME, item, null));
        sb.append(" ");
        sb.append(getAttrValue(AvrcpConstants.ATTRID_TRACK_NUM, item, null));
        sb.append("/");
        sb.append(getAttrValue(AvrcpConstants.ATTRID_NUM_TRACKS, item, null));
        sb.append(") ");
        sb.append(getAttrValue(AvrcpConstants.ATTRID_GENRE, item, null));
        return sb.toString();
    }

    public void dump(StringBuilder sb, @Nullable MediaController mediaController) {
        ProfileService.println(sb, "AddressedPlayer info:");
        ProfileService.println(sb, "mLastTrackIdSent: " + mLastTrackIdSent);
        ProfileService.println(sb, "mNowPlayingList: " + mNowPlayingList.size() + " elements");
        long currentQueueId = getActiveQueueItemId(mediaController);
        for (MediaSession.QueueItem item : mNowPlayingList) {
            long itemId = item.getQueueId();
            ProfileService.println(
                    sb, (itemId == currentQueueId ? "*" : " ") + displayMediaItem(item));
        }
    }
}
