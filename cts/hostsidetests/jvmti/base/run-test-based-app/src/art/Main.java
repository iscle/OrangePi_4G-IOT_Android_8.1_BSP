/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package art;

/**
 * This is class is only provided to make development in an IDE easier. The art.Main version
 * out of the ART run-tests will be used when building.
 */
public class Main {
    // General functionality shared between tests.
    public static native void setTag(Object o, long tag);

    public static native long getTag(Object o);
}
