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
 * limitations under the License.
 */

package com.android.server.telecom.tests;

import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallerInfoAsyncQuery;
import com.android.server.telecom.CallerInfoAsyncQueryFactory;

import android.content.Context;
import android.telecom.Log;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Controls a test {@link CallerInfoAsyncQueryFactory} to abstract away the asynchronous retrieval
 * of caller information from the Android contacts database.
 */
public class CallerInfoAsyncQueryFactoryFixture implements
        TestFixture<CallerInfoAsyncQueryFactory> {

    static class Request {
        int mToken;
        Object mCookie;
        CallerInfoAsyncQuery.OnQueryCompleteListener mListener;
        void reply() {
            replyWithCallerInfo(new CallerInfo());
        }

        void replyWithCallerInfo(CallerInfo callerInfo) {
            mListener.onQueryComplete(mToken, mCookie, callerInfo);
        }
    }

    CallerInfoAsyncQueryFactory mCallerInfoAsyncQueryFactory = new CallerInfoAsyncQueryFactory() {
        @Override
        public CallerInfoAsyncQuery startQuery(int token, Context context, String number,
                CallerInfoAsyncQuery.OnQueryCompleteListener listener, Object cookie) {
            Request r = new Request();
            r.mToken = token;
            r.mCookie = cookie;
            r.mListener = listener;
            mRequests.add(r);
            return Mockito.mock(CallerInfoAsyncQuery.class);
        }
    };

    final List<Request> mRequests = Collections.synchronizedList(new ArrayList<Request>());

    public CallerInfoAsyncQueryFactoryFixture() throws Exception {
        Log.i(this, "Creating ...");
    }

    @Override
    public CallerInfoAsyncQueryFactory getTestDouble() {
        return mCallerInfoAsyncQueryFactory;
    }
}
