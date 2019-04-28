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

import android.database.ContentObserver;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.widget.VerticalGridView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelNumber;
import com.android.tv.ui.OnRepeatedKeyInterceptListener;
import com.android.tv.ui.sidepanel.ActionItem;
import com.android.tv.ui.sidepanel.ChannelCheckItem;
import com.android.tv.ui.sidepanel.DividerItem;
import com.android.tv.ui.sidepanel.Item;
import com.android.tv.ui.sidepanel.SideFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChannelsBlockedFragment extends SideFragment {
    private static final String TRACKER_LABEL = "Channels blocked";
    private int mBlockedChannelCount;
    private final List<Channel> mChannels = new ArrayList<>();
    private long mLastFocusedChannelId = Channel.INVALID_ID;
    private int mSelectedPosition = INVALID_POSITION;
    private final ContentObserver mProgramUpdateObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            notifyItemsChanged();
        }
    };
    private final Item mLockAllItem = new BlockAllItem();
    private final List<Item> mItems = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (mSelectedPosition != INVALID_POSITION) {
            setSelectedPosition(mSelectedPosition);
        }
        VerticalGridView listView = (VerticalGridView) view.findViewById(R.id.side_panel_list);
        listView.setOnKeyInterceptListener(new OnRepeatedKeyInterceptListener(listView) {
            @Override
            public boolean onInterceptKeyEvent(KeyEvent event) {
                // In order to send tune operation once for continuous channel up/down events,
                // we only call the moveToChannel method on ACTION_UP event of channel switch keys.
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    switch (event.getKeyCode()) {
                        case KeyEvent.KEYCODE_DPAD_UP:
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            if (mLastFocusedChannelId != Channel.INVALID_ID) {
                                getMainActivity().tuneToChannel(
                                        getChannelDataManager().getChannel(mLastFocusedChannelId));
                            }
                            break;
                    }
                }
                return super.onInterceptKeyEvent(event);
            }
        });
        getActivity().getContentResolver().registerContentObserver(TvContract.Programs.CONTENT_URI,
                true, mProgramUpdateObserver);
        getMainActivity().startShrunkenTvView(true, true);
        return view;
    }

    @Override
    public void onDestroyView() {
        getActivity().getContentResolver().unregisterContentObserver(mProgramUpdateObserver);
        getChannelDataManager().applyUpdatedValuesToDb();
        getMainActivity().endShrunkenTvView();
        super.onDestroyView();
    }

    @Override
    protected String getTitle() {
        return getString(R.string.option_channels_locked);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<Item> getItemList() {
        mItems.clear();
        mItems.add(mLockAllItem);
        mChannels.clear();
        mChannels.addAll(getChannelDataManager().getChannelList());
        Collections.sort(mChannels, new Comparator<Channel>() {
            @Override
            public int compare(Channel lhs, Channel rhs) {
                if (lhs.isBrowsable() != rhs.isBrowsable()) {
                    return lhs.isBrowsable() ? -1 : 1;
                }
                return ChannelNumber.compare(lhs.getDisplayNumber(), rhs.getDisplayNumber());
            }
        });

        final long currentChannelId = getMainActivity().getCurrentChannelId();
        boolean hasHiddenChannels = false;
        for (Channel channel : mChannels) {
            if (!channel.isBrowsable() && !hasHiddenChannels) {
                mItems.add(new DividerItem(
                        getString(R.string.option_channels_subheader_hidden)));
                hasHiddenChannels = true;
            }
            mItems.add(new ChannelBlockedItem(channel));
            if (channel.isLocked()) {
                ++mBlockedChannelCount;
            }
            if (channel.getId() == currentChannelId) {
                mSelectedPosition = mItems.size() - 1;
            }
        }
        return mItems;
    }

    private class BlockAllItem extends ActionItem {
        private TextView mTextView;

        public BlockAllItem() {
            super(null);
        }

        @Override
        protected void onBind(View view) {
            super.onBind(view);
            mTextView = (TextView) view.findViewById(R.id.title);
        }

        @Override
        protected void onUpdate() {
            super.onUpdate();
            updateText();
        }

        @Override
        protected void onUnbind() {
            super.onUnbind();
            mTextView = null;
        }

        @Override
        protected void onSelected() {
            boolean lock = !areAllChannelsBlocked();
            for (Channel channel : mChannels) {
                getChannelDataManager().updateLocked(channel.getId(), lock);
            }
            mBlockedChannelCount = lock ? mChannels.size() : 0;
            notifyItemsChanged();
        }

        @Override
        protected void onFocused() {
            super.onFocused();
            mLastFocusedChannelId = Channel.INVALID_ID;
        }

        private void updateText() {
            mTextView.setText(getString(areAllChannelsBlocked() ?
                    R.string.option_channels_unlock_all : R.string.option_channels_lock_all));
        }

        private boolean areAllChannelsBlocked() {
            return mBlockedChannelCount == mChannels.size();
        }
    }

    private class ChannelBlockedItem extends ChannelCheckItem {
        private ChannelBlockedItem(Channel channel) {
            super(channel, getChannelDataManager(), getProgramDataManager());
        }

        @Override
        protected int getResourceId() {
            return R.layout.option_item_channel_lock;
        }

        @Override
        protected void onUpdate() {
            super.onUpdate();
            setChecked(getChannel().isLocked());
        }

        @Override
        protected void onSelected() {
            super.onSelected();
            getChannelDataManager().updateLocked(getChannel().getId(), isChecked());
            mBlockedChannelCount += isChecked() ? 1 : -1;
            notifyItemChanged(mLockAllItem);
        }

        @Override
        protected void onFocused() {
            super.onFocused();
            mLastFocusedChannelId = getChannel().getId();
        }
    }
}
