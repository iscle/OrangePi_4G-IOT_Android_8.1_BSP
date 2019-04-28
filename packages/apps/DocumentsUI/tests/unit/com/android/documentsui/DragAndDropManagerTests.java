/*
 * Copyright (C) 2017 The Android Open Source Project
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
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;

import android.content.ClipData;
import android.content.ClipDescription;
import android.graphics.drawable.Drawable;
import android.os.PersistableBundle;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;

import com.android.documentsui.DragAndDropManager.State;
import com.android.documentsui.DragAndDropManager.RuntimeDragAndDropManager;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperations;
import com.android.documentsui.testing.ClipDatas;
import com.android.documentsui.testing.KeyEvents;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestDocumentClipper;
import com.android.documentsui.testing.TestDrawable;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestEventListener;
import com.android.documentsui.testing.TestIconHelper;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.TestSelectionDetails;
import com.android.documentsui.testing.Views;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DragAndDropManagerTests {

    private static final String PLURAL_FORMAT = "%1$d items";

    private TestEnv mEnv;
    private TestActivity mActivity;
    private TestDragShadowBuilder mShadowBuilder;
    private View mStartDragView;
    private View mUpdateShadowView;
    private TestActionHandler mActions;

    private TestDocumentClipper mClipper;
    private TestSelectionDetails mDetails;
    private ClipData mClipData;

    private TestIconHelper mIconHelper;
    private Drawable mDefaultIcon;

    private TestEventListener<ClipData> mStartDragListener;
    private TestEventListener<Void> mShadowUpdateListener;
    private TestEventListener<Integer> mFlagListener;

    private TestEventListener<Integer> mCallbackListener;
    private FileOperations.Callback mCallback = new FileOperations.Callback() {
        @Override
        public void onOperationResult(@Status int status,
                @FileOperationService.OpType int opType, int docCount) {
            mCallbackListener.accept(status);
        }
    };

    private DragAndDropManager mManager;

    @Before
    public void setUp() {
        mEnv = TestEnv.create();
        mActivity = TestActivity.create(mEnv);
        mActivity.resources.plurals.put(R.plurals.elements_dragged, PLURAL_FORMAT);

        mShadowBuilder = TestDragShadowBuilder.create();

        mStartDragView = Views.createTestView();
        mUpdateShadowView = Views.createTestView();

        mActions = new TestActionHandler(mEnv);

        mClipper = new TestDocumentClipper();
        mDetails = new TestSelectionDetails();
        mDetails.canDelete = true;
        ClipDescription description = new ClipDescription("", new String[]{});
        description.setExtras(new PersistableBundle());
        mClipData = ClipDatas.createTestClipData(description);
        mClipper.nextClip = mClipData;

        mDefaultIcon = new TestDrawable();
        mIconHelper = TestIconHelper.create();
        mIconHelper.nextDocumentIcon = new TestDrawable();

        mStartDragListener = new TestEventListener<>();
        mShadowUpdateListener = new TestEventListener<>();
        mCallbackListener = new TestEventListener<>();
        mFlagListener = new TestEventListener<>();

        mManager = new RuntimeDragAndDropManager(mActivity, mClipper, mShadowBuilder,
                mDefaultIcon) {
            @Override
            void startDragAndDrop(View v, ClipData clipData, DragShadowBuilder builder,
                    Object localState, int flag) {
                assertSame(mStartDragView, v);
                assertSame(mShadowBuilder, builder);
                assertNotNull(localState);

                mFlagListener.accept(flag);
                mStartDragListener.accept(clipData);
            }

            @Override
            void updateDragShadow(View v) {
                assertSame(mUpdateShadowView, v);

                mShadowUpdateListener.accept(null);
            }
        };
    }

    @Test
    public void testStartDrag_SetsCorrectClipData() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mStartDragListener.assertLastArgument(mClipper.nextClip);
    }

    @Test
    public void testStartDrag_SetsCorrectClipData_NullParent() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                null);

        mStartDragListener.assertLastArgument(mClipper.nextClip);
    }

    @Test
    public void testStartDrag_BuildsCorrectShadow_SingleDoc() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mShadowBuilder.title.assertLastArgument(TestEnv.FILE_APK.displayName);
        mShadowBuilder.icon.assertLastArgument(mIconHelper.nextDocumentIcon);
    }

    @Test
    public void testStartDrag_BuildsCorrectShadow_MultipleDocs() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mShadowBuilder.title.assertLastArgument(mActivity.getResources().getQuantityString(
                R.plurals.elements_dragged, 2, 2));
        mShadowBuilder.icon.assertLastArgument(mDefaultIcon);
    }

    @Test
    public void testCanSpringOpen_ReturnsFalse_RootNotSupportCreate() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FOLDER_1, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FOLDER_1.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        assertFalse(mManager.canSpringOpen(TestProvidersAccess.HAMMY, TestEnv.FOLDER_2));
    }

    @Test
    public void testInArchiveUris_HasCorrectFlagPermission() {
        mDetails.containsFilesInArchive = true;
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_IN_ARCHIVE),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FILE_ARCHIVE.derivedUri, TestEnv.FILE_IN_ARCHIVE.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FILE_ARCHIVE);

        mFlagListener.assertLastArgument(View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_OPAQUE);
    }

    @Test
    public void testCanSpringOpen_ReturnsFalse_DocIsInvalidDestination() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FOLDER_1, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FOLDER_1.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        assertFalse(mManager.canSpringOpen(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1));
    }

    @Test
    public void testCanSpringOpen() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FOLDER_1, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FOLDER_1.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        assertTrue(mManager.canSpringOpen(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_2));
    }

    @Test
    public void testDefaultToUnknownState() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FOLDER_1, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FOLDER_1.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mShadowBuilder.state.assertLastArgument(DragAndDropManager.STATE_UNKNOWN);
    }

    @Test
    public void testUpdateStateToNotAllowed() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mManager.updateStateToNotAllowed(mUpdateShadowView);

        assertStateUpdated(DragAndDropManager.STATE_NOT_ALLOWED);
    }

    @Test
    public void testUpdateState_UpdatesToNotAllowed_RootNotSupportCreate() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.HAMMY, TestEnv.FOLDER_2);

        assertEquals(DragAndDropManager.STATE_NOT_ALLOWED, state);
        assertStateUpdated(DragAndDropManager.STATE_NOT_ALLOWED);
    }

    @Test
    public void testUpdateState_UpdatesToUnknown_RootDocIsNull() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, null);

        assertEquals(DragAndDropManager.STATE_UNKNOWN, state);
        assertStateUpdated(DragAndDropManager.STATE_UNKNOWN);
    }

    @Test
    public void testUpdateState_UpdatesToMove_SameRoot() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_MOVE, state);
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    @Test
    public void testUpdateState_UpdatesToCopy_DifferentRoot() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_COPY, state);
        assertStateUpdated(DragAndDropManager.STATE_COPY);
    }

    @Test
    public void testUpdateState_UpdatesToCopy_SameRoot_LeftCtrlPressed() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_COPY, state);
        assertStateUpdated(DragAndDropManager.STATE_COPY);
    }

    @Test
    public void testUpdateState_UpdatesToCopy_SameRoot_RightCtrlPressed() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        KeyEvent event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_COPY, state);
        assertStateUpdated(DragAndDropManager.STATE_COPY);
    }

    @Test
    public void testUpdateState_UpdatesToMove_DifferentRoot_LeftCtrlPressed() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_MOVE, state);
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    @Test
    public void testUpdateState_UpdatesToMove_DifferentRoot_RightCtrlPressed() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        KeyEvent event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_MOVE, state);
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    @Test
    public void testUpdateState_UpdatesToMove_SameRoot_LeftCtrlReleased() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_UP);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_MOVE, state);
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    @Test
    public void testUpdateState_UpdatesToMove_SameRoot_RightCtrlReleased() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        KeyEvent event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_UP);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_MOVE, state);
        assertStateUpdated(DragAndDropManager.STATE_MOVE);
    }

    @Test
    public void testUpdateState_UpdatesToCopy_DifferentRoot_LeftCtrlReleased() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_UP);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_COPY, state);
        assertStateUpdated(DragAndDropManager.STATE_COPY);
    }

    @Test
    public void testUpdateState_UpdatesToCopy_DifferentRoot_RightCtrlReleased() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        KeyEvent event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        event = KeyEvents.createRightCtrlKey(KeyEvent.ACTION_UP);
        mManager.onKeyEvent(event);

        final @State int state = mManager.updateState(
                mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        assertEquals(DragAndDropManager.STATE_COPY, state);
        assertStateUpdated(DragAndDropManager.STATE_COPY);
    }

    @Test
    public void testResetState_UpdatesToUnknown() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mManager.updateStateToNotAllowed(mUpdateShadowView);

        mManager.resetState(mUpdateShadowView);

        assertStateUpdated(DragAndDropManager.STATE_UNKNOWN);
    }

    @Test
    public void testDrop_Rejects_RootNotSupportCreate_DropOnRoot() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.HAMMY, TestEnv.FOLDER_1);

        assertFalse(mManager.drop(
                mClipData, mManager, TestProvidersAccess.HAMMY, mActions, mCallback));
    }

    @Test
    public void testDrop_Rejects_InvalidRoot() {
        RootInfo root = new RootInfo();
        root.authority = TestProvidersAccess.HOME.authority;
        root.documentId = TestEnv.FOLDER_0.documentId;

        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                root,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.HOME, TestEnv.FOLDER_0);

        assertFalse(mManager.drop(mClipData, mManager, root, mActions, mCallback));
    }

    @Test
    public void testDrop_Fails_NotGetRootDoc() throws Exception {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        mManager.drop(
                mClipData, mManager, TestProvidersAccess.DOWNLOADS, mActions, mCallback);

        mEnv.beforeAsserts();
        mCallbackListener.assertLastArgument(FileOperations.Callback.STATUS_FAILED);
    }

    @Test
    public void testDrop_Copies_DifferentRoot_DropOnRoot() throws Exception {
        mActions.nextRootDocument = TestEnv.FOLDER_1;

        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        mManager.drop(
                mClipData, mManager, TestProvidersAccess.DOWNLOADS, mActions, mCallback);

        mEnv.beforeAsserts();
        final DocumentStack expect =
                new DocumentStack(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);
        mClipper.copyFromClip.assertLastArgument(Pair.create(expect, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_COPY);
    }

    @Test
    public void testDrop_Moves_SameRoot_DropOnRoot() throws Exception {
        mActions.nextRootDocument = TestEnv.FOLDER_1;

        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        mManager.drop(
                mClipData, mManager, TestProvidersAccess.DOWNLOADS, mActions, mCallback);

        mEnv.beforeAsserts();
        final DocumentStack expect =
                new DocumentStack(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);
        mClipper.copyFromClip.assertLastArgument(Pair.create(expect, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_MOVE);
    }

    @Test
    public void testDrop_Copies_SameRoot_DropOnRoot_ReleasesCtrlBeforeGettingRootDocument()
            throws Exception{
        mActions.nextRootDocument = TestEnv.FOLDER_1;

        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        KeyEvent event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_DOWN);
        mManager.onKeyEvent(event);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);

        mManager.drop(
                mClipData, mManager, TestProvidersAccess.DOWNLOADS, mActions, mCallback);

        event = KeyEvents.createLeftCtrlKey(KeyEvent.ACTION_UP);
        mManager.onKeyEvent(event);

        mEnv.beforeAsserts();
        final DocumentStack expect =
                new DocumentStack(TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1);
        mClipper.copyFromClip.assertLastArgument(Pair.create(expect, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_COPY);
    }

    @Test
    public void testDrop_Rejects_RootNotSupportCreate_DropOnDocument() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.HAMMY, TestEnv.FOLDER_2);

        final DocumentStack stack = new DocumentStack(
                TestProvidersAccess.HAMMY, TestEnv.FOLDER_1, TestEnv.FOLDER_2);
        assertFalse(mManager.drop(mClipData, mManager, stack, mCallback));
    }

    @Test
    public void testDrop_Copies_DifferentRoot_DropOnDocument() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_2);

        final DocumentStack stack = new DocumentStack(
                TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1, TestEnv.FOLDER_2);
        assertTrue(mManager.drop(mClipData, mManager, stack, mCallback));

        mClipper.copyFromClip.assertLastArgument(Pair.create(stack, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_COPY);
    }

    @Test
    public void testDrop_Moves_SameRoot_DropOnDocument() {
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_APK, TestEnv.FILE_JPG),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_APK.derivedUri,
                        TestEnv.FILE_JPG.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_2);

        final DocumentStack stack = new DocumentStack(
                TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1, TestEnv.FOLDER_2);
        assertTrue(mManager.drop(mClipData, mManager, stack, mCallback));

        mClipper.copyFromClip.assertLastArgument(Pair.create(stack, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_MOVE);
    }

    @Test
    public void testDrop_Copies_SameRoot_ReadOnlyFile_DropOnDocument() {
        mDetails.canDelete = false;
        mManager.startDrag(
                mStartDragView,
                Arrays.asList(TestEnv.FILE_READ_ONLY),
                TestProvidersAccess.DOWNLOADS,
                Arrays.asList(TestEnv.FOLDER_0.derivedUri, TestEnv.FILE_READ_ONLY.derivedUri),
                mDetails,
                mIconHelper,
                TestEnv.FOLDER_0);

        mManager.updateState(mUpdateShadowView, TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_2);

        final DocumentStack stack = new DocumentStack(
                TestProvidersAccess.DOWNLOADS, TestEnv.FOLDER_1, TestEnv.FOLDER_2);
        assertTrue(mManager.drop(mClipData, mManager, stack, mCallback));

        mClipper.copyFromClip.assertLastArgument(Pair.create(stack, mClipData));
        mClipper.opType.assertLastArgument(FileOperationService.OPERATION_COPY);
    }

    private void assertStateUpdated(@State int expected) {
        mShadowBuilder.state.assertLastArgument(expected);
        mShadowUpdateListener.assertCalled();
    }

    public static class TestDragShadowBuilder extends DragShadowBuilder {

        public TestEventListener<String> title;
        public TestEventListener<Drawable> icon;
        public TestEventListener<Integer> state;

        private TestDragShadowBuilder() {
            super(null);
        }

        @Override
        void updateTitle(String title) {
            this.title.accept(title);
        }

        @Override
        void updateIcon(Drawable icon) {
            this.icon.accept(icon);
        }

        @Override
        void onStateUpdated(@State int state) {
            this.state.accept(state);
        }

        public static TestDragShadowBuilder create() {
            TestDragShadowBuilder builder =
                    Mockito.mock(TestDragShadowBuilder.class, Mockito.CALLS_REAL_METHODS);

            builder.title = new TestEventListener<>();
            builder.icon = new TestEventListener<>();
            builder.state = new TestEventListener<>();

            return builder;
        }
    }
}
