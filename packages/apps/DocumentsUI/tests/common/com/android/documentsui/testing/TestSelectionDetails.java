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

package com.android.documentsui.testing;

import com.android.documentsui.MenuManager.SelectionDetails;

/**
 * Test copy of SelectionDetails, everything default to false
 */
public class TestSelectionDetails implements SelectionDetails {

    public int size;
    public boolean canRename;
    public boolean canDelete;
    public boolean containPartial;
    public boolean containsFilesInArchive;
    public boolean containDirectories;
    public boolean containFiles;
    public boolean canPasteInto;
    public boolean canExtract;
    public boolean canOpenWith;
    public boolean canViewInOwner;

    @Override
    public boolean containsPartialFiles() {
        return containPartial;
    }

    @Override
    public boolean containsFiles() {
        return containFiles;
    }

    @Override
    public boolean containsDirectories() {
        return containDirectories;
    }

    @Override
    public boolean containsFilesInArchive() {
        return containsFilesInArchive;
    }

    @Override
    public boolean canRename() {
        return canRename;
    }

    @Override
    public boolean canDelete() {
        return canDelete;
    }

    @Override
    public boolean canExtract() {
        return canExtract;
    }

    @Override
    public boolean canPasteInto() {
        return canPasteInto;
    }

    @Override
    public boolean canOpenWith() {
        return canOpenWith;
    }

    @Override
    public boolean canViewInOwner() {
        return canViewInOwner;
    }

    @Override
    public int size() {
        return size;
    }
 }
