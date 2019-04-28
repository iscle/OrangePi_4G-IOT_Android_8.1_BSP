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

package com.android.tv.settings.connectivity;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.settings.connectivity.setup.MessageWizardFragment;
import com.android.tv.settings.connectivity.setup.PasswordInputWizardFragment;
import com.android.tv.settings.connectivity.setup.SelectFromListWizardFragment;
import com.android.tv.settings.connectivity.setup.SelectFromListWizardFragment.ListItem;
import com.android.tv.settings.connectivity.setup.TextInputWizardFragment;
import com.android.tv.settings.form.FormPage;
import com.android.tv.settings.form.FormPageResultListener;

import java.util.ArrayList;

/**
 * Displays form pages.
 */
public class FormPageDisplayer
        implements TextInputWizardFragment.Listener, SelectFromListWizardFragment.Listener,
            PasswordInputWizardFragment.Listener {

    public static final int DISPLAY_TYPE_TEXT_INPUT = 1;
    public static final int DISPLAY_TYPE_LIST_CHOICE = 2;
    public static final int DISPLAY_TYPE_LOADING = 3;
    // Minimum 5 characters for WEP, 8 characters for PSK
    public static final int DISPLAY_TYPE_PSK_INPUT = 4;

    private static final int WEP_MIN_LENGTH = 5;
    public static final int PSK_MIN_LENGTH = 8;

    public interface FormPageInfo {
        /**
         * @return the resource id of the title for this page.
         */
        int getTitleResourceId();

        /**
         * @return the resource id of the description for this page. 0 if no description.
         */
        int getDescriptionResourceId();

        /**
         * @return the input type for the editable text field for this page.
         */
        int getInputType();

        /**
         * @return the way this form page should be displayed.
         */
        int getDisplayType();

        /**
         * @return the default value for this input
         */
        int getDefaultPrefillResourceId();

        /**
         * @return the set of choices for this form page.
         */
        ArrayList<SelectFromListWizardFragment.ListItem> getChoices(Context context,
                ArrayList<SelectFromListWizardFragment.ListItem> extraChoices);
    }

    public interface UserActivityListener {
        void onUserActivity();
    }

    private static final String TAG = "FormPageDisplayer";
    private static final boolean DEBUG = false;
    private static final String RESULT_LIST_ITEM = "result_list_item";

    private final Context mContext;
    private final FragmentManager mFragmentManager;
    private TextInputWizardFragment.Listener mTextInputWizardFragmentListener;
    private PasswordInputWizardFragment.Listener mPasswordInputWizardFragmentListener;
    private SelectFromListWizardFragment.Listener mSelectFromListWizardFragmentListener;
    private final int mContentViewId;

    public FormPageDisplayer(Context context, FragmentManager fragmentManager, int contentViewId) {
        mContext = context;
        mFragmentManager = fragmentManager;
        mContentViewId = contentViewId;
    }

    @Override
    public boolean onTextInputComplete(String text) {
        if (mTextInputWizardFragmentListener != null) {
            return mTextInputWizardFragmentListener.onTextInputComplete(text);
        }
        return true;
    }

    @Override
    public boolean onPasswordInputComplete(String text, boolean obfuscate) {
        if (mPasswordInputWizardFragmentListener != null) {
            return mPasswordInputWizardFragmentListener.onPasswordInputComplete(text, obfuscate);
        }
        return true;
    }

    @Override
    public void onListSelectionComplete(ListItem listItem) {
        if (mSelectFromListWizardFragmentListener != null) {
            mSelectFromListWizardFragmentListener.onListSelectionComplete(listItem);
        }
    }

    @Override
    public void onListFocusChanged(ListItem listItem) {
        if (mSelectFromListWizardFragmentListener != null) {
            mSelectFromListWizardFragmentListener.onListFocusChanged(listItem);
        }
    }

    public Fragment displayPage(FormPageInfo formPageInfo, String titleArgument,
            String descriptionArgument,
            ArrayList<SelectFromListWizardFragment.ListItem> extraChoices,
            FormPage previousFormPage, UserActivityListener userActivityListener,
            boolean showProgress, FormPage currentFormPage,
            FormPageResultListener formPageResultListener, boolean forward, boolean emptyAllowed) {
        if (DEBUG) {
            Log.d(TAG, "Displaying: " + currentFormPage.getTitle());
        }
        switch (formPageInfo.getDisplayType()) {
            case DISPLAY_TYPE_LIST_CHOICE:
                return displayList(formPageInfo, titleArgument, descriptionArgument, extraChoices,
                        currentFormPage, formPageResultListener, previousFormPage,
                        userActivityListener, forward);

            case DISPLAY_TYPE_TEXT_INPUT:
                return displayTextInput(formPageInfo, titleArgument, descriptionArgument,
                        currentFormPage, formPageResultListener, previousFormPage, forward,
                        emptyAllowed);

            case DISPLAY_TYPE_PSK_INPUT:
                return displayPskInput(formPageInfo, titleArgument, descriptionArgument,
                        currentFormPage, formPageResultListener, previousFormPage, forward);

            case DISPLAY_TYPE_LOADING:
                return displayLoading(formPageInfo, titleArgument, showProgress, forward);

            default:
                return null;
        }
    }

    public ListItem getListItem(FormPage formPage) {
        return formPage.getData().getParcelable(RESULT_LIST_ITEM);
    }

    public void displayFragment(Fragment fragment, boolean forward) {
        FragmentTransaction transaction = mFragmentManager.beginTransaction();
        if (forward) {
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        } else {
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
        }
        transaction.replace(mContentViewId, fragment).commit();
    }

    private Fragment displayList(final FormPageInfo formPageInfo, String titleArgument,
            String descriptionArgument,
            ArrayList<SelectFromListWizardFragment.ListItem> extraChoices, final FormPage formPage,
            final FormPageResultListener formPageResultListener, FormPage lastPage,
            final UserActivityListener userActivityListener, boolean forward) {
        Fragment fragment = SelectFromListWizardFragment.newInstance(
                getTitle(formPageInfo, titleArgument),
                getDescription(formPageInfo, descriptionArgument),
                formPageInfo.getChoices(mContext, extraChoices), lastPage == null ? null
                        : (ListItem) lastPage.getData().getParcelable(RESULT_LIST_ITEM));
        displayFragment(fragment, forward);
        mSelectFromListWizardFragmentListener = new SelectFromListWizardFragment.Listener() {
            @Override
            public void onListSelectionComplete(ListItem selection) {
                Bundle result = new Bundle();
                result.putParcelable(RESULT_LIST_ITEM, selection);
                result.putString(FormPage.DATA_KEY_SUMMARY_STRING, selection.getName());
                formPageResultListener.onBundlePageResult(formPage, result);
            }

            @Override
            public void onListFocusChanged(ListItem listItem) {
                if (userActivityListener != null) {
                    userActivityListener.onUserActivity();
                }
            }
        };
        return fragment;
    }

    private Fragment displayTextInput(FormPageInfo formPageInfo, String titleArgument,
            String descriptionArgument, final FormPage formPage,
            final FormPageResultListener listener, FormPage lastPage, boolean forward,
            final boolean emptyAllowed) {
        final String prefill = lastPage != null && !TextUtils.isEmpty(lastPage.getDataSummary())
                ? lastPage.getDataSummary() : getDefaultPrefill(formPageInfo);
        Fragment fragment = TextInputWizardFragment.newInstance(
                getTitle(formPageInfo, titleArgument),
                getDescription(formPageInfo, descriptionArgument),
                formPageInfo.getInputType(),
                prefill);
        displayFragment(fragment, forward);

        mTextInputWizardFragmentListener = new TextInputWizardFragment.Listener() {
            @Override
            public boolean onTextInputComplete(String text) {
                if (!TextUtils.isEmpty(text) || emptyAllowed) {
                    Bundle result = new Bundle();
                    result.putString(FormPage.DATA_KEY_SUMMARY_STRING, text);
                    listener.onBundlePageResult(formPage, result);
                    return true;
                }
                return false;
            }
        };
        return fragment;
    }

    private Fragment displayPskInput(FormPageInfo formPageInfo, String titleArgument,
            String descriptionArgument, final FormPage formPage,
            final FormPageResultListener listener, FormPage lastPage, boolean forward) {
        boolean obfuscate = lastPage != null
                ? TextUtils.equals(
                          lastPage.getDataSecondary(), PasswordInputWizardFragment.OPTION_OBFUSCATE)
                : false;
        Fragment fragment =
                PasswordInputWizardFragment.newInstance(getTitle(formPageInfo, titleArgument),
                        getDescription(formPageInfo, descriptionArgument),
                        lastPage == null ? null : lastPage.getDataSummary(), obfuscate);
        displayFragment(fragment, forward);

        mPasswordInputWizardFragmentListener = new PasswordInputWizardFragment.Listener() {
            @Override
            public boolean onPasswordInputComplete(String text, boolean obfuscate) {
                if (!TextUtils.isEmpty(text) && text.length() >= WEP_MIN_LENGTH) {
                    Bundle result = new Bundle();
                    result.putString(FormPage.DATA_KEY_SUMMARY_STRING, text);
                    if (obfuscate) {
                        result.putString(FormPage.DATA_KEY_SECONDARY_STRING,
                                PasswordInputWizardFragment.OPTION_OBFUSCATE);
                    }
                    listener.onBundlePageResult(formPage, result);
                    return true;
                }
                return false;
            }
        };
        return fragment;
    }

    private Fragment displayLoading(FormPageInfo formPageInfo, String argument,
            boolean showProgress, boolean forward) {
        Fragment fragment = MessageWizardFragment.newInstance(
                getTitle(formPageInfo, argument), showProgress);
        displayFragment(fragment, forward);
        return fragment;
    }

    private String getTitle(FormPageInfo formPageInfo, String argument) {
        return mContext.getString(formPageInfo.getTitleResourceId(), argument);
    }

    private String getDescription(FormPageInfo formPageInfo, String argument) {
        int descriptionResourceId = formPageInfo.getDescriptionResourceId();
        return (descriptionResourceId != 0) ? mContext.getString(descriptionResourceId, argument)
                : null;
    }

    private String getDefaultPrefill(FormPageInfo formPageInfo) {
        int defaultPrefillResourceId = formPageInfo.getDefaultPrefillResourceId();
        return (defaultPrefillResourceId != 0)
                ? mContext.getString(defaultPrefillResourceId) : null;
    }
}
