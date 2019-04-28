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


/*************************************************************************************************
 * Interface for classes which handle callbacks from AvrcpMediaManager.
 * These callbacks should map to native responses and used to communicate with the native layer.
 ************************************************************************************************/

public interface AvrcpMediaRspInterface {
    public void setAddrPlayerRsp(byte[] address, int rspStatus);

    public void setBrowsedPlayerRsp(byte[] address, int rspStatus, byte depth, int numItems,
        String[] textArray);

    public void mediaPlayerListRsp(byte[] address, int rspStatus, MediaPlayerListRsp rspObj);

    public void folderItemsRsp(byte[] address, int rspStatus, FolderItemsRsp rspObj);

    public void changePathRsp(byte[] address, int rspStatus, int numItems);

    public void getItemAttrRsp(byte[] address, int rspStatus, ItemAttrRsp rspObj);

    public void playItemRsp(byte[] address, int rspStatus);

    public void getTotalNumOfItemsRsp(byte[] address, int rspStatus, int uidCounter,
        int numItems);

    public void addrPlayerChangedRsp(int type, int playerId, int uidCounter);

    public void avalPlayerChangedRsp(byte[] address, int type);

    public void uidsChangedRsp(int type);

    public void nowPlayingChangedRsp(int type);

    public void trackChangedRsp(int type, byte[] uid);
}

