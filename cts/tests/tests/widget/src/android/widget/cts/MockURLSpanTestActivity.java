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

package android.widget.cts;

import android.app.Activity;
import android.os.Bundle;

/**
 * A Mock application for {@link URLSpan} test.
 */
public class MockURLSpanTestActivity extends Activity {
    public static final String KEY_PARAM = "MockURLSpanTestActivity.param";

    private String mParam;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mParam = getIntent().getStringExtra(KEY_PARAM);
        setContentView(R.layout.urlspan_layout);
    }

    public String getParam() {
        return mParam;
    }
}
