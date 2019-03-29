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
package com.example.android.wearable.wear.messaging.chatlist;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
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
import java.util.Collection;

/**
 * Adapter for list of chats.
 *
 * <p>If chat is empty, displays icon to start a new chat. Otherwise, activity displays the title of
 * chat as first element followed by the rest of the conversation.
 *
 * <p>The RecyclerView holds the title so it can scroll off the screen as a user scrolls down the
 * page.
 */
public class ChatListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "ChatListAdapter";

    private static final int TYPE_ADD = 0;
    private static final int TYPE_CONTENT = 1;

    private static final int INDEX_OFFSET = 1;

    private final Context mContext;
    private final ChatAdapterListener mListener;
    private final SortedList<Chat> mChats;

    public ChatListAdapter(Context context, ChatAdapterListener listener) {
        this.mContext = context;
        this.mListener = listener;
        mChats =
                new SortedList<>(
                        Chat.class,
                        new SortedList.Callback<Chat>() {

                            // Descending list based on chat's last message time.
                            @Override
                            public int compare(Chat chat1, Chat chat2) {
                                Long diff =
                                        (chat2.getLastMessage().getSentTime()
                                                - chat1.getLastMessage().getSentTime());
                                return diff.intValue();
                            }

                            @Override
                            public void onInserted(int position, int count) {
                                notifyItemRangeInserted(position + INDEX_OFFSET, count);
                            }

                            @Override
                            public void onRemoved(int position, int count) {
                                notifyItemRangeRemoved(position + INDEX_OFFSET, count);
                            }

                            @Override
                            public void onMoved(int fromPosition, int toPosition) {
                                notifyItemMoved(
                                        fromPosition + INDEX_OFFSET, toPosition + INDEX_OFFSET);
                            }

                            @Override
                            public void onChanged(int position, int count) {
                                Log.d(
                                        TAG,
                                        String.format("Item changed %d", position + INDEX_OFFSET));
                                // Since position 0 is the title
                                notifyItemRangeChanged(position + INDEX_OFFSET, count);
                            }

                            @Override
                            public boolean areContentsTheSame(Chat oldItem, Chat newItem) {
                                return oldItem.equals(newItem);
                            }

                            @Override
                            public boolean areItemsTheSame(Chat item1, Chat item2) {
                                return item1.getId().equals(item2.getId());
                            }
                        });
    }

    /**
     * Listens for actions that occur in the adapter; i.e., either the user wants a new chat started
     * or they are opening an existing chat.
     */
    public interface ChatAdapterListener {
        void newChatSelected();

        void openChat(Chat chat);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return TYPE_ADD;
        }
        return TYPE_CONTENT;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_ADD) {
            return new AddChatViewHolder(
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.action_new_chat, parent, false));
        } else {
            return new ChatItemViewHolder(
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.chat_list_item, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {

        if (holder instanceof AddChatViewHolder) {
            ((AddChatViewHolder) holder)
                    .row.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    Log.d(TAG, "New chat has been selected");
                                    mListener.newChatSelected();
                                }
                            });
        } else if (holder instanceof ChatItemViewHolder) {
            final ChatItemViewHolder chatItemViewHolder = (ChatItemViewHolder) holder;

            //need to account for having the add button before the list of mChats
            final Chat chat = mChats.get(position - INDEX_OFFSET);

            chatItemViewHolder.row.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mListener.openChat(chat);
                        }
                    });

            chatItemViewHolder.alias.setText(chat.getAlias());

            Message lastMessage = chat.getLastMessage();
            if (lastMessage != null) {
                Profile lastMessageSender;
                if (chat.getParticipants().size() == 1) {
                    lastMessageSender = chat.getParticipants().values().iterator().next();
                } else {
                    lastMessageSender = chat.getParticipants().get(lastMessage.getSenderId());
                }

                if (lastMessageSender == null) {
                    chatItemViewHolder.aliasImage.setImageResource(R.drawable.ic_face_white_24dp);
                    // Blank out any text that may be left after updating the view holder contents.
                    chatItemViewHolder.lastMessage.setText("");
                } else {
                    String lastMessageText = lastMessage.getText();
                    String messageString = lastMessageSender.getName() + ": " + lastMessageText;

                    chatItemViewHolder.lastMessage.setText(messageString);

                    Glide.with(mContext)
                            .load(lastMessageSender.getProfileImageSource())
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
                                            chatItemViewHolder.aliasImage.setImageDrawable(
                                                    circularBitmapDrawable);
                                        }
                                    });
                }
            }

            // Show group icon if more than 1 participant
            int numParticipants = chat.getParticipants().size();
            if (numParticipants > 1) {
                chatItemViewHolder.aliasImage.setImageResource(R.drawable.ic_group_white_48dp);
            }
        }
    }

    @Override
    public int getItemCount() {
        return mChats.size() + INDEX_OFFSET;
    }

    public void setChats(final Collection<Chat> chats) {
        // There is a bug with {@link SortedList#addAll} that will add new items to the end
        // of the list allowing duplications in the view which is unexpected behavior
        // https://code.google.com/p/android/issues/detail?id=201618
        // so we will mimic the add all operation (add individually but execute in one batch)
        mChats.beginBatchedUpdates();
        for (Chat chat : chats) {
            mChats.add(chat);
        }
        mChats.endBatchedUpdates();
    }

    private class AddChatViewHolder extends RecyclerView.ViewHolder {

        private final ViewGroup row;

        AddChatViewHolder(View itemView) {
            super(itemView);

            row = (ViewGroup) itemView.findViewById(R.id.percent_layout);
        }
    }

    private class ChatItemViewHolder extends RecyclerView.ViewHolder {

        private ViewGroup row;
        private final TextView alias;
        private final ImageView aliasImage;
        private final TextView lastMessage;

        ChatItemViewHolder(View itemView) {
            super(itemView);

            row = (ViewGroup) itemView.findViewById(R.id.layout_chat_list_item);
            alias = (TextView) itemView.findViewById(R.id.text_alias);
            aliasImage = (ImageView) itemView.findViewById(R.id.profile);
            lastMessage = (TextView) itemView.findViewById(R.id.text_last_message);
        }
    }
}
