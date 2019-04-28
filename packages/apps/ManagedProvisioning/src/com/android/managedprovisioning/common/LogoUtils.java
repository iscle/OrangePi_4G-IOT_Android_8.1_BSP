/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.common;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;

import java.io.File;

public class LogoUtils {
    @VisibleForTesting static final int DEFAULT_LOGO_ID = R.drawable.ic_enterprise_blue_24dp;

    public static void saveOrganisationLogo(Context context, Uri uri) {
        final File logoFile = getOrganisationLogoFile(context);
        StoreUtils.copyUriIntoFile(context.getContentResolver(), uri, logoFile);
    }

    /**
     * @param colorTint optional color colorTint to apply to the logo
     */
    public static @NonNull Drawable getOrganisationLogo(Context context, Integer colorTint) {
        final File logoFile = getOrganisationLogoFile(context);
        Bitmap bitmap = null;
        int maxWidth = (int) context.getResources().getDimension(R.dimen.max_logo_width);
        int maxHeight = (int) context.getResources().getDimension(R.dimen.max_logo_height);
        if (logoFile.exists()) {
            bitmap = getBitmapPartiallyResized(logoFile.getPath(), maxWidth, maxHeight);
            if (bitmap == null) {
                ProvisionLogger.loge("Could not get organisation logo from " + logoFile);
            }
        }

        if (bitmap != null) {
            return new BitmapDrawable(context.getResources(),
                    resizeBitmap(bitmap, maxWidth, maxHeight));
        }

        // fall back to a default logo
        Drawable organisationLogo = context.getDrawable(DEFAULT_LOGO_ID);
        if (colorTint != null) {
            organisationLogo.setColorFilter(colorTint, PorterDuff.Mode.SRC_ATOP);
        }
        return organisationLogo;
    }

    /**
     * Decodes a bitmap from an input stream.
     * If the actual dimensions of the bitmap are larger than the desired ones, will try to return a
     * subsample.
     * The point of using this method is that the entire image may be too big to fit entirely in
     * memmory. Since we may not need the entire image anyway, it's better to only decode a
     * subsample when possible.
     */
    @VisibleForTesting
    static Bitmap getBitmapPartiallyResized(String filePath, int maxDesiredWidth,
            int maxDesiredHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        // Firstly, let's just get the dimensions of the image.
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, bounds);
        int streamWidth = bounds.outWidth;
        int streamHeight = bounds.outHeight;
        int ratio = Math.max(streamWidth / maxDesiredWidth, streamHeight / maxDesiredHeight);
        if (ratio > 1) {
            // Decodes a smaller bitmap. Note that this ratio will be rounded down to the nearest
            // power of 2. The decoded bitmap will not have the expected size, but we'll do another
            // round of scaling.
            bounds.inSampleSize = ratio;
        }
        bounds.inJustDecodeBounds = false;
        // Now, decode the actual bitmap
        return BitmapFactory.decodeFile(filePath, bounds);
    }

    /*
     * Returns a new Bitmap with the specified maximum width and height. Does scaling if
     * necessary. Keeps the ratio of the original image.
     */
    @VisibleForTesting
    static Bitmap resizeBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        double ratio = Math.max((double) width / maxWidth, (double) height / maxHeight);
        // We don't scale up.
        if (ratio > 1) {
            width /= ratio;
            height /= ratio;
            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
        }
        return bitmap;
    }

    public static void cleanUp(Context context) {
        getOrganisationLogoFile(context).delete();
    }

    private static File getOrganisationLogoFile(Context context) {
        return new File(context.getFilesDir() + File.separator + "organisation_logo");
    }
}
