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

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.car.apps.common.CircleBitmapDrawable;
import com.android.car.apps.common.LetterTileDrawable;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * A {@link android.support.v7.widget.RecyclerView.ViewHolder} that will parse relevant
 * views out of a {@code contact_result} layout.
 */
public class ContactResultViewHolder extends RecyclerView.ViewHolder {
    private final Context mContext;
    private final View mContactCard;
    private final TextView mContactName;
    private final ImageView mContactPicture;

    public ContactResultViewHolder(View view) {
        super(view);
        mContext = view.getContext();
        mContactCard = view.findViewById(R.id.contact_result_card);
        mContactName = view.findViewById(R.id.contact_name);
        mContactPicture = view.findViewById(R.id.contact_picture);
    }

    /**
     * Populates the view that is represented by this ViewHolder with the information in the
     * provided {@link ContactDetails}.
     */
    public void bind(ContactDetails details, int itemCount) {
        updateBackground(itemCount);

        mContactCard.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setAction(TelecomIntents.ACTION_SHOW_CONTACT_DETAILS);
            intent.putExtra(TelecomIntents.CONTACT_LOOKUP_URI_EXTRA, details.lookupUri.toString());
            mContext.startActivity(intent);
        });

        mContactName.setText(details.displayName);

        if (details.photoUri == null) {
            setLetterDrawableForContact(details);
            return;
        }

        Bitmap bitmap = getContactBitmapFromUri(mContext, details.photoUri);
        if (bitmap == null) {
            setLetterDrawableForContact(details);
        } else {
            mContactPicture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mContactPicture.setImageDrawable(
                    new CircleBitmapDrawable(mContext.getResources(), bitmap));
        }
    }

    /**
     * Sets the contact picture to be a rounded, colored circle that has the first letter of the
     * contact's name in it.
     */
    private void setLetterDrawableForContact(ContactDetails details) {
        mContactPicture.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LetterTileDrawable letterTileDrawable = new LetterTileDrawable(mContext.getResources());
        letterTileDrawable.setContactDetails(details.displayName, details.displayName);
        letterTileDrawable.setIsCircular(true);
        mContactPicture.setImageDrawable(letterTileDrawable);
    }

    /**
     * Sets the appropriate background on the card containing the preset information. The cards
     * need to have rounded corners depending on its position in the list and the number of items
     * in the list.
     */
    private void updateBackground(int itemCount) {
        int position = getAdapterPosition();

        // Correctly set the background for each card. Only the top and last card should
        // have rounded corners.
        if (itemCount == 1) {
            // One card - all corners are rounded
            mContactCard.setBackgroundResource(
                    R.drawable.car_card_rounded_top_bottom_background);
        } else if (position == 0) {
            // First card gets rounded top
            mContactCard.setBackgroundResource(R.drawable.car_card_rounded_top_background);
        } else if (position == itemCount - 1) {
            // Last one has a rounded bottom
            mContactCard.setBackgroundResource(R.drawable.car_card_rounded_bottom_background);
        } else {
            // Middle has no rounded corners
            mContactCard.setBackgroundResource(R.color.car_card);
        }
    }

    /**
     * Retrieves the picture that is specified by the given {@link Uri}.
     */
    @Nullable
    private static Bitmap getContactBitmapFromUri(Context context, Uri uri) {
        try {
            InputStream input = context.getContentResolver().openInputStream(uri);
            return input == null ? null : BitmapFactory.decodeStream(input);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    /**
     * A struct that holds the details for a contact row.
     */
    public static class ContactDetails {
        public final String displayName;
        public final Uri photoUri;
        public final Uri lookupUri;

        public ContactDetails(String displayName, String photoUri, Uri lookupUri) {
            this.displayName = displayName;
            this.photoUri = photoUri == null ? null : Uri.parse(photoUri);
            this.lookupUri = lookupUri;
        }
    }
}
