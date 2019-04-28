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
package com.android.car.dialer;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.car.dialer.telecom.PhoneLoader;
import com.android.car.dialer.telecom.TelecomUtils;

/**
 * Encapsulates data about a phone Contact entry. Typically loaded from the local Contact store.
 */
public class ContactEntry implements Comparable<ContactEntry> {
    private final Context mContext;

    @Nullable
    public String name;
    public String number;
    public boolean isStarred;
    public int pinnedPosition;

    /**
     * Parses a Contact entry for a Cursor loaded from the OS Strequents DB.
     */
    public static ContactEntry fromCursor(Cursor cursor, Context context) {
        int nameColumn = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
        int starredColumn = cursor.getColumnIndex(ContactsContract.Contacts.STARRED);
        int pinnedColumn = cursor.getColumnIndex("pinned");

        String name = cursor.getString(nameColumn);
        String number = PhoneLoader.getPhoneNumber(cursor, context.getContentResolver());
        int starred = cursor.getInt(starredColumn);
        int pinnedPosition = cursor.getInt(pinnedColumn);
        return new ContactEntry(context, name, number, starred > 0, pinnedPosition);
    }

    public ContactEntry(
            Context context, String name, String number, boolean isStarred, int pinnedPosition) {
        mContext = context;
        this.name = name;
        this.number = number;
        this.isStarred = isStarred;
        this.pinnedPosition = pinnedPosition;
    }

    /**
     * Retrieves a best-effort contact name ready for display to the user.
     * It takes into account the number associated with a name for fail cases.
     */
    public String getDisplayName() {
        if (!TextUtils.isEmpty(name)) {
            return name;
        }
        if (isVoicemail()) {
            return mContext.getResources().getString(R.string.voicemail);
        } else {
            String displayName = TelecomUtils.getFormattedNumber(mContext, number);
            if (TextUtils.isEmpty(displayName)) {
                displayName = mContext.getString(R.string.unknown);
            }
            return displayName;
        }
    }

    public boolean isVoicemail() {
        return number.equals(TelecomUtils.getVoicemailNumber(mContext));
    }

    @Override
    public int compareTo(ContactEntry strequentContactEntry) {
        if (isStarred == strequentContactEntry.isStarred) {
            if (pinnedPosition == strequentContactEntry.pinnedPosition) {
                if (name == strequentContactEntry.name) {
                    return compare(number, strequentContactEntry.number);
                }
                return compare(name, strequentContactEntry.name);
            } else {
                if (pinnedPosition > 0 && strequentContactEntry.pinnedPosition > 0) {
                    return pinnedPosition - strequentContactEntry.pinnedPosition;
                }

                if (pinnedPosition > 0) {
                    return -1;
                }

                return 1;
            }
        }

        if (isStarred) {
            return -1;
        }

        return 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ContactEntry) {
            ContactEntry other = (ContactEntry) obj;
            if (compare(name, other.name) == 0
                    && compare(number, other.number) == 0
                    && isStarred == other.isStarred
                    && pinnedPosition == other.pinnedPosition) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (isStarred ? 1 : 0);
        result = 31 * result + pinnedPosition;
        result = 31 * result + (name == null ? 0 : name.hashCode());
        result = 31 * result + (number == null ? 0 : number.hashCode());
        return result;
    }

    private int compare(final String one, final String two) {
        if (one == null ^ two == null) {
            return (one == null) ? -1 : 1;
        }

        if (one == null && two == null) {
            return 0;
        }

        return one.compareTo(two);
    }
}
