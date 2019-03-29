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

package android.media.cts;

import android.graphics.ImageFormat;
import android.graphics.Point;

import android.media.cts.CodecImage;
import android.media.cts.CodecUtils;

import java.nio.ByteBuffer;

public class YUVImage extends CodecImage {
    private final int mImageWidth;
    private final int mImageHeight;
    private final Plane[] mPlanes;

    public YUVImage(
            Point origin,
            int imageWidth, int imageHeight,
            int arrayWidth, int arrayHeight,
            boolean semiPlanar,
            ByteBuffer bufferY, ByteBuffer bufferUV) {
        mImageWidth = imageWidth;
        mImageHeight = imageHeight;
        ByteBuffer dupY = bufferY.duplicate();
        ByteBuffer dupUV = bufferUV.duplicate();
        mPlanes = new Plane[3];

        int srcOffsetY = origin.x + origin.y * arrayWidth;

        mPlanes[0] = new YUVPlane(
                mImageWidth, mImageHeight, arrayWidth, 1,
                dupY, srcOffsetY);

        if (semiPlanar) {
            int srcOffsetUV = origin.y / 2 * arrayWidth + origin.x / 2 * 2;

            mPlanes[1] = new YUVPlane(
                    mImageWidth / 2, mImageHeight / 2, arrayWidth, 2,
                    dupUV, srcOffsetUV);
            mPlanes[2] = new YUVPlane(
                    mImageWidth / 2, mImageHeight / 2, arrayWidth, 2,
                    dupUV, srcOffsetUV + 1);
        } else {
            int srcOffsetU = origin.y / 2 * arrayWidth / 2 + origin.x / 2;
            int srcOffsetV = srcOffsetU + arrayWidth / 2 * arrayHeight / 2;

            mPlanes[1] = new YUVPlane(
                    mImageWidth / 2, mImageHeight / 2, arrayWidth / 2, 1,
                    dupUV, srcOffsetU);
            mPlanes[2] = new YUVPlane(
                    mImageWidth / 2, mImageHeight / 2, arrayWidth / 2, 1,
                    dupUV, srcOffsetV);
        }
    }

    @Override
    public int getFormat() {
        return ImageFormat.YUV_420_888;
    }

    @Override
    public int getWidth() {
        return mImageWidth;
    }

    @Override
    public int getHeight() {
        return mImageHeight;
    }

    @Override
    public long getTimestamp() {
        return 0;
    }

    @Override
    public Plane[] getPlanes() {
        return mPlanes;
    }

    @Override
    public void close() {
        mPlanes[0] = null;
        mPlanes[1] = null;
        mPlanes[2] = null;
    }

    class YUVPlane extends CodecImage.Plane {
        private final int mRowStride;
        private final int mPixelStride;
        private final ByteBuffer mByteBuffer;

        YUVPlane(int w, int h, int rowStride, int pixelStride,
                ByteBuffer buffer, int offset) {
            mRowStride = rowStride;
            mPixelStride = pixelStride;

            // only safe to access length bytes starting from buffer[offset]
            int length = (h - 1) * rowStride + (w - 1) * pixelStride + 1;

            buffer.position(offset);
            mByteBuffer = buffer.slice();
            mByteBuffer.limit(length);
        }

        @Override
        public int getRowStride() {
            return mRowStride;
        }

        @Override
        public int getPixelStride() {
            return mPixelStride;
        }

        @Override
        public ByteBuffer getBuffer() {
            return mByteBuffer;
        }
    }
}

