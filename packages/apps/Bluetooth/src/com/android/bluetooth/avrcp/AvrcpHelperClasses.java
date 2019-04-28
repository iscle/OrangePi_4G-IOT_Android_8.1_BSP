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
import android.media.session.MediaSession;

import com.android.bluetooth.Utils;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Collection;

/*************************************************************************************************
 * Helper classes used for callback/response of browsing commands:-
 *     1) To bundle parameters for  native callbacks/response.
 *     2) Stores information of Addressed and Browsed Media Players.
 ************************************************************************************************/

class AvrcpCmd {

    public AvrcpCmd() {}

    /* Helper classes to pass parameters from callbacks to Avrcp handler */
    class FolderItemsCmd {
        byte mScope;
        long mStartItem;
        long mEndItem;
        byte mNumAttr;
        int[] mAttrIDs;
        public byte[] mAddress;

        public FolderItemsCmd(byte[] address, byte scope, long startItem, long endItem,
                byte numAttr, int[] attrIds) {
            mAddress = address;
            this.mScope = scope;
            this.mStartItem = startItem;
            this.mEndItem = endItem;
            this.mNumAttr = numAttr;
            this.mAttrIDs = attrIds;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[FolderItemCmd: scope " + mScope);
            sb.append(" start " + mStartItem);
            sb.append(" end " + mEndItem);
            sb.append(" numAttr " + mNumAttr);
            sb.append(" attrs: ");
            for (int i = 0; i < mNumAttr; i++) {
                sb.append(mAttrIDs[i] + " ");
            }
            return sb.toString();
        }
    }

    class ItemAttrCmd {
        byte mScope;
        byte[] mUid;
        int mUidCounter;
        byte mNumAttr;
        int[] mAttrIDs;
        public byte[] mAddress;

        public ItemAttrCmd(byte[] address, byte scope, byte[] uid, int uidCounter, byte numAttr,
                int[] attrIDs) {
            mAddress = address;
            mScope = scope;
            mUid = uid;
            mUidCounter = uidCounter;
            mNumAttr = numAttr;
            mAttrIDs = attrIDs;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[ItemAttrCmd: scope " + mScope);
            sb.append(" uid " + Utils.byteArrayToString(mUid));
            sb.append(" numAttr " + mNumAttr);
            sb.append(" attrs: ");
            for (int i = 0; i < mNumAttr; i++) {
                sb.append(mAttrIDs[i] + " ");
            }
            return sb.toString();
        }
    }

    class ElementAttrCmd {
        byte mNumAttr;
        int[] mAttrIDs;
        public byte[] mAddress;

        public ElementAttrCmd(byte[] address, byte numAttr, int[] attrIDs) {
            mAddress = address;
            mNumAttr = numAttr;
            mAttrIDs = attrIDs;
        }
    }
}

/* Helper classes to pass parameters to native response */
class MediaPlayerListRsp {
    byte mStatus;
    short mUIDCounter;
    byte itemType;
    int[] mPlayerIds;
    byte[] mPlayerTypes;
    int[] mPlayerSubTypes;
    byte[] mPlayStatusValues;
    short[] mFeatureBitMaskValues;
    String[] mPlayerNameList;
    int mNumItems;

    public MediaPlayerListRsp(byte status, short UIDCounter, int numItems, byte itemType,
            int[] playerIds, byte[] playerTypes, int[] playerSubTypes, byte[] playStatusValues,
            short[] featureBitMaskValues, String[] playerNameList) {
        this.mStatus = status;
        this.mUIDCounter = UIDCounter;
        this.mNumItems = numItems;
        this.itemType = itemType;
        this.mPlayerIds = playerIds;
        this.mPlayerTypes = playerTypes;
        this.mPlayerSubTypes = new int[numItems];
        this.mPlayerSubTypes = playerSubTypes;
        this.mPlayStatusValues = new byte[numItems];
        this.mPlayStatusValues = playStatusValues;
        int bitMaskSize = AvrcpConstants.AVRC_FEATURE_MASK_SIZE;
        this.mFeatureBitMaskValues = new short[numItems * bitMaskSize];
        for (int bitMaskIndex = 0; bitMaskIndex < (numItems * bitMaskSize); bitMaskIndex++) {
            this.mFeatureBitMaskValues[bitMaskIndex] = featureBitMaskValues[bitMaskIndex];
        }
        this.mPlayerNameList = playerNameList;
    }
}

class FolderItemsRsp {
    byte mStatus;
    short mUIDCounter;
    byte mScope;
    int mNumItems;
    byte[] mFolderTypes;
    byte[] mPlayable;
    byte[] mItemTypes;
    byte[] mItemUid;
    String[] mDisplayNames; /* display name of the item. Eg: Folder name or song name */
    int[] mAttributesNum;
    int[] mAttrIds;
    String[] mAttrValues;

