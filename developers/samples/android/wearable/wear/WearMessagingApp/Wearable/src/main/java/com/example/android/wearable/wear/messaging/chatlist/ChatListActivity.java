/*
 * Copyright 2017 Google Inc.
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
package com.example.android.wearable.wear.messaging.chatlist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.wearable.view.WearableRecyclerView;
import android.util.Log;
import com.example.android.wearable.wear.messaging.GoogleSignedInActivity;
import com.example.android.wearable.wear.messaging.R;
import com.example.android.wearable.wear.messaging.chat.ChatActivity;
import com.example.android.wearable.wear.messaging.contacts.ContactsListActivity;
import com.example.android.wearable.wear.messaging.mock.MockDatabase;
import com.example.android.wearable.wear.messaging.model.Chat;
import com.example.android.wearable.wear.messaging.model.Profile;
import com.example.android.wearable.wear.messaging.util.Constants;
import com.example.android.wearable.wear.messaging.util.DividerItemDecoration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Displays list of active chats of user.
 *
 * <p>Uses a simple mocked backend solution with shared preferences.
 */
public class ChatListActivity extends GoogleSignedInActivity {

    private static final String TAG = "ChatListActivity";

    // Triggered by contact selection in ContactsListActivity.
    private static final int CONTACTS_SELECTED_REQUEST_CODE = 9004;

    private ChatListAdapter mRecyclerAdapter;
    private RetrieveChatsAsyncTask mRetrieveChatsTask;
    private CreateNewChatAsyncTask mCreateChatTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.BlueTheme);
        setContentView(R.layout.activity_chat_list);

        WearableRecyclerView mRecyclerView =
                (WearableRecyclerView) findViewById(R.id.recycler_view);

        mRecyclerView.addItemDecoration(new DividerItemDecoration(this, R.drawable.divider));
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setHasFixedSize(true);

        mRecyclerAdapter = new ChatListAdapter(this, new MyChatListAdapterListener(this));
        mRecyclerView.setAdapter(mRecyclerAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        if (getUser() != null) {
            mRetrieveChatsTask = new RetrieveChatsAsyncTask(this);
            mRetrieveChatsTask.execute();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mRetrieveChatsTask != null) {
            mRetrieveChatsTask.cancel(true);
            mRetrieveChatsTask = null;
        }
        if (mCreateChatTask != null) {
            mCreateChatTask.cancel(true);
            mCreateChatTask = null;
        }
    }

    /** Launches the correct activities based on the type of item selected. */
    private class MyChatListAdapterListener implements ChatListAdapter.ChatAdapterListener {

        private final Activity activity;

        MyChatListAdapterListener(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void newChatSelected() {
            Intent intent = new Intent(activity, ContactsListActivity.class);
            activity.startActivityForResult(intent, CONTACTS_SELECTED_REQUEST_CODE);
        }

        @Override
        public void openChat(Chat chat) {
            Intent startChat = new Intent(activity, ChatActivity.class);
            startChat.putExtra(Constants.EXTRA_CHAT, chat);
            activity.startActivity(startChat);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CONTACTS_SELECTED_REQUEST_CODE) {
            // Selected contacts confirmed.
            if (resultCode == RESULT_OK) {
                ArrayList<Profile> contacts =
                        data.getParcelableArrayListExtra(Constants.RESULT_CONTACTS_KEY);
                mCreateChatTask = new CreateNewChatAsyncTask(this, getUser(), contacts);
                mCreateChatTask.execute();
            }
        }
    }

    private class RetrieveChatsAsyncTask extends AsyncTask<Void, Void, Collection<Chat>> {

        final Context mContext;

        private RetrieveChatsAsyncTask(Context context) {
            this.mContext = context;
        }

        @Override
        protected Collection<Chat> doInBackground(Void... params) {
            return MockDatabase.getAllChats(mContext);
        }

        @Override
        protected void onPostExecute(Collection<Chat> chats) {
            super.onPostExecute(chats);
            mRecyclerAdapter.setChats(chats);
        }
    }

    private class CreateNewChatAsyncTask extends AsyncTask<Void, Void, Chat> {

        final Context mContext;
        final Profile mUser;
        final List<Profile> mContacts;

        CreateNewChatAsyncTask(Context context, Profile user, List<Profile> contacts) {
            this.mContext = context;
            this.mUser = user;
            this.mContacts = contacts;
        }

        @Override
        protected Chat doInBackground(Void... params) {
            return MockDatabase.createChat(mContext, mContacts, mUser);
        }

        @Override
        protected void onPostExecute(Chat chat) {
            super.onPostExecute(chat);
            Log.d(
                    TAG,
                    String.format(
                            "Starting chat with %d partcipants(s)", chat.getParticipants().size()));

            // Launch ChatActivity with new chat.
            Intent startChat = new Intent(mContext, ChatActivity.class);
            startChat.putExtra(Constants.EXTRA_CHAT, chat);
            startActivity(startChat);
        }
    }
}
