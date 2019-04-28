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

package com.android.tv.ui.sidepanel.parentalcontrols;

import android.view.View;
import android.widget.TextView;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.data.Channel;
import com.android.tv.dialog.PinDialogFragment;
import com.android.tv.ui.sidepanel.ActionItem;
import com.android.tv.ui.sidepanel.Item;
import com.android.tv.ui.sidepanel.SideFragment;
import com.android.tv.ui.sidepanel.SubMenuItem;
import com.android.tv.ui.sidepanel.SwitchItem;

import java.util.ArrayList;
import java.util.List;

public class ParentalControlsFragment extends SideFragment {
    private static final String TRACKER_LABEL = "Parental controls";
    private List<ActionItem> mActionItems;

    private final SideFragmentListener mSideFragmentListener = new SideFragmentListener() {
        @Override
        public void onSideFragmentViewDestroyed() {
            notifyDataSetChanged();
        }
    };

    @Override
    protected String getTitle() {
        return getString(R.string.menu_parental_controls);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<Item> getItemList() {
        List<Item> items = new ArrayList<>();
        items.add(new SwitchItem(getString(R.string.option_toggle_parental_controls_on),
                getString(R.string.option_toggle_parental_controls_off)) {
            @Override
            protected void onUpdate() {
                super.onUpdate();
                setChecked(getMainActivity().getParentalControlSettings()
                        .isParentalControlsEnabled());
            }

            @Override
            protected void onSelected() {
                super.onSelected();
                boolean checked = isChecked();
                getMainActivity().getParentalControlSettings().setParentalControlsEnabled(checked);
                enableActionItems(checked);
            }
        });

        mActionItems = new ArrayList<>();
        mActionItems.add(new SubMenuItem(getString(R.string.option_channels_locked), "",
                getMainActivity().getOverlayManager().getSideFragmentManager()) {
            TextView mDescriptionView;

            @Override
            protected SideFragment getFragment() {
                SideFragment fragment = new ChannelsBlockedFragment();
                fragment.setListener(mSideFragmentListener);
                return fragment;
            }

            @Override
            protected void onBind(View view) {
                super.onBind(view);
                mDescriptionView = (TextView) view.findViewById(R.id.description);
            }

            @Override
            protected void onUpdate() {
                super.onUpdate();
                int lockedAndBrowsableChannelCount = 0;
                for (Channel channel : getChannelDataManager().getChannelList()) {
                    if (channel.isLocked() && channel.isBrowsable()) {
                        ++lockedAndBrowsableChannelCount;
                    }
                }
                if (lockedAndBrowsableChannelCount > 0) {
                    mDescriptionView.setText(Integer.toString(lockedAndBrowsableChannelCount));
                } else {
                    mDescriptionView.setText(
                            getMainActivity().getString(R.string.option_no_locked_channel));
                }
            }

            @Override
            protected void onUnbind() {
                super.onUnbind();
                mDescriptionView = null;
            }
        });
        mActionItems.add(new SubMenuItem(getString(R.string.option_program_restrictions),
                ProgramRestrictionsFragment.getDescription(getMainActivity()),
                getMainActivity().getOverlayManager().getSideFragmentManager()) {
            @Override
            protected SideFragment getFragment() {
                SideFragment fragment = new ProgramRestrictionsFragment();
                fragment.setListener(mSideFragmentListener);
                return fragment;
            }
        });
        mActionItems.add(new ActionItem(getString(R.string.option_change_pin)) {
            @Override
            protected void onSelected() {
                final MainActivity tvActivity = getMainActivity();
                tvActivity.getOverlayManager().getSideFragmentManager().hideSidePanel(true);
                PinDialogFragment fragment = PinDialogFragment.create(
                        PinDialogFragment.PIN_DIALOG_TYPE_NEW_PIN);
                tvActivity.getOverlayManager().showDialogFragment(PinDialogFragment.DIALOG_TAG,
                        fragment, true);
            }
        });
        items.addAll(mActionItems);
        enableActionItems(getMainActivity().getParentalControlSettings()
                .isParentalControlsEnabled());
        return items;
    }

    private void enableActionItems(boolean enabled) {
        for (ActionItem actionItem : mActionItems) {
            actionItem.setEnabled(enabled);
        }
    }
}