    public FolderItemsRsp(byte Status, short UIDCounter, byte scope, int numItems,
            byte[] folderTypes, byte[] playable, byte[] ItemTypes, byte[] ItemsUid,
            String[] displayNameArray, int[] AttributesNum, int[] AttrIds, String[] attrValues) {
        this.mStatus = Status;
        this.mUIDCounter = UIDCounter;
        this.mScope = scope;
        this.mNumItems = numItems;
        this.mFolderTypes = folderTypes;
        this.mPlayable = playable;
        this.mItemTypes = ItemTypes;
        this.mItemUid = ItemsUid;
        this.mDisplayNames = displayNameArray;
        this.mAttributesNum = AttributesNum;
        this.mAttrIds = AttrIds;
        this.mAttrValues = attrValues;
    }
}

class ItemAttrRsp {
    byte mStatus;
    byte mNumAttr;
    int[] mAttributesIds;
    String[] mAttributesArray;

    public ItemAttrRsp(byte status, int[] attributesIds, String[] attributesArray) {
        mStatus = status;
        mNumAttr = (byte) attributesIds.length;
        mAttributesIds = attributesIds;
        mAttributesArray = attributesArray;
    }
}

/* stores information of Media Players in the system */
class MediaPlayerInfo {

    private byte majorType;
    private int subType;
    private byte playStatus;
    private short[] featureBitMask;
    private @NonNull String packageName;
    private @NonNull String displayableName;
    private @Nullable MediaController mediaController;

    MediaPlayerInfo(@Nullable MediaController controller, byte majorType, int subType,
            byte playStatus, short[] featureBitMask, @NonNull String packageName,
            @Nullable String displayableName) {
        this.setMajorType(majorType);
        this.setSubType(subType);
        this.playStatus = playStatus;
        // store a copy the FeatureBitMask array
        this.featureBitMask = Arrays.copyOf(featureBitMask, featureBitMask.length);
        Arrays.sort(this.featureBitMask);
        this.setPackageName(packageName);
        this.setDisplayableName(displayableName);
        this.setMediaController(controller);
    }

    /* getters and setters */
    byte getPlayStatus() {
        return playStatus;
    }

    void setPlayStatus(byte playStatus) {
        this.playStatus = playStatus;
    }

    MediaController getMediaController() {
        return mediaController;
    }

    void setMediaController(MediaController mediaController) {
        if (mediaController != null) {
            this.packageName = mediaController.getPackageName();
        }
        this.mediaController = mediaController;
    }

    void setPackageName(@NonNull String name) {
        // Controller determines package name when it is set.
        if (mediaController != null) return;
        this.packageName = name;
    }

    String getPackageName() {
        if (mediaController != null) {
            return mediaController.getPackageName();
        } else if (packageName != null) {
            return packageName;
        }
        return null;
    }

    byte getMajorType() {
        return majorType;
    }

    void setMajorType(byte majorType) {
        this.majorType = majorType;
    }

    int getSubType() {
        return subType;
    }

    void setSubType(int subType) {
        this.subType = subType;
    }

    String getDisplayableName() {
        return displayableName;
    }

    void setDisplayableName(@Nullable String displayableName) {
        if (displayableName == null) displayableName = "";
        this.displayableName = displayableName;
    }

    short[] getFeatureBitMask() {
        return featureBitMask;
    }

    void setFeatureBitMask(short[] featureBitMask) {
        synchronized (this) {
            this.featureBitMask = Arrays.copyOf(featureBitMask, featureBitMask.length);
            Arrays.sort(this.featureBitMask);
        }
    }

