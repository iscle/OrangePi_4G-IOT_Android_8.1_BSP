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

package com.android.documentsui.files;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.test.AndroidTestCase;

import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.TestContext;
import com.android.documentsui.dirlist.TestData;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.testing.SelectionManagers;
import com.android.documentsui.testing.TestDirectoryDetails;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestFeatures;
import com.android.documentsui.testing.TestMenu;
import com.android.documentsui.testing.TestMenuInflater;
import com.android.documentsui.testing.TestMenuItem;
import com.android.documentsui.testing.TestScopedPreferences;
import com.android.documentsui.testing.TestSearchViewManager;
import com.android.documentsui.testing.TestSelectionDetails;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class MenuManagerTest {

    private TestMenu testMenu;

    /* Directory Context Menu items */
    private TestMenuItem dirShare;
    private TestMenuItem dirOpen;
    private TestMenuItem dirOpenWith;
    private TestMenuItem dirCutToClipboard;
    private TestMenuItem dirCopyToClipboard;
    private TestMenuItem dirPasteFromClipboard;
    private TestMenuItem dirCreateDir;
    private TestMenuItem dirSelectAll;
    private TestMenuItem dirRename;
    private TestMenuItem dirDelete;
    private TestMenuItem dirViewInOwner;
    private TestMenuItem dirOpenInNewWindow;
    private TestMenuItem dirPasteIntoFolder;

    /* Root List Context Menu items */
    private TestMenuItem rootEjectRoot;
    private TestMenuItem rootOpenInNewWindow;
    private TestMenuItem rootPasteIntoFolder;
    private TestMenuItem rootSettings;

    /* Action Mode menu items */
    private TestMenuItem actionModeOpen;
    private TestMenuItem actionModeOpenWith;
    private TestMenuItem actionModeShare;
    private TestMenuItem actionModeDelete;
    private TestMenuItem actionModeSelectAll;
    private TestMenuItem actionModeCopyTo;
    private TestMenuItem actionModeExtractTo;
    private TestMenuItem actionModeMoveTo;
    private TestMenuItem actionModeCompress;
    private TestMenuItem actionModeRename;
    private TestMenuItem actionModeViewInOwner;
    private TestMenuItem actionModeInspector;

    /* Option Menu items */
    private TestMenuItem optionSearch;
    private TestMenuItem optionDebug;
    private TestMenuItem optionGrid;
    private TestMenuItem optionList;
    private TestMenuItem optionNewWindow;
    private TestMenuItem optionCreateDir;
    private TestMenuItem optionSelectAll;
    private TestMenuItem optionAdvanced;
    private TestMenuItem optionSettings;

    private TestFeatures features;
    private TestSelectionDetails selectionDetails;
    private TestDirectoryDetails dirDetails;
    private TestSearchViewManager testSearchManager;
    private TestScopedPreferences preferences;
    private RootInfo testRootInfo;
    private DocumentInfo testDocInfo;
    private State state = new State();
    private MenuManager mgr;
    private TestActivity activity = TestActivity.create(TestEnv.create());
    private SelectionManager selectionManager;

    @Before
    public void setUp() {
        testMenu = TestMenu.create();
        dirShare = testMenu.findItem(R.id.dir_menu_share);
        dirOpen = testMenu.findItem(R.id.dir_menu_open);
        dirOpenWith = testMenu.findItem(R.id.dir_menu_open_with);
        dirCutToClipboard = testMenu.findItem(R.id.dir_menu_cut_to_clipboard);
        dirCopyToClipboard = testMenu.findItem(R.id.dir_menu_copy_to_clipboard);
        dirPasteFromClipboard = testMenu.findItem(R.id.dir_menu_paste_from_clipboard);
        dirCreateDir = testMenu.findItem(R.id.dir_menu_create_dir);
        dirSelectAll = testMenu.findItem(R.id.dir_menu_select_all);
        dirRename = testMenu.findItem(R.id.dir_menu_rename);
        dirDelete = testMenu.findItem(R.id.dir_menu_delete);
        dirViewInOwner = testMenu.findItem(R.id.dir_menu_view_in_owner);
        dirOpenInNewWindow = testMenu.findItem(R.id.dir_menu_open_in_new_window);
        dirPasteIntoFolder = testMenu.findItem(R.id.dir_menu_paste_into_folder);

        rootEjectRoot = testMenu.findItem(R.id.root_menu_eject_root);
        rootOpenInNewWindow = testMenu.findItem(R.id.root_menu_open_in_new_window);
        rootPasteIntoFolder = testMenu.findItem(R.id.root_menu_paste_into_folder);
        rootSettings = testMenu.findItem(R.id.root_menu_settings);

        actionModeOpen = testMenu.findItem(R.id.action_menu_open);
        actionModeOpenWith = testMenu.findItem(R.id.action_menu_open_with);
        actionModeShare = testMenu.findItem(R.id.action_menu_share);
        actionModeDelete = testMenu.findItem(R.id.action_menu_delete);
        actionModeSelectAll = testMenu.findItem(R.id.action_menu_select_all);
        actionModeCopyTo = testMenu.findItem(R.id.action_menu_copy_to);
        actionModeExtractTo = testMenu.findItem(R.id.action_menu_extract_to);
        actionModeMoveTo = testMenu.findItem(R.id.action_menu_move_to);
        actionModeCompress = testMenu.findItem(R.id.action_menu_compress);
        actionModeRename = testMenu.findItem(R.id.action_menu_rename);
        actionModeInspector = testMenu.findItem(R.id.action_menu_inspector);
        actionModeViewInOwner = testMenu.findItem(R.id.action_menu_view_in_owner);

        optionSearch = testMenu.findItem(R.id.option_menu_search);
        optionDebug = testMenu.findItem(R.id.option_menu_debug);
        optionGrid = testMenu.findItem(R.id.option_menu_grid);
        optionList = testMenu.findItem(R.id.option_menu_list);
        optionNewWindow = testMenu.findItem(R.id.option_menu_new_window);
        optionCreateDir = testMenu.findItem(R.id.option_menu_create_dir);
        optionSelectAll = testMenu.findItem(R.id.option_menu_select_all);
        optionAdvanced = testMenu.findItem(R.id.option_menu_advanced);
        optionSettings = testMenu.findItem(R.id.option_menu_settings);

        features = new TestFeatures();

        // These items by default are visible
        testMenu.findItem(R.id.dir_menu_select_all).setVisible(true);
        testMenu.findItem(R.id.option_menu_select_all).setVisible(true);
        testMenu.findItem(R.id.option_menu_list).setVisible(true);

        selectionDetails = new TestSelectionDetails();
        dirDetails = new TestDirectoryDetails();
        testSearchManager = new TestSearchViewManager();
        preferences = new TestScopedPreferences();
        selectionManager = SelectionManagers.createTestInstance(TestData.create(1));
        selectionManager.toggleSelection("0");

        mgr = new MenuManager(
                features,
                testSearchManager,
                state,
                dirDetails,
                activity,
                selectionManager,
                this::getApplicationNameFromAuthority,
                this::getUriFromModelId);

        testRootInfo = new RootInfo();
        testDocInfo = new DocumentInfo();
    }

    private Uri getUriFromModelId(String id) {
        return Uri.EMPTY;
    }
    private String getApplicationNameFromAuthority(String authority) {
        return "TestApp";
    }

    @Test
    public void testActionMenu() {
        selectionDetails.canDelete = true;
        selectionDetails.canRename = true;
        dirDetails.canCreateDoc = true;

        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeRename.assertEnabled();
        actionModeDelete.assertVisible();
        actionModeShare.assertVisible();
        actionModeCopyTo.assertEnabled();
        actionModeCompress.assertEnabled();
        actionModeExtractTo.assertInvisible();
        actionModeMoveTo.assertEnabled();
        actionModeViewInOwner.assertInvisible();
    }

    @Test
    public void testActionMenu_ContainsPartial() {
        selectionDetails.containPartial = true;
        dirDetails.canCreateDoc = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeRename.assertDisabled();
        actionModeShare.assertInvisible();
        actionModeCopyTo.assertDisabled();
        actionModeCompress.assertDisabled();
        actionModeExtractTo.assertDisabled();
        actionModeMoveTo.assertDisabled();
        actionModeViewInOwner.assertInvisible();
    }

    @Test
    public void testActionMenu_CreateArchives_ReflectsFeatureState() {
        features.archiveCreation = false;
        dirDetails.canCreateDoc = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeCompress.assertInvisible();
        actionModeCompress.assertDisabled();
    }

    @Test
    public void testActionMenu_CreateArchive() {
        dirDetails.canCreateDoc = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeCompress.assertEnabled();
    }

    @Test
    public void testActionMenu_NoCreateArchive() {
        dirDetails.canCreateDoc = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeCompress.assertDisabled();
    }

    @Test
    public void testActionMenu_cantRename() {
        selectionDetails.canRename = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeRename.assertDisabled();
    }

    @Test
    public void testActionMenu_cantDelete() {
        selectionDetails.canDelete = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeDelete.assertInvisible();
        // We shouldn't be able to move files if we can't delete them
        actionModeMoveTo.assertDisabled();
    }

    @Test
    public void testActionsMenu_canViewInOwner() {
        selectionDetails.canViewInOwner = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeViewInOwner.assertVisible();
    }

    @Test
    public void testActionMenu_changeToCanDelete() {
        selectionDetails.canDelete = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        selectionDetails.canDelete = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeDelete.assertVisible();
        actionModeDelete.assertEnabled();
        actionModeMoveTo.assertVisible();
        actionModeMoveTo.assertEnabled();
    }

    @Test
    public void testActionMenu_ContainsDirectory() {
        selectionDetails.containDirectories = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        // We can't share directories
        actionModeShare.assertInvisible();
    }

    @Test
    public void testActionMenu_RemovesDirectory() {
        selectionDetails.containDirectories = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        selectionDetails.containDirectories = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeShare.assertVisible();
        actionModeShare.assertEnabled();
    }

    @Test
    public void testActionMenu_CantExtract() {
        selectionDetails.canExtract = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeExtractTo.assertInvisible();
    }

    @Test
    public void testActionMenu_CanExtract_hidesCopyToAndCompressAndShare() {
        features.archiveCreation = true;
        selectionDetails.canExtract = true;
        dirDetails.canCreateDoc = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeExtractTo.assertEnabled();
        actionModeCopyTo.assertDisabled();
        actionModeCompress.assertDisabled();
    }

    @Test
    public void testActionMenu_CanOpenWith() {
        selectionDetails.canOpenWith = true;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeOpenWith.assertVisible();
        actionModeOpenWith.assertEnabled();
    }

    @Test
    public void testActionMenu_NoOpenWith() {
        selectionDetails.canOpenWith = false;
        mgr.updateActionMenu(testMenu, selectionDetails);

        actionModeOpenWith.assertVisible();
        actionModeOpenWith.assertDisabled();
    }

    @Test
    public void testOptionMenu() {
        mgr.updateOptionMenu(testMenu);

        optionAdvanced.assertInvisible();
        optionAdvanced.assertTitle(R.string.menu_advanced_show);
        optionCreateDir.assertDisabled();
        optionDebug.assertInvisible();
        assertTrue(testSearchManager.updateMenuCalled());
    }

    @Test
    public void testOptionMenu_ShowAdvanced() {
        state.showAdvanced = true;
        state.showDeviceStorageOption = true;
        mgr.updateOptionMenu(testMenu);

        optionAdvanced.assertVisible();
        optionAdvanced.assertTitle(R.string.menu_advanced_hide);
    }

    @Test
    public void testOptionMenu_CanCreateDirectory() {
        dirDetails.canCreateDirectory = true;
        mgr.updateOptionMenu(testMenu);

        optionCreateDir.assertEnabled();
    }

    @Test
    public void testOptionMenu_HasRootSettings() {
        dirDetails.hasRootSettings = true;
        mgr.updateOptionMenu(testMenu);

        optionSettings.assertVisible();
    }

    @Test
    public void testInflateContextMenu_Files() {
        TestMenuInflater inflater = new TestMenuInflater();

        selectionDetails.containFiles = true;
        selectionDetails.containDirectories = false;
        mgr.inflateContextMenuForDocs(testMenu, inflater, selectionDetails);

        assertEquals(R.menu.file_context_menu, inflater.lastInflatedMenuId);
    }

    @Test
    public void testInflateContextMenu_Dirs() {
        TestMenuInflater inflater = new TestMenuInflater();

        selectionDetails.containFiles = false;
        selectionDetails.containDirectories = true;
        mgr.inflateContextMenuForDocs(testMenu, inflater, selectionDetails);

        assertEquals(R.menu.dir_context_menu, inflater.lastInflatedMenuId);
    }

    @Test
    public void testInflateContextMenu_Mixed() {
        TestMenuInflater inflater = new TestMenuInflater();

        selectionDetails.containFiles = true;
        selectionDetails.containDirectories = true;
        mgr.inflateContextMenuForDocs(testMenu, inflater, selectionDetails);

        assertEquals(R.menu.mixed_context_menu, inflater.lastInflatedMenuId);
    }

    @Test
    public void testContextMenu_EmptyArea() {
        mgr.updateContextMenuForContainer(testMenu);

        dirSelectAll.assertVisible();
        dirSelectAll.assertEnabled();
        dirPasteFromClipboard.assertVisible();
        dirPasteFromClipboard.assertDisabled();
        dirCreateDir.assertVisible();
        dirCreateDir.assertDisabled();
    }

    @Test
    public void testContextMenu_EmptyArea_NoItemToPaste() {
        dirDetails.hasItemsToPaste = false;
        dirDetails.canCreateDoc = true;

        mgr.updateContextMenuForContainer(testMenu);

        dirSelectAll.assertVisible();
        dirSelectAll.assertEnabled();
        dirPasteFromClipboard.assertVisible();
        dirPasteFromClipboard.assertDisabled();
        dirCreateDir.assertVisible();
        dirCreateDir.assertDisabled();
    }

    @Test
    public void testContextMenu_EmptyArea_CantCreateDoc() {
        dirDetails.hasItemsToPaste = true;
        dirDetails.canCreateDoc = false;

        mgr.updateContextMenuForContainer(testMenu);

        dirSelectAll.assertVisible();
        dirSelectAll.assertEnabled();
        dirPasteFromClipboard.assertVisible();
        dirPasteFromClipboard.assertDisabled();
        dirCreateDir.assertVisible();
        dirCreateDir.assertDisabled();
    }

    @Test
    public void testContextMenu_EmptyArea_CanPaste() {
        dirDetails.hasItemsToPaste = true;
        dirDetails.canCreateDoc = true;

        mgr.updateContextMenuForContainer(testMenu);

        dirSelectAll.assertVisible();
        dirSelectAll.assertEnabled();
        dirPasteFromClipboard.assertVisible();
        dirPasteFromClipboard.assertEnabled();
        dirCreateDir.assertVisible();
        dirCreateDir.assertDisabled();
    }

    @Test
    public void testContextMenu_EmptyArea_CanCreateDirectory() {
        dirDetails.canCreateDirectory = true;

        mgr.updateContextMenuForContainer(testMenu);

        dirSelectAll.assertVisible();
        dirSelectAll.assertEnabled();
        dirPasteFromClipboard.assertVisible();
        dirPasteFromClipboard.assertDisabled();
        dirCreateDir.assertVisible();
        dirCreateDir.assertEnabled();
    }

    @Test
    public void testContextMenu_OnFile() {
        selectionDetails.size = 1;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        dirOpen.assertVisible();
        dirOpen.assertEnabled();
        dirCutToClipboard.assertVisible();
        dirCopyToClipboard.assertVisible();
        dirRename.assertVisible();
        dirCreateDir.assertVisible();
        dirDelete.assertVisible();
    }

    @Test
    public void testContextMenu_OnFile_CanOpenWith() {
        selectionDetails.canOpenWith = true;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        dirOpenWith.assertVisible();
        dirOpenWith.assertEnabled();
    }

    @Test
    public void testContextMenu_OnFile_NoOpenWith() {
        selectionDetails.canOpenWith = false;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        dirOpenWith.assertVisible();
        dirOpenWith.assertDisabled();
    }

    @Test
    public void testContextMenu_OnMultipleFiles() {
        selectionDetails.size = 3;
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        dirOpen.assertVisible();
        dirOpen.assertDisabled();
    }

    @Test
    public void testContextMenu_OnWritableDirectory() {
        selectionDetails.size = 1;
        selectionDetails.canPasteInto = true;
        dirDetails.hasItemsToPaste = true;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        dirOpenInNewWindow.assertVisible();
        dirOpenInNewWindow.assertEnabled();
        dirCutToClipboard.assertVisible();
        dirCopyToClipboard.assertVisible();
        dirPasteIntoFolder.assertVisible();
        dirPasteIntoFolder.assertEnabled();
        dirRename.assertVisible();
        dirDelete.assertVisible();
    }

    @Test
    public void testContextMenu_OnNonWritableDirectory() {
        selectionDetails.size = 1;
        selectionDetails.canPasteInto = false;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        dirOpenInNewWindow.assertVisible();
        dirOpenInNewWindow.assertEnabled();
        dirCutToClipboard.assertVisible();
        dirCopyToClipboard.assertVisible();
        dirPasteIntoFolder.assertVisible();
        dirPasteIntoFolder.assertDisabled();
        dirRename.assertVisible();
        dirDelete.assertVisible();
    }

    @Test
    public void testContextMenu_OnWritableDirectory_NothingToPaste() {
        selectionDetails.canPasteInto = true;
        selectionDetails.size = 1;
        dirDetails.hasItemsToPaste = false;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        dirPasteIntoFolder.assertVisible();
        dirPasteIntoFolder.assertDisabled();
    }

    @Test
    public void testContextMenu_OnMultipleDirectories() {
        selectionDetails.size = 3;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        dirOpenInNewWindow.assertVisible();
        dirOpenInNewWindow.assertDisabled();
    }

    @Test
    public void testContextMenu_OnMixedDocs() {
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.canDelete = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        dirCutToClipboard.assertVisible();
        dirCutToClipboard.assertEnabled();
        dirCopyToClipboard.assertVisible();
        dirCopyToClipboard.assertEnabled();
        dirDelete.assertVisible();
        dirDelete.assertEnabled();
    }

    @Test
    public void testContextMenu_OnMixedDocs_hasPartialFile() {
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.containPartial = true;
        selectionDetails.canDelete = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        dirCutToClipboard.assertVisible();
        dirCutToClipboard.assertDisabled();
        dirCopyToClipboard.assertVisible();
        dirCopyToClipboard.assertDisabled();
        dirDelete.assertVisible();
        dirDelete.assertEnabled();
    }

    @Test
    public void testContextMenu_OnMixedDocs_hasUndeletableFile() {
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.canDelete = false;
        mgr.updateContextMenu(testMenu, selectionDetails);
        dirCutToClipboard.assertVisible();
        dirCutToClipboard.assertDisabled();
        dirCopyToClipboard.assertVisible();
        dirCopyToClipboard.assertEnabled();
        dirDelete.assertVisible();
        dirDelete.assertDisabled();
    }

    @Test
    public void testRootContextMenu() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_CREATE;

        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        rootEjectRoot.assertInvisible();

        rootOpenInNewWindow.assertVisible();
        rootOpenInNewWindow.assertEnabled();

        rootPasteIntoFolder.assertVisible();
        rootPasteIntoFolder.assertDisabled();

        rootSettings.assertVisible();
        rootSettings.assertDisabled();
    }

    @Test
    public void testRootContextMenu_HasRootSettings() {
        testRootInfo.flags = Root.FLAG_HAS_SETTINGS;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        rootSettings.assertEnabled();
    }

    @Test
    public void testRootContextMenu_NonWritableRoot() {
        dirDetails.hasItemsToPaste = true;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        rootPasteIntoFolder.assertVisible();
        rootPasteIntoFolder.assertDisabled();
    }

    @Test
    public void testRootContextMenu_NothingToPaste() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_CREATE;
        testDocInfo.flags = Document.FLAG_DIR_SUPPORTS_CREATE;
        dirDetails.hasItemsToPaste = false;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        rootPasteIntoFolder.assertVisible();
        rootPasteIntoFolder.assertDisabled();
    }

    @Test
    public void testRootContextMenu_PasteIntoWritableRoot() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_CREATE;
        testDocInfo.flags = Document.FLAG_DIR_SUPPORTS_CREATE;
        dirDetails.hasItemsToPaste = true;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        rootPasteIntoFolder.assertVisible();
        rootPasteIntoFolder.assertEnabled();
    }

    @Test
    public void testRootContextMenu_Eject() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_EJECT;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        rootEjectRoot.assertEnabled();
    }

    @Test
    public void testRootContextMenu_EjectInProcess() {
        testRootInfo.flags = Root.FLAG_SUPPORTS_EJECT;
        testRootInfo.ejecting = true;
        mgr.updateRootContextMenu(testMenu, testRootInfo, testDocInfo);

        rootEjectRoot.assertDisabled();
    }
}
