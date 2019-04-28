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

import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.RemoteException;
import android.support.test.filters.LargeTest;

import com.android.documentsui.files.FilesActivity;

import java.util.HashMap;
import java.util.Map;

@LargeTest
public class ContextMenuUiTest extends ActivityTest<FilesActivity> {

    private Map<String, Boolean> menuItems;

    public ContextMenuUiTest() {
        super(FilesActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        initTestFiles();
        bots.roots.closeDrawer();
        menuItems = new HashMap<>();

        menuItems.put("Share", false);
        menuItems.put("Open", false);
        menuItems.put("Open with", false);
        menuItems.put("Cut", false);
        menuItems.put("Copy", false);
        menuItems.put("Rename", false);
        menuItems.put("Delete", false);
        menuItems.put("Open in new window", false);
        menuItems.put("Paste into folder", false);
        menuItems.put("Select all", false);
        menuItems.put("New folder", false);
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

    public void testContextMenu_onFile() throws Exception {
        menuItems.put("Share", true);
        menuItems.put("Open", true);
        menuItems.put("Open with", true);
        menuItems.put("Cut", true);
        menuItems.put("Copy", true);
        menuItems.put("Rename", true);
        menuItems.put("Delete", true);

        bots.directory.rightClickDocument("file1.png");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    public void testContextMenu_onDir() throws Exception {
        menuItems.put("Cut", true);
        menuItems.put("Copy", true);
        menuItems.put("Paste into folder", true);
        menuItems.put("Open in new window", true);
        menuItems.put("Delete", true);
        menuItems.put("Rename", true);
        bots.directory.rightClickDocument("Dir1");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    public void testContextMenu_onMixedFileDir() throws Exception {
        menuItems.put("Cut", true);
        menuItems.put("Copy", true);
        menuItems.put("Delete", true);
        bots.directory.selectDocument("file1.png", 1);
        bots.directory.selectDocument("Dir1", 2);
        bots.directory.rightClickDocument("Dir1");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    public void testContextMenu_onEmptyArea() throws Exception {
        menuItems.put("Select all", true);
        menuItems.put("Paste", true);
        menuItems.put("New folder", true);
        Rect dirListBounds = bots.directory.findDocumentsList().getBounds();
        bots.directory.rightClickDocument(
                new Point(dirListBounds.right - 1, dirListBounds.bottom - 1)); //bottom right corner
        bots.menu.assertPresentMenuItems(menuItems);
    }
}
