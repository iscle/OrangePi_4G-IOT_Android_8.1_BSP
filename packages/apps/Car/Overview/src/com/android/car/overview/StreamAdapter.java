/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.overview;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.car.stream.AbstractBundleable;
import com.android.car.stream.MediaPlaybackExtension;
import com.android.car.stream.StreamCard;
import com.android.car.stream.StreamConstants;
import com.android.car.view.PagedListView;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * A {@link RecyclerView.Adapter} that binds {@link StreamCard} to their respective views.
 */
public class StreamAdapter extends RecyclerView.Adapter<StreamViewHolder>
        implements PagedListView.ItemCap {

    private static final int BASIC_CARD_LAYOUT_TYPE = 0;
    private static final int CURRENT_CALL_CARD_LAYOUT_TYPE = 1;
    private static final int MEDIA_CARD_LAYOUT_TYPE = 2;

    private static final String TAG = "StreamAdapter";

    private static final int MAX_NUMBER_ITEMS = 25;
    private int mMaxItems = MAX_NUMBER_ITEMS;

    private final ArrayList<StreamCard> mStreamCards = new ArrayList<>(mMaxItems);
    private final Context mContext;

    public StreamAdapter(Context context) {
        mContext = context;
    }

    @Override
    public StreamViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext)
                .inflate(R.layout.stream_card_container, parent, false);
        CardView container = (CardView) view.findViewById(R.id.stream_item_container);
        switch (viewType) {
            case CURRENT_CALL_CARD_LAYOUT_TYPE:
                container.addView(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.stream_card_current_call, container, false));
                return new CurrentCallStreamViewHolder(mContext, view);
            case MEDIA_CARD_LAYOUT_TYPE:
                container.addView(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.stream_card_media, container, false));
                return new MediaStreamViewHolder(mContext, view);
            default:
                // For all cards that do not have their own view holder/layout, use the basic stream
                // card layout.
                View contentView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.stream_card_simple, container, false);
                container.addView(contentView);

                return new SimpleStreamViewHolder(mContext, view);
        }
    }

    @Override
    public void onBindViewHolder(StreamViewHolder holder, int position) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Stream Card being bound: " + mStreamCards.get(position));
        }
        holder.bindStreamCard(mStreamCards.get(position));
    }

    @Override
    public int getItemViewType(int position) {
        StreamCard card = mStreamCards.get(position);

        // If the card has no extensions, then render as a basic card.
        if (card.getCardExtension() == null) {
            return BASIC_CARD_LAYOUT_TYPE;
        }

        switch (card.getType()) {
            case StreamConstants.CARD_TYPE_CURRENT_CALL:
                return CURRENT_CALL_CARD_LAYOUT_TYPE;
            case StreamConstants.CARD_TYPE_MEDIA:
                return MEDIA_CARD_LAYOUT_TYPE;
            default:
                return BASIC_CARD_LAYOUT_TYPE;
        }
    }

    @Override
    public int getItemCount() {
        return mStreamCards.size();
    }

    @Override
    public void setMaxItems(int max) {
        if (max < 1) {
            return;
        }
        mMaxItems = Math.min(max, MAX_NUMBER_ITEMS);
    }

    /**
     * Remove all {@link StreamCard} in the adapter.
     */
    public void removeAllCards() {
        mStreamCards.clear();
        notifyDataSetChanged();
    }

    public void addCard(StreamCard card) {
        // There should only be one card in the stream that is of type MEDIA. As a result, handle
        // this case specially. Otherwise, check if the card matches a stream card that already
        // exists and replace it.
        if (card.getType() == StreamConstants.CARD_TYPE_MEDIA && !canAddMediaCard(card)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Card: " + card + " does not have focus, so will not be added.");
            }
            return;
        } else if (maybeReplaceCard(card)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Card: " + card + " was replaced in stream");
            }
            return;
        }

        if (mStreamCards.size() >= mMaxItems) {
            StreamCard removedCard = mStreamCards.remove(mStreamCards.size() - 1);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Card: " + removedCard + " was pushed out the stream");
            }
        }

        int size = mStreamCards.size();
        for (int i = 0; i < size; i++) {
            if (mStreamCards.get(i).getPriority() <= card.getPriority()) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Card: " + card + " was inserted at i: " + i);
                }
                mStreamCards.add(i, card);
                notifyDataSetChanged();
                return;
            }
        }

        // The card had lower priority than all existing cards, add to the end.
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Card: " + card + " was inserted at the end");
        }
        mStreamCards.add(card);
        notifyDataSetChanged();
    }

    public void removeCard(StreamCard card) {
        for (Iterator<StreamCard> iterator = mStreamCards.iterator(); iterator.hasNext();) {
            StreamCard existingCard = iterator.next();
            if (existingCard.getType() == card.getType() && existingCard.getId() == card.getId()) {
                iterator.remove();
                notifyDataSetChanged();
                break;
            }
        }
    }

    /**
     * Replaces a card in the adapter if the new card has the same priority. Otherwise it removes
     * the card from the adapter.
     */
    private boolean maybeReplaceCard(StreamCard newCard) {
        for (int i = 0, size = mStreamCards.size(); i < size; i++) {
            StreamCard existingCard = mStreamCards.get(i);

            if (existingCard.getType() == newCard.getType()
                    && existingCard.getId() == newCard.getId()) {
                mStreamCards.set(i, newCard);
                if (existingCard.getPriority() == newCard.getPriority()) {
                    mStreamCards.set(i, newCard);
                    notifyDataSetChanged();
                    return true;
                } else {
                    // If the priority is no longer the same, just remove the card
                    // and let it be added again.
                    mStreamCards.remove(i);
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Searches through {@link #mStreamCards} and returns the first card in the list that has a
     * card type of {@link StreamConstants#CARD_TYPE_MEDIA}. If none is found, then {@code null}
     * is returned.
     */
    @Nullable
    private StreamCard getExistingMediaCard() {
        for (StreamCard streamCard : mStreamCards) {
            if (streamCard.getType() == StreamConstants.CARD_TYPE_MEDIA) {
                return streamCard;
            }
        }

        return null;
    }

    /**
     * Returns {@code true} if the given {@link StreamCard} of type
     * {@link StreamConstants#CARD_TYPE_MEDIA} can be added to the set of stream cards. This method
     * is responsible for ensuring that there is only a single instance of a media card at all
     * times.
     */
    private boolean canAddMediaCard(StreamCard card) {
        StreamCard existingMediaCard = getExistingMediaCard();

        // If there is no other media StreamCard, then it is ok to add.
        if (existingMediaCard == null) {
            return true;
        }

        // If this update is coming from the same application, then add the StreamCard.
        if (existingMediaCard.getId() == card.getId()) {
            return true;
        }

        AbstractBundleable cardExtension = card.getCardExtension();

        // Cannot infer play state from the card to be added, so just add it.
        if (!(cardExtension instanceof MediaPlaybackExtension)) {
            return true;
        }

        // Otherwise, ensure only the application that currently has focus has the ability to show
        // their card. When a card is currently playing, it implies that it has focus.
        boolean hasFocus = ((MediaPlaybackExtension) cardExtension).isPlaying();

        // Since this new card has focus, remove the existing card from the list.
        if (hasFocus) {
            mStreamCards.remove(existingMediaCard);
        }

        return hasFocus;
    }
}
