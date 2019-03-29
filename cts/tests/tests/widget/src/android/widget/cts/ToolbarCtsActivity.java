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

package android.widget.cts;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.widget.Toolbar;

/**
 * A minimal application for {@link Toolbar} test.
 */
public class ToolbarCtsActivity extends Activity {
    private Toolbar mMainToolbar;

    public int mCreateMenuCount;
    public int mPrepareMenuCount;
    public int mKeyShortcutCount;

    /**
     * Called with the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.toolbar_layout);

        mMainToolbar = (Toolbar) findViewById(R.id.toolbar_main);
        setActionBar(mMainToolbar);
    }

    public Toolbar getMainToolbar() {
        return mMainToolbar;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        ++mCreateMenuCount;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        ++mPrepareMenuCount;
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        ++mKeyShortcutCount;
        return super.onKeyShortcut(keyCode, event);
    }

    public void resetCounts() {
        mCreateMenuCount = mPrepareMenuCount = mKeyShortcutCount = 0;
    }
}

