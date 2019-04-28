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

import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.MediaDescription;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.service.media.MediaBrowserService.Result;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

// Browsing hierarchy.
// Root:
//      Player1:
//        Now_Playing:
//          MediaItem1
//          MediaItem2
//        Folder1
//        Folder2
//        ....
//      Player2
//      ....
public class BrowseTree {
    private static final String TAG = "BrowseTree";
    private static final boolean DBG = true;

    public static final int DIRECTION_DOWN = 0;
    public static final int DIRECTION_UP = 1;
    public static final int DIRECTION_SAME = 2;
    public static final int DIRECTION_UNKNOWN = -1;

    public static final String ROOT = "__ROOT__";
    public static final String NOW_PLAYING_PREFIX = "NOW_PLAYING";
    public static final String PLAYER_PREFIX = "PLAYER";

    // Static instance of Folder ID <-> Folder Instance (for navigation purposes)
    private final HashMap<String, BrowseNode> mBrowseMap = new HashMap<String, BrowseNode>();
    private BrowseNode mCurrentBrowseNode;
    private BrowseNode mCurrentBrowsedPlayer;
    private BrowseNode mCurrentAddressedPlayer;

    BrowseTree() {
    }

    public void init() {
        MediaDescription.Builder mdb = new MediaDescription.Builder();
        mdb.setMediaId(ROOT);
        mdb.setTitle(ROOT);
        Bundle mdBundle = new Bundle();
        mdBundle.putString(AvrcpControllerService.MEDIA_ITEM_UID_KEY, ROOT);
        mdb.setExtras(mdBundle);
        mBrowseMap.put(ROOT, new BrowseNode(new MediaItem(mdb.build(), MediaItem.FLAG_BROWSABLE)));
        mCurrentBrowseNode = mBrowseMap.get(ROOT);
    }

    public void clear() {
        // Clearing the map should garbage collect everything.
        mBrowseMap.clear();
    }

    // Each node of the tree is represented by Folder ID, Folder Name and the children.
    class BrowseNode {
        // MediaItem to store the media related details.
        MediaItem mItem;

        // Type of this browse node.
        // Since Media APIs do not define the player separately we define that
        // distinction here.
        boolean mIsPlayer = false;

        // If this folder is currently cached, can be useful to return the contents
        // without doing another fetch.
        boolean mCached = false;

        // Result object if this node is not loaded yet. This result object will be used
        // once loading is finished.
        Result<List<MediaItem>> mResult = null;

        // List of children.
        final List<BrowseNode> mChildren = new ArrayList<BrowseNode>();

        BrowseNode(MediaItem item) {
            mItem = item;
        }

        BrowseNode(AvrcpPlayer player) {
            mIsPlayer = true;

            // Transform the player into a item.
            MediaDescription.Builder mdb = new MediaDescription.Builder();
            Bundle mdExtra = new Bundle();
            String playerKey = PLAYER_PREFIX + player.getId();
            mdExtra.putString(AvrcpControllerService.MEDIA_ITEM_UID_KEY, playerKey);
            mdb.setExtras(mdExtra);
            mdb.setMediaId(playerKey);
            mdb.setTitle(player.getName());
            mItem = new MediaBrowser.MediaItem(mdb.build(), MediaBrowser.MediaItem.FLAG_BROWSABLE);
        }

        synchronized List<BrowseNode> getChildren() {
            return mChildren;
        }

        synchronized boolean isChild(BrowseNode node) {
            for (BrowseNode bn : mChildren) {
                if (bn.equals(node)) {
                    return true;
                }
            }
            return false;
        }

        synchronized boolean isCached() {
            return mCached;
        }

        synchronized void setCached(boolean cached) {
            mCached = cached;
        }

        // Fetch the Unique UID for this item, this is unique across all elements in the tree.
        synchronized String getID() {
            return mItem.getDescription().getMediaId();
        }

        // Get the BT Player ID associated with this node.
        synchronized int getPlayerID() {
            return Integer.parseInt(getID().replace(PLAYER_PREFIX, ""));
        }

        // Fetch the Folder UID that can be used to fetch folder listing via bluetooth.
        // This may not be unique hence this combined with direction will define the
        // browsing here.
        synchronized String getFolderUID() {
            return mItem.getDescription().getExtras().getString(
                AvrcpControllerService.MEDIA_ITEM_UID_KEY);
        }

        synchronized MediaItem getMediaItem() {
            return mItem;
        }

        synchronized boolean isPlayer() {
            return mIsPlayer;
        }

