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
import android.os.UserManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.car.settings.R;
import com.android.car.settings.common.ListSettingsFragment;
import com.android.car.settings.common.TypedPagedListAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists all Users available on this device.
 */
public class UserSettingsFragment extends ListSettingsFragment {
    private static final String TAG = "UserSettingsFragment";
    private Context mContext;
    private UserManager mUserManager;

    public static UserSettingsFragment getInstance() {
        UserSettingsFragment
                userSettingsFragment = new UserSettingsFragment();
        Bundle bundle = ListSettingsFragment.getBundle();
        bundle.putInt(EXTRA_TITLE_ID, R.string.user_settings_title);
        bundle.putInt(EXTRA_ACTION_BAR_LAYOUT, R.layout.action_bar_with_button);
        userSettingsFragment.setArguments(bundle);
        return userSettingsFragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        mContext = getContext();
        mUserManager =
                (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        super.onActivityCreated(savedInstanceState);
        TextView addUserBtn = (TextView) getActivity().findViewById(R.id.action_button1);
        addUserBtn.setText(R.string.user_add_user_menu);
        addUserBtn.setOnClickListener(v -> {
            UserInfo user = mUserManager.createUser(
                    mContext.getString(R.string.user_new_user_name), 0 /* flags */);
            if (user == null) {
                // Couldn't create user, most likely because there are too many, but we haven't
                // been able to reload the list yet.
                Log.w(TAG, "can't create user.");
                return;
            }
            try {
                ActivityManager.getService().switchUser(user.id);
            } catch (RemoteException e) {
                Log.e(TAG, "Couldn't switch user.", e);
            }
        });

        TextView guestBtn = (TextView) getActivity().findViewById(R.id.action_button2);
        guestBtn.setVisibility(View.VISIBLE);
        guestBtn.setText(R.string.user_guest);
        guestBtn.setOnClickListener(v -> {
            UserInfo guest = mUserManager.createGuest(
                    mContext, mContext.getString(R.string.user_guest));
            if (guest == null) {
                // Couldn't create user, most likely because there are too many, but we haven't
                // been able to reload the list yet.
                Log.w(TAG, "can't create user.");
                return;
            }
            try {
                ActivityManager.getService().switchUser(guest.id);
            } catch (RemoteException e) {
                Log.e(TAG, "Couldn't switch user.", e);
            }
        });
    }

    @Override
    public ArrayList<TypedPagedListAdapter.LineItem> getLineItems() {
        List<UserInfo> infos = mUserManager.getUsers(true);
        ArrayList<TypedPagedListAdapter.LineItem> items = new ArrayList<>();
        for (UserInfo userInfo : infos) {
            items.add(new UserLineItem(mContext, userInfo, mUserManager, mFragmentController));
        }
        return items;
    }
}
