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

package com.android.tv.settings.form;

import com.android.tv.settings.dialog.old.Action;
import com.android.tv.settings.dialog.old.ActionAdapter;
import com.android.tv.settings.dialog.old.ActionFragment;
import com.android.tv.settings.dialog.old.ContentFragment;
import com.android.tv.settings.dialog.old.DialogActivity;
import com.android.tv.settings.dialog.old.EditTextFragment;
import com.android.tv.settings.R;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Stack;

/**
 * Implements a MultiPagedForm.
 * <p>
 * This is a multi-paged form that can be used for fragment transitions used in
 * such as setup, add network, and add credit cards
 */
public abstract class MultiPagedForm extends DialogActivity implements ActionAdapter.Listener,
    FormPageResultListener, FormResultListener {

    private static final int INTENT_FORM_PAGE_DATA_REQUEST = 1;
    private static final String TAG = "MultiPagedForm";

    private enum Key {
        DONE, CANCEL
    }

    protected final ArrayList<FormPage> mFormPages = new ArrayList<FormPage>();
    private final Stack<Object> mFlowStack = new Stack<Object>();
    private ActionAdapter.Listener mListener = null;

    @Override
    public void onActionClicked(Action action) {
        if (mListener != null) {
            mListener.onActionClicked(action);
        }
    }

    @Override
    public void onBackPressed() {

        // If we don't have a page to go back to, finish as cancelled.
        if (mFlowStack.size() < 1) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // Pop the current location off the stack.
        mFlowStack.pop();

        // Peek at the previous location on the stack.
        Object lastLocation = mFlowStack.isEmpty() ? null : mFlowStack.peek();

        if (lastLocation instanceof FormPage && !mFormPages.contains(lastLocation)) {
            onBackPressed();
        } else {
            displayCurrentStep(false);
            if (mFlowStack.isEmpty()) {
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    @Override
    public void onBundlePageResult(FormPage page, Bundle bundleResults) {
        // Complete the form with the results.
        page.complete(bundleResults);

        // Indicate that we've completed a page. If we get back false it means
        // the data was invalid and the page must be filled out again.
        // Otherwise, we move on to the next page.
        if (!onPageComplete(page)) {
            displayCurrentStep(false);
        } else {
            performNextStep();
        }
    }

    @Override
    public void onFormComplete() {
        onComplete(mFormPages);
    }

    @Override
    public void onFormCancelled() {
        onCancel(mFormPages);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        performNextStep();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == INTENT_FORM_PAGE_DATA_REQUEST) {
            if (resultCode == RESULT_OK) {
                overridePendingTransition(
                        R.anim.wps_activity_open_in, R.anim.wps_activity_open_out);
                Object currentLocation = mFlowStack.peek();
                if (currentLocation instanceof FormPage) {
                    FormPage page = (FormPage) currentLocation;
                    Bundle results = data == null ? null : data.getExtras();
                    if (data == null) {
                        Log.w(TAG, "Intent result was null!");
                    } else if (results == null) {
                        Log.w(TAG, "Intent result extras were null!");
                    } else if (!results.containsKey(FormPage.DATA_KEY_SUMMARY_STRING)) {
                        Log.w(TAG, "Intent result extras didn't have the result summary key!");
                    }
                    onBundlePageResult(page, results);
                } else {
                    Log.e(TAG, "Our current location wasn't on the top of the stack!");
                }
            } else {
                overridePendingTransition(
                        R.anim.wps_activity_close_in, R.anim.wps_activity_close_out);
                onBackPressed();
            }
        }
    }

    /**
     * Called when a form page completes. If necessary, add or remove any pages
     * from the form before this call completes. If all pages are complete when
     * onPageComplete returns, the form will be considered finished and the form
     * results will be displayed for confirmation.
     *
     * @param formPage the page that was completed.
     * @return true if the form can continue to the next incomplete page, or
     *         false if the data input is invalid and the form page must be
     *         completed again.
     */
    protected abstract boolean onPageComplete(FormPage formPage);

    /**
     * Called when all form pages have been completed and the user has accepted
     * them.
     *
     * @param formPages the pages that were completed. Any pages removed during
     *            the completion of the form are not included.
     */
    protected abstract void onComplete(ArrayList<FormPage> formPages);

    /**
     * Called when all form pages have been completed but the user wants to
     * cancel the form and discard the results.
     *
     * @param formPages the pages that were completed. Any pages removed during
     *            the completion of the form are not included.
     */
    protected abstract void onCancel(ArrayList<FormPage> formPages);

    /**
     * Override this to fully customize the display of the page.
     *
     * @param formPage the page that should be displayed.
     * @param listener the listener to notify when the page is complete.
     */
    protected void displayPage(FormPage formPage, FormPageResultListener listener,
            boolean forward) {
        switch (formPage.getType()) {
            case PASSWORD_INPUT:
                setContentAndActionFragments(getContentFragment(formPage),
                        createPasswordEditTextFragment(formPage));
                break;
            case TEXT_INPUT:
                setContentAndActionFragments(getContentFragment(formPage),
                        createEditTextFragment(formPage));
                break;
            case MULTIPLE_CHOICE:
                setContentAndActionFragments(getContentFragment(formPage),
                        createActionFragment(formPage));
                break;
            case INTENT:
            default:
                break;
        }
    }

    /**
     * Override this to fully customize the display of the form results.
     *
     * @param formPages the pages that were whose results should be displayed.
     * @param listener the listener to notify when the form is complete or has been cancelled.
     */
    protected void displayFormResults(ArrayList<FormPage> formPages, FormResultListener listener) {
        setContentAndActionFragments(createResultContentFragment(),
                createResultActionFragment(formPages, listener));
    }

    /**
     * @return the main title for this multipage form.
     */
    protected String getMainTitle() {
        return "";
    }

    /**
     * @return the action title to indicate the form is correct.
     */
    protected String getFormIsCorrectActionTitle() {
        return "";
    }

    /**
     * @return the action title to indicate the form should be canceled and its
     *         results discarded.
     */
    protected String getFormCancelActionTitle() {
        return "";
    }

    /**
     * Override this to provide a custom Fragment for displaying the content
     * portion of the page.
     *
     * @param formPage the page the Fragment should display.
     * @return a Fragment for identifying the current step.
     */
    protected Fragment getContentFragment(FormPage formPage) {
        return ContentFragment.newInstance(formPage.getTitle());
    }

    /**
     * Override this to provide a custom Fragment for displaying the content
     * portion of the form results.
     *
     * @return a Fragment for giving context to the result page.
     */
    protected Fragment getResultContentFragment() {
        return ContentFragment.newInstance(getMainTitle());
    }

    /**
     * Override this to provide a custom EditTextFragment for displaying a form
     * page for password input. Warning: the OnEditorActionListener of this
     * fragment will be overridden.
     *
     * @param initialText initial text that should be displayed in the edit
     *            field.
     * @return an EditTextFragment for password input.
     */
    protected EditTextFragment getPasswordEditTextFragment(String initialText) {
        return EditTextFragment.newInstance(null, initialText, true /* password */);
    }

    /**
     * Override this to provide a custom EditTextFragment for displaying a form
     * page for text input. Warning: the OnEditorActionListener of this fragment
     * will be overridden.
     *
     * @param initialText initial text that should be displayed in the edit
     *            field.
     * @return an EditTextFragment for custom input.
     */
    protected EditTextFragment getEditTextFragment(String initialText) {
        return EditTextFragment.newInstance(null, initialText);
    }

    /**
     * Override this to provide a custom ActionFragment for displaying a form
     * page for a list of choices.
     *
     * @param formPage the page the ActionFragment is for.
     * @param actions the actions the ActionFragment should display.
     * @param selectedAction the action in actions that is currently selected,
     *            or null if none are selected.
     * @return an ActionFragment displaying the given actions.
     */
    protected ActionFragment getActionFragment(FormPage formPage, ArrayList<Action> actions,
            Action selectedAction) {
        ActionFragment actionFragment = ActionFragment.newInstance(actions);
        if (selectedAction != null) {
            int indexOfSelection = actions.indexOf(selectedAction);
            if (indexOfSelection >= 0) {
                // TODO: Set initial focus action:
                // actionFragment.setSelection(indexOfSelection);
            }
        }
        return actionFragment;
    }

    /**
     * Override this to provide a custom ActionFragment for displaying the list
     * of page results.
     *
     * @param actions the actions the ActionFragment should display.
     * @return an ActionFragment displaying the given form results.
     */
    protected ActionFragment getResultActionFragment(ArrayList<Action> actions) {
        return ActionFragment.newInstance(actions);
    }

    /**
     * Adds the page to the end of the form. Only call this before onCreate or
     * during onPageComplete.
     *
     * @param formPage the page to add to the end of the form.
     */
    protected void addPage(FormPage formPage) {
        mFormPages.add(formPage);
    }

    /**
     * Removes the page from the form. Only call this before onCreate or during
     * onPageComplete.
     *
     * @param formPage the page to remove from the form.
     */
    protected void removePage(FormPage formPage) {
        mFormPages.remove(formPage);
    }

    /**
     * Clears all pages from the form. Only call this before onCreate or during
     * onPageComplete.
     */
    protected void clear() {
        mFormPages.clear();
    }

    /**
     * Clears all pages after the given page from the form. Only call this
     * before onCreate or during onPageComplete.
     *
     * @param formPage all pages after this page in the form will be removed
     *            from the form.
     */
    protected void clearAfter(FormPage formPage) {
        int indexOfPage = mFormPages.indexOf(formPage);
        if (indexOfPage >= 0) {
            for (int i = mFormPages.size() - 1; i > indexOfPage; i--) {
                mFormPages.remove(i);
            }
        }
    }

    /**
     * Stop display the currently displayed page. Note that this does <b>not</b>
     * remove the form page from the set of form pages for this form, it is just
     * no longer displayed and no replacement is provided, the screen should be
     * empty after this method.
     */
    protected void undisplayCurrentPage() {
    }

    private void performNextStep() {

        // First see if there are any incomplete form pages.
        FormPage nextIncompleteStep = findNextIncompleteStep();

        // If all the pages we have are complete, display the results.
        if (nextIncompleteStep == null) {
            mFlowStack.push(this);
        } else {
            mFlowStack.push(nextIncompleteStep);
        }
        displayCurrentStep(true);
    }

    private FormPage findNextIncompleteStep() {
        for (int i = 0, size = mFormPages.size(); i < size; i++) {
            FormPage formPage = mFormPages.get(i);
            if (!formPage.isComplete()) {
                return formPage;
            }
        }
        return null;
    }

    private void displayCurrentStep(boolean forward) {

        if (!mFlowStack.isEmpty()) {
            Object currentLocation = mFlowStack.peek();

            if (currentLocation instanceof MultiPagedForm) {
                displayFormResults(mFormPages, this);
            } else if (currentLocation instanceof FormPage) {
                FormPage page = (FormPage) currentLocation;
                if (page.getType() == FormPage.Type.INTENT) {
                    startActivityForResult(page.getIntent(), INTENT_FORM_PAGE_DATA_REQUEST);
                }
                displayPage(page, this, forward);
            } else {
                // If this is an unexpected type, something went wrong, finish as
                // cancelled.
                setResult(RESULT_CANCELED);
                finish();
            }
        } else {
            undisplayCurrentPage();
        }

    }

    private Fragment createResultContentFragment() {
        return getResultContentFragment();
    }

    private Fragment createResultActionFragment(final ArrayList<FormPage> formPages,
            final FormResultListener listener) {

        mListener = new ActionAdapter.Listener() {

            @Override
            public void onActionClicked(Action action) {
                Key key = getKeyFromKey(action.getKey());
                if (key != null) {
                    switch (key) {
                        case DONE:
                            listener.onFormComplete();
                            break;
                        case CANCEL:
                            listener.onFormCancelled();
                            break;
                        default:
                            break;
                    }
                } else {
                    String formPageKey = action.getKey();
                    for (int i = 0, size = formPages.size(); i < size; i++) {
                        FormPage formPage = formPages.get(i);
                        if (formPageKey.equals(formPage.getTitle())) {
                            mFlowStack.push(formPage);
                            displayCurrentStep(true);
                            break;
                        }
                    }
                }
            }
        };

        return getResultActionFragment(getResultActions());
    }

    private Key getKeyFromKey(String key) {
        try {
            return Key.valueOf(key);
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

    private ArrayList<Action> getActions(FormPage formPage) {
        ArrayList<Action> actions = new ArrayList<Action>();
        for (String choice : formPage.getChoices()) {
            actions.add(new Action.Builder().key(choice).title(choice).build());
        }
        return actions;
    }

    private ArrayList<Action> getResultActions() {
        ArrayList<Action> actions = new ArrayList<Action>();
        for (int i = 0, size = mFormPages.size(); i < size; i++) {
            FormPage formPage = mFormPages.get(i);
            actions.add(new Action.Builder().key(formPage.getTitle())
                    .title(formPage.getDataSummary()).description(formPage.getTitle()).build());
        }
        actions.add(new Action.Builder().key(Key.CANCEL.name())
                .title(getFormCancelActionTitle()).build());
        actions.add(new Action.Builder().key(Key.DONE.name())
                .title(getFormIsCorrectActionTitle()).build());
        return actions;
    }

    private Fragment createActionFragment(final FormPage formPage) {
        mListener = new ActionAdapter.Listener() {

            @Override
            public void onActionClicked(Action action) {
                handleStringPageResult(formPage, action.getKey());
            }
        };

        ArrayList<Action> actions = getActions(formPage);

        Action selectedAction = null;
        String choice = formPage.getDataSummary();
        for (int i = 0, size = actions.size(); i < size; i++) {
            Action action = actions.get(i);
            if (action.getKey().equals(choice)) {
                selectedAction = action;
                break;
            }
        }

        return getActionFragment(formPage, actions, selectedAction);
    }

    private Fragment createPasswordEditTextFragment(final FormPage formPage) {
        EditTextFragment editTextFragment = getPasswordEditTextFragment(formPage.getDataSummary());
        attachListeners(editTextFragment, formPage);
        return editTextFragment;
    }

    private Fragment createEditTextFragment(final FormPage formPage) {
        EditTextFragment editTextFragment = getEditTextFragment(formPage.getDataSummary());
        attachListeners(editTextFragment, formPage);
        return editTextFragment;
    }

    private void attachListeners(EditTextFragment editTextFragment, final FormPage formPage) {

        editTextFragment.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                handleStringPageResult(formPage, v.getText().toString());
                return true;
            }
        });
    }

    private void handleStringPageResult(FormPage page, String stringResults) {
        Bundle bundleResults = new Bundle();
        bundleResults.putString(FormPage.DATA_KEY_SUMMARY_STRING, stringResults);
        onBundlePageResult(page, bundleResults);
    }
}
