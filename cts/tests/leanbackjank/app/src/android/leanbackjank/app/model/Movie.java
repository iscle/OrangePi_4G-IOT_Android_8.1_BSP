/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.leanbackjank.app.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Movie class represents video entity with title, description, image thumbs and video url.
 */
public class Movie implements Parcelable {
    static final long serialVersionUID = 727566175075960653L;
    private static int sCount = 0;
    private String mId;
    private String mTitle;
    private String mDescription;
    private String mStudio;
    private String mCategory;

    public Movie() {
    }

    public Movie(Parcel in){
        String[] data = new String[5];

        in.readStringArray(data);
        mId = data[0];
        mTitle = data[1];
        mDescription = data[2];
        mStudio = data[3];
        mCategory = data[4];
    }

    public static String getCount() {
        return Integer.toString(sCount);
    }

    public static void incrementCount() {
        sCount++;
    }

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        mId = id;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getDescription() {
        return mDescription;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public String getStudio() {
        return mStudio;
    }

    public void setStudio(String studio) {
        mStudio = studio;
    }

    public String getCategory() {
        return mCategory;
    }

    public void setCategory(String category) {
        mCategory = category;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(new String[] {mId,
                mTitle,
                mDescription,
                mStudio,
                mCategory});
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(200);
        sb.append("Movie{");
        sb.append("mId=" + mId);
        sb.append(", mTitle='" + mTitle + '\'');
        sb.append('}');
        return sb.toString();
    }

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
        public Movie createFromParcel(Parcel in) {
            return new Movie(in);
        }

        public Movie[] newArray(int size) {
            return new Movie[size];
        }
    };
}
