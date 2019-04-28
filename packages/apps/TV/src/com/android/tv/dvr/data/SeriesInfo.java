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
 * limitations under the License
 */

package com.android.tv.dvr.data;

/**
 * Series information.
 */
public class SeriesInfo {
    private final String mId;
    private final String mTitle;
    private final String mDescription;
    private final String mLongDescription;
    private final int[] mCanonicalGenreIds;
    private final String mPosterUri;
    private final String mPhotoUri;

    public SeriesInfo(String id, String title, String description, String longDescription,
            int[] canonicalGenreIds, String posterUri, String photoUri) {
        this.mId = id;
        this.mTitle = title;
        this.mDescription = description;
        this.mLongDescription = longDescription;
        this.mCanonicalGenreIds = canonicalGenreIds;
        this.mPosterUri = posterUri;
        this.mPhotoUri = photoUri;
    }

    /** Returns the ID. **/
    public String getId() {
        return mId;
    }

    /** Returns the title. **/
    public String getTitle() {
        return mTitle;
    }

    /** Returns the description. **/
    public String getDescription() {
        return mDescription;
    }

    /** Returns the description. **/
    public String getLongDescription() {
        return mLongDescription;
    }

    /** Returns the canonical genre IDs. **/
    public int[] getCanonicalGenreIds() {
        return mCanonicalGenreIds;
    }

    /** Returns the poster URI. **/
    public String getPosterUri() {
        return mPosterUri;
    }

    /** Returns the photo URI. **/
    public String getPhotoUri() {
        return mPhotoUri;
    }
}
