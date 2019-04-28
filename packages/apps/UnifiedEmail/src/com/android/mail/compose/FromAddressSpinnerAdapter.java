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
package com.android.mail.compose;

import android.content.Context;
import android.text.TextUtils;
import android.text.util.Rfc822Tokenizer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.ReplyFromAccount;

import java.util.List;

/**
 * FromAddressSpinnerAdapter returns the correct spinner adapter for reply from
 * addresses based on device size.
 *
 * @author mindyp@google.com
 */
public class FromAddressSpinnerAdapter extends ArrayAdapter<ReplyFromAccount> {
    private static String sFormatString;

    private LayoutInflater mInflater;

    public FromAddressSpinnerAdapter(Context context) {
        super(context, R.layout.from_item, R.id.spinner_account_address);
        sFormatString = getContext().getString(R.string.formatted_email_address);
    }

    protected LayoutInflater getInflater() {
        if (mInflater == null) {
            mInflater = (LayoutInflater) getContext().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
        }
        return mInflater;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ReplyFromAccount fromItem = getItem(position);
        final View fromEntry = convertView == null ?
                getInflater().inflate(R.layout.from_item, null) : convertView;
        final TextView nameView = (TextView) fromEntry.findViewById(R.id.spinner_account_name);
        if (fromItem.isCustomFrom) {
            nameView.setText(fromItem.name);
            nameView.setVisibility(View.VISIBLE);

            ((TextView) fromEntry.findViewById(R.id.spinner_account_address))
                    .setText(formatAddress(fromItem.address));
        } else {
            nameView.setVisibility(View.GONE);
            ((TextView) fromEntry.findViewById(R.id.spinner_account_address))
                    .setText(fromItem.address);
        }
        return fromEntry;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        final ReplyFromAccount fromItem = getItem(position);
        final int res = fromItem.isCustomFrom ? R.layout.custom_from_dropdown_item
                : R.layout.from_dropdown_item;
        final View fromEntry = getInflater().inflate(res, null);
        if (fromItem.isCustomFrom) {
            ((TextView) fromEntry.findViewById(R.id.spinner_account_name)).setText(fromItem.name);
            ((TextView) fromEntry.findViewById(R.id.spinner_account_address))
                    .setText(formatAddress(fromItem.address));
        } else {
            ((TextView) fromEntry.findViewById(R.id.spinner_account_address))
                    .setText(fromItem.address);
        }
        return fromEntry;
    }

    private CharSequence formatAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        return String.format(sFormatString, Rfc822Tokenizer.tokenize(address)[0].getAddress());
    }

    public void addAccounts(List<ReplyFromAccount> replyFromAccounts) {
        // Get the position of the current account
        for (ReplyFromAccount account : replyFromAccounts) {
            // Add the account to the Adapter
            add(account);
        }
    }
}
