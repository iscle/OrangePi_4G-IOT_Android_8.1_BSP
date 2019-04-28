/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.stream.telecom;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.telecom.Call;
import android.telecom.GatewayInfo;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.LruCache;
import com.android.car.apps.common.CircleBitmapDrawable;
import com.android.car.apps.common.LetterTileDrawable;
import com.android.car.stream.R;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;

/**
 * Telecom related utility methods.
 */
public class TelecomUtils {
    private static final int LRU_CACHE_SIZE = 4194304; /** 4 mb **/

    private static final String[] CONTACT_ID_PROJECTION = new String[] {
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.TYPE,
            ContactsContract.PhoneLookup.LABEL,
            ContactsContract.PhoneLookup._ID
    };

    private static String sVoicemailNumber;

    private static LruCache<String, Bitmap> sContactPhotoNumberCache;
    private static LruCache<Long, Bitmap> sContactPhotoIdCache;
    private static HashMap<String, String> sContactNameCache;
    private static HashMap<String, Integer> sContactIdCache;
    private static HashMap<String, String> sFormattedNumberCache;
    private static HashMap<String, String> sDisplayNameCache;

    /**
     * Create a round bitmap icon to represent the call. If a contact photo does not exist,
     * a letter tile will be used instead.
     */
    public static Bitmap createStreamCardSecondaryIcon(Context context, String number) {
        Resources res = context.getResources();
        Bitmap largeIcon
                = TelecomUtils.getContactPhotoFromNumber(context.getContentResolver(), number);
        if (largeIcon == null) {
            LetterTileDrawable ltd = new LetterTileDrawable(res);
            String name = TelecomUtils.getDisplayName(context, number);
            ltd.setContactDetails(name, number);
            ltd.setIsCircular(true);
            int size = res.getDimensionPixelSize(R.dimen.stream_card_secondary_icon_dimen);
            largeIcon = ltd.toBitmap(size);
        }

        return new CircleBitmapDrawable(res, largeIcon)
                .toBitmap(res.getDimensionPixelSize(R.dimen.stream_card_secondary_icon_dimen));
    }


    /**
     * Fetch contact photo by number from local cache.
     *
     * @param number
     * @return Contact photo if it's in the cache, otherwise null.
     */
    @Nullable
    public static Bitmap getCachedContactPhotoFromNumber(String number) {
        if (number == null) {
            return null;
        }

        if (sContactPhotoNumberCache == null) {
            sContactPhotoNumberCache = new LruCache<String, Bitmap>(LRU_CACHE_SIZE) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
        }
        return sContactPhotoNumberCache.get(number);
    }

    @WorkerThread
    public static Bitmap getContactPhotoFromNumber(ContentResolver contentResolver, String number) {
        if (number == null) {
            return null;
        }

        Bitmap photo = getCachedContactPhotoFromNumber(number);
        if (photo != null) {
            return photo;
        }

        int id = getContactIdFromNumber(contentResolver, number);
        if (id == 0) {
            return null;
        }
        photo = getContactPhotoFromId(contentResolver, id);
        if (photo != null) {
            sContactPhotoNumberCache.put(number, photo);
        }
        return photo;
    }

    /**
     * Return the contact id for the given contact id
     * @param id the contact id to get the photo for
     * @return the contact photo if it is found, null otherwise.
     */
    public static Bitmap getContactPhotoFromId(ContentResolver contentResolver, long id) {
        if (sContactPhotoIdCache == null) {
            sContactPhotoIdCache = new LruCache<Long, Bitmap>(LRU_CACHE_SIZE) {
                @Override
                protected int sizeOf(Long key, Bitmap value) {
                    return value.getByteCount();
                }
            };
        } else if (sContactPhotoIdCache.get(id) != null) {
            return sContactPhotoIdCache.get(id);
        }

        Uri photoUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
        InputStream photoDataStream = ContactsContract.Contacts.openContactPhotoInputStream(
                contentResolver, photoUri, true);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferQualityOverSpeed = true;
        // Scaling will be handled by later. We shouldn't scale multiple times to avoid
        // quality lost due to multiple potential scaling up and down.
        options.inScaled = false;

        Rect nullPadding = null;
        Bitmap photo = BitmapFactory.decodeStream(photoDataStream, nullPadding, options);
        if (photo != null) {
            photo.setDensity(Bitmap.DENSITY_NONE);
            sContactPhotoIdCache.put(id, photo);
        }
        return photo;
    }

