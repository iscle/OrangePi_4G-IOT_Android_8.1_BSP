/**
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.accessibilityservice.cts;

import android.os.Bundle;
import android.view.View;

import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.accessibilityservice.cts.R;

/**
 * Activity for testing the accessibility APIs for querying of
 * the screen content. These APIs allow exploring the screen and
 * requesting an action to be performed on a given view from an
 * AccessibilityService.
 */
public class AccessibilityWindowQueryActivity extends AccessibilityTestActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.query_window_test);

        findViewById(R.id.button5).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                /* do nothing */
            }
        });
        findViewById(R.id.button5).setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                return true;
            }
        });

        findViewById(R.id.button5).setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityAction(R.id.foo_custom_action, "Foo"));
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == R.id.foo_custom_action) {
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }
}
