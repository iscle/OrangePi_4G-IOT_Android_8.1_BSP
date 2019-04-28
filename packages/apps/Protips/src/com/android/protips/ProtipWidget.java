/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.protips;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mister Widget appears on your home screen to provide helpful tips. */
public class ProtipWidget extends AppWidgetProvider {
    public static final String ACTION_NEXT_TIP = "com.android.misterwidget.NEXT_TIP";
    public static final String ACTION_POKE = "com.android.misterwidget.HEE_HEE";

    public static final String EXTRA_TIMES = "times";

    public static final String PREFS_NAME = "Protips";
    public static final String PREFS_TIP_NUMBER = "widget_tip";
    public static final String PREFS_TIP_SET = "widget_tip_set";

    private static final Pattern sNewlineRegex = Pattern.compile(" *\\n *");
    private static final Pattern sDrawableRegex = Pattern.compile(" *@(drawable/[a-z0-9_]+) *");

    private static Handler mAsyncHandler;
    static {
        HandlerThread thr = new HandlerThread("ProtipWidget async");
        thr.start();
        mAsyncHandler = new Handler(thr.getLooper());
    }
    
    // initial appearance: eyes closed, no bubble
    private int mIconRes = R.drawable.droidman_open;
    private int mMessage = 0;
    private int mTipSet = 0;

    private AppWidgetManager mWidgetManager = null;
    private int[] mWidgetIds;
    private Context mContext;

    private CharSequence[] mTips;

    private void setup(Context context) {
        mContext = context;
        mWidgetManager = AppWidgetManager.getInstance(context);
        mWidgetIds = mWidgetManager.getAppWidgetIds(new ComponentName(context, ProtipWidget.class));

        SharedPreferences pref = context.getSharedPreferences(PREFS_NAME, 0);
        mMessage = pref.getInt(PREFS_TIP_NUMBER, 0);
        mTipSet = pref.getInt(PREFS_TIP_SET, 0);

        mTips = context.getResources().getTextArray(mTipSet == 1 ? R.array.tips2 : R.array.tips);

        if (mTips != null) {
            if (mMessage >= mTips.length) mMessage = 0;
        } else {
            mMessage = -1;
        }
    }

    public void goodmorning() {
        mMessage = -1;
        try {
            setIcon(R.drawable.droidman_down_closed);
            Thread.sleep(500);
            setIcon(R.drawable.droidman_down_open);
            Thread.sleep(200);
            setIcon(R.drawable.droidman_down_closed);
            Thread.sleep(100);
            setIcon(R.drawable.droidman_down_open);
            Thread.sleep(600);
        } catch (InterruptedException ex) {
        }
        mMessage = 0;
        mIconRes = R.drawable.droidman_open;
        refresh();
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final PendingResult result = goAsync();
        Runnable worker = new Runnable() {
            @Override
            public void run() {
                onReceiveAsync(context, intent);
                result.finish();
            }
        };
        mAsyncHandler.post(worker);
    }
    
    void onReceiveAsync(Context context, Intent intent) {
        setup(context);

        Resources res = mContext.getResources();
        mTips = res.getTextArray(mTipSet == 1 ? R.array.tips2 : R.array.tips);

        if (intent.getAction().equals(ACTION_NEXT_TIP)) {
            mMessage = getNextMessageIndex();
            SharedPreferences.Editor pref = context.getSharedPreferences(PREFS_NAME, 0).edit();
            pref.putInt(PREFS_TIP_NUMBER, mMessage);
            pref.apply();
            refresh();
        } else if (intent.getAction().equals(ACTION_POKE)) {
            blink(intent.getIntExtra(EXTRA_TIMES, 1));
        } else if (intent.getAction().equals(AppWidgetManager.ACTION_APPWIDGET_ENABLED)) {
            goodmorning();
        } else if (intent.getAction().equals("android.provider.Telephony.SECRET_CODE")) {
            Log.d("Protips", "ACHIEVEMENT UNLOCKED");
            mTipSet = 1 - mTipSet;
            mMessage = 0;

            SharedPreferences.Editor pref = context.getSharedPreferences(PREFS_NAME, 0).edit();
            pref.putInt(PREFS_TIP_NUMBER, mMessage);
            pref.putInt(PREFS_TIP_SET, mTipSet);
            pref.apply();

            mContext.startActivity(
                new Intent(Intent.ACTION_MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .addCategory(Intent.CATEGORY_HOME));
            
            final Intent bcast = new Intent(context, ProtipWidget.class);
            bcast.setAction(ACTION_POKE);
            bcast.putExtra(EXTRA_TIMES, 3);
            mContext.sendBroadcast(bcast);
        } else {
            mIconRes = R.drawable.droidman_open;
            refresh();
        }
    }

    private void refresh() {
        RemoteViews rv = buildUpdate(mContext);
        for (int i : mWidgetIds) {
            mWidgetManager.updateAppWidget(i, rv);
        }
    }

    private void setIcon(int resId) {
        mIconRes = resId;
        refresh();
    }

    private int getNextMessageIndex() {
        return (mMessage + 1) % mTips.length;
    }

    private void blink(int blinks) {
        // don't blink if no bubble showing or if goodmorning() is happening
        if (mMessage < 0) return;

        setIcon(R.drawable.droidman_closed);
        try {
            Thread.sleep(100);
            while (0<--blinks) {
                setIcon(R.drawable.droidman_open);
                Thread.sleep(200);
                setIcon(R.drawable.droidman_closed);
                Thread.sleep(100);
            }
        } catch (InterruptedException ex) { }
        setIcon(R.drawable.droidman_open);
    }

    public RemoteViews buildUpdate(Context context) {
        RemoteViews updateViews = new RemoteViews(
            context.getPackageName(), R.layout.widget);

        // Action for tap on bubble
        Intent bcast = new Intent(context, ProtipWidget.class);
        bcast.setAction(ACTION_NEXT_TIP);
        PendingIntent pending = PendingIntent.getBroadcast(
            context, 0, bcast, PendingIntent.FLAG_UPDATE_CURRENT);
        updateViews.setOnClickPendingIntent(R.id.tip_bubble, pending);

        // Action for tap on android
        bcast = new Intent(context, ProtipWidget.class);
        bcast.setAction(ACTION_POKE);
        bcast.putExtra(EXTRA_TIMES, 1);
        pending = PendingIntent.getBroadcast(
            context, 0, bcast, PendingIntent.FLAG_UPDATE_CURRENT);
        updateViews.setOnClickPendingIntent(R.id.bugdroid, pending);

        // Tip bubble text
        if (mMessage >= 0) {
            String[] parts = sNewlineRegex.split(mTips[mMessage], 2);
            String title = parts[0];
            String text = parts.length > 1 ? parts[1] : "";

            // Look for a callout graphic referenced in the text
            Matcher m = sDrawableRegex.matcher(text);
            if (m.find()) {
                String imageName = m.group(1);
                int resId = context.getResources().getIdentifier(

                    imageName, null, context.getPackageName());
                updateViews.setImageViewResource(R.id.tip_callout, resId);
                updateViews.setViewVisibility(R.id.tip_callout, View.VISIBLE);
                text = m.replaceFirst("");
            } else {
                updateViews.setImageViewResource(R.id.tip_callout, 0);
                updateViews.setViewVisibility(R.id.tip_callout, View.GONE);
            }

            updateViews.setTextViewText(R.id.tip_message, 
                text);
            updateViews.setTextViewText(R.id.tip_header,
                title);
            updateViews.setTextViewText(R.id.tip_footer, 
                context.getResources().getString(
                    R.string.pager_footer,
                    (1+mMessage), mTips.length));
            updateViews.setViewVisibility(R.id.tip_bubble, View.VISIBLE);
        } else {
            updateViews.setViewVisibility(R.id.tip_bubble, View.INVISIBLE);
        }

        updateViews.setImageViewResource(R.id.bugdroid, mIconRes);

        return updateViews;
    }
}
