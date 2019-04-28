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
 * limitations under the License.
 */
package com.google.android.car.diagnosticverifier;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

/**
 * A recycler view adapter for verification result messages
 */
public class VerificationResultAdapter extends
        RecyclerView.Adapter<VerificationResultAdapter.VerificationResultViewHolder> {

    private List<String> mResultMessages;

    @Override
    public VerificationResultViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        Context context = viewGroup.getContext();
        int messageItemLayoutId = R.layout.result_message_item;
        LayoutInflater inflater = LayoutInflater.from(context);
        boolean shouldAttachToParentImmediately = false;

        View view = inflater.inflate(
                messageItemLayoutId, viewGroup, shouldAttachToParentImmediately);
        return new VerificationResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(VerificationResultViewHolder verificationResultViewHolder, int i) {
        String resultMessage = mResultMessages.get(i);
        verificationResultViewHolder.mResultMessageTextView.setText(resultMessage);
    }

    @Override
    public int getItemCount() {
        if (mResultMessages == null) {
            return 0;
        }
        return mResultMessages.size();
    }

    public void setResultMessages(List<String> resultMessages) {
        mResultMessages = resultMessages;
        notifyDataSetChanged();
    }

    public class VerificationResultViewHolder extends RecyclerView.ViewHolder {
        public final TextView mResultMessageTextView;

        public VerificationResultViewHolder(View view) {
            super(view);
            mResultMessageTextView = (TextView) view.findViewById(R.id.result_message);
        }
    }
}
