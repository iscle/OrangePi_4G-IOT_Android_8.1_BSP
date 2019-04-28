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
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import com.android.bitmap.BitmapCache;
import com.android.bitmap.RequestKey;
import com.android.bitmap.ReusableBitmap;

import com.android.mail.R;
import com.android.mail.bitmap.ContactResolver.ContactDrawableInterface;

/**
 * A drawable that encapsulates all the functionality needed to display a contact image,
 * including request creation/cancelling and data unbinding/re-binding.
 * <p>
 * The actual contact resolving and decoding is handled by {@link ContactResolver}.
 * <p>
 * For better performance, you should define a cache with {@link #setBitmapCache(BitmapCache)}.
 */
public abstract class AbstractAvatarDrawable extends Drawable implements ContactDrawableInterface {
    protected final Resources mResources;

    private BitmapCache mCache;
    private ContactResolver mContactResolver;

    protected ContactRequest mContactRequest;
    protected ReusableBitmap mBitmap;

    protected final float mBorderWidth;
    protected final Paint mBitmapPaint;
    protected final Paint mBorderPaint;
    protected final Matrix mMatrix;

    private int mDecodedWidth;
    private int mDecodedHeight;

    public AbstractAvatarDrawable(final Resources res) {
        mResources = res;

        mBitmapPaint = new Paint();
        mBitmapPaint.setAntiAlias(true);
        mBitmapPaint.setFilterBitmap(true);
        mBitmapPaint.setDither(true);

        mBorderWidth = res.getDimensionPixelSize(R.dimen.avatar_border_width);

        mBorderPaint = new Paint();
        mBorderPaint.setColor(Color.TRANSPARENT);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(mBorderWidth);
        mBorderPaint.setAntiAlias(true);

        mMatrix = new Matrix();
    }

    public void setBitmapCache(final BitmapCache cache) {
        mCache = cache;
    }

    public void setContactResolver(final ContactResolver contactResolver) {
        mContactResolver = contactResolver;
    }

    @Override
    public void draw(final Canvas canvas) {
        final Rect bounds = getBounds();
        if (!isVisible() || bounds.isEmpty()) {
            return;
        }

        if (mBitmap != null && mBitmap.bmp != null) {
            // Draw sender image.
            drawBitmap(mBitmap.bmp, mBitmap.getLogicalWidth(), mBitmap.getLogicalHeight(), canvas);
        } else {
            // Draw the default image
            drawDefaultAvatar(canvas);
        }
    }

    protected abstract void drawDefaultAvatar(Canvas canvas);

    /**
     * Draw the bitmap onto the canvas at the current bounds taking into account the current scale.
     */
    protected void drawBitmap(final Bitmap bitmap, final int width, final int height,
            final Canvas canvas) {
        final Rect bounds = getBounds();
        // Draw bitmap through shader first.
        final BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
        mMatrix.reset();

        // Fit bitmap to bounds.
        final float boundsWidth = (float) bounds.width();
        final float boundsHeight = (float) bounds.height();
        final float scale = Math.max(boundsWidth / width, boundsHeight / height);
        mMatrix.postScale(scale, scale);

        // Translate bitmap to dst bounds.
        mMatrix.postTranslate(bounds.left, bounds.top);

        shader.setLocalMatrix(mMatrix);
        mBitmapPaint.setShader(shader);
        drawCircle(canvas, bounds, mBitmapPaint);

        // Then draw the border.
        final float radius = bounds.width() / 2f - mBorderWidth / 2;
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), radius, mBorderPaint);
    }

    /**
     * Draws the largest circle that fits within the given <code>bounds</code>.
     *
     * @param canvas the canvas on which to draw
     * @param bounds the bounding box of the circle
     * @param paint the paint with which to draw
     */
    protected static void drawCircle(Canvas canvas, Rect bounds, Paint paint) {
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), bounds.width() / 2, paint);
    }

    @Override
    public int getDecodeWidth() {
        return mDecodedWidth;
    }

    @Override
    public int getDecodeHeight() {
        return mDecodedHeight;
    }

    public void setDecodeDimensions(final int decodeWidth, final int decodeHeight) {
        mDecodedWidth = decodeWidth;
        mDecodedHeight = decodeHeight;
    }

    public void unbind() {
        setImage(null);
    }

    public void bind(final String name, final String email) {
        setImage(new ContactRequest(name, email));
    }

    private void setImage(final ContactRequest contactRequest) {
        if (mContactRequest != null && mContactRequest.equals(contactRequest)) {
            return;
        }

        if (mBitmap != null) {
            mBitmap.releaseReference();
            mBitmap = null;
        }

        if (mContactResolver != null) {
            mContactResolver.remove(mContactRequest, this);
        }

        mContactRequest = contactRequest;

        if (contactRequest == null) {
            invalidateSelf();
            return;
        }

        ReusableBitmap cached = null;
        if (mCache != null) {
            cached = mCache.get(contactRequest, true /* incrementRefCount */);
        }

        if (cached != null) {
            setBitmap(cached);
        } else {
            decode();
        }
    }

    private void decode() {
        if (mContactRequest == null) {
            return;
        }
        // Add to batch.
        mContactResolver.add(mContactRequest, this);
    }

    private void setBitmap(final ReusableBitmap bmp) {
        if (mBitmap != null && mBitmap != bmp) {
            mBitmap.releaseReference();
        }
        mBitmap = bmp;
        invalidateSelf();
    }

    @Override
    public void onDecodeComplete(RequestKey key, ReusableBitmap result) {
        final ContactRequest request = (ContactRequest) key;
        // Remove from batch.
        mContactResolver.remove(request, this);
        if (request.equals(mContactRequest)) {
            setBitmap(result);
        } else {
            // if the requests don't match (i.e. this request is stale), decrement the
            // ref count to allow the bitmap to be pooled
            if (result != null) {
                result.releaseReference();
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mBitmapPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mBitmapPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return 0;
    }
}
