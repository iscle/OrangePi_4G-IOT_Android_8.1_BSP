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

package com.android.tv;

import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.ArraySet;

import com.android.tv.data.Channel;

import java.util.Set;

/**
 * A wrapper for safely getting the current {@link MainActivity}.
 * Note that this class is not thread-safe. All the public methods should be called on main thread.
 */
@MainThread
public final class MainActivityWrapper {
    private MainActivity mActivity;

    private final Set<OnCurrentChannelChangeListener> mListeners = new ArraySet<>();

    /**
     * Returns the current main activity.
     * <b>WARNING</b> do not keep a reference to MainActivity, leaking activities is expensive.
     */
    MainActivity getMainActivity() {
        return mActivity;
    }

    /**
     * Checks if the given {@code activity} is the current main activity.
     */
    boolean isCurrent(MainActivity activity) {
        return activity != null && mActivity == activity;
    }

    /**
     * Sets the currently created main activity instance.
     */
    public void onMainActivityCreated(@NonNull MainActivity activity) {
        mActivity = activity;
    }

    /**
     * Unsets the main activity instance.
     */
    public void onMainActivityDestroyed(@NonNull MainActivity activity) {
        if (mActivity == activity) {
            mActivity = null;
        }
    }

    /**
     * Notifies the current channel change.
     */
    void notifyCurrentChannelChange(@NonNull MainActivity caller, @Nullable Channel channel) {
        if (mActivity == caller) {
            for (OnCurrentChannelChangeListener listener : mListeners) {
                listener.onCurrentChannelChange(channel);
            }
        }
    }

    /**
     * Checks if the main activity is created.
     */
    public boolean isCreated() {
        return mActivity != null;
    }

    /**
     * Checks if the main activity is started.
     */
    public boolean isStarted() {
        return mActivity != null && mActivity.isActivityStarted();
    }

    /**
     * Checks if the main activity is resumed.
     */
    public boolean isResumed() {
        return mActivity != null && mActivity.isActivityResumed();
    }

    /**
     * Adds OnCurrentChannelChangeListener.
     */
    public void addOnCurrentChannelChangeListener(OnCurrentChannelChangeListener listener) {
        mListeners.add(listener);
    }

    /**
     * Removes OnCurrentChannelChangeListener.
     */
    public void removeOnCurrentChannelChangeListener(OnCurrentChannelChangeListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Listener for the current channel change in main activity.
     */
    public interface OnCurrentChannelChangeListener {
        /**
         * Called when the current channel changes.
         */
        void onCurrentChannelChange(@Nullable Channel channel);
    }
}
