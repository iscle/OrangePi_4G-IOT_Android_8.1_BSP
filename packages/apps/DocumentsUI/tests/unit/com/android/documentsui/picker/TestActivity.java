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

package com.android.documentsui.picker;

import android.annotation.RequiresPermission;
import android.content.Intent;
import android.util.Pair;

import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestEventListener;

import org.mockito.Mockito;

public abstract class TestActivity extends AbstractBase {

    public TestEventListener<Pair<Integer, Intent>> setResult;
    public TestEventListener<Pair<Intent, Integer>> startActivityForResult;

    public static TestActivity create(TestEnv env) {
        TestActivity activity = Mockito.mock(TestActivity.class, Mockito.CALLS_REAL_METHODS);
        activity.init(env);
        return activity;
    }

    @Override
    public void init(TestEnv env) {
        super.init(env);

        setResult = new TestEventListener<>();
        startActivityForResult = new TestEventListener<>();
    }

    @Override
    public void setResult(int resultCode, Intent intent, int notUsed) {
        setResult.accept(Pair.create(resultCode, intent));
    }

    @Override
    public final void startActivityForResult(@RequiresPermission Intent intent, int requestCode) {
        startActivityForResult.accept(Pair.create(intent, requestCode));
    }
}

// Trick Mockito into finding our Addons methods correctly. W/o this
// hack, Mockito thinks Addons methods are not implemented.
abstract class AbstractBase extends com.android.documentsui.TestActivity
        implements ActionHandler.Addons {}
