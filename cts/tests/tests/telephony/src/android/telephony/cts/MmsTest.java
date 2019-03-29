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

package android.telephony.cts;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.SystemClock;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.test.AndroidTestCase;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.mms.ContentType;
import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduComposer;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.SendConf;
import com.google.android.mms.pdu.SendReq;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Test sending MMS using {@link android.telephony.SmsManager}.
 */
public class MmsTest extends AndroidTestCase {
    private static final String TAG = "MmsTest";

    private static final String ACTION_MMS_SENT = "CTS_MMS_SENT_ACTION";
    private static final long DEFAULT_EXPIRY_TIME = 7 * 24 * 60 * 60;
    private static final int DEFAULT_PRIORITY = PduHeaders.PRIORITY_NORMAL;

    private static final String SUBJECT = "CTS MMS Test";
    private static final String MESSAGE_BODY = "CTS MMS test message body";
    private static final String TEXT_PART_FILENAME = "text_0.txt";
    private static final String sSmilText =
            "<smil>" +
                    "<head>" +
                        "<layout>" +
                            "<root-layout/>" +
                            "<region height=\"100%%\" id=\"Text\" left=\"0%%\" top=\"0%%\" width=\"100%%\"/>" +
                        "</layout>" +
                    "</head>" +
                    "<body>" +
                        "<par dur=\"8000ms\">" +
                            "<text src=\"%s\" region=\"Text\"/>" +
                        "</par>" +
                    "</body>" +
            "</smil>";

    private static final long SENT_TIMEOUT = 1000 * 60 * 5; // 5 minutes

    private static final String PROVIDER_AUTHORITY = "telephonyctstest";

    private Random mRandom;
    private SentReceiver mSentReceiver;
    private TelephonyManager mTelephonyManager;
    private PackageManager mPackageManager;

    private static class SentReceiver extends BroadcastReceiver {
        private final Object mLock;
        private boolean mSuccess;
        private boolean mDone;

