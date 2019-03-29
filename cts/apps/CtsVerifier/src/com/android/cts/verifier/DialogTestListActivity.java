/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.verifier;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.cts.verifier.R;

/**
 * Test list activity that supports showing dialogs with pass/fail buttons instead of
 * starting new activities.
 * In addition to that dialogs have a 'go' button that can be configured to launch an intent.
 * Instructions are shown on top of the screen and a test preparation button is provided.
 */
public abstract class DialogTestListActivity extends PassFailButtons.TestListActivity {
    private final String TAG = "DialogTestListActivity";
    private final int mLayoutId;
    private final int mTitleStringId;
    private final int mInfoStringId;
    private final int mInstructionsStringId;

    protected Button mPrepareTestButton;
    protected ListView mTestFeaturesList;

    protected int mCurrentTestPosition;

    protected DialogTestListActivity(int layoutId, int titleStringId, int infoStringId,
            int instructionsStringId) {
        mLayoutId = layoutId;
        mTitleStringId = titleStringId;
        mInfoStringId = infoStringId;
        mInstructionsStringId = instructionsStringId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(mLayoutId);
        setInfoResources(mTitleStringId, mInfoStringId, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setResult(RESULT_CANCELED);

        ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);

        setupTests(adapter);

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });

        setTestListAdapter(adapter);

        mCurrentTestPosition = 0;

        TextView instructionTextView = (TextView) findViewById(R.id.test_instructions);
        instructionTextView.setText(mInstructionsStringId);
        mPrepareTestButton = (Button) findViewById(R.id.prepare_test_button);
        mTestFeaturesList = (ListView) findViewById(android.R.id.list);
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            mTestFeaturesList.setOnTouchListener((View v, MotionEvent e) -> {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        break;
                    case MotionEvent.ACTION_UP:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                    default:
                }
                return false;
            });
        }
    }

    /**
     * Subclasses must add their tests items to the provided adapter(usually instances of
     * {@link DialogTestListItem} or {@link DialogTestListItemWithIcon} but any class deriving from
     * {@link TestListAdapter.TestListItem} will do).
     *
     * @param adapter The adapter to add test items to.
     */
    protected abstract void setupTests(ArrayTestListAdapter adapter);

    public class DefaultTestCallback implements DialogTestListItem.TestCallback {
        final private DialogTestListItem mTest;

        public DefaultTestCallback(DialogTestListItem test) {
            mTest = test;
        }

        @Override
        public void onPass() {
            clearRemainingState(mTest);
            setTestResult(mTest, TestResult.TEST_RESULT_PASSED);
        }

        @Override
        public void onFail() {
            clearRemainingState(mTest);
            setTestResult(mTest, TestResult.TEST_RESULT_FAILED);
        }
    }

    public void showManualTestDialog(final DialogTestListItem test) {
        showManualTestDialog(test, new DefaultTestCallback(test));
    }

    public void showManualTestDialog(final DialogTestListItem test,
            final DialogTestListItem.TestCallback callback) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(mTitleStringId)
                .setNeutralButton(R.string.go_button_text, null)
                .setPositiveButton(R.string.pass_button_text, new AlertDialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callback.onPass();
                    }
                })
                .setNegativeButton(R.string.fail_button_text, new AlertDialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callback.onFail();
                    }
                });
        View customView = test.getCustomView();
        if (customView != null) {
            dialogBuilder.setView(customView);
        } else {
            dialogBuilder.setMessage(test.getManualTestInstruction());
        }
        final AlertDialog dialog = dialogBuilder.show();
        // Note: Setting the OnClickListener on the Dialog rather than the Builder, prevents the
        // dialog being dismissed on onClick.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!startTestIntent(test)) {
                    dialog.dismiss();
                }
            }
        });
    }

    @Override
    protected void handleItemClick(ListView l, View v, int position, long id) {
        TestListAdapter.TestListItem test = (TestListAdapter.TestListItem) getListAdapter()
                .getItem(position);
        if (test instanceof DialogTestListItem) {
            mCurrentTestPosition = position;
            ((DialogTestListItem) test).performTest(this);
        } else {
            try {
                super.handleItemClick(l, v, position, id);
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "handleItemClick() threw exception: ", e);
                setTestResult(test, TestResult.TEST_RESULT_FAILED);
                showToast(R.string.test_failed_cannot_start_intent);
            }
        }
    }


    /**
     * Start a test's manual intent
     *
     * @param test The test the manual intent of which is to be started.
     * @return true if activity could be started successfully, false otherwise.
     */
    boolean startTestIntent(final DialogTestListItem test) {
        final Intent intent = test.intent;
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Cannot start activity.", e);
            Toast.makeText(this, "Cannot start " + intent, Toast.LENGTH_LONG).show();
            setTestResult(test, TestResult.TEST_RESULT_FAILED);
            return false;
        }
        return true;
    }

    protected void clearRemainingState(final DialogTestListItem test) {
        // do nothing, override in subclass if needed
    }

    protected void setTestResult(TestListAdapter.TestListItem test, int result) {
        // Bundle result in an intent to feed into handleLaunchTestResult
        Intent resultIntent = new Intent();
        TestResult.addResultData(resultIntent, result, test.testName, /* testDetails */ null,
                /* reportLog */ null);
        handleLaunchTestResult(RESULT_OK, resultIntent);
        getListView().smoothScrollToPosition(mCurrentTestPosition + 1);
    }

    protected void setTestResult(String testName, int result) {
        // Bundle result in an intent to feed into handleLaunchTestResult
        Intent resultIntent = new Intent();
        TestResult.addResultData(resultIntent, result, testName, /* testDetails */ null,
                /* reportLog */ null);
        handleLaunchTestResult(RESULT_OK, resultIntent);
        getListView().smoothScrollToPosition(mCurrentTestPosition + 1);
    }

    protected void showToast(int messageId) {
        String message = getString(messageId);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected static class DialogTestListItem extends TestListAdapter.TestListItem {

        public interface TestCallback {
            void onPass();

            void onFail();
        }

        private String mManualInstruction;

        public DialogTestListItem(Context context, String nameId, String testId) {
            super(nameId, testId, null, null, null, null);
        }

        public DialogTestListItem(Context context, int nameResId, String testId) {
            super(context.getString(nameResId), testId, null, null, null, null);
        }

        public DialogTestListItem(Context context, int nameResId, String testId,
                int testInstructionResId, Intent testIntent) {
            super(context.getString(nameResId), testId, testIntent, null, null, null);
            mManualInstruction = context.getString(testInstructionResId);
        }

        public void performTest(DialogTestListActivity activity) {
            activity.showManualTestDialog(this);
        }

        public String getManualTestInstruction() {
            return mManualInstruction;
        }

        public Intent getManualTestIntent() {
            return intent;
        }

        public View getCustomView() {
            return null;
        }

        @Override
        boolean isTest() {
            return true;
        }
    }

    protected static class DialogTestListItemWithIcon extends DialogTestListItem {

        private final int mImageResId;
        private final Context mContext;

        public DialogTestListItemWithIcon(Context context, int nameResId, String testId,
                int testInstructionResId, Intent testIntent, int imageResId) {
            super(context, nameResId, testId, testInstructionResId, testIntent);
            mContext = context;
            mImageResId = imageResId;
        }

        @Override
        public View getCustomView() {
            LayoutInflater layoutInflater = LayoutInflater.from(mContext);
            View view = layoutInflater.inflate(R.layout.dialog_custom_view,
                    null /* root */);
            ((ImageView) view.findViewById(R.id.sample_icon)).setImageResource(mImageResId);
            ((TextView) view.findViewById(R.id.message)).setText(getManualTestInstruction());
            return view;
        }
    }
}
