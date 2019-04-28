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

package com.android.documentsui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.PairedTask;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.AnimationView;
import com.android.documentsui.queries.SearchViewManager;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.selection.SelectionManager;

import java.util.Collection;

/**
 * Monitors roots change and refresh the page when necessary.
 */
final class RootsMonitor<T extends Activity & CommonAddons> {

    private final LocalBroadcastManager mManager;
    private final BroadcastReceiver mReceiver;

    RootsMonitor(
            final T activity,
            final ActionHandler actions,
            final ProvidersAccess providers,
            final DocumentsAccess docs,
            final State state,
            final SearchViewManager searchMgr,
            final Runnable actionModeFinisher) {
        mManager = LocalBroadcastManager.getInstance(activity);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                new HandleRootsChangedTask<T>(
                        activity,
                        actions,
                        providers,
                        docs,
                        state,
                        searchMgr,
                        actionModeFinisher).execute(activity.getCurrentRoot());
            }
        };
    }

    void start() {
        mManager.registerReceiver(mReceiver, new IntentFilter(ProvidersAccess.BROADCAST_ACTION));
    }

    void stop() {
        mManager.unregisterReceiver(mReceiver);
    }

    private static class HandleRootsChangedTask<T extends Activity & CommonAddons>
            extends PairedTask<T, RootInfo, RootInfo> {
        private final ActionHandler mActions;
        private final ProvidersAccess mProviders;
        private final DocumentsAccess mDocs;
        private final State mState;
        private final SearchViewManager mSearchMgr;
        private final Runnable mActionModeFinisher;

        private RootInfo mCurrentRoot;
        private DocumentInfo mDefaultRootDocument;

        private HandleRootsChangedTask(
                T activity,
                ActionHandler actions,
                ProvidersAccess providers,
                DocumentsAccess docs,
                State state,
                SearchViewManager searchMgr,
                Runnable actionModeFinisher) {
            super(activity);
            mActions = actions;
            mProviders = providers;
            mDocs = docs;
            mState = state;
            mSearchMgr = searchMgr;
            mActionModeFinisher = actionModeFinisher;
        }

        @Override
        protected RootInfo run(RootInfo... roots) {
            assert (roots.length == 1);
            mCurrentRoot = roots[0];
            final Collection<RootInfo> cachedRoots = mProviders.getRootsBlocking();
            for (final RootInfo root : cachedRoots) {
                if (root.getUri().equals(mCurrentRoot.getUri())) {
                    // We don't need to change the current root as the current root was not removed.
                    return null;
                }
            }

            // Choose the default root.
            final RootInfo defaultRoot = mProviders.getDefaultRootBlocking(mState);
            assert (defaultRoot != null);
            if (!defaultRoot.isRecents()) {
                mDefaultRootDocument = mDocs.getRootDocument(defaultRoot);
            }
            return defaultRoot;
        }

        @Override
        protected void finish(RootInfo defaultRoot) {
            if (defaultRoot == null) {
                return;
            }

            // If the activity has been launched for the specific root and it is removed, finish the
            // activity.
            final Uri uri = mOwner.getIntent().getData();
            if (uri != null && uri.equals(mCurrentRoot.getUri())) {
                mOwner.finish();
                return;
            }

            // Clean action mode before changing root.
            mActionModeFinisher.run();

            // Clear entire backstack and start in new root.
            mState.stack.changeRoot(defaultRoot);
            mSearchMgr.update(mState.stack);

            if (defaultRoot.isRecents()) {
                mOwner.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
            } else {
                mActions.openContainerDocument(mDefaultRootDocument);
            }
        }
    }
}
