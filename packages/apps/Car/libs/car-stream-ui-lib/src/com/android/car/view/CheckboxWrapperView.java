/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.car.view;

import android.content.Context;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.widget.CheckBox;

/**
 * A wrapper class for CheckBox that is required because the state is created by two different
 * class loaders, which causes a crash. When the state and class are created by different class
 * loaders, the default state will be restored instead of the saved state.
 * Reflection cannot be used to recreate the state because the class that stores the state
 * (CompoundButton$SavedState) has been stripped out of the Android SDK.
 * @hide
 */
public class CheckboxWrapperView extends CheckBox {

    public CheckboxWrapperView(Context context) {
        super(context);
    }

    public CheckboxWrapperView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckboxWrapperView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        // If the class loaders are different, just restore the default state. This is fine
        // since we refetch the menus anyways and any state will be restored then.
        if (state.getClass().getClassLoader() != getClass().getClassLoader()) {
            super.onRestoreInstanceState(onSaveInstanceState());
        } else {
            super.onRestoreInstanceState(state);
        }
    }
}
