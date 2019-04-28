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
package com.android.car.dialer;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.provider.CallLog;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.car.dialer.telecom.PhoneLoader;
import com.android.car.dialer.telecom.TelecomUtils;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.view.PagedListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Adapter class for populating Contact data as loaded from the DB to an AA GroupingRecyclerView.
 * It handles two types of contacts:
 * <p>
 * <ul>
 *     <li>Strequent contacts (starred and/or frequent)
 *     <li>Last call contact
 * </ul>
 */
public class StrequentsAdapter extends RecyclerView.Adapter<CallLogViewHolder>
        implements PagedListView.ItemCap {
    // The possible view types in this adapter.
    private static final int VIEW_TYPE_EMPTY = 0;
    private static final int VIEW_TYPE_LASTCALL = 1;
    private static final int VIEW_TYPE_STREQUENT = 2;

    private final Context mContext;
    private final UiCallManager mUiCallManager;
    private List<ContactEntry> mData;

    private LastCallData mLastCallData;

    private final ContentResolver mContentResolver;

    public interface StrequentsListener<T> {
        /** Notified when a row corresponding an individual Contact (not group) was clicked. */
        void onContactClicked(T viewHolder);
    }

    private View.OnFocusChangeListener mFocusChangeListener;
    private StrequentsListener<CallLogViewHolder> mStrequentsListener;

    private int mMaxItems = -1;
    private boolean mIsEmpty;

    public StrequentsAdapter(Context context, UiCallManager callManager) {
        mContext = context;
        mUiCallManager = callManager;
        mContentResolver = context.getContentResolver();
    }

    public void setStrequentsListener(@Nullable StrequentsListener<CallLogViewHolder> listener) {
        mStrequentsListener = listener;
    }

    public void setLastCallCursor(@Nullable Cursor cursor) {
        mLastCallData = convertLastCallCursor(cursor);
        notifyDataSetChanged();
    }

    public void setStrequentCursor(@Nullable Cursor cursor) {
        if (cursor != null) {
            setData(convertStrequentCursorToArray(cursor));
        } else {
            setData(null);
        }
        notifyDataSetChanged();
    }

    private void setData(List<ContactEntry> data) {
        mData = data;
        notifyDataSetChanged();
    }

    @Override
    public void setMaxItems(int maxItems) {
        mMaxItems = maxItems;
    }

    @Override
    public int getItemViewType(int position) {
        if (mIsEmpty) {
            return VIEW_TYPE_EMPTY;
        } else if (position == 0 && mLastCallData != null) {
            return VIEW_TYPE_LASTCALL;
        } else {
            return VIEW_TYPE_STREQUENT;
        }
    }

    @Override
    public int getItemCount() {
        int itemCount = mData == null ? 0 : mData.size();
        itemCount += mLastCallData == null ? 0 : 1;

        mIsEmpty = itemCount == 0;

        // If there is no data to display, add one to the item count to display the card in the
        // empty state.
        if (mIsEmpty) {
            itemCount++;
        }

        return mMaxItems >= 0 ? Math.min(mMaxItems, itemCount) : itemCount;
    }

    @Override
    public CallLogViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_TYPE_LASTCALL:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.call_log_last_call_item_card, parent, false);
                return new CallLogViewHolder(view);

            case VIEW_TYPE_EMPTY:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.car_list_item_empty, parent, false);
                return new CallLogViewHolder(view);

            case VIEW_TYPE_STREQUENT:
            default:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.call_log_list_item_card, parent, false);
                return new CallLogViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(final CallLogViewHolder viewHolder, int position) {
        switch (viewHolder.getItemViewType()) {
            case VIEW_TYPE_LASTCALL:
                onBindLastCallRow(viewHolder);
                break;

            case VIEW_TYPE_EMPTY:
                viewHolder.icon.setImageResource(R.drawable.ic_empty_speed_dial);
                viewHolder.title.setText(R.string.speed_dial_empty);
                viewHolder.title.setTextColor(mContext.getColor(R.color.car_body1_light));
                break;

            case VIEW_TYPE_STREQUENT:
            default:
                int positionIntoData = position;

                // If there is last call data, then decrement the position so there is not an out of
                // bounds error on the mData.
                if (mLastCallData != null) {
                    positionIntoData--;
                }

                onBindView(viewHolder, mData.get(positionIntoData));
                viewHolder.callType.setVisibility(View.VISIBLE);
        }
    }

    private void onViewClicked(CallLogViewHolder viewHolder) {
        if (mStrequentsListener != null) {
            mStrequentsListener.onContactClicked(viewHolder);
        }
    }

    @Override
    public void onViewAttachedToWindow(CallLogViewHolder holder) {
        if (mFocusChangeListener != null) {
            holder.itemView.setOnFocusChangeListener(mFocusChangeListener);
        }
    }

    @Override
    public void onViewDetachedFromWindow(CallLogViewHolder holder) {
        holder.itemView.setOnFocusChangeListener(null);
    }

    /**
     * Converts the strequents data in the given cursor into a list of {@link ContactEntry}s.
     */
    private List<ContactEntry> convertStrequentCursorToArray(Cursor cursor) {
        List<ContactEntry> strequentContactEntries = new ArrayList<>();
        HashMap<Integer, ContactEntry> entryMap = new HashMap<>();
        cursor.moveToPosition(-1);

        while (cursor.moveToNext()) {
            final ContactEntry entry = ContactEntry.fromCursor(cursor, mContext);
            entryMap.put(entry.hashCode(), entry);
        }

        strequentContactEntries.addAll(entryMap.values());
        Collections.sort(strequentContactEntries);
        return strequentContactEntries;
    }

    /**
     * Binds the views in the entry to the data of last call.
     *
     * @param viewHolder the view holder corresponding to this entry
     */
    private void onBindLastCallRow(final CallLogViewHolder viewHolder) {
        if (mLastCallData == null) {
            return;
        }

        viewHolder.itemView.setOnClickListener(v -> onViewClicked(viewHolder));

        String primaryText = mLastCallData.getPrimaryText();
        String number = mLastCallData.getNumber();

        viewHolder.title.setText(mLastCallData.getPrimaryText());
        viewHolder.text.setText(mLastCallData.getSecondaryText());
        viewHolder.itemView.setTag(number);
        viewHolder.callTypeIconsView.clear();
        viewHolder.callTypeIconsView.setVisibility(View.VISIBLE);

        // mHasFirstItem is true only in main screen, or else it is in drawer, then we need to add
        // call type icons for call history items.
        viewHolder.smallIcon.setVisibility(View.GONE);
        int[] callTypes = mLastCallData.getCallTypes();
        int icons = Math.min(callTypes.length, CallTypeIconsView.MAX_CALL_TYPE_ICONS);
        for (int i = 0; i < icons; i++) {
            viewHolder.callTypeIconsView.add(callTypes[i]);
        }

        setBackground(viewHolder);

        TelecomUtils.setContactBitmapAsync(mContext, viewHolder.icon, primaryText, number);
    }

    /**
     * Converts the last call information in the given cursor into a {@link LastCallData} object
     * so that the cursor can be closed.
     *
     * @return A valid {@link LastCallData} or {@code null} if the cursor is {@code null} or has no
     * data in it.
     */
    @Nullable
    public LastCallData convertLastCallCursor(@Nullable Cursor cursor) {
        if (cursor == null || cursor.getCount() == 0) {
            return null;
        }

        cursor.moveToFirst();

        final StringBuilder nameSb = new StringBuilder();
        int column = PhoneLoader.getNameColumnIndex(cursor);
        String cachedName = cursor.getString(column);
        final String number = PhoneLoader.getPhoneNumber(cursor, mContentResolver);
        if (cachedName == null) {
            cachedName = TelecomUtils.getDisplayName(mContext, number);
        }

        boolean isVoicemail = false;
        if (cachedName == null) {
            if (number.equals(TelecomUtils.getVoicemailNumber(mContext))) {
                isVoicemail = true;
                nameSb.append(mContext.getString(R.string.voicemail));
            } else {
                String displayName = TelecomUtils.getFormattedNumber(mContext, number);
                if (TextUtils.isEmpty(displayName)) {
                    displayName = mContext.getString(R.string.unknown);
                }
                nameSb.append(displayName);
            }
        } else {
            nameSb.append(cachedName);
        }
        column = cursor.getColumnIndex(CallLog.Calls.DATE);
        // If we set this to 0, getRelativeTime will return null and no relative time
        // will be displayed.
        long millis = column == -1 ? 0 : cursor.getLong(column);
        StringBuilder secondaryText = new StringBuilder();
        CharSequence relativeDate = getRelativeTime(millis);
        if (!isVoicemail) {
            CharSequence type = TelecomUtils.getTypeFromNumber(mContext, number);
            secondaryText.append(type);
            if (!TextUtils.isEmpty(type) && !TextUtils.isEmpty(relativeDate)) {
                secondaryText.append(", ");
            }
        }
        if (relativeDate != null) {
            secondaryText.append(relativeDate);
        }

        int[] callTypes = mUiCallManager.getCallTypes(cursor, 1);

        return new LastCallData(number, nameSb.toString(), secondaryText.toString(), callTypes);
    }

    /**
     * Bind view function for frequent call row.
     */
    private void onBindView(final CallLogViewHolder viewHolder, final ContactEntry entry) {
        viewHolder.itemView.setOnClickListener(v -> onViewClicked(viewHolder));

        final String number = entry.number;
        // TODO(mcrico): Why is being a voicemail related to not having a name?
        boolean isVoicemail = (entry.name == null)
                && (number.equals(TelecomUtils.getVoicemailNumber(mContext)));
        String secondaryText = "";
        if (!isVoicemail) {
            secondaryText = String.valueOf(TelecomUtils.getTypeFromNumber(mContext, number));
        }

        viewHolder.text.setText(secondaryText);
        viewHolder.itemView.setTag(number);
        viewHolder.callTypeIconsView.clear();

        String displayName = entry.getDisplayName();
        viewHolder.title.setText(displayName);

        TelecomUtils.setContactBitmapAsync(mContext, viewHolder.icon, displayName, number);

        if (entry.isStarred) {
            viewHolder.smallIcon.setVisibility(View.VISIBLE);
            final int iconColor = mContext.getColor(android.R.color.white);
            viewHolder.smallIcon.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
            viewHolder.smallIcon.setImageResource(R.drawable.ic_favorite);
        } else {
            viewHolder.smallIcon.setVisibility(View.GONE);
        }

        setBackground(viewHolder);
    }

    /**
     * Appropriately sets the background for the View that is being bound. This method will allow
     * for rounded corners on either the top or bottom of a card.
     */
    private void setBackground(CallLogViewHolder viewHolder) {
        int itemCount = getItemCount();
        int adapterPosition = viewHolder.getAdapterPosition();

        if (itemCount == 1) {
            // Only element - all corners are rounded
            viewHolder.card.setBackgroundResource(
                    R.drawable.car_card_rounded_top_bottom_background);
        } else if (adapterPosition == 0) {
            // First element gets rounded top
            viewHolder.card.setBackgroundResource(R.drawable.car_card_rounded_top_background);
        } else if (adapterPosition == itemCount - 1) {
            // Last one has a rounded bottom
            viewHolder.card.setBackgroundResource(R.drawable.car_card_rounded_bottom_background);
        } else {
            // Middle have no rounded corners
            viewHolder.card.setBackgroundResource(R.color.car_card);
        }
    }

    /**
     * Build any timestamp and label into a single string. If the given timestamp is invalid, then
     * {@code null} is returned.
     */
    @Nullable
    private static CharSequence getRelativeTime(long millis) {
        if (millis <= 0) {
            return null;
        }

        return DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
    }

    /**
     * A container for data relating to a last call entry.
     */
    private class LastCallData {
        private final String mNumber;
        private final String mPrimaryText;
        private final String mSecondaryText;
        private final int[] mCallTypes;

        LastCallData(String number, String primaryText, String secondaryText,
                int[] callTypes) {
            mNumber = number;
            mPrimaryText = primaryText;
            mSecondaryText = secondaryText;
            mCallTypes = callTypes;
        }

        public String getNumber() {
            return mNumber;
        }

        public String getPrimaryText() {
            return mPrimaryText;
        }

        public String getSecondaryText() {
            return mSecondaryText;
        }

        public int[] getCallTypes() {
            return mCallTypes;
        }
    }
}
