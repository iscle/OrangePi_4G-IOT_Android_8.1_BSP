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
package com.android.tv.testing.uihelper;

import android.graphics.Point;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiObject2;

/**
 * Static utility methods for {@link UiObject2}s.
 */
public class UiObject2Utils {

    public static boolean hasSiblingInDirection(UiObject2 theUiObject, Direction direction) {
        Point myCenter = theUiObject.getVisibleCenter();
        for (UiObject2 sibling : theUiObject.getParent().getChildren()) {
            Point siblingCenter = sibling.getVisibleCenter();
            switch (direction) {
                case UP:
                    if (myCenter.y > siblingCenter.y) {
                        return true;
                    }
                    break;
                case DOWN:
                    if (myCenter.y < siblingCenter.y) {
                        return true;
                    }
                    break;
                case LEFT:
                    if (myCenter.x > siblingCenter.x) {
                        return true;
                    }
                    break;
                case RIGHT:
                    if (myCenter.x < siblingCenter.x) {
                        return true;
                    }
                    break;
                default:
                    throw new IllegalArgumentException(direction.toString());
            }
        }
        return false;
    }

    private UiObject2Utils() {
    }
}
