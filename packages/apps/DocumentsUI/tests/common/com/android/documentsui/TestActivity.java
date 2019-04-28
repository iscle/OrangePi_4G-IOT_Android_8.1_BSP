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

import static junit.framework.Assert.assertEquals;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.test.mock.MockContentResolver;
import android.util.Pair;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestEventHandler;
import com.android.documentsui.testing.TestEventListener;
import com.android.documentsui.testing.TestLoaderManager;
import com.android.documentsui.testing.TestPackageManager;
import com.android.documentsui.testing.TestResources;

import org.mockito.Mockito;

/**
 * Abstract to avoid having to implement unnecessary Activity stuff.
 * Instances are created using {@link #create()}.
 */
public abstract class TestActivity extends AbstractBase {

    public TestResources resources;
    public TestPackageManager packageMgr;
    public Intent intent;
    public RootInfo currentRoot;
    public MockContentResolver contentResolver;
    public TestLoaderManager loaderManager;
    public ActivityManager activityManager;

    public TestEventListener<Intent> startActivity;
    public TestEventListener<Intent> startService;
    public TestEventListener<Pair<IntentSender, Integer>> startIntentSender;
    public TestEventListener<RootInfo> rootPicked;
    public TestEventListener<Void> restoreRootAndDirectory;
    public TestEventListener<Integer> refreshCurrentRootAndDirectory;
    public TestEventListener<Boolean> setRootsDrawerOpen;
    public TestEventListener<Uri> notifyDirectoryNavigated;
    public TestEventHandler<Void> finishedHandler;

    public static TestActivity create(TestEnv env) {
        TestActivity activity = Mockito.mock(TestActivity.class, Mockito.CALLS_REAL_METHODS);
        activity.init(env);
        return activity;
    }

    public void init(TestEnv env) {
        resources = TestResources.create();
        packageMgr = TestPackageManager.create();
        intent = new Intent();

        startActivity = new TestEventListener<>();
        startService = new TestEventListener<>();
        startIntentSender = new TestEventListener<>();
        rootPicked = new TestEventListener<>();
        restoreRootAndDirectory = new TestEventListener<>();
        refreshCurrentRootAndDirectory =  new TestEventListener<>();
        setRootsDrawerOpen = new TestEventListener<>();
        notifyDirectoryNavigated = new TestEventListener<>();
        contentResolver = env.contentResolver;
        loaderManager = new TestLoaderManager();
        finishedHandler = new TestEventHandler<>();
    }

    @Override
    public final String getPackageName() {
        return "Banarama";
    }

    @Override
    public final void startActivity(Intent intent) {
        startActivity.accept(intent);
    }

    public final void assertActivityStarted(String expectedAction) {
        assertEquals(expectedAction, startActivity.getLastValue().getAction());
    }

    @Override
    public final ComponentName startService(Intent intent) {
        startService.accept(intent);
        return null;
    }

    public final void assertServiceStarted(String expectedAction) {
        assertEquals(expectedAction, startService.getLastValue().getAction());
    }

    @Override
    public final Intent getIntent() {
        return intent;
    }

    @Override
    public final Resources getResources() {
        return resources;
    }

    @Override
    public final PackageManager getPackageManager() {
        return packageMgr;
    }

    @Override
    public final void startIntentSenderForResult(IntentSender intent, int requestCode,
            @Nullable Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags)
            throws IntentSender.SendIntentException {
        startIntentSender.accept(new Pair<>(intent, requestCode));
    }

    @Override
    public final void onRootPicked(RootInfo root) {
        rootPicked.accept(root);
    }

    @Override
    public final void onDocumentPicked(DocumentInfo doc) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void notifyDirectoryNavigated(Uri uri) {
        notifyDirectoryNavigated.accept(uri);
    }

    @Override
    public final void restoreRootAndDirectory() {
        restoreRootAndDirectory.accept(null);
    }

    @Override
    public final void refreshCurrentRootAndDirectory(int anim) {
        refreshCurrentRootAndDirectory.accept(anim);
    }

    @Override
    public final RootInfo getCurrentRoot() {
        return currentRoot;
    }

    @Override
    public final void setRootsDrawerOpen(boolean open) {
        setRootsDrawerOpen.accept(open);
    }

    @Override
    public final ContentResolver getContentResolver() {
        return contentResolver;
    }

    @Override
    public final Context getApplicationContext() {
        return this;
    }

    @Override
    public boolean isDestroyed() {
        return false;
    }

    @Override
    public final void updateNavigator() {}

    @Override
    public final LoaderManager getLoaderManager() {
        return loaderManager;
    }

    @Override
    public final Object getSystemService(String service) {
        switch (service) {
            case Context.ACTIVITY_SERVICE:
                return activityManager;
        }

        throw new IllegalArgumentException("Unknown service " + service);
    }

    @Override
    public final void finish() {
        finishedHandler.accept(null);
    }
}

// Trick Mockito into finding our Addons methods correctly. W/o this
// hack, Mockito thinks Addons methods are not implemented.
abstract class AbstractBase extends Activity implements CommonAddons {}
