/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.annotation.DrawableRes;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;

import com.android.documentsui.R;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.prefs.ScopedPreferences;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages dynamic shortcuts.
 */
public final class ShortcutsUpdater {

    private final ScopedPreferences mPrefs;
    private final Context mContext;

    public ShortcutsUpdater(Context context, ScopedPreferences prefs) {
        mContext = context;
        mPrefs = prefs;
    }

    public void update(Collection<RootInfo> roots) {
        ShortcutManager mgr = mContext.getSystemService(ShortcutManager.class);

        Map<String, ShortcutInfo> existing = getPinnedShortcuts(mgr);
        List<ShortcutInfo> devices = getDeviceShortcuts(roots);
        List<String> deviceIds = new ArrayList<>();
        for (ShortcutInfo s : devices) {
            deviceIds.add(s.getId());
        }

        mgr.setDynamicShortcuts(devices.subList(0, getNumDynSlots(mgr, devices.size())));

        // Mark any shortcut that doesn't correspond to a current root as disabled.
        List<String> disabled = new ArrayList<>();
        for (String id : existing.keySet()) {
            // If it isn't in candidates, it isn't a live target, so we disable it.
            if (!deviceIds.contains(id)) {
                disabled.add(id);
            }
        }

        mgr.enableShortcuts(deviceIds);
        mgr.disableShortcuts(disabled);
    }

    /**
     * Return at most four awesome devices/roots to include as dynamic shortcuts.
     */
    private List<ShortcutInfo> getDeviceShortcuts(Collection<RootInfo> roots) {
        List<ShortcutInfo> devices = new ArrayList<>();
        for (RootInfo root : roots) {
            String id = root.getUri().toString();
            // TODO: Hook up third party providers. For now, there may be dupes when
            // user has multiple accounts installed, and the plain title doesn't
            // disambiguate for the user. So, we don't add them.
            // if (!Providers.isSystemProvider(root.authority)) {
            //    // add third party providers at the beginning of the list.
            //    devices.add(createShortcut(root, R.drawable.ic_folder_shortcut));
            // } else
            if (root.isAdvanced() && root.authority.equals(Providers.AUTHORITY_STORAGE)) {
                // internal storage
                if (mPrefs.getShowDeviceRoot()) {
                    devices.add(0, createShortcut(root, R.drawable.ic_advanced_shortcut));
                }
            } else if (root.isAdvanced()) {
                // probably just bugreports provider
                devices.add(0, createShortcut(root, R.drawable.ic_folder_shortcut));
            }
            // TODO: Hook up USB and MTP devices. In order to do this we need
            // to fire up a broadcast to listen for ACTION_MEDIA_MOUNTED
            // and ACTION_MEDIA_REMOVED. But doing so now would require a good
            // bit of refactoring, rendering out of scope for now. <sadface>.
            // else if (root.isUsb() || root.isMtp()) {
            //    // probably just bugreports provider
            //    devices.add(0, createShortcut(root, R.drawable.ic_usb_shortcut));
            // }
        }

        return devices;
    }

    private Map<String, ShortcutInfo> getPinnedShortcuts(ShortcutManager mgr) {
        Map<String, ShortcutInfo> pinned = new HashMap<>();
        for (ShortcutInfo s : mgr.getDynamicShortcuts()) {
            pinned.put(s.getId(), s);
        }
        return pinned;
    }

    private int getNumDynSlots(ShortcutManager mgr, int numDevices) {
        int slots = mgr.getMaxShortcutCountForActivity() - mgr.getManifestShortcuts().size();
        return numDevices >= slots ? slots : numDevices;
    }

    private ShortcutInfo createShortcut(RootInfo root, @DrawableRes int resId) {
        Intent intent = new Intent(mContext, FilesActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(root.getUri());

        return new ShortcutInfo.Builder(mContext, root.getUri().toString())
                .setShortLabel(root.title)
                .setIcon(Icon.createWithResource(mContext, resId))
                .setIntent(intent)
                .build();
    }
}
