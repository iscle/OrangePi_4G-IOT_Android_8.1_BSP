/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.base;

/**
 * A functional interface that handles an event and returns a boolean to indicate if the event
 * is consumed.
 */
@FunctionalInterface
public interface EventHandler<T> {
    boolean accept(T event);

    public static <T> EventHandler<T> createStub(boolean reply) {
        return new Stub<T>(reply);
    }

    public static final class Stub<T> implements EventHandler<T> {

        private boolean mReply;

        private Stub(boolean reply) {
            mReply = reply;}

        @Override
        public boolean accept(T event) {
            return mReply;
        }

    }
}