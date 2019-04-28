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
 * limitations under the License
 */

package com.android.tv.license;

import android.content.Context;

import com.android.tv.R;
import com.android.tv.ui.sidepanel.ActionItem;
import com.android.tv.ui.sidepanel.SideFragment;

import java.util.ArrayList;
import java.util.List;

/** Opens a dialog showing open source licenses. */
public final class LicenseSideFragment extends SideFragment {

    public static final String TRACKER_LABEL = "Open Source Licenses";

    public class LicenseActionItem extends ActionItem {
        private final License license;

        public LicenseActionItem(License license) {
            super(license.getLibraryName());
            this.license = license;
        }

        @Override
        protected void onSelected() {
            LicenseDialogFragment dialog = LicenseDialogFragment.newInstance(license);
            getMainActivity()
                    .getOverlayManager()
                    .showDialogFragment(LicenseDialogFragment.DIALOG_TAG, dialog, true);
        }
    }

    private List<LicenseActionItem> licenses;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        licenses = toActionItems(Licenses.getLicenses(context));
    }

    private List<LicenseActionItem> toActionItems(ArrayList<License> licenses) {
        List<LicenseActionItem> items = new ArrayList<>(licenses.size());
        for (License license : licenses) {
            items.add(new LicenseActionItem(license));
        }
        return items;
    }

    @Override
    protected String getTitle() {
        return getResources().getString(R.string.settings_menu_licenses);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<LicenseActionItem> getItemList() {
        return licenses;
    }
}
