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

package com.android.tv.ui.sidepanel;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.tv.R;

public class CheckBoxItem extends CompoundButtonItem {
    private final boolean mLayoutForLargeDescription;

    public CheckBoxItem(String title) {
        this(title, null);
    }

    public CheckBoxItem(String title, String description) {
        this(title, description, false);
    }

    public CheckBoxItem(String title, String description, boolean layoutForLargeDescription) {
        super(title, description);
        mLayoutForLargeDescription = layoutForLargeDescription;
    }

    @Override
    protected void onBind(View view) {
        super.onBind(view);
        if (mLayoutForLargeDescription) {
            CompoundButton checkBox = (CompoundButton) view.findViewById(getCompoundButtonId());
            LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) checkBox.getLayoutParams();
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.topMargin = view.getResources().getDimensionPixelOffset(
                    R.dimen.option_item_check_box_margin_top);
            checkBox.setLayoutParams(lp);

            TypedValue outValue = new TypedValue();
            view.getResources().getValue(R.dimen.option_item_check_box_line_spacing_multiplier,
                    outValue, true);

            TextView descriptionTextView = (TextView) view.findViewById(getDescriptionViewId());
            descriptionTextView.setMaxLines(Integer.MAX_VALUE);
            descriptionTextView.setLineSpacing(0, outValue.getFloat());
        }
    }

    @Override
    protected int getResourceId() {
        return R.layout.option_item_check_box;
    }

    @Override
    protected int getCompoundButtonId() {
        return R.id.check_box;
    }

    @Override
    protected void onSelected() {
        setChecked(!isChecked());
    }
}