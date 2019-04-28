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
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;

import com.android.tv.settings.R;
import com.android.tv.settings.connectivity.FormPageDisplayer.FormPageInfo;
import com.android.tv.settings.connectivity.FormPageDisplayer.UserActivityListener;
import com.android.tv.settings.connectivity.setup.PasswordInputWizardFragment;
import com.android.tv.settings.connectivity.setup.SelectFromListWizardFragment;
import com.android.tv.settings.connectivity.setup.SelectFromListWizardFragment.ListItem;
import com.android.tv.settings.connectivity.setup.TextInputWizardFragment;
import com.android.tv.settings.form.FormPage;
import com.android.tv.settings.form.FormPageResultListener;
import com.android.tv.settings.form.FormResultListener;
import com.android.tv.settings.form.MultiPagedForm;

import java.util.ArrayList;

/**
 * Common functionality for wifi multipaged forms.
 */
public abstract class WifiMultiPagedFormActivity extends MultiPagedForm
        implements TextInputWizardFragment.Listener, SelectFromListWizardFragment.Listener,
        AdvancedWifiOptionsFlow.PageHandler, PasswordInputWizardFragment.Listener {

    @Override
    protected abstract void displayPage(FormPage formPage, FormPageResultListener listener,
            boolean forward);

    protected abstract boolean onPageComplete(WifiFormPageType formPageType, FormPage formPage);

    private FormPageDisplayer mFormPageDisplayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setLayoutProperties(R.layout.setup_auth_activity, R.id.description, R.id.action);
        mFormPageDisplayer = new FormPageDisplayer(this, getFragmentManager(), R.id.content);
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onTextInputComplete(String text) {
        return mFormPageDisplayer.onTextInputComplete(text);
    }

    @Override
    public boolean onPasswordInputComplete(String text, boolean obfuscate) {
        return mFormPageDisplayer.onPasswordInputComplete(text, obfuscate);
    }

    @Override
    public void onListSelectionComplete(ListItem listItem) {
        mFormPageDisplayer.onListSelectionComplete(listItem);
    }

    @Override
    public void onListFocusChanged(ListItem listItem) {
        mFormPageDisplayer.onListFocusChanged(listItem);
    }

    @Override
    public void addPage(WifiFormPageType formPageType) {
        addPage(formPageType.create());
    }

    @Override
    public void removePage(FormPage formPage) {
        super.removePage(formPage);
    }

    @Override
    public boolean choiceChosen(FormPage formPage, int choiceResourceId) {
        return getString(choiceResourceId).equals(formPage.getDataSummary());
    }

    @Override
    protected void displayFormResults(ArrayList<FormPage> formPages, FormResultListener listener) {
        // Don't need to display anything, just exit.
        finish();
    }

    @Override
    protected void onComplete(ArrayList<FormPage> formPages) {
        // We should never reach this point.
    }

    @Override
    protected void onCancel(ArrayList<FormPage> formPages) {
        // We should never reach this point.
    }

    @Override
    protected boolean onPageComplete(FormPage formPage) {
        WifiFormPageType formPageType = getFormPageType(formPage);

        // Always clear future pages.
        clearAfter(formPage);

        // Always clear loading pages.
        if (formPageType.getDisplayType() == FormPageDisplayer.DISPLAY_TYPE_LOADING) {
            removePage(formPage);
        }

        return onPageComplete(formPageType, formPage);
    }

    protected Fragment displayPage(FormPageInfo formPageInfo, String titleArgument,
            String descriptionArgument,
            ArrayList<SelectFromListWizardFragment.ListItem> extraChoices,
            FormPage previousFormPage, UserActivityListener userActivityListener,
            boolean showProgress, FormPage currentFormPage,
            FormPageResultListener formPageResultListener, boolean forward, boolean emptyAllowed) {
        return mFormPageDisplayer.displayPage(formPageInfo, titleArgument, descriptionArgument,
                extraChoices, previousFormPage, userActivityListener, showProgress, currentFormPage,
                formPageResultListener, forward, emptyAllowed);
    }

    protected SelectFromListWizardFragment.ListItem getListItem(FormPage formPage) {
        return mFormPageDisplayer.getListItem(formPage);
    }

    protected void setWifiConfigurationPassword(
            WifiConfiguration wifiConfiguration, WifiSecurity wifiSecurity, String password) {
        if (wifiSecurity == WifiSecurity.WEP) {
            int length = password.length();
            // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
            if ((length == 10 || length == 26 || length == 32 || length == 58)
                    && password.matches("[0-9A-Fa-f]*")) {
                wifiConfiguration.wepKeys[0] = password;
            } else if (length == 5 || length == 13 || length == 16 || length == 29) {
                wifiConfiguration.wepKeys[0] = '"' + password + '"';
            }
        } else {
            if (wifiSecurity == WifiSecurity.PSK
                    && password.length() < FormPageDisplayer.PSK_MIN_LENGTH) {
                return;
            }
            if (password.matches("[0-9A-Fa-f]{64}")) {
                wifiConfiguration.preSharedKey = password;
            } else {
                wifiConfiguration.preSharedKey = '"' + password + '"';
            }
        }
    }

    protected WifiFormPageType getFormPageType(FormPage formPage) {
        return WifiFormPageType.valueOf(formPage.getTitle());
    }

    protected void addPage(WifiFormPageType formPageType, Intent intent) {
        addPage(formPageType.create(intent));
    }

    protected void displayFragment(Fragment fragment, boolean forward) {
        mFormPageDisplayer.displayFragment(fragment, forward);
    }
}
