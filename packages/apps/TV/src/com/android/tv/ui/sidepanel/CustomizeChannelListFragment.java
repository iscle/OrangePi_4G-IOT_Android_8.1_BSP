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

import android.content.Context;
import android.content.SharedPreferences;
import android.media.tv.TvContract.Channels;
import android.os.Bundle;
import android.support.v17.leanback.widget.VerticalGridView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.common.SharedPreferencesUtils;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelNumber;
import com.android.tv.ui.OnRepeatedKeyInterceptListener;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class CustomizeChannelListFragment extends SideFragment {
    private static final int GROUP_BY_SOURCE = 0;
    private static final int GROUP_BY_HD_SD = 1;
    private static final String TRACKER_LABEL = "customize channel list";

    private static final String PREF_KEY_GROUP_SETTINGS = "pref_key_group_settigns";

    private final List<Channel> mChannels = new ArrayList<>();
    private long mInitialChannelId = Channel.INVALID_ID;
    private long mLastFocusedChannelId = Channel.INVALID_ID;

    private static Integer sGroupingType;
    private TvInputManagerHelper mInputManager;
    private Channel.DefaultComparator mChannelComparator;
    private boolean mGroupByFragmentRunning;

    private final List<Item> mItems = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInputManager = getMainActivity().getTvInputManagerHelper();
        mInitialChannelId = getMainActivity().getCurrentChannelId();
        mChannelComparator = new Channel.DefaultComparator(getActivity(), mInputManager);
        if (sGroupingType == null) {
            SharedPreferences sharedPreferences = getContext().getSharedPreferences(
                    SharedPreferencesUtils.SHARED_PREF_UI_SETTINGS, Context.MODE_PRIVATE);
            sGroupingType = sharedPreferences.getInt(PREF_KEY_GROUP_SETTINGS, GROUP_BY_SOURCE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
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

        if (!mGroupByFragmentRunning) {
            getMainActivity().startShrunkenTvView(false, true);

            int initialChannelPosition = INVALID_POSITION;
            int i = 0;
            for (Item item : mItems) {
                if (item instanceof ChannelItem
                        && ((ChannelItem) item).getChannel().getId() == mInitialChannelId) {
                    initialChannelPosition = i;
                    break;
                }
                ++i;
            }
            if (initialChannelPosition != INVALID_POSITION) {
                setSelectedPosition(initialChannelPosition);
            } else {
                setSelectedPosition(0);
            }
            mLastFocusedChannelId = mInitialChannelId;
            MainActivity tvActivity = getMainActivity();
            if (mLastFocusedChannelId != Channel.INVALID_ID &&
                    mLastFocusedChannelId != tvActivity.getCurrentChannelId()) {
                tvActivity.tuneToChannel(getChannelDataManager().getChannel(mLastFocusedChannelId));
            }
        }
        mGroupByFragmentRunning = false;
        return view;
    }

    @Override
    public void onDestroyView() {
        getChannelDataManager().applyUpdatedValuesToDb();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getMainActivity().endShrunkenTvView();
    }

    @Override
    protected String getTitle() {
        return getString(R.string.side_panel_title_edit_channels_for_an_input);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<Item> getItemList() {
        mItems.clear();
        mChannels.clear();
        mChannels.addAll(getChannelDataManager().getChannelList());
        if (sGroupingType == GROUP_BY_SOURCE) {
            addItemForGroupBySource(mItems);
        } else {
            // GROUP_BY_HD_SD
            addItemForGroupByHdSd(mItems);
        }
        return mItems;
    }

    private void cleanUpOneChannelGroupItem(List<Item> items) {
        Iterator<Item> iter = items.iterator();
        while (iter.hasNext()) {
            Item item = iter.next();
            if (item instanceof SelectGroupItem) {
                SelectGroupItem selectGroupItem = (SelectGroupItem) item;
                if (selectGroupItem.mChannelItemsInGroup.size() == 1) {
                    selectGroupItem.mChannelItemsInGroup.get(0).mSelectGroupItem = null;
                    iter.remove();
                }
            }
        }
    }

    private void addItemForGroupBySource(List<Item> items) {
        items.add(new GroupBySubMenu(getString(R.string.edit_channels_group_by_sources)));
        SelectGroupItem selectGroupItem = null;
        ArrayList<Channel> channels = new ArrayList<>(mChannels);
        Collections.sort(channels, mChannelComparator);

        String inputId = null;
        for (Channel channel: channels) {
            if (!channel.getInputId().equals(inputId)) {
                inputId = channel.getInputId();
                String inputLabel = Utils.loadLabel(getActivity(),
                        mInputManager.getTvInputInfo(inputId));
                items.add(new DividerItem(inputLabel));
                selectGroupItem = new SelectGroupItem();
                items.add(selectGroupItem);
            }
            ChannelItem channelItem = new ChannelItem(channel, selectGroupItem);
            items.add(channelItem);
            selectGroupItem.addChannelItem(channelItem);
        }
        cleanUpOneChannelGroupItem(items);
    }

    private void addItemForGroupByHdSd(List<Item> items) {
        items.add(new GroupBySubMenu(getString(R.string.edit_channels_group_by_hd_sd)));
        SelectGroupItem selectGroupItem = null;
        ArrayList<Channel> channels = new ArrayList<>(mChannels);
        Collections.sort(channels, new Comparator<Channel>() {
            @Override
            public int compare(Channel lhs, Channel rhs) {
                boolean lhsHd = isHdChannel(lhs);
                boolean rhsHd = isHdChannel(rhs);
                if (lhsHd == rhsHd) {
                    return ChannelNumber.compare(lhs.getDisplayNumber(), rhs.getDisplayNumber());
                } else {
                    return lhsHd ? -1 : 1;
                }
            }
        });

        Boolean isHdGroup = null;
        for (Channel channel: channels) {
            boolean isHd = isHdChannel(channel);
            if (isHdGroup == null || isHd != isHdGroup) {
                isHdGroup = isHd;
                items.add(new DividerItem(isHd
                        ? getString(R.string.edit_channels_group_divider_for_hd)
                        : getString(R.string.edit_channels_group_divider_for_sd)));
                selectGroupItem = new SelectGroupItem();
                items.add(selectGroupItem);
            }
            ChannelItem channelItem = new ChannelItem(channel, selectGroupItem);
            items.add(channelItem);
            selectGroupItem.addChannelItem(channelItem);
        }
        cleanUpOneChannelGroupItem(items);
    }

    private static boolean isHdChannel(Channel channel) {
        String videoFormat = channel.getVideoFormat();
        return videoFormat != null &&
                (Channels.VIDEO_FORMAT_720P.equals(videoFormat)
                        || Channels.VIDEO_FORMAT_1080I.equals(videoFormat)
                        || Channels.VIDEO_FORMAT_1080P.equals(videoFormat)
                        || Channels.VIDEO_FORMAT_2160P.equals(videoFormat)
                        || Channels.VIDEO_FORMAT_4320P.equals(videoFormat));
    }

    private class SelectGroupItem extends ActionItem {
        private final List<ChannelItem> mChannelItemsInGroup = new ArrayList<>();
        private TextView mTextView;
        private boolean mAllChecked;

        public SelectGroupItem() {
            super(null);
        }

        private void addChannelItem(ChannelItem channelItem) {
            mChannelItemsInGroup.add(channelItem);
        }

        @Override
        protected void onBind(View view) {
            super.onBind(view);
            mTextView = (TextView) view.findViewById(R.id.title);
        }

        @Override
        protected void onUpdate() {
            super.onUpdate();
            mAllChecked = true;
            for (ChannelItem channelItem : mChannelItemsInGroup) {
                if (!channelItem.getChannel().isBrowsable()) {
                    mAllChecked = false;
                    break;
                }
            }
            mTextView.setText(getString(mAllChecked
                    ? R.string.edit_channels_item_deselect_group
                    : R.string.edit_channels_item_select_group));
        }

        @Override
        protected void onSelected() {
            for (ChannelItem channelItem : mChannelItemsInGroup) {
                Channel channel = channelItem.getChannel();
                if (channel.isBrowsable() == mAllChecked) {
                    getChannelDataManager().updateBrowsable(channel.getId(), !mAllChecked, true);
                    channelItem.notifyUpdated();
                }
            }
            getChannelDataManager().notifyChannelBrowsableChanged();
            mAllChecked = !mAllChecked;
            mTextView.setText(getString(mAllChecked
                    ? R.string.edit_channels_item_deselect_group
                    : R.string.edit_channels_item_select_group));
        }
    }

    private class ChannelItem extends ChannelCheckItem {
        private SelectGroupItem mSelectGroupItem;

        public ChannelItem(Channel channel, SelectGroupItem selectGroupItem) {
            super(channel, getChannelDataManager(), getProgramDataManager());
            mSelectGroupItem = selectGroupItem;
        }

        @Override
        protected void onUpdate() {
            super.onUpdate();
            setChecked(getChannel().isBrowsable());
        }

        @Override
        protected void onSelected() {
            super.onSelected();
            getChannelDataManager().updateBrowsable(getChannel().getId(), isChecked());
            if (mSelectGroupItem != null) {
                mSelectGroupItem.notifyUpdated();
            }
        }

        @Override
        protected void onFocused() {
            super.onFocused();
            mLastFocusedChannelId = getChannel().getId();
        }
    }

    public static class GroupByFragment extends SideFragment {
        @Override
        protected String getTitle() {
            return getString(R.string.side_panel_title_group_by);
        }
        @Override
        public String getTrackerLabel() {
            return GroupBySubMenu.TRACKER_LABEL;
        }

        @Override
        protected List<Item> getItemList() {
            List<Item> items = new ArrayList<>();
            items.add(new RadioButtonItem(
                    getString(R.string.edit_channels_group_by_sources)) {
                @Override
                protected void onSelected() {
                    super.onSelected();
                    setGroupingType(GROUP_BY_SOURCE);
                    closeFragment();
                }
            });
            items.add(new RadioButtonItem(
                    getString(R.string.edit_channels_group_by_hd_sd)) {
                @Override
                protected void onSelected() {
                    super.onSelected();
                    setGroupingType(GROUP_BY_HD_SD);
                    closeFragment();
                }
            });
            ((RadioButtonItem) items.get(sGroupingType)).setChecked(true);
            return items;
        }

        private void setGroupingType(int groupingType) {
            sGroupingType = groupingType;
            SharedPreferences sharedPreferences = getContext().getSharedPreferences(
                    SharedPreferencesUtils.SHARED_PREF_UI_SETTINGS, Context.MODE_PRIVATE);
            sharedPreferences.edit().putInt(PREF_KEY_GROUP_SETTINGS, groupingType).apply();
        }
    }

    private class GroupBySubMenu extends SubMenuItem {
        private static final String TRACKER_LABEL = "Group by";
        public GroupBySubMenu(String description) {
            super(getString(R.string.edit_channels_item_group_by), description,
                    getMainActivity().getOverlayManager().getSideFragmentManager());
        }

        @Override
        protected SideFragment getFragment() {
            return new GroupByFragment();
        }

        @Override
        protected void onSelected() {
            mGroupByFragmentRunning = true;
            super.onSelected();
        }
    }
}
