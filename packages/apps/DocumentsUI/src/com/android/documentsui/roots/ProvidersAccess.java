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

package com.android.documentsui.roots;

import static com.android.documentsui.base.Shared.DEBUG;
import static com.android.documentsui.base.Shared.VERBOSE;

import android.util.Log;

import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Provides testable access to key {@link ProvidersCache} methods.
 */
public interface ProvidersAccess {

    String BROADCAST_ACTION = "com.android.documentsui.action.ROOT_CHANGED";

    /**
     * Return the requested {@link RootInfo}, but only loading the roots for the
     * requested authority. This is useful when we want to load fast without
     * waiting for all the other roots to come back.
     */
    RootInfo getRootOneshot(String authority, String rootId);

    Collection<RootInfo> getMatchingRootsBlocking(State state);

    Collection<RootInfo> getRootsBlocking();

    RootInfo getDefaultRootBlocking(State state);

    RootInfo getRecentsRoot();

    String getApplicationName(String authority);

    String getPackageName(String authority);

    /**
     * Returns a list of roots for the specified authority. If not found, then
     * an empty list is returned.
     */
    Collection<RootInfo> getRootsForAuthorityBlocking(String authority);

    public static List<RootInfo> getMatchingRoots(Collection<RootInfo> roots, State state) {

        final String tag = "ProvidersAccess";

        final List<RootInfo> matching = new ArrayList<>();
        for (RootInfo root : roots) {

            if (VERBOSE) Log.v(tag, "Evaluationg root: " + root);

            if (state.action == State.ACTION_CREATE && !root.supportsCreate()) {
                if (VERBOSE) Log.v(tag, "Excluding read-only root because: ACTION_CREATE.");
                continue;
            }

            if (state.action == State.ACTION_PICK_COPY_DESTINATION
                    && !root.supportsCreate()) {
                if (VERBOSE) Log.v(
                        tag, "Excluding read-only root because: ACTION_PICK_COPY_DESTINATION.");
                continue;
            }

            if (state.action == State.ACTION_OPEN_TREE && !root.supportsChildren()) {
                if (VERBOSE) Log.v(
                        tag, "Excluding root !supportsChildren because: ACTION_OPEN_TREE.");
                continue;
            }

            if (!state.showAdvanced && root.isAdvanced()) {
                if (VERBOSE) Log.v(tag, "Excluding root because: unwanted advanced device.");
                continue;
            }

            if (state.localOnly && !root.isLocalOnly()) {
                if (VERBOSE) Log.v(tag, "Excluding root because: unwanted non-local device.");
                continue;
            }

            if (state.directoryCopy && root.isDownloads()) {
                if (VERBOSE) Log.v(
                        tag, "Excluding downloads root because: unsupported directory copy.");
                continue;
            }

            if (state.action == State.ACTION_OPEN && root.isEmpty()) {
                if (VERBOSE) Log.v(tag, "Excluding empty root because: ACTION_OPEN.");
                continue;
            }

            if (state.action == State.ACTION_GET_CONTENT && root.isEmpty()) {
                if (VERBOSE) Log.v(tag, "Excluding empty root because: ACTION_GET_CONTENT.");
                continue;
            }

            final boolean overlap =
                    MimeTypes.mimeMatches(root.derivedMimeTypes, state.acceptMimes) ||
                    MimeTypes.mimeMatches(state.acceptMimes, root.derivedMimeTypes);
            if (!overlap) {
                if (VERBOSE) Log.v(
                        tag, "Excluding root because: unsupported content types > "
                        + Arrays.toString(state.acceptMimes));
                continue;
            }

            if (state.excludedAuthorities.contains(root.authority)) {
                if (VERBOSE) Log.v(tag, "Excluding root because: owned by calling package.");
                continue;
            }

            matching.add(root);
        }

        if (DEBUG) Log.d(tag, "Matched roots: " + matching);
        return matching;
    }
}