        public SentReceiver() {
            mLock = new Object();
            mSuccess = false;
            mDone = false;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Action " + intent.getAction());
            if (!ACTION_MMS_SENT.equals(intent.getAction())) {
                return;
            }
            final int resultCode = getResultCode();
            if (resultCode == Activity.RESULT_OK) {
                final byte[] response = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA);
                if (response != null) {
                    final GenericPdu pdu = new PduParser(
                            response, shouldParseContentDisposition()).parse();
                    if (pdu != null && pdu instanceof SendConf) {
                        final SendConf sendConf = (SendConf) pdu;
                        if (sendConf.getResponseStatus() == PduHeaders.RESPONSE_STATUS_OK) {
                            mSuccess = true;
                        } else {
                            Log.e(TAG, "SendConf response status=" + sendConf.getResponseStatus());
                        }
                    } else {
                        Log.e(TAG, "Not a SendConf: " +
                                (pdu != null ? pdu.getClass().getCanonicalName() : "NULL"));
                    }
                } else {
                    Log.e(TAG, "Empty response");
                }
            } else {
                Log.e(TAG, "Failure result=" + resultCode);
                if (resultCode == SmsManager.MMS_ERROR_HTTP_FAILURE) {
                    final int httpError = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, 0);
                    Log.e(TAG, "HTTP failure=" + httpError);
                }
            }
            synchronized (mLock) {
                mDone = true;
                mLock.notify();
            }
        }

        public boolean waitForSuccess(long timeout) {
            synchronized(mLock) {
                final long startTime = SystemClock.elapsedRealtime();
                long waitTime = timeout;
                while (waitTime > 0) {
                    try {
                        mLock.wait(waitTime);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    if (mDone) {
                        break;
                    }
                    waitTime = timeout - (SystemClock.elapsedRealtime() - startTime);
                }
                Log.i(TAG, "Wait for sent: done=" + mDone + ", success=" + mSuccess);
                return mDone && mSuccess;
            }

        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mRandom = new Random();
        mTelephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mPackageManager = mContext.getPackageManager();
    }

    public void testSendMmsMessage() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
             || !doesSupportMMS()) {
            Log.i(TAG, "testSendMmsMessage skipped: no telephony available or MMS not supported");
            return;
        }

        Log.i(TAG, "testSendMmsMessage");
        // Prime the MmsService so that MMS config is loaded
        final SmsManager smsManager = SmsManager.getDefault();
        smsManager.getAutoPersisting();
        // MMS config is loaded asynchronously. Wait a bit so it will be loaded.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // Ignore
        }

        final Context context = getContext();
        // Register sent receiver
        mSentReceiver = new SentReceiver();
        context.registerReceiver(mSentReceiver, new IntentFilter(ACTION_MMS_SENT));
        // Create local provider file for sending PDU
        final String fileName = "send." + String.valueOf(Math.abs(mRandom.nextLong())) + ".dat";
        final File sendFile = new File(context.getCacheDir(), fileName);
        final String selfNumber = getSimNumber(context);
        assertTrue(!TextUtils.isEmpty(selfNumber));
        final byte[] pdu = buildPdu(context, selfNumber, SUBJECT, MESSAGE_BODY);
        assertNotNull(pdu);
        assertTrue(writePdu(sendFile, pdu));
        final Uri contentUri = (new Uri.Builder())
                .authority(PROVIDER_AUTHORITY)
                .path(fileName)
                .scheme(ContentResolver.SCHEME_CONTENT)
                .build();
        // Send
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_MMS_SENT), 0);
        smsManager.sendMultimediaMessage(context,
                contentUri, null/*locationUrl*/, null/*configOverrides*/, pendingIntent);
        assertTrue(mSentReceiver.waitForSuccess(SENT_TIMEOUT));
        sendFile.delete();
    }

    private static boolean writePdu(File file, byte[] pdu) {
        FileOutputStream writer = null;
        try {
            writer = new FileOutputStream(file);
            writer.write(pdu);
            return true;
        } catch (final IOException e) {
            return false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private byte[] buildPdu(Context context, String selfNumber, String subject, String text) {
        final SendReq req = new SendReq();
        // From, per spec
        req.setFrom(new EncodedStringValue(selfNumber));
        // To
        final String[] recipients = new String[1];
        recipients[0] = selfNumber;
        final EncodedStringValue[] encodedNumbers = EncodedStringValue.encodeStrings(recipients);
        if (encodedNumbers != null) {
            req.setTo(encodedNumbers);
        }
        // Subject
        if (!TextUtils.isEmpty(subject)) {
            req.setSubject(new EncodedStringValue(subject));
        }
        // Date
        req.setDate(System.currentTimeMillis() / 1000);
        // Body
        final PduBody body = new PduBody();
        // Add text part. Always add a smil part for compatibility, without it there
        // may be issues on some carriers/client apps
        final int size = addTextPart(body, text, true/* add text smil */);
        req.setBody(body);
        // Message size
        req.setMessageSize(size);
        // Message class
        req.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes());
        // Expiry
        req.setExpiry(DEFAULT_EXPIRY_TIME);
        // The following set methods throw InvalidHeaderValueException
        try {
            // Priority
            req.setPriority(DEFAULT_PRIORITY);
            // Delivery report
            req.setDeliveryReport(PduHeaders.VALUE_NO);
            // Read report
            req.setReadReport(PduHeaders.VALUE_NO);
        } catch (InvalidHeaderValueException e) {
            return null;
        }

        return new PduComposer(context, req).make();
    }

    private static int addTextPart(PduBody pb, String message, boolean addTextSmil) {
        final PduPart part = new PduPart();
        // Set Charset if it's a text media.
        part.setCharset(CharacterSets.UTF_8);
        // Set Content-Type.
        part.setContentType(ContentType.TEXT_PLAIN.getBytes());
        // Set Content-Location.
        part.setContentLocation(TEXT_PART_FILENAME.getBytes());
        int index = TEXT_PART_FILENAME.lastIndexOf(".");
        String contentId = (index == -1) ? TEXT_PART_FILENAME
                : TEXT_PART_FILENAME.substring(0, index);
        part.setContentId(contentId.getBytes());
        part.setData(message.getBytes());
        pb.addPart(part);
        if (addTextSmil) {
            final String smil = String.format(sSmilText, TEXT_PART_FILENAME);
            addSmilPart(pb, smil);
        }
        return part.getData().length;
    }

    private static void addSmilPart(PduBody pb, String smil) {
        final PduPart smilPart = new PduPart();
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType(ContentType.APP_SMIL.getBytes());
        smilPart.setData(smil.getBytes());
        pb.addPart(0, smilPart);
    }

    private static String getSimNumber(Context context) {
        final TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        return telephonyManager.getLine1Number();
    }

    private static boolean shouldParseContentDisposition() {
        return SmsManager
                .getDefault()
                .getCarrierConfigValues()
                .getBoolean(SmsManager.MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION, true);
    }

    private static boolean doesSupportMMS() {
        return SmsManager
                .getDefault()
                .getCarrierConfigValues()
                .getBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, true);
    }

}
