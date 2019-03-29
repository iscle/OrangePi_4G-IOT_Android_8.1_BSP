/*
 * Copyright (C) 2012 The Android Open Source Project
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
package android.renderscript.cts;

import java.util.concurrent.Semaphore;
import java.util.Random;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript.RSMessageHandler;

public class SendToClient extends RSBaseCompute {
    private Allocation mInAllocation;
    private static final Semaphore mSync = new Semaphore(0);
    private static Random random = new Random();

    @Override
    protected void tearDown() throws Exception {
        if (mInAllocation != null) {
            mInAllocation.destroy();
        }
        super.tearDown();
    }

    int outArray[] = new int[4];
    RSMessageHandler mRsMessage = new RSMessageHandler() {
        public void run() {
            switch (mID) {
                default:
                    outArray[0] = mID;
                    outArray[1] = mData[0];
                    outArray[2] = mData[1];
                    outArray[3] = mData[2];
                    try {
                        mSync.release();
                    } catch (Exception e) {
                        //TODO: handle exception
                    }
                    return;
            }
        }
    };

    /*
     * test rsSendToClient(int cmdID, const void* data, uint len);
     */
    public void testSendToClient() {
        int[] inArray = new int[4];
        for (int i=0; i<4; i++) {
             inArray[i] = random.nextInt(1000);
        }
        mInAllocation = Allocation.createSized(mRS, Element.I32_4(mRS), 1);
        mInAllocation.copyFrom(inArray);
        ScriptC_send_to_client mScript;
        mRS.setMessageHandler(mRsMessage);
        mScript = new ScriptC_send_to_client(mRS);
        mScript.forEach_root(mInAllocation);
        try {
            mSync.acquire();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        for (int i=0; i<inArray.length; i++) {
            assertEquals(createErrorMsgF(i, inArray[i], outArray[i]),
                    inArray[i], outArray[i]);
        }
    }

    /*
     * test rsSendToClient(int cmdID)
     */
    public void testSendToClient1(){
        outArray[0] = 0;
        int Id = random.nextInt(100);
        mRS.setMessageHandler(mRsMessage);
        ScriptC_send_to_client_1 mScript;
        mScript = new ScriptC_send_to_client_1(mRS);
        mScript.invoke_callback(Id);
        try {
            mSync.acquire();
        } catch (InterruptedException e) {
        // TODO Auto-generated catch block
            e.printStackTrace();
        }

        mScript.destroy();

        assertEquals(createErrorMsgF(1, Id, outArray[0]), Id, outArray[0]);
    }

    private String createErrorMsgF(int i, int in, int temp) {
        StringBuffer bf = new StringBuffer();
        bf.append("[Wrong value]");
        bf.append("; i = " + i);
        bf.append("; InValue = " + in);
        bf.append("; exceptValue = " + temp);
        return bf.toString();
    }
}
