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
 * limitations under the License
 */

package com.android.tv.settings.system.development;

import android.annotation.Nullable;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.text.TextUtils;

import com.android.tv.settings.R;

import java.text.Collator;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppPicker extends Activity {

    public static final String EXTRA_REQUESTIING_PERMISSION
            = "com.android.settings.extra.REQUESTIING_PERMISSION";
    public static final String EXTRA_DEBUGGABLE = "com.android.settings.extra.DEBUGGABLE";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            final String permissionName = getIntent().getStringExtra(EXTRA_REQUESTIING_PERMISSION);
            final Boolean debuggableOnly = getIntent().getBooleanExtra(EXTRA_DEBUGGABLE, false);

            GuidedStepFragment.addAsRoot(this,
                    AppPickerFragment.newInstance(permissionName, debuggableOnly),
                    android.R.id.content);
        }
    }

    public static class AppPickerFragment extends GuidedStepFragment {

        private String mPermissionName;
        private boolean mDebuggableOnly;

        public static AppPickerFragment newInstance(String permissionName, boolean debuggableOnly) {
            final AppPickerFragment f = new AppPickerFragment();
            final Bundle b = new Bundle(2);
            b.putString(EXTRA_REQUESTIING_PERMISSION, permissionName);
            b.putBoolean(EXTRA_DEBUGGABLE, debuggableOnly);
            f.setArguments(b);
            return f;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            mPermissionName = getArguments().getString(EXTRA_REQUESTIING_PERMISSION);
            mDebuggableOnly = getArguments().getBoolean(EXTRA_DEBUGGABLE);

            super.onCreate(savedInstanceState);
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.choose_application),
                    null,
                    null,
                    getContext().getDrawable(R.drawable.ic_adb_132dp));
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            final List<ApplicationInfo> pkgs =
                    getActivity().getPackageManager().getInstalledApplications(0);
            final PackageManager pm = getActivity().getPackageManager();

            for (final ApplicationInfo ai : pkgs) {
                if (ai.uid == Process.SYSTEM_UID) {
                    continue;
                }

                // Filter out apps that are not debuggable if required.
                if (mDebuggableOnly) {
                    // On a user build, we only allow debugging of apps that
                    // are marked as debuggable.  Otherwise (for platform development)
                    // we allow all apps.
                    if ((ai.flags&ApplicationInfo.FLAG_DEBUGGABLE) == 0
                            && "user".equals(Build.TYPE)) {
                        continue;
                    }
                }

                // Filter out apps that do not request the permission if required.
                if (mPermissionName != null) {
                    boolean requestsPermission = false;
                    try {
                        PackageInfo pi = pm.getPackageInfo(ai.packageName,
                                PackageManager.GET_PERMISSIONS);
                        if (pi.requestedPermissions == null) {
                            continue;
                        }
                        for (String requestedPermission : pi.requestedPermissions) {
                            if (requestedPermission.equals(mPermissionName)) {
                                requestsPermission = true;
                                break;
                            }
                        }
                        if (!requestsPermission) {
                            continue;
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        continue;
                    }
                }

                actions.add(new AppAction(
                        ai.packageName, ai.loadLabel(pm).toString(), ai.loadIcon(pm)));
            }

            Collections.sort(actions, new Comparator<GuidedAction>() {
                private final Collator mCollator = Collator.getInstance();
                @Override
                public int compare(GuidedAction a, GuidedAction b) {
                    return mCollator.compare(a.getTitle(), b.getTitle());
                }
            });

            actions.add(0, new AppAction(null, getString(R.string.no_application), null));
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            final Intent intent = new Intent();
            final String packageName = ((AppAction) action).getPackageName();
            if (!TextUtils.isEmpty(packageName)) {
                intent.setAction(packageName);
            }
            getActivity().setResult(RESULT_OK, intent);
            getActivity().finish();
        }

        private static class AppAction extends GuidedAction {

            private final String mPackageName;

            public AppAction(String packageName, String label, Drawable icon) {
                mPackageName = packageName;
                setTitle(label);
                setDescription(packageName);
                setIcon(icon);
                setEnabled(true);
                setFocusable(true);
            }

            public String getPackageName() {
                return mPackageName;
            }
        }
    }
}
