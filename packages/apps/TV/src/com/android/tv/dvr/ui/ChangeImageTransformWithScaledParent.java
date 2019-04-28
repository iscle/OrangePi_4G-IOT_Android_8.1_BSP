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

package com.android.tv.dvr.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.transition.ChangeImageTransform;
import android.transition.TransitionValues;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.android.tv.R;

import java.util.Map;

/**
 * TODO: Remove this class once b/32405620 is fixed.
 * This class is for the workaround of b/32405620 and only for the shared element transition between
 * {@link com.android.tv.dvr.ui.browse.RecordingCardView} and
 * {@link com.android.tv.dvr.ui.browse.DvrDetailsActivity}.
 */
public class ChangeImageTransformWithScaledParent extends ChangeImageTransform {
    private static final String PROPNAME_MATRIX = "android:changeImageTransform:matrix";

    public ChangeImageTransformWithScaledParent(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        super.captureStartValues(transitionValues);
        applyParentScale(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        super.captureEndValues(transitionValues);
        applyParentScale(transitionValues);
    }

    private void applyParentScale(TransitionValues transitionValues) {
        View view = transitionValues.view;
        Map<String, Object> values = transitionValues.values;
        Matrix matrix = (Matrix) values.get(PROPNAME_MATRIX);
        if (matrix != null && view.getId() == R.id.details_overview_image
                && view instanceof ImageView) {
            ImageView imageView = (ImageView) view;
            if (imageView.getScaleType() == ScaleType.CENTER_INSIDE
                    && imageView.getDrawable() instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
                if (bitmap.getWidth() < imageView.getWidth()
                        && bitmap.getHeight() < imageView.getHeight()) {
                    float scale = imageView.getContext().getResources().getFraction(
                            R.fraction.lb_focus_zoom_factor_medium, 1, 1);
                    matrix.postScale(scale, scale, imageView.getWidth() / 2,
                            imageView.getHeight() / 2);
                }
            }
        }
    }
}
