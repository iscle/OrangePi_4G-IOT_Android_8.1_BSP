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
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.support.wearable.view.WearableRecyclerView;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.example.android.wearable.wear.messaging.R;
import com.example.android.wearable.wear.messaging.model.Profile;
import java.util.ArrayList;
import java.util.List;

/**
 * An adapter to display a list of mContacts.
 *
 * <p>
 *
 * <p>Each user's image and name will be displayed. The first item in the list is the title and can
 * be scrolled away so that it does not take up as much precious screen space. Uses Glide to load
 * images into view.
 */
public class ContactsListAdapter extends WearableRecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "ContactsListAdapter";

    private static final int TYPE_TITLE = 0;
    private static final int TYPE_CONTENT = 1;

    private static final int INDEX_OFFSET = 1;

    private SortedList<Profile> mContacts;
    private final Context mContext;
    private final ContactsSelectionListener mListener;

    // Keeps track of selected items
    private SparseBooleanArray mSelectedContacts = new SparseBooleanArray();

    /**
     * A listener for when a contact has been selected/unselected. Note that this is not the same
     * thing as when the user *submits* their selected contacts.
     */
    public interface ContactsSelectionListener {
        void onContactInteraction(boolean selected);
    }

    public ContactsListAdapter(
            @NonNull Context context, @NonNull ContactsSelectionListener listener) {
        this.mContext = context;
        mListener = listener;
        mContacts =
                new SortedList<>(
                        Profile.class,
                        new SortedList.BatchedCallback<>(
                                new SortedList.Callback<Profile>() {
                                    @Override
                                    public int compare(Profile left, Profile right) {
                                        // Sort list by last update time and name and list
                                        // descending
                                        if (right != null && left == null) {
                                            return -1;
                                        } else if (right == null && left == null) {
                                            return 0;
                                        } else if (right == null) {
                                            return 1;
                                        }

                                        int comp =
                                                right.getLastUpdatedTime()
                                                        .compareTo(left.getLastUpdatedTime());
                                        if (comp != 0) {
                                            return comp;
                                        } else {
                                            return left.getName().compareTo(right.getName());
                                        }
                                    }

                                    @Override
                                    public void onChanged(int position, int count) {
                                        // Since position 0 is the title
                                        notifyItemRangeChanged(position + INDEX_OFFSET, count);
                                    }

                                    @Override
                                    public boolean areContentsTheSame(
                                            Profile oldItem, Profile newItem) {
                                        return oldItem.equals(newItem);
                                    }

                                    @Override
                                    public boolean areItemsTheSame(Profile item1, Profile item2) {
                                        return item1.getId().equals(item2.getId());
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
                                                fromPosition + INDEX_OFFSET,
                                                toPosition + INDEX_OFFSET);
                                    }
                                }));
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_TITLE) {
            return new ViewHolderTitle(
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.list_title, parent, false));
        } else if (viewType == TYPE_CONTENT) {
            return new ViewHolderContent(
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.contacts_list_item, parent, false));
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof ViewHolderTitle) {
            ((ViewHolderTitle) holder)
                    .title.setText(mContext.getString(R.string.contacts_list_title));
        } else if (holder instanceof ViewHolderContent) {
            final ViewHolderContent viewHolderContent = (ViewHolderContent) holder;
            // Offset due to the title being at position 0.
            Profile contact = mContacts.get(position - INDEX_OFFSET);

            viewHolderContent.name.setText(contact.getName());

            Glide.with(mContext)
                    .load(contact.getProfileImageSource())
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
                                    viewHolderContent.profileImage.setImageDrawable(
                                            circularBitmapDrawable);
                                }
                            });

            viewHolderContent.itemView.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // Get the freshest position.
                            int position = viewHolderContent.getAdapterPosition();
                            toggleContact(position);
                            boolean selected = mSelectedContacts.get(position);
                            if (selected) {
                                Log.d(TAG, "View " + position + " selected");
                                viewHolderContent.itemView.setSelected(true);
                            } else {
                                Log.d(TAG, "View " + position + " deselected");
                                viewHolderContent.itemView.setSelected(false);
                            }
                            mListener.onContactInteraction(selected);
                        }
                    });
        }
    }

    @Override
    public int getItemViewType(int position) {
        return (position == 0) ? TYPE_TITLE : TYPE_CONTENT;
    }

    @Override
    public int getItemCount() {
        // Offset the size since title is position 0.
        return mContacts.size() + INDEX_OFFSET;
    }

    /**
     * Adds the contacts to the collection of contacts to be displayed.
     *
     * @param contacts
     */
    public void addAll(List<Profile> contacts) {
        this.mContacts.addAll(contacts);
        notifyDataSetChanged();
    }

    /**
     * Toggles the selected state of a contact.
     *
     * @param position position of contact.
     */
    private void toggleContact(int position) {
        Log.d(TAG, "Adding position to selected contacts: " + position);
        mSelectedContacts.put(position, !mSelectedContacts.get(position, false));
    }

    /**
     * Returns an array of the selected profiles.
     *
     * @return ArrayList of selected profiles
     */
    public ArrayList<Profile> getSelectedContacts() {
        ArrayList<Profile> selectedContacts = new ArrayList<>();

        for (int i = 0; i < mSelectedContacts.size(); i++) {
            if (mSelectedContacts.valueAt(i)) {
                selectedContacts.add(mContacts.get(mSelectedContacts.keyAt(i) - INDEX_OFFSET));
            }
        }
        return selectedContacts;
    }

    /** Holds reference to the title. */
    public static class ViewHolderTitle extends RecyclerView.ViewHolder {
        public TextView title;

        public ViewHolderTitle(View view) {
            super(view);
            title = (TextView) view.findViewById(R.id.text_title);
        }
    }

    /** Holds references each contact layout element. */
    public static class ViewHolderContent extends RecyclerView.ViewHolder {

        protected final ImageView profileImage;
        protected final TextView name;

        public ViewHolderContent(View itemView) {
            super(itemView);

            profileImage = (ImageView) itemView.findViewById(R.id.profile_img);
            name = (TextView) itemView.findViewById(R.id.text_contact_name);
        }
    }
}
