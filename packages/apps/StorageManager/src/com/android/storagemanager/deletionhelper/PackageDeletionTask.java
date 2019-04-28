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

package com.android.storagemanager.deletionhelper;

import android.content.pm.IPackageDeleteObserver;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deletes a specified set of apps as a specified user and calls back once done.
 */
public class PackageDeletionTask {
    private Set<String> mPackages;
    private Callback mCallback;
    private PackageManager mPm;
    private UserHandle mUser;

    public PackageDeletionTask(PackageManager pm, Set<String> packageNames, Callback callback) {
        mPackages = packageNames;
        mCallback = callback;
        mPm = pm;
        mUser = android.os.Process.myUserHandle();
    }

    /**
     * Runs the deletion task and clears out the given packages. Upon completion, the callback
     * is run, if it is set.
     */
    public void run() {
        PackageDeletionObserver observer = new PackageDeletionObserver(mPackages.size());
        for (String packageName : mPackages) {
            mPm.deletePackageAsUser(packageName, observer, 0, mUser.getIdentifier());
        }
    }

    private class PackageDeletionObserver extends IPackageDeleteObserver.Stub {
        private final AtomicInteger mPackagesRemaining = new AtomicInteger(0);

        public PackageDeletionObserver(int packages) {
            mPackagesRemaining.set(packages);
        }

        @Override
        public void packageDeleted(String packageName, int returnCode) {
            if (returnCode != PackageManager.DELETE_SUCCEEDED) {
                mCallback.onError();
                return;
            }

            int remaining = mPackagesRemaining.decrementAndGet();
            if (remaining == 0) {
                mCallback.onSuccess();
            }
        }
    }

    public static abstract class Callback {
        public abstract void onSuccess();
        public abstract void onError();
    }
}
