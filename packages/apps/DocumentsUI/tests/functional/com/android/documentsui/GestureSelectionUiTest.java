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

import android.support.test.filters.LargeTest;

import com.android.documentsui.files.FilesActivity;

@LargeTest
public class GestureSelectionUiTest extends ActivityTest<FilesActivity> {

    public GestureSelectionUiTest() {
        super(FilesActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        initTestFiles();
        bots.roots.closeDrawer();
    }

    public void testGridGestureSelect_twoFiles() throws Exception {
        bots.main.switchToGridMode();
        bots.gesture.gestureSelectFiles(fileName1, fileName2);

        bots.directory.assertSelection(2);
    }

    public void testGridGestureSelect_multipleFiles() throws Exception {
        bots.main.switchToGridMode();
        bots.gesture.gestureSelectFiles(fileName2, dirName1);

        bots.directory.assertSelection(3);

    }

    public void testListGestureSelect_twoFiles() throws Exception {
        bots.main.switchToListMode();
        bots.gesture.gestureSelectFiles(fileName1, fileName2);

        bots.directory.assertSelection(2);

    }

    public void testListGestureSelect_multipleFiles() throws Exception {
        bots.main.switchToListMode();
        bots.gesture.gestureSelectFiles(fileName2, dirName1);

        bots.directory.assertSelection(3);

    }
}
