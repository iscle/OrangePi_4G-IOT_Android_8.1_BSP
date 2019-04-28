/*
 * Copyright (c) 2016, The Android Open Source Project
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
package com.android.car.stream;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A parcelable that is used for communication between various stream components.
 * The card is uniquely identified by the type and id.
 */
public class StreamCard extends AbstractBundleable {
    private final static String TAG = "StreamCard";

    private final static String TYPE_KEY = "type";
    private final static String ID_KEY = "id";
    private final static String PRIORITY_KEY = "priority";
    private final static String TIMESTAMP_KEY = "timestamp";
    private final static String CONTENT_PENDING_INTENT_KEY = "content_pending_intent";
    private final static String PRIMARY_ICON_KEY = "primary_icon";
    private final static String SECONDARY_ICON_KEY = "secondary_icon";
    private final static String PRIMARY_TEXT_KEY = "primary_text";
    private final static String SECONDARY_TEXT_KEY = "secondary_text";
    private final static String DESCRIPTION_KEY = "description";
    private final static String CARD_EXTENSION_BUNDLE_KEY = "extension_bundle";
    private final static String CARD_EXTENSION_CLASS_KEY = "extension_key";

    /** The type of the card */
    private int mType;
    /** The unique id of this card */
    private long mId;
    /** Priority or rank compared to other cards */
    private int mPriority;
    /** Time at which the StreamCard was generated */
    private long mTimestamp;
    /** Primary action to be taken when the entire card is clicked */
    private PendingIntent mContentPendingIntent;
    /** Primary icon of the stream card */
    private Bitmap mPrimaryIcon;
    /** Secondary icon of the stream card */
    private Bitmap mSecondaryIcon;
    /** Primary text of the stream card */
    private CharSequence mPrimaryText;
    /** Secondary text of the stream card */
    private CharSequence mSecondaryText;
    /** Additional meta information for the card */
    private StreamCardExtension mCardExtension;

    /** String description of the {@link StreamCard} */
    private String mDescription;

    public static final Creator<StreamCard> CREATOR = new BundleableCreator<>(StreamCard.class);

    public StreamCard() {
    }

    private StreamCard(int type, long id, int priority, long timestamp,
            PendingIntent contentPendingIntent, Bitmap primaryIcon, Bitmap secondaryIcon,
            CharSequence primaryText, CharSequence secondaryText, String description,
            StreamCardExtension cardExtension) {
        mType = type;
        mId = id;
        mPriority = priority;
        mTimestamp = timestamp;
        mContentPendingIntent = contentPendingIntent;
        mPrimaryIcon = primaryIcon;
        mSecondaryIcon = secondaryIcon;
        mPrimaryText = primaryText;
        mSecondaryText = secondaryText;
        mDescription = description;
        mCardExtension = cardExtension;
    }

    @Override
    protected void writeToBundle(Bundle bundle) {
        bundle.putInt(TYPE_KEY, mType);
        bundle.putLong(ID_KEY, mId);
        bundle.putInt(PRIORITY_KEY, mPriority);
        bundle.putLong(TIMESTAMP_KEY, mTimestamp);
        bundle.putParcelable(CONTENT_PENDING_INTENT_KEY, mContentPendingIntent);
        // Note there is a 1MB limit on the transaction size of the binder, if the bitmaps
        // are too large, pass through a resource or URI
        bundle.putParcelable(PRIMARY_ICON_KEY, mPrimaryIcon);
        bundle.putParcelable(SECONDARY_ICON_KEY, mSecondaryIcon);
        bundle.putString(DESCRIPTION_KEY, mDescription);
        bundle.putCharSequence(PRIMARY_TEXT_KEY, mPrimaryText);
        bundle.putCharSequence(SECONDARY_TEXT_KEY, mSecondaryText);

        if (mCardExtension != null) {
            Bundle extension = new Bundle();
            mCardExtension.writeToBundle(extension);
            bundle.putString(CARD_EXTENSION_CLASS_KEY, mCardExtension.getClass().getName());
            bundle.putBundle(CARD_EXTENSION_BUNDLE_KEY, extension);
        }
    }

    @Override
    protected void readFromBundle(Bundle bundle) {
        mType = bundle.getInt(TYPE_KEY);
        mId = bundle.getLong(ID_KEY);
        mPriority = bundle.getInt(PRIORITY_KEY);
        mTimestamp = bundle.getLong(TIMESTAMP_KEY, System.currentTimeMillis());
        mContentPendingIntent = bundle.getParcelable(CONTENT_PENDING_INTENT_KEY);
        mPrimaryIcon = bundle.getParcelable(PRIMARY_ICON_KEY);
        mSecondaryIcon = bundle.getParcelable(SECONDARY_ICON_KEY);
        mDescription = bundle.getString(DESCRIPTION_KEY);
        mPrimaryText = bundle.getCharSequence(PRIMARY_TEXT_KEY);
        mSecondaryText = bundle.getCharSequence(SECONDARY_TEXT_KEY);
        mCardExtension = extractExtension(bundle);
    }

    private StreamCardExtension extractExtension(Bundle bundle) {
        String className = bundle.getString(CARD_EXTENSION_CLASS_KEY);
        Bundle extensionBundle = bundle.getBundle(CARD_EXTENSION_BUNDLE_KEY);
        if (className == null || extensionBundle == null) {
            return null;
        }

        StreamCardExtension extension = null;
        try {
            Class clazz = getClass().getClassLoader().loadClass(className);
            extension = (StreamCardExtension) clazz.newInstance();
            extension.readFromBundle(extensionBundle);
        } catch (Exception e) {
            Log.e(TAG, "Failed to instantiate " + className, e);
        }
        return extension;
    }

    @Override
    public String toString() {
        String extensionName = null;
        if (mCardExtension != null) {
            extensionName = mCardExtension.getClass().getName();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\nType: " + mType);
        sb.append("\nId: " + mId);
        sb.append("\nContent Pending Intent: " + mContentPendingIntent);
        sb.append("\nDescription: " + mDescription);
        sb.append("\nCard Extension: " + extensionName);
        sb.append("\nTimestamp: "
                + SimpleDateFormat.getDateInstance().format(new Date(mTimestamp)));

        return sb.toString();
    }

    public void setPriority(int priority) {
        mPriority = priority;
    }

    public int getPriority() {
        return mPriority;
    }

    public String getDescription() {
        return mDescription;
    }

    public long getId() {
        return mId;
    }

    public int getType() {
        return mType;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public CharSequence getPrimaryText() {
        return mPrimaryText;
    }

    public CharSequence getSecondaryText() {
        return mSecondaryText;
    }

    public Bitmap getPrimaryIcon() {
        return mPrimaryIcon;
    }

    public Bitmap getSecondaryIcon() {
        return mSecondaryIcon;
    }

    public PendingIntent getContentPendingIntent() {
        return mContentPendingIntent;
    }

    public AbstractBundleable getCardExtension() {
        return mCardExtension;
    }

    /**
     * Builder to generate a {@link StreamCard}
     */
    public static class Builder {
        private int type;
        private long id;
        private int priority;
        private long timestamp;
        private Bitmap primaryIcon;
        private Bitmap secondaryIcon;
        private CharSequence primaryText;
        private CharSequence secondaryText;
        private String description;
        private PendingIntent contentPendingIntent;
        private StreamCardExtension cardExtension;

        public Builder(int type, long id, long timestamp) {
            this.type = type;
            this.id = id;
            this.timestamp = timestamp;
        }

        public StreamCard build() {
            return new StreamCard(type, id, priority, timestamp, contentPendingIntent, primaryIcon,
                    secondaryIcon, primaryText, secondaryText, description, cardExtension);
        }

        /**
         * Set the primary text of this Stream Card.
         */
        public Builder setPrimaryText(CharSequence text) {
            this.primaryText = text;
            return this;
        }

        /**
         * Set the secondary text of this Stream Card.
         */
        public Builder setSecondaryText(CharSequence text) {
            this.secondaryText = text;
            return this;
        }

        /**
         * Set the pending intent to be executed when the card is clicked.
         */
        public Builder setClickAction(PendingIntent action) {
            this.contentPendingIntent = action;
            return this;
        }

        /**
         * Set the bitmap for the primary icon.
         */
        public Builder setPrimaryIcon(Bitmap bitmap){
            this.primaryIcon = bitmap;
            return this;
        }

        /**
         * Set the bitmap for the secondary icon.
         */
        public Builder setSecondaryIcon(Bitmap bitmap){
            this.secondaryIcon = bitmap;
            return this;
        }

        /**
         * Set the description of the card.
         */
        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the extension of the card.
         */
        public Builder setCardExtension(StreamCardExtension extension) {
            this.cardExtension = extension;
            return this;
        }
    }
}
