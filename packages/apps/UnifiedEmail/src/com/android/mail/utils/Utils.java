/**
 * Copyright (c) 2011, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Fragment;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Browser;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.android.emailcommon.mail.Address;
import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.perf.SimpleTimer;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.EditSettingsExtras;
import com.android.mail.ui.HelpActivity;
import com.google.android.mail.common.html.parser.HtmlDocument;
import com.google.android.mail.common.html.parser.HtmlParser;
import com.google.android.mail.common.html.parser.HtmlTree;
import com.google.android.mail.common.html.parser.HtmlTreeBuilder;

import org.json.JSONObject;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;

public class Utils {
    /**
     * longest extension we recognize is 4 characters (e.g. "html", "docx")
     */
    private static final int FILE_EXTENSION_MAX_CHARS = 4;
    public static final String SENDER_LIST_TOKEN_ELIDED = "e";
    public static final String SENDER_LIST_TOKEN_NUM_MESSAGES = "n";
    public static final String SENDER_LIST_TOKEN_NUM_DRAFTS = "d";
    public static final String SENDER_LIST_TOKEN_LITERAL = "l";
    public static final String SENDER_LIST_TOKEN_SENDING = "s";
    public static final String SENDER_LIST_TOKEN_SEND_FAILED = "f";
    public static final Character SENDER_LIST_SEPARATOR = '\n';

    public static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_ACCOUNT_URI = "accountUri";
    public static final String EXTRA_FOLDER_URI = "folderUri";
    public static final String EXTRA_FOLDER = "folder";
    public static final String EXTRA_COMPOSE_URI = "composeUri";
    public static final String EXTRA_CONVERSATION = "conversationUri";
    public static final String EXTRA_FROM_NOTIFICATION = "notification";
    public static final String EXTRA_IGNORE_INITIAL_CONVERSATION_LIMIT =
            "ignore-initial-conversation-limit";

    public static final String MAILTO_SCHEME = "mailto";

    /** Extra tag for debugging the blank fragment problem. */
    public static final String VIEW_DEBUGGING_TAG = "MailBlankFragment";

    /*
     * Notifies that changes happened. Certain UI components, e.g., widgets, can
     * register for this {@link Intent} and update accordingly. However, this
     * can be very broad and is NOT the preferred way of getting notification.
     */
    // TODO: UI Provider has this notification URI?
    public static final String ACTION_NOTIFY_DATASET_CHANGED =
            "com.android.mail.ACTION_NOTIFY_DATASET_CHANGED";

    /** Parameter keys for context-aware help. */
    private static final String SMART_HELP_LINK_PARAMETER_NAME = "p";

    private static final String SMART_LINK_APP_VERSION = "version";
    private static String sVersionCode = null;

    private static final int SCALED_SCREENSHOT_MAX_HEIGHT_WIDTH = 600;

    private static final String APP_VERSION_QUERY_PARAMETER = "appVersion";
    private static final String FOLDER_URI_QUERY_PARAMETER = "folderUri";

    private static final String LOG_TAG = LogTag.getLogTag();

    public static final boolean ENABLE_CONV_LOAD_TIMER = false;
    public static final SimpleTimer sConvLoadTimer =
            new SimpleTimer(ENABLE_CONV_LOAD_TIMER).withSessionName("ConvLoadTimer");

    public static boolean isRunningJellybeanOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    public static boolean isRunningJBMR1OrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    public static boolean isRunningKitkatOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    public static boolean isRunningLOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public static boolean isRunningNOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    /**
     * @return Whether we are running on a low memory device.  This is used to disable certain
     * memory intensive features in the app.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean isLowRamDevice(Context context) {
        if (isRunningKitkatOrLater()) {
            final ActivityManager am = (ActivityManager) context.getSystemService(
                    Context.ACTIVITY_SERVICE);
            // This will be null when running unit tests
            return am != null && am.isLowRamDevice();
        } else {
            return false;
        }
    }

    /**
     * Sets WebView in a restricted mode suitable for email use.
     *
     * @param webView The WebView to restrict
     */
    public static void restrictWebView(WebView webView) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setSavePassword(false);
        webSettings.setSaveFormData(false);
        webSettings.setJavaScriptEnabled(false);
        webSettings.setSupportZoom(false);
    }

    /**
     * Sets custom user agent to WebView so we don't get GAIA interstitials b/13990689.
     *
     * @param webView The WebView to customize.
     */
    public static void setCustomUserAgent(WebView webView, Context context) {
        final WebSettings settings = webView.getSettings();
        final String version = getVersionCode(context);
        final String originalUserAgent = settings.getUserAgentString();
        final String userAgent = context.getResources().getString(
                R.string.user_agent_format, originalUserAgent, version);
        settings.setUserAgentString(userAgent);
    }

    /**
     * Returns the version code for the package, or null if it cannot be retrieved.
     */
    public static String getVersionCode(Context context) {
        if (sVersionCode == null) {
            try {
                sVersionCode = String.valueOf(context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0 /* flags */)
                        .versionCode);
            } catch (NameNotFoundException e) {
                LogUtils.e(Utils.LOG_TAG, "Error finding package %s",
                        context.getApplicationInfo().packageName);
            }
        }
        return sVersionCode;
    }

    /**
     * Format a plural string.
     *
     * @param resource The identity of the resource, which must be a R.plurals
     * @param count The number of items.
     */
    public static String formatPlural(Context context, int resource, int count) {
        final CharSequence formatString = context.getResources().getQuantityText(resource, count);
        return String.format(formatString.toString(), count);
    }

    /**
     * @return an ellipsized String that's at most maxCharacters long. If the
     *         text passed is longer, it will be abbreviated. If it contains a
     *         suffix, the ellipses will be inserted in the middle and the
     *         suffix will be preserved.
     */
    public static String ellipsize(String text, int maxCharacters) {
        int length = text.length();
        if (length < maxCharacters)
            return text;

        int realMax = Math.min(maxCharacters, length);
        // Preserve the suffix if any
        int index = text.lastIndexOf(".");
        String extension = "\u2026"; // "...";
        if (index >= 0) {
            // Limit the suffix to dot + four characters
            if (length - index <= FILE_EXTENSION_MAX_CHARS + 1) {
                extension = extension + text.substring(index + 1);
            }
        }
        realMax -= extension.length();
        if (realMax < 0)
            realMax = 0;
        return text.substring(0, realMax) + extension;
    }

    /**
     * This lock must be held before accessing any of the following fields
     */
    private static final Object sStaticResourcesLock = new Object();
    private static ComponentCallbacksListener sComponentCallbacksListener;
    private static int sMaxUnreadCount = -1;
    private static String sUnreadText;
    private static String sUnseenText;
    private static String sLargeUnseenText;
    private static int sDefaultFolderBackgroundColor = -1;

    private static class ComponentCallbacksListener implements ComponentCallbacks {

        @Override
        public void onConfigurationChanged(Configuration configuration) {
            synchronized (sStaticResourcesLock) {
                sMaxUnreadCount = -1;
                sUnreadText = null;
                sUnseenText = null;
                sLargeUnseenText = null;
                sDefaultFolderBackgroundColor = -1;
            }
        }

        @Override
        public void onLowMemory() {}
    }

    public static void getStaticResources(Context context) {
        synchronized (sStaticResourcesLock) {
            if (sUnreadText == null) {
                final Resources r = context.getResources();
                sMaxUnreadCount = r.getInteger(R.integer.maxUnreadCount);
                sUnreadText = r.getString(R.string.widget_large_unread_count);
                sUnseenText = r.getString(R.string.unseen_count);
                sLargeUnseenText = r.getString(R.string.large_unseen_count);
                sDefaultFolderBackgroundColor = r.getColor(R.color.default_folder_background_color);

                if (sComponentCallbacksListener == null) {
                    sComponentCallbacksListener = new ComponentCallbacksListener();
                    context.getApplicationContext()
                            .registerComponentCallbacks(sComponentCallbacksListener);
                }
            }
        }
    }

    private static int getMaxUnreadCount(Context context) {
        synchronized (sStaticResourcesLock) {
            getStaticResources(context);
            return sMaxUnreadCount;
        }
    }

    private static String getUnreadText(Context context) {
        synchronized (sStaticResourcesLock) {
            getStaticResources(context);
            return sUnreadText;
        }
    }

    private static String getUnseenText(Context context) {
        synchronized (sStaticResourcesLock) {
            getStaticResources(context);
            return sUnseenText;
        }
    }

    private static String getLargeUnseenText(Context context) {
        synchronized (sStaticResourcesLock) {
            getStaticResources(context);
            return sLargeUnseenText;
        }
    }

    public static int getDefaultFolderBackgroundColor(Context context) {
        synchronized (sStaticResourcesLock) {
            getStaticResources(context);
            return sDefaultFolderBackgroundColor;
        }
    }

    /**
     * Returns a boolean indicating whether the table UI should be shown.
     */
    public static boolean useTabletUI(Resources res) {
        return res.getBoolean(R.bool.use_tablet_ui);
    }

    /**
     * Returns displayable text from the provided HTML string.
     * @param htmlText HTML string
     * @return Plain text string representation of the specified Html string
     */
    public static String convertHtmlToPlainText(String htmlText) {
        if (TextUtils.isEmpty(htmlText)) {
            return "";
        }
        return getHtmlTree(htmlText, new HtmlParser(), new HtmlTreeBuilder()).getPlainText();
    }

    public static String convertHtmlToPlainText(String htmlText, HtmlParser parser,
            HtmlTreeBuilder builder) {
        if (TextUtils.isEmpty(htmlText)) {
            return "";
        }
        return getHtmlTree(htmlText, parser, builder).getPlainText();
    }

    /**
     * Returns a {@link HtmlTree} representation of the specified HTML string.
     */
    public static HtmlTree getHtmlTree(String htmlText) {
        return getHtmlTree(htmlText, new HtmlParser(), new HtmlTreeBuilder());
    }

    /**
     * Returns a {@link HtmlTree} representation of the specified HTML string.
     */
    private static HtmlTree getHtmlTree(String htmlText, HtmlParser parser,
            HtmlTreeBuilder builder) {
        final HtmlDocument doc = parser.parse(htmlText);
        doc.accept(builder);

        return builder.getTree();
    }

    /**
     * Perform a simulated measure pass on the given child view, assuming the
     * child has a ViewGroup parent and that it should be laid out within that
     * parent with a matching width but variable height. Code largely lifted
     * from AnimatedAdapter.measureChildHeight().
     *
     * @param child a child view that has already been placed within its parent
     *            ViewGroup
     * @param parent the parent ViewGroup of child
     * @return measured height of the child in px
     */
    public static int measureViewHeight(View child, ViewGroup parent) {
        final ViewGroup.LayoutParams lp = child.getLayoutParams();
        final int childSideMargin;
        if (lp instanceof MarginLayoutParams) {
            final MarginLayoutParams mlp = (MarginLayoutParams) lp;
            childSideMargin = mlp.leftMargin + mlp.rightMargin;
        } else {
            childSideMargin = 0;
        }

        final int parentWSpec = MeasureSpec.makeMeasureSpec(parent.getWidth(), MeasureSpec.EXACTLY);
        final int wSpec = ViewGroup.getChildMeasureSpec(parentWSpec,
                parent.getPaddingLeft() + parent.getPaddingRight() + childSideMargin,
                ViewGroup.LayoutParams.MATCH_PARENT);
        final int hSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        child.measure(wSpec, hSpec);
        return child.getMeasuredHeight();
    }

    /**
     * Encode the string in HTML.
     *
     * @param removeEmptyDoubleQuotes If true, also remove any occurrence of ""
     *            found in the string
     */
    public static Object cleanUpString(String string, boolean removeEmptyDoubleQuotes) {
        return !TextUtils.isEmpty(string) ? TextUtils.htmlEncode(removeEmptyDoubleQuotes ? string
                .replace("\"\"", "") : string) : "";
    }

    /**
     * Get the correct display string for the unread count of a folder.
     */
    public static String getUnreadCountString(Context context, int unreadCount) {
        final String unreadCountString;
        final int maxUnreadCount = getMaxUnreadCount(context);
        if (unreadCount > maxUnreadCount) {
            final String unreadText = getUnreadText(context);
            // Localize "99+" according to the device language
            unreadCountString = String.format(unreadText, maxUnreadCount);
        } else if (unreadCount <= 0) {
            unreadCountString = "";
        } else {
            // Localize unread count according to the device language
            unreadCountString = String.format("%d", unreadCount);
        }
        return unreadCountString;
    }

    /**
     * Get the correct display string for the unseen count of a folder.
     */
    public static String getUnseenCountString(Context context, int unseenCount) {
        final String unseenCountString;
        final int maxUnreadCount = getMaxUnreadCount(context);
        if (unseenCount > maxUnreadCount) {
            final String largeUnseenText = getLargeUnseenText(context);
            // Localize "99+" according to the device language
            unseenCountString = String.format(largeUnseenText, maxUnreadCount);
        } else if (unseenCount <= 0) {
            unseenCountString = "";
        } else {
            // Localize unseen count according to the device language
            unseenCountString = String.format(getUnseenText(context), unseenCount);
        }
        return unseenCountString;
    }

    /**
     * Get text matching the last sync status.
     */
    public static CharSequence getSyncStatusText(Context context, int packedStatus) {
        final String[] errors = context.getResources().getStringArray(R.array.sync_status);
        final int status = packedStatus & 0x0f;
        if (status >= errors.length) {
            return "";
        }
        return errors[status];
    }

    /**
     * Create an intent to show a conversation.
     * @param conversation Conversation to open.
     * @param folderUri
     * @param account
     * @return
     */
    public static Intent createViewConversationIntent(final Context context,
            Conversation conversation, final Uri folderUri, Account account) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        final Uri versionedUri = appendVersionQueryParameter(context, conversation.uri);
        // We need the URI to be unique, even if it's for the same message, so append the folder URI
        final Uri uniqueUri = versionedUri.buildUpon().appendQueryParameter(
                FOLDER_URI_QUERY_PARAMETER, folderUri.toString()).build();
        intent.setDataAndType(uniqueUri, account.mimeType);
        intent.putExtra(Utils.EXTRA_ACCOUNT, account.serialize());
        intent.putExtra(Utils.EXTRA_FOLDER_URI, folderUri);
        intent.putExtra(Utils.EXTRA_CONVERSATION, conversation);
        return intent;
    }

    /**
     * Create an intent to open a folder.
     *
     * @param folderUri Folder to open.
     * @param account
     * @return
     */
    public static Intent createViewFolderIntent(final Context context, final Uri folderUri,
            Account account) {
        if (folderUri == null || account == null) {
            LogUtils.wtf(LOG_TAG, "Utils.createViewFolderIntent(%s,%s): Bad input", folderUri,
                    account);
            return null;
        }
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        intent.setDataAndType(appendVersionQueryParameter(context, folderUri), account.mimeType);
        intent.putExtra(Utils.EXTRA_ACCOUNT, account.serialize());
        intent.putExtra(Utils.EXTRA_FOLDER_URI, folderUri);
        return intent;
    }

    /**
     * Creates an intent to open the default inbox for the given account.
     *
     * @param account
     * @return
     */
    public static Intent createViewInboxIntent(Account account) {
        if (account == null) {
            LogUtils.wtf(LOG_TAG, "Utils.createViewInboxIntent(%s): Bad input", account);
            return null;
        }
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        intent.setDataAndType(account.settings.defaultInbox, account.mimeType);
        intent.putExtra(Utils.EXTRA_ACCOUNT, account.serialize());
        return intent;
    }

    /**
     * Helper method to show context-aware help.
     *
     * @param context Context to be used to open the help.
     * @param account Account from which the help URI is extracted
     * @param helpTopic Information about the activity the user was in
     *      when they requested help which specifies the help topic to display
     */
    public static void showHelp(Context context, Account account, String helpTopic) {
        final String urlString = account.helpIntentUri != null ?
                account.helpIntentUri.toString() : null;
        if (TextUtils.isEmpty(urlString)) {
            LogUtils.e(LOG_TAG, "unable to show help for account: %s", account);
            return;
        }
        showHelp(context, account.helpIntentUri, helpTopic);
    }

    /**
     * Helper method to show context-aware help.
     *
     * @param context Context to be used to open the help.
     * @param helpIntentUri URI of the help content to display
     * @param helpTopic Information about the activity the user was in
     *      when they requested help which specifies the help topic to display
     */
    public static void showHelp(Context context, Uri helpIntentUri, String helpTopic) {
        final String urlString = helpIntentUri == null ? null : helpIntentUri.toString();
        if (TextUtils.isEmpty(urlString)) {
            LogUtils.e(LOG_TAG, "unable to show help for help URI: %s", helpIntentUri);
            return;
        }

        // generate the full URL to the requested help section
        final Uri helpUrl = HelpUrl.getHelpUrl(context, helpIntentUri, helpTopic);

        final boolean useBrowser = context.getResources().getBoolean(R.bool.openHelpWithBrowser);
        if (useBrowser) {
            // open a browser with the full help URL
            openUrl(context, helpUrl, null);
        } else {
            // start the help activity with the full help URL
            final Intent intent = new Intent(context, HelpActivity.class);
            intent.putExtra(HelpActivity.PARAM_HELP_URL, helpUrl);
            context.startActivity(intent);
        }
    }

    /**
     * Helper method to open a link in a browser.
     *
     * @param context Context
     * @param uri Uri to open.
     */
    private static void openUrl(Context context, Uri uri, Bundle optionalExtras) {
        if(uri == null || TextUtils.isEmpty(uri.toString())) {
            LogUtils.wtf(LOG_TAG, "invalid url in Utils.openUrl(): %s", uri);
            return;
        }
        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        // Fill in any of extras that have been requested.
        if (optionalExtras != null) {
            intent.putExtras(optionalExtras);
        }
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        context.startActivity(intent);
    }

    /**
     * Show the top level settings screen for the supplied account.
     */
    public static void showSettings(Context context, Account account) {
        if (account == null) {
            LogUtils.e(LOG_TAG, "Invalid attempt to show setting screen with null account");
            return;
        }
        final Intent settingsIntent = new Intent(Intent.ACTION_EDIT, account.settingsIntentUri);

        settingsIntent.setPackage(context.getPackageName());
        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        context.startActivity(settingsIntent);
    }

    /**
     * Show the account level settings screen for the supplied account.
     */
    public static void showAccountSettings(Context context, Account account) {
        if (account == null) {
            LogUtils.e(LOG_TAG, "Invalid attempt to show setting screen with null account");
            return;
        }
        final Intent settingsIntent = new Intent(Intent.ACTION_EDIT,
                appendVersionQueryParameter(context, account.settingsIntentUri));

        settingsIntent.setPackage(context.getPackageName());
        settingsIntent.putExtra(EditSettingsExtras.EXTRA_ACCOUNT, account);
        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        context.startActivity(settingsIntent);
    }

    /**
     * Show the feedback screen for the supplied account.
     */
    public static void sendFeedback(Activity activity, Account account, boolean reportingProblem) {
        if (activity != null && account != null) {
            sendFeedback(activity, account.sendFeedbackIntentUri, reportingProblem);
        }
    }

    public static void sendFeedback(Activity activity, Uri feedbackIntentUri,
            boolean reportingProblem) {
        if (activity != null &&  !isEmpty(feedbackIntentUri)) {
            final Bundle optionalExtras = new Bundle(2);
            optionalExtras.putBoolean(
                    UIProvider.SendFeedbackExtras.EXTRA_REPORTING_PROBLEM, reportingProblem);
            final Bitmap screenBitmap = getReducedSizeBitmap(activity);
            if (screenBitmap != null) {
                optionalExtras.putParcelable(
                        UIProvider.SendFeedbackExtras.EXTRA_SCREEN_SHOT, screenBitmap);
            }
            openUrl(activity, feedbackIntentUri, optionalExtras);
        }
    }

    private static Bitmap getReducedSizeBitmap(Activity activity) {
        final Window activityWindow = activity.getWindow();
        final View currentView = activityWindow != null ? activityWindow.getDecorView() : null;
        final View rootView = currentView != null ? currentView.getRootView() : null;
        if (rootView != null) {
            rootView.setDrawingCacheEnabled(true);
            final Bitmap drawingCache = rootView.getDrawingCache();
            // Null check to avoid NPE discovered from monkey crash:
            if (drawingCache != null) {
                try {
                    final Bitmap originalBitmap = drawingCache.copy(Bitmap.Config.RGB_565, false);
                    double originalHeight = originalBitmap.getHeight();
                    double originalWidth = originalBitmap.getWidth();
                    int newHeight = SCALED_SCREENSHOT_MAX_HEIGHT_WIDTH;
                    int newWidth = SCALED_SCREENSHOT_MAX_HEIGHT_WIDTH;
                    double scaleX, scaleY;
                    scaleX = newWidth  / originalWidth;
                    scaleY = newHeight / originalHeight;
                    final double scale = Math.min(scaleX, scaleY);
                    newWidth = (int)Math.round(originalWidth * scale);
                    newHeight = (int)Math.round(originalHeight * scale);
                    return Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);
                } catch (OutOfMemoryError e) {
                    LogUtils.e(LOG_TAG, e, "OOME when attempting to scale screenshot");
                }
            }
        }
        return null;
    }

    /**
     * Split out a filename's extension and return it.
     * @param filename a file name
     * @return the file extension (max of 5 chars including period, like ".docx"), or null
     */
    public static String getFileExtension(String filename) {
        String extension = null;
        int index = !TextUtils.isEmpty(filename) ? filename.lastIndexOf('.') : -1;
        // Limit the suffix to dot + four characters
        if (index >= 0 && filename.length() - index <= FILE_EXTENSION_MAX_CHARS + 1) {
            extension = filename.substring(index);
        }
        return extension;
    }

   /**
    * (copied from {@link Intent#normalizeMimeType(String)} for pre-J)
    *
    * Normalize a MIME data type.
    *
    * <p>A normalized MIME type has white-space trimmed,
    * content-type parameters removed, and is lower-case.
    * This aligns the type with Android best practices for
    * intent filtering.
    *
    * <p>For example, "text/plain; charset=utf-8" becomes "text/plain".
    * "text/x-vCard" becomes "text/x-vcard".
    *
    * <p>All MIME types received from outside Android (such as user input,
    * or external sources like Bluetooth, NFC, or the Internet) should
    * be normalized before they are used to create an Intent.
    *
    * @param type MIME data type to normalize
    * @return normalized MIME data type, or null if the input was null
    * @see {@link android.content.Intent#setType}
    * @see {@link android.content.Intent#setTypeAndNormalize}
    */
   public static String normalizeMimeType(String type) {
       if (type == null) {
           return null;
       }

       type = type.trim().toLowerCase(Locale.US);

       final int semicolonIndex = type.indexOf(';');
       if (semicolonIndex != -1) {
           type = type.substring(0, semicolonIndex);
       }
       return type;
   }

   /**
    * (copied from {@link android.net.Uri#normalizeScheme()} for pre-J)
    *
    * Return a normalized representation of this Uri.
    *
    * <p>A normalized Uri has a lowercase scheme component.
    * This aligns the Uri with Android best practices for
    * intent filtering.
    *
    * <p>For example, "HTTP://www.android.com" becomes
    * "http://www.android.com"
    *
    * <p>All URIs received from outside Android (such as user input,
    * or external sources like Bluetooth, NFC, or the Internet) should
    * be normalized before they are used to create an Intent.
    *
    * <p class="note">This method does <em>not</em> validate bad URI's,
    * or 'fix' poorly formatted URI's - so do not use it for input validation.
    * A Uri will always be returned, even if the Uri is badly formatted to
    * begin with and a scheme component cannot be found.
    *
    * @return normalized Uri (never null)
    * @see {@link android.content.Intent#setData}
    */
   public static Uri normalizeUri(Uri uri) {
       String scheme = uri.getScheme();
       if (scheme == null) return uri;  // give up
       String lowerScheme = scheme.toLowerCase(Locale.US);
       if (scheme.equals(lowerScheme)) return uri;  // no change

       return uri.buildUpon().scheme(lowerScheme).build();
   }

   public static Intent setIntentTypeAndNormalize(Intent intent, String type) {
       return intent.setType(normalizeMimeType(type));
   }

   public static Intent setIntentDataAndTypeAndNormalize(Intent intent, Uri data, String type) {
       return intent.setDataAndType(normalizeUri(data), normalizeMimeType(type));
   }

   public static int getTransparentColor(int color) {
       return 0x00ffffff & color;
   }

    /**
     * Note that this function sets both the visibility and enabled flags for the menu item so that
     * if shouldShow is false then the menu item is also no longer valid for keyboard shortcuts.
     */
    public static void setMenuItemPresent(Menu menu, int itemId, boolean shouldShow) {
        setMenuItemPresent(menu.findItem(itemId), shouldShow);
    }

    /**
     * Note that this function sets both the visibility and enabled flags for the menu item so that
     * if shouldShow is false then the menu item is also no longer valid for keyboard shortcuts.
     */
    public static void setMenuItemPresent(MenuItem item, boolean shouldShow) {
        if (item == null) {
            return;
        }
        item.setVisible(shouldShow);
        item.setEnabled(shouldShow);
    }

    /**
     * Parse a string (possibly null or empty) into a URI. If the string is null
     * or empty, null is returned back. Otherwise an empty URI is returned.
     *
     * @param uri
     * @return a valid URI, possibly {@link android.net.Uri#EMPTY}
     */
    public static Uri getValidUri(String uri) {
        if (TextUtils.isEmpty(uri) || uri == JSONObject.NULL)
            return Uri.EMPTY;
        return Uri.parse(uri);
    }

    public static boolean isEmpty(Uri uri) {
        return uri == null || Uri.EMPTY.equals(uri);
    }

    public static String dumpFragment(Fragment f) {
        final StringWriter sw = new StringWriter();
        f.dump("", new FileDescriptor(), new PrintWriter(sw), new String[0]);
        return sw.toString();
    }

    /**
     * Executes an out-of-band command on the cursor.
     * @param cursor
     * @param request Bundle with all keys and values set for the command.
     * @param key The string value against which we will check for success or failure
     * @return true if the operation was a success.
     */
    private static boolean executeConversationCursorCommand(
            Cursor cursor, Bundle request, String key) {
        final Bundle response = cursor.respond(request);
        final String result = response.getString(key,
                UIProvider.ConversationCursorCommand.COMMAND_RESPONSE_FAILED);

        return UIProvider.ConversationCursorCommand.COMMAND_RESPONSE_OK.equals(result);
    }

    /**
     * Commands a cursor representing a set of conversations to indicate that an item is being shown
     * in the UI.
     *
     * @param cursor a conversation cursor
     * @param position position of the item being shown.
     */
    public static boolean notifyCursorUIPositionChange(Cursor cursor, int position) {
        final Bundle request = new Bundle();
        final String key =
                UIProvider.ConversationCursorCommand.COMMAND_NOTIFY_CURSOR_UI_POSITION_CHANGE;
        request.putInt(key, position);
        return executeConversationCursorCommand(cursor, request, key);
    }

    /**
     * Commands a cursor representing a set of conversations to set its visibility state.
     *
     * @param cursor a conversation cursor
     * @param visible true if the conversation list is visible, false otherwise.
     * @param isFirstSeen true if you want to notify the cursor that this conversation list was seen
     *        for the first time: the user launched the app into it, or the user switched from some
     *        other folder into it.
     */
    public static void setConversationCursorVisibility(
            Cursor cursor, boolean visible, boolean isFirstSeen) {
        new MarkConversationCursorVisibleTask(cursor, visible, isFirstSeen).execute();
    }

    /**
     * Async task for  marking conversations "seen" and informing the cursor that the folder was
     * seen for the first time by the UI.
     */
    private static class MarkConversationCursorVisibleTask extends AsyncTask<Void, Void, Void> {
        private final Cursor mCursor;
        private final boolean mVisible;
        private final boolean mIsFirstSeen;

        /**
         * Create a new task with the given cursor, with the given visibility and
         *
         * @param cursor
         * @param isVisible true if the conversation list is visible, false otherwise.
         * @param isFirstSeen true if the folder was shown for the first time: either the user has
         *        just switched to it, or the user started the app in this folder.
         */
        public MarkConversationCursorVisibleTask(
                Cursor cursor, boolean isVisible, boolean isFirstSeen) {
            mCursor = cursor;
            mVisible = isVisible;
            mIsFirstSeen = isFirstSeen;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (mCursor == null) {
                return null;
            }
            final Bundle request = new Bundle();
            if (mIsFirstSeen) {
                request.putBoolean(
                        UIProvider.ConversationCursorCommand.COMMAND_KEY_ENTERED_FOLDER, true);
            }
            final String key = UIProvider.ConversationCursorCommand.COMMAND_KEY_SET_VISIBILITY;
            request.putBoolean(key, mVisible);
            executeConversationCursorCommand(mCursor, request, key);
            return null;
        }
    }


    /**
     * This utility method returns the conversation ID at the current cursor position.
     * @return the conversation id at the cursor.
     */
    public static long getConversationId(ConversationCursor cursor) {
        return cursor.getLong(UIProvider.CONVERSATION_ID_COLUMN);
    }

    /**
     * Sets the layer type of a view to hardware if the view is attached and hardware acceleration
     * is enabled. Does nothing otherwise.
     */
    public static void enableHardwareLayer(View v) {
        if (v != null && v.isHardwareAccelerated() &&
                v.getLayerType() != View.LAYER_TYPE_HARDWARE) {
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            v.buildLayer();
        }
    }

    /**
     * Returns the count that should be shown for the specified folder.  This method should be used
     * when the UI wants to display an "unread" count.  For most labels, the returned value will be
     * the unread count, but for some folder types (outbox, drafts, trash) this will return the
     * total count.
     */
    public static int getFolderUnreadDisplayCount(final Folder folder) {
        if (folder != null) {
            if (folder.supportsCapability(UIProvider.FolderCapabilities.UNSEEN_COUNT_ONLY)) {
                return 0;
            } else if (folder.isUnreadCountHidden()) {
                return folder.totalCount;
            } else {
                return folder.unreadCount;
            }
        }
        return 0;
    }

    public static Uri appendVersionQueryParameter(final Context context, final Uri uri) {
        return uri.buildUpon().appendQueryParameter(APP_VERSION_QUERY_PARAMETER,
                getVersionCode(context)).build();
    }

    /**
     * Convenience method for diverting mailto: uris directly to our compose activity. Using this
     * method ensures that the Account object is not accidentally sent to a different process.
     *
     * @param context for sending the intent
     * @param uri mailto: or other uri
     * @param account desired account for potential compose activity
     * @return true if a compose activity was started, false if uri should be sent to a view intent
     */
    public static boolean divertMailtoUri(final Context context, final Uri uri,
            final Account account) {
        final String scheme = normalizeUri(uri).getScheme();
        if (TextUtils.equals(MAILTO_SCHEME, scheme)) {
            ComposeActivity.composeMailto(context, account, uri);
            return true;
        }
        return false;
    }

    /**
     * Gets the specified {@link Folder} object.
     *
     * @param folderUri The {@link Uri} for the folder
     * @param allowHidden <code>true</code> to allow a hidden folder to be returned,
     *        <code>false</code> to return <code>null</code> instead
     * @return the specified {@link Folder} object, or <code>null</code>
     */
    public static Folder getFolder(final Context context, final Uri folderUri,
            final boolean allowHidden) {
        final Uri uri = folderUri
                .buildUpon()
                .appendQueryParameter(UIProvider.ALLOW_HIDDEN_FOLDERS_QUERY_PARAM,
                        Boolean.toString(allowHidden))
                .build();

        final Cursor cursor = context.getContentResolver().query(uri,
                UIProvider.FOLDERS_PROJECTION, null, null, null);

        if (cursor == null) {
            return null;
        }

        try {
            if (cursor.moveToFirst()) {
                return new Folder(cursor);
            } else {
                return null;
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Begins systrace tracing for a given tag. No-op on unsupported platform versions.
     *
     * @param tag systrace tag to use
     *
     * @see android.os.Trace#beginSection(String)
     */
    public static void traceBeginSection(String tag) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            android.os.Trace.beginSection(tag);
        }
    }

    /**
     * Ends systrace tracing for the most recently begun section. No-op on unsupported platform
     * versions.
     *
     * @see android.os.Trace#endSection()
     */
    public static void traceEndSection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            android.os.Trace.endSection();
        }
    }

    /**
     * Given a value and a set of upper-bounds to use as buckets, return the smallest upper-bound
     * that is greater than the value.<br>
     * <br>
     * Useful for turning a continuous value into one of a set of discrete ones.
     *
     * @param value a value to bucketize
     * @param upperBounds list of upper-bound buckets to clamp to, sorted from smallest-greatest
     * @return the smallest upper-bound larger than the value, or -1 if the value is larger than
     * all upper-bounds
     */
    public static long getUpperBound(long value, long[] upperBounds) {
        for (long ub : upperBounds) {
            if (value < ub) {
                return ub;
            }
        }
        return -1;
    }

    public static @Nullable Address getAddress(Map<String, Address> cache, String emailStr) {
        Address addr;
        synchronized (cache) {
            addr = cache.get(emailStr);
            if (addr == null) {
                addr = Address.getEmailAddress(emailStr);
                if (addr != null) {
                    cache.put(emailStr, addr);
                }
            }
        }
        return addr;
    }

    /**
     * Applies the given appearance on the given subString, and inserts that as a parameter in the
     * given parentString.
     */
    @VisibleForTesting
    public static Spanned insertStringWithStyle(Context context,
            String entireString, String subString, int appearance) {
        final int index = entireString.indexOf(subString);
        final SpannableString descriptionText = new SpannableString(entireString);
        if (index >= 0) {
            descriptionText.setSpan(
                    new TextAppearanceSpan(context, appearance),
                    index,
                    index + subString.length(),
                    0);
        }
        return descriptionText;
    }

    /**
     * Email addresses are supposed to be treated as case-insensitive for the host-part and
     * case-sensitive for the local-part, but nobody really wants email addresses to match
     * case-sensitive on the local-part, so just smash everything to lower case.
     * @param email Hello@Example.COM
     * @return hello@example.com
     */
    public static String normalizeEmailAddress(String email) {
        /*
        // The RFC5321 version
        if (TextUtils.isEmpty(email)) {
            return email;
        }
        String[] parts = email.split("@");
        if (parts.length != 2) {
            LogUtils.d(LOG_TAG, "Tried to normalize a malformed email address: ", email);
            return email;
        }

        return parts[0] + "@" + parts[1].toLowerCase(Locale.US);
        */
        if (TextUtils.isEmpty(email)) {
            return email;
        } else {
            // Doing this for other locales might really screw things up, so do US-version only
            return email.toLowerCase(Locale.US);
        }
    }

    /**
     * Returns whether the device currently has network connection. This does not guarantee that
     * the connection is reliable.
     */
    public static boolean isConnected(final Context context) {
        final ConnectivityManager connectivityManager =
                ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return (networkInfo != null) && networkInfo.isConnected();
    }
}
