/*
 * Copyright 2017, The Android Open Source Project
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
package com.android.managedprovisioning.common;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.SoundEffectConstants;
import android.view.View;

/** Used to standardize the way we set up clickable spanned elements */
public class ClickableSpanFactory {
    private final int linkColor;

    /** @param linkColor color value (i.e. not resource id) */
    public ClickableSpanFactory(int linkColor) {
        this.linkColor = linkColor;
    }

    /**
     * @param intent to start on click
     */
    public @NonNull ClickableSpan create(@NonNull Intent intent) {
        return new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                widget.playSoundEffect(SoundEffectConstants.CLICK);
                widget.getContext().startActivity(intent);
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
                ds.setColor(linkColor);
            }
        };
    }
}