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

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;

import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Path;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.dirlist.DocumentDetails;
import com.android.documentsui.files.LauncherActivity;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortModel;
import com.android.documentsui.testing.DocumentStackAsserts;
import com.android.documentsui.testing.Roots;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestEventHandler;
import com.android.documentsui.testing.TestProvidersAccess;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * A unit test *for* AbstractActionHandler, not an abstract test baseclass.
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class AbstractActionHandlerTest {

    private TestActivity mActivity;
    private TestEnv mEnv;
    private AbstractActionHandler<TestActivity> mHandler;

    @Before
    public void setUp() {
        mEnv = TestEnv.create();
        mActivity = TestActivity.create(mEnv);
        mHandler = new AbstractActionHandler<TestActivity>(
                mActivity,
                mEnv.state,
                mEnv.providers,
                mEnv.docs,
                mEnv.searchViewManager,
                mEnv::lookupExecutor,
                mEnv.injector) {

            @Override
            public void openRoot(RootInfo root) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean openDocument(DocumentDetails doc, @ViewType int type,
                    @ViewType int fallback) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void initLocation(Intent intent) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void launchToDefaultLocation() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    public void testOpenNewWindow() {
        DocumentStack path = new DocumentStack(Roots.create("123"));
        mHandler.openInNewWindow(path);

        Intent expected = LauncherActivity.createLaunchIntent(mActivity);
        expected.putExtra(Shared.EXTRA_STACK, (Parcelable) path);
        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    public void testOpensContainerDocuments_jumpToNewLocation() throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.populateStack();

        mEnv.searchViewManager.isSearching = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(TestEnv.FOLDER_1.documentId, TestEnv.FOLDER_2.documentId));
        mEnv.docs.nextDocuments = Arrays.asList(TestEnv.FOLDER_1, TestEnv.FOLDER_2);

        mHandler.openContainerDocument(TestEnv.FOLDER_2);

        mEnv.beforeAsserts();

        assertEquals(mEnv.docs.nextPath.getPath().size(), mEnv.state.stack.size());
        assertEquals(TestEnv.FOLDER_2, mEnv.state.stack.peek());
    }


    @Test
    public void testOpensContainerDocuments_pushToRootDoc_NoFindPathSupport() throws Exception {
        mEnv.populateStack();

        mEnv.searchViewManager.isSearching = true;
        mEnv.docs.nextDocuments = Arrays.asList(TestEnv.FOLDER_1, TestEnv.FOLDER_2);

        mHandler.openContainerDocument(TestEnv.FOLDER_2);

        mEnv.beforeAsserts();

        assertEquals(2, mEnv.state.stack.size());
        assertEquals(TestEnv.FOLDER_2, mEnv.state.stack.pop());
        assertEquals(TestEnv.FOLDER_0, mEnv.state.stack.pop());
    }

    @Test
    public void testOpensDocument_AssertionErrorIfAlreadyInStack() throws Exception {
        mEnv.populateStack();
        boolean threw = false;
        try {
            mEnv.state.stack.push(TestEnv.FOLDER_0);
        } catch (AssertionError e) {
            threw = true;
        }
        assertTrue(threw);
    }

    @Test
    public void testLaunchToDocuments() throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(
                        TestEnv.FOLDER_0.documentId,
                        TestEnv.FOLDER_1.documentId,
                        TestEnv.FILE_GIF.documentId));
        mEnv.docs.nextDocuments =
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1, TestEnv.FILE_GIF);

        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();
        assertTrue(mHandler.launchToDocument(TestEnv.FILE_GIF.derivedUri));

        mEnv.beforeAsserts();

        DocumentStackAsserts.assertEqualsTo(mEnv.state.stack, TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1));
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testLaunchToDocuments_convertsTreeUriToDocumentUri() throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(
                        TestEnv.FOLDER_0.documentId,
                        TestEnv.FOLDER_1.documentId,
                        TestEnv.FILE_GIF.documentId));
        mEnv.docs.nextDocuments =
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1, TestEnv.FILE_GIF);

        final Uri treeBaseUri = DocumentsContract.buildTreeDocumentUri(
                TestProvidersAccess.HOME.authority, TestEnv.FOLDER_0.documentId);
        final Uri treeDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeBaseUri, TestEnv.FILE_GIF.documentId);
        assertTrue(mHandler.launchToDocument(treeDocUri));

        mEnv.beforeAsserts();

        DocumentStackAsserts.assertEqualsTo(mEnv.state.stack, TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1));
        mEnv.docs.lastUri.assertLastArgument(TestEnv.FILE_GIF.derivedUri);
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testLoadChildrenDocuments() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_0);

        mEnv.state.sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_ASCENDING);

        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority)
                .setNextChildDocumentsReturns(TestEnv.FILE_APK, TestEnv.FILE_GIF);

        mHandler.loadDocumentsForCurrentStack();
        mActivity.loaderManager.runAsyncTaskLoader(AbstractActionHandler.LOADER_ID);

        assertEquals(2, mEnv.model.getItemCount());
        String[] modelIds = mEnv.model.getModelIds();
        assertEquals(TestEnv.FILE_APK, mEnv.model.getDocument(modelIds[0]));
        assertEquals(TestEnv.FILE_GIF, mEnv.model.getDocument(modelIds[1]));
    }

    @Test
    public void testLoadChildrenDocuments_failsWithNonRecentsAndEmptyStack() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);

        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority)
                .setNextChildDocumentsReturns(TestEnv.FILE_APK, TestEnv.FILE_GIF);

        TestEventHandler<Model.Update> listener = new TestEventHandler<>();
        mEnv.model.addUpdateListener(listener::accept);

        mHandler.loadDocumentsForCurrentStack();

        assertTrue(listener.getLastValue().hasException());
    }
}
