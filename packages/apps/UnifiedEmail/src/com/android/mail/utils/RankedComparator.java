/**
 * Copyright (C) 2014 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.utils;

import com.google.android.mail.common.base.Function;
import com.google.common.collect.ImmutableMap;

import java.util.Comparator;
import java.util.Map;

/**
 * Compares objects of the type {@code T} based on predefined order of the ranks of the type
 * {@code K}. The ranks that are not in the predefined order will be considered larger than other
 * ranks (if this comparator is used for sorting those elements will be at the end).
 *
 * @param <T> Type to be compared.
 * @param <K> Type of the ranks used for comparison.
 */
public class RankedComparator<T, K> implements Comparator<T> {

    private final Map<K, Integer> mRankOrder;

    private final Function<T, K> mRankExtractorFunction;

    /**
     * Creates a comparator that compares objects of type {@code T} by extracting the ranks of the
     * objects using {@code rankExtractorFunction} and comparing them by their position in
     * {@code rankOrder}. Ranks not present in {@code rankOrder} are considered larger than the
     * ranks present in the {@code rankOrder}.
     *
     * @param rankOrder             Order of the ranks.
     * @param rankExtractorFunction Function that extracts rank from the object.
     */
    public RankedComparator(K[] rankOrder, Function<T, K> rankExtractorFunction) {
        final ImmutableMap.Builder<K, Integer> orderBuilder = ImmutableMap.builder();
        for (int i = 0; i < rankOrder.length; i++) {
            orderBuilder.put(rankOrder[i], i);
        }
        mRankOrder = orderBuilder.build();

        mRankExtractorFunction = rankExtractorFunction;
    }

    private int getOrder(T object) {
        final K key = mRankExtractorFunction.apply(object);

        if (mRankOrder.containsKey(key)) {
            return mRankOrder.get(key);
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public int compare(T lhs, T rhs) {
        final int orderLhs = getOrder(lhs);
        final int orderRhs = getOrder(rhs);
        // Order can be Integer.MAX_VALUE so subtraction should not be used.
        return orderLhs < orderRhs ? -1 : (orderLhs == orderRhs ? 0 : 1);
    }
}
