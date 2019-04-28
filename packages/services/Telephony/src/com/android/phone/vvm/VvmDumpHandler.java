package com.android.phone.vvm;

import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;

import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public class VvmDumpHandler {

    public static void dump(Context context, FileDescriptor fd, PrintWriter writer,
            String[] args) {
        TelephonyManager telephonyManager = TelephonyManager.from(context);
        IndentingPrintWriter indentedWriter = new IndentingPrintWriter(writer, "  ");
        indentedWriter.println("******* OmtpVvm *******");
        indentedWriter.println("======= Configs =======");
        indentedWriter.increaseIndent();
        for (PhoneAccountHandle handle : TelecomManager.from(context)
                .getCallCapablePhoneAccounts()) {
            int subId = PhoneAccountHandleConverter.toSubId(handle);
            indentedWriter.println(
                    "VisualVoicemailPackageName:" + telephonyManager.createForSubscriptionId(subId)
                            .getVisualVoicemailPackageName());
            indentedWriter.println(
                    "VisualVoicemailSmsFilterSettings(" + subId + "):" + telephonyManager
                            .getActiveVisualVoicemailSmsFilterSettings(subId));
        }
        indentedWriter.decreaseIndent();
        indentedWriter.println("======== Logs =========");
        VvmLog.dump(fd, indentedWriter, args);
    }
}
