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

package com.android.tv.menu;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.android.tv.R;
import com.android.tv.data.Channel;

/**
 * A class for the items in channels row.
 */
public class ChannelsRowItem {
    /** The item ID for guide item */
    public static final int GUIDE_ITEM_ID = -1;
    /** The item ID for setup item */
    public static final int SETUP_ITEM_ID = -2;
    /** The item ID for DVR item */
    public static final int DVR_ITEM_ID = -3;
    /** The item ID for app link item */
    public static final int APP_LINK_ITEM_ID = -4;

    /** The item which represents the guide. */
    public static final ChannelsRowItem GUIDE_ITEM =
            new ChannelsRowItem(GUIDE_ITEM_ID, R.layout.menu_card_guide);
    /** The item which represents the setup. */
    public static final ChannelsRowItem SETUP_ITEM =
            new ChannelsRowItem(SETUP_ITEM_ID, R.layout.menu_card_setup);
    /** The item which represents the DVR. */
    public static final ChannelsRowItem DVR_ITEM =
            new ChannelsRowItem(DVR_ITEM_ID, R.layout.menu_card_dvr);
    /** The item which represents the app link. */
    public static final ChannelsRowItem APP_LINK_ITEM =
            new ChannelsRowItem(APP_LINK_ITEM_ID, R.layout.menu_card_app_link);

    private final long mItemId;
    @NonNull private Channel mChannel;
    private final int mLayoutId;

    public ChannelsRowItem(@NonNull Channel channel, int layoutId) {
        this(channel.getId(), layoutId);
        mChannel = channel;
    }

    private ChannelsRowItem(long itemId, int layoutId) {
        mItemId = itemId;
        mLayoutId = layoutId;
    }

    /**
     * Returns the channel for this item.
     */
    @NonNull
    public Channel getChannel() {
        return mChannel;
    }

    /**
     * Sets the channel.
     */
    public void setChannel(@NonNull Channel channel) {
        mChannel = channel;
    }

    /**
     * Returns the layout resource ID to represent this item.
     */
    public int getLayoutId() {
        return mLayoutId;
    }

    /**
     * Returns the unique ID for this item.
     */
    public long getItemId() {
        return mItemId;
    }

    @Override
    public String toString() {
        return "ChannelsRowItem{"
                + "itemId=" + mItemId
                + ", layoutId=" + mLayoutId
                + ", channel=" + mChannel + "}";
    }
}
