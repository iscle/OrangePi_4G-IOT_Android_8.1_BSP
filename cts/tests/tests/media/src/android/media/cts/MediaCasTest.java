/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.media.cts;

import android.media.MediaCas;
import android.media.MediaCas.PluginDescriptor;
import android.media.MediaCas.Session;
import android.media.MediaCasException;
import android.media.MediaCasException.UnsupportedCasException;
import android.media.MediaCasStateException;
import android.media.MediaCodec;
import android.media.MediaDescrambler;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.platform.test.annotations.RequiresDevice;
import android.support.test.filters.SmallTest;
import android.test.AndroidTestCase;
import android.util.Log;

import java.lang.ArrayIndexOutOfBoundsException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SmallTest
@RequiresDevice
public class MediaCasTest extends AndroidTestCase {
    private static final String TAG = "MediaCasTest";

    // CA System Ids used for testing
    private static final int sInvalidSystemId = 0;
    private static final int sClearKeySystemId = 0xF6D8;

    // ClearKey CAS/Descrambler test vectors
    private static final String sProvisionStr =
            "{                                                   " +
            "  \"id\": 21140844,                                 " +
            "  \"name\": \"Test Title\",                         " +
            "  \"lowercase_organization_name\": \"Android\",     " +
            "  \"asset_key\": {                                  " +
            "  \"encryption_key\": \"nezAr3CHFrmBR9R8Tedotw==\"  " +
            "  },                                                " +
            "  \"cas_type\": 1,                                  " +
            "  \"track_types\": [ ]                              " +
            "}                                                   " ;

    private static final String sEcmBufferStr =
            "00 00 01 f0 00 50 00 01  00 00 00 01 00 46 00 00" +
            "00 02 00 00 00 00 00 01  00 00 27 10 02 00 01 77" +
            "01 42 95 6c 0e e3 91 bc  fd 05 b1 60 4f 17 82 a4" +
            "86 9b 23 56 00 01 00 00  00 01 00 00 27 10 02 00" +
            "01 77 01 42 95 6c d7 43  62 f8 1c 62 19 05 c7 3a" +
            "42 cd fd d9 13 48                               " ;

    private static final String sInputBufferStr =
            "00 00 00 01 09 f0 00 00  00 01 67 42 c0 1e db 01" +
            "40 16 ec 04 40 00 00 03  00 40 00 00 0f 03 c5 8b" +
            "b8 00 00 00 01 68 ca 8c  b2 00 00 01 06 05 ff ff" +
            "70 dc 45 e9 bd e6 d9 48  b7 96 2c d8 20 d9 23 ee" +
            "ef 78 32 36 34 20 2d 20  63 6f 72 65 20 31 34 32" +
            "20 2d 20 48 2e 32 36 34  2f 4d 50 45 47 2d 34 20" +
            "41 56 43 20 63 6f 64 65  63 20 2d 20 43 6f 70 79" +
            "6c 65 66 74 20 32 30 30  33 2d 32 30 31 34 20 2d" +
            "20 68 74 74 70 3a 2f 2f  77 77 77 2e 76 69 64 65" +
            "6f 6c 61 6e 2e 6f 72 67  2f 78 32 36 34 2e 68 74" +
            "6d 6c 6e 45 21 82 38 f0  9d 7d 96 e6 94 ae e2 87" +
            "8f 04 49 e5 f6 8c 8b 9a  10 18 ba 94 e9 22 31 04" +
            "7e 60 5b c4 24 00 90 62  0d dc 85 74 75 78 d0 14" +
            "08 cb 02 1d 7d 9d 34 e8  81 b9 f7 09 28 79 29 8d" +
            "e3 14 ed 5f ca af f4 1c  49 15 e1 80 29 61 76 80" +
            "43 f8 58 53 40 d7 31 6d  61 81 41 e9 77 9f 9c e1" +
            "6d f2 ee d9 c8 67 d2 5f  48 73 e3 5c cd a7 45 58" +
            "bb dd 28 1d 68 fc b4 c6  f6 92 f6 30 03 aa e4 32" +
            "f6 34 51 4b 0f 8c f9 ac  98 22 fb 49 c8 bf ca 8c" +
            "80 86 5d d7 a4 52 b1 d9  a6 04 4e b3 2d 1f b8 35" +
            "cc 45 6d 9c 20 a7 a4 34  59 72 e3 ae ba 49 de d1" +
            "aa ee 3d 77 fc 5d c6 1f  9d ac c2 15 66 b8 e1 54" +
            "4e 74 93 db 9a 24 15 6e  20 a3 67 3e 5a 24 41 5e" +
            "b0 e6 35 87 1b c8 7a f9  77 65 e0 01 f2 4c e4 2b" +
            "a9 64 96 96 0b 46 ca ea  79 0e 78 a3 5f 43 fc 47" +
            "6a 12 fa c4 33 0e 88 1c  19 3a 00 c3 4e b5 d8 fa" +
            "8e f1 bc 3d b2 7e 50 8d  67 c3 6b ed e2 ea a6 1f" +
            "25 24 7c 94 74 50 49 e3  c6 58 2e fd 28 b4 c6 73" +
            "b1 53 74 27 94 5c df 69  b7 a1 d7 f5 d3 8a 2c 2d" +
            "b4 5e 8a 16 14 54 64 6e  00 6b 11 59 8a 63 38 80" +
            "76 c3 d5 59 f7 3f d2 fa  a5 ca 82 ff 4a 62 f0 e3" +
            "42 f9 3b 38 27 8a 89 aa  50 55 4b 29 f1 46 7c 75" +
            "ef 65 af 9b 0d 6d da 25  94 14 c1 1b f0 c5 4c 24" +
            "0e 65                                           " ;

    private static final String sExpectedOutputBufferStr =
            "00 00 00 01 09 f0 00 00  00 01 67 42 c0 1e db 01" +
            "40 16 ec 04 40 00 00 03  00 40 00 00 0f 03 c5 8b" +
            "b8 00 00 00 01 68 ca 8c  b2 00 00 01 06 05 ff ff" +
            "70 dc 45 e9 bd e6 d9 48  b7 96 2c d8 20 d9 23 ee" +
            "ef 78 32 36 34 20 2d 20  63 6f 72 65 20 31 34 32" +
            "20 2d 20 48 2e 32 36 34  2f 4d 50 45 47 2d 34 20" +
            "41 56 43 20 63 6f 64 65  63 20 2d 20 43 6f 70 79" +
            "6c 65 66 74 20 32 30 30  33 2d 32 30 31 34 20 2d" +
            "20 68 74 74 70 3a 2f 2f  77 77 77 2e 76 69 64 65" +
            "6f 6c 61 6e 2e 6f 72 67  2f 78 32 36 34 2e 68 74" +
            "6d 6c 20 2d 20 6f 70 74  69 6f 6e 73 3a 20 63 61" +
            "62 61 63 3d 30 20 72 65  66 3d 32 20 64 65 62 6c" +
            "6f 63 6b 3d 31 3a 30 3a  30 20 61 6e 61 6c 79 73" +
            "65 3d 30 78 31 3a 30 78  31 31 31 20 6d 65 3d 68" +
            "65 78 20 73 75 62 6d 65  3d 37 20 70 73 79 3d 31" +
            "20 70 73 79 5f 72 64 3d  31 2e 30 30 3a 30 2e 30" +
            "30 20 6d 69 78 65 64 5f  72 65 66 3d 31 20 6d 65" +
            "5f 72 61 6e 67 65 3d 31  36 20 63 68 72 6f 6d 61" +
            "5f 6d 65 3d 31 20 74 72  65 6c 6c 69 73 3d 31 20" +
            "38 78 38 64 63 74 3d 30  20 63 71 6d 3d 30 20 64" +
            "65 61 64 7a 6f 6e 65 3d  32 31 2c 31 31 20 66 61" +
            "73 74 5f 70 73 6b 69 70  3d 31 20 63 68 72 6f 6d" +
            "61 5f 71 70 5f 6f 66 66  73 65 74 3d 2d 32 20 74" +
            "68 72 65 61 64 73 3d 36  30 20 6c 6f 6f 6b 61 68" +
            "65 61 64 5f 74 68 72 65  61 64 73 3d 35 20 73 6c" +
            "69 63 65 64 5f 74 68 72  65 61 64 73 3d 30 20 6e" +
            "72 3d 30 20 64 65 63 69  6d 61 74 65 3d 31 20 69" +
            "6e 74 65 72 6c 61 63 65  64 3d 30 20 62 6c 75 72" +
            "61 79 5f 63 6f 6d 70 61  74 3d 30 20 63 6f 6e 73" +
            "74 72 61 69 6e 65 64 5f  69 6e 74 72 61 3d 30 20" +
            "62 66 72 61 6d 65 73 3d  30 20 77 65 69 67 68 74" +
            "70 3d 30 20 6b 65 79 69  6e 74 3d 32 35 30 20 6b" +
            "65 79 69 6e 74 5f 6d 69  6e 3d 32 35 20 73 63 65" +
            "6e 65                                           " ;

