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
import android.support.test.filters.LargeTest;

import com.android.documentsui.files.FilesActivity;

@LargeTest
public class BandSelectionUiTest extends ActivityTest<FilesActivity> {

    public BandSelectionUiTest() {
        super(FilesActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        initTestFiles();
        bots.roots.closeDrawer();
    }

    public void testBandSelection_allFiles() throws Exception {
        bots.main.switchToGridMode();
        Rect dirListBounds = bots.directory.findDocumentsList().getBounds();
        Point start = new Point(dirListBounds.right - 1, dirListBounds.bottom - 1);
        Point end = new Point(dirListBounds.left + 1, dirListBounds.top + 1);
        bots.gesture.bandSelection(start, end);

        bots.directory.assertSelection(4);
    }

    public void testBandSelection_someFiles() throws Exception {
        bots.main.switchToGridMode();
        Rect dirListBounds = bots.directory.findDocumentsList().getBounds();
        Rect startDoc = bots.directory.findDocument(fileName2).getBounds();
        // 100 pixels below bottom of file2
        Point start = new Point(startDoc.centerX(), startDoc.bottom + 100);
        // Top left corner
        Point end = new Point(dirListBounds.left + 1, dirListBounds.top + 1);
        bots.gesture.bandSelection(start, end);

        bots.directory.assertSelection(3);
    }
}
