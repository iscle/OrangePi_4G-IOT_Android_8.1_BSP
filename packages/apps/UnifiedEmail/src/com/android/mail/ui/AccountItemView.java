/**
 * Copyright (c) 2013, Google Inc.
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

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.bitmap.BitmapCache;
import com.android.mail.R;
import com.android.mail.bitmap.AccountAvatarDrawable;
import com.android.mail.bitmap.ContactResolver;
import com.android.mail.providers.Account;

/**
 * The view for each account in the folder list/drawer.
 */
public class AccountItemView extends LinearLayout {
    private TextView mAccountDisplayName;
    private TextView mAccountAddress;
    private ImageView mAvatar;
    private ImageView mCheckmark;

    public AccountItemView(Context context) {
        super(context);
    }

    public AccountItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AccountItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAccountDisplayName = (TextView)findViewById(R.id.account_display_name);
        mAccountAddress = (TextView)findViewById(R.id.account_address);
        mAvatar = (ImageView)findViewById(R.id.avatar);
        mCheckmark = (ImageView)findViewById(R.id.checkmark);
    }

    /**
     * Sets the account name and draws the unread count. Depending on the account state (current or
     * unused), the colors and drawables will change through the call to setSelected for each
     * necessary element.
     *
     * @param account account whose name will be displayed
     * @param isCurrentAccount true if the account is the one in use, false otherwise
     */
    public void bind(final Context context, final Account account, final boolean isCurrentAccount,
            final BitmapCache imagesCache, final ContactResolver contactResolver) {
        if (!TextUtils.isEmpty(account.getSenderName())) {
            mAccountDisplayName.setText(account.getSenderName());
            mAccountAddress.setText(account.getEmailAddress());
            mAccountAddress.setVisibility(View.VISIBLE);
        } else if (!TextUtils.isEmpty(account.getDisplayName()) &&
                !TextUtils.equals(account.getDisplayName(), account.getEmailAddress())) {
            mAccountDisplayName.setText(account.getDisplayName());
            mAccountAddress.setText(account.getEmailAddress());
            mAccountAddress.setVisibility(View.VISIBLE);
        } else {
            mAccountDisplayName.setText(account.getEmailAddress());
            mAccountAddress.setVisibility(View.GONE);
        }
        if (isCurrentAccount) {
            mCheckmark.setVisibility(View.VISIBLE);
            mAccountDisplayName.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            final int blackColor = getResources().getColor(R.color.text_color_black);
            mAccountDisplayName.setTextColor(blackColor);
            mAccountAddress.setTextColor(blackColor);
        } else {
            mCheckmark.setVisibility(View.GONE);
            mAccountDisplayName.setTypeface(Typeface.DEFAULT);
            final int greyColor = getResources().getColor(R.color.text_color_grey);
            mAccountDisplayName.setTextColor(greyColor);
            mAccountAddress.setTextColor(greyColor);
        }

        ImageView v = (ImageView) mAvatar.findViewById(R.id.avatar);
        AccountAvatarDrawable drawable = new AccountAvatarDrawable(
                context.getResources(), imagesCache, contactResolver);
        final int size = context.getResources().getDimensionPixelSize(
                R.dimen.account_avatar_dimension);
        drawable.setDecodeDimensions(size, size);
        drawable.bind(account.getSenderName(), account.getEmailAddress());
        v.setImageDrawable(drawable);

    }
}
