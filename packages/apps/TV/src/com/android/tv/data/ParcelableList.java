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

package com.android.tv.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A convenience class for the list of {@link Parcelable}s.
 */
public final class ParcelableList<T extends Parcelable> implements Parcelable {
    /**
     * Create instance from {@link Parcel}.
     */
    public static ParcelableList fromParcel(Parcel in) {
        ParcelableList list = new ParcelableList();
        int length = in.readInt();
        if (length > 0) {
            for (int i = 0; i < length; ++i) {
                list.mList.add(in.readParcelable(Thread.currentThread().getContextClassLoader()));
            }
        }
        return list;
    }

    /**
     * A creator for {@link ParcelableList}.
     */
    public static final Creator<ParcelableList> CREATOR = new Creator<ParcelableList>() {
        @Override
        public ParcelableList createFromParcel(Parcel in) {
            return ParcelableList.fromParcel(in);
        }

        @Override
        public ParcelableList[] newArray(int size) {
            return new ParcelableList[size];
        }
    };

    private final List<T> mList = new ArrayList<>();

    private ParcelableList() { }

    public ParcelableList(Collection<T> initialList) {
        mList.addAll(initialList);
    }

    /**
     * Returns the list.
     */
    public List<T> getList() {
        return new ArrayList<T>(mList);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int paramInt) {
        out.writeInt(mList.size());
        for (T data : mList) {
            out.writeParcelable(data, 0);
        }
    }
}
