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

package android.widget.cts.util;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.List;

public class TestUtilsMatchers {
    /**
     * Returns a matcher that matches lists of int values that are in ascending order.
     */
    public static Matcher<List<Integer>> inAscendingOrder() {
        return new TypeSafeMatcher<List<Integer>>() {
            private String mFailedDescription;

            @Override
            public void describeTo(Description description) {
                description.appendText(mFailedDescription);
            }

            @Override
            protected boolean matchesSafely(List<Integer> item) {
                int itemCount = item.size();

                if (itemCount >= 2) {
                    for (int i = 0; i < itemCount - 1; i++) {
                        int curr = item.get(i);
                        int next = item.get(i + 1);

                        if (curr > next) {
                            mFailedDescription = "Values should increase between #" + i +
                                    ":" + curr + " and #" + (i + 1) + ":" + next;
                            return false;
                        }
                    }
                }

                return true;
            }
        };
    }
}
