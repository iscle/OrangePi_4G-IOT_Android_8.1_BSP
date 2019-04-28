/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import com.android.bluetooth.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;

import android.app.Activity;
import android.bluetooth.BluetoothDevicePicker;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;

/**
 * This class is designed to act as the entry point of handling the share intent
 * via BT from other APPs. and also make "Bluetooth" available in sharing method
 * selection dialog.
 */
public class BluetoothOppLauncherActivity extends Activity {
    private static final String TAG = "BluetoothLauncherActivity";
    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;

    // Regex that matches characters that have special meaning in HTML. '<', '>', '&' and
    // multiple continuous spaces.
    private static final Pattern PLAIN_TEXT_TO_ESCAPE = Pattern.compile("[<>&]| {2,}|\r?\n");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_SEND) || action.equals(Intent.ACTION_SEND_MULTIPLE)) {
            //Check if Bluetooth is available in the beginning instead of at the end
            if (!isBluetoothAllowed()) {
                Intent in = new Intent(this, BluetoothOppBtErrorActivity.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                in.putExtra("title", this.getString(R.string.airplane_error_title));
                in.putExtra("content", this.getString(R.string.airplane_error_msg));
                startActivity(in);
                finish();
                return;
            }

            /*
             * Other application is trying to share a file via Bluetooth,
             * probably Pictures, videos, or vCards. The Intent should contain
             * an EXTRA_STREAM with the data to attach.
             */
            if (action.equals(Intent.ACTION_SEND)) {
                // TODO: handle type == null case
                final String type = intent.getType();
                final Uri stream = (Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM);
                CharSequence extra_text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                // If we get ACTION_SEND intent with EXTRA_STREAM, we'll use the
                // uri data;
                // If we get ACTION_SEND intent without EXTRA_STREAM, but with
                // EXTRA_TEXT, we will try send this TEXT out; Currently in
                // Browser, share one link goes to this case;
                if (stream != null && type != null) {
                    if (V) Log.v(TAG, "Get ACTION_SEND intent: Uri = " + stream + "; mimetype = "
                                + type);
                    // Save type/stream, will be used when adding transfer
                    // session to DB.
                    Thread t = new Thread(new Runnable() {
                        public void run() {
                            sendFileInfo(type, stream.toString(), false /* isHandover */,
                                    true /* fromExternal */);
                        }
                    });
                    t.start();
                    return;
                } else if (extra_text != null && type != null) {
                    if (V) Log.v(TAG, "Get ACTION_SEND intent with Extra_text = "
                                + extra_text.toString() + "; mimetype = " + type);
                    final Uri fileUri = creatFileForSharedContent(this.createCredentialProtectedStorageContext(), extra_text);
                    if (fileUri != null) {
                        Thread t = new Thread(new Runnable() {
                            public void run() {
                                sendFileInfo(type, fileUri.toString(), false /* isHandover */,
                                        false /* fromExternal */);
                            }
                        });
                        t.start();
                        return;
                    } else {
                        Log.w(TAG,"Error trying to do set text...File not created!");
                        finish();
                        return;
                    }
                } else {
                    Log.e(TAG, "type is null; or sending file URI is null");
                    finish();
                    return;
                }
            } else if (action.equals(Intent.ACTION_SEND_MULTIPLE)) {
                final String mimeType = intent.getType();
                final ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (mimeType != null && uris != null) {
                    if (V) Log.v(TAG, "Get ACTION_SHARE_MULTIPLE intent: uris " + uris + "\n Type= "
                                + mimeType);
                    Thread t = new Thread(new Runnable() {
                        public void run() {
                            try {
                                BluetoothOppManager.getInstance(BluetoothOppLauncherActivity.this)
                                        .saveSendingFileInfo(mimeType, uris, false /* isHandover */,
                                                true /* fromExternal */);
                                //Done getting file info..Launch device picker
                                //and finish this activity
                                launchDevicePicker();
                                finish();
                            } catch (IllegalArgumentException exception) {
                                showToast(exception.getMessage());
                                finish();
                            }
                        }
                    });
                    t.start();
                    return;
                } else {
                    Log.e(TAG, "type is null; or sending files URIs are null");
                    finish();
                    return;
                }
            }
        } else if (action.equals(Constants.ACTION_OPEN)) {
            Uri uri = getIntent().getData();
            if (V) Log.v(TAG, "Get ACTION_OPEN intent: Uri = " + uri);

            Intent intent1 = new Intent();
            intent1.setAction(action);
            intent1.setClassName(Constants.THIS_PACKAGE_NAME, BluetoothOppReceiver.class.getName());
            intent1.setDataAndNormalize(uri);
            this.sendBroadcast(intent1);
            finish();
        } else {
            Log.w(TAG, "Unsupported action: " + action);
            finish();
        }
    }

    /**
     * Turns on Bluetooth if not already on, or launches device picker if Bluetooth is on
     * @return
     */
    private final void launchDevicePicker() {
        // TODO: In the future, we may send intent to DevicePickerActivity
        // directly,
        // and let DevicePickerActivity to handle Bluetooth Enable.
        if (!BluetoothOppManager.getInstance(this).isEnabled()) {
            if (V) Log.v(TAG, "Prepare Enable BT!! ");
            Intent in = new Intent(this, BluetoothOppBtEnableActivity.class);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(in);
        } else {
            if (V) Log.v(TAG, "BT already enabled!! ");
            Intent in1 = new Intent(BluetoothDevicePicker.ACTION_LAUNCH);
            in1.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            in1.putExtra(BluetoothDevicePicker.EXTRA_NEED_AUTH, false);
            in1.putExtra(BluetoothDevicePicker.EXTRA_FILTER_TYPE,
                    BluetoothDevicePicker.FILTER_TYPE_TRANSFER);
            in1.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_PACKAGE,
                    Constants.THIS_PACKAGE_NAME);
            in1.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_CLASS,
                    BluetoothOppReceiver.class.getName());
            if (V) {Log.d(TAG,"Launching " +BluetoothDevicePicker.ACTION_LAUNCH );}
            startActivity(in1);
        }
    }
    /* Returns true if Bluetooth is allowed given current airplane mode settings. */
    private final boolean isBluetoothAllowed() {
        final ContentResolver resolver = this.getContentResolver();

        // Check if airplane mode is on
        final boolean isAirplaneModeOn =
                Settings.System.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        if (!isAirplaneModeOn) {
            return true;
        }

        // Check if airplane mode matters
        final String airplaneModeRadios =
                Settings.System.getString(resolver, Settings.Global.AIRPLANE_MODE_RADIOS);
        final boolean isAirplaneSensitive = airplaneModeRadios == null ? true :
                airplaneModeRadios.contains(Settings.System.RADIO_BLUETOOTH);
        if (!isAirplaneSensitive) {
            return true;
        }

        // Check if Bluetooth may be enabled in airplane mode
        final String airplaneModeToggleableRadios = Settings.System.getString(
                resolver, Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);
        final boolean isAirplaneToggleable = airplaneModeToggleableRadios == null
                ? false
                : airplaneModeToggleableRadios.contains(Settings.Global.RADIO_BLUETOOTH);
        if (isAirplaneToggleable) {
            return true;
        }

        // If we get here we're not allowed to use Bluetooth right now
        return false;
    }

    private Uri creatFileForSharedContent(Context context, CharSequence shareContent) {
        if (shareContent == null) {
            return null;
        }

        Uri fileUri = null;
        FileOutputStream outStream = null;
        try {
            String fileName = getString(R.string.bluetooth_share_file_name) + ".html";
            context.deleteFile(fileName);

            /*
             * Convert the plain text to HTML
             */
            StringBuffer sb = new StringBuffer("<html><head><meta http-equiv=\"Content-Type\""
                    + " content=\"text/html; charset=UTF-8\"/></head><body>");
            // Escape any inadvertent HTML in the text message
            String text = escapeCharacterToDisplay(shareContent.toString());

            // Regex that matches Web URL protocol part as case insensitive.
            Pattern webUrlProtocol = Pattern.compile("(?i)(http|https)://");

            Pattern pattern = Pattern.compile("("
                    + Patterns.WEB_URL.pattern() + ")|("
                    + Patterns.EMAIL_ADDRESS.pattern() + ")|("
                    + Patterns.PHONE.pattern() + ")");
            // Find any embedded URL's and linkify
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                String matchStr = m.group();
                String link = null;

                // Find any embedded URL's and linkify
                if (Patterns.WEB_URL.matcher(matchStr).matches()) {
                    Matcher proto = webUrlProtocol.matcher(matchStr);
                    if (proto.find()) {
                        // This is work around to force URL protocol part be lower case,
                        // because WebView could follow only lower case protocol link.
                        link = proto.group().toLowerCase(Locale.US) +
                                matchStr.substring(proto.end());
                    } else {
                        // Patterns.WEB_URL matches URL without protocol part,
                        // so added default protocol to link.
                        link = "http://" + matchStr;
                    }

                // Find any embedded email address
                } else if (Patterns.EMAIL_ADDRESS.matcher(matchStr).matches()) {
                    link = "mailto:" + matchStr;

                // Find any embedded phone numbers and linkify
                } else if (Patterns.PHONE.matcher(matchStr).matches()) {
                    link = "tel:" + matchStr;
                }
                if (link != null) {
                    String href = String.format("<a href=\"%s\">%s</a>", link, matchStr);
                    m.appendReplacement(sb, href);
                }
            }
            m.appendTail(sb);
            sb.append("</body></html>");

            byte[] byteBuff = sb.toString().getBytes();

            outStream = context.openFileOutput(fileName, Context.MODE_PRIVATE);
            if (outStream != null) {
                outStream.write(byteBuff, 0, byteBuff.length);
                fileUri = Uri.fromFile(new File(context.getFilesDir(), fileName));
                if (fileUri != null) {
                    if (D) Log.d(TAG, "Created one file for shared content: "
                            + fileUri.toString());
                }
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException: " + e.toString());
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.toString());
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e.toString());
        } finally {
            try {
                if (outStream != null) {
                    outStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return fileUri;
    }

    /**
     * Escape some special character as HTML escape sequence.
     *
     * @param text Text to be displayed using WebView.
     * @return Text correctly escaped.
     */
    private static String escapeCharacterToDisplay(String text) {
        Pattern pattern = PLAIN_TEXT_TO_ESCAPE;
        Matcher match = pattern.matcher(text);

        if (match.find()) {
            StringBuilder out = new StringBuilder();
            int end = 0;
            do {
                int start = match.start();
                out.append(text.substring(end, start));
                end = match.end();
                int c = text.codePointAt(start);
                if (c == ' ') {
                    // Escape successive spaces into series of "&nbsp;".
                    for (int i = 1, n = end - start; i < n; ++i) {
                        out.append("&nbsp;");
                    }
                    out.append(' ');
                } else if (c == '\r' || c == '\n') {
                    out.append("<br>");
                } else if (c == '<') {
                    out.append("&lt;");
                } else if (c == '>') {
                    out.append("&gt;");
                } else if (c == '&') {
                    out.append("&amp;");
                }
            } while (match.find());
            out.append(text.substring(end));
            text = out.toString();
        }
        return text;
    }

    private void sendFileInfo(
            String mimeType, String uriString, boolean isHandover, boolean fromExternal) {
        BluetoothOppManager manager = BluetoothOppManager.getInstance(getApplicationContext());
        try {
            manager.saveSendingFileInfo(mimeType, uriString, isHandover, fromExternal);
            launchDevicePicker();
            finish();
        } catch (IllegalArgumentException exception) {
            showToast(exception.getMessage());
            finish();
        }
    }

    private void showToast(final String msg) {
        BluetoothOppLauncherActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

}
