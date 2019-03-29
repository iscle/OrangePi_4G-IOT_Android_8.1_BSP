/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.cts.rscpp;

import android.content.Context;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.renderscript.RenderScript.RSErrorHandler;
import android.renderscript.RenderScript.RSMessageHandler;
import android.renderscript.RSRuntimeException;
import android.renderscript.RenderScript;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Type;
import android.util.Log;

public class RSCppTest extends AndroidTestCase {

    Context mCtx;
    Resources mRes;
    RenderScript mRS;
    protected ScriptC_verify mVerify;

    private int result;
    private boolean msgHandled;

    private static final int RS_MSG_TEST_PASSED = 100;
    private static final int RS_MSG_TEST_FAILED = 101;

    RSMessageHandler mRsMessage = new RSMessageHandler() {
        public void run() {
            if (result == 0) {
                switch (mID) {
                    case RS_MSG_TEST_PASSED:
                    case RS_MSG_TEST_FAILED:
                        result = mID;
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
            Log.e("RSCppCTS", mErrorMessage);
            throw new RSRuntimeException("Received error " + mErrorNum +
                                         " message " + mErrorMessage);
        }
    };

    protected Element makeElement(Element.DataType dt, int vecSize) {
        Element e;
        if (vecSize > 1) {
            e = Element.createVector(mRS, dt, vecSize);
        } else {
            if (dt == Element.DataType.UNSIGNED_8) {
                e = Element.U8(mRS);
            } else {
                e = Element.F32(mRS);
            }
        }
        return e;
    }

    protected Allocation makeAllocation(int w, int h, Element e) {
        Type.Builder tb = new Type.Builder(mRS, e);
        tb.setX(w);
        tb.setY(h);
        Type t = tb.create();
        Allocation a = Allocation.createTyped(mRS, t);
        return a;
    }

    protected void checkForErrors() {
        mRS.finish();
        mVerify.invoke_checkError();
        waitForMessage();
        assertFalse(FoundError);
        assertTrue(result != RS_MSG_TEST_FAILED);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        result = 0;
        msgHandled = false;
        mCtx = getContext();
        mRes = mCtx.getResources();
        mRS = RenderScript.create(mCtx);
        mRS.setMessageHandler(mRsMessage);
        mVerify = new ScriptC_verify(mRS);
        mVerify.set_gAllowedIntError(3);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mVerify != null) {
            mVerify.destroy();
            mVerify = null;
        }
        if (mRS != null) {
            mRS.destroy();
            mRS = null;
        }
        super.tearDown();
    }
}
