/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.View;

import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.preferences.AccountPreferences;
import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.StyleUtils;
import com.android.mail.utils.Utils;

/**
 * A tip displayed on top of conversation view to indicate that Gmail sync is
 * currently disabled on this account.
 */
public class ConversationSyncDisabledTipView extends ConversationTipView {
    public static final String LOG_TAG = LogTag.getLogTag();
    private Account mAccount = null;
    private Folder mFolder = null;
    private final MailPrefs mMailPrefs;
    private AccountPreferences mAccountPreferences;
    private Activity mActivity;

    private int mReasonSyncOff = ReasonSyncOff.NONE;

    public interface ReasonSyncOff {
        // Background sync is enabled for current account, do not display this tip
        public static final int NONE = 0;
        // Global auto-sync (affects all apps and all accounts) is turned off
        public static final int AUTO_SYNC_OFF = 1;
        // Global auto-sync is on, but Gmail app level sync is disabled for this particular account
        public static final int ACCOUNT_SYNC_OFF = 2;
    }

    public ConversationSyncDisabledTipView(final Context context) {
        super(context);

        mMailPrefs = MailPrefs.get(context);
    }

    @Override
    protected OnClickListener getTextAreaOnClickListener() {
        return new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mReasonSyncOff == ReasonSyncOff.AUTO_SYNC_OFF) {
                    final TurnAutoSyncOnDialog dialog = TurnAutoSyncOnDialog.newInstance(
                            mAccount.getAccountManagerAccount(), mAccount.syncAuthority);
                    dialog.show(mActivity.getFragmentManager(), TurnAutoSyncOnDialog.DIALOG_TAG);
                } else if (mReasonSyncOff == ReasonSyncOff.ACCOUNT_SYNC_OFF) {
                    Utils.showAccountSettings(getContext(), mAccount);
                }
            }
        };
    }

    public void bindAccount(Account account, ControllableActivity activity) {
        mAccount = account;
        mAccountPreferences = AccountPreferences.get(getContext(), account);
        mActivity = (Activity) activity;
    }

    @Override
    public void onUpdate(Folder folder, ConversationCursor cursor) {
        mFolder = folder;
    }

    @Override
    public boolean getShouldDisplayInList() {
        if (mAccount == null || mAccount.syncAuthority == null) {
            return false;
        }

        // Do not show this message for folders/labels that are not set to sync.
        if (mFolder == null || mFolder.syncWindow <= 0) {
            return false;
        }

        setReasonSyncOff(calculateReasonSyncOff(mMailPrefs, mAccount, mAccountPreferences));

        if (mReasonSyncOff != ReasonSyncOff.NONE) {
            LogUtils.i(LOG_TAG, "Sync is off with reason %d", mReasonSyncOff);
        }

        switch (mReasonSyncOff) {
            case ReasonSyncOff.AUTO_SYNC_OFF:
                return (mMailPrefs.getNumOfDismissesForAutoSyncOff() == 0);
            case ReasonSyncOff.ACCOUNT_SYNC_OFF:
                return (mAccountPreferences.getNumOfDismissesForAccountSyncOff() == 0);
            default:
                return false;
        }
    }

    public static int calculateReasonSyncOff(MailPrefs mailPrefs,
            Account account, AccountPreferences accountPreferences) {
        if (!ContentResolver.getMasterSyncAutomatically()) {
            // Global sync is turned off
            accountPreferences.resetNumOfDismissesForAccountSyncOff();
            // Logging to track down bug where this tip is being showing when it shouldn't be.
            LogUtils.i(LOG_TAG, "getMasterSyncAutomatically() return false");
            return ReasonSyncOff.AUTO_SYNC_OFF;
        } else {
            // Global sync is on, clear the number of times users has dismissed this
            // warning so that next time global sync is off, warning gets displayed again.
            mailPrefs.resetNumOfDismissesForAutoSyncOff();

            // Now check for whether account level sync is on/off.
            android.accounts.Account acct = account.getAccountManagerAccount();
            if (!TextUtils.isEmpty(account.syncAuthority) &&
                    !ContentResolver.getSyncAutomatically(acct, account.syncAuthority)) {
                // Account level sync is off
                return ReasonSyncOff.ACCOUNT_SYNC_OFF;
            } else {
                // Account sync is on, clear the number of times users has dismissed this
                // warning so that next time sync is off, warning gets displayed again.
                accountPreferences.resetNumOfDismissesForAccountSyncOff();
                return ReasonSyncOff.NONE;
            }
        }
    }

    private void setReasonSyncOff(int reason) {
        if (mReasonSyncOff != reason) {
            mReasonSyncOff = reason;
            final Resources resources = getResources();
            switch (mReasonSyncOff) {
                case ReasonSyncOff.AUTO_SYNC_OFF:
                    setText(resources.getString(R.string.auto_sync_off));
                    break;
                case ReasonSyncOff.ACCOUNT_SYNC_OFF:
                    // Create the "Turn on in Account settings." text where "Account settings" appear as
                    // a blue link.
                    Spannable accountSyncOff = new SpannableString(
                            Html.fromHtml(resources.getString(R.string.account_sync_off)));
                    StyleUtils.stripUnderlinesAndLinkUrls(accountSyncOff, null);
                    setText(accountSyncOff);
                    break;
                default:
            }
        }
    }

    @Override
    public void dismiss() {
        final String reason;
        switch (mReasonSyncOff) {
            case ReasonSyncOff.AUTO_SYNC_OFF:
                mMailPrefs.incNumOfDismissesForAutoSyncOff();
                reason = "auto_sync_off";
                break;
            case ReasonSyncOff.ACCOUNT_SYNC_OFF:
                mAccountPreferences.incNumOfDismissesForAccountSyncOff();
                reason = "account_sync_off";
                break;
            default:
                reason = null;
                break;
        }
        Analytics.getInstance().sendEvent("list_swipe", "sync_disabled_tip", reason, 0);
        super.dismiss();
    }
}