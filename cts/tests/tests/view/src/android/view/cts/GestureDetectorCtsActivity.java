/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.view.cts;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;

public class GestureDetectorCtsActivity extends Activity {
    private GestureDetector mGestureDetector;
    private GestureDetector.SimpleOnGestureListener mOnGestureListener;
    private Handler mHandler;
    private View mView;
    private Button mTop;
    private Button mButton;
    private ViewGroup mViewGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mOnGestureListener = mock(GestureDetector.SimpleOnGestureListener.class);
        doReturn(true).when(mOnGestureListener).onDown(any(MotionEvent.class));
        doReturn(true).when(mOnGestureListener).onFling(any(MotionEvent.class),
                any(MotionEvent.class), anyFloat(), anyFloat());
        doReturn(true).when(mOnGestureListener).onScroll(any(MotionEvent.class),
                any(MotionEvent.class), anyFloat(), anyFloat());
        doReturn(true).when(mOnGestureListener).onSingleTapUp(any(MotionEvent.class));

        mHandler = new Handler();

        mGestureDetector = new GestureDetector(this, mOnGestureListener, mHandler);
        mGestureDetector.setOnDoubleTapListener(mOnGestureListener);
        mGestureDetector.setContextClickListener(mOnGestureListener);
        mView = new View(this);
        mButton = new Button(this);
        mTop = new Button(this);
        mView.setOnTouchListener(new MockOnTouchListener());

        mViewGroup = new ViewGroup(this) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
            }
        };
        mViewGroup.addView(mView);
        mViewGroup.addView(mTop);
        mViewGroup.addView(mButton);
        mViewGroup.setOnTouchListener(new MockOnTouchListener());
        setContentView(mViewGroup);

    }

    public View getView() {
        return mView;
    }

    public GestureDetector getGestureDetector() {
        return mGestureDetector;
    }

    public GestureDetector.SimpleOnGestureListener getListener() {
        return mOnGestureListener;
    }

    class MockOnTouchListener implements OnTouchListener, OnGenericMotionListener {

        public boolean onTouch(View v, MotionEvent event) {
            mGestureDetector.onTouchEvent(event);
            return true;
        }

        public boolean onGenericMotion(View v, MotionEvent event) {
            mGestureDetector.onGenericMotionEvent(event);
            return true;
        }
    }

}
