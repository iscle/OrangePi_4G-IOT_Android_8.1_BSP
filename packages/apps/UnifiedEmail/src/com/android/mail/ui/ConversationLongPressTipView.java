/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.mail.ui;

import android.content.Context;

import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.Folder;

/**
 * A tip to educate users about long press to enter CAB mode.  Appears on top of conversation list.
 */
public class ConversationLongPressTipView extends ConversationTipView {
    private final MailPrefs mMailPrefs;
    private boolean mShow;

    public ConversationLongPressTipView(final Context context) {
        super(context);

        mMailPrefs = MailPrefs.get(context);
        setText(getResources().getString(R.string.long_press_to_select_tip));
    }

    @Override
    public void onUpdate(Folder folder, ConversationCursor cursor) {
        mShow = checkWhetherToShow();
    }

    @Override
    public boolean getShouldDisplayInList() {
        mShow = checkWhetherToShow();
        return mShow;
    }

    private boolean checkWhetherToShow() {
        // show if 1) sender images are disabled 2) there are items
        return !shouldShowSenderImage() && !mAdapter.isEmpty()
                && !mMailPrefs.isLongPressToSelectTipAlreadyShown();
    }

    @Override
    public void onCabModeEntered() {
        if (mShow) {
            dismiss();
        }
    }

    @Override
    public void dismiss() {
        if (mShow) {
            mMailPrefs.setLongPressToSelectTipAlreadyShown();
            mShow = false;
            Analytics.getInstance().sendEvent("list_swipe", "long_press_tip", null, 0);
        }
        super.dismiss();
    }

    protected boolean shouldShowSenderImage() {
        return mMailPrefs.getShowSenderImages();
    }
}