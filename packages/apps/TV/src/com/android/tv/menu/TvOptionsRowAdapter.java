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
import android.media.tv.TvTrackInfo;
import android.support.annotation.VisibleForTesting;

import com.android.tv.Features;
import com.android.tv.TvOptionsManager;
import com.android.tv.customization.CustomAction;
import com.android.tv.data.DisplayMode;
import com.android.tv.ui.TvViewUiManager;
import com.android.tv.ui.sidepanel.ClosedCaptionFragment;
import com.android.tv.ui.sidepanel.DeveloperOptionFragment;
import com.android.tv.ui.sidepanel.DisplayModeFragment;
import com.android.tv.ui.sidepanel.MultiAudioFragment;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.List;

/*
 * An adapter of options.
 */
public class TvOptionsRowAdapter extends CustomizableOptionsRowAdapter {
    public TvOptionsRowAdapter(Context context, List<CustomAction> customActions) {
        super(context, customActions);
    }

    @Override
    protected List<MenuAction> createBaseActions() {
        List<MenuAction> actionList = new ArrayList<>();
        actionList.add(MenuAction.SELECT_CLOSED_CAPTION_ACTION);
        actionList.add(MenuAction.SELECT_DISPLAY_MODE_ACTION);
        if (Features.PICTURE_IN_PICTURE.isEnabled(getMainActivity())) {
            actionList.add(MenuAction.SYSTEMWIDE_PIP_ACTION);
        }
        actionList.add(MenuAction.SELECT_AUDIO_LANGUAGE_ACTION);
        actionList.add(MenuAction.MORE_CHANNELS_ACTION);
        if (Utils.isDeveloper()) {
            actionList.add(MenuAction.DEV_ACTION);
        }
        actionList.add(MenuAction.SETTINGS_ACTION);

        updateClosedCaptionAction();
        updatePipAction();
        updateMultiAudioAction();
        updateDisplayModeAction();
        return actionList;
    }

    @Override
    protected void updateActions() {
        if (updateClosedCaptionAction()) {
            notifyItemChanged(getItemPosition(MenuAction.SELECT_CLOSED_CAPTION_ACTION));
        }
        if (updatePipAction()) {
            notifyItemChanged(getItemPosition(MenuAction.SYSTEMWIDE_PIP_ACTION));
        }
        if (updateMultiAudioAction()) {
            notifyItemChanged(getItemPosition(MenuAction.SELECT_AUDIO_LANGUAGE_ACTION));
        }
        if (updateDisplayModeAction()) {
            notifyItemChanged(getItemPosition(MenuAction.SELECT_DISPLAY_MODE_ACTION));
        }
    }

    @VisibleForTesting
    private boolean updateClosedCaptionAction() {
        return updateActionDescription(MenuAction.SELECT_CLOSED_CAPTION_ACTION);
    }

    private boolean updatePipAction() {
        if (containsItem(MenuAction.SYSTEMWIDE_PIP_ACTION)) {
            return MenuAction.setEnabled(MenuAction.SYSTEMWIDE_PIP_ACTION,
                    !getMainActivity().isScreenBlockedByResourceConflictOrParentalControl());
        }
        return false;
    }

    boolean updateMultiAudioAction() {
        List<TvTrackInfo> audioTracks = getMainActivity().getTracks(TvTrackInfo.TYPE_AUDIO);
        boolean enabled = audioTracks != null && audioTracks.size() > 1;
        // Use "|" operator for non-short-circuit evaluation.
        return MenuAction.setEnabled(MenuAction.SELECT_AUDIO_LANGUAGE_ACTION, enabled)
                | updateActionDescription(MenuAction.SELECT_AUDIO_LANGUAGE_ACTION);
    }

    private boolean updateDisplayModeAction() {
        TvViewUiManager uiManager = getMainActivity().getTvViewUiManager();
        boolean enabled = uiManager.isDisplayModeAvailable(DisplayMode.MODE_FULL)
                || uiManager.isDisplayModeAvailable(DisplayMode.MODE_ZOOM);
        // Use "|" operator for non-short-circuit evaluation.
        return MenuAction.setEnabled(MenuAction.SELECT_DISPLAY_MODE_ACTION, enabled)
                | updateActionDescription(MenuAction.SELECT_DISPLAY_MODE_ACTION);
    }

    private boolean updateActionDescription(MenuAction action) {
        return MenuAction.setActionDescription(action,
                getMainActivity().getTvOptionsManager().getOptionString(action.getType()));
    }

    @Override
    protected void executeBaseAction(int type) {
        switch (type) {
            case TvOptionsManager.OPTION_CLOSED_CAPTIONS:
                getMainActivity().getOverlayManager().getSideFragmentManager()
                        .show(new ClosedCaptionFragment());
                break;
            case TvOptionsManager.OPTION_DISPLAY_MODE:
                getMainActivity().getOverlayManager().getSideFragmentManager()
                        .show(new DisplayModeFragment());
                break;
            case TvOptionsManager.OPTION_SYSTEMWIDE_PIP:
                getMainActivity().enterPictureInPictureMode();
                break;
            case TvOptionsManager.OPTION_MULTI_AUDIO:
                getMainActivity().getOverlayManager().getSideFragmentManager()
                        .show(new MultiAudioFragment());
                break;
            case TvOptionsManager.OPTION_MORE_CHANNELS:
                getMainActivity().showMerchantCollection();
                break;
            case TvOptionsManager.OPTION_DEVELOPER:
                getMainActivity().getOverlayManager().getSideFragmentManager()
                        .show(new DeveloperOptionFragment());
                break;
            case TvOptionsManager.OPTION_SETTINGS:
                getMainActivity().showSettingsFragment();
                break;
        }
    }
}