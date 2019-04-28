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

package com.android.car.messenger;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.annotation.Nullable;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.car.messenger.tts.TTSHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Controls the TTS of the message received.
 */
public class PlayMessageActivity extends Activity {
    private static final String TAG = "PlayMessageActivity";

    public static final String EXTRA_MESSAGE_KEY = "car.messenger.EXTRA_MESSAGE_KEY";
    public static final String EXTRA_SENDER_NAME = "car.messenger.EXTRA_SENDER_NAME";
    public static final String EXTRA_SHOW_REPLY_LIST_FLAG =
            "car.messenger.EXTRA_SHOW_REPLY_LIST_FLAG";
    public static final String EXTRA_REPLY_DISABLED_FLAG =
            "car.messenger.EXTRA_REPLY_DISABLED_FLAG";
    private View mContainer;
    private View mMessageContainer;
    private View mVoicePlate;
    private TextView mReplyNotice;
    private TextView mLeftButton;
    private TextView mRightButton;
    private ImageView mVoiceIcon;
    private MessengerService mMessengerService;
    private MessengerServiceBroadcastReceiver mMessengerServiceBroadcastReceiver =
            new MessengerServiceBroadcastReceiver();
    private MapMessageMonitor.SenderKey mSenderKey;
    private TTSHelper mTTSHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.play_message_layout);
        mContainer = findViewById(R.id.container);
        mMessageContainer = findViewById(R.id.message_container);
        mReplyNotice = (TextView) findViewById(R.id.reply_notice);
        mVoicePlate = findViewById(R.id.voice_plate);
        mLeftButton = (TextView) findViewById(R.id.left_btn);
        mRightButton = (TextView) findViewById(R.id.right_btn);
        mVoiceIcon = (ImageView) findViewById(R.id.voice_icon);

        mTTSHelper = new TTSHelper(this);
        setupEmojis();
        hideAutoReply();
        setupAutoReply();
        updateViewForMessagePlaying();

        AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(this,
                R.anim.trans_bottom_in);
        set.setTarget(mContainer);
        set.start();
    }

    private void setupEmojis() {
        TextView emoji1 = (TextView) findViewById(R.id.emoji1);
        emoji1.setText(getEmojiByUnicode(getResources().getInteger(R.integer.emoji_ok_hand_sign)));
        TextView emoji2 = (TextView) findViewById(R.id.emoji2);
        emoji2.setText(getEmojiByUnicode(getResources().getInteger(R.integer.emoji_thumb_up)));
        TextView emoji3 = (TextView) findViewById(R.id.emoji3);
        emoji3.setText(getEmojiByUnicode(getResources().getInteger(R.integer.emoji_thumb_down)));
        TextView emoji4 = (TextView) findViewById(R.id.emoji4);
        emoji4.setText(getEmojiByUnicode(getResources().getInteger(R.integer.emoji_heart)));
        TextView emoji5 = (TextView) findViewById(R.id.emoji5);
        emoji5.setText(getEmojiByUnicode(getResources().getInteger(R.integer.emoji_smiling_face)));
    }

    private String getEmojiByUnicode(int unicode){
        return new String(Character.toChars(unicode));
    }

    private void setupAutoReply() {
        TextView cannedMessage = (TextView) findViewById(R.id.canned_message);
        cannedMessage.setText(getString(R.string.reply_message_display_template,
                getString(R.string.caned_message_driving_right_now)));
        cannedMessage.setOnClickListener(
                v -> sendReply(getString(R.string.caned_message_driving_right_now)));
        findViewById(R.id.emoji1).setOnClickListener(this::sendReply);
        findViewById(R.id.emoji2).setOnClickListener(this::sendReply);
        findViewById(R.id.emoji3).setOnClickListener(this::sendReply);
        findViewById(R.id.emoji4).setOnClickListener(this::sendReply);
        findViewById(R.id.emoji5).setOnClickListener(this::sendReply);
    }

    /**
     * View needs to be TextView. Leave it as View, so can take advantage of lambda syntax
     */
    private void sendReply(View view) {
        sendReply(((TextView) view).getText());
    }

    private void sendReply(CharSequence message) {
        // send auto reply
        Intent intent = new Intent(getBaseContext(), MessengerService.class)
                .setAction(MessengerService.ACTION_AUTO_REPLY)
                .putExtra(MessengerService.EXTRA_SENDER_KEY, mSenderKey)
                .putExtra(
                        MessengerService.EXTRA_REPLY_MESSAGE,
                        message);
        startService(intent);

        String messageSent = getString(
                R.string.message_sent_notice,
                getIntent().getStringExtra(EXTRA_SENDER_NAME));
        // hide all view and show reply sent notice text
        mContainer.invalidate();
        mMessageContainer.setVisibility(View.GONE);
        mVoicePlate.setVisibility(View.GONE);
        mReplyNotice.setText(messageSent);
        mReplyNotice.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams layoutParams = mContainer.getLayoutParams();
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mContainer.requestLayout();

        // read out the reply sent notice. Finish activity after TTS is done.
        List<CharSequence> ttsMessages = new ArrayList<>();
        ttsMessages.add(messageSent);
        mTTSHelper.requestPlay(ttsMessages,
                new TTSHelper.Listener() {
                    @Override
                    public void onTTSStarted() {
                    }

                    @Override
                    public void onTTSStopped(boolean error) {
                        if (error) {
                            Log.w(TAG, "TTS error.");
                        }
                        finish();
                    }
                });
    }

    private void showAutoReply() {
        mContainer.invalidate();
        mMessageContainer.setVisibility(View.VISIBLE);
        mLeftButton.setText(getString(R.string.action_close_messages));
        mLeftButton.setOnClickListener(v -> finish());
        ViewGroup.LayoutParams layoutParams = mContainer.getLayoutParams();
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        mContainer.requestLayout();
    }

    private void hideAutoReply() {
        mContainer.invalidate();
        mMessageContainer.setVisibility(View.GONE);
        mLeftButton.setText(getString(R.string.action_reply));
        mLeftButton.setOnClickListener(v -> showAutoReply());
        ViewGroup.LayoutParams layoutParams = mContainer.getLayoutParams();
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mContainer.requestLayout();
    }

    /**
     * If there's a touch outside the voice plate, exit the activity.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (event.getX() < mContainer.getX()
                    || event.getX() > mContainer.getX() + mContainer.getWidth()
                    || event.getY() < mContainer.getY()
                    || event.getY() > mContainer.getY() + mContainer.getHeight()) {
                finish();

            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Bind to LocalService
        Intent intent = new Intent(this, MessengerService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mMessengerServiceBroadcastReceiver.start();
        processIntent();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        processIntent();
    }

    private void processIntent() {
        mSenderKey = getIntent().getParcelableExtra(EXTRA_MESSAGE_KEY);
        playMessage();
        if (getIntent().getBooleanExtra(EXTRA_SHOW_REPLY_LIST_FLAG, false)) {
            showAutoReply();
        }
        if (getIntent().getBooleanExtra(EXTRA_REPLY_DISABLED_FLAG, false)) {
            mLeftButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(this,
                R.anim.trans_bottom_out);
        set.setTarget(mContainer);
        set.start();
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mTTSHelper.cleanup();
        mMessengerServiceBroadcastReceiver.cleanup();
        unbindService(mConnection);
    }

    private void playMessage() {
        Intent intent = new Intent(getBaseContext(), MessengerService.class)
                .setAction(MessengerService.ACTION_PLAY_MESSAGES)
                .putExtra(MessengerService.EXTRA_SENDER_KEY, mSenderKey);
        startService(intent);
    }

    private void stopMessage() {
        Intent intent = new Intent(getBaseContext(), MessengerService.class)
                .setAction(MessengerService.ACTION_STOP_PLAYOUT)
                .putExtra(MessengerService.EXTRA_SENDER_KEY, mSenderKey);
        startService(intent);
    }

    private void updateViewForMessagePlaying() {
        mRightButton.setText(getString(R.string.action_stop));
        mRightButton.setOnClickListener(v -> stopMessage());
        mVoiceIcon.setVisibility(View.VISIBLE);
    }

    private void updateViewFoeMessageStopped() {
        mRightButton.setText(getString(R.string.action_repeat));
        mRightButton.setOnClickListener(v -> playMessage());
        mVoiceIcon.setVisibility(View.INVISIBLE);
    }

    private class MessengerServiceBroadcastReceiver extends BroadcastReceiver {
        private final IntentFilter mIntentFilter;
        MessengerServiceBroadcastReceiver() {
            mIntentFilter = new IntentFilter();
            mIntentFilter.addAction(MapMessageMonitor.ACTION_MESSAGE_PLAY_START);
            mIntentFilter.addAction(MapMessageMonitor.ACTION_MESSAGE_PLAY_STOP);
        }

        void start() {
            registerReceiver(this, mIntentFilter);
        }

        void cleanup() {
            unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case MapMessageMonitor.ACTION_MESSAGE_PLAY_START:
                    updateViewForMessagePlaying();
                    break;
                case MapMessageMonitor.ACTION_MESSAGE_PLAY_STOP:
                    updateViewFoeMessageStopped();
                    break;
                default:
                    break;
            }
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MessengerService.LocalBinder binder = (MessengerService.LocalBinder) service;
            mMessengerService = binder.getService();
            if (mMessengerService.isPlaying()) {
                updateViewForMessagePlaying();
            } else {
                updateViewFoeMessageStopped();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mMessengerService = null;
        }
    };
}
