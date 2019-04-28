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

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.StubProvider.ROOT_1_ID;

import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.test.filters.LargeTest;
import android.support.test.filters.Suppress;
import android.view.KeyEvent;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortModel;

import java.util.List;

@LargeTest
public class FileManagementUiTest extends ActivityTest<FilesActivity> {

    public FileManagementUiTest() {
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

    @Suppress
    public void testCreateDirectory() throws Exception {
        bots.main.openOverflowMenu();
        device.waitForIdle();

        bots.main.clickToolbarOverflowItem("New folder");
        device.waitForIdle();

        bots.main.setDialogText("Kung Fu Panda");
        device.waitForIdle();

        bots.keyboard.pressEnter();

        bots.directory.waitForDocument("Kung Fu Panda");
    }

    public void testDeleteDocument() throws Exception {
        bots.directory.selectDocument("file1.png");
        device.waitForIdle();
        bots.main.clickToolbarItem(R.id.action_menu_delete);

        bots.main.clickDialogOkButton();
        device.waitForIdle();

        bots.directory.assertDocumentsAbsent("file1.png");
    }

    public void testKeyboard_CutDocument() throws Exception {
        bots.directory.selectDocument("file1.png");
        device.waitForIdle();
        bots.keyboard.pressKey(KeyEvent.KEYCODE_X, KeyEvent.META_CTRL_ON);

        device.waitForIdle();

        bots.roots.openRoot(ROOT_1_ID);
        bots.keyboard.pressKey(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON);

        bots.directory.waitForDocument("file1.png");
        bots.directory.assertDocumentsPresent("file1.png");

        bots.roots.openRoot(ROOT_0_ID);
        bots.directory.assertDocumentsAbsent("file1.png");
    }

    public void testKeyboard_CopyDocument() throws Exception {
        bots.directory.selectDocument("file1.png");
        device.waitForIdle();
        bots.keyboard.pressKey(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON);

        device.waitForIdle();

        bots.roots.openRoot(ROOT_1_ID);
        bots.keyboard.pressKey(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON);

        bots.directory.waitForDocument("file1.png");

        bots.roots.openRoot(ROOT_0_ID);
        bots.directory.waitForDocument("file1.png");
    }

    public void testDeleteDocument_Cancel() throws Exception {
        bots.directory.selectDocument("file1.png");
        device.waitForIdle();
        bots.main.clickToolbarItem(R.id.action_menu_delete);

        bots.main.clickDialogCancelButton();

        bots.directory.waitForDocument("file1.png");
    }

    public void testCopyLargeAmountOfFiles() throws Exception {
        // Suppress root notification. We're gonna create tons of files and it will soon crash
        // DocsUI because too many root refreshes are queued in an executor.
        Bundle conf = new Bundle();
        conf.putBoolean(StubProvider.EXTRA_ENABLE_ROOT_NOTIFICATION, false);
        mDocsHelper.configure(null, conf);

        final Uri test = mDocsHelper.createFolder(rootDir0, "test");
        final Uri target = mDocsHelper.createFolder(rootDir0, "target");
        String nameOfLastFile = "";
        for (int i = 0; i <= Shared.MAX_DOCS_IN_INTENT; ++i) {
            final String name = i + ".txt";
            final Uri doc =
                    mDocsHelper.createDocument(test, "text/plain", name);
            mDocsHelper.writeDocument(doc, Integer.toString(i).getBytes());
            nameOfLastFile = nameOfLastFile.compareTo(name) < 0 ? name : nameOfLastFile;
        }

        bots.roots.openRoot(ROOT_0_ID);
        bots.directory.openDocument("test");
        bots.sortHeader.sortBy(
                SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_ASCENDING);
        bots.directory.waitForDocument("0.txt");
        bots.keyboard.pressKey(
                KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_ON);
        bots.keyboard.pressKey(
                KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_ON);

        bots.roots.openRoot(ROOT_0_ID);
        bots.directory.openDocument("target");
        bots.directory.pasteFilesFromClipboard();

        // Use these 2 events as a signal that many files have already been copied. Only considering
        // Android devices a more reliable way is to wait until notification goes away, but ARC++
        // uses Chrome OS notifications so it isn't even an option.
        bots.directory.waitForDocument("0.txt");
        bots.directory.waitForDocument(nameOfLastFile);

        final int expectedCount = Shared.MAX_DOCS_IN_INTENT + 1;
        List<DocumentInfo> children = mDocsHelper.listChildren(target, -1);
        if (children.size() == expectedCount) {
            return;
        }

        // Files weren't copied fast enough, so gonna do some polling until they all arrive or copy
        // seems stalled.
        while (true) {
            Thread.sleep(200);
            List<DocumentInfo> newChildren = mDocsHelper.listChildren(target, -1);
            if (newChildren.size() == expectedCount) {
                return;
            }

            if (newChildren.size() > expectedCount) {
                // Should never happen
                fail("Something wrong with this test case. Copied file count "
                        + newChildren.size() + " exceeds expected number " + expectedCount);
            }

            if (newChildren.size() <= children.size()) {
                fail("Only copied " + children.size()
                        + " files, expected to copy " + expectedCount + " files.");
            }

            children = newChildren;
        }
    }
}
