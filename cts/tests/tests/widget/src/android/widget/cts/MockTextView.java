/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.widget.cts;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.method.MovementMethod;
import android.util.AttributeSet;
import android.widget.TextView;

public class MockTextView extends TextView {

    public MockTextView(Context context) {
        super(context);
    }

    public MockTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MockTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public int computeHorizontalScrollRange() {
        return super.computeHorizontalScrollRange();
    }

    @Override
    public int computeVerticalScrollRange() {
        return super.computeVerticalScrollRange();
    }

    @Override
    public boolean getDefaultEditable() {
        return super.getDefaultEditable();
    }

    @Override
    public MovementMethod getDefaultMovementMethod() {
        return super.getDefaultMovementMethod();
    }

    @Override
    public boolean setFrame(int l, int t, int r, int b) {
        return super.setFrame(l, t, r, b);
    }

    @Override
    public float getLeftFadingEdgeStrength() {
        return super.getLeftFadingEdgeStrength();
    }

    @Override
    public float getRightFadingEdgeStrength() {
        return super.getRightFadingEdgeStrength();
    }

    @Override
    public int getBottomPaddingOffset() {
        return super.getBottomPaddingOffset();
    }

    @Override
    public int getLeftPaddingOffset() {
        return super.getLeftPaddingOffset();
    }

    @Override
    public int getRightPaddingOffset() {
        return super.getRightPaddingOffset();
    }

    @Override
    public int getTopPaddingOffset() {
        return super.getTopPaddingOffset();
    }

    @Override
    public boolean isPaddingOffsetRequired() {
        return super.isPaddingOffsetRequired();
    }

    @Override
    public void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
    }

    @Override
    public void drawableStateChanged() {
        super.drawableStateChanged();
    }

    @Override
    public boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who);
    }

    @Override
    public int computeVerticalScrollExtent() {
        return super.computeVerticalScrollExtent();
    }
}
