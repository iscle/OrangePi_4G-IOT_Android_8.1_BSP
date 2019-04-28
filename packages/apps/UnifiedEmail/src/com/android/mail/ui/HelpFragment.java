/**
 * Copyright (c) 2014, Google Inc.
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
package com.android.mail.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.support.v4.print.PrintHelper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.android.mail.R;

import java.util.Calendar;

/**
 * This fragment shows the Help screen.
 */
public final class HelpFragment extends Fragment {

    /** Displays the copyright information, privacy policy or open source licenses. */
    private WebView mWebView;

    // Public no-args constructor needed for fragment re-instantiation
    public HelpFragment() {
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Uri helpUri = getActivity().getIntent()
                .getParcelableExtra(HelpActivity.PARAM_HELP_URL);
        mWebView.loadUrl(helpUri.toString());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        setHasOptionsMenu(true);

        final View view = inflater.inflate(R.layout.help_fragment, container, false);
        if (view != null) {
            mWebView = (WebView) view.findViewById(R.id.webview);
            mWebView.setWebViewClient(new WebViewClient());
            if (state != null) {
                mWebView.restoreState(state);
            }
        }

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.help_menu, menu);

        // if we have no play store URI, hide that menu item
        final MenuItem viewAppUrlMenuItem = menu.findItem(R.id.view_app_url);
        if (viewAppUrlMenuItem != null) {
            final String appUrl = getString(R.string.app_url);
            viewAppUrlMenuItem.setVisible(!TextUtils.isEmpty(appUrl));
        }

        // printing the content of the webview is only allowed if running on Kitkat or later
        final MenuItem printItem = menu.findItem(R.id.print_dialog);
        if (printItem != null) {
            printItem.setVisible(PrintHelper.systemSupportsPrint());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            final Activity activity = getActivity();
            if (activity != null) {
                activity.finish();
            }
            return true;
        } else if (itemId == R.id.view_app_url) {
            showAppUrl();
            return true;
        } else if (itemId == R.id.print_dialog) {
            print();
            return true;
        } else if (itemId == R.id.copyright_information) {
            showCopyrightInformation();
            return true;
        } else if (itemId == R.id.open_source_licenses) {
            showOpenSourceLicenses();
            return true;
        } else if (itemId == R.id.privacy_policy) {
            showPrivacyPolicy();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @TargetApi(19)
    private void print() {
        // pick a name for the print job we will create
        final String title = getActivity().getActionBar().getTitle().toString();
        final String jobName = getString(R.string.print_job_name, title);

        // ask the webview for a print adapter
        final PrintDocumentAdapter pda = mWebView.createPrintDocumentAdapter();

        // ask the print manager to print the contents of the webview using the job name
        final PrintManager pm = (PrintManager) getActivity().
                getSystemService(Context.PRINT_SERVICE);
        pm.print(jobName, pda, new PrintAttributes.Builder().build());
    }

    private void showAppUrl() {
        final Uri uri = Uri.parse(getString(R.string.app_url));
        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }

    private void showCopyrightInformation() {
        new CopyrightDialogFragment().show(getFragmentManager(), "copyright");
    }

    private void showOpenSourceLicenses() {
        final Context context = getActivity();
        final Intent intent = new Intent(context, LicensesActivity.class);
        context.startActivity(intent);
    }

    private void showPrivacyPolicy() {
        final Uri uri = Uri.parse(getString(R.string.privacy_policy_uri));
        final Intent i = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(i);
    }

    public static class CopyrightDialogFragment extends DialogFragment {

        public CopyrightDialogFragment() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // generate and display a copyright statement resembling "© Google 2014"
            final int year = Calendar.getInstance().get(Calendar.YEAR);
            final String copyright = getString(R.string.copyright, year);

            return new AlertDialog.Builder(getActivity())
                    .setMessage(copyright)
                    .setNegativeButton(R.string.cancel, null).create();
        }
    }
}
