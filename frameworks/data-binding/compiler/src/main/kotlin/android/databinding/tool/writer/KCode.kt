/*
 * Copyright (C) 2015 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.databinding.tool.writer

import android.databinding.tool.util.StringUtils
import java.util.BitSet

class KCode (private val s : String? = null){

    private var sameLine = false

    private val lineSeparator = StringUtils.LINE_SEPARATOR

    class Appendix(val glue : String, val code : KCode)

    private val nodes = arrayListOf<Any>()

    companion object {
        private val cachedIndentations = BitSet()
        private val indentCache = arrayListOf<String>()
        fun indent(n: Int): String {
            if (cachedIndentations.get(n)) {
                return indentCache[n]
            }
            val s = (0..n-1).fold(""){prev, next -> "$prev    "}
            cachedIndentations.set(n, true )
            while (indentCache.size <= n) {
                indentCache.add("");
            }
            indentCache.set(n, s)
            return s
        }
    }

    fun isNull(kcode : KCode?) = kcode == null || (kcode.nodes.isEmpty() && (kcode.s == null || kcode.s.trim() == ""))

    public fun tab(vararg codes : KCode?) : KCode {
        codes.forEach { tab(it) }
        return this
    }

    public fun tab(codes : Collection<KCode?> ) : KCode {
        codes.forEach { tab(it) }
        return this
    }

    fun tab(s : String?, init : (KCode.() -> Unit)? = null) : KCode {
        val c = KCode(s)
        if (init != null) {
            c.init()
        }
        return tab(c)
    }

    fun tab(c : KCode?) : KCode {
        if (c == null || isNull(c)) {
            return this
        }
        nodes.add(c)
        return this
    }

    infix fun nl(c : KCode?) : KCode {
        if (c == null || isNull(c)) {
            return this
        }
        nodes.add(c)
        c.sameLine = true
        return this
    }

    fun nl(s : String?, init : (KCode.() -> Unit)? = null) : KCode {
        val c = KCode(s)
        if (init != null) {
            c.init()
        }
        return nl(c)
    }

    fun block(s : String, init : (KCode.() -> Unit)? = null) : KCode {
        val c = KCode()
        if (init != null) {
            c.init()
        }
        return nl(s) {
            app(" {")
            tab(c)
            nl("}")
        }
    }

    fun app(glue : String = "", c : KCode?) : KCode {
        if (isNull(c)) {
            return this
        }
        nodes.add(Appendix(glue, c!!))
        return this
    }

    infix fun app(s : String) : KCode {
        val c = KCode(s)
        return app("", c)
    }

    fun app(glue : String = "", s : String?, init : (KCode.() -> Unit)? = null) : KCode {
        val c = KCode(s)
        if (init != null) {
            c.init()
        }
        return app(glue, c)
    }


    fun toS(n : Int, sb : StringBuilder) {
        if (s != null) {
            sb.append(s)
        }
        val newlineFirstNode = s != null || (nodes.isNotEmpty() && nodes.first() is Appendix)
        var addedChild = false
        nodes.forEach { when(it) {
            is Appendix -> {
                sb.append(it.glue)
                it.code.toS(n, sb)
            }
            is KCode -> {
                val childTab = n + (if(it.sameLine) 0 else 1)
                if (addedChild || newlineFirstNode) {
                    sb.append(lineSeparator)
                }
                if (!isNull(it)) { // avoid spaces for empty lines
                    if (it.s != null && it.s.trim() != "") {
                        sb.append("${indent(childTab)}")
                    }
                    it.toS(childTab, sb)
                }
                addedChild = true
            }
        } }

    }

    fun generate() : String {
        val sb = StringBuilder()
        toS(0, sb)
        return sb.toString()
    }
}

fun kcode(s : String?, init : (KCode.() -> Unit)? = null) : KCode {
    val c = KCode(s)
    if (init != null) {
        c.init()
    }
    return c
}