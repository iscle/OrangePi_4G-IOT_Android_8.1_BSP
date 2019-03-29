/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.documentclient;

import static org.junit.Assert.assertNotEquals;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Path;
import android.provider.DocumentsProvider;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.test.MoreAsserts;
import android.util.Log;

import com.android.cts.documentclient.MyActivity.Result;

import java.util.List;

/**
 * Tests for {@link DocumentsProvider} and interaction with platform intents
 * like {@link Intent#ACTION_OPEN_DOCUMENT}.
 */
public class DocumentsClientTest extends DocumentsClientTestCase {
    private static final String TAG = "DocumentsClientTest";

    private UiSelector findRootListSelector() throws UiObjectNotFoundException {
        return new UiSelector().resourceId(
                "com.android.documentsui:id/container_roots").childSelector(
                new UiSelector().resourceId("com.android.documentsui:id/roots_list"));

    }

    private void revealRoot(UiSelector rootsList, String label) throws UiObjectNotFoundException {
        // We might need to expand drawer if not visible
        if (!new UiObject(rootsList).waitForExists(TIMEOUT)) {
            Log.d(TAG, "Failed to find roots list; trying to expand");
            final UiSelector hamburger = new UiSelector().resourceId(
                    "com.android.documentsui:id/toolbar").childSelector(
                    new UiSelector().className("android.widget.ImageButton").clickable(true));
            new UiObject(hamburger).click();
        }

        // Wait for the first list item to appear
        assertTrue("First list item",
                new UiObject(rootsList.childSelector(new UiSelector())).waitForExists(TIMEOUT));

        // Now scroll around to find our item
        new UiScrollable(rootsList).scrollIntoView(new UiSelector().text(label));
    }

    private UiObject findRoot(String label) throws UiObjectNotFoundException {
        final UiSelector rootsList = findRootListSelector();
        revealRoot(rootsList, label);

        return new UiObject(rootsList.childSelector(new UiSelector().text(label)));
    }

    private UiObject findEjectIcon(String rootLabel) throws UiObjectNotFoundException {
        final UiSelector rootsList = findRootListSelector();
        revealRoot(rootsList, rootLabel);

        final UiScrollable rootsListObject = new UiScrollable(rootsList);
        final UiObject rootItem = rootsListObject.getChildByText(
                new UiSelector().className("android.widget.LinearLayout"), rootLabel, false);
        final UiSelector ejectIcon =
                new UiSelector().resourceId("com.android.documentsui:id/eject_icon");
        return new UiObject(rootItem.getSelector().childSelector(ejectIcon));
    }

    private UiObject findDocument(String label) throws UiObjectNotFoundException {
        final UiSelector docList = new UiSelector().resourceId(
                "com.android.documentsui:id/container_directory").childSelector(
                new UiSelector().resourceId("com.android.documentsui:id/dir_list"));

        // Wait for the first list item to appear
        assertTrue("First list item",
                new UiObject(docList.childSelector(new UiSelector())).waitForExists(TIMEOUT));

        // Now scroll around to find our item
        new UiScrollable(docList).scrollIntoView(new UiSelector().text(label));
        return new UiObject(docList.childSelector(new UiSelector().text(label)));
    }

    private UiObject findSaveButton() throws UiObjectNotFoundException {
        return new UiObject(new UiSelector().resourceId("com.android.documentsui:id/container_save")
                .childSelector(new UiSelector().resourceId("android:id/button1")));
    }

    private UiObject findPositiveButton() throws UiObjectNotFoundException {
        return new UiObject(new UiSelector().resourceId("android:id/button1"));
    }

