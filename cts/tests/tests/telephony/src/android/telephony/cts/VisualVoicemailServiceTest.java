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
 * limitations under the License.
 */

package android.telephony.cts;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Intents;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.telephony.VisualVoicemailSms;
import android.telephony.VisualVoicemailSmsFilterSettings;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class VisualVoicemailServiceTest extends InstrumentationTestCase {

    private static final String TAG = "VvmServiceTest";

    private static final String COMMAND_SET_DEFAULT_DIALER = "telecom set-default-dialer ";

    private static final String COMMAND_GET_DEFAULT_DIALER = "telecom get-default-dialer";

    private static final String PACKAGE = "android.telephony.cts";

    private static final long EVENT_RECEIVED_TIMEOUT_MILLIS = 60_000;
    private static final long EVENT_NOT_RECEIVED_TIMEOUT_MILLIS = 1_000;

    private Context mContext;
    private TelephonyManager mTelephonyManager;

    private String mPreviousDefaultDialer;

    private PhoneAccountHandle mPhoneAccountHandle;
    private String mPhoneNumber;

    private SmsBroadcastReceiver mSmsReceiver;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        if (hasTelephony(mContext)) {
            mPreviousDefaultDialer = getDefaultDialer(getInstrumentation());
            setDefaultDialer(getInstrumentation(), PACKAGE);

            TelecomManager telecomManager = mContext.getSystemService(TelecomManager.class);
            mPhoneAccountHandle = telecomManager
                    .getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
            mPhoneNumber = telecomManager.getLine1Number(mPhoneAccountHandle);

            mTelephonyManager = mContext.getSystemService(TelephonyManager.class)
                    .createForPhoneAccountHandle(mPhoneAccountHandle);
        }

        PackageManager packageManager = mContext.getPackageManager();
        packageManager.setComponentEnabledSetting(
                new ComponentName(mContext, MockVisualVoicemailService.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        packageManager.setComponentEnabledSetting(
                new ComponentName(mContext, PermissionlessVisualVoicemailService.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    @Override
    protected void tearDown() throws Exception {
        if (hasTelephony(mContext)) {
            if (!TextUtils.isEmpty(mPreviousDefaultDialer)) {
                setDefaultDialer(getInstrumentation(), mPreviousDefaultDialer);
            }

            if (mSmsReceiver != null) {
                mContext.unregisterReceiver(mSmsReceiver);
            }
        }
        super.tearDown();
    }

    public void testPermissionlessService_ignored() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }

        PackageManager packageManager = mContext.getPackageManager();
        packageManager.setComponentEnabledSetting(
                new ComponentName(mContext, MockVisualVoicemailService.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        packageManager.setComponentEnabledSetting(
                new ComponentName(mContext, PermissionlessVisualVoicemailService.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        String clientPrefix = "//CTSVVM";
        String text = "//CTSVVM:STATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1";

        mTelephonyManager.setVisualVoicemailSmsFilterSettings(
                new VisualVoicemailSmsFilterSettings.Builder()
                        .setClientPrefix(clientPrefix)
                        .build());

        try {
            mTelephonyManager
                    .sendVisualVoicemailSms(mPhoneNumber, 0, text, null);
            fail("SecurityException expected");
        } catch (SecurityException e) {
            // Expected
        }

        CompletableFuture<VisualVoicemailSms> future = new CompletableFuture<>();
        PermissionlessVisualVoicemailService.setSmsFuture(future);

        setupSmsReceiver(text);

        SmsManager.getDefault().sendTextMessage(mPhoneNumber, null, text, null, null);

        mSmsReceiver.assertReceived(EVENT_RECEIVED_TIMEOUT_MILLIS);
        try {
            future.get(EVENT_NOT_RECEIVED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            throw new RuntimeException("Unexpected visual voicemail SMS received");
        } catch (TimeoutException e) {
            // expected
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    public void testFilter() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        VisualVoicemailSms result = getSmsFromText("//CTSVVM",
                "//CTSVVM:STATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1");

        assertEquals("STATUS", result.getPrefix());
        assertEquals("R", result.getFields().getString("st"));
        assertEquals("0", result.getFields().getString("rc"));
        assertEquals("1", result.getFields().getString("srv"));
        assertEquals("1", result.getFields().getString("dn"));
        assertEquals("1", result.getFields().getString("ipt"));
        assertEquals("0", result.getFields().getString("spt"));
        assertEquals("eg@example.com", result.getFields().getString("u"));
        assertEquals("1", result.getFields().getString("pw"));
    }

    public void testFilter_data() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        if (!hasDataSms()) {
            Log.d(TAG, "skipping test that requires data SMS feature");
            return;
        }

        VisualVoicemailSmsFilterSettings settings = new VisualVoicemailSmsFilterSettings.Builder()
                .setClientPrefix("//CTSVVM")
                .build();
        VisualVoicemailSms result = getSmsFromData(settings, (short) 1000,
                "//CTSVVM:STATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1", true);

        assertEquals("STATUS", result.getPrefix());
        assertEquals("R", result.getFields().getString("st"));
        assertEquals("0", result.getFields().getString("rc"));
        assertEquals("1", result.getFields().getString("srv"));
        assertEquals("1", result.getFields().getString("dn"));
        assertEquals("1", result.getFields().getString("ipt"));
        assertEquals("0", result.getFields().getString("spt"));
        assertEquals("eg@example.com", result.getFields().getString("u"));
        assertEquals("1", result.getFields().getString("pw"));
    }


    public void testFilter_TrailingSemiColon() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        VisualVoicemailSms result = getSmsFromText("//CTSVVM",
                "//CTSVVM:STATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1;");

        assertEquals("STATUS", result.getPrefix());
        assertEquals("R", result.getFields().getString("st"));
        assertEquals("0", result.getFields().getString("rc"));
        assertEquals("1", result.getFields().getString("srv"));
        assertEquals("1", result.getFields().getString("dn"));
        assertEquals("1", result.getFields().getString("ipt"));
        assertEquals("0", result.getFields().getString("spt"));
        assertEquals("eg@example.com", result.getFields().getString("u"));
        assertEquals("1", result.getFields().getString("pw"));
    }

    public void testFilter_EmptyPrefix() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        VisualVoicemailSms result = getSmsFromText("//CTSVVM",
                "//CTSVVM::st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1");

        assertEquals("", result.getPrefix());
        assertEquals("R", result.getFields().getString("st"));
        assertEquals("0", result.getFields().getString("rc"));
        assertEquals("1", result.getFields().getString("srv"));
        assertEquals("1", result.getFields().getString("dn"));
        assertEquals("1", result.getFields().getString("ipt"));
        assertEquals("0", result.getFields().getString("spt"));
        assertEquals("eg@example.com", result.getFields().getString("u"));
        assertEquals("1", result.getFields().getString("pw"));
    }

    public void testFilter_EmptyField() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        VisualVoicemailSms result = getSmsFromText("//CTSVVM",
                "//CTSVVM:STATUS:");
        assertTrue(result.getFields().isEmpty());
    }

    public void testFilterFail_NotVvm() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "helloworld");
    }

    public void testFilterFail_PrefixMismatch() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//FOOVVM:STATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1");
    }

    public void testFilterFail_MissingFirstColon() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//CTSVVMSTATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1");
    }

    public void testFilterFail_MissingSecondColon() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//CTSVVM:STATUSst=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1");
    }

    public void testFilterFail_MessageEndAfterClientPrefix() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//CTSVVM:");
    }

    public void testFilterFail_MessageEndAfterPrefix() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//CTSVVM:STATUS");
    }

    public void testFilterFail_InvalidKeyValuePair() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//CTSVVM:STATUS:key");
    }

    public void testFilterFail_InvalidMissingKey() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertVisualVoicemailSmsNotReceived("//CTSVVM",
                "//CTSVVM:STATUS:=value");
    }

    public void testFilter_MissingValue() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        VisualVoicemailSms result = getSmsFromText("//CTSVVM",
                "//CTSVVM:STATUS:key=");
        assertEquals("STATUS", result.getPrefix());
        assertEquals("", result.getFields().getString("key"));
    }

    public void testFilter_originatingNumber_match_filtered() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        VisualVoicemailSmsFilterSettings settings = new VisualVoicemailSmsFilterSettings.Builder()
                .setClientPrefix("//CTSVVM")
                .setOriginatingNumbers(Arrays.asList(mPhoneNumber))
                .build();

        getSmsFromText(settings, "//CTSVVM:SYNC:key=value", true);
    }

    public void testFilter_originatingNumber_mismatch_notFiltered() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        VisualVoicemailSmsFilterSettings settings = new VisualVoicemailSmsFilterSettings.Builder()
                .setClientPrefix("//CTSVVM")
                .setOriginatingNumbers(Arrays.asList("1"))
                .build();

        getSmsFromText(settings, "//CTSVVM:SYNC:key=value", false);
    }

    public void testFilter_port_match() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        if (!hasDataSms()) {
            Log.d(TAG, "skipping test that requires data SMS feature");
            return;
        }

        VisualVoicemailSmsFilterSettings settings = new VisualVoicemailSmsFilterSettings.Builder()
                .setClientPrefix("//CTSVVM")
                .setDestinationPort(1000)
                .build();
        getSmsFromData(settings, (short) 1000,
                "//CTSVVM:STATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1", true);
    }

    public void testFilter_port_mismatch() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        if (!hasDataSms()) {
            Log.d(TAG, "skipping test that requires data SMS feature");
            return;
        }

        VisualVoicemailSmsFilterSettings settings = new VisualVoicemailSmsFilterSettings.Builder()
                .setClientPrefix("//CTSVVM")
                .setDestinationPort(1001)
                .build();
        getSmsFromData(settings, (short) 1000,
                "//CTSVVM:STATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1", false);
    }

    public void testFilter_port_anydata() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        if (!hasDataSms()) {
            Log.d(TAG, "skipping test that requires data SMS feature");
            return;
        }

        VisualVoicemailSmsFilterSettings settings = new VisualVoicemailSmsFilterSettings.Builder()
                .setClientPrefix("//CTSVVM")
                .setDestinationPort(VisualVoicemailSmsFilterSettings.DESTINATION_PORT_DATA_SMS)
                .build();
        getSmsFromData(settings, (short) 1000,
                "//CTSVVM:STATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1", true);
    }

    /**
     * Text SMS should not be filtered with DESTINATION_PORT_DATA_SMS
     */
    public void testFilter_port_anydata_notData() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        if (!hasDataSms()) {
            Log.d(TAG, "skipping test that requires data SMS feature");
            return;
        }

        VisualVoicemailSmsFilterSettings settings = new VisualVoicemailSmsFilterSettings.Builder()
                .setClientPrefix("//CTSVVM")
                .setDestinationPort(VisualVoicemailSmsFilterSettings.DESTINATION_PORT_DATA_SMS)
                .build();
        getSmsFromText(settings,
                "//CTSVVM:STATUS:st=R;rc=0;srv=1;dn=1;ipt=1;spt=0;u=eg@example.com;pw=1", false);
    }

    public void testGetVisualVoicemailPackageName_isSelf() {
        if (!hasTelephony(mContext)) {
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        assertEquals(PACKAGE, mTelephonyManager.getVisualVoicemailPackageName());
    }

    private VisualVoicemailSms getSmsFromText(String clientPrefix, String text) {
        return getSmsFromText(clientPrefix, text, true);
    }

    @Nullable
    private VisualVoicemailSms getSmsFromText(String clientPrefix, String text,
            boolean expectVvmSms) {
        return getSmsFromText(
                new VisualVoicemailSmsFilterSettings.Builder()
                        .setClientPrefix(clientPrefix)
                        .build(),
                text,
                expectVvmSms);
    }

    private void assertVisualVoicemailSmsNotReceived(String clientPrefix, String text) {
        getSmsFromText(clientPrefix, text, false);
    }

    /**
     * Setup the SMS filter with only the {@code clientPrefix}, and sends {@code text} to the
     * device. The SMS sent should not be written to the SMS provider. <p> If {@code expectVvmSms}
     * is {@code true}, the SMS should be be caught by the SMS filter. The user should not receive
     * the text, and the parsed result will be returned.* <p> If {@code expectVvmSms} is {@code
     * false}, the SMS should pass through the SMS filter. The user should receive the text, and
     * {@code null} be returned.
     */
    @Nullable
    private VisualVoicemailSms getSmsFromText(VisualVoicemailSmsFilterSettings settings,
            String text,
            boolean expectVvmSms) {

        mTelephonyManager.setVisualVoicemailSmsFilterSettings(settings);

        CompletableFuture<VisualVoicemailSms> future = new CompletableFuture<>();
        MockVisualVoicemailService.setSmsFuture(future);

        setupSmsReceiver(text);
        try (SentSmsObserver observer = new SentSmsObserver(mContext, text)) {
            mTelephonyManager
                    .sendVisualVoicemailSms(mPhoneNumber,0, text, null);

            if (expectVvmSms) {
                VisualVoicemailSms sms;
                try {
                    sms = future.get(EVENT_RECEIVED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    throw new RuntimeException(e);
                }
                mSmsReceiver.assertNotReceived(EVENT_NOT_RECEIVED_TIMEOUT_MILLIS);
                observer.assertNotChanged();
                return sms;
            } else {
                mSmsReceiver.assertReceived(EVENT_RECEIVED_TIMEOUT_MILLIS);
                try {
                    future.get(EVENT_NOT_RECEIVED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                    throw new RuntimeException("Unexpected visual voicemail SMS received");
                } catch (TimeoutException e) {
                    // expected
                    return null;
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Nullable
    private VisualVoicemailSms getSmsFromData(VisualVoicemailSmsFilterSettings settings, short port,
            String text, boolean expectVvmSms) {

        mTelephonyManager.setVisualVoicemailSmsFilterSettings(settings);

        CompletableFuture<VisualVoicemailSms> future = new CompletableFuture<>();
        MockVisualVoicemailService.setSmsFuture(future);

        setupSmsReceiver(text);
        mTelephonyManager.sendVisualVoicemailSms(mPhoneNumber, port, text, null);

        if (expectVvmSms) {
            VisualVoicemailSms sms;
            try {
                sms = future.get(EVENT_RECEIVED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
            mSmsReceiver.assertNotReceived(EVENT_NOT_RECEIVED_TIMEOUT_MILLIS);
            return sms;
        } else {
            mSmsReceiver.assertReceived(EVENT_RECEIVED_TIMEOUT_MILLIS);
            try {
                future.get(EVENT_NOT_RECEIVED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                throw new RuntimeException("Unexpected visual voicemail SMS received");
            } catch (TimeoutException e) {
                // expected
                return null;
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void setupSmsReceiver(String text) {
        mSmsReceiver = new SmsBroadcastReceiver(text);
        mContext.registerReceiver(mSmsReceiver, new IntentFilter(Intents.SMS_RECEIVED_ACTION));
        IntentFilter dataFilter = new IntentFilter(Intents.DATA_SMS_RECEIVED_ACTION);
        dataFilter.addDataScheme("sms");
        mContext.registerReceiver(mSmsReceiver, dataFilter);
    }

    private static class SmsBroadcastReceiver extends BroadcastReceiver {

        private final String mText;

        private CompletableFuture<Boolean> mFuture = new CompletableFuture<>();

        public SmsBroadcastReceiver(String text) {
            mText = text;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            SmsMessage[] messages = Sms.Intents.getMessagesFromIntent(intent);
            StringBuilder messageBody = new StringBuilder();
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            for (SmsMessage message : messages) {
                if (message.getMessageBody() != null) {
                    messageBody.append(message.getMessageBody());
                } else if (message.getUserData() != null) {
                    ByteBuffer byteBuffer = ByteBuffer.wrap(message.getUserData());
                    try {
                        messageBody.append(decoder.decode(byteBuffer).toString());
                    } catch (CharacterCodingException e) {
                        return;
                    }
                }
            }
            if (!TextUtils.equals(mText, messageBody.toString())) {
                return;
            }
            mFuture.complete(true);
        }

        public void assertReceived(long timeoutMillis) {
            try {
                mFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }

        public void assertNotReceived(long timeoutMillis) {
            try {
                mFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
                throw new RuntimeException("Unexpected SMS received");
            } catch (TimeoutException e) {
                // expected
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class SentSmsObserver extends ContentObserver implements AutoCloseable {

        private final Context mContext;
        private final String mText;

        public CompletableFuture<Boolean> mFuture = new CompletableFuture<>();

        public SentSmsObserver(Context context, String text) {
            super(new Handler(Looper.getMainLooper()));
            mContext = context;
            mText = text;
            mContext.getContentResolver().registerContentObserver(Sms.CONTENT_URI, true, this);
        }

        public void assertNotChanged() {
            try {
                mFuture.get(EVENT_NOT_RECEIVED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                fail("Visual voicemail SMS should not be added into the sent SMS");
            } catch (TimeoutException e) {
                // expected
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }

        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            try (Cursor cursor = mContext.getContentResolver()
                    .query(uri, new String[] {Sms.TYPE, Sms.BODY}, null, null, null)) {
                if (cursor == null){
                    return;
                }
                if (!cursor.moveToFirst()){
                    return;
                }
                if (cursor.getInt(0) == Sms.MESSAGE_TYPE_SENT && TextUtils
                        .equals(cursor.getString(1), mText)) {
                    mFuture.complete(true);
                }
            } catch (SQLiteException e) {

            }
        }

        @Override
        public void close() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    private static boolean hasTelephony(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE);
    }

    private boolean hasDataSms() {
        String mccmnc = mTelephonyManager.getSimOperator();
        return !CarrierCapability.UNSUPPORT_DATA_SMS_MESSAGES.contains(mccmnc);
    }

    private static String setDefaultDialer(Instrumentation instrumentation, String packageName)
            throws Exception {
        return executeShellCommand(instrumentation, COMMAND_SET_DEFAULT_DIALER + packageName);
    }

    private static String getDefaultDialer(Instrumentation instrumentation) throws Exception {
        return executeShellCommand(instrumentation, COMMAND_GET_DEFAULT_DIALER);
    }

    /**
     * Executes the given shell command and returns the output in a string. Note that even if we
     * don't care about the output, we have to read the stream completely to make the command
     * execute.
     */
    private static String executeShellCommand(Instrumentation instrumentation,
            String command) throws Exception {
        final ParcelFileDescriptor parcelFileDescriptor =
                instrumentation.getUiAutomation().executeShellCommand(command);
        BufferedReader bufferedReader = null;
        try (InputStream in = new FileInputStream(parcelFileDescriptor.getFileDescriptor())) {
            bufferedReader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String string = null;
            StringBuilder out = new StringBuilder();
            while ((string = bufferedReader.readLine()) != null) {
                out.append(string);
            }
            return out.toString();
        } finally {
            if (bufferedReader != null) {
                closeQuietly(bufferedReader);
            }
            closeQuietly(parcelFileDescriptor);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }
}
