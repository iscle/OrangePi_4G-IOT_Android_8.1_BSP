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

package com.android.simpleperf;

import android.test.AndroidTestCase;
import android.util.Log;

// This file is just to ensure that we have some code in the apk.
public class EmptyTest extends AndroidTestCase {
  private static final String TAG = "EmptyTest";

  public void testEmpty() {
    Log.i(TAG, "testEmpty()");
  }
}
