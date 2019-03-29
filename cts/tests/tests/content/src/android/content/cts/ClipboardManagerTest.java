/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.content.cts;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ClipData.Item;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.test.UiThreadTest;

public class ClipboardManagerTest extends InstrumentationTestCase {
    private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getTargetContext();
    }

    @UiThreadTest
    public void testSetGetText() {
        ClipboardManager clipboardManager = makeClipboardManager();
        clipboardManager.setText("Test Text 1");
        assertEquals("Test Text 1", clipboardManager.getText());

        clipboardManager.setText("Test Text 2");
        assertEquals("Test Text 2", clipboardManager.getText());
    }

    @UiThreadTest
    public void testHasPrimaryClip() {
        ClipboardManager clipboardManager = makeClipboardManager();
        if (clipboardManager.hasPrimaryClip()) {
            assertNotNull(clipboardManager.getPrimaryClip());
            assertNotNull(clipboardManager.getPrimaryClipDescription());
        } else {
            assertNull(clipboardManager.getPrimaryClip());
            assertNull(clipboardManager.getPrimaryClipDescription());
        }

        clipboardManager.setPrimaryClip(ClipData.newPlainText("Label", "Text"));
        assertTrue(clipboardManager.hasPrimaryClip());
    }

    @UiThreadTest
    public void testSetPrimaryClip_plainText() {
        ClipData textData = ClipData.newPlainText("TextLabel", "Text");
        assertSetPrimaryClip(textData, "TextLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN},
                new ExpectedClipItem("Text", null, null));
    }

    @UiThreadTest
    public void testSetPrimaryClip_intent() {
        Intent intent = new Intent(mContext, ClipboardManagerTest.class);
        ClipData intentData = ClipData.newIntent("IntentLabel", intent);
        assertSetPrimaryClip(intentData, "IntentLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_INTENT},
                new ExpectedClipItem(null, intent, null));
    }

    @UiThreadTest
    public void testSetPrimaryClip_rawUri() {
        Uri uri = Uri.parse("http://www.google.com");
        ClipData uriData = ClipData.newRawUri("UriLabel", uri);
        assertSetPrimaryClip(uriData, "UriLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_URILIST},
                new ExpectedClipItem(null, null, uri));
    }

    @UiThreadTest
    public void testSetPrimaryClip_contentUri() {
        Uri contentUri = Uri.parse("content://cts/test/for/clipboardmanager");
        ClipData contentUriData = ClipData.newUri(mContext.getContentResolver(),
                "ContentUriLabel", contentUri);
        assertSetPrimaryClip(contentUriData, "ContentUriLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_URILIST},
                new ExpectedClipItem(null, null, contentUri));
    }

    @UiThreadTest
    public void testSetPrimaryClip_complexItem() {
        Intent intent = new Intent(mContext, ClipboardManagerTest.class);
        Uri uri = Uri.parse("http://www.google.com");
        ClipData multiData = new ClipData(new ClipDescription("ComplexItemLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN,
                        ClipDescription.MIMETYPE_TEXT_INTENT,
                        ClipDescription.MIMETYPE_TEXT_URILIST}),
                new Item("Text", intent, uri));
        assertSetPrimaryClip(multiData, "ComplexItemLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN,
                        ClipDescription.MIMETYPE_TEXT_INTENT,
                        ClipDescription.MIMETYPE_TEXT_URILIST},
                new ExpectedClipItem("Text", intent, uri));
    }

    @UiThreadTest
    public void testSetPrimaryClip_multipleItems() {
        Intent intent = new Intent(mContext, ClipboardManagerTest.class);
        Uri uri = Uri.parse("http://www.google.com");
        ClipData textData = ClipData.newPlainText("TextLabel", "Text");
        textData.addItem(new Item("More Text"));
        textData.addItem(new Item(intent));
        textData.addItem(new Item(uri));
        assertSetPrimaryClip(textData, "TextLabel",
                new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN},
                new ExpectedClipItem("Text", null, null),
                new ExpectedClipItem("More Text", null, null),
                new ExpectedClipItem(null, intent, null),
                new ExpectedClipItem(null, null, uri));
    }

    @UiThreadTest
    public void testSetPrimaryClip_multipleMimeTypes() {
        ContentResolver contentResolver = mContext.getContentResolver();

        Intent intent = new Intent(mContext, ClipboardManagerTest.class);
        Uri uri = Uri.parse("http://www.google.com");
        Uri contentUri1 = Uri.parse("content://ctstest/testtable1");
        Uri contentUri2 = Uri.parse("content://ctstest/testtable2");
        Uri contentUri3 = Uri.parse("content://ctstest/testtable1/0");
        Uri contentUri4 = Uri.parse("content://ctstest/testtable1/1");
        Uri contentUri5 = Uri.parse("content://ctstest/testtable2/0");
        Uri contentUri6 = Uri.parse("content://ctstest/testtable2/1");
        Uri contentUri7 = Uri.parse("content://ctstest/testtable2/2");
        Uri contentUri8 = Uri.parse("content://ctstest/testtable2/3");

        ClipData clipData = ClipData.newPlainText("TextLabel", "Text");
        clipData.addItem(contentResolver, new Item("More Text"));
        clipData.addItem(contentResolver, new Item(intent));
        clipData.addItem(contentResolver, new Item(uri));
        clipData.addItem(contentResolver, new Item(contentUri1));
        clipData.addItem(contentResolver, new Item(contentUri2));
        clipData.addItem(contentResolver, new Item(contentUri3));
        clipData.addItem(contentResolver, new Item(contentUri4));
        clipData.addItem(contentResolver, new Item(contentUri5));
        clipData.addItem(contentResolver, new Item(contentUri6));
        clipData.addItem(contentResolver, new Item(contentUri7));
        clipData.addItem(contentResolver, new Item(contentUri8));

        assertClipData(clipData, "TextLabel",
                new String[] {
                        ClipDescription.MIMETYPE_TEXT_PLAIN,
                        ClipDescription.MIMETYPE_TEXT_INTENT,
                        ClipDescription.MIMETYPE_TEXT_URILIST,
                        "vnd.android.cursor.dir/com.android.content.testtable1",
                        "vnd.android.cursor.dir/com.android.content.testtable2",
                        "vnd.android.cursor.item/com.android.content.testtable1",
                        "vnd.android.cursor.item/com.android.content.testtable2",
                        "image/jpeg",
                        "audio/mpeg",
                        "video/mpeg"
                },
                new ExpectedClipItem("Text", null, null),
                new ExpectedClipItem("More Text", null, null),
                new ExpectedClipItem(null, intent, null),
                new ExpectedClipItem(null, null, uri),
                new ExpectedClipItem(null, null, contentUri1),
                new ExpectedClipItem(null, null, contentUri2),
                new ExpectedClipItem(null, null, contentUri3),
                new ExpectedClipItem(null, null, contentUri4),
                new ExpectedClipItem(null, null, contentUri5),
                new ExpectedClipItem(null, null, contentUri6),
                new ExpectedClipItem(null, null, contentUri7),
                new ExpectedClipItem(null, null, contentUri8));
    }

    private class ExpectedClipItem {
        CharSequence mText;
        Intent mIntent;
        Uri mUri;

        ExpectedClipItem(CharSequence text, Intent intent, Uri uri) {
            mText = text;
            mIntent = intent;
            mUri = uri;
        }
    }

    private void assertSetPrimaryClip(ClipData clipData,
            String expectedLabel,
            String[] expectedMimeTypes,
            ExpectedClipItem... expectedClipItems) {
        ClipboardManager clipboardManager = makeClipboardManager();

        clipboardManager.setPrimaryClip(clipData);
        assertTrue(clipboardManager.hasPrimaryClip());

        if (expectedClipItems != null
                && expectedClipItems.length > 0
                && expectedClipItems[0].mText != null) {
            assertTrue(clipboardManager.hasText());
        } else {
            assertFalse(clipboardManager.hasText());
        }

        assertNotNull(clipboardManager.getPrimaryClip());
        assertNotNull(clipboardManager.getPrimaryClipDescription());

        assertClipData(clipboardManager.getPrimaryClip(),
                expectedLabel, expectedMimeTypes, expectedClipItems);

        assertClipDescription(clipboardManager.getPrimaryClipDescription(),
                expectedLabel, expectedMimeTypes);
    }

    private void assertClipData(ClipData actualData, String expectedLabel,
            String[] expectedMimeTypes, ExpectedClipItem... expectedClipItems) {
        if (expectedClipItems != null) {
            assertEquals(expectedClipItems.length, actualData.getItemCount());
            for (int i = 0; i < expectedClipItems.length; i++) {
                assertClipItem(expectedClipItems[i], actualData.getItemAt(i));
            }
        } else {
            throw new IllegalArgumentException("Should have at least one expectedClipItem...");
        }

        assertClipDescription(actualData.getDescription(), expectedLabel, expectedMimeTypes);
    }

    private void assertClipDescription(ClipDescription description, String expectedLabel,
            String... mimeTypes) {
        assertEquals(expectedLabel, description.getLabel());
        assertEquals(mimeTypes.length, description.getMimeTypeCount());
        int mimeTypeCount = description.getMimeTypeCount();
        for (int i = 0; i < mimeTypeCount; i++) {
            assertEquals(mimeTypes[i], description.getMimeType(i));
        }
    }

    private void assertClipItem(ExpectedClipItem expectedItem, Item item) {
        assertEquals(expectedItem.mText, item.getText());
        if (expectedItem.mIntent != null) {
            assertNotNull(item.getIntent());
        } else {
            assertNull(item.getIntent());
        }
        if (expectedItem.mUri != null) {
            assertEquals(expectedItem.mUri.toString(), item.getUri().toString());
        } else {
            assertNull(item.getUri());
        }
    }

    private ClipboardManager makeClipboardManager() {
        return (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
    }
}
