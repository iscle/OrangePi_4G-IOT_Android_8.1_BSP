/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.car.settings.users;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.car.settings.R;
import com.android.car.settings.common.EditTextLineItem;
import com.android.car.settings.common.ListSettingsFragment;
import com.android.car.settings.common.TypedPagedListAdapter;

import java.util.ArrayList;

/**
 * Shows details of an user
 */
public class UserDetailsSettingsFragment extends ListSettingsFragment {
    private static final String TAG = "UserDetailsSettingsFragment";

    public static final String EXTRA_USER_INFO = "extra_user_info";

    private UserInfo mUserInfo;
    private UserManager mUserManager;

    public static UserDetailsSettingsFragment getInstance(UserInfo userInfo) {
        UserDetailsSettingsFragment
                userSettingsFragment = new UserDetailsSettingsFragment();
        Bundle bundle = ListSettingsFragment.getBundle();
        bundle.putInt(EXTRA_ACTION_BAR_LAYOUT, R.layout.action_bar_with_button);
        bundle.putInt(EXTRA_TITLE_ID, R.string.user_settings_details_title);
        bundle.putParcelable(EXTRA_USER_INFO, userInfo);
        userSettingsFragment.setArguments(bundle);
        return userSettingsFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUserInfo = getArguments().getParcelable(EXTRA_USER_INFO);
    }

    @Override
    public ArrayList<TypedPagedListAdapter.LineItem> getLineItems() {
        ArrayList<TypedPagedListAdapter.LineItem> lineItems = new ArrayList<>();
        EditTextLineItem userNameLineItem = new EditTextLineItem(
                getContext().getText(R.string.user_name_label),
                mUserInfo.name);
        userNameLineItem.setTextType(EditTextLineItem.TextType.TEXT);
        userNameLineItem.setTextChangeListener(new EditTextLineItem.TextChangeListener() {
            @Override
            public void textChanged(Editable s) {
                mUserManager.setUserName(mUserInfo.id, s.toString());
            }
        });
        lineItems.add(userNameLineItem);
        return lineItems;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mUserManager = (UserManager) getContext().getSystemService(Context.USER_SERVICE);
        TextView removeUserBtn = getActivity().findViewById(R.id.action_button1);
        removeUserBtn.setText(R.string.delete_button);
        removeUserBtn.setOnClickListener(v -> removeUser());

        if (!isCurrentUser()) {
            TextView switchUserBtn = getActivity().findViewById(R.id.action_button2);
            switchUserBtn.setVisibility(View.VISIBLE);
            switchUserBtn.setText(R.string.user_switch);
            switchUserBtn.setOnClickListener(v -> switchTo());
        }
    }

    private void removeUser() {
        if (mUserInfo.id == UserHandle.USER_SYSTEM) {
            Log.w(TAG, "User " + mUserInfo.id + " could not removed.");
            return;
        }
        if (isCurrentUser()) {
            switchToUserId(UserHandle.USER_SYSTEM);
        }
        if (mUserManager.removeUser(mUserInfo.id)) {
            getActivity().onBackPressed();
        }
    }

    private boolean isCurrentUser() {
        return ActivityManager.getCurrentUser() == mUserInfo.id;
    }

    private void switchTo() {
        if (isCurrentUser()) {
            if (mUserInfo.isGuest()) {
                switchToGuest();
            }
            return;
        }

        if (UserManager.isGuestUserEphemeral()) {
            // If switching from guest, we want to bring up the guest exit dialog instead of switching
            UserInfo currUserInfo = mUserManager.getUserInfo(ActivityManager.getCurrentUser());
            if (currUserInfo != null && currUserInfo.isGuest()) {
                //showExitGuestDialog(currUserId, record.resolveId());
                return;
            }
        }

        switchToUserId(mUserInfo.id);
        getActivity().onBackPressed();
    }

    private void switchToGuest() {
        UserInfo guest = mUserManager.createGuest(
                getContext(), getContext().getString(R.string.user_guest));
        if (guest == null) {
            // Couldn't create user, most likely because there are too many, but we haven't
            // been able to reload the list yet.
            Log.w(TAG, "can't create user.");
            return;
        }
        switchToUserId(guest.id);
    }

    private void switchToUserId(int id) {
        try {
            ActivityManager.getService().switchUser(id);
        } catch (RemoteException e) {
            Log.e(TAG, "Couldn't switch user.", e);
        }
    }
}
