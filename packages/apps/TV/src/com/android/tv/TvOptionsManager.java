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

package com.android.tv;

import android.content.Context;
import android.media.tv.TvTrackInfo;
import android.support.annotation.IntDef;
import android.util.SparseArray;

import com.android.tv.data.DisplayMode;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

/**
 * The TvOptionsManager is responsible for keeping track of current TV options such as closed
 * captions and display mode. Can be also used to create MenuAction items to control such options.
 */
public class TvOptionsManager {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({OPTION_CLOSED_CAPTIONS, OPTION_DISPLAY_MODE, OPTION_SYSTEMWIDE_PIP, OPTION_MULTI_AUDIO,
            OPTION_MORE_CHANNELS, OPTION_DEVELOPER, OPTION_SETTINGS})
    public @interface OptionType {}
    public static final int OPTION_CLOSED_CAPTIONS = 0;
    public static final int OPTION_DISPLAY_MODE = 1;
    public static final int OPTION_SYSTEMWIDE_PIP = 2;
    public static final int OPTION_MULTI_AUDIO = 3;
    public static final int OPTION_MORE_CHANNELS = 4;
    public static final int OPTION_DEVELOPER = 5;
    public static final int OPTION_SETTINGS = 6;

    private final Context mContext;
    private final SparseArray<OptionChangedListener> mOptionChangedListeners = new SparseArray<>();

    private String mClosedCaptionsLanguage;
    private int mDisplayMode;
    private String mMultiAudio;

    public TvOptionsManager(Context context) {
        mContext = context;
    }

    /**
     * Returns a suitable displayed string for the given option type under current settings.
     * @param option the type of option, should be one of {@link OptionType}.
     */
    public String getOptionString(@OptionType int option) {
        switch (option) {
            case OPTION_CLOSED_CAPTIONS:
                if (mClosedCaptionsLanguage == null) {
                    return mContext.getString(R.string.closed_caption_option_item_off);
                }
                return new Locale(mClosedCaptionsLanguage).getDisplayName();
            case OPTION_DISPLAY_MODE:
                return ((MainActivity) mContext).getTvViewUiManager()
                        .isDisplayModeAvailable(mDisplayMode)
                        ? DisplayMode.getLabel(mDisplayMode, mContext)
                        : DisplayMode.getLabel(DisplayMode.MODE_NORMAL, mContext);
            case OPTION_MULTI_AUDIO:
                return mMultiAudio;
        }
        return "";
    }

    /**
     * Handles changing selection of closed caption.
     */
    public void onClosedCaptionsChanged(TvTrackInfo track, int trackIndex) {
        mClosedCaptionsLanguage = (track == null) ?
                null : (track.getLanguage() != null) ? track.getLanguage()
                : mContext.getString(R.string.closed_caption_unknown_language, trackIndex + 1);
        notifyOptionChanged(OPTION_CLOSED_CAPTIONS);
    }

    /**
     * Handles changing selection of display mode.
     */
    public void onDisplayModeChanged(int displayMode) {
        mDisplayMode = displayMode;
        notifyOptionChanged(OPTION_DISPLAY_MODE);
    }

    /**
     * Handles changing selection of multi-audio.
     */
    public void onMultiAudioChanged(String multiAudio) {
        mMultiAudio = multiAudio;
        notifyOptionChanged(OPTION_MULTI_AUDIO);
    }

    private void notifyOptionChanged(@OptionType int option) {
        OptionChangedListener listener = mOptionChangedListeners.get(option);
        if (listener != null) {
            listener.onOptionChanged(option, getOptionString(option));
        }
    }

    /**
     * Sets listeners to changes of the given option type.
     */
    public void setOptionChangedListener(int option, OptionChangedListener listener) {
        mOptionChangedListeners.put(option, listener);
    }

    /**
     * An interface used to monitor option changes.
     */
    public interface OptionChangedListener {
        void onOptionChanged(@OptionType int optionType, String newString);
    }
}