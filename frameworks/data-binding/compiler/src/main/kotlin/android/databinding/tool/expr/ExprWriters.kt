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

package android.databinding.tool.expr

import android.databinding.tool.reflection.Callable
import android.databinding.tool.solver.ExecutionPath
import android.databinding.tool.writer.KCode
import android.databinding.tool.writer.fieldName
import android.databinding.tool.writer.isForcedToLocalize
import android.databinding.tool.writer.isVariable
import android.databinding.tool.writer.kcode
import android.databinding.tool.writer.scopedName

fun Expr.shouldLocalizeInCallbacks() = canBeEvaluatedToAVariable() && !resolvedType.isVoid && (isDynamic || isForcedToLocalize())

fun CallbackExprModel.localizeGlobalVariables(vararg ignore: Expr): KCode = kcode("// localize variables for thread safety") {
    // puts all variables in this model to local values.
    mExprMap.values.filter { it.shouldLocalizeInCallbacks() && !ignore.contains(it) }.forEach {
        nl("// ${it.toString()}")
        nl("${it.resolvedType.toJavaCode()} ${it.scopedName()} = ${if (it.isVariable()) it.fieldName else it.defaultValue};")
    }
}

fun ExecutionPath.toCode(): KCode = kcode("") {
    val myExpr = expr
    if (myExpr != null && !isAlreadyEvaluated) {
        // variables are read up top
        val localize = myExpr.shouldLocalizeInCallbacks() && !myExpr.isVariable()
        // if this is not a method call (or method call via field access, don't do anything
        val eligible = localize || (myExpr is MethodCallExpr || (myExpr is FieldAccessExpr && myExpr.getter.type == Callable.Type.METHOD))
        if (eligible) {
            val assign = if (localize) {
                "${myExpr.scopedName()} = "
            } else {
                ""
            }
            if (myExpr is TernaryExpr) {
                // if i know the value, short circuit it
                if (knownValues.containsKey(myExpr.pred)) {
                    val chosen = if (knownValues[myExpr.pred]!!) myExpr.ifTrue else myExpr.ifFalse
                    // fast read me
                    nl("$assign${chosen.toCode().generate()};")
                } else {
                    // read me
                    nl("$assign${myExpr.toFullCode().generate()};")
                }
            } else {
                // read me
                nl("$assign${myExpr.toFullCode().generate()};")
            }
        }
    }
    children.forEach {
        nl(it.toCode())
    }
    // if i have branches, execute them
    val myTrue = trueBranch
    val myFalse = falseBranch
    if (myTrue != null) {
        val condition = with(myTrue.conditional) {
            if (shouldLocalizeInCallbacks()) {
                scopedName()
            } else {
                toFullCode().generate()
            }
        }
        block("if ($condition)") {
            nl(myTrue.path.toCode())
        }
        if (myFalse != null) {
            block("else") {
                nl(myFalse.path.toCode())
            }
        }
    }
}