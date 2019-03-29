/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.inputmethodservice.cts.common;

/**
 * Utility class to build Android's component name.
 */
final class ComponentNameUtils {

    // This is utility class, can't instantiate.
    private ComponentNameUtils() {}

    /**
     * Build Android component name from {@code packageName} and {@code className}.
     * @param packageName package name of a component.
     * @param className class name of a component.
     * @return a component of {@code packageName/className} that can be used to specify component,
     *         for example, for {@code android.content.Intent}.
     */
    static String buildComponentName(final String packageName, final String className) {
        return packageName + "/" + (className.startsWith(packageName)
                ? className.substring(packageName.length()) : className);
    }
}
