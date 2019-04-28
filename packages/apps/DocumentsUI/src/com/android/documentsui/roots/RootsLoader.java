/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui.roots;

import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;

import java.util.Collection;

public class RootsLoader extends AsyncTaskLoader<Collection<RootInfo>> {
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onContentChanged();
        }
    };

    private final ProvidersCache mProviders;
    private final State mState;

    private Collection<RootInfo> mResult;

    public RootsLoader(Context context, ProvidersCache providers, State state) {
        super(context);
        mProviders = providers;
        mState = state;

        LocalBroadcastManager.getInstance(context).registerReceiver(
                mReceiver, new IntentFilter(ProvidersAccess.BROADCAST_ACTION));
    }

    @Override
    public final Collection<RootInfo> loadInBackground() {
        return mProviders.getMatchingRootsBlocking(mState);
    }

    @Override
    public void deliverResult(Collection<RootInfo> result) {
        if (isReset()) {
            return;
        }

        mResult = result;

        if (isStarted()) {
            super.deliverResult(result);
        }
    }

    @Override
    protected void onStartLoading() {
        if (mResult != null) {
            deliverResult(mResult);
        }
        if (takeContentChanged() || mResult == null) {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        mResult = null;

        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mReceiver);
    }
}
