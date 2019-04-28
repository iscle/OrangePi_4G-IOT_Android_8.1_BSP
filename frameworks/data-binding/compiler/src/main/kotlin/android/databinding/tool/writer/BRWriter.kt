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

package android.databinding.tool.writer

import android.databinding.tool.util.StringUtils

class BRWriter(properties: Set<String>, val useFinal : Boolean) {
    val indexedProps = properties.sorted().withIndex()
    public fun write(pkg : String): String = "package $pkg;${StringUtils.LINE_SEPARATOR}$klass"
    val klass: String by lazy {
        kcode("") {
            val prefix = if (useFinal) "final " else "";
            block("public class BR") {
                tab("public static ${prefix}int _all = 0;")
                indexedProps.forEach {
                    tab ("public static ${prefix}int ${it.value} = ${it.index + 1};")
                }
            }
        }.generate()
    }
}
