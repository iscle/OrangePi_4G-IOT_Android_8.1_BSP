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

package android.widget.cts.appwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.cts.R;

import com.android.compatibility.common.util.PollingCheck;

import java.util.concurrent.CountDownLatch;

public final class MyAppWidgetProvider extends AppWidgetProvider {
    private static final long TIME_SLICE = 100;

    public static final String KEY_DISPLAYED_CHILD_INDEX =
            "MyAppWidgetProvider.displayedChildIndex";
    public static final String KEY_SHOW_NEXT = "MyAppWidgetProvider.showNext";
    public static final String KEY_SHOW_PREVIOUS = "MyAppWidgetProvider.showPrevious";
    public static final String KEY_SWITCH_TO_LIST = "MyAppWidgetProvider.switchToList";
    public static final String KEY_SCROLL_POSITION = "MyAppWidgetProvider.scrollPosition";
    public static final String KEY_SCROLL_OFFSET = "MyAppWidgetProvider.scrollOffset";

    // This latch will be notified when onEnabled is called on our provider.
    private static CountDownLatch sCountDownLatch;
    // Gating condition to be polled to proceed with setScrollPosition call.
    private static PollingCheck.PollingCheckCondition sSetScrollCondition;
    // Gating condition to be polled to proceed with setRelativeScrollPosition call.
    private static PollingCheck.PollingCheckCondition sSetRelativeScrollCondition;

    private int mDisplayedChildIndex;
    private boolean mShowNext;
    private boolean mShowPrevious;
    private boolean mSwitchToList;
    private int mScrollPosition;
    private int mScrollOffset;

    public static void configure(CountDownLatch countDownLatch,
            PollingCheck.PollingCheckCondition setScrollCondition,
            PollingCheck.PollingCheckCondition setRelativeScrollCondition) {
        sCountDownLatch = countDownLatch;
        sSetScrollCondition = setScrollCondition;
        sSetRelativeScrollCondition = setRelativeScrollCondition;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mDisplayedChildIndex = intent.getIntExtra(KEY_DISPLAYED_CHILD_INDEX, -1);
        mShowNext = intent.getBooleanExtra(KEY_SHOW_NEXT, false);
        mShowPrevious = intent.getBooleanExtra(KEY_SHOW_PREVIOUS, false);
        mSwitchToList = intent.getBooleanExtra(KEY_SWITCH_TO_LIST, false);
        mScrollPosition = intent.getIntExtra(KEY_SCROLL_POSITION, -1);
        mScrollOffset = intent.getIntExtra(KEY_SCROLL_OFFSET, 0);

        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final int appWidgetId = appWidgetIds[0];
        final RemoteViews widgetAdapterView = new RemoteViews(context.getPackageName(),
                R.layout.remoteviews_adapter);

        final Intent stackIntent = new Intent(context, MyAppWidgetService.class);
        stackIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        stackIntent.setData(Uri.parse(stackIntent.toUri(Intent.URI_INTENT_SCHEME)));

        widgetAdapterView.setRemoteAdapter(R.id.remoteViews_stack, stackIntent);
        widgetAdapterView.setEmptyView(R.id.remoteViews_stack, R.id.remoteViews_empty);

        if (mDisplayedChildIndex >= 0) {
            widgetAdapterView.setDisplayedChild(R.id.remoteViews_stack, mDisplayedChildIndex);
        }
        if (mShowNext) {
            widgetAdapterView.showNext(R.id.remoteViews_stack);
        }
        if (mShowPrevious) {
            widgetAdapterView.showPrevious(R.id.remoteViews_stack);
        }

        // Here we setup the a pending intent template. Individuals items of a collection
        // cannot setup their own pending intents, instead, the collection as a whole can
        // setup a pending intent template, and the individual items can set a fillInIntent
        // to create unique before on an item to item basis.
        Intent viewIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("ctstest://RemoteView/testWidget"));
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        widgetAdapterView.setPendingIntentTemplate(R.id.remoteViews_stack, pendingIntent);

        if (mSwitchToList) {
            final Intent listIntent = new Intent(context, MyAppWidgetService.class);
            listIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            listIntent.setData(Uri.parse(stackIntent.toUri(Intent.URI_INTENT_SCHEME)));

            widgetAdapterView.setRemoteAdapter(R.id.remoteViews_list, listIntent);

            widgetAdapterView.setViewVisibility(R.id.remoteViews_stack, View.GONE);
            widgetAdapterView.setViewVisibility(R.id.remoteViews_list, View.VISIBLE);
        }

        final Handler handler = new Handler(Looper.myLooper());
        if (mScrollPosition >= 0) {
            // We need to schedule the call to setScrollPosition as a separate event that runs
            // after the underlying ListView has been laid out on the screen. Otherwise calling
            // that API on a ListView with 0x0 dimension has no effect - the list content is only
            // populated via the adapter when ListView has "real" bounds.
            final Runnable setScrollRunnable = new Runnable() {
                public void run() {
                    if (sSetScrollCondition.canProceed()) {
                        // Gating condition has been satisfied. Call setScrollPosition and
                        // ask the widget manager to update our widget
                        widgetAdapterView.setScrollPosition(R.id.remoteViews_list, mScrollPosition);
                        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, widgetAdapterView);
                    } else {
                        // Keep on "waiting" until the gating condition is satisfied
                        handler.postDelayed(this, TIME_SLICE);
                    }
                }
            };
            handler.postDelayed(setScrollRunnable, TIME_SLICE);
        }

        if (mScrollOffset != 0) {
            // We need to schedule the call to setRelativeScrollPosition as a separate event that
            // runs after the underlying ListView has been laid out on the screen. Otherwise calling
            // that API on a ListView with 0x0 dimension has no effect - the list content is only
            // populated via the adapter when ListView has "real" bounds.
            final Runnable setRelativeScrollRunnable = new Runnable() {
                public void run() {
                    if (sSetRelativeScrollCondition.canProceed()) {
                        // Gating condition has been satisfied. Call setRelativeScrollPosition and
                        // ask the widget manager to update our widget
                        widgetAdapterView.setRelativeScrollPosition(
                                R.id.remoteViews_list, mScrollOffset);
                        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, widgetAdapterView);
                    } else {
                        // Keep on "waiting" until the gating condition is satisfied
                        handler.postDelayed(this, TIME_SLICE);
                    }
                }
            };
            handler.postDelayed(setRelativeScrollRunnable, TIME_SLICE);
        }

        appWidgetManager.updateAppWidget(appWidgetId, widgetAdapterView);

        sCountDownLatch.countDown();
    }

    @Override
    public void onEnabled(Context context) {
        sCountDownLatch.countDown();
    }
}