    public void testOpenSimple() throws Exception {
        if (!supportedHardware()) return;

        try {
            // Opening without permission should fail
            readFully(Uri.parse("content://com.android.cts.documentprovider/document/doc:file1"));
            fail("Able to read data before opened!");
        } catch (SecurityException expected) {
        }

        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Ensure that we see both of our roots
        mDevice.waitForIdle();
        assertTrue("CtsLocal root", findRoot("CtsLocal").exists());
        assertTrue("CtsCreate root", findRoot("CtsCreate").exists());
        assertFalse("CtsGetContent root", findRoot("CtsGetContent").exists());

        // Choose the local root.
        mDevice.waitForIdle();
        findRoot("CtsLocal").click();

        // Try picking a virtual file. Virtual files must not be returned for CATEGORY_OPENABLE
        // though, so the click should be ignored.
        mDevice.waitForIdle();
        findDocument("VIRTUAL_FILE").click();
        mDevice.waitForIdle();

        // Pick a regular file.
        mDevice.waitForIdle();
        findDocument("FILE1").click();

        // Confirm that the returned file is a regular file caused by the second click.
        final Result result = mActivity.getResult();
        final Uri uri = result.data.getData();
        assertEquals("doc:file1", DocumentsContract.getDocumentId(uri));

        // We should now have permission to read/write
        MoreAsserts.assertEquals("fileone".getBytes(), readFully(uri));

        writeFully(uri, "replaced!".getBytes());
        SystemClock.sleep(500);
        MoreAsserts.assertEquals("replaced!".getBytes(), readFully(uri));
    }

    public void testOpenVirtual() throws Exception {
        if (!supportedHardware()) return;

        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Pick a virtual file from the local root.
        mDevice.waitForIdle();
        findRoot("CtsLocal").click();

        mDevice.waitForIdle();
        findDocument("VIRTUAL_FILE").click();

        // Confirm that the returned file is actually the selected one.
        final Result result = mActivity.getResult();
        final Uri uri = result.data.getData();
        assertEquals("doc:virtual-file", DocumentsContract.getDocumentId(uri));

        final ContentResolver resolver = getInstrumentation().getContext().getContentResolver();
        final String streamTypes[] = resolver.getStreamTypes(uri, "*/*");
        assertEquals(1, streamTypes.length);
        assertEquals("text/plain", streamTypes[0]);

        // Virtual files are not readable unless an alternative MIME type is specified.
        try {
            readFully(uri);
            fail("Unexpected success in reading a virtual file. It should've failed.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }

        // However, they are readable using an alternative MIME type from getStreamTypes().
        MoreAsserts.assertEquals(
                "Converted contents.".getBytes(), readTypedFully(uri, streamTypes[0]));
    }

    public void testCreateNew() throws Exception {
        if (!supportedHardware()) return;

        final String DISPLAY_NAME = "My New Awesome Document Title";
        final String MIME_TYPE = "image/png";

        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, DISPLAY_NAME);
        intent.setType(MIME_TYPE);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        mDevice.waitForIdle();
        findRoot("CtsCreate").click();

        mDevice.waitForIdle();
        findSaveButton().click();

        final Result result = mActivity.getResult();
        final Uri uri = result.data.getData();

        writeFully(uri, "meow!".getBytes());

        assertEquals(DISPLAY_NAME, getColumn(uri, Document.COLUMN_DISPLAY_NAME));
        assertEquals(MIME_TYPE, getColumn(uri, Document.COLUMN_MIME_TYPE));
    }

    public void testCreateExisting() throws Exception {
        if (!supportedHardware()) return;

        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, "NEVERUSED");
        intent.setType("mime2/file2");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        mDevice.waitForIdle();
        findRoot("CtsCreate").click();

        // Pick file2, which should be selected since MIME matches, then try
        // picking a non-matching MIME, which should leave file2 selected.
        mDevice.waitForIdle();
        findDocument("FILE2").click();
        mDevice.waitForIdle();
        findDocument("FILE1").click();

        mDevice.waitForIdle();
        findSaveButton().click();

        mDevice.waitForIdle();
        findPositiveButton().click();

        final Result result = mActivity.getResult();
        final Uri uri = result.data.getData();

        MoreAsserts.assertEquals("filetwo".getBytes(), readFully(uri));
    }

