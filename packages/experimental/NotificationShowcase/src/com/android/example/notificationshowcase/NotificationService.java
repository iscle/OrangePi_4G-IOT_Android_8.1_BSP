/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.example.notificationshowcase;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;

import com.android.example.notificationshowcase.R;

import java.util.ArrayList;

public class NotificationService extends IntentService {

    private static final String TAG = "NotificationService";

    public static final String ACTION_CREATE = "create";
    public static final String ACTION_DESTROY = "destroy";
    public static final int NOTIFICATION_ID = 31338;
    private static final long FADE_TIME_MILLIS = 1000 * 60 * 5;

    public NotificationService() {
        super(TAG);
    }

    public NotificationService(String name) {
        super(name);
    }

    private static PendingIntent makeCancelAllIntent(Context context) {
        final Intent intent = new Intent(ACTION_DESTROY);
        intent.setComponent(new ComponentName(context, NotificationService.class));
        return PendingIntent.getService(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static Bitmap getBitmap(Context context, int resId) {
        int largeIconWidth = (int) context.getResources()
                .getDimension(R.dimen.notification_large_icon_width);
        int largeIconHeight = (int) context.getResources()
                .getDimension(R.dimen.notification_large_icon_height);
        Drawable d = context.getResources().getDrawable(resId);
        Bitmap b = Bitmap.createBitmap(largeIconWidth, largeIconHeight, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, largeIconWidth, largeIconHeight);
        d.draw(c);
        return b;
    }

    private static PendingIntent makeEmailIntent(Context context, String who) {
        final Intent intent = new Intent(android.content.Intent.ACTION_SENDTO,
                Uri.parse("mailto:" + who));
        return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT);
    }

    public static Notification makeSmsNotification(Context context, int update, int id, long when) {
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        String sender = context.getString(R.string.sms_sender);

        String personUri = null;
        if (sharedPref.getBoolean(SettingsActivity.KEY_SMS_PERSON, false)) {
            Cursor c = null;
            try {
                String[] projection = new String[] { ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.LOOKUP_KEY };
                String selections = ContactsContract.Contacts.DISPLAY_NAME + " = ?";
                String[] selectionArgs = { sender };
                final ContentResolver contentResolver = context.getContentResolver();
                c = contentResolver.query(ContactsContract.Contacts.CONTENT_URI,
                        projection, selections, selectionArgs, null);
                if (c != null && c.getCount() > 0) {
                    c.moveToFirst();
                    int lookupIdx = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY);
                    int idIdx = c.getColumnIndex(ContactsContract.Contacts._ID);
                    String lookupKey = c.getString(lookupIdx);
                    long contactId = c.getLong(idIdx);
                    Uri lookupUri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
                    personUri = lookupUri.toString();
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        if (update > 2) {
            when = System.currentTimeMillis();
        }
        final String priorityName = sharedPref.getString(SettingsActivity.KEY_SMS_PRIORITY, "0");
        final int priority = Integer.valueOf(priorityName);
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.bigText(context.getString(R.string.sms_message));
        PendingIntent ci = ToastService.getPendingIntent(context, R.string.sms_click);
        PendingIntent ai = UpdateService.getPendingIntent(context, update + 1, id, when);
        NotificationCompat.Builder bigText = new NotificationCompat.Builder(context)
                .setContentTitle(sender)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(ci)
                .setContentText(context.getString(R.string.sms_message))
                .setWhen(when)
                .setLargeIcon(getBitmap(context, R.drawable.bucket))
                .setPriority(priority)
                .addAction(R.drawable.ic_media_next, context.getString(R.string.sms_reply), ai)
                .setSmallIcon(R.drawable.stat_notify_talk_text)
                .setStyle(bigTextStyle)
                .setOnlyAlertOnce(sharedPref.getBoolean(SettingsActivity.KEY_SMS_ONCE, true));

        if (TextUtils.isEmpty(personUri)) {
            Log.w(TAG, "failed to find contact for Mike Cleron");
        } else {
            bigText.addPerson(personUri);
            Log.w(TAG, "Mike Cleron is " + personUri);
        }

        int defaults = 0;
        if(sharedPref.getBoolean(SettingsActivity.KEY_SMS_NOISY, true)) {
            String uri = sharedPref.getString(SettingsActivity.KEY_SMS_SOUND, null);
            if(uri == null) {
                defaults |= Notification.DEFAULT_SOUND;
            } else {
                bigText.setSound(Uri.parse(uri));
            }
        }
        if(sharedPref.getBoolean(SettingsActivity.KEY_SMS_BUZZY, false)) {
            defaults |= Notification.DEFAULT_VIBRATE;
        }
        bigText.setDefaults(defaults);

        return bigText.build();
    }

    public static Notification makeUploadNotification(Context context, int progress, long when) {
        PendingIntent pi = ToastService.getPendingIntent(context, R.string.upload_click);
        NotificationCompat.Builder uploadNotification = new NotificationCompat.Builder(context)
                .setContentTitle(context.getString(R.string.upload_title))
                .setContentText(context.getString(R.string.upload_text))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(pi)
                .setWhen(when)
                .setSmallIcon(R.drawable.ic_menu_upload)
                .setProgress(100, Math.min(progress, 100), false);
        return uploadNotification.build();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        NotificationManagerCompat noMa = NotificationManagerCompat.from(this);
        if (ACTION_DESTROY.equals(intent.getAction())) {
            noMa.cancelAll();
            return;
        }

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        ArrayList<Notification> mNotifications = new ArrayList<Notification>();

        if(sharedPref.getBoolean(SettingsActivity.KEY_SMS_ENABLE, true)) {
            final int id = mNotifications.size();
            mNotifications.add(makeSmsNotification(this, 2, id, System.currentTimeMillis()));
        }

        if(sharedPref.getBoolean(SettingsActivity.KEY_UPLOAD_ENABLE, false)) {
            final int id = mNotifications.size();
            final long uploadWhen = System.currentTimeMillis();
            mNotifications.add(makeUploadNotification(this, 10, uploadWhen));
            ProgressService.startProgressUpdater(this, id, uploadWhen, 0);
        }

        if(sharedPref.getBoolean(SettingsActivity.KEY_PHONE_ENABLE, false)) {
            final int id = mNotifications.size();
            final PendingIntent fullscreen = FullScreenActivity.getPendingIntent(this, id);
            PendingIntent ans = PhoneService.getPendingIntent(this, id,
                    PhoneService.ACTION_ANSWER);
            PendingIntent ign =
                    PhoneService.getPendingIntent(this, id, PhoneService.ACTION_IGNORE);
            NotificationCompat.Builder phoneCall = new NotificationCompat.Builder(this)
                    .setContentTitle(getString(R.string.call_title))
                    .setContentText(getString(R.string.call_text))
                    .setLargeIcon(getBitmap(this, R.drawable.matias_hed))
                    .setSmallIcon(R.drawable.stat_sys_phone_call)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setContentIntent(fullscreen)
                    .addAction(R.drawable.ic_dial_action_call, getString(R.string.call_answer), ans)
                    .addAction(R.drawable.ic_end_call, getString(R.string.call_ignore), ign)
                    .setOngoing(true);

            if(sharedPref.getBoolean(SettingsActivity.KEY_PHONE_FULLSCREEN, false)) {
                phoneCall.setFullScreenIntent(fullscreen, true);
            }

            if(sharedPref.getBoolean(SettingsActivity.KEY_PHONE_NOISY, false)) {
                phoneCall.setDefaults(Notification.DEFAULT_SOUND);
            }

            if (sharedPref.getBoolean(SettingsActivity.KEY_PHONE_PERSON, false)) {
                phoneCall.addPerson(Uri.fromParts("tel",
                        "1 (617) 555-1212", null)
                        .toString());
            }
            mNotifications.add(phoneCall.build());
        }

        if(sharedPref.getBoolean(SettingsActivity.KEY_TIMER_ENABLE, false)) {
            PendingIntent pi = ToastService.getPendingIntent(this, R.string.timer_click);
            mNotifications.add(new NotificationCompat.Builder(this)
                    .setContentTitle(getString(R.string.timer_title))
                    .setContentText(getString(R.string.timer_text))
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setContentIntent(pi)
                    .setSmallIcon(R.drawable.stat_notify_alarm)
                    .setUsesChronometer(true)
                            .build());
        }

        if(sharedPref.getBoolean(SettingsActivity.KEY_CALENDAR_ENABLE, false)) {
            mNotifications.add(new NotificationCompat.Builder(this)
                    .setContentTitle(getString(R.string.calendar_title))
                    .setContentText(getString(R.string.calendar_text))
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.stat_notify_calendar)
                    .setContentIntent(ToastService.getPendingIntent(this, R.string.calendar_click))
                    .setContentInfo("7PM")
                    .addAction(R.drawable.stat_notify_snooze, getString(R.string.calendar_10),
                            ToastService.getPendingIntent(this, R.string.calendar_10_click))
                    .addAction(R.drawable.stat_notify_snooze_longer, getString(R.string.calendar_60),
                            ToastService.getPendingIntent(this, R.string.calendar_60_click))
                            .addAction(R.drawable.stat_notify_email, "Email",
                                    makeEmailIntent(this,
                                            "gabec@example.com,mcleron@example.com,dsandler@example.com"))
                            .build());
        }

        if(sharedPref.getBoolean(SettingsActivity.KEY_PICTURE_ENABLE, false)) {
            BitmapDrawable d =
                    (BitmapDrawable) getResources().getDrawable(R.drawable.romainguy_rockaway);
            PendingIntent ci = ToastService.getPendingIntent(this, R.string.picture_click);
            PendingIntent ai = ToastService.getPendingIntent(this, R.string.picture_add_click);
            mNotifications.add(new NotificationCompat.BigPictureStyle(
                    new NotificationCompat.Builder(this)
                            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                            .setContentTitle(getString(R.string.picture_title))
                            .setContentText(getString(R.string.picture_text))
                            .setSmallIcon(R.drawable.ic_stat_gplus)
                            .setContentIntent(ci)
                            .setLargeIcon(getBitmap(this, R.drawable.romainguy_hed))
                            .addAction(R.drawable.add, getString(R.string.picture_add), ai)
                            .setSubText(getString(R.string.picture_sub_text)))
                    .bigPicture(d.getBitmap())
                    .build());
        }

        if(sharedPref.getBoolean(SettingsActivity.KEY_INBOX_ENABLE, false)) {
            PendingIntent pi = ToastService.getPendingIntent(this, R.string.email_click);
            mNotifications.add(new NotificationCompat.InboxStyle(
                    new NotificationCompat.Builder(this)
                            .setContentTitle(getString(R.string.email_title))
                            .setContentText(getString(R.string.email_text))
                            .setSubText(getString(R.string.email_sub_text))
                            .setCategory(NotificationCompat.CATEGORY_EMAIL)
                            .setContentIntent(pi)
                            .setSmallIcon(R.drawable.stat_notify_email))
                    .setSummaryText("+21 more")
                    .addLine(getString(R.string.email_a))
                    .addLine(getString(R.string.email_b))
                    .addLine(getString(R.string.email_c))
                    .build());
        }

        if(sharedPref.getBoolean(SettingsActivity.KEY_SOCIAL_ENABLE, false)) {
            PendingIntent pi = ToastService.getPendingIntent(this, R.string.social_click);
            mNotifications.add(new NotificationCompat.Builder(this)
                    .setContentTitle(getString(R.string.social_title))
                    .setContentText(getString(R.string.social_text))
                    .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                    .setContentIntent(pi)
                    .setSmallIcon(R.drawable.twitter_icon)
                    .setNumber(15)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build());
        }

        for (int i=0; i<mNotifications.size(); i++) {
            noMa.notify(NOTIFICATION_ID + i, mNotifications.get(i));
        }

        // always cancel any previous alarm
        PendingIntent pendingCancel = makeCancelAllIntent(this);
        AlarmManager alMa = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alMa.cancel(pendingCancel);
        if(sharedPref.getBoolean(SettingsActivity.KEY_GLOBAL_FADE, false)) {
            long t = SystemClock.elapsedRealtime() + FADE_TIME_MILLIS;
            alMa.set(AlarmManager.ELAPSED_REALTIME, t, pendingCancel);
        }
    }
}