    /**
     * Test that all enumerated CA systems can be instantiated.
     *
     * Due to the vendor-proprietary nature of CAS, we cannot verify all operations
     * of an arbitrary plugin. We can only verify that isSystemIdSupported() is
     * consistent with the enumeration results, and all enumerated CA system ids can
     * be instantiated.
     */
    public void testEnumeratePlugins() throws Exception {
        PluginDescriptor[] descriptors = MediaCas.enumeratePlugins();
        for (int i = 0; i < descriptors.length; i++) {
            Log.d(TAG, "desciptor[" + i + "]: id=" + descriptors[i].getSystemId()
                    + ", name=" + descriptors[i].getName());
            MediaCas mediaCas = null;
            MediaDescrambler descrambler = null;
            byte[] sessionId = null, streamSessionId = null;
            try {
                final int CA_system_id = descriptors[i].getSystemId();
                if (!MediaCas.isSystemIdSupported(CA_system_id)) {
                    fail("Enumerated " + descriptors[i] + " but is not supported.");
                }
                mediaCas = new MediaCas(CA_system_id);
                if (mediaCas == null) {
                    fail("Enumerated " + descriptors[i] + " but cannot instantiate MediaCas.");
                }
                descrambler = new MediaDescrambler(CA_system_id);
                if (descrambler == null) {
                    fail("Enumerated " + descriptors[i] + " but cannot instantiate MediaDescrambler.");
                }

                // Should always accept a listener (even if the plugin doesn't use it)
                mediaCas.setEventListener(new MediaCas.EventListener() {
                    @Override
                    public void onEvent(MediaCas MediaCas, int event, int arg, byte[] data) {
                        Log.d(TAG, "Received MediaCas event: "
                                + "event=" + event + ", arg=" + arg
                                + ", data=" + Arrays.toString(data));
                    }
                }, null);
            } finally {
                if (mediaCas != null) {
                    mediaCas.close();
                }
                if (descrambler != null) {
                    descrambler.close();
                }
            }
        }
    }

    public void testInvalidSystemIdFails() throws Exception {
        assertFalse("Invalid id " + sInvalidSystemId + " should not be supported",
                MediaCas.isSystemIdSupported(sInvalidSystemId));

        MediaCas unsupportedCAS = null;
        MediaDescrambler unsupportedDescrambler = null;

        try {
            try {
                unsupportedCAS = new MediaCas(sInvalidSystemId);
                fail("Shouldn't be able to create MediaCas with invalid id " + sInvalidSystemId);
            } catch (UnsupportedCasException e) {
                // expected
            }

            try {
                unsupportedDescrambler = new MediaDescrambler(sInvalidSystemId);
                fail("Shouldn't be able to create MediaDescrambler with invalid id " + sInvalidSystemId);
            } catch (UnsupportedCasException e) {
                // expected
            }
        } finally {
            if (unsupportedCAS != null) {
                unsupportedCAS.close();
            }
            if (unsupportedDescrambler != null) {
                unsupportedDescrambler.close();
            }
        }
    }

    public void testClearKeyPluginInstalled() throws Exception {
        PluginDescriptor[] descriptors = MediaCas.enumeratePlugins();
        for (int i = 0; i < descriptors.length; i++) {
            if (descriptors[i].getSystemId() == sClearKeySystemId) {
                return;
            }
        }
        fail("ClearKey plugin " + String.format("0x%d", sClearKeySystemId) + " is not found");
    }

    /**
     * Test that valid call sequences succeed.
     */
    public void testClearKeyApis() throws Exception {
        MediaCas mediaCas = null;
        MediaDescrambler descrambler = null;

        try {
            mediaCas = new MediaCas(sClearKeySystemId);
            descrambler = new MediaDescrambler(sClearKeySystemId);

            mediaCas.provision(sProvisionStr);

            byte[] pvtData = new byte[256];
            mediaCas.setPrivateData(pvtData);

            Session session = mediaCas.openSession();
            if (session == null) {
                fail("Can't open session for program");
            }

            session.setPrivateData(pvtData);

            Session streamSession = mediaCas.openSession();
            if (streamSession == null) {
                fail("Can't open session for stream");
            }
            streamSession.setPrivateData(pvtData);

            descrambler.setMediaCasSession(session);

            descrambler.setMediaCasSession(streamSession);

            mediaCas.refreshEntitlements(3, null);

            byte[] refreshBytes = new byte[4];
            refreshBytes[0] = 0;
            refreshBytes[1] = 1;
            refreshBytes[2] = 2;
            refreshBytes[3] = 3;

            mediaCas.refreshEntitlements(10, refreshBytes);

            final HandlerThread thread = new HandlerThread("EventListenerHandlerThread");
            thread.start();
            Handler handler = new Handler(thread.getLooper());
            testEventEcho(mediaCas, 1, 2, null /* data */, handler);
            thread.interrupt();

            String eventDataString = "event data string";
            byte[] eventData = eventDataString.getBytes();
            testEventEcho(mediaCas, 3, 4, eventData, null /* handler */);

            String emm = "clear key emm";
            byte[] emmData = emm.getBytes();
            mediaCas.processEmm(emmData);

            byte[] ecmData = loadByteArrayFromString(sEcmBufferStr);
            session.processEcm(ecmData);
            streamSession.processEcm(ecmData);

            ByteBuffer outputBuf = descrambleTestInputBuffer(descrambler);
            ByteBuffer expectedOutputBuf = ByteBuffer.wrap(
                    loadByteArrayFromString(sExpectedOutputBufferStr));
            assertTrue("Incorrect decryption result",
                    expectedOutputBuf.compareTo(outputBuf) == 0);

            session.close();
            streamSession.close();
        } finally {
            if (mediaCas != null) {
                mediaCas.close();
            }
            if (descrambler != null) {
                descrambler.close();
            }
        }
    }

    /**
     * Test that all sessions are closed after a MediaCas object is released.
     */
    public void testClearKeySessionClosedAfterRelease() throws Exception {
        MediaCas mediaCas = null;
        MediaDescrambler descrambler = null;

        try {
            mediaCas = new MediaCas(sClearKeySystemId);
            descrambler = new MediaDescrambler(sClearKeySystemId);
            mediaCas.provision(sProvisionStr);

            Session session = mediaCas.openSession();
            if (session == null) {
                fail("Can't open session for program");
            }

            Session streamSession = mediaCas.openSession();
            if (streamSession == null) {
                fail("Can't open session for stream");
            }

            mediaCas.close();
            mediaCas = null;

            try {
                descrambler.setMediaCasSession(session);
                fail("Program session not closed after MediaCas is released");
            } catch (MediaCasStateException e) {
                Log.d(TAG, "setMediaCasSession throws "
                        + e.getDiagnosticInfo() + " (as expected)");
            }
            try {
                descrambler.setMediaCasSession(streamSession);
                fail("Stream session not closed after MediaCas is released");
            } catch (MediaCasStateException e) {
                Log.d(TAG, "setMediaCasSession throws "
                        + e.getDiagnosticInfo() + " (as expected)");
            }
        } finally {
            if (mediaCas != null) {
                mediaCas.close();
            }
            if (descrambler != null) {
                descrambler.close();
            }
        }
    }

    /**
     * Test that invalid call sequences fail with expected exceptions.
     */
    public void testClearKeyExceptions() throws Exception {
        MediaCas mediaCas = null;
        MediaDescrambler descrambler = null;

        try {
            mediaCas = new MediaCas(sClearKeySystemId);
            descrambler = new MediaDescrambler(sClearKeySystemId);

            /*
             * Test MediaCas exceptions
             */

            // provision should fail with an invalid asset string
            try {
                mediaCas.provision("invalid asset string");
                fail("provision shouldn't succeed with invalid asset");
            } catch (MediaCasStateException e) {
                Log.d(TAG, "provision throws " + e.getDiagnosticInfo() + " (as expected)");
            }

            // processEmm should reject invalid offset and length
            String emm = "clear key emm";
            byte[] emmData = emm.getBytes();
            try {
                mediaCas.processEmm(emmData, 8, 40);
            } catch (ArrayIndexOutOfBoundsException e) {
                Log.d(TAG, "processEmm throws ArrayIndexOutOfBoundsException (as expected)");
            }

            // open a session, then close it so that it should become invalid
            Session invalidSession = mediaCas.openSession();
            if (invalidSession == null) {
                fail("Can't open session for program");
            }
            invalidSession.close();

            byte[] ecmData = loadByteArrayFromString(sEcmBufferStr);

            // processEcm should fail with an invalid session id
            try {
                invalidSession.processEcm(ecmData);
                fail("processEcm shouldn't succeed with invalid session id");
            } catch (MediaCasStateException e) {
                Log.d(TAG, "processEcm throws " + e.getDiagnosticInfo() + " (as expected)");
            }

            Session session = mediaCas.openSession();
            if (session == null) {
                fail("Can't open session for program");
            }

            // processEcm should fail without provisioning
            try {
                session.processEcm(ecmData);
                fail("processEcm shouldn't succeed without provisioning");
            } catch (MediaCasException.NotProvisionedException e) {
                Log.d(TAG, "processEcm throws NotProvisionedException (as expected)");
            }

            // Now provision it, and expect failures other than NotProvisionedException
            mediaCas.provision(sProvisionStr);

            // processEcm should fail with ecm buffer that's too short
            try {
                session.processEcm(ecmData, 0, 8);
                fail("processEcm shouldn't succeed with truncated ecm");
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "processEcm throws " + e.toString() + " (as expected)");
            }

            // processEcm should fail with ecm with bad descriptor count
            try {
                ecmData[17] = 3; // change the descriptor count field to 3 (invalid)
                session.processEcm(ecmData);
                fail("processEcm shouldn't succeed with altered descriptor count");
            } catch (MediaCasStateException e) {
                Log.d(TAG, "processEcm throws " + e.getDiagnosticInfo() + " (as expected)");
            }

            /*
             * Test MediaDescrambler exceptions
             */

            // setMediaCasSession should fail with an invalid session id
            try {
                descrambler.setMediaCasSession(invalidSession);
                fail("setMediaCasSession shouldn't succeed with invalid session id");
            } catch (MediaCasStateException e) {
                Log.d(TAG, "setMediaCasSession throws "
                        + e.getDiagnosticInfo() + " (as expected)");
            }

            // descramble should fail without a valid session
            try {
                ByteBuffer outputBuf = descrambleTestInputBuffer(descrambler);
                fail("descramble should fail without a valid session");
            } catch (MediaCasStateException e) {
                Log.d(TAG, "descramble throws " + e.getDiagnosticInfo() + " (as expected)");
            }

            // Now set a valid session, should still fail because no valid ecm is processed
            descrambler.setMediaCasSession(session);
            try {
                ByteBuffer outputBuf = descrambleTestInputBuffer(descrambler);
                fail("descramble should fail without valid ecm");
            } catch (MediaCasStateException e) {
                Log.d(TAG, "descramble throws " + e.getDiagnosticInfo() + " (as expected)");
            }
        } finally {
            if (mediaCas != null) {
                mediaCas.close();
            }
            if (descrambler != null) {
                descrambler.close();
            }
        }
    }

    private class TestEventListener implements MediaCas.EventListener {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final MediaCas mMediaCas;
        private final int mEvent;
        private final int mArg;
        private final byte[] mData;
        private boolean mIsIdential;

        TestEventListener(MediaCas mediaCas, int event, int arg, byte[] data) {
            mMediaCas = mediaCas;
            mEvent = event;
            mArg = arg;
            mData = data;
        }

        boolean waitForResult() {
            try {
                if (!mLatch.await(1, TimeUnit.SECONDS)) {
                    return false;
                }
                return mIsIdential;
            } catch (InterruptedException e) {}
            return false;
        }

        @Override
        public void onEvent(MediaCas mediaCas, int event, int arg, byte[] data) {
            Log.d(TAG, "Received MediaCas event: event=" + event
                    + ", arg=" + arg + ", data=" + Arrays.toString(data));
            if (mediaCas == mMediaCas && event == mEvent
                    && arg == mArg && (Arrays.equals(data, mData) ||
                            data == null && mData.length == 0 ||
                            mData == null && data.length == 0)) {
                mIsIdential = true;
            }
            mLatch.countDown();
        }
    }

    // helper to send an event and wait for echo
    private void testEventEcho(MediaCas mediaCas, int event,
            int arg, byte[] data, Handler handler) throws Exception {
        TestEventListener listener = new TestEventListener(mediaCas, event, arg, data);
        mediaCas.setEventListener(listener, handler);
        mediaCas.sendEvent(event, arg, data);
        assertTrue("Didn't receive event callback for " + event, listener.waitForResult());
    }

    // helper to descramble from the sample input (sInputBufferStr) and get output buffer
    private ByteBuffer descrambleTestInputBuffer(
            MediaDescrambler descrambler) throws Exception {
        MediaCodec.CryptoInfo cryptoInfo = new MediaCodec.CryptoInfo();
        int[] numBytesOfClearData     = new int[] { 162,   0,   0 };
        int[] numBytesOfEncryptedData = new int[] {   0, 184, 184 };
        byte[] key = new byte[16];
        key[0] = 2; // scrambling mode = even key
        byte[] iv = new byte[16]; // not used
        cryptoInfo.set(3, numBytesOfClearData, numBytesOfEncryptedData,
                key, iv, MediaCodec.CRYPTO_MODE_AES_CBC);
        ByteBuffer inputBuf = ByteBuffer.wrap(
                loadByteArrayFromString(sInputBufferStr));
        ByteBuffer outputBuf = ByteBuffer.allocate(inputBuf.capacity());
        descrambler.descramble(inputBuf, outputBuf, cryptoInfo);

        return outputBuf;
    }

    // helper to load byte[] from a String
    private byte[] loadByteArrayFromString(final String str) {
        Pattern pattern = Pattern.compile("[0-9a-fA-F]{2}");
        Matcher matcher = pattern.matcher(str);
        // allocate a large enough byte array first
        byte[] tempArray = new byte[str.length() / 2];
        int i = 0;
        while (matcher.find()) {
          tempArray[i++] = (byte)Integer.parseInt(matcher.group(), 16);
        }
        return Arrays.copyOfRange(tempArray, 0, i);
    }
}
