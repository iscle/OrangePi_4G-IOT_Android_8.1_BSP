/*
* Copyright (C) 2015 Samsung System LSI
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
package com.android.bluetooth.map;

import com.android.bluetooth.SignedLongLong;


/**
 * Local representation of an Android contact
 */
public class MapContact {
    private final String mName;
    private final long mId;

    private MapContact(long id, String name) {
        mId = id;
        mName = name;
    }

    public static MapContact create(long id, String name){
        return new MapContact(id, name);
    }

    public String getName() {
        return mName;
    }

    public long getId() {
        return mId;
    }

    public String getXBtUidString() {
        if(mId > 0) {
            return  BluetoothMapUtils.getLongLongAsString(mId, 0);
        }
        return null;
    }

    public SignedLongLong getXBtUid() {
        if(mId > 0) {
            return  new SignedLongLong(mId, 0);
        }
        return null;
    }

    @Override
    public String toString(){
        return mName;
    }
}
