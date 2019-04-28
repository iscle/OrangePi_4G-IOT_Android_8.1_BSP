/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv;

import android.app.Activity;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;

import com.android.tv.data.Channel;
import com.android.tv.ui.SelectInputView;
import com.android.tv.ui.SelectInputView.OnInputSelectedCallback;
import com.android.tv.util.Utils;

/**
 * An activity to select input.
 */
public class SelectInputActivity extends Activity {
    private SelectInputView mSelectInputView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((TvApplication) getApplicationContext()).setSelectInputActivity(this);
        setContentView(R.layout.activity_select_input);
        mSelectInputView = (SelectInputView) findViewById(R.id.scene_transition_common);
        mSelectInputView.setOnInputSelectedCallback(new OnInputSelectedCallback() {
            @Override
            public void onTunerInputSelected() {
                startTvWithChannel(TvContract.Channels.CONTENT_URI);
            }

            @Override
            public void onPassthroughInputSelected(TvInputInfo input) {
                startTvWithChannel(TvContract.buildChannelUriForPassthroughInput(input.getId()));
            }

            private void startTvWithChannel(Uri channelUri) {
                Intent intent = new Intent(Intent.ACTION_VIEW, channelUri,
                        SelectInputActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });
        String channelUriString = Utils.getLastWatchedChannelUri(this);
        if (channelUriString != null) {
            Uri channelUri = Uri.parse(channelUriString);
            if (TvContract.isChannelUriForPassthroughInput(channelUri)) {
                mSelectInputView.setCurrentChannel(Channel.createPassthroughChannel(channelUri));
            }
            // No need to set the tuner channel because it's the default selection.
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSelectInputView.onEnterAction(true);
    }

    @Override
    protected void onPause() {
        mSelectInputView.onExitAction();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ((TvApplication) getApplicationContext()).setSelectInputActivity(null);
        super.onDestroy();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_I || keyCode == KeyEvent.KEYCODE_TV_INPUT) {
            mSelectInputView.onKeyUp(keyCode, event);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }
}
