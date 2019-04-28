/**
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

import com.android.car.stream.IStreamConsumer;
import com.android.car.stream.StreamCard;

/**
 * An API for clients to communicate with StreamService.
 */
interface IStreamService {
    /**
     * Registers the stream consumer to listen for changes to cards within the StreamService.
     */
    void registerConsumer(in IStreamConsumer consumer) = 0;

    /**
     * Removes the given stream consumer from the list of listeners within the StreamService.
     */
    void unregisterConsumer(in IStreamConsumer consumer) = 1;

    /**
     * Returns all {@link StreamCard}s that the StreamService currently has.
     */
    List<StreamCard> fetchAllStreamCards() = 2;

    /**
     * Notifies the stream service that a card was dismissed.
     */
    void notifyStreamCardDismissed(in StreamCard card) = 3;

    /**
     * Notifies the stream service that a card interation has occured.
     */
    void notifyStreamCardInteracted(in StreamCard card) = 4;
}
