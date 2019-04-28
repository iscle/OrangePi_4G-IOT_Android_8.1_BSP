/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.mms.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Bundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import com.android.mms.service.exception.MmsHttpException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MMS HTTP client for sending and downloading MMS messages
 */
public class MmsHttpClient {
    public static final String METHOD_POST = "POST";
    public static final String METHOD_GET = "GET";

    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_ACCEPT_LANGUAGE = "Accept-Language";
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String HEADER_CONNECTION = "Connection";

    // The "Accept" header value
    private static final String HEADER_VALUE_ACCEPT =
            "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic";
    // The "Content-Type" header value
    private static final String HEADER_VALUE_CONTENT_TYPE_WITH_CHARSET =
            "application/vnd.wap.mms-message; charset=utf-8";
    private static final String HEADER_VALUE_CONTENT_TYPE_WITHOUT_CHARSET =
            "application/vnd.wap.mms-message";
    private static final String HEADER_CONNECTION_CLOSE = "close";

    private static final int IPV4_WAIT_ATTEMPTS = 15;
    private static final long IPV4_WAIT_DELAY_MS = 1000; // 1 seconds

    private final Context mContext;
    private final Network mNetwork;
    private final ConnectivityManager mConnectivityManager;

    /**
     * Constructor
     *  @param context The Context object
     * @param network The Network for creating an OKHttp client
     * @param connectivityManager
     */
    public MmsHttpClient(Context context, Network network,
            ConnectivityManager connectivityManager) {
        mContext = context;
        mNetwork = network;
        mConnectivityManager = connectivityManager;
    }

    /**
     * Execute an MMS HTTP request, either a POST (sending) or a GET (downloading)
     *
     * @param urlString The request URL, for sending it is usually the MMSC, and for downloading
     *                  it is the message URL
     * @param pdu For POST (sending) only, the PDU to send
     * @param method HTTP method, POST for sending and GET for downloading
     * @param isProxySet Is there a proxy for the MMSC
     * @param proxyHost The proxy host
     * @param proxyPort The proxy port
     * @param mmsConfig The MMS config to use
     * @param subId The subscription ID used to get line number, etc.
     * @param requestId The request ID for logging
     * @return The HTTP response body
     * @throws MmsHttpException For any failures
     */
    public byte[] execute(String urlString, byte[] pdu, String method, boolean isProxySet,
            String proxyHost, int proxyPort, Bundle mmsConfig, int subId, String requestId)
            throws MmsHttpException {
        LogUtil.d(requestId, "HTTP: " + method + " " + redactUrlForNonVerbose(urlString)
                + (isProxySet ? (", proxy=" + proxyHost + ":" + proxyPort) : "")
                + ", PDU size=" + (pdu != null ? pdu.length : 0));
        checkMethod(method);
        HttpURLConnection connection = null;
        try {
            Proxy proxy = Proxy.NO_PROXY;
            if (isProxySet) {
                proxy = new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(mNetwork.getByName(proxyHost), proxyPort));
            }
            final URL url = new URL(urlString);
            maybeWaitForIpv4(requestId, url);
            // Now get the connection
            connection = (HttpURLConnection) mNetwork.openConnection(url, proxy);
            connection.setDoInput(true);
            connection.setConnectTimeout(
                    mmsConfig.getInt(SmsManager.MMS_CONFIG_HTTP_SOCKET_TIMEOUT));
            // ------- COMMON HEADERS ---------
            // Header: Accept
            connection.setRequestProperty(HEADER_ACCEPT, HEADER_VALUE_ACCEPT);
            // Header: Accept-Language
            connection.setRequestProperty(
                    HEADER_ACCEPT_LANGUAGE, getCurrentAcceptLanguage(Locale.getDefault()));
            // Header: User-Agent
            final String userAgent = mmsConfig.getString(SmsManager.MMS_CONFIG_USER_AGENT);
            LogUtil.i(requestId, "HTTP: User-Agent=" + userAgent);
            connection.setRequestProperty(HEADER_USER_AGENT, userAgent);
            // Header: x-wap-profile
            final String uaProfUrlTagName =
                    mmsConfig.getString(SmsManager.MMS_CONFIG_UA_PROF_TAG_NAME);
            final String uaProfUrl = mmsConfig.getString(SmsManager.MMS_CONFIG_UA_PROF_URL);
            if (uaProfUrl != null) {
                LogUtil.i(requestId, "HTTP: UaProfUrl=" + uaProfUrl);
                connection.setRequestProperty(uaProfUrlTagName, uaProfUrl);
            }
            // Header: Connection: close (if needed)
            // Some carriers require that the HTTP connection's socket is closed
            // after an MMS request/response is complete. In these cases keep alive
            // is disabled. See https://tools.ietf.org/html/rfc7230#section-6.6
            if (mmsConfig.getBoolean(SmsManager.MMS_CONFIG_CLOSE_CONNECTION, false)) {
                LogUtil.i(requestId, "HTTP: Connection close after request");
                connection.setRequestProperty(HEADER_CONNECTION, HEADER_CONNECTION_CLOSE);
            }
            // Add extra headers specified by mms_config.xml's httpparams
            addExtraHeaders(connection, mmsConfig, subId);
            // Different stuff for GET and POST
            if (METHOD_POST.equals(method)) {
                if (pdu == null || pdu.length < 1) {
                    LogUtil.e(requestId, "HTTP: empty pdu");
                    throw new MmsHttpException(0/*statusCode*/, "Sending empty PDU");
                }
                connection.setDoOutput(true);
                connection.setRequestMethod(METHOD_POST);
                if (mmsConfig.getBoolean(SmsManager.MMS_CONFIG_SUPPORT_HTTP_CHARSET_HEADER)) {
                    connection.setRequestProperty(HEADER_CONTENT_TYPE,
                            HEADER_VALUE_CONTENT_TYPE_WITH_CHARSET);
                } else {
                    connection.setRequestProperty(HEADER_CONTENT_TYPE,
                            HEADER_VALUE_CONTENT_TYPE_WITHOUT_CHARSET);
                }
                if (LogUtil.isLoggable(Log.VERBOSE)) {
                    logHttpHeaders(connection.getRequestProperties(), requestId);
                }
                connection.setFixedLengthStreamingMode(pdu.length);
                // Sending request body
                final OutputStream out =
                        new BufferedOutputStream(connection.getOutputStream());
                out.write(pdu);
                out.flush();
                out.close();
            } else if (METHOD_GET.equals(method)) {
                if (LogUtil.isLoggable(Log.VERBOSE)) {
                    logHttpHeaders(connection.getRequestProperties(), requestId);
                }
                connection.setRequestMethod(METHOD_GET);
            }
            // Get response
            final int responseCode = connection.getResponseCode();
            final String responseMessage = connection.getResponseMessage();
            LogUtil.d(requestId, "HTTP: " + responseCode + " " + responseMessage);
            if (LogUtil.isLoggable(Log.VERBOSE)) {
                logHttpHeaders(connection.getHeaderFields(), requestId);
            }
            if (responseCode / 100 != 2) {
                throw new MmsHttpException(responseCode, responseMessage);
            }
            final InputStream in = new BufferedInputStream(connection.getInputStream());
            final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            final byte[] buf = new byte[4096];
            int count = 0;
            while ((count = in.read(buf)) > 0) {
                byteOut.write(buf, 0, count);
            }
            in.close();
            final byte[] responseBody = byteOut.toByteArray();
            LogUtil.d(requestId, "HTTP: response size="
                    + (responseBody != null ? responseBody.length : 0));
            return responseBody;
        } catch (MalformedURLException e) {
            final String redactedUrl = redactUrlForNonVerbose(urlString);
            LogUtil.e(requestId, "HTTP: invalid URL " + redactedUrl, e);
            throw new MmsHttpException(0/*statusCode*/, "Invalid URL " + redactedUrl, e);
        } catch (ProtocolException e) {
            final String redactedUrl = redactUrlForNonVerbose(urlString);
            LogUtil.e(requestId, "HTTP: invalid URL protocol " + redactedUrl, e);
            throw new MmsHttpException(0/*statusCode*/, "Invalid URL protocol " + redactedUrl, e);
        } catch (IOException e) {
            LogUtil.e(requestId, "HTTP: IO failure", e);
            throw new MmsHttpException(0/*statusCode*/, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void maybeWaitForIpv4(final String requestId, final URL url) {
        // If it's a literal IPv4 address and we're on an IPv6-only network,
        // wait until IPv4 is available.
        Inet4Address ipv4Literal = null;
        try {
            ipv4Literal = (Inet4Address) InetAddress.parseNumericAddress(url.getHost());
        } catch (IllegalArgumentException | ClassCastException e) {
            // Ignore
        }
        if (ipv4Literal == null) {
            // Not an IPv4 address.
            return;
        }
        for (int i = 0; i < IPV4_WAIT_ATTEMPTS; i++) {
            final LinkProperties lp = mConnectivityManager.getLinkProperties(mNetwork);
            if (lp != null) {
                if (!lp.isReachable(ipv4Literal)) {
                    LogUtil.w(requestId, "HTTP: IPv4 not yet provisioned");
                    try {
                        Thread.sleep(IPV4_WAIT_DELAY_MS);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                } else {
                    LogUtil.i(requestId, "HTTP: IPv4 provisioned");
                    break;
                }
            } else {
                LogUtil.w(requestId, "HTTP: network disconnected, skip ipv4 check");
                break;
            }
        }
    }

    private static void logHttpHeaders(Map<String, List<String>> headers, String requestId) {
        final StringBuilder sb = new StringBuilder();
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                final String key = entry.getKey();
                final List<String> values = entry.getValue();
                if (values != null) {
                    for (String value : values) {
                        sb.append(key).append('=').append(value).append('\n');
                    }
                }
            }
            LogUtil.v(requestId, "HTTP: headers\n" + sb.toString());
        }
    }

    private static void checkMethod(String method) throws MmsHttpException {
        if (!METHOD_GET.equals(method) && !METHOD_POST.equals(method)) {
            throw new MmsHttpException(0/*statusCode*/, "Invalid method " + method);
        }
    }

    private static final String ACCEPT_LANG_FOR_US_LOCALE = "en-US";

    /**
     * Return the Accept-Language header.  Use the current locale plus
     * US if we are in a different locale than US.
     * This code copied from the browser's WebSettings.java
     *
     * @return Current AcceptLanguage String.
     */
    public static String getCurrentAcceptLanguage(Locale locale) {
        final StringBuilder buffer = new StringBuilder();
        addLocaleToHttpAcceptLanguage(buffer, locale);

        if (!Locale.US.equals(locale)) {
            if (buffer.length() > 0) {
                buffer.append(", ");
            }
            buffer.append(ACCEPT_LANG_FOR_US_LOCALE);
        }

        return buffer.toString();
    }

    /**
     * Convert obsolete language codes, including Hebrew/Indonesian/Yiddish,
     * to new standard.
     */
    private static String convertObsoleteLanguageCodeToNew(String langCode) {
        if (langCode == null) {
            return null;
        }
        if ("iw".equals(langCode)) {
            // Hebrew
            return "he";
        } else if ("in".equals(langCode)) {
            // Indonesian
            return "id";
        } else if ("ji".equals(langCode)) {
            // Yiddish
            return "yi";
        }
        return langCode;
    }

    private static void addLocaleToHttpAcceptLanguage(StringBuilder builder, Locale locale) {
        final String language = convertObsoleteLanguageCodeToNew(locale.getLanguage());
        if (language != null) {
            builder.append(language);
            final String country = locale.getCountry();
            if (country != null) {
                builder.append("-");
                builder.append(country);
            }
        }
    }

    /**
     * Add extra HTTP headers from mms_config.xml's httpParams, which is a list of key/value
     * pairs separated by "|". Each key/value pair is separated by ":". Value may contain
     * macros like "##LINE1##" or "##NAI##" which is resolved with methods in this class
     *
     * @param connection The HttpURLConnection that we add headers to
     * @param mmsConfig The MmsConfig object
     * @param subId The subscription ID used to get line number, etc.
     */
    private void addExtraHeaders(HttpURLConnection connection, Bundle mmsConfig, int subId) {
        final String extraHttpParams = mmsConfig.getString(SmsManager.MMS_CONFIG_HTTP_PARAMS);
        if (!TextUtils.isEmpty(extraHttpParams)) {
            // Parse the parameter list
            String paramList[] = extraHttpParams.split("\\|");
            for (String paramPair : paramList) {
                String splitPair[] = paramPair.split(":", 2);
                if (splitPair.length == 2) {
                    final String name = splitPair[0].trim();
                    final String value =
                            resolveMacro(mContext, splitPair[1].trim(), mmsConfig, subId);
                    if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(value)) {
                        // Add the header if the param is valid
                        connection.setRequestProperty(name, value);
                    }
                }
            }
        }
    }

    private static final Pattern MACRO_P = Pattern.compile("##(\\S+)##");
    /**
     * Resolve the macro in HTTP param value text
     * For example, "something##LINE1##something" is resolved to "something9139531419something"
     *
     * @param value The HTTP param value possibly containing macros
     * @param subId The subscription ID used to get line number, etc.
     * @return The HTTP param with macros resolved to real value
     */
    private static String resolveMacro(Context context, String value, Bundle mmsConfig, int subId) {
        if (TextUtils.isEmpty(value)) {
            return value;
        }
        final Matcher matcher = MACRO_P.matcher(value);
        int nextStart = 0;
        StringBuilder replaced = null;
        while (matcher.find()) {
            if (replaced == null) {
                replaced = new StringBuilder();
            }
            final int matchedStart = matcher.start();
            if (matchedStart > nextStart) {
                replaced.append(value.substring(nextStart, matchedStart));
            }
            final String macro = matcher.group(1);
            final String macroValue = getMacroValue(context, macro, mmsConfig, subId);
            if (macroValue != null) {
                replaced.append(macroValue);
            }
            nextStart = matcher.end();
        }
        if (replaced != null && nextStart < value.length()) {
            replaced.append(value.substring(nextStart));
        }
        return replaced == null ? value : replaced.toString();
    }

    /**
     * Redact the URL for non-VERBOSE logging. Replace url with only the host part and the length
     * of the input URL string.
     *
     * @param urlString
     * @return
     */
    public static String redactUrlForNonVerbose(String urlString) {
        if (LogUtil.isLoggable(Log.VERBOSE)) {
            // Don't redact for VERBOSE level logging
            return urlString;
        }
        if (TextUtils.isEmpty(urlString)) {
            return urlString;
        }
        String protocol = "http";
        String host = "";
        try {
            final URL url = new URL(urlString);
            protocol = url.getProtocol();
            host = url.getHost();
        } catch (MalformedURLException e) {
            // Ignore
        }
        // Print "http://host[length]"
        final StringBuilder sb = new StringBuilder();
        sb.append(protocol).append("://").append(host)
                .append("[").append(urlString.length()).append("]");
        return sb.toString();
    }

    /*
     * Macro names
     */
    // The raw phone number from TelephonyManager.getLine1Number
    private static final String MACRO_LINE1 = "LINE1";
    // The phone number without country code
    private static final String MACRO_LINE1NOCOUNTRYCODE = "LINE1NOCOUNTRYCODE";
    // NAI (Network Access Identifier), used by Sprint for authentication
    private static final String MACRO_NAI = "NAI";
    /**
     * Return the HTTP param macro value.
     * Example: "LINE1" returns the phone number, etc.
     *
     * @param macro The macro name
     * @param mmsConfig The MMS config which contains NAI suffix.
     * @param subId The subscription ID used to get line number, etc.
     * @return The value of the defined macro
     */
    private static String getMacroValue(Context context, String macro, Bundle mmsConfig,
            int subId) {
        if (MACRO_LINE1.equals(macro)) {
            return getLine1(context, subId);
        } else if (MACRO_LINE1NOCOUNTRYCODE.equals(macro)) {
            return getLine1NoCountryCode(context, subId);
        } else if (MACRO_NAI.equals(macro)) {
            return getNai(context, mmsConfig, subId);
        }
        LogUtil.e("Invalid macro " + macro);
        return null;
    }

    /**
     * Returns the phone number for the given subscription ID.
     */
    private static String getLine1(Context context, int subId) {
        final TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        return telephonyManager.getLine1Number(subId);
    }

    /**
     * Returns the phone number (without country code) for the given subscription ID.
     */
    private static String getLine1NoCountryCode(Context context, int subId) {
        final TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        return PhoneUtils.getNationalNumber(
                telephonyManager,
                subId,
                telephonyManager.getLine1Number(subId));
    }

    /**
     * Returns the NAI (Network Access Identifier) from SystemProperties for the given subscription
     * ID.
     */
    private static String getNai(Context context, Bundle mmsConfig, int subId) {
        final TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        String nai = telephonyManager.getNai(SubscriptionManager.getSlotIndex(subId));
        if (LogUtil.isLoggable(Log.VERBOSE)) {
            LogUtil.v("getNai: nai=" + nai);
        }

        if (!TextUtils.isEmpty(nai)) {
            String naiSuffix = mmsConfig.getString(SmsManager.MMS_CONFIG_NAI_SUFFIX);
            if (!TextUtils.isEmpty(naiSuffix)) {
                nai = nai + naiSuffix;
            }
            byte[] encoded = null;
            try {
                encoded = Base64.encode(nai.getBytes("UTF-8"), Base64.NO_WRAP);
            } catch (UnsupportedEncodingException e) {
                encoded = Base64.encode(nai.getBytes(), Base64.NO_WRAP);
            }
            try {
                nai = new String(encoded, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                nai = new String(encoded);
            }
        }
        return nai;
    }
}
