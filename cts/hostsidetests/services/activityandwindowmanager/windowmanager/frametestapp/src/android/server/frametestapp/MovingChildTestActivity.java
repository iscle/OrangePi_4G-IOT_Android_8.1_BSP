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

package android.server.FrameTestApp;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.Window;
import android.view.Gravity;
import android.view.View;
import android.widget.Space;
import android.widget.Button;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.FrameLayout;

// This activity will parent a Child to the main window, and then move
// the main window around. We can use this to verify the Child
// is properly updated.
public class MovingChildTestActivity extends Activity {
    Space mView;
    int mX = 0;
    int mY = 0;

    final Runnable moveWindow = new Runnable() {
            @Override
            public void run() {
                final Window w = getWindow();
                final WindowManager.LayoutParams attribs = w.getAttributes();
                attribs.privateFlags = WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
                attribs.x = mX % 1000;
                attribs.y = mY % 1000;
                w.setAttributes(attribs);
                mX += 5;
                mY += 5;
                mView.postDelayed(this, 50);
            }
    };

    final Runnable makeChild = new Runnable() {
            @Override
            public void run() {
                Button b = new Button(MovingChildTestActivity.this);
                WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.TYPE_APPLICATION_PANEL);
                p.privateFlags = WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
                p.x = 0;
                p.y = 0;
                p.token = mView.getWindowToken();
                p.setTitle("ChildWindow");

                ((WindowManager)getSystemService(Context.WINDOW_SERVICE)).addView(b, p);

                mView.postDelayed(moveWindow, 50);
            }
    };

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final LayoutParams p = new LayoutParams(100, 100);
        final Window w = getWindow();
        w.setLayout(100, 100);
        mView = new Space(this);

        setContentView(mView, p);
        mView.post(makeChild);
    }
}
