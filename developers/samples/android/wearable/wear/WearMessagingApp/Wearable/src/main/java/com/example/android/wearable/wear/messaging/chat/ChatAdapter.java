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
import android.graphics.Bitmap;
import android.support.percent.PercentRelativeLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.util.SortedListAdapterCallback;
import android.support.wearable.view.WearableRecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.example.android.wearable.wear.messaging.R;
import com.example.android.wearable.wear.messaging.model.Chat;
import com.example.android.wearable.wear.messaging.model.Message;
import com.example.android.wearable.wear.messaging.model.Profile;
import java.util.Calendar;
import java.util.Collection;
import java.util.Locale;

/**
 * Adapter for chat view. Uses a SortedList of Messages by sent time. Determines if the senderId is
 * the current user and chooses the corresponding senderId layout.
 *
 * <p>The adapter will tint the background of a message so that there is an alternating visual
 * difference to make reading messages easier by providing better visual clues
 */
class ChatAdapter extends WearableRecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private final Context mContext;
    private final Chat mChat;
    private final Profile mUser;
    private final SortedList<Message> mMessages;

    private final int mBlue30;
    private final int mBlue15;

    ChatAdapter(Context context, Chat chat, Profile user) {
        this.mContext = context;
        this.mChat = chat;
        this.mUser = user;

        mBlue15 = ContextCompat.getColor(mContext, R.color.blue_15);
        mBlue30 = ContextCompat.getColor(mContext, R.color.blue_30);

        mMessages =
                new SortedList<>(
                        Message.class,
                        new SortedListAdapterCallback<Message>(this) {
                            @Override
                            public int compare(Message m1, Message m2) {
                                return (int) (m1.getSentTime() - m2.getSentTime());
                            }

                            @Override
                            public boolean areContentsTheSame(Message oldItem, Message newItem) {
                                return oldItem.equals(newItem);
                            }

                            @Override
                            public boolean areItemsTheSame(Message item1, Message item2) {
                                return item1.getId().equals(item2.getId());
                            }
                        });
    }

    @Override
    public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new MessageViewHolder(
                LayoutInflater.from(mContext).inflate(R.layout.chat_message, parent, false));
    }

    @Override
    public void onBindViewHolder(final MessageViewHolder holder, int position) {
        Message message = mMessages.get(position);
        Profile sender = mChat.getParticipants().get(mMessages.get(position).getSenderId());
        if (sender == null) {
            sender = mUser;
        }

        Glide.with(mContext)
                .load(sender.getProfileImageSource())
                .asBitmap()
                .placeholder(R.drawable.ic_face_white_24dp)
                .into(
                        new SimpleTarget<Bitmap>(100, 100) {
                            @Override
                            public void onResourceReady(
                                    Bitmap resource,
                                    GlideAnimation<? super Bitmap> glideAnimation) {
                                RoundedBitmapDrawable circularBitmapDrawable =
                                        RoundedBitmapDrawableFactory.create(
                                                mContext.getResources(), resource);
                                circularBitmapDrawable.setCircular(true);
                                holder.profileImage.setImageDrawable(circularBitmapDrawable);
                            }
                        });

        // Convert to just the first name of the sender or short hand it if the sender is you.
        String name;
        if (isUser(sender.getId())) {
            name = "You";
        } else {
            name = sender.getName().split("\\s")[0];
        }
        holder.textName.setText(name);

        // Odd messages have a darker background.
        if (position % 2 == 0) {
            holder.parentLayout.setBackgroundColor(mBlue15);
        } else {
            holder.parentLayout.setBackgroundColor(mBlue30);
        }

        holder.textContent.setText(message.getText());
        holder.textTime.setText(millisToDateTime(message.getSentTime()));
    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    public void addMessage(Message message) {
        mMessages.add(message);
    }

    public void addMessages(Collection<Message> messages) {
        // There is a bug with {@link SortedList#addAll} that will add new items to the end
        // of the list allowing duplications in the view which is unexpected behavior
        // https://code.google.com/p/android/issues/detail?id=201618
        // so we will mimic the add all operation (add individually but execute in one batch)
        mMessages.beginBatchedUpdates();
        for (Message message : messages) {
            mMessages.add(message);
        }
        mMessages.endBatchedUpdates();
    }

    /**
     * Converts time since epoch to Month Date Time.
     *
     * @param time since epoch
     * @return String formatted in Month Date HH:MM
     */
    private String millisToDateTime(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        String month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US);
        int date = cal.get(Calendar.DAY_OF_MONTH);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        return month + " " + date + " " + hour + ":" + String.format(Locale.US, "%02d", minute);
    }

    private boolean isUser(String id) {
        return id.equals(mUser.getId());
    }

    /** View holder to encapsulate the details of a chat message. */
    class MessageViewHolder extends RecyclerView.ViewHolder {

        final ViewGroup parentLayout;
        final TextView textContent;
        final TextView textName;
        final ImageView profileImage;
        final TextView textTime;

        public MessageViewHolder(View itemView) {
            super(itemView);

            parentLayout = (PercentRelativeLayout) itemView.findViewById(R.id.layout_container);
            textContent = (TextView) itemView.findViewById(R.id.text_content);
            textName = (TextView) itemView.findViewById(R.id.text_name);
            textTime = (TextView) itemView.findViewById(R.id.text_time);
            profileImage = (ImageView) itemView.findViewById(R.id.profile_img);
        }
    }
}