        synchronized boolean isNowPlaying() {
            return getID().startsWith(NOW_PLAYING_PREFIX);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof BrowseNode)) {
                return false;
            }
            BrowseNode otherNode = (BrowseNode) other;
            return getID().equals(otherNode.getID());
        }

        @Override
        public String toString() {
            return "ID: " + getID() + " desc: " + mItem;
        }
    }

    synchronized <E> void refreshChildren(String parentID, List<E> children) {
        BrowseNode parent = findFolderByIDLocked(parentID);
        if (parent == null) {
            Log.w(TAG, "parent not found for parentID " + parentID);
            return;
        }
        refreshChildren(parent, children);
    }

    synchronized <E> void refreshChildren(BrowseNode parent, List<E> children) {
        if (children == null) {
            Log.e(TAG, "children cannot be null ");
            return;
        }

        List<BrowseNode> bnList = new ArrayList<BrowseNode>();
        for (E child : children) {
            if (child instanceof MediaItem) {
                bnList.add(new BrowseNode((MediaItem) child));
            } else if (child instanceof AvrcpPlayer) {
                bnList.add(new BrowseNode((AvrcpPlayer) child));
            }
        }

        String parentID = parent.getID();
        // Make sure that the child list is clean.
        if (DBG) {
            Log.d(TAG, "parent " + parentID + " child list " + parent.getChildren());
        }

        addChildrenLocked(parent, bnList);
        List<MediaItem> childrenList = new ArrayList<MediaItem>();
        for (BrowseNode bn : parent.getChildren()) {
            childrenList.add(bn.getMediaItem());
        }

        parent.setCached(true);
    }

    synchronized BrowseNode findBrowseNodeByID(String parentID) {
        BrowseNode bn = mBrowseMap.get(parentID);
        if (bn == null) {
            Log.e(TAG, "folder " + parentID + " not found!");
            return null;
        }
        if (DBG) {
            Log.d(TAG, "Browse map: " + mBrowseMap);
        }
        return bn;
    }

    BrowseNode findFolderByIDLocked(String parentID) {
        return mBrowseMap.get(parentID);
    }

    void addChildrenLocked(BrowseNode parent, List<BrowseNode> items) {
        // Remove existing children and then add the new children.
        for (BrowseNode c : parent.getChildren()) {
            mBrowseMap.remove(c.getID());
        }
        parent.getChildren().clear();

        for (BrowseNode bn : items) {
            parent.getChildren().add(bn);
            mBrowseMap.put(bn.getID(), bn);
        }
    }

    synchronized int getDirection(String toUID) {
        BrowseNode fromFolder = mCurrentBrowseNode;
        BrowseNode toFolder = findFolderByIDLocked(toUID);
        if (fromFolder == null || toFolder == null) {
            Log.e(TAG, "from folder " + mCurrentBrowseNode + " or to folder " + toUID + " null!");
        }

        // Check the relationship.
        if (fromFolder.isChild(toFolder)) {
            return DIRECTION_DOWN;
        } else if (toFolder.isChild(fromFolder)) {
            return DIRECTION_UP;
        } else if (fromFolder.equals(toFolder)) {
            return DIRECTION_SAME;
        } else {
            Log.w(TAG, "from folder " + mCurrentBrowseNode + " children " +
                fromFolder.getChildren() + "to folder " + toUID + " children " +
                toFolder.getChildren());
            return DIRECTION_UNKNOWN;
        }
    }

    synchronized boolean setCurrentBrowsedFolder(String uid) {
        BrowseNode bn = findFolderByIDLocked(uid);
        if (bn == null) {
            Log.e(TAG, "Setting an unknown browsed folder, ignoring bn " + uid);
            return false;
        }

        // Set the previous folder as not cached so that we fetch the contents again.
        if (!bn.equals(mCurrentBrowseNode)) {
            Log.d(TAG, "Set cache false " + bn + " curr " + mCurrentBrowseNode);
            mCurrentBrowseNode.setCached(false);
        }

        mCurrentBrowseNode = bn;
        return true;
    }

    synchronized BrowseNode getCurrentBrowsedFolder() {
        return mCurrentBrowseNode;
    }

    synchronized boolean setCurrentBrowsedPlayer(String uid) {
        BrowseNode bn = findFolderByIDLocked(uid);
        if (bn == null) {
            Log.e(TAG, "Setting an unknown browsed player, ignoring bn " + uid);
            return false;
        }
        mCurrentBrowsedPlayer = bn;
        return true;
    }

    synchronized BrowseNode getCurrentBrowsedPlayer() {
        return mCurrentBrowsedPlayer;
    }

    synchronized boolean setCurrentAddressedPlayer(String uid) {
        BrowseNode bn = findFolderByIDLocked(uid);
        if (bn == null) {
            Log.e(TAG, "Setting an unknown addressed player, ignoring bn " + uid);
            return false;
        }
        mCurrentAddressedPlayer = bn;
        return true;
    }

    synchronized BrowseNode getCurrentAddressedPlayer() {
        return mCurrentAddressedPlayer;
    }

    @Override
    public String toString() {
        return mBrowseMap.toString();
    }
}
