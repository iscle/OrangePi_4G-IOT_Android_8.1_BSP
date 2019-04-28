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

package com.android.tv.menu;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import com.android.tv.R;
import com.android.tv.TvOptionsManager;
import com.android.tv.TvOptionsManager.OptionType;

/**
 * A class to define possible actions from main menu.
 */
public class MenuAction {
    // Actions in the TV option row.
    public static final MenuAction SELECT_CLOSED_CAPTION_ACTION =
            new MenuAction(R.string.options_item_closed_caption,
                    TvOptionsManager.OPTION_CLOSED_CAPTIONS,
                    R.drawable.ic_tvoption_cc);
    public static final MenuAction SELECT_DISPLAY_MODE_ACTION =
            new MenuAction(R.string.options_item_display_mode, TvOptionsManager.OPTION_DISPLAY_MODE,
                    R.drawable.ic_tvoption_aspect);
    public static final MenuAction SYSTEMWIDE_PIP_ACTION =
            new MenuAction(R.string.options_item_pip, TvOptionsManager.OPTION_SYSTEMWIDE_PIP,
                    R.drawable.ic_tvoption_pip);
    public static final MenuAction SELECT_AUDIO_LANGUAGE_ACTION =
            new MenuAction(R.string.options_item_multi_audio, TvOptionsManager.OPTION_MULTI_AUDIO,
                    R.drawable.ic_tvoption_multi_track);
    public static final MenuAction MORE_CHANNELS_ACTION =
            new MenuAction(R.string.options_item_more_channels,
                    TvOptionsManager.OPTION_MORE_CHANNELS, R.drawable.ic_store);
    public static final MenuAction DEV_ACTION =
            new MenuAction(R.string.options_item_developer,
                    TvOptionsManager.OPTION_DEVELOPER, R.drawable.ic_developer_mode_tv_white_48dp);
    public static final MenuAction SETTINGS_ACTION =
            new MenuAction(R.string.options_item_settings, TvOptionsManager.OPTION_SETTINGS,
                    R.drawable.ic_settings);

    private final String mActionName;
    private final int mActionNameResId;
    @OptionType private final int mType;
    private String mActionDescription;
    private Drawable mDrawable;
    private int mDrawableResId;
    private boolean mEnabled = true;

    /**
     * Sets the action description. Returns {@code trye} if the description is changed.
     */
    public static boolean setActionDescription(MenuAction action, String actionDescription) {
        String oldDescription = action.mActionDescription;
        action.mActionDescription = actionDescription;
        return !TextUtils.equals(action.mActionDescription, oldDescription);
    }

    /**
     * Enables or disables the action. Returns {@code true} if the value is changed.
     */
    public static boolean setEnabled(MenuAction action, boolean enabled) {
        boolean changed = action.mEnabled != enabled;
        action.mEnabled = enabled;
        return changed;
    }

    public MenuAction(int actionNameResId, int type, int drawableResId) {
        mActionName = null;
        mActionNameResId = actionNameResId;
        mType = type;
        mDrawable = null;
        mDrawableResId = drawableResId;
    }

    public MenuAction(String actionName, int type, Drawable drawable) {
        mActionName = actionName;
        mActionNameResId = 0;
        mType = type;
        mDrawable = drawable;
        mDrawableResId = 0;
    }

    public String getActionName(Context context) {
        if (!TextUtils.isEmpty(mActionName)) {
            return mActionName;
        }
        return context.getString(mActionNameResId);
    }

    public String getActionDescription() {
        return mActionDescription;
    }

    @OptionType public int getType() {
        return mType;
    }

    /**
     * Returns Drawable.
     */
    public Drawable getDrawable(Context context) {
        if (mDrawable == null) {
            mDrawable = context.getDrawable(mDrawableResId);
        }
        return mDrawable;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public int getActionNameResId() {
        return mActionNameResId;
    }
}