    /**
     * Return the contact id for the given phone number.
     * @param number Caller phone number
     * @return the contact id if it is found, 0 otherwise.
     */
    public static int getContactIdFromNumber(ContentResolver cr, String number) {
        if (number == null || number.isEmpty()) {
            return 0;
        }
        if (sContactIdCache == null) {
            sContactIdCache = new HashMap<>();
        } else if (sContactIdCache.containsKey(number)) {
            return sContactIdCache.get(number);
        }

        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number));
        Cursor cursor = cr.query(uri, CONTACT_ID_PROJECTION, null, null, null);

        try {
            if (cursor != null && cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndex(ContactsContract.PhoneLookup._ID));
                sContactIdCache.put(number, id);
                return id;
            }
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0;
    }

    public static String getDisplayName(Context context, String number) {
        return getDisplayName(context, number, (Uri)null);
    }

    public static String getDisplayName(Context context, Call call) {
        // A call might get created before its children are added. In that case, the display name
        // would go from "Unknown" to "Conference call" therefore we don't want to cache it.
        if (call.getChildren() != null && call.getChildren().size() > 0) {
            return context.getString(R.string.conference_call);
        }
        return getDisplayName(context, getNumber(call), getGatewayInfoOriginalAddress(call));
    }

    private static Uri getGatewayInfoOriginalAddress(Call call) {
        if (call == null || call.getDetails() == null) {
            return null;
        }
        GatewayInfo gatewayInfo = call.getDetails().getGatewayInfo();

        if (gatewayInfo != null && gatewayInfo.getOriginalAddress() != null) {
            return gatewayInfo.getGatewayAddress();
        }
        return null;
    }

    /**
     * Return the phone number of the call. This CAN return null under certain circumstances such
     * as if the incoming number is hidden.
     */
    public static String getNumber(Call call) {
        if (call == null || call.getDetails() == null) {
            return null;
        }

        Uri gatewayInfoOriginalAddress = getGatewayInfoOriginalAddress(call);
        if (gatewayInfoOriginalAddress != null) {
            return gatewayInfoOriginalAddress.getSchemeSpecificPart();
        }

        if (call.getDetails().getHandle() != null) {
            return call.getDetails().getHandle().getSchemeSpecificPart();
        }
        return null;
    }

    private static String getContactNameFromNumber(ContentResolver cr, String number) {
        if (sContactNameCache == null) {
            sContactNameCache = new HashMap<>();
        } else if (sContactNameCache.containsKey(number)) {
            return sContactNameCache.get(number);
        }

        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));

        Cursor cursor = null;
        String name = null;
        try {
            cursor = cr.query(uri,
                    new String[] {ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
                sContactNameCache.put(number, name);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return name;
    }

    private static String getDisplayName(
            Context context, String number, Uri gatewayOriginalAddress) {
        if (sDisplayNameCache == null) {
            sDisplayNameCache = new HashMap<>();
        } else {
            if (sDisplayNameCache.containsKey(number)) {
                return sDisplayNameCache.get(number);
            }
        }

        if (TextUtils.isEmpty(number)) {
            return context.getString(R.string.unknown);
        }
        ContentResolver cr = context.getContentResolver();
        String name;
        if (number.equals(getVoicemailNumber(context))) {
            name = context.getString(R.string.voicemail);
        } else {
            name = getContactNameFromNumber(cr, number);
        }

        if (name == null) {
            name = getFormattedNumber(context, number);
        }
        if (name == null && gatewayOriginalAddress != null) {
            name = gatewayOriginalAddress.getSchemeSpecificPart();
        }
        if (name == null) {
            name = context.getString(R.string.unknown);
        }
        sDisplayNameCache.put(number, name);
        return name;
    }

    public static String getVoicemailNumber(Context context) {
        if (sVoicemailNumber == null) {
            TelephonyManager tm =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            sVoicemailNumber = tm.getVoiceMailNumber();
        }
        return sVoicemailNumber;
    }

    public static String getFormattedNumber(Context context, @Nullable String number) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }

        if (sFormattedNumberCache == null) {
            sFormattedNumberCache = new HashMap<>();
        } else {
            if (sFormattedNumberCache.containsKey(number)) {
                return sFormattedNumberCache.get(number);
            }
        }

        String countryIso = getSimRegionCode(context);
        String e164 = PhoneNumberUtils.formatNumberToE164(number, countryIso);
        String formattedNumber = PhoneNumberUtils.formatNumber(number, e164, countryIso);
        formattedNumber = TextUtils.isEmpty(formattedNumber) ? number : formattedNumber;
        sFormattedNumberCache.put(number, formattedNumber);
        return formattedNumber;
    }

    /**
     * Wrapper around TelephonyManager.getSimCountryIso() that will fallback to locale or USA ISOs
     * if it finds bogus data.
     */
    private static String getSimRegionCode(Context context) {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        // This can be null on some phones (and is null on robolectric default TelephonyManager)
        String countryIso = telephonyManager.getSimCountryIso();
        if (TextUtils.isEmpty(countryIso) || countryIso.length() != 2) {
            countryIso = Locale.getDefault().getCountry();
            if (countryIso == null || countryIso.length() != 2) {
                countryIso = "US";
            }
        }

        return countryIso.toUpperCase(Locale.US);
    }
}