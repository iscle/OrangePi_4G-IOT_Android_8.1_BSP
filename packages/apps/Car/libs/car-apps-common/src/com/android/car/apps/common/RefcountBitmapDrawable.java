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
package com.android.car.apps.common;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

/**
 * RefcountBitmapDrawable class
 * @hide
 */
public class RefcountBitmapDrawable extends BitmapDrawable {

    private RefcountObject<Bitmap> mRefcountObject;

    /**
     *  create initial drawable,  this will not increase the refcount
     */
    public RefcountBitmapDrawable(Resources res, RefcountObject<Bitmap> bitmap) {
        super(res, bitmap.getObject());
        mRefcountObject = bitmap;
    }

    /**
     *  create the drawable from existing drawable, will not increase refcount
     */
    public RefcountBitmapDrawable(Resources res, RefcountBitmapDrawable drawable) {
        this(res, drawable.getRefcountObject());
    }

    public RefcountObject<Bitmap> getRefcountObject() {
        return mRefcountObject;
    }
}
