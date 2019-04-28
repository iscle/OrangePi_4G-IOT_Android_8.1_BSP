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

package android.databinding.tool.writer

import android.databinding.tool.CallbackWrapper

fun CallbackWrapper.allArgsWithTypes() =
        "int ${CallbackWrapper.SOURCE_ID} ${method.parameterTypes.withIndex().map { ", ${it.value.toJavaCode()} ${CallbackWrapper.ARG_PREFIX}${it.index}" }.joinToString("")}"

fun CallbackWrapper.argsWithTypes() =
        method.parameterTypes.withIndex().map { "${it.value.toJavaCode()} ${CallbackWrapper.ARG_PREFIX}${it.index}" }.joinToString(", ")

fun CallbackWrapper.args() =
        method.parameterTypes.withIndex().map { "${CallbackWrapper.ARG_PREFIX}${it.index}" }.joinToString(", ")

fun CallbackWrapper.allArgs() =
        "mSourceId ${method.parameterTypes.withIndex().map { ", ${CallbackWrapper.ARG_PREFIX}${it.index}" }.joinToString("")}"

/**
 * For any listener type we see, we create a class that can wrap around it. This wrapper has an
 * interface which is implemented by the ViewDataBinding.
 */
public class CallbackWrapperWriter(val wrapper: CallbackWrapper) {

    public fun write() = kcode("") {
        with(wrapper) {
            @Suppress("RemoveCurlyBracesFromTemplate")
            app("package ${`package`};")
            val extendsImplements = if (klass.isInterface) {
                "implements"
            } else {
                "extends"
            }
            block("public final class $className $extendsImplements ${klass.canonicalName}") {
                // declare the actual listener interface
                nl("final $listenerInterfaceName mListener;")
                nl("final int mSourceId;")
                block("public $className($listenerInterfaceName listener, int sourceId)") {
                    nl("mListener = listener;")
                    nl("mSourceId = sourceId;")
                }
                nl("")
                nl("@Override")
                block("public ${method.returnType.canonicalName} ${method.name}(${wrapper.argsWithTypes()})") {
                    val evaluate = "mListener.$listenerMethodName(${wrapper.allArgs()});"
                    if (method.returnType.isVoid) {
                        nl("$evaluate")
                    } else {
                        nl("return $evaluate")
                    }
                }
                nl("")
                block("public interface $listenerInterfaceName") {
                    nl("${method.returnType} $listenerMethodName(${wrapper.allArgsWithTypes()});")
                }
            }
        }
    }.generate()
}