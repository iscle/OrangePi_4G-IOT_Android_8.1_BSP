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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.test.suitebuilder.annotation.SmallTest;
import android.telecom.Logging.Session;

import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallerInfoAsyncQuery;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallerInfoAsyncQueryFactory;
import com.android.server.telecom.CallerInfoLookupHelper;
import com.android.server.telecom.ContactsAsyncHelper;
import com.android.server.telecom.TelecomSystem;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.CountDownLatch;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CallerInfoLookupHelperTest extends TelecomTestCase {
    @Mock Context mContext;
    @Mock CallerInfoAsyncQueryFactory mFactory;
    @Mock ContactsAsyncHelper mContactsAsyncHelper;
    @Mock Drawable mDrawable2;

    CallerInfo mCallerInfo1;
    CallerInfo mCallerInfo2;

    @Mock Drawable mDrawable1;
    CallerInfoLookupHelper mCallerInfoLookupHelper;
    static final Uri URI1 = Uri.parse("tel:555-555-7010");
    static final Uri URI2 = Uri.parse("tel:555-555-7016");

    static final Uri CONTACTS_PHOTO_URI = Uri.parse(
            "android.resource://com.android.server.telecom.tests/"
                    + R.drawable.contacts_sample_photo_small);

    Bitmap mBitmap;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mCallerInfoLookupHelper = new CallerInfoLookupHelper(mContext,
                mFactory, mContactsAsyncHelper, new TelecomSystem.SyncRoot() { });
        when(mFactory.startQuery(anyInt(), eq(mContext), anyString(),
                any(CallerInfoAsyncQuery.OnQueryCompleteListener.class), any()))
                .thenReturn(mock(CallerInfoAsyncQuery.class));
        mCallerInfo1 = new CallerInfo();
        mCallerInfo2 = new CallerInfo();

        if (mBitmap == null) {
            InputStream is;
            try {
                is = getTestContext().getContentResolver().openInputStream(CONTACTS_PHOTO_URI);
            } catch (FileNotFoundException e) {
                return;
            }

            Drawable d = Drawable.createFromStream(is, CONTACTS_PHOTO_URI.toString());
            mBitmap = ((BitmapDrawable) d).getBitmap();
        }
    }

    @SmallTest
    public void testLookupWithEmptyHandle() {
        CallerInfoLookupHelper.OnQueryCompleteListener listener = mock(
                CallerInfoLookupHelper.OnQueryCompleteListener.class);
        mCallerInfoLookupHelper.startLookup(Uri.EMPTY, listener);

        verify(listener).onCallerInfoQueryComplete(eq(Uri.EMPTY), isNull(CallerInfo.class));
        verifyProperCleanup();
    }

    public void testSimpleLookup() {
        CallerInfoLookupHelper.OnQueryCompleteListener listener = mock(
                CallerInfoLookupHelper.OnQueryCompleteListener.class);
        mCallerInfo1.contactDisplayPhotoUri = CONTACTS_PHOTO_URI;

        mCallerInfoLookupHelper.startLookup(URI1, listener);
        waitForActionCompletion();

        // CallerInfo section
        ArgumentCaptor<CallerInfoAsyncQuery.OnQueryCompleteListener> queryListenerCaptor =
                ArgumentCaptor.forClass(CallerInfoAsyncQuery.OnQueryCompleteListener.class);
        ArgumentCaptor<Session> logSessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(mFactory).startQuery(anyInt(), eq(mContext), eq(URI1.getSchemeSpecificPart()),
                queryListenerCaptor.capture(), logSessionCaptor.capture());

        queryListenerCaptor.getValue().onQueryComplete(
                0, logSessionCaptor.getValue(), mCallerInfo1);
        verify(listener).onCallerInfoQueryComplete(URI1, mCallerInfo1);
        waitForActionCompletion();

        // Contacts photo section
        ArgumentCaptor<ContactsAsyncHelper.OnImageLoadCompleteListener> imageListenerCaptor =
                ArgumentCaptor.forClass(ContactsAsyncHelper.OnImageLoadCompleteListener.class);
        verify(mContactsAsyncHelper).startObtainPhotoAsync(anyInt(), eq(mContext),
                eq(CONTACTS_PHOTO_URI), imageListenerCaptor.capture(), logSessionCaptor.capture());

        imageListenerCaptor.getValue().onImageLoadComplete(0, mDrawable1, mBitmap,
                logSessionCaptor.getValue());
        verify(listener).onContactPhotoQueryComplete(URI1, mCallerInfo1);
        assertEquals(mDrawable1, mCallerInfo1.cachedPhoto);
        assertEquals(mBitmap, mCallerInfo1.cachedPhotoIcon);

        verifyProperCleanup();
    }

    public void testLookupWithTwoListeners() {
        CallerInfoLookupHelper.OnQueryCompleteListener callListener = mock(
                CallerInfoLookupHelper.OnQueryCompleteListener.class);
        CallerInfoLookupHelper.OnQueryCompleteListener otherListener = mock(
                CallerInfoLookupHelper.OnQueryCompleteListener.class);
        mCallerInfo1.contactDisplayPhotoUri = CONTACTS_PHOTO_URI;

        mCallerInfoLookupHelper.startLookup(URI1, callListener);
        mCallerInfoLookupHelper.startLookup(URI1, otherListener);
        waitForActionCompletion();

        ArgumentCaptor<CallerInfoAsyncQuery.OnQueryCompleteListener> queryListenerCaptor =
                ArgumentCaptor.forClass(CallerInfoAsyncQuery.OnQueryCompleteListener.class);
        ArgumentCaptor<Session> logSessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(mFactory, times(1)).startQuery(anyInt(), eq(mContext),
                eq(URI1.getSchemeSpecificPart()), queryListenerCaptor.capture(),
                logSessionCaptor.capture());

        queryListenerCaptor.getValue().onQueryComplete(
                0, logSessionCaptor.getValue(), mCallerInfo1);
        verify(callListener, times(1)).onCallerInfoQueryComplete(URI1, mCallerInfo1);
        verify(otherListener, times(1)).onCallerInfoQueryComplete(URI1, mCallerInfo1);
        waitForActionCompletion();

        ArgumentCaptor<ContactsAsyncHelper.OnImageLoadCompleteListener> imageListenerCaptor =
                ArgumentCaptor.forClass(ContactsAsyncHelper.OnImageLoadCompleteListener.class);
        verify(mContactsAsyncHelper).startObtainPhotoAsync(anyInt(), eq(mContext),
                eq(CONTACTS_PHOTO_URI), imageListenerCaptor.capture(), logSessionCaptor.capture());

        imageListenerCaptor.getValue().onImageLoadComplete(0, mDrawable1, mBitmap,
                logSessionCaptor.getValue());
        verify(callListener).onContactPhotoQueryComplete(URI1, mCallerInfo1);
        verify(otherListener).onContactPhotoQueryComplete(URI1, mCallerInfo1);
        assertEquals(mDrawable1, mCallerInfo1.cachedPhoto);
        assertEquals(mBitmap, mCallerInfo1.cachedPhotoIcon);

        verifyProperCleanup();
    }

    public void testListenerAddedAfterCallerInfoBeforePhoto() {
        CallerInfoLookupHelper.OnQueryCompleteListener callListener = mock(
                CallerInfoLookupHelper.OnQueryCompleteListener.class);
        CallerInfoLookupHelper.OnQueryCompleteListener otherListener = mock(
                CallerInfoLookupHelper.OnQueryCompleteListener.class);
        mCallerInfo1.contactDisplayPhotoUri = CONTACTS_PHOTO_URI;

        mCallerInfoLookupHelper.startLookup(URI1, callListener);
        waitForActionCompletion();

        ArgumentCaptor<CallerInfoAsyncQuery.OnQueryCompleteListener> queryListenerCaptor =
                ArgumentCaptor.forClass(CallerInfoAsyncQuery.OnQueryCompleteListener.class);
        ArgumentCaptor<Session> logSessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(mFactory, times(1)).startQuery(anyInt(), eq(mContext),
                eq(URI1.getSchemeSpecificPart()), queryListenerCaptor.capture(),
                logSessionCaptor.capture());

        queryListenerCaptor.getValue().onQueryComplete(
                0, logSessionCaptor.getValue(), mCallerInfo1);
        verify(callListener, times(1)).onCallerInfoQueryComplete(URI1, mCallerInfo1);
        waitForActionCompletion();

        ArgumentCaptor<ContactsAsyncHelper.OnImageLoadCompleteListener> imageListenerCaptor =
                ArgumentCaptor.forClass(ContactsAsyncHelper.OnImageLoadCompleteListener.class);
        verify(mContactsAsyncHelper).startObtainPhotoAsync(anyInt(), eq(mContext),
                eq(CONTACTS_PHOTO_URI), imageListenerCaptor.capture(), logSessionCaptor.capture());
        mCallerInfoLookupHelper.startLookup(URI1, otherListener);
        verify(otherListener, times(1)).onCallerInfoQueryComplete(URI1, mCallerInfo1);

        imageListenerCaptor.getValue().onImageLoadComplete(0, mDrawable1, mBitmap,
                logSessionCaptor.getValue());
        verify(callListener).onContactPhotoQueryComplete(URI1, mCallerInfo1);
        verify(otherListener).onContactPhotoQueryComplete(URI1, mCallerInfo1);
        assertEquals(mDrawable1, mCallerInfo1.cachedPhoto);
        assertEquals(mBitmap, mCallerInfo1.cachedPhotoIcon);

        verifyProperCleanup();
    }

    private void verifyProperCleanup() {
        assertEquals(0, mCallerInfoLookupHelper.getCallerInfoEntries().size());
    }

    private void waitForActionCompletion() {
        final CountDownLatch lock = new CountDownLatch(1);
        mCallerInfoLookupHelper.getHandler().post(lock::countDown);
        while (lock.getCount() > 0) {
            try {
                lock.await();
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }
}