    public void testTree() throws Exception {
        if (!supportedHardware()) return;

        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        mDevice.waitForIdle();
        findRoot("CtsCreate").click();

        mDevice.waitForIdle();
        findDocument("DIR2").click();
        mDevice.waitForIdle();
        findSaveButton().click();

        final Result result = mActivity.getResult();
        final Uri uri = result.data.getData();

        // We should have selected DIR2
        Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri,
                DocumentsContract.getTreeDocumentId(uri));
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(uri,
                DocumentsContract.getTreeDocumentId(uri));

        assertEquals("DIR2", getColumn(doc, Document.COLUMN_DISPLAY_NAME));

        // Look around and make sure we can see children
        final ContentResolver resolver = getInstrumentation().getContext().getContentResolver();
        Cursor cursor = resolver.query(children, new String[] {
                Document.COLUMN_DISPLAY_NAME }, null, null, null);
        try {
            assertEquals(2, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("FILE4", cursor.getString(0));
        } finally {
            cursor.close();
        }

        // Create some documents
        Uri pic = DocumentsContract.createDocument(resolver, doc, "image/png", "pic.png");
        Uri dir = DocumentsContract.createDocument(resolver, doc, Document.MIME_TYPE_DIR, "my dir");
        Uri dirPic = DocumentsContract.createDocument(resolver, dir, "image/png", "pic2.png");

        writeFully(pic, "pic".getBytes());
        writeFully(dirPic, "dirPic".getBytes());

        // Read then delete existing doc
        final Uri file4 = DocumentsContract.buildDocumentUriUsingTree(uri, "doc:file4");
        MoreAsserts.assertEquals("filefour".getBytes(), readFully(file4));
        assertTrue("delete", DocumentsContract.deleteDocument(resolver, file4));
        try {
            MoreAsserts.assertEquals("filefour".getBytes(), readFully(file4));
            fail("Expected file to be gone");
        } catch (SecurityException expected) {
        }

        // And rename something
        dirPic = DocumentsContract.renameDocument(resolver, dirPic, "wow");
        assertNotNull("rename", dirPic);

        // We should only see single child
        assertEquals("wow", getColumn(dirPic, Document.COLUMN_DISPLAY_NAME));
        MoreAsserts.assertEquals("dirPic".getBytes(), readFully(dirPic));

        try {
            // Make sure we can't see files outside selected dir
            getColumn(DocumentsContract.buildDocumentUriUsingTree(uri, "doc:file1"),
                    Document.COLUMN_DISPLAY_NAME);
            fail("Somehow read document outside tree!");
        } catch (SecurityException expected) {
        }
    }

    public void testGetContent() throws Exception {
        if (!supportedHardware()) return;

        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Look around, we should be able to see both DocumentsProviders and
        // other GET_CONTENT sources.
        mDevice.waitForIdle();
        assertTrue("CtsLocal root", findRoot("CtsLocal").exists());
        assertTrue("CtsCreate root", findRoot("CtsCreate").exists());
        assertTrue("CtsGetContent root", findRoot("CtsGetContent").exists());

        mDevice.waitForIdle();
        findRoot("CtsGetContent").click();

        final Result result = mActivity.getResult();
        assertEquals("ReSuLt", result.data.getAction());
    }

    public void testTransferDocument() throws Exception {
        if (!supportedHardware()) return;

        try {
            // Opening without permission should fail.
            readFully(Uri.parse("content://com.android.cts.documentprovider/document/doc:file1"));
            fail("Able to read data before opened!");
        } catch (SecurityException expected) {
        }

        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        mDevice.waitForIdle();
        findRoot("CtsCreate").click();

        findDocument("DIR2").click();
        mDevice.waitForIdle();
        findSaveButton().click();

        final Result result = mActivity.getResult();
        final Uri uri = result.data.getData();

        // We should have selected DIR2.
        final Uri docUri = DocumentsContract.buildDocumentUriUsingTree(uri,
                DocumentsContract.getTreeDocumentId(uri));

        assertEquals("DIR2", getColumn(docUri, Document.COLUMN_DISPLAY_NAME));

        final ContentResolver resolver = getInstrumentation().getContext().getContentResolver();
        final Cursor cursor = resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(
                        docUri, DocumentsContract.getDocumentId(docUri)),
                new String[] { Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
                        Document.COLUMN_FLAGS },
                null, null, null);

