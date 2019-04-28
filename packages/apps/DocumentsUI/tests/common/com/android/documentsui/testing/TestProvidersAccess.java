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
package com.android.documentsui.testing;

import android.provider.DocumentsContract.Root;
import com.android.documentsui.InspectorProvider;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.roots.ProvidersAccess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class TestProvidersAccess implements ProvidersAccess {

    public static final RootInfo DOWNLOADS;
    public static final RootInfo HOME;
    public static final RootInfo HAMMY;
    public static final RootInfo PICKLES;
    public static final RootInfo RECENTS;
    public static final RootInfo INSPECTOR;

    static {
        DOWNLOADS = new RootInfo() {{
            flags = Root.FLAG_SUPPORTS_CREATE;
        }};
        DOWNLOADS.authority = Providers.AUTHORITY_DOWNLOADS;
        DOWNLOADS.rootId = Providers.ROOT_ID_DOWNLOADS;
        DOWNLOADS.flags = Root.FLAG_LOCAL_ONLY
                | Root.FLAG_SUPPORTS_CREATE
                | Root.FLAG_SUPPORTS_IS_CHILD
                | Root.FLAG_SUPPORTS_RECENTS;

        HOME = new RootInfo();
        HOME.authority = Providers.AUTHORITY_STORAGE;
        HOME.rootId = Providers.ROOT_ID_HOME;
        HOME.flags = Root.FLAG_LOCAL_ONLY
                | Root.FLAG_SUPPORTS_CREATE
                | Root.FLAG_SUPPORTS_IS_CHILD
                | Root.FLAG_SUPPORTS_RECENTS;

        HAMMY = new RootInfo();
        HAMMY.authority = "yummies";
        HAMMY.rootId = "hamsandwich";

        PICKLES = new RootInfo();
        PICKLES.authority = "yummies";
        PICKLES.rootId = "pickles";

        RECENTS = new RootInfo() {{
            // Special root for recents
            derivedType = RootInfo.TYPE_RECENTS;
            flags = Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_IS_CHILD;
            availableBytes = -1;
        }};

        INSPECTOR = new RootInfo();
        INSPECTOR.authority = InspectorProvider.AUTHORITY;
        INSPECTOR.rootId = InspectorProvider.ROOT_ID;
        INSPECTOR.flags = Root.FLAG_LOCAL_ONLY
            | Root.FLAG_SUPPORTS_CREATE;
    }

    public final Map<String, Collection<RootInfo>> roots = new HashMap<>();
    private @Nullable RootInfo nextRoot;

    public TestProvidersAccess() {
        add(DOWNLOADS);
        add(HOME);
        add(HAMMY);
        add(PICKLES);
    }

    private void add(RootInfo root) {
        if (!roots.containsKey(root.authority)) {
            roots.put(root.authority, new ArrayList<>());
        }
        roots.get(root.authority).add(root);
    }

    public void configurePm(TestPackageManager pm) {
        pm.addStubContentProviderForRoot(TestProvidersAccess.DOWNLOADS);
        pm.addStubContentProviderForRoot(TestProvidersAccess.HOME);
        pm.addStubContentProviderForRoot(TestProvidersAccess.HAMMY);
        pm.addStubContentProviderForRoot(TestProvidersAccess.PICKLES);
    }

    @Override
    public RootInfo getRootOneshot(String authority, String rootId) {
        if (roots.containsKey(authority)) {
            for (RootInfo root : roots.get(authority)) {
                if (rootId.equals(root.rootId)) {
                    return root;
                }
            }
        }
        return null;
    }

    @Override
    public Collection<RootInfo> getMatchingRootsBlocking(State state) {
        List<RootInfo> allRoots = new ArrayList<>();
        for (String authority : roots.keySet()) {
            allRoots.addAll(roots.get(authority));
        }
        return ProvidersAccess.getMatchingRoots(allRoots, state);
    }

    @Override
    public Collection<RootInfo> getRootsForAuthorityBlocking(String authority) {
        return roots.get(authority);
    }

    @Override
    public Collection<RootInfo> getRootsBlocking() {
        List<RootInfo> result = new ArrayList<>();
        for (Collection<RootInfo> vals : roots.values()) {
            result.addAll(vals);
        }
        return result;
    }

    @Override
    public RootInfo getDefaultRootBlocking(State state) {
        return DOWNLOADS;
    }

    @Override
    public RootInfo getRecentsRoot() {
        return RECENTS;
    }

    @Override
    public String getApplicationName(String authority) {
        return "Test Application";
    }

    @Override
    public String getPackageName(String authority) {
        return "com.android.documentsui";
    }
}
