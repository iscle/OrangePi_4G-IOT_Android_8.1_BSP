/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.provider.CallLog;
import android.support.annotation.NonNull;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.android.car.apps.common.CircleBitmapDrawable;
import com.android.car.apps.common.LetterTileDrawable;
import com.android.car.dialer.telecom.PhoneLoader;
import com.android.car.dialer.telecom.TelecomUtils;

import java.util.ArrayList;
import java.util.List;

class CallLogListingTask extends AsyncTask<Void, Void, Void> {
    static class CallLogItem {
        final String mTitle;
        final String mText;
        final String mNumber;
        final Bitmap mIcon;

        public CallLogItem(String title, String text, String number, Bitmap icon) {
            mTitle = title;
            mText = text;
            mNumber = number;
            mIcon = icon;
        }
    }

    interface LoadCompleteListener {
        void onLoadComplete(List<CallLogItem> items);
    }


    // Like a constant but needs a context so not static.
    private final String VOICEMAIL_NUMBER;

    private Context mContext;
    private Cursor mCursor;
    private List<CallLogItem> mItems;
    private LoadCompleteListener mListener;

    CallLogListingTask(Context context, Cursor cursor,
            @NonNull LoadCompleteListener listener) {
        mContext = context;
        mCursor = cursor;
        mItems = new ArrayList<>(mCursor.getCount());
        mListener = listener;
        VOICEMAIL_NUMBER = TelecomUtils.getVoicemailNumber(mContext);
    }

    private String maybeAppendCount(StringBuilder sb, int count) {
        if (count > 1) {
            sb.append(" (").append(count).append(")");
        }
        return sb.toString();
    }

    private String getContactName(String cachedName, String number,
            int count, boolean isVoicemail) {
        if (cachedName != null) {
            return maybeAppendCount(new StringBuilder(cachedName), count);
        }

        StringBuilder sb = new StringBuilder();
        if (isVoicemail) {
            sb.append(mContext.getString(R.string.voicemail));
        } else {
            String displayName = TelecomUtils.getDisplayName(mContext, number);
            if (TextUtils.isEmpty(displayName)) {
                displayName = mContext.getString(R.string.unknown);
            }
            sb.append(displayName);
        }

        return maybeAppendCount(sb, count);
    }

    private Bitmap getContactImage(Context context, ContentResolver contentResolver,
            String name, String number) {
        Resources r = context.getResources();
        int size = r.getDimensionPixelSize(R.dimen.dialer_menu_icon_container_width);

        Bitmap bitmap = TelecomUtils.getContactPhotoFromNumber(contentResolver, number);
        if (bitmap != null) {
            return new CircleBitmapDrawable(r, bitmap).toBitmap(size);
        }

        LetterTileDrawable letterTileDrawable = new LetterTileDrawable(r);
        letterTileDrawable.setContactDetails(name, number);
        letterTileDrawable.setIsCircular(true);
        return letterTileDrawable.toBitmap(size);
    }

    private static CharSequence getRelativeTime(long millis) {
        boolean validTimestamp = millis > 0;

        return validTimestamp ? DateUtils.getRelativeTimeSpanString(
                millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE) : null;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        ContentResolver resolver = mContext.getContentResolver();

        try {
            if (mCursor != null) {
                int cachedNameColumn = PhoneLoader.getNameColumnIndex(mCursor);
                int numberColumn = PhoneLoader.getNumberColumnIndex(mCursor);
                int dateColumn = mCursor.getColumnIndex(CallLog.Calls.DATE);

                while (mCursor.moveToNext()) {
                    int count = 1;
                    String number = mCursor.getString(numberColumn);

                    // We want to group calls to the same number into one so seek forward as many
                    // entries as possible as long as the number is the same.
                    int position = mCursor.getPosition();
                    while (mCursor.moveToNext()) {
                        String nextNumber = mCursor.getString(numberColumn);
                        if (equalNumbers(number, nextNumber)) {
                            count++;
                        } else {
                            break;
                        }
                    }
                    mCursor.moveToPosition(position);

                    boolean isVoicemail = number.equals(VOICEMAIL_NUMBER);
                    String name = getContactName(mCursor.getString(cachedNameColumn),
                            number, count, isVoicemail);

                    // Not sure why this is the only column checked here but I'm assuming this was
                    // to work around some bug on some device.
                    long millis = dateColumn == -1 ? 0 : mCursor.getLong(dateColumn);

                    StringBuffer secondaryText = new StringBuffer();
                    CharSequence relativeDate = getRelativeTime(millis);

                    // Append the type (work, mobile etc.) if it isnt voicemail.
                    if (!isVoicemail) {
                        CharSequence type = TelecomUtils.getTypeFromNumber(mContext, number);
                        secondaryText.append(type);
                        if (!TextUtils.isEmpty(type) && !TextUtils.isEmpty(relativeDate)) {
                            secondaryText.append(", ");
                        }
                    }

                    // Add in the timestamp.
                    if (relativeDate != null) {
                        secondaryText.append(relativeDate);
                    }

                    Bitmap contactImage = getContactImage(mContext, resolver, name, number);

                    CallLogItem item =
                            new CallLogItem(name, secondaryText.toString(), number, contactImage);
                    mItems.add(item);

                    // Since we deduplicated count rows, we can move all the way to that row so the
                    // next iteration takes us to the row following the last duplicate row.
                    if (count > 1) {
                        mCursor.moveToPosition(position + count - 1);
                    }
                }
            }
        } finally {
            if (mCursor != null) {
                mCursor.close();
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        mListener.onLoadComplete(mItems);
    }

    /**
     * Determines if the specified number is actually a URI
     * (i.e. a SIP address) rather than a regular PSTN phone number,
     * based on whether or not the number contains an "@" character.
     *
     * @return true if number contains @
     *
     * from android.telephony.PhoneNumberUtils
     */
    public static boolean isUriNumber(String number) {
        // Note we allow either "@" or "%40" to indicate a URI, in case
        // the passed-in string is URI-escaped.  (Neither "@" nor "%40"
        // will ever be found in a legal PSTN number.)
        return number != null && (number.contains("@") || number.contains("%40"));
    }

    private static boolean equalNumbers(String number1, String number2) {
        if (isUriNumber(number1) || isUriNumber(number2)) {
            return compareSipAddresses(number1, number2);
        } else {
            return PhoneNumberUtils.compare(number1, number2);
        }
    }

    private static boolean compareSipAddresses(String number1, String number2) {
        if (number1 == null || number2 == null) {
            return number1 == null && number2 == null;
        }

        String[] address1 = splitSipAddress(number1);
        String[] address2 = splitSipAddress(number2);

        return address1[0].equals(address2[0]) && address1[1].equals(address2[1]);
    }

    /**
     * Splits a sip address on either side of the @ sign and returns both halves.
     * If there is no @ sign, user info will be number and rest will be empty string
     * @param number the sip number to split
     * @return a string array of size 2. Element 0 is the user info (left side of @ sign) and
     *         element 1 is the rest (right side of @ sign).
     */
    private static String[] splitSipAddress(String number) {
        String[] values = new String[2];
        int index = number.indexOf('@');
        if (index == -1) {
            values[0] = number;
            values[1] = "";
        } else {
            values[0] = number.substring(0, index);
            values[1] = number.substring(index);
        }
        return values;
    }
}