        Uri sourceFileUri = null;
        Uri targetDirUri = null;

        try {
            assertEquals(2, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            sourceFileUri = DocumentsContract.buildDocumentUriUsingTree(
                    docUri, cursor.getString(0));
            assertEquals("FILE4", cursor.getString(1));
            assertEquals(Document.FLAG_SUPPORTS_WRITE |
                    Document.FLAG_SUPPORTS_COPY |
                    Document.FLAG_SUPPORTS_MOVE |
                    Document.FLAG_SUPPORTS_REMOVE, cursor.getInt(2));

            assertTrue(cursor.moveToNext());
            targetDirUri = DocumentsContract.buildDocumentUriUsingTree(
                    docUri, cursor.getString(0));
            assertEquals("SUB_DIR2", cursor.getString(1));
        } finally {
            cursor.close();
        }

        // Move, copy then remove.
        final Uri movedFileUri = DocumentsContract.moveDocument(
                resolver, sourceFileUri, docUri, targetDirUri);
        assertTrue(movedFileUri != null);
        final Uri copiedFileUri = DocumentsContract.copyDocument(
                resolver, movedFileUri, targetDirUri);
        assertTrue(copiedFileUri != null);

        // Confirm that the files are at the destinations.
        Cursor cursorDst = resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(
                        targetDirUri, DocumentsContract.getDocumentId(targetDirUri)),
                new String[] { Document.COLUMN_DOCUMENT_ID },
                null, null, null);
        try {
            assertEquals(2, cursorDst.getCount());
            assertTrue(cursorDst.moveToFirst());
            assertEquals("doc:file4", cursorDst.getString(0));
            assertTrue(cursorDst.moveToNext());
            assertEquals("doc:file4_copy", cursorDst.getString(0));
        } finally {
            cursorDst.close();
        }