    boolean isBrowseSupported() {
        synchronized (this) {
            if (this.featureBitMask == null) return false;
            for (short bit : this.featureBitMask) {
                if (bit == AvrcpConstants.AVRC_PF_BROWSE_BIT_NO) return true;
            }
        }
        return false;
    }

    /** Tests if the view of this player presented to the controller is different enough to
     *  justify sending an Available Players Changed update */
    public boolean equalView(MediaPlayerInfo other) {
        return (this.majorType == other.getMajorType()) && (this.subType == other.getSubType())
                && Arrays.equals(this.featureBitMask, other.getFeatureBitMask())
                && this.displayableName.equals(other.getDisplayableName());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MediaPlayerInfo ");
        sb.append(getPackageName());
        sb.append(" (as '" + getDisplayableName() + "')");
        sb.append(" Type = " + getMajorType());
        sb.append(", SubType = " + getSubType());
        sb.append(", Status = " + playStatus);
        sb.append(" Feature Bits [");
        short[] bits = getFeatureBitMask();
        for (int i = 0; i < bits.length; i++) {
            if (i != 0) sb.append(" ");
            sb.append(bits[i]);
        }
        sb.append("] Controller: ");
        sb.append(getMediaController());
        return sb.toString();
    }
}

/* stores information for browsable Media Players available in the system */
class BrowsePlayerInfo {
    String packageName;
    String displayableName;
    String serviceClass;

    public BrowsePlayerInfo(String packageName, String displayableName, String serviceClass) {
        this.packageName = packageName;
        this.displayableName = displayableName;
        this.serviceClass = serviceClass;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BrowsePlayerInfo ");
        sb.append(packageName);
        sb.append(" ( as '" + displayableName + "')");
        sb.append(" service " + serviceClass);
        return sb.toString();
    }
}

class FolderItemsData {
    /* initialize sizes for rsp parameters */
    int mNumItems;
    int[] mAttributesNum;
    byte[] mFolderTypes;
    byte[] mItemTypes;
    byte[] mPlayable;
    byte[] mItemUid;
    String[] mDisplayNames;
    int[] mAttrIds;
    String[] mAttrValues;
    int attrCounter;

    public FolderItemsData(int size) {
        mNumItems = size;
        mAttributesNum = new int[size];

        mFolderTypes = new byte[size]; /* folderTypes */
        mItemTypes = new byte[size]; /* folder or media item */
        mPlayable = new byte[size];
        Arrays.fill(mFolderTypes, AvrcpConstants.FOLDER_TYPE_MIXED);
        Arrays.fill(mItemTypes, AvrcpConstants.BTRC_ITEM_MEDIA);
        Arrays.fill(mPlayable, AvrcpConstants.ITEM_PLAYABLE);

        mItemUid = new byte[size * AvrcpConstants.UID_SIZE];
        mDisplayNames = new String[size];

        mAttrIds = null; /* array of attr ids */
        mAttrValues = null; /* array of attr values */
    }
}

/** A queue that evicts the first element when you add an element to the end when it reaches a
 * maximum size.
 * This is useful for keeping a FIFO queue of items where the items drop off the front, i.e. a log
 * with a maximum size.
 */
class EvictingQueue<E> extends ArrayDeque<E> {
    private int mMaxSize;

    public EvictingQueue(int maxSize) {
        super();
        mMaxSize = maxSize;
    }

    public EvictingQueue(int maxSize, int initialElements) {
        super(initialElements);
        mMaxSize = maxSize;
    }

    public EvictingQueue(int maxSize, Collection<? extends E> c) {
        super(c);
        mMaxSize = maxSize;
    }

    @Override
    public void addFirst(E e) {
        if (super.size() == mMaxSize) return;
        super.addFirst(e);
    }

    @Override
    public void addLast(E e) {
        if (super.size() == mMaxSize) {
            super.remove();
        }
        super.addLast(e);
    }

    @Override
    public boolean offerFirst(E e) {
        if (super.size() == mMaxSize) return false;
        return super.offerFirst(e);
    }

    @Override
    public boolean offerLast(E e) {
        if (super.size() == mMaxSize) {
            super.remove();
        }
        return super.offerLast(e);
    }
}
