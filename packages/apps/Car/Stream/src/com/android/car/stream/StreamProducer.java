/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.stream;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.CallSuper;
import android.util.Log;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/**
 * A base class that produces {@link StreamCard} for the StreamService
 */
public abstract class StreamProducer {
    private static final String TAG = "StreamProducer";

    protected final Context mContext;

    /**
     * A queue that holds {@link StreamCard}s that were added before this {@link StreamProducer}
     * has connected to the {@link StreamService}. After connecting, these cards are posted to
     * the StreamService.
     */
    private final Queue<StreamCard> mQueuedCards = new LinkedList<>();

    private StreamService mStreamService;

    public StreamProducer(Context context) {
        mContext = context;
    }

    /**
     * Posts the given card to the {@link StreamService} for rendering by stream consumers.
     *
     * @return {@code true} if the card was successfully posted. {@code false} is returned if the
     * {@link StreamService} is not available. The given card will be queued and posted when the
     * {@link StreamService} becomes available.
     */
    public final boolean postCard(StreamCard card) {
        if (mStreamService != null) {
            mStreamService.addStreamCard(card);
            return true;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "StreamService not found, adding card to queue for later addition.");
        }

        mQueuedCards.add(card);
        return false;
    }

    /**
     * Removes the given card from the {@link StreamService}. If this {@link StreamProducer} has not
     * connected to the {@link StreamService}, then {@link #mQueuedCards} is checked to see if it
     * contains the given card.
     *
     * @return {@code true} if the card is successfully removed from either the
     * {@link StreamService} or {@link #mQueuedCards}.
     */
    public final boolean removeCard(StreamCard card) {
        if (card == null) {
            return false;
        }

        if (mStreamService != null) {
            mStreamService.removeStreamCard(card);
            return true;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "StreamService not found, checking if it exists in the queue.");
        }

        for (Iterator<StreamCard> iterator = mQueuedCards.iterator(); iterator.hasNext();) {
            StreamCard queuedCard = iterator.next();
            if (queuedCard.getType() == card.getType() && queuedCard.getId() == card.getId()) {
                iterator.remove();
                return true;
            }
        }

        return false;
    }

    public void onCardDismissed(StreamCard card) {
        // Handle when a StreamCard is dismissed.
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Stream Card dismissed: " + card);
        }
    }

    /**
     * Start the producer and connect to the {@link StreamService}
     */
    @CallSuper
    public void start() {
        Intent streamServiceIntent = new Intent(mContext, StreamService.class);
        streamServiceIntent.setAction(StreamConstants.STREAM_PRODUCER_BIND_ACTION);
        mContext.bindService(streamServiceIntent, mServiceConnection, 0 /* flags */);
    }

    /**
     * Stop the producer.
     */
    @CallSuper
    public void stop() {
        mContext.unbindService(mServiceConnection);
        mQueuedCards.clear();
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            StreamService.StreamProducerBinder binder
                    = (StreamService.StreamProducerBinder) service;
            mStreamService = binder.getService();

            while (!mQueuedCards.isEmpty()) {
                mStreamService.addStreamCard(mQueuedCards.remove());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mStreamService = null;
        }
    };
}
