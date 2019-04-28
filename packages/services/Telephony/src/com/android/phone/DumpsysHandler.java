
package com.android.phone;

import android.content.Context;

import com.android.phone.vvm.VvmDumpHandler;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Handles "adb shell dumpsys phone" and bug report dump.
 */
public class DumpsysHandler {

    public static void dump(Context context, FileDescriptor fd, PrintWriter writer,
            String[] args) {
        PhoneGlobals.getInstance().dump(fd, writer, args);
        // Dump OMTP visual voicemail log.
        VvmDumpHandler.dump(context, fd, writer, args);
    }
}
