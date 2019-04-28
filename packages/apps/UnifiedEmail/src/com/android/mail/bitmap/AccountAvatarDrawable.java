/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.mail.bitmap;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;

import com.android.bitmap.BitmapCache;
import com.android.mail.R;

/**
 * A contact drawable with a set default avatar.
 */
public class AccountAvatarDrawable extends AbstractAvatarDrawable {
    private static Bitmap DEFAULT_AVATAR = null;

    public AccountAvatarDrawable(final Resources res, final BitmapCache cache,
            final ContactResolver contactResolver) {
        super(res);
        setBitmapCache(cache);
        setContactResolver(contactResolver);

        if (DEFAULT_AVATAR == null) {
            DEFAULT_AVATAR = BitmapFactory.decodeResource(res, R.drawable.avatar_placeholder_gray);
        }
    }

    @Override
    protected void drawDefaultAvatar(Canvas canvas) {
        drawBitmap(DEFAULT_AVATAR, DEFAULT_AVATAR.getWidth(), DEFAULT_AVATAR.getHeight(),
                canvas);
    }
}

