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

package android.assist.testapp;

import android.app.Activity;
import android.assist.common.Utils;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.ScrollView;
import android.widget.TextView;

public class TextViewActivity extends Activity {
    static final String TAG = "TextViewActivity";

    private BroadcastReceiver mReceiver;
    private TextView mTextView;
    private ScrollView mScrollView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "TextViewActivity created");
        setContentView(R.layout.text_view);
        mScrollView = (ScrollView) findViewById(R.id.scroll_view);
        mTextView = (TextView) findViewById(R.id.text_view);
        mTextView.setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "TextViewActivity has resumed");

        mReceiver = new ScrollReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.SCROLL_TEXTVIEW_ACTION);
        filter.addAction(Utils.SCROLL_SCROLLVIEW_ACTION);
        registerReceiver(mReceiver, filter);

        final View layout = findViewById(android.R.id.content);
        ViewTreeObserver vto = layout.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                layout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                sendBroadcast(new Intent(Utils.APP_3P_HASRESUMED));
            }
        });
    }

    @Override
    public void onPause() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onPause();
    }

    class ScrollReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int scrollX, scrollY;
            scrollX = intent.getIntExtra(Utils.SCROLL_X_POSITION, 0);
            scrollY = intent.getIntExtra(Utils.SCROLL_Y_POSITION, 0);
            if (intent.getAction().equals(Utils.SCROLL_TEXTVIEW_ACTION)) {
                Log.i(TAG, "Scrolling textview to (" + scrollX + "," + scrollY + ")");
                if (scrollX < 0 || scrollY < 0) {
                    // Scroll to bottom as negative positions are not possible.
                    scrollX = mTextView.getWidth();
                    scrollY = mTextView.getLayout().getLineTop(mTextView.getLineCount())
                            - mTextView.getHeight();
                }
                TextViewActivity.this.mTextView.scrollTo(scrollX, scrollY);
            } else if (intent.getAction().equals(Utils.SCROLL_SCROLLVIEW_ACTION)) {
                Log.i(TAG, "Scrolling scrollview to (" + scrollX + "," + scrollY + ")");
                if (scrollX < 0 || scrollY < 0) {
                    // Scroll to bottom
                    TextViewActivity.this.mScrollView.fullScroll(View.FOCUS_DOWN);
                    TextViewActivity.this.mScrollView.fullScroll(View.FOCUS_RIGHT);
                } else {
                    TextViewActivity.this.mScrollView.scrollTo(scrollX, scrollY);
                }
            }
            Log.i(TAG, "the max height of this textview is: " + mTextView.getHeight());
            Log.i(TAG, "the max line count of this text view is: " + mTextView.getMaxLines());
        }
    }
}