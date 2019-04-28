/*
* Copyright (C) 2013 Samsung System LSI
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
package com.android.bluetooth.map;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import android.util.Log;

import com.android.bluetooth.map.BluetoothMapSmsPdu.SmsPdu;
import com.android.bluetooth.map.BluetoothMapUtils.TYPE;

public class BluetoothMapbMessageSms extends BluetoothMapbMessage {

    private ArrayList<SmsPdu> mSmsBodyPdus = null;
    private String mSmsBody = null;

    public void setSmsBodyPdus(ArrayList<SmsPdu> smsBodyPdus) {
        this.mSmsBodyPdus = smsBodyPdus;
        this.mCharset = null;
        if(smsBodyPdus.size() > 0)
            this.mEncoding = smsBodyPdus.get(0).getEncodingString();
    }

    public String getSmsBody() {
        return mSmsBody;
    }

    public void setSmsBody(String smsBody) {
        this.mSmsBody = smsBody;
        this.mCharset = "UTF-8";
        this.mEncoding = null;
    }

    @Override
    public void parseMsgPart(String msgPart) {
        if(mAppParamCharset == BluetoothMapAppParams.CHARSET_NATIVE) {
            if(D) Log.d(TAG, "Decoding \"" + msgPart + "\" as native PDU");
            byte[] msgBytes = decodeBinary(msgPart);
            if(msgBytes.length > 0 &&
                    msgBytes[0] < msgBytes.length-1 &&
                    (msgBytes[msgBytes[0]+1] & 0x03) != 0x01) {
                if(D) Log.d(TAG, "Only submit PDUs are supported");
                throw new IllegalArgumentException("Only submit PDUs are supported");
            }

            mSmsBody += BluetoothMapSmsPdu.decodePdu(msgBytes,
                    mType == TYPE.SMS_CDMA ? BluetoothMapSmsPdu.SMS_TYPE_CDMA
                                          : BluetoothMapSmsPdu.SMS_TYPE_GSM);
        } else {
            mSmsBody += msgPart;
        }
    }
    @Override
    public void parseMsgInit() {
        mSmsBody = "";
    }

    public byte[] encode() throws UnsupportedEncodingException
    {
        ArrayList<byte[]> bodyFragments = new ArrayList<byte[]>();

        /* Store the messages in an ArrayList to be able to handle the different message types in a generic way.
         * We use byte[] since we need to extract the length in bytes.
         */
        if(mSmsBody != null) {
            String tmpBody = mSmsBody.replaceAll("END:MSG", "/END\\:MSG"); // Replace any occurrences of END:MSG with \END:MSG
            bodyFragments.add(tmpBody.getBytes("UTF-8"));
        }else if (mSmsBodyPdus != null && mSmsBodyPdus.size() > 0) {
            for (SmsPdu pdu : mSmsBodyPdus) {
                // This cannot(must not) contain END:MSG
                bodyFragments.add(encodeBinary(pdu.getData(),pdu.getScAddress()).getBytes("UTF-8"));
            }
        } else {
            bodyFragments.add(new byte[0]); // An empty message - no text
        }

        return encodeGeneric(bodyFragments);
    }

}
