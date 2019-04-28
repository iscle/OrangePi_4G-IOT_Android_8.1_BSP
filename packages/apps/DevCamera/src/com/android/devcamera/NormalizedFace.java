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
package com.android.devcamera;

import android.graphics.PointF;
import android.graphics.RectF;
import android.hardware.camera2.params.Face;

/**
 *
 * Face coordinates.  Normalized 0 to 1, and in native sensor orientation, which so far seems to be
 * landscape.
 *
 */
public class NormalizedFace {
    public RectF bounds;
    public PointF leftEye;
    public PointF rightEye;
    public PointF mouth;

    public NormalizedFace(Face face, int dX, int dY, int offX, int offY) {
        if (face.getLeftEyePosition() != null) {
            leftEye = new PointF();
            leftEye.x = (float) (face.getLeftEyePosition().x - offX) / dX;
            leftEye.y = (float) (face.getLeftEyePosition().y - offY) / dY;
        }
        if (face.getRightEyePosition() != null) {
            rightEye = new PointF();
            rightEye.x = (float) (face.getRightEyePosition().x - offX) / dX;
            rightEye.y = (float) (face.getRightEyePosition().y - offY) / dY;
        }
        if (face.getMouthPosition() != null) {
            mouth = new PointF();
            mouth.x = (float) (face.getMouthPosition().x - offX) / dX;
            mouth.y = (float) (face.getMouthPosition().y - offY) / dY;
        }
        if (face.getBounds() != null) {
            bounds = new RectF();
            bounds.left = (float) (face.getBounds().left - offX) / dX;
            bounds.top = (float) (face.getBounds().top - offY) / dY;
            bounds.right = (float) (face.getBounds().right - offX) / dX;
            bounds.bottom = (float) (face.getBounds().bottom - offY) / dY;
        }
    }

    public void mirrorInX() {
        if (leftEye != null) {
            leftEye.x = 1f - leftEye.x;
        }
        if (rightEye != null) {
            rightEye.x = 1f - rightEye.x;
        }
        if (mouth != null) {
            mouth.x = 1f - mouth.x;
        }
        float oldLeft = bounds.left;
        bounds.left = 1f - bounds.right;
        bounds.right = 1f - oldLeft;
    }

    /**
     * Typically required for front camera
     */
    public void mirrorInY() {
        if (leftEye != null) {
            leftEye.y = 1f - leftEye.y;
        }
        if (rightEye != null) {
            rightEye.y = 1f - rightEye.y;
        }
        if (mouth != null) {
            mouth.y = 1f - mouth.y;
        }
        float oldTop = bounds.top;
        bounds.top = 1f - bounds.bottom;
        bounds.bottom = 1f - oldTop;
    }
}
