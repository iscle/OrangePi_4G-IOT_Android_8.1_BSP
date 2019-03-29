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

package android.renderscript.cts.refocus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.renderscript.cts.refocus.image.RangeInverseDepthTransform;
import android.net.Uri;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class DepthImage {
    private final String mFormat;
    private final double mFar;
    private final double mNear;
    private final Bitmap mDepthBitmap;
    private final double mBlurAtInfinity;
    private final double mFocalDistance;
    private final double mDepthOfFiled;
    private final double mFocalPointX;
    private final double mFocalPointY;
    private final DepthTransform mDepthTransform;

    public DepthImage(String format, double far, double near,
                      Bitmap depthBitmap, double blurAtInfinity,
                      double focalDistance, double depthOfField,
                      double focalPointX, double focalPointY,
                      DepthTransform depthTransform) {
        mFormat = format;
        mFar = far;
        mNear = near;
        mDepthBitmap = depthBitmap;
        mBlurAtInfinity = blurAtInfinity;
        mFocalDistance = focalDistance;
        mDepthOfFiled = depthOfField;
        mFocalPointX = focalPointX;
        mFocalPointY = focalPointY;
        mDepthTransform = depthTransform;
    }

    public static DepthImage createFromXMPMetadata(Context context, Uri image)
            throws IOException {
        InputStream input = context.getContentResolver().openInputStream(image);
        XmpDepthDecode decode = new XmpDepthDecode(input);
        return new DepthImage(decode.getFormat(), decode.getFar(),
                              decode.getNear(), decode.getDepthBitmap(),
                              decode.getBlurAtInfinity(),
                              decode.getFocalDistance(),
                              decode.getDepthOfField(),
                              decode.getFocalPointX(),
                              decode.getFocalPointY(),
                              decode.getDepthTransform());
    }

    public static DepthImage createFromDepthmap(Context context, Uri uriDepthmap)
            throws IOException {
        Bitmap bitmap = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uriDepthmap));
        if (bitmap == null) {
            throw new FileNotFoundException(uriDepthmap.toString());
        }

        double near = 12.0;
        double far = 120.0;
        DepthTransform transform = new RangeInverseDepthTransform((float)near, (float)far);
        return new DepthImage(RangeInverseDepthTransform.FORMAT,
                              far,
                              near,
                              bitmap, // depthmap
                              5.0,    // blur at ininity
                              15.0,   // focal distance
                              0.1,    // depth of field
                              0.5,    // x of focal point
                              0.5,    // y of focla point
                              transform);
    }

    public Bitmap getDepthBitmap() {
        return mDepthBitmap;
    }

    public DepthTransform getDepthTransform() { return mDepthTransform; }

    public String getFormat() {
        return mFormat;
    }

    public double getFar() {
        return mFar;
    }

    public double getNear() {
        return mNear;
    }

    public double getBlurAtInfinity() {
        return mBlurAtInfinity;
    }

    public double getFocalDistance() {
        return mFocalDistance;
    }

    public double getDepthOfField() {return mDepthOfFiled; }

    public double getFocalPointX() {
        return mFocalPointX;
    }

    public double getFocalPointY() {
        return mFocalPointY;
    }
}

