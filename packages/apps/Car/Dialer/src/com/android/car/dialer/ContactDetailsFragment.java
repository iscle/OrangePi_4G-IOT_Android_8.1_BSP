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
package com.android.car.dialer;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.car.dialer.telecom.TelecomUtils;
import com.android.car.view.PagedListView;

import java.util.ArrayList;
import java.util.List;

/**
 * A fragment that shows the name of the contact, the photo and all listed phone numbers. It is
 * primarily used to respond to the results of search queries but supplyig it with the content://
 * uri of a contact should work too.
 */
public class ContactDetailsFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ContactDetailsFragment";
    private static final String TELEPHONE_URI_PREFIX = "tel:";

    private static final int DETAILS_LOADER_QUERY_ID = 1;
    private static final int PHONE_LOADER_QUERY_ID = 2;

    private static final String KEY_URI = "uri";

    private static final String[] CONTACT_DETAILS_PROJECTION = {
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.DISPLAY_NAME,
        ContactsContract.Contacts.PHOTO_URI,
        ContactsContract.Contacts.HAS_PHONE_NUMBER
    };

    private PagedListView mListView;
    private List<RecyclerView.OnScrollListener> mOnScrollListeners = new ArrayList<>();

    public static ContactDetailsFragment newInstance(Uri uri,
            @Nullable RecyclerView.OnScrollListener listener) {
        ContactDetailsFragment fragment = new ContactDetailsFragment();
        fragment.addOnScrollListener(listener);

        Bundle args = new Bundle();
        args.putParcelable(KEY_URI, uri);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.contact_details, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mListView = view.findViewById(R.id.list_view);
        mListView.setLightMode();

        RecyclerView recyclerView = mListView.getRecyclerView();
        for (RecyclerView.OnScrollListener listener : mOnScrollListeners) {
            recyclerView.addOnScrollListener(listener);
        }

        mOnScrollListeners.clear();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(DETAILS_LOADER_QUERY_ID, null, this);
    }

    /**
     * Adds a {@link android.support.v7.widget.RecyclerView.OnScrollListener} to be notified when
     * the contact details are scrolled.
     *
     * @see RecyclerView#addOnScrollListener(RecyclerView.OnScrollListener)
     */
    public void addOnScrollListener(RecyclerView.OnScrollListener onScrollListener) {
        // If the view has not been created yet, then queue the setting of the scroll listener.
        if (mListView == null) {
            mOnScrollListeners.add(onScrollListener);
            return;
        }

        mListView.getRecyclerView().addOnScrollListener(onScrollListener);
    }

    @Override
    public void onDestroy() {
        // Clear all scroll listeners.
        mListView.getRecyclerView().removeOnScrollListener(null);
        super.onDestroy();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (vdebug()) {
            Log.d(TAG, "onCreateLoader id=" + id);
        }

        if (id != DETAILS_LOADER_QUERY_ID) {
            return null;
        }

        Uri contactUri = getArguments().getParcelable(KEY_URI);
        return new CursorLoader(getContext(), contactUri, CONTACT_DETAILS_PROJECTION,
                null /* selection */, null /* selectionArgs */, null /* sortOrder */);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (vdebug()) {
            Log.d(TAG, "onLoadFinished");
        }

        if (cursor.moveToFirst()) {
            mListView.setAdapter(new ContactDetailsAdapter(cursor));
        }
    }

    @Override
    public void onLoaderReset(Loader loader) {  }

    private boolean vdebug() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }

    private class ContactDetailViewHolder extends RecyclerView.ViewHolder {
        public View card;
        public ImageView leftIcon;
        public TextView title;
        public TextView text;
        public ImageView rightIcon;

        public ContactDetailViewHolder(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            leftIcon = v.findViewById(R.id.icon);
            title = v.findViewById(R.id.title);
            text = v.findViewById(R.id.text);
            rightIcon = v.findViewById(R.id.right_icon);
        }
    }

    private class ContactDetailsAdapter extends RecyclerView.Adapter<ContactDetailViewHolder>
            implements PagedListView.ItemCap {

        private static final int ID_HEADER = 1;
        private static final int ID_CONTENT = 2;

        private final String mContactName;
        @ColorInt private int mIconTint;

        private List<Pair<String, String>> mPhoneNumbers = new ArrayList<>();

        public ContactDetailsAdapter(Cursor cursor) {
            super();

            mIconTint = getContext().getColor(R.color.contact_details_icon_tint);

            int idColIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID);
            String contactId = cursor.getString(idColIdx);
            int nameColIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
            mContactName = cursor.getString(nameColIdx);
            int hasPhoneColIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER);
            boolean hasPhoneNumber = Integer.parseInt(cursor.getString(hasPhoneColIdx)) > 0;

            if (!hasPhoneNumber) {
                return;
            }

            // Fetch the phone number from the contacts db using another loader.
            getLoaderManager().initLoader(PHONE_LOADER_QUERY_ID, null,
                    new LoaderManager.LoaderCallbacks<Cursor>() {
                        @Override
                        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                            return new CursorLoader(getContext(),
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    null, /* All columns **/
                                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                    new String[] { contactId },
                                    null /* sortOrder */);
                        }

                        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
                            while (cursor.moveToNext()) {
                                int typeColIdx = cursor.getColumnIndex(
                                        ContactsContract.CommonDataKinds.Phone.TYPE);
                                int type = cursor.getInt(typeColIdx);
                                int numberColIdx = cursor.getColumnIndex(
                                        ContactsContract.CommonDataKinds.Phone.NUMBER);
                                String number = cursor.getString(numberColIdx);
                                String numberType;
                                switch (type) {
                                    case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
                                        numberType = getString(R.string.type_home);
                                        break;
                                    case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
                                        numberType = getString(R.string.type_work);
                                        break;
                                    case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
                                        numberType = getString(R.string.type_mobile);
                                        break;
                                    default:
                                        numberType = getString(R.string.type_other);
                                }
                                mPhoneNumbers.add(new Pair<>(numberType,
                                        TelecomUtils.getFormattedNumber(getContext(), number)));
                                notifyItemInserted(mPhoneNumbers.size());
                            }
                            notifyDataSetChanged();
                        }

                        public void onLoaderReset(Loader loader) {  }
                    });
        }

        /**
         * Appropriately sets the background for the View that is being bound. This method will
         * allow for rounded corners on either the top or bottom of a card.
         */
        private void setBackground(ContactDetailViewHolder viewHolder) {
            int itemCount = getItemCount();
            int adapterPosition = viewHolder.getAdapterPosition();

            if (itemCount == 1) {
                // Only element - all corners are rounded
                viewHolder.card.setBackgroundResource(
                        R.drawable.car_card_rounded_top_bottom_background);
            } else if (adapterPosition == 0) {
                // First element gets rounded top
                viewHolder.card.setBackgroundResource(R.drawable.car_card_rounded_top_background);
            } else if (adapterPosition == itemCount - 1) {
                // Last one has a rounded bottom
                viewHolder.card.setBackgroundResource(
                        R.drawable.car_card_rounded_bottom_background);
            } else {
                // Middle have no rounded corners
                viewHolder.card.setBackgroundResource(R.color.car_card);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? ID_HEADER : ID_CONTENT;
        }

        @Override
        public void setMaxItems(int maxItems) {
            // Ignore.
        }

        @Override
        public int getItemCount() {
            return mPhoneNumbers.size() + 1;  // +1 for the header row.
        }

        @Override
        public ContactDetailViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            int layoutResId;
            switch (viewType) {
                case ID_HEADER:
                    layoutResId = R.layout.contact_detail_name_image;
                    break;
                case ID_CONTENT:
                    layoutResId = R.layout.contact_details_number;
                    break;
                default:
                    Log.e(TAG, "Unknown view type " + viewType);
                    return null;
            }

            View view = LayoutInflater.from(parent.getContext()).inflate(layoutResId, null);
            return new ContactDetailViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ContactDetailViewHolder viewHolder, int position) {
            switch (viewHolder.getItemViewType()) {
                case ID_HEADER:
                    viewHolder.title.setText(mContactName);
                    if (!mPhoneNumbers.isEmpty()) {
                        String firstNumber = mPhoneNumbers.get(0).second;
                        TelecomUtils.setContactBitmapAsync(getContext(), viewHolder.rightIcon,
                                mContactName, firstNumber);
                    }
                    // Just in case a viewholder object gets recycled.
                    viewHolder.card.setOnClickListener(null);
                    break;
                case ID_CONTENT:
                    Pair<String, String> data = mPhoneNumbers.get(position - 1);
                    viewHolder.title.setText(data.first);  // Type.
                    viewHolder.text.setText(data.second);  // Number.
                    viewHolder.leftIcon.setImageResource(R.drawable.ic_phone);
                    viewHolder.leftIcon.setColorFilter(mIconTint);
                    viewHolder.card.setOnClickListener(v -> {
                        Intent callIntent = new Intent(Intent.ACTION_CALL);
                        callIntent.setData(Uri.parse(TELEPHONE_URI_PREFIX + data.second));
                        getContext().startActivity(callIntent);
                    });
                    break;
                default:
                    Log.e(TAG, "Unknown view type " + viewHolder.getItemViewType());
                    return;
            }
            setBackground(viewHolder);
        }
    }
}
