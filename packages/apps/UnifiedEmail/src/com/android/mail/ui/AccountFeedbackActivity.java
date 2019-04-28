/*
 * Copyright (C) 2014 Google Inc.
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

package com.android.mail.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.android.mail.R;
import com.android.mail.browse.ConversationAccountController;
import com.android.mail.content.ObjectCursor;
import com.android.mail.providers.Account;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.Utils;

/**
 * Activity that provides support for querying an {@link Account}
 * as well as showing settings/help/send feedback in the action
 * overflow menu.
 */
public abstract class AccountFeedbackActivity extends AppCompatActivity
        implements ConversationAccountController, AccountLoadCallbacks.AccountLoadCallbackListener {
    public static final String EXTRA_ACCOUNT_URI = "extra-account-uri";

    private static final int ACCOUNT_LOADER = 0;

    private static final String SAVED_ACCOUNT = "saved-account";

    private MenuItem mHelpAndFeedbackItem;

    protected Uri mAccountUri;
    protected Account mAccount;

    private AccountLoadCallbacks mAccountLoadCallbacks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_feedback_activity);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        final Intent intent = getIntent();
        mAccountUri = intent.getParcelableExtra(EXTRA_ACCOUNT_URI);

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(SAVED_ACCOUNT)) {
                mAccount = savedInstanceState.getParcelable(SAVED_ACCOUNT);
            }
        }

        // Account uri will be null if we launched from outside of the app.
        // So just don't load an account at all.
        if (mAccountUri != null) {
            mAccountLoadCallbacks = new AccountLoadCallbacks(
                    this /* context */, mAccountUri, this /* accountLoadCallbackListener*/);
            getLoaderManager().initLoader(ACCOUNT_LOADER, Bundle.EMPTY, mAccountLoadCallbacks);
        }
    }

    @Override
    public void onAccountLoadCallbackFinished(ObjectCursor<Account> data) {
        if (data != null && data.moveToFirst()) {
            mAccount = data.getModel();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mAccountUri == null) {
            return false;
        }

        getMenuInflater().inflate(R.menu.account_feedback_menu, menu);
        mHelpAndFeedbackItem = menu.findItem(R.id.help_info_menu_item);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mHelpAndFeedbackItem != null) {
            mHelpAndFeedbackItem.setVisible(mAccount != null
                    && mAccount.supportsCapability(UIProvider.AccountCapabilities.HELP_CONTENT));
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
        } else if (itemId == R.id.settings) {
            Utils.showSettings(this, mAccount);
        } else if (itemId == R.id.help_info_menu_item) {
            showHelp(getString(R.string.main_help_context));
        } else {
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    @Override
    public Account getAccount() {
        return mAccount;
    }

    protected void showHelp(String helpContext) {
        Utils.showHelp(this, mAccount, helpContext);
    }
}