        // ... and gone from the source.
        final Cursor cursorSrc = resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(
                        docUri, DocumentsContract.getDocumentId(docUri)),
                new String[] { Document.COLUMN_DOCUMENT_ID },
                null, null, null);
        try {
            assertEquals(1, cursorSrc.getCount());
            assertTrue(cursorSrc.moveToFirst());
            assertEquals("doc:sub_dir2", cursorSrc.getString(0));
        } finally {
            cursorSrc.close();
        }

        assertTrue(DocumentsContract.removeDocument(resolver, movedFileUri, targetDirUri));
        assertTrue(DocumentsContract.removeDocument(resolver, copiedFileUri, targetDirUri));

        // Finally, confirm that removing actually removed the files from the destination.
        cursorDst = resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(
                        targetDirUri, DocumentsContract.getDocumentId(targetDirUri)),
                new String[] { Document.COLUMN_DOCUMENT_ID },
                null, null, null);
        try {
            assertEquals(0, cursorDst.getCount());
        } finally {
            cursorDst.close();
        }
    }

    public void testFindDocumentPathInScopedAccess() throws Exception {
        if (!supportedHardware()) return;

        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        mDevice.waitForIdle();
        findRoot("CtsCreate").click();

        mDevice.waitForIdle();
        findDocument("DIR2").click();
        mDevice.waitForIdle();
        findSaveButton().click();

        final Result result = mActivity.getResult();
        final Uri uri = result.data.getData();

        // We should have selected DIR2
        Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri,
                DocumentsContract.getTreeDocumentId(uri));

        assertEquals("DIR2", getColumn(doc, Document.COLUMN_DISPLAY_NAME));

        final ContentResolver resolver = getInstrumentation().getContext().getContentResolver();

        // Create some documents
        Uri dir = DocumentsContract.createDocument(resolver, doc, Document.MIME_TYPE_DIR, "my dir");
        Uri dirPic = DocumentsContract.createDocument(resolver, dir, "image/png", "pic2.png");

        writeFully(dirPic, "dirPic".getBytes());

        // Find the path of a document
        Path path = DocumentsContract.findDocumentPath(resolver, dirPic);
        assertNull(path.getRootId());

        final List<String> docs = path.getPath();
        assertEquals("Unexpected path: " + path, 3, docs.size());
        assertEquals(DocumentsContract.getTreeDocumentId(uri), docs.get(0));
        assertEquals(DocumentsContract.getDocumentId(dir), docs.get(1));
        assertEquals(DocumentsContract.getDocumentId(dirPic), docs.get(2));
    }

    public void testOpenDocumentAtInitialLocation() throws Exception {
        if (!supportedHardware()) return;

        // Clear DocsUI's storage to avoid it opening stored last location
        // which may make this test pass "luckily".
        clearDocumentsUi();

        final Uri docUri = DocumentsContract.buildDocumentUri(PROVIDER_PACKAGE, "doc:file1");
        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, docUri);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        mDevice.waitForIdle();

        assertTrue(findDocument("FILE1").exists());
    }

    public void testOpenDocumentTreeAtInitialLocation() throws Exception {
        if (!supportedHardware()) return;

        // Clear DocsUI's storage to avoid it opening stored last location
        // which may make this test pass "luckily".
        clearDocumentsUi();

        final Uri docUri = DocumentsContract.buildDocumentUri(PROVIDER_PACKAGE, "doc:dir2");
        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, docUri);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        mDevice.waitForIdle();

        assertTrue(findDocument("FILE4").exists());
    }

    public void testCreateDocumentAtInitialLocation() throws Exception {
        if (!supportedHardware()) return;

        // Clear DocsUI's storage to avoid it opening stored last location
        // which may make this test pass "luckily".
        clearDocumentsUi();

        final Uri treeUri = DocumentsContract.buildTreeDocumentUri(PROVIDER_PACKAGE, "doc:local");
        final Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "doc:file1");
        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("plain/text");
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, docUri);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        mDevice.waitForIdle();

        assertTrue(findDocument("FILE1").exists());
    }

    public void testCreateWebLink() throws Exception {
        if (!supportedHardware()) return;

        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Pick a virtual file from the local root.
        mDevice.waitForIdle();
        findRoot("CtsLocal").click();

        mDevice.waitForIdle();
        findDocument("WEB_LINKABLE_FILE").click();

        // Confirm that the returned file is actually the selected one.
        final Result result = mActivity.getResult();
        final Uri uri = result.data.getData();
        assertEquals("doc:web-linkable-file", DocumentsContract.getDocumentId(uri));

        final ContentResolver resolver = getInstrumentation().getContext().getContentResolver();

        Bundle bundle = new Bundle();
        bundle.putStringArray(Intent.EXTRA_EMAIL, new String[] { "x@x.com" });
        final IntentSender intentSender = DocumentsContract.createWebLinkIntent(resolver,
                uri, bundle);

        final int WEB_LINK_REQUEST_CODE = 1;
        mActivity.startIntentSenderForResult(intentSender, WEB_LINK_REQUEST_CODE,
                null, 0, 0, 0);
        mDevice.waitForIdle();

        // Confirm the permissions dialog. The dialog is provided by the stub
        // provider.
        UiObject okButton = new UiObject(new UiSelector().resourceId("android:id/button1"));
        assertNotNull(okButton);
        assertTrue(okButton.waitForExists(TIMEOUT));
        okButton.click();

        final Result webLinkResult = mActivity.getResult();
        assertEquals(WEB_LINK_REQUEST_CODE, webLinkResult.requestCode);
        assertEquals(Activity.RESULT_OK, webLinkResult.resultCode);

        final Uri webLinkUri = webLinkResult.data.getData();
        assertEquals("http://www.foobar.com/shared/SW33TCH3RR13S", webLinkUri.toString());
    }

    public void testEject() throws Exception {
        if (!supportedHardware()) return;

        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setDataAndType(
                DocumentsContract.buildChildDocumentsUri(PROVIDER_PACKAGE, "doc:dir1"),
                Document.MIME_TYPE_DIR);
        mActivity.startActivity(intent);

        findEjectIcon("eject").click();

        try {
            findRoot("eject").click();
            fail("Root eject was not ejected");
        } catch(UiObjectNotFoundException e) {
            // expected
        }
    }
}
