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

package com.android.tv.ui.sidepanel;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.ChannelDataManager.ChannelListener;
import com.android.tv.data.OnCurrentProgramUpdatedListener;
import com.android.tv.data.Program;
import com.android.tv.data.ProgramDataManager;

public abstract class ChannelCheckItem extends CompoundButtonItem {
    private final ChannelDataManager mChannelDataManager;
    private final ProgramDataManager mProgramDataManager;
    private Channel mChannel;
    private TextView mProgramTitleView;
    private TextView mChannelNumberView;
    private final ChannelListener mChannelListener = new ChannelListener() {
        @Override
        public void onChannelRemoved(Channel channel) { }

        @Override
        public void onChannelUpdated(Channel channel) {
            mChannel = channel;
        }
    };

    private final OnCurrentProgramUpdatedListener mOnCurrentProgramUpdatedListener
            = new OnCurrentProgramUpdatedListener() {
        @Override
        public void onCurrentProgramUpdated(long channelId, Program program) {
            updateProgramTitle(program);
        }
    };

    public ChannelCheckItem(Channel channel,
            ChannelDataManager channelDataManager,
            ProgramDataManager programDataManager) {
        super(channel.getDisplayName(), "");
        mChannel = channel;
        mChannelDataManager = channelDataManager;
        mProgramDataManager = programDataManager;
    }

    protected Channel getChannel() {
        return mChannel;
    }

    @Override
    protected int getResourceId() {
        return R.layout.option_item_channel_check;
    }

    @Override
    protected int getCompoundButtonId() {
        return R.id.check_box;
    }

    @Override
    protected int getTitleViewId() {
        return R.id.channel_name;
    }

    @Override
    protected int getDescriptionViewId() {
        return R.id.program_title;
    }

    @Override
    protected void onBind(View view) {
        super.onBind(view);
        mChannelNumberView = (TextView) view.findViewById(R.id.channel_number);
        mProgramTitleView = (TextView) view.findViewById(R.id.program_title);
        mChannelDataManager.addChannelListener(mChannel.getId(), mChannelListener);
        mProgramDataManager.addOnCurrentProgramUpdatedListener(mChannel.getId(),
                mOnCurrentProgramUpdatedListener);
    }

    @Override
    protected void onUpdate() {
        super.onUpdate();
        mChannelNumberView.setText(getChannel().getDisplayNumber());
        updateProgramTitle(mProgramDataManager.getCurrentProgram(mChannel.getId()));
    }

    @Override
    protected void onUnbind() {
        mChannelDataManager.removeChannelListener(mChannel.getId(), mChannelListener);
        mProgramDataManager.removeOnCurrentProgramUpdatedListener(mChannel.getId(),
                mOnCurrentProgramUpdatedListener);
        mProgramTitleView = null;
        mChannelNumberView = null;
        super.onUnbind();
    }

    @Override
    protected void onSelected() {
        setChecked(!isChecked());
    }

    private void updateProgramTitle(Program program) {
        String title = null;
        if (program != null) {
            title = program.getTitle();
        }
        if (TextUtils.isEmpty(title)) {
            title = mProgramTitleView.getContext().getString(R.string.no_program_information);
        }
        mProgramTitleView.setText(title);
    }
}
