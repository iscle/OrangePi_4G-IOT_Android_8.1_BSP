/*
 * Copyright (C) 2013 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.browse;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.widget.Toast;

import com.android.emailcommon.mail.Address;
import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.ui.AbstractConversationWebViewClient;
import com.android.mail.ui.ContactLoaderCallbacks;
import com.android.mail.ui.SecureConversationViewController;
import com.android.mail.ui.SecureConversationViewControllerCallbacks;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fragment that is used to view EML files. It is mostly stubs
 * that calls {@link SecureConversationViewController} to do most
 * of the rendering work.
 */
public class EmlMessageViewFragment extends Fragment
        implements SecureConversationViewControllerCallbacks {
    private static final String ARG_EML_FILE_URI = "eml_file_uri";
    private static final String BASE_URI = "x-thread://message/rfc822/";

    private static final int MESSAGE_LOADER = 0;
    private static final int CONTACT_LOADER = 1;
    private static final int FILENAME_LOADER = 2;

    private static final String LOG_TAG = LogTag.getLogTag();

    private final Handler mHandler = new Handler();

    private EmlWebViewClient mWebViewClient;
    private SecureConversationViewController mViewController;
    private ContactLoaderCallbacks mContactLoaderCallbacks;

    private final MessageLoadCallbacks mMessageLoadCallbacks = new MessageLoadCallbacks();
    private final FilenameLoadCallbacks mFilenameLoadCallbacks = new FilenameLoadCallbacks();

    private Uri mEmlFileUri;

    private boolean mMessageLoadFailed;

    /**
     * Cache of email address strings to parsed Address objects.
     * <p>
     * Remember to synchronize on the map when reading or writing to this cache, because some
     * instances use it off the UI thread (e.g. from WebView).
     */
    protected final Map<String, Address> mAddressCache = Collections.synchronizedMap(
            new HashMap<String, Address>());

    private class EmlWebViewClient extends AbstractConversationWebViewClient {
        public EmlWebViewClient(Account account) {
            super(account);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            // try to load the url assuming it is a cid url
            final Uri uri = Uri.parse(url);
            final WebResourceResponse response = loadCIDUri(uri, mViewController.getMessage());
            if (response != null) {
                return response;
            }

            return super.shouldInterceptRequest(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            // Ignore unsafe calls made after a fragment is detached from an activity.
            // This method needs to, for example, get at the loader manager, which needs
            // the fragment to be added.
            if (!isAdded()) {
                LogUtils.d(LOG_TAG, "ignoring EMLVF.onPageFinished, url=%s fragment=%s", url,
                        EmlMessageViewFragment.this);
                return;
            }
            mViewController.dismissLoadingStatus();

            final Set<String> emailAddresses = Sets.newHashSet();
            final List<Address> cacheCopy;
            synchronized (mAddressCache) {
                cacheCopy = ImmutableList.copyOf(mAddressCache.values());
            }
            for (Address addr : cacheCopy) {
                emailAddresses.add(addr.getAddress());
            }
            final ContactLoaderCallbacks callbacks = getContactInfoSource();
            callbacks.setSenders(emailAddresses);
            getLoaderManager().restartLoader(CONTACT_LOADER, Bundle.EMPTY, callbacks);
        }
    }

    /**
     * Creates a new instance of {@link EmlMessageViewFragment},
     * initialized to display an eml file from the specified {@link Uri}.
     */
    public static EmlMessageViewFragment newInstance(Uri emlFileUri, Uri accountUri) {
        EmlMessageViewFragment f = new EmlMessageViewFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(ARG_EML_FILE_URI, emlFileUri);
        f.setArguments(args);
        return f;
    }

    /**
     * Constructor needs to be public to handle orientation changes and activity
     * lifecycle events.
     */
    public EmlMessageViewFragment() {}

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        Bundle args = getArguments();
        mEmlFileUri = args.getParcelable(ARG_EML_FILE_URI);

        mWebViewClient = new EmlWebViewClient(null);
        mViewController = new SecureConversationViewController(this);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return mViewController.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mMessageLoadFailed) {
            bailOutOnLoadError();
            return;
        }
        mWebViewClient.setActivity(getActivity());
        mViewController.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (Utils.isRunningKitkatOrLater()) {
            inflater.inflate(R.menu.eml_fragment_menu, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.print_message) {
            mViewController.printMessage();
        } else {
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private void bailOutOnLoadError() {
        final Activity activity = getActivity();
        Toast.makeText(activity, R.string.eml_loader_error_toast, Toast.LENGTH_LONG).show();
        activity.finish();
    }

    // Start SecureConversationViewControllerCallbacks

    @Override
    public Handler getHandler() {
        return mHandler;
    }

    @Override
    public AbstractConversationWebViewClient getWebViewClient() {
        return mWebViewClient;
    }

    @Override
    public Fragment getFragment() {
        return this;
    }

    @Override
    public void setupConversationHeaderView(ConversationViewHeader headerView) {
        // DO NOTHING
    }

    @Override
    public boolean isViewVisibleToUser() {
        return true;
    }

    @Override
    public ContactLoaderCallbacks getContactInfoSource() {
        if (mContactLoaderCallbacks == null) {
            mContactLoaderCallbacks = new ContactLoaderCallbacks(getActivity());
        }
        return mContactLoaderCallbacks;
    }

    @Override
    public ConversationAccountController getConversationAccountController() {
        return (EmlViewerActivity) getActivity();
    }

    @Override
    public Map<String, Address> getAddressCache() {
        return mAddressCache;
    }

    @Override
    public void setupMessageHeaderVeiledMatcher(MessageHeaderView messageHeaderView) {
        // DO NOTHING
    }

    @Override
    public void startMessageLoader() {
        final LoaderManager manager = getLoaderManager();
        manager.initLoader(MESSAGE_LOADER, null, mMessageLoadCallbacks);
        manager.initLoader(FILENAME_LOADER, null, mFilenameLoadCallbacks);
    }

    @Override
    public String getBaseUri() {
        return BASE_URI;
    }

    @Override
    public boolean isViewOnlyMode() {
        return true;
    }

    @Override
    public boolean shouldAlwaysShowImages() {
        return false;
    }

    // End SecureConversationViewControllerCallbacks

    private class MessageLoadCallbacks
            implements LoaderManager.LoaderCallbacks<ConversationMessage> {
        @Override
        public Loader<ConversationMessage> onCreateLoader(int id, Bundle args) {
            switch (id) {
                case MESSAGE_LOADER:
                    return new EmlMessageLoader(getActivity(), mEmlFileUri);
                default:
                    return null;
            }
        }

        @Override
        public void onLoadFinished(Loader<ConversationMessage> loader, ConversationMessage data) {
            if (data == null) {
                final Activity activity = getActivity();
                if (activity != null) {
                    bailOutOnLoadError();
                } else {
                    mMessageLoadFailed = true;
                }
                return;
            }
            mViewController.setSubject(data.subject);
            mViewController.renderMessage(data);
        }

        @Override
        public void onLoaderReset(Loader<ConversationMessage> loader) {
            // Do nothing
        }
    }

    private class FilenameLoadCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            switch (id) {
                case FILENAME_LOADER:
                    return new CursorLoader(getActivity(), mEmlFileUri,
                            new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE },
                            null, null, null);
                default:
                    return null;
            }
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (data == null || !data.moveToFirst()) {
                return;
            }

            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(
                    data.getString(data.getColumnIndex(OpenableColumns.DISPLAY_NAME)));
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        }
    }

}
