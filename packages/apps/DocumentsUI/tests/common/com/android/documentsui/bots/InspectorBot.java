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
package com.android.documentsui.bots;

import android.annotation.StringRes;
import android.app.Activity;
import android.content.Context;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiSelector;
import android.widget.LinearLayout;
import android.widget.TextView;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import com.android.documentsui.inspector.DetailsView;
import com.android.documentsui.R;

public class InspectorBot extends Bots.BaseBot {

    public InspectorBot(UiDevice device, Context context, int timeout) {
        super(device, context, timeout);
    }

    public void assertTitle(String expected) throws Exception {
        UiSelector textView
                = new UiSelector().resourceId("com.android.documentsui:id/inspector_file_title");
        String text = mDevice.findObject(textView).getText();
        assertEquals(expected, text);
    }

    public void assertRowPresent(@StringRes String key, String value, Activity activity)
            throws Exception {
        assertTrue(isRowPresent(key, value, activity));
    }

    private boolean isRowPresent(@StringRes String key, String value, Activity activity)
            throws Exception {
        DetailsView detailsView = (DetailsView) activity.findViewById(R.id.inspector_details_view);
        int children = detailsView.getChildCount();
        for (int i = 0; i < children; i++) {
            LinearLayout child = (LinearLayout) detailsView.getChildAt(i);
            TextView title = (TextView) child.getChildAt(0);
            if (title.getText().equals(key)) {
                TextView info = (TextView) child.getChildAt(1);
                return info.getText().equals(value);
            }
        }
        return false;
    }
}
