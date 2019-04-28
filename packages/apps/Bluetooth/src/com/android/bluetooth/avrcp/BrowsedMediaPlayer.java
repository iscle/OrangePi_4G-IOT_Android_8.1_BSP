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
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.media.session.MediaSession.QueueItem;
import android.os.Bundle;
import android.util.Log;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

/*************************************************************************************************
 * Provides functionality required for Browsed Media Player like browsing Virtual File System, get
 * Item Attributes, play item from the file system, etc.
 * Acts as an Interface to communicate with Media Browsing APIs for browsing FileSystem.
 ************************************************************************************************/

class BrowsedMediaPlayer {
    private static final boolean DEBUG = false;
    private static final String TAG = "BrowsedMediaPlayer";

    /* connection state with MediaBrowseService */
    private static final int DISCONNECTED = 0;
    private static final int CONNECTED = 1;
    private static final int SUSPENDED = 2;

    private static final String[] ROOT_FOLDER = {"root"};

    /*  package and service name of target Media Player which is set for browsing */
    private String mPackageName;
    private String mConnectingPackageName;
    private String mClassName;
    private Context mContext;
    private AvrcpMediaRspInterface mMediaInterface;
    private byte[] mBDAddr;

    /* Object used to connect to MediaBrowseService of Media Player */
    private MediaBrowser mMediaBrowser = null;
    private MediaController mMediaController = null;

    /* The mediaId to be used for subscribing for children using the MediaBrowser */
    private String mMediaId = null;
    private String mRootFolderUid = null;
    private int mConnState = DISCONNECTED;

    /* stores the path trail during changePath */
    private Stack<String> mPathStack = null;

    /* Number of items in current folder */
    private int mCurrFolderNumItems = 0;

    /* store mapping between uid(Avrcp) and mediaId(Media Player). */
    private HashMap<Integer, String> mHmap = new HashMap<Integer, String>();

    /* command objects from avrcp handler */
    private AvrcpCmd.FolderItemsCmd mFolderItemsReqObj;

    /* store result of getfolderitems with scope="vfs" */
    private List<MediaBrowser.MediaItem> mFolderItems = null;

    /* Connection state callback handler */
    class MediaConnectionCallback extends MediaBrowser.ConnectionCallback {
        private String mCallbackPackageName;
        private MediaBrowser mBrowser;

        public MediaConnectionCallback(String packageName) {
            this.mCallbackPackageName = packageName;
        }

        public void setBrowser(MediaBrowser b) {
            mBrowser = b;
        }

        @Override
        public void onConnected() {
            mConnState = CONNECTED;
            if (DEBUG) Log.d(TAG, "mediaBrowser CONNECTED to " + mPackageName);
            /* perform init tasks and set player as browsed player on successful connection */
            onBrowseConnect(mCallbackPackageName, mBrowser);

            // Remove what could be a circular dependency causing GC to never happen on this object
            mBrowser = null;
        }

        @Override
        public void onConnectionFailed() {
            mConnState = DISCONNECTED;
            // Remove what could be a circular dependency causing GC to never happen on this object
            mBrowser = null;
            Log.e(TAG, "mediaBrowser Connection failed with " + mPackageName
                    + ", Sending fail response!");
            mMediaInterface.setBrowsedPlayerRsp(mBDAddr, AvrcpConstants.RSP_INTERNAL_ERR,
                (byte)0x00, 0, null);
        }

        @Override
        public void onConnectionSuspended() {
            mBrowser = null;
            mConnState = SUSPENDED;
            Log.e(TAG, "mediaBrowser SUSPENDED connection with " + mPackageName);
        }
    }

    /* Subscription callback handler. Subscribe to a folder to get its contents */
    private MediaBrowser.SubscriptionCallback folderItemsCb =
            new MediaBrowser.SubscriptionCallback() {

        @Override
        public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
            if (DEBUG) Log.d(TAG, "OnChildren Loaded folder items: childrens= " + children.size());

            /*
             * cache current folder items and send as rsp when remote requests
             * get_folder_items (scope = vfs)
             */
            if (mFolderItems == null) {
                if (DEBUG) Log.d(TAG, "sending setbrowsed player rsp");
                mFolderItems = children;
                mMediaInterface.setBrowsedPlayerRsp(mBDAddr, AvrcpConstants.RSP_NO_ERROR,
                        (byte)0x00, children.size(), ROOT_FOLDER);
            } else {
                mFolderItems = children;
                mCurrFolderNumItems = mFolderItems.size();
                mMediaInterface.changePathRsp(mBDAddr, AvrcpConstants.RSP_NO_ERROR,
                        mCurrFolderNumItems);
            }
            mMediaBrowser.unsubscribe(parentId);
        }

        /* UID is invalid */
        @Override
        public void onError(String id) {
            Log.e(TAG, "set browsed player rsp. Could not get root folder items");
            mMediaInterface.setBrowsedPlayerRsp(mBDAddr, AvrcpConstants.RSP_INTERNAL_ERR,
                    (byte)0x00, 0, null);
        }
    };

    /* callback from media player in response to getitemAttr request */
    private class ItemAttribSubscriber extends MediaBrowser.SubscriptionCallback {
        private String mMediaId;
        private AvrcpCmd.ItemAttrCmd mAttrReq;

        public ItemAttribSubscriber(
                @NonNull AvrcpCmd.ItemAttrCmd attrReq, @NonNull String mediaId) {
            mAttrReq = attrReq;
            mMediaId = mediaId;
        }

        @Override
        public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
            String logprefix = "ItemAttribSubscriber(" + mMediaId + "): ";
            if (DEBUG) Log.d(TAG, logprefix + "OnChildren Loaded");
            int status = AvrcpConstants.RSP_INV_ITEM;

            if (children == null) {
                Log.w(TAG, logprefix + "children list is null parentId: " + parentId);
            } else {
                /* find the item in the folder */
                for (MediaBrowser.MediaItem item : children) {
                    if (item.getMediaId().equals(mMediaId)) {
                        if (DEBUG) Log.d(TAG, logprefix + "found item");
                        getItemAttrFilterAttr(item);
                        status = AvrcpConstants.RSP_NO_ERROR;
                        break;
                    }
                }
            }
            /* Send only error from here, in case of success, getItemAttrFilterAttr sends */
            if (status != AvrcpConstants.RSP_NO_ERROR) {
                Log.e(TAG, logprefix + "not able to find item from " + parentId);
                mMediaInterface.getItemAttrRsp(mBDAddr, status, null);
            }
            mMediaBrowser.unsubscribe(parentId);
        }

        @Override
        public void onError(String id) {
            Log.e(TAG, "Could not get attributes from media player id: " + id);
            mMediaInterface.getItemAttrRsp(mBDAddr, AvrcpConstants.RSP_INTERNAL_ERR, null);
        }

        /* helper method to filter required attibuteand send GetItemAttr response */
        private void getItemAttrFilterAttr(@NonNull MediaBrowser.MediaItem mediaItem) {
            /* Response parameters */
            int[] attrIds = null; /* array of attr ids */
            String[] attrValues = null; /* array of attr values */

            /* variables to temperorily add attrs */
            ArrayList<Integer> attrIdArray = new ArrayList<Integer>();
            ArrayList<String> attrValueArray = new ArrayList<String>();
            ArrayList<Integer> attrReqIds = new ArrayList<Integer>();

            if (mAttrReq.mNumAttr == AvrcpConstants.NUM_ATTR_NONE) {
                // Note(jamuraa): the stack should never send this, remove?
                Log.i(TAG, "getItemAttrFilterAttr: No attributes requested");
                mMediaInterface.getItemAttrRsp(mBDAddr, AvrcpConstants.RSP_BAD_PARAM, null);
                return;
            }

            /* check if remote device has requested all attributes */
            if (mAttrReq.mNumAttr == AvrcpConstants.NUM_ATTR_ALL
                    || mAttrReq.mNumAttr == AvrcpConstants.MAX_NUM_ATTR) {
                for (int idx = 1; idx <= AvrcpConstants.MAX_NUM_ATTR; idx++) {
                    attrReqIds.add(idx); /* attr id 0x00 is unused */
                }
            } else {
                /* get only the requested attribute ids from the request */
                for (int idx = 0; idx < mAttrReq.mNumAttr; idx++) {
                    attrReqIds.add(mAttrReq.mAttrIDs[idx]);
                }
            }

            /* lookup and copy values of attributes for ids requested above */
            for (int attrId : attrReqIds) {
                /* check if media player provided requested attributes */
                String value = getAttrValue(attrId, mediaItem);
                if (value != null) {
                    attrIdArray.add(attrId);
                    attrValueArray.add(value);
                }
            }

            /* copy filtered attr ids and attr values to response parameters */
            attrIds = new int[attrIdArray.size()];
            for (int i = 0; i < attrIdArray.size(); i++) attrIds[i] = attrIdArray.get(i);

            attrValues = attrValueArray.toArray(new String[attrIdArray.size()]);

            /* create rsp object and send response */
            ItemAttrRsp rspObj = new ItemAttrRsp(AvrcpConstants.RSP_NO_ERROR, attrIds, attrValues);
            mMediaInterface.getItemAttrRsp(mBDAddr, AvrcpConstants.RSP_NO_ERROR, rspObj);
        }
    }

    /* Constructor */
    public BrowsedMediaPlayer(byte[] address, Context context,
            AvrcpMediaRspInterface mAvrcpMediaRspInterface) {
        mContext = context;
        mMediaInterface = mAvrcpMediaRspInterface;
        mBDAddr = address;
    }

    /* initialize mediacontroller in order to communicate with media player. */
    private void onBrowseConnect(String connectedPackage, MediaBrowser browser) {
        if (!connectedPackage.equals(mConnectingPackageName)) {
            Log.w(TAG, "onBrowseConnect: recieved callback for package we aren't connecting to "
                            + connectedPackage);
            return;
        }
        mConnectingPackageName = null;

        if (browser == null) {
            Log.e(TAG, "onBrowseConnect: received a null browser for " + connectedPackage);
            mMediaInterface.setBrowsedPlayerRsp(
                    mBDAddr, AvrcpConstants.RSP_INTERNAL_ERR, (byte) 0x00, 0, null);
            return;
        }

        MediaSession.Token token = null;
        try {
            if (!browser.isConnected()) {
                Log.e(TAG, "setBrowsedPlayer: " + mPackageName + "not connected");
            } else if ((token = browser.getSessionToken()) == null) {
                Log.e(TAG, "setBrowsedPlayer: " + mPackageName + "no Session token");
            } else {
                /* update to the new MediaBrowser */
                if (mMediaBrowser != null) mMediaBrowser.disconnect();
                mMediaBrowser = browser;
                mPackageName = connectedPackage;

                /* get rootfolder uid from media player */
                if (mMediaId == null) {
                    mMediaId = mMediaBrowser.getRoot();
                    /*
                     * assuming that root folder uid will not change on uids changed
                     */
                    mRootFolderUid = mMediaId;
                    /* store root folder uid to stack */
                    mPathStack.push(mMediaId);
                }

                mMediaController = MediaController.wrap(
                    new android.media.session.MediaController(mContext, token));
                /* get root folder items */
                mMediaBrowser.subscribe(mRootFolderUid, folderItemsCb);
                return;
            }
        } catch (NullPointerException ex) {
            Log.e(TAG, "setBrowsedPlayer : Null pointer during init");
            ex.printStackTrace();
        }

        mMediaInterface.setBrowsedPlayerRsp(
                mBDAddr, AvrcpConstants.RSP_INTERNAL_ERR, (byte) 0x00, 0, null);
    }

    public void setBrowsed(String packageName, String cls) {
        mConnectingPackageName = packageName;
        mClassName = cls;
        /* cleanup variables from previous browsed calls */
        mFolderItems = null;
        mMediaId = null;
        mRootFolderUid = null;
        /*
         * create stack to store the navigation trail (current folder ID). This
         * will be required while navigating up the folder
         */
        mPathStack = new Stack<String>();

        /* Bind to MediaBrowseService of MediaPlayer */
        MediaConnectionCallback callback = new MediaConnectionCallback(packageName);
        MediaBrowser tempBrowser = new MediaBrowser(
                mContext, new ComponentName(packageName, mClassName), callback, null);
        callback.setBrowser(tempBrowser);

        tempBrowser.connect();
    }

    /* called when connection to media player is closed */
    public void cleanup() {
        if (DEBUG) Log.d(TAG, "cleanup");

        if (mConnState != DISCONNECTED) {
            mMediaBrowser.disconnect();
        }

        mHmap = null;
        mMediaController = null;
        mMediaBrowser = null;
        mPathStack = null;
    }

    public boolean isPlayerConnected() {
        if (mMediaBrowser == null) {
            if (DEBUG) Log.d(TAG, "isPlayerConnected: mMediaBrowser = null!");
            return false;
        }

        return mMediaBrowser.isConnected();
    }

    /* returns number of items in new path as reponse */
    public void changePath(byte[] folderUid, byte direction) {
        if (DEBUG) Log.d(TAG, "changePath.direction = " + direction);
        String newPath = "";

        if (isPlayerConnected() == false) {
            Log.w(TAG, "changePath: disconnected from player service, sending internal error");
            mMediaInterface.changePathRsp(mBDAddr, AvrcpConstants.RSP_INTERNAL_ERR, 0);
            return;
        }

        if (mMediaBrowser == null) {
            Log.e(TAG, "Media browser is null, sending internal error");
            mMediaInterface.changePathRsp(mBDAddr, AvrcpConstants.RSP_INTERNAL_ERR, 0);
            return;
        }

        /* check direction and change the path */
        if (direction == AvrcpConstants.DIR_DOWN) { /* move down */
            if ((newPath = byteToString(folderUid)) == null) {
                Log.e(TAG, "Could not get media item from folder Uid, sending err response");
                mMediaInterface.changePathRsp(mBDAddr, AvrcpConstants.RSP_INV_ITEM, 0);
            } else if (isBrowsableFolderDn(newPath) == false) {
                /* new path is not browsable */
                Log.e(TAG, "ItemUid received from changePath cmd is not browsable");
                mMediaInterface.changePathRsp(mBDAddr, AvrcpConstants.RSP_INV_DIRECTORY, 0);
            } else if (mPathStack.peek().equals(newPath) == true) {
                /* new_folder is same as current folder */
                Log.e(TAG, "new_folder is same as current folder, Invalid direction!");
                mMediaInterface.changePathRsp(mBDAddr, AvrcpConstants.RSP_INV_DIRN, 0);
            } else {
                mMediaBrowser.subscribe(newPath, folderItemsCb);
                /* assume that call is success and update stack with new folder path */
                mPathStack.push(newPath);
            }
        } else if (direction == AvrcpConstants.DIR_UP) { /* move up */
            if (isBrowsableFolderUp() == false) {
                /* Already on the root, cannot allow up: PTS: test case TC_TG_MCN_CB_BI_02_C
                 * This is required, otherwise some CT will keep on sending change path up
                 * until they receive error */
                Log.w(TAG, "Cannot go up from now, already in the root, Invalid direction!");
                mMediaInterface.changePathRsp(mBDAddr, AvrcpConstants.RSP_INV_DIRN, 0);
            } else {
                /* move folder up */
                mPathStack.pop();
                newPath = mPathStack.peek();
                mMediaBrowser.subscribe(newPath, folderItemsCb);
            }
        } else { /* invalid direction */
            Log.w(TAG, "changePath : Invalid direction " + direction);
            mMediaInterface.changePathRsp(mBDAddr, AvrcpConstants.RSP_INV_DIRN, 0);
        }
    }

    public void getItemAttr(AvrcpCmd.ItemAttrCmd itemAttr) {
        String mediaID;
        if (DEBUG) Log.d(TAG, "getItemAttr");

        /* check if uid is valid by doing a lookup in hashmap */
        mediaID = byteToString(itemAttr.mUid);
        if (mediaID == null) {
            Log.e(TAG, "uid is invalid");
            mMediaInterface.getItemAttrRsp(mBDAddr, AvrcpConstants.RSP_INV_ITEM, null);
            return;
        }

        /* check scope */
        if (itemAttr.mScope != AvrcpConstants.BTRC_SCOPE_FILE_SYSTEM) {
            Log.e(TAG, "invalid scope");
            mMediaInterface.getItemAttrRsp(mBDAddr, AvrcpConstants.RSP_INV_SCOPE, null);
            return;
        }

        if (mMediaBrowser == null) {
            Log.e(TAG, "mMediaBrowser is null");
            mMediaInterface.getItemAttrRsp(mBDAddr, AvrcpConstants.RSP_INTERNAL_ERR, null);
            return;
        }

        /* Subscribe to the parent to list items and retrieve the right one */
        mMediaBrowser.subscribe(mPathStack.peek(), new ItemAttribSubscriber(itemAttr, mediaID));
    }

    public void getTotalNumOfItems(byte scope) {
        if (DEBUG) Log.d(TAG, "getTotalNumOfItems scope = " + scope);
        if (scope != AvrcpConstants.BTRC_SCOPE_FILE_SYSTEM) {
            Log.e(TAG, "getTotalNumOfItems error" + scope);
            mMediaInterface.getTotalNumOfItemsRsp(mBDAddr, AvrcpConstants.RSP_INV_SCOPE, 0, 0);
            return;
        }

        if (mFolderItems == null) {
            Log.e(TAG, "mFolderItems is null, sending internal error");
            /* folderitems were not fetched during change path */
            mMediaInterface.getTotalNumOfItemsRsp(mBDAddr, AvrcpConstants.RSP_INTERNAL_ERR, 0, 0);
            return;
        }

        /* find num items using size of already cached folder items */
        mMediaInterface.getTotalNumOfItemsRsp(
                mBDAddr, AvrcpConstants.RSP_NO_ERROR, 0, mFolderItems.size());
    }

    public void getFolderItemsVFS(AvrcpCmd.FolderItemsCmd reqObj) {
        if (!isPlayerConnected()) {
            Log.e(TAG, "unable to connect to media player, sending internal error");
            /* unable to connect to media player. Send error response to remote device */
            mMediaInterface.folderItemsRsp(mBDAddr, AvrcpConstants.RSP_INTERNAL_ERR, null);
            return;
        }

        if (DEBUG) Log.d(TAG, "getFolderItemsVFS");
        mFolderItemsReqObj = reqObj;

        if (mFolderItems == null) {
            /* Failed to fetch folder items from media player. Send error to remote device */
            Log.e(TAG, "Failed to fetch folder items during getFolderItemsVFS");
            mMediaInterface.folderItemsRsp(mBDAddr, AvrcpConstants.RSP_INTERNAL_ERR, null);
            return;
        }

        /* Filter attributes based on the request and send response to remote device */
        getFolderItemsFilterAttr(mBDAddr, reqObj, mFolderItems,
                AvrcpConstants.BTRC_SCOPE_FILE_SYSTEM, mFolderItemsReqObj.mStartItem,
                mFolderItemsReqObj.mEndItem);
    }

    /* Instructs media player to play particular media item */
    public void playItem(byte[] uid, byte scope) {
        String folderUid;

        if (isPlayerConnected()) {
            /* check if uid is valid */
            if ((folderUid = byteToString(uid)) == null) {
                Log.e(TAG, "uid is invalid!");
                mMediaInterface.playItemRsp(mBDAddr, AvrcpConstants.RSP_INV_ITEM);
                return;
            }

            if (mMediaController != null) {
                MediaController.TransportControls mediaControllerCntrl =
                        mMediaController.getTransportControls();
                if (DEBUG) Log.d(TAG, "Sending playID: " + folderUid);

                if (scope == AvrcpConstants.BTRC_SCOPE_FILE_SYSTEM) {
                    mediaControllerCntrl.playFromMediaId(folderUid, null);
                    mMediaInterface.playItemRsp(mBDAddr, AvrcpConstants.RSP_NO_ERROR);
                } else {
                    Log.e(TAG, "playItem received for invalid scope!");
                    mMediaInterface.playItemRsp(mBDAddr, AvrcpConstants.RSP_INV_SCOPE);
                }
            } else {
                Log.e(TAG, "mediaController is null");
                mMediaInterface.playItemRsp(mBDAddr, AvrcpConstants.RSP_INTERNAL_ERR);
            }
        } else {
            Log.e(TAG, "playItem: Not connected to media player");
            mMediaInterface.playItemRsp(mBDAddr, AvrcpConstants.RSP_INTERNAL_ERR);
        }
    }

    /*
     * helper method to check if startItem and endItem index is with range of
     * MediaItem list. (Resultset containing all items in current path)
     */
    private List<MediaBrowser.MediaItem> checkIndexOutofBounds(
            byte[] bdaddr, List<MediaBrowser.MediaItem> children, long startItem, long endItem) {
        if (endItem >= children.size()) endItem = children.size() - 1;
        if (startItem >= Integer.MAX_VALUE) startItem = Integer.MAX_VALUE;
        try {
            List<MediaBrowser.MediaItem> childrenSubList =
                    children.subList((int) startItem, (int) endItem + 1);
            if (childrenSubList.isEmpty()) {
                Log.i(TAG, "childrenSubList is empty.");
                throw new IndexOutOfBoundsException();
            }
            return childrenSubList;
        } catch (IndexOutOfBoundsException ex) {
            Log.w(TAG, "Index out of bounds start item ="+ startItem + " end item = "+
                    Math.min(children.size(), endItem + 1));
            return null;
        } catch (IllegalArgumentException ex) {
            Log.i(TAG, "Index out of bounds start item =" + startItem + " > size");
            return null;
        }
    }


    /*
     * helper method to filter required attibutes before sending GetFolderItems response
     */
    public void getFolderItemsFilterAttr(byte[] bdaddr, AvrcpCmd.FolderItemsCmd mFolderItemsReqObj,
            List<MediaBrowser.MediaItem> children, byte scope, long startItem, long endItem) {
        if (DEBUG)
            Log.d(TAG,
                    "getFolderItemsFilterAttr: startItem =" + startItem + ", endItem = " + endItem);

        List<MediaBrowser.MediaItem> result_items = new ArrayList<MediaBrowser.MediaItem>();

        if (children == null) {
            Log.e(TAG, "Error: children are null in getFolderItemsFilterAttr");
            mMediaInterface.folderItemsRsp(bdaddr, AvrcpConstants.RSP_INV_RANGE, null);
            return;
        }

        /* check for index out of bound errors */
        result_items = checkIndexOutofBounds(bdaddr, children, startItem, endItem);
        if (result_items == null) {
            Log.w(TAG, "result_items is null.");
            mMediaInterface.folderItemsRsp(bdaddr, AvrcpConstants.RSP_INV_RANGE, null);
            return;
        }
        FolderItemsData folderDataNative = new FolderItemsData(result_items.size());

        /* variables to temperorily add attrs */
        ArrayList<String> attrArray = new ArrayList<String>();
        ArrayList<Integer> attrId = new ArrayList<Integer>();

        for (int itemIndex = 0; itemIndex < result_items.size(); itemIndex++) {
            /* item type. Needs to be set by media player */
            MediaBrowser.MediaItem item = result_items.get(itemIndex);
            int flags = item.getFlags();
            if ((flags & MediaBrowser.MediaItem.FLAG_BROWSABLE) != 0) {
                folderDataNative.mItemTypes[itemIndex] = AvrcpConstants.BTRC_ITEM_FOLDER;
            } else {
                folderDataNative.mItemTypes[itemIndex] = AvrcpConstants.BTRC_ITEM_MEDIA;
            }

            /* set playable */
            if ((flags & MediaBrowser.MediaItem.FLAG_PLAYABLE) != 0) {
                folderDataNative.mPlayable[itemIndex] = AvrcpConstants.ITEM_PLAYABLE;
            } else {
                folderDataNative.mPlayable[itemIndex] = AvrcpConstants.ITEM_NOT_PLAYABLE;
            }
            /* set uid for current item */
            byte[] uid = stringToByte(item.getDescription().getMediaId());
            for (int idx = 0; idx < AvrcpConstants.UID_SIZE; idx++) {
                folderDataNative.mItemUid[itemIndex * AvrcpConstants.UID_SIZE + idx] = uid[idx];
            }

            /* Set display name for current item */
            folderDataNative.mDisplayNames[itemIndex] =
                    getAttrValue(AvrcpConstants.ATTRID_TITLE, item);

            int maxAttributesRequested = 0;
            boolean isAllAttribRequested = false;
            /* check if remote requested for attributes */
            if (mFolderItemsReqObj.mNumAttr != AvrcpConstants.NUM_ATTR_NONE) {
                int attrCnt = 0;

                /* add requested attr ids to a temp array */
                if (mFolderItemsReqObj.mNumAttr == AvrcpConstants.NUM_ATTR_ALL) {
                    isAllAttribRequested = true;
                    maxAttributesRequested = AvrcpConstants.MAX_NUM_ATTR;
                } else {
                    /* get only the requested attribute ids from the request */
                    maxAttributesRequested = mFolderItemsReqObj.mNumAttr;
                }

                /* lookup and copy values of attributes for ids requested above */
                for (int idx = 0; idx < maxAttributesRequested; idx++) {
                    /* check if media player provided requested attributes */
                    String value = null;

                    int attribId = isAllAttribRequested ? (idx + 1) :
                            mFolderItemsReqObj.mAttrIDs[idx];
                    value = getAttrValue(attribId, result_items.get(itemIndex));
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
        if (attrId.size() > 0) {
            folderDataNative.mAttrIds = new int[attrId.size()];
            for (int attrIndex = 0; attrIndex < attrId.size(); attrIndex++)
                folderDataNative.mAttrIds[attrIndex] = attrId.get(attrIndex);
            folderDataNative.mAttrValues = attrArray.toArray(new String[attrArray.size()]);
        }

        /* create rsp object and send response to remote device */
        FolderItemsRsp rspObj = new FolderItemsRsp(AvrcpConstants.RSP_NO_ERROR, Avrcp.sUIDCounter,
                scope, folderDataNative.mNumItems, folderDataNative.mFolderTypes,
                folderDataNative.mPlayable, folderDataNative.mItemTypes, folderDataNative.mItemUid,
                folderDataNative.mDisplayNames, folderDataNative.mAttributesNum,
                folderDataNative.mAttrIds, folderDataNative.mAttrValues);
        mMediaInterface.folderItemsRsp(bdaddr, AvrcpConstants.RSP_NO_ERROR, rspObj);
    }

    public static String getAttrValue(int attr, MediaBrowser.MediaItem item) {
        String attrValue = null;
        try {
            MediaDescription desc = item.getDescription();
            Bundle extras = desc.getExtras();
            switch (attr) {
                /* Title is mandatory attribute */
                case AvrcpConstants.ATTRID_TITLE:
                    attrValue = desc.getTitle().toString();
                    break;

                case AvrcpConstants.ATTRID_ARTIST:
                    attrValue = extras.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    break;

                case AvrcpConstants.ATTRID_ALBUM:
                    attrValue = extras.getString(MediaMetadata.METADATA_KEY_ALBUM);
                    break;

                case AvrcpConstants.ATTRID_TRACK_NUM:
                    attrValue = extras.getString(MediaMetadata.METADATA_KEY_TRACK_NUMBER);
                    break;

                case AvrcpConstants.ATTRID_NUM_TRACKS:
                    attrValue = extras.getString(MediaMetadata.METADATA_KEY_NUM_TRACKS);
                    break;

                case AvrcpConstants.ATTRID_GENRE:
                    attrValue = extras.getString(MediaMetadata.METADATA_KEY_GENRE);
                    break;

                case AvrcpConstants.ATTRID_PLAY_TIME:
                    attrValue = extras.getString(MediaMetadata.METADATA_KEY_DURATION);
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
        if (DEBUG) Log.d(TAG, "getAttrValue: attrvalue = " + attrValue + "attr id:" + attr);
        return attrValue;
    }


    public String getPackageName() {
        return mPackageName;
    }

    /* Helper methods */

    /* check if item is browsable Down*/
    private boolean isBrowsableFolderDn(String uid) {
        for (MediaBrowser.MediaItem item : mFolderItems) {
            if (item.getMediaId().equals(uid) &&
                ((item.getFlags() & MediaBrowser.MediaItem.FLAG_BROWSABLE) ==
                    MediaBrowser.MediaItem.FLAG_BROWSABLE))
                return true;
        }
        return false;
    }

    /* check if browsable Up*/
    private boolean isBrowsableFolderUp() {
        if (mPathStack.peek().equals(mRootFolderUid)) {
            /* Already on the root, cannot go up */
            return false;
        }
        return true;
    }

    /* convert uid to mediaId */
    private String byteToString(byte[] byteArray) {
        int uid = new BigInteger(byteArray).intValue();
        String mediaId = mHmap.get(uid);
        return mediaId;
    }

    /* convert mediaId to uid */
    private byte[] stringToByte(String mediaId) {
        /* check if this mediaId already exists in hashmap */
        if (!mHmap.containsValue(mediaId)) { /* add to hashmap */
            // Offset by one as uid 0 is reserved
            int uid = mHmap.size() + 1;
            mHmap.put(uid, mediaId);
            return intToByteArray(uid);
        } else { /* search key for give mediaId */
            for (int uid : mHmap.keySet()) {
                if (mHmap.get(uid).equals(mediaId)) {
                    return intToByteArray(uid);
                }
            }
        }
        return null;
    }

    /* converts queue item received from getQueue call, to MediaItem used by FilterAttr method */
    private List<MediaBrowser.MediaItem> queueItem2MediaItem(
            List<MediaSession.QueueItem> tempItems) {

        List<MediaBrowser.MediaItem> tempMedia = new ArrayList<MediaBrowser.MediaItem>();
        for (int itemCount = 0; itemCount < tempItems.size(); itemCount++) {
            MediaDescription.Builder build = new MediaDescription.Builder();
            build.setMediaId(Long.toString(tempItems.get(itemCount).getQueueId()));
            build.setTitle(tempItems.get(itemCount).getDescription().getTitle());
            build.setExtras(tempItems.get(itemCount).getDescription().getExtras());
            MediaDescription des = build.build();
            MediaItem item = new MediaItem((des), MediaItem.FLAG_PLAYABLE);
            tempMedia.add(item);
        }
        return tempMedia;
    }

    /* convert integer to byte array of size 8 bytes */
    public byte[] intToByteArray(int value) {
        int index = 0;
        byte[] encodedValue = new byte[AvrcpConstants.UID_SIZE];

        encodedValue[index++] = (byte)0x00;
        encodedValue[index++] = (byte)0x00;
        encodedValue[index++] = (byte)0x00;
        encodedValue[index++] = (byte)0x00;
        encodedValue[index++] = (byte)(value >> 24);
        encodedValue[index++] = (byte)(value >> 16);
        encodedValue[index++] = (byte)(value >> 8);
        encodedValue[index++] = (byte)value;

        return encodedValue;
    }
}
