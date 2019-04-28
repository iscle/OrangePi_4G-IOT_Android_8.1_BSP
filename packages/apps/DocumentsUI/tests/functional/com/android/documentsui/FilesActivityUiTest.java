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

package com.android.documentsui;

import android.app.Instrumentation;
import android.net.Uri;
import android.os.RemoteException;
import android.support.test.filters.LargeTest;

import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.inspector.InspectorActivity;

@LargeTest
public class FilesActivityUiTest extends ActivityTest<FilesActivity> {

    public FilesActivityUiTest() {
        super(FilesActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        initTestFiles();
    }

    @Override
    public void initTestFiles() throws RemoteException {
        Uri uri = mDocsHelper.createFolder(rootDir0, dirName1);
        mDocsHelper.createFolder(uri, childDir1);

        mDocsHelper.createDocument(rootDir0, "text/plain", "file0.log");
        mDocsHelper.createDocument(rootDir0, "image/png", "file1.png");
        mDocsHelper.createDocument(rootDir0, "text/csv", "file2.csv");

        mDocsHelper.createDocument(rootDir1, "text/plain", "anotherFile0.log");
        mDocsHelper.createDocument(rootDir1, "text/plain", "poodles.text");
    }

    // Recents is a strange meta root that gathers entries from other providers.
    // It is special cased in a variety of ways, which is why we just want
    // to be able to click on it.
    public void testClickRecent() throws Exception {
        bots.roots.openRoot("Recent");
        bots.main.assertWindowTitle("Recent");
    }

    public void testRootClick_SetsWindowTitle() throws Exception {
        bots.roots.openRoot("Images");
        bots.main.assertWindowTitle("Images");
    }

    public void testProtectedFolder_showsAuthenticationUi() throws Exception {
        // If feature is disabled, this test is a no-op.
        if (features.isRemoteActionsEnabled()) {
            bots.roots.openRoot("Demo Root");
            bots.main.switchToListMode();
            bots.directory.openDocument("throw a authentication exception");
            bots.directory.assertHeaderMessageText(
                    "To view this directory, sign in to DocumentsUI Tests");
        }

    }

    public void testFilesListed() throws Exception {
        bots.directory.assertDocumentsPresent("file0.log", "file1.png", "file2.csv");
    }

    public void testFilesList_LiveUpdate() throws Exception {
        mDocsHelper.createDocument(rootDir0, "yummers/sandwich", "Ham & Cheese.sandwich");

        bots.directory.waitForDocument("Ham & Cheese.sandwich");
        bots.directory.assertDocumentsPresent(
                "file0.log", "file1.png", "file2.csv", "Ham & Cheese.sandwich");
    }

    public void testNavigate_inFixedLayout_byBreadcrumb() throws Exception {
        bots.directory.openDocument(dirName1);
        bots.directory.waitForDocument(childDir1);  // wait for known content
        bots.directory.assertDocumentsPresent(childDir1);

        bots.breadcrumb.revealAsNeeded();
        device.waitForIdle();
        bots.breadcrumb.assertItemsPresent(dirName1, "TEST_ROOT_0");

        bots.breadcrumb.clickItem("TEST_ROOT_0");
        bots.directory.waitForDocument(dirName1);
    }

    public void testNavigate_inFixedLayout_whileHasSelection() throws Exception {
        if (bots.main.inFixedLayout()) {
            bots.roots.openRoot(rootDir0.title);
            device.waitForIdle();
            bots.directory.selectDocument("file0.log", 1);

            // ensure no exception is thrown while navigating to a different root
            bots.roots.openRoot(rootDir1.title);
        }
    }

    public void testNavigationToInspector() throws Exception {
        if(!features.isInspectorEnabled()) {
            return;
        }
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                InspectorActivity.class.getName(), null, false);
        bots.directory.selectDocument("file0.log");
        bots.main.clickActionItem("Properties");
        monitor.waitForActivityWithTimeout(TIMEOUT);
    }

    public void testRootChange_UpdatesSortHeader() throws Exception {

        // switch to separate display modes for two separate roots. Each
        // mode has its own distinct sort header. This should be remembered
        // by files app.
        bots.roots.openRoot("Images");
        bots.main.switchToGridMode();
        bots.roots.openRoot("Videos");
        bots.main.switchToListMode();

        // Now switch back and assert the correct mode sort header mode
        // is restored when we load the root with that display mode.
        bots.roots.openRoot("Images");
        bots.sortHeader.assertDropdownMode();
        if (bots.main.inFixedLayout()) {
            bots.roots.openRoot("Videos");
            bots.sortHeader.assertColumnMode();
        } else {
            bots.roots.openRoot("Videos");
            bots.sortHeader.assertDropdownMode();
        }
    }
}
