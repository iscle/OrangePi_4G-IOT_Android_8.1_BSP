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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.ContactsAsyncHelper;

import org.mockito.ArgumentCaptor;

import java.io.FileNotFoundException;
import java.io.InputStream;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

public class ContactsAsyncHelperTest extends TelecomTestCase {
    private static final Uri SAMPLE_CONTACT_PHOTO_URI = Uri.parse(
            "android.resource://com.android.server.telecom.tests/"
                    + R.drawable.contacts_sample_photo);

    private static final Uri SAMPLE_CONTACT_PHOTO_URI_SMALL = Uri.parse(
            "android.resource://com.android.server.telecom.tests/"
                    + R.drawable.contacts_sample_photo_small);

    private static final int TOKEN = 4847524;
    private static final int TEST_TIMEOUT = 500;
    private static final Object COOKIE = new Object();

    public static class ImageLoadListenerImpl
            implements ContactsAsyncHelper.OnImageLoadCompleteListener {
        @Override
        public void onImageLoadComplete(int token, Drawable photo,
                Bitmap photoIcon, Object cookie) {
        }
    }

    private ImageLoadListenerImpl mListener = spy(new ImageLoadListenerImpl());

    private ContactsAsyncHelper.ContentResolverAdapter mWorkingContentResolverAdapter =
            new ContactsAsyncHelper.ContentResolverAdapter() {
                @Override
                public InputStream openInputStream(Context context, Uri uri)
                        throws FileNotFoundException {
                    return context.getContentResolver().openInputStream(uri);
                }
            };

    private ContactsAsyncHelper.ContentResolverAdapter mNullContentResolverAdapter =
            new ContactsAsyncHelper.ContentResolverAdapter() {
                @Override
                public InputStream openInputStream(Context context, Uri uri)
                        throws FileNotFoundException {
                    return null;
                }
            };

    @Override
    public void setUp() throws Exception {
        mContext = getTestContext();
        super.setUp();
    }

    @SmallTest
    public void testEmptyUri() throws Exception {
        ContactsAsyncHelper cah = new ContactsAsyncHelper(mNullContentResolverAdapter);
        try {
            cah.startObtainPhotoAsync(TOKEN, mContext, null, mListener, COOKIE);
        } catch (IllegalStateException e) {
            // expected to fail
        }
        Thread.sleep(TEST_TIMEOUT);
        verify(mListener, never()).onImageLoadComplete(anyInt(),
                any(Drawable.class), any(Bitmap.class), anyObject());
    }

    @SmallTest
    public void testNullReturnFromOpenInputStream() {
        ContactsAsyncHelper cah = new ContactsAsyncHelper(mNullContentResolverAdapter);
        cah.startObtainPhotoAsync(TOKEN, mContext, SAMPLE_CONTACT_PHOTO_URI, mListener, COOKIE);

        verify(mListener, timeout(TEST_TIMEOUT)).onImageLoadComplete(eq(TOKEN),
                isNull(Drawable.class), isNull(Bitmap.class), eq(COOKIE));
    }

    @SmallTest
    public void testImageScaling() {
        ContactsAsyncHelper cah = new ContactsAsyncHelper(mWorkingContentResolverAdapter);
        cah.startObtainPhotoAsync(TOKEN, mContext, SAMPLE_CONTACT_PHOTO_URI, mListener, COOKIE);

        ArgumentCaptor<Drawable> photoCaptor = ArgumentCaptor.forClass(Drawable.class);
        ArgumentCaptor<Bitmap> iconCaptor = ArgumentCaptor.forClass(Bitmap.class);

        verify(mListener, timeout(TEST_TIMEOUT)).onImageLoadComplete(eq(TOKEN),
                photoCaptor.capture(), iconCaptor.capture(), eq(COOKIE));

        Bitmap capturedPhoto = ((BitmapDrawable) photoCaptor.getValue()).getBitmap();
        assertTrue(getExpectedPhoto(SAMPLE_CONTACT_PHOTO_URI).sameAs(capturedPhoto));
        int iconSize = mContext.getResources()
                .getDimensionPixelSize(R.dimen.notification_icon_size);
        assertTrue(iconSize >= iconCaptor.getValue().getHeight());
        assertTrue(iconSize >= iconCaptor.getValue().getWidth());
    }

    @SmallTest
    public void testNoScaling() {
        ContactsAsyncHelper cah = new ContactsAsyncHelper(mWorkingContentResolverAdapter);
        cah.startObtainPhotoAsync(TOKEN, mContext, SAMPLE_CONTACT_PHOTO_URI_SMALL,
                mListener, COOKIE);

        ArgumentCaptor<Drawable> photoCaptor = ArgumentCaptor.forClass(Drawable.class);
        ArgumentCaptor<Bitmap> iconCaptor = ArgumentCaptor.forClass(Bitmap.class);

        verify(mListener, timeout(TEST_TIMEOUT)).onImageLoadComplete(eq(TOKEN),
                photoCaptor.capture(), iconCaptor.capture(), eq(COOKIE));

        Bitmap capturedPhoto = ((BitmapDrawable) photoCaptor.getValue()).getBitmap();
        assertTrue(getExpectedPhoto(SAMPLE_CONTACT_PHOTO_URI_SMALL).sameAs(capturedPhoto));
        assertTrue(capturedPhoto.sameAs(iconCaptor.getValue()));
    }

    private Bitmap getExpectedPhoto(Uri uri) {
        InputStream is;
        try {
            is = mContext.getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            return null;
        }

        Drawable d = Drawable.createFromStream(is, uri.toString());
        return ((BitmapDrawable) d).getBitmap();
    }
}
