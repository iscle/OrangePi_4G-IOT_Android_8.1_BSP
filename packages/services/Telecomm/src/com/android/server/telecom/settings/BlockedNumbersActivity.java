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
 * limitations under the License
 */

package com.android.server.telecom.settings;

import android.annotation.Nullable;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.BlockedNumberContract;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.server.telecom.R;

/**
 * Activity to manage blocked numbers using {@link BlockedNumberContract}.
 */
public class BlockedNumbersActivity extends ListActivity
        implements LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener, TextWatcher,
        BlockNumberTaskFragment.Listener {
    private static final String ACTION_MANAGE_BLOCKED_NUMBERS =
            "android.telecom.action.MANAGE_BLOCKED_NUMBERS";
    private static final String TAG_BLOCK_NUMBER_TASK_FRAGMENT = "block_number_task_fragment";
    private static final String TELECOM_PACKAGE = "com.android.server.telecom";
    private static final String[] PROJECTION = new String[] {
            BlockedNumberContract.BlockedNumbers.COLUMN_ID,
            BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER
    };

    private static final String SELECTION = "((" +
            BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + " NOTNULL) AND (" +
            BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + " != '' ))";

    private BlockNumberTaskFragment mBlockNumberTaskFragment;
    private BlockedNumbersAdapter mAdapter;
    private TextView mAddButton;
    private ProgressBar mProgressBar;
    private RelativeLayout mButterBar;
    @Nullable private Button mBlockButton;
    private TextView mReEnableButton;

    private BroadcastReceiver mBlockingStatusReceiver;

    public static Intent getIntentForStartingActivity() {
        Intent intent = new Intent(ACTION_MANAGE_BLOCKED_NUMBERS);
        intent.setPackage(TELECOM_PACKAGE);
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.xml.activity_blocked_numbers);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (!BlockedNumberContract.canCurrentUserBlockNumbers(this)) {
            TextView nonPrimaryUserText = (TextView) findViewById(R.id.non_primary_user);
            nonPrimaryUserText.setVisibility(View.VISIBLE);

            LinearLayout manageBlockedNumbersUi =
                    (LinearLayout) findViewById(R.id.manage_blocked_ui);
            manageBlockedNumbersUi.setVisibility(View.GONE);
            return;
        }

        FragmentManager fm = getFragmentManager();
        mBlockNumberTaskFragment =
                (BlockNumberTaskFragment) fm.findFragmentByTag(TAG_BLOCK_NUMBER_TASK_FRAGMENT);

        if (mBlockNumberTaskFragment == null) {
            mBlockNumberTaskFragment = new BlockNumberTaskFragment();
            fm.beginTransaction()
                    .add(mBlockNumberTaskFragment, TAG_BLOCK_NUMBER_TASK_FRAGMENT).commit();
        }

        mAddButton = (TextView) findViewById(R.id.add_blocked);
        mAddButton.setOnClickListener(this);

        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        String[] fromColumns = {BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER};
        int[] toViews = {R.id.blocked_number};
        mAdapter = new BlockedNumbersAdapter(this, R.xml.layout_blocked_number, null, fromColumns,
                toViews, 0);

        ListView listView = getListView();
        listView.setAdapter(mAdapter);
        listView.setDivider(null);
        listView.setDividerHeight(0);

        mButterBar = (RelativeLayout) findViewById(R.id.butter_bar);
        mReEnableButton = (TextView) mButterBar.findViewById(R.id.reenable_button);
        mReEnableButton.setOnClickListener(this);

        updateButterBar();

        mBlockingStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateButterBar();
            }
        };
        registerReceiver(mBlockingStatusReceiver, new IntentFilter(
                BlockedNumberContract.SystemContract.ACTION_BLOCK_SUPPRESSION_STATE_CHANGED));

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    protected void onDestroy() {
        if (mBlockingStatusReceiver != null) {
            unregisterReceiver(mBlockingStatusReceiver);
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateButterBar() {
        if (BlockedNumberContract.SystemContract.getBlockSuppressionStatus(this).isSuppressed) {
            mButterBar.setVisibility(View.VISIBLE);
        } else {
            mButterBar.setVisibility(View.GONE);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(this, BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                PROJECTION, SELECTION, null,
                BlockedNumberContract.BlockedNumbers.COLUMN_ID + " DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
        mProgressBar.setVisibility(View.GONE);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onClick(View view) {
        if (view == mAddButton) {
            showAddBlockedNumberDialog();
        } else if (view == mReEnableButton) {
            BlockedNumberContract.SystemContract.endBlockSuppression(this);
            mButterBar.setVisibility(View.GONE);
        }
    }

    private void showAddBlockedNumberDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.xml.add_blocked_number_dialog, null);
        final EditText editText = (EditText) dialogView.findViewById(R.id.add_blocked_number);
        editText.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
        editText.addTextChangedListener(this);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.block_button, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        addBlockedNumber(PhoneNumberUtils.stripSeparators(
                                editText.getText().toString()));
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                })
                .create();
        dialog.setOnShowListener(new AlertDialog.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        mBlockButton = ((AlertDialog) dialog)
                                .getButton(AlertDialog.BUTTON_POSITIVE);
                        mBlockButton.setEnabled(false);
                        // show keyboard
                        InputMethodManager inputMethodManager =
                                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        inputMethodManager.showSoftInput(editText,
                                InputMethodManager.SHOW_IMPLICIT);

                    }
                });
        dialog.show();
    }

    /**
     * Add blocked number if it does not exist.
     */
    private void addBlockedNumber(String number) {
        if (PhoneNumberUtils.isEmergencyNumber(number)) {
            Toast.makeText(
                    this,
                    getString(R.string.blocked_numbers_block_emergency_number_message),
                    Toast.LENGTH_SHORT).show();
        } else {
            // We disable the add button, to prevent the user from adding other numbers until the
            // current number is added.
            mAddButton.setEnabled(false);
            mBlockNumberTaskFragment.blockIfNotAlreadyBlocked(number, this);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // no-op
    }

    @Override
    public void onTextChanged(CharSequence text, int start, int before, int count) {
        if (mBlockButton != null) {
            mBlockButton.setEnabled(
                    !TextUtils.isEmpty(PhoneNumberUtils.stripSeparators(text.toString())));
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
        // no-op
    }

    @Override
    public void onBlocked(String number, boolean alreadyBlocked) {
        if (alreadyBlocked) {
            BlockedNumbersUtil.showToastWithFormattedNumber(this,
                    R.string.blocked_numbers_number_already_blocked_message, number);
        } else {
            BlockedNumbersUtil.showToastWithFormattedNumber(this,
                    R.string.blocked_numbers_number_blocked_message, number);
        }
        mAddButton.setEnabled(true);
    }
}
