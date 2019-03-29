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
package com.example.android.wearable.wear.messaging.chat;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.wearable.view.WearableRecyclerView;
import android.support.wearable.view.drawer.WearableActionDrawer;
import android.support.wearable.view.drawer.WearableDrawerLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import com.example.android.wearable.wear.messaging.GoogleSignedInActivity;
import com.example.android.wearable.wear.messaging.R;
import com.example.android.wearable.wear.messaging.mock.MockDatabase;
import com.example.android.wearable.wear.messaging.model.Chat;
import com.example.android.wearable.wear.messaging.model.Message;
import com.example.android.wearable.wear.messaging.util.Constants;
import com.example.android.wearable.wear.messaging.util.DividerItemDecoration;
import com.example.android.wearable.wear.messaging.util.MenuTinter;
import com.example.android.wearable.wear.messaging.util.PrescrollToBottom;
import com.example.android.wearable.wear.messaging.util.SchedulerHelper;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Handles chat functionality. Activity uses chat id (passed as extra) to load chat details
 * (messages, etc.) and displays them.
 *
 * <p>Action drawer allows several inputs (speech, text) and can be easily extended to offer more
 * (gif, etc.).
 */
public class ChatActivity extends GoogleSignedInActivity {

    private static final String TAG = "ChatActivity";

    private static final int SPEECH_REQUEST_CODE = 9001;

    private WearableRecyclerView mRecyclerView;
    private WearableDrawerLayout mDrawerLayout;
    private EditText mInput;
    private TextView mNoMessagesView;
    private ChatAdapter mAdapter;

    private InputMethodManager mInputMethodManager;

    private Chat mChat;
    private FindChatsAsyncTask mFindChatsTask;
    private SendMessageAsyncTask mSendMessageTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        if (!getIntent().hasExtra(Constants.EXTRA_CHAT)) {
            finish();
            return;
        }

        mChat = getIntent().getParcelableExtra(Constants.EXTRA_CHAT);

        mNoMessagesView = (TextView) findViewById(R.id.no_messages_view);
        mInput = (EditText) findViewById(R.id.edit_text_input);
        mDrawerLayout = (WearableDrawerLayout) findViewById(R.id.drawer_layout);

        mRecyclerView = (WearableRecyclerView) findViewById(R.id.recycler_list);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(this, R.drawable.divider));
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);

        mAdapter = new ChatAdapter(this, mChat, getUser());
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView
                .getViewTreeObserver()
                .addOnPreDrawListener(new PrescrollToBottom(mRecyclerView, mAdapter));

        WearableActionDrawer actionDrawer =
                (WearableActionDrawer) findViewById(R.id.bottom_action_drawer);
        configureWearableActionDrawer(actionDrawer);

        mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        // Automatically sends updated content as a message when input changes to streamline
        // communication.
        mInput.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView editText, int actionId, KeyEvent event) {
                        if (actionId != EditorInfo.IME_ACTION_NONE) {

                            mInputMethodManager.hideSoftInputFromWindow(
                                    editText.getWindowToken(), 0);
                            editText.setVisibility(View.INVISIBLE);

                            String text = editText.getText().toString();
                            if (!text.isEmpty()) {
                                sendMessage(text);
                                mInputMethodManager.hideSoftInputFromInputMethod(
                                        mInput.getWindowToken(), 0);
                            }
                            return true;
                        }
                        return false;
                    }
                });
    }

    private void configureWearableActionDrawer(WearableActionDrawer actionDrawer) {
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

        View peek = getLayoutInflater().inflate(R.layout.drawer_chat_action, mDrawerLayout, false);
        actionDrawer.setPeekContent(peek);

        // Handles peek interactions.
        peek.findViewById(R.id.button_speech)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                displayVoiceToTextInput();
                            }
                        });

        peek.findViewById(R.id.button_keyboard)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                showKeyboard();
                            }
                        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Peeks drawer on page start.
        mDrawerLayout.peekDrawer(Gravity.BOTTOM);
        // After messages are added, re-scroll to the bottom.
        mRecyclerView
                .getViewTreeObserver()
                .addOnPreDrawListener(new PrescrollToBottom(mRecyclerView, mAdapter));

        if (mFindChatsTask != null) {
            mFindChatsTask.cancel(true);
        }
        mFindChatsTask = new FindChatsAsyncTask(this, mChat);
        mFindChatsTask.execute();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mFindChatsTask != null) {
            mFindChatsTask.cancel(true);
        }
        if (mSendMessageTask != null) {
            mSendMessageTask.cancel(true);
        }
    }

    /*
     * Takes a menu item and delegates to the appropriate function based on
     * the menu item id. Used with click handlers.
     */
    private boolean handleMenuItems(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.item_voice) {
            displayVoiceToTextInput();
        } else if (id == R.id.item_keyboard) {
            showKeyboard();
        }
        mDrawerLayout.closeDrawer(Gravity.BOTTOM);
        return false;
    }

    private void showKeyboard() {
        Log.d(TAG, "Showing keyboard");
        mInput.setText("");
        mInput.setVisibility(View.VISIBLE);
        mInput.requestFocus();
        mInputMethodManager.showSoftInput(mInput, InputMethodManager.SHOW_FORCED);
    }

    private void displayVoiceToTextInput() {
        Log.d(TAG, "Starting speech recognizer");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }

    private void sendMessage(String text) {
        Message message =
                new Message.Builder()
                        .id(UUID.randomUUID().toString())
                        .senderId(getUser().getId())
                        .text(text)
                        .build();
        sendMessage(message);
    }

    private void sendMessage(Message message) {
        if (mSendMessageTask != null) {
            mSendMessageTask.cancel(true);
        }
        mSendMessageTask = new SendMessageAsyncTask(this, mChat);
        mSendMessageTask.execute(message);
    }

    class FindChatsAsyncTask extends AsyncTask<Void, Void, Collection<Message>> {

        final Context mContext;
        final Chat mChat;

        FindChatsAsyncTask(Context context, Chat chat) {
            this.mContext = context;
            this.mChat = chat;
        }

        @Override
        protected Collection<Message> doInBackground(Void... params) {
            return MockDatabase.getAllMessagesForChat(mContext, mChat.getId());
        }

        @Override
        protected void onPostExecute(Collection<Message> chats) {
            super.onPostExecute(chats);
            mAdapter.addMessages(chats);
            // Displays welcome message if no messages in chat.
            if (mAdapter.getItemCount() == 0) {
                mRecyclerView.setVisibility(View.GONE);
                mNoMessagesView.setVisibility(View.VISIBLE);
            } else {
                mRecyclerView.setVisibility(View.VISIBLE);
                mNoMessagesView.setVisibility(View.GONE);
            }
        }
    }

    class SendMessageAsyncTask extends AsyncTask<Message, Void, Message> {

        final Context mContext;
        final Chat mChat;

        SendMessageAsyncTask(Context context, Chat chat) {
            this.mContext = context;
            this.mChat = chat;
        }

        @Override
        protected Message doInBackground(Message... params) {
            List<Message> parameters = Arrays.asList(params);
            if (parameters.isEmpty()) {
                throw new IllegalArgumentException("Messages are required for sending.");
            }
            for (Message message : parameters) {
                MockDatabase.saveMessage(mContext, mChat, message);
                SchedulerHelper.scheduleMockNotification(mContext, mChat, message);
            }
            return parameters.get(parameters.size() - 1);
        }

        @Override
        protected void onPostExecute(Message message) {
            super.onPostExecute(message);
            mAdapter.addMessage(message);
            mRecyclerView.smoothScrollToPosition(mAdapter.getItemCount());
        }
    }
}
