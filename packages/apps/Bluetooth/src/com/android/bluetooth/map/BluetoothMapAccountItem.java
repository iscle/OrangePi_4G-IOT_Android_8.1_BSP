/*
* Copyright (C) 2014 Samsung System LSI
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

import android.graphics.drawable.Drawable;
import android.util.Log;

/**
 * Class to contain all the info about the items of the Map Email Settings Menu.
 * It can be used for both Email Apps (group Parent item) and Accounts (Group child Item).
 *
 */
public class BluetoothMapAccountItem implements Comparable<BluetoothMapAccountItem>{
    private static final String TAG = "BluetoothMapAccountItem";

    private static final boolean D = BluetoothMapService.DEBUG;
    private static final boolean V = BluetoothMapService.VERBOSE;

    protected boolean mIsChecked;
    private final String mName;
    private final String mPackageName;
    private final String mId;
    private final String mProviderAuthority;
    private final Drawable mIcon;
    private final BluetoothMapUtils.TYPE mType;
    public final String mBase_uri;
    public final String mBase_uri_no_account;
    private final String mUci;
    private final String mUciPrefix;

    public BluetoothMapAccountItem(String id, String name, String packageName, String authority,
            Drawable icon, BluetoothMapUtils.TYPE appType, String uci, String uciPrefix) {
        this.mName = name;
        this.mIcon = icon;
        this.mPackageName = packageName;
        this.mId = id;
        this.mProviderAuthority = authority;
        this.mType = appType;
        this.mBase_uri_no_account = "content://" + authority;
        this.mBase_uri = mBase_uri_no_account + "/"+id;
        this.mUci = uci;
        this.mUciPrefix = uciPrefix;
    }

    public static BluetoothMapAccountItem create(String id, String name, String packageName,
            String authority, Drawable icon, BluetoothMapUtils.TYPE appType) {
        return new BluetoothMapAccountItem(id, name, packageName, authority,
                icon, appType, null, null);
    }

    public static BluetoothMapAccountItem create(String id, String name, String packageName,
            String authority, Drawable icon, BluetoothMapUtils.TYPE appType, String uci,
            String uciPrefix) {
        return new BluetoothMapAccountItem(id, name, packageName, authority,
                icon, appType, uci, uciPrefix);
    }
    public long getAccountId() {
        if(mId != null) {
            return Long.parseLong(mId);
        }
        return -1;
    }

    public String getUci() {
        return mUci;
    }

    public String getUciPrefix(){
        return mUciPrefix;
    }

    public String getUciFull(){
        if(mUci == null)
            return null;
        if(mUciPrefix == null)
            return null;
        return new StringBuilder(mUciPrefix).append(":").append(mUci).toString();
    }

    @Override
    public int compareTo(BluetoothMapAccountItem other) {

        if(!other.mId.equals(this.mId)){
            if(V) Log.d(TAG, "Wrong id : " + this.mId + " vs " + other.mId);
            return -1;
        }
        if(!other.mName.equals(this.mName)){
            if(V) Log.d(TAG, "Wrong name : " + this.mName + " vs " + other.mName);
            return -1;
        }
        if(!other.mPackageName.equals(this.mPackageName)){
            if(V) Log.d(TAG, "Wrong packageName : " + this.mPackageName + " vs "
                    + other.mPackageName);
             return -1;
        }
        if(!other.mProviderAuthority.equals(this.mProviderAuthority)){
            if(V) Log.d(TAG, "Wrong providerName : " + this.mProviderAuthority + " vs " 
                    + other.mProviderAuthority);
            return -1;
        }
        if(other.mIsChecked != this.mIsChecked){
            if(V) Log.d(TAG, "Wrong isChecked : " + this.mIsChecked + " vs " + other.mIsChecked);
            return -1;
        }
        if(!other.mType.equals(this.mType)){
            if(V) Log.d(TAG, "Wrong appType : " + this.mType + " vs " + other.mType);
             return -1;
        }
        return 0;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mId == null) ? 0 : mId.hashCode());
        result = prime * result + ((mName == null) ? 0 : mName.hashCode());
        result = prime * result
                + ((mPackageName == null) ? 0 : mPackageName.hashCode());
        result = prime * result
                + ((mProviderAuthority == null) ? 0 : mProviderAuthority.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BluetoothMapAccountItem other = (BluetoothMapAccountItem) obj;
        if (mId == null) {
            if (other.mId != null)
                return false;
        } else if (!mId.equals(other.mId))
            return false;
        if (mName == null) {
            if (other.mName != null)
                return false;
        } else if (!mName.equals(other.mName))
            return false;
        if (mPackageName == null) {
            if (other.mPackageName != null)
                return false;
        } else if (!mPackageName.equals(other.mPackageName))
            return false;
        if (mProviderAuthority == null) {
            if (other.mProviderAuthority != null)
                return false;
        } else if (!mProviderAuthority.equals(other.mProviderAuthority))
            return false;
        if (mType == null) {
            if (other.mType != null)
                return false;
        } else if (!mType.equals(other.mType))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return mName + " (" + mBase_uri + ")";
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public String getName() {
        return mName;
    }

    public String getId() {
        return mId;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getProviderAuthority() {
        return mProviderAuthority;
    }

    public BluetoothMapUtils.TYPE getType() {
        return mType;
    }

}
