/*
 * Copyright (c) 2017 Google Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.example.android.wearable.wear.messaging.contacts;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.wearable.view.WearableRecyclerView;
import android.support.wearable.view.drawer.WearableActionDrawer;
import android.support.wearable.view.drawer.WearableDrawerLayout;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import com.example.android.wearable.wear.messaging.GoogleSignedInActivity;
import com.example.android.wearable.wear.messaging.R;
import com.example.android.wearable.wear.messaging.mock.MockDatabase;
import com.example.android.wearable.wear.messaging.model.Profile;
import com.example.android.wearable.wear.messaging.util.Constants;
import com.example.android.wearable.wear.messaging.util.DividerItemDecoration;
import com.example.android.wearable.wear.messaging.util.MenuTinter;
import java.util.List;

/**
 * Displays a selectable list of contacts. Tapping on a contact (item) causes the action drawer to
 * peek. Tapping the check returns the selected contacts/profiles.
 *
 * <p>The caller needs to provide the `ArrayList` of Profiles to display.
 */
public class ContactsListActivity extends GoogleSignedInActivity {

    private WearableDrawerLayout mDrawerLayout;
    private ContactsListAdapter mAdapter;
    private FindContactsAsyncTask mContactsAsyncTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts_list);

        mDrawerLayout = (WearableDrawerLayout) findViewById(R.id.drawer_layout);

        WearableRecyclerView recyclerView = (WearableRecyclerView) findViewById(R.id.recycler_list);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, R.drawable.divider));
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        mAdapter =
                new ContactsListAdapter(
                        this,
                        new ContactsListAdapter.ContactsSelectionListener() {
                            @Override
                            public void onContactInteraction(boolean selected) {
                                mDrawerLayout.peekDrawer(Gravity.BOTTOM);
                            }
                        });
        recyclerView.setAdapter(mAdapter);

        WearableActionDrawer actionDrawer =
                (WearableActionDrawer) findViewById(R.id.bottom_action_drawer);

        Menu menu = actionDrawer.getMenu();
        MenuTinter.tintMenu(this, menu, R.color.blue_15);
        actionDrawer.setOnMenuItemClickListener(
                new WearableActionDrawer.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        return handleMenuItems(menuItem);
                    }
                });

        actionDrawer.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_65));

        View peek =
                getLayoutInflater()
                        .inflate(R.layout.drawer_check_confirmation, mDrawerLayout, false);
        actionDrawer.setPeekContent(peek);

        peek.findViewById(R.id.button_confirm)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                sendSelectedContacts();
                            }
                        });

        peek.findViewById(R.id.button_cancel)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                cancelSelectingContacts();
                            }
                        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mContactsAsyncTask != null) {
            mContactsAsyncTask.cancel(true);
        }
        mContactsAsyncTask = new FindContactsAsyncTask(this);
        mContactsAsyncTask.execute();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mContactsAsyncTask != null) {
            mContactsAsyncTask.cancel(true);
        }
    }

    private boolean handleMenuItems(MenuItem menuItem) {
        int menuId = menuItem.getItemId();
        if (menuId == R.id.button_confirm) {
            sendSelectedContacts();
        } else if (menuId == R.id.button_cancel) {
            cancelSelectingContacts();
        }
        return false;
    }

    private void sendSelectedContacts() {
        Intent data = new Intent();
        data.putParcelableArrayListExtra(
                Constants.RESULT_CONTACTS_KEY, mAdapter.getSelectedContacts());
        setResult(RESULT_OK, data);
        finish();
    }

    protected void cancelSelectingContacts() {
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * Since messaging apps will retrieve contacts remotely, {@link AsyncTask} will allow us to
     * perform the expensive operation in the background and not block the main thread.
     *
     * <p>Retrieves the user's contacts and updates the adapter with the new list of contacts.
     */
    private class FindContactsAsyncTask extends AsyncTask<Void, Void, List<Profile>> {

        private final Context mContext;

        FindContactsAsyncTask(Context context) {
            this.mContext = context;
        }

        @Override
        protected List<Profile> doInBackground(Void... params) {
            return MockDatabase.getUserContacts(mContext);
        }

        @Override
        protected void onPostExecute(List<Profile> contacts) {
            super.onPostExecute(contacts);
            mAdapter.addAll(contacts);
        }
    }
}
