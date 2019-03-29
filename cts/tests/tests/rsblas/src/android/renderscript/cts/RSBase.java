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

package android.cts.rsblas;

import android.content.Context;
import android.content.res.Resources;
import android.renderscript.RenderScript.RSErrorHandler;
import android.renderscript.RenderScript.RSMessageHandler;
import android.renderscript.RSRuntimeException;
import android.test.AndroidTestCase;
import android.util.Log;

/**
 * Base RenderScript test class. This class provides a message handler and a
 * convenient way to wait for compute scripts to complete their execution.
 */
public class RSBase extends AndroidTestCase {

    Context mCtx;
    Resources mRes;

    private int result;
    // msgHandled is used to synchronize between waitForMessage() and the
    // RSMessageHandler thread.
    private volatile boolean msgHandled;

    protected static final int RS_MSG_TEST_PASSED = 100;
    protected static final int RS_MSG_TEST_FAILED = 101;
    protected static final int RS_MSG_TEST_FLUSH = 102;

    RSMessageHandler mRsMessage = new RSMessageHandler() {
        public void run() {
            if (result == 0) {
                switch (mID) {
                    case RS_MSG_TEST_PASSED:
                    case RS_MSG_TEST_FAILED:
                        result = mID;
                        break;
                    case RS_MSG_TEST_FLUSH:
                        break;
                    default:
                        fail("Got unexpected RS message");
                        return;
                }
            }
            msgHandled = true;
        }
    };

    protected void waitForMessage() {
        while (!msgHandled) {
            Thread.yield();
        }
    }

    protected boolean FoundError = false;
    protected RSErrorHandler mRsError = new RSErrorHandler() {
        public void run() {
            FoundError = true;
            Log.e("RenderscriptCTS", mErrorMessage);
            throw new RSRuntimeException("Received error " + mErrorNum +
                                         " message " + mErrorMessage);
        }
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        result = 0;
        msgHandled = false;
        mCtx = getContext();
        mRes = mCtx.getResources();
    }

    /**
     * Verify that we didn't fail on the control or script side of things.
     */
    protected void checkForErrors() {
        assertFalse(FoundError);
        assertTrue(result != RS_MSG_TEST_FAILED);
    }
}
