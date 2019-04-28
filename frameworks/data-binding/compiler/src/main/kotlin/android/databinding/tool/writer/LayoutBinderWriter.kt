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

import android.databinding.tool.Binding
import android.databinding.tool.BindingTarget
import android.databinding.tool.CallbackWrapper
import android.databinding.tool.InverseBinding
import android.databinding.tool.LayoutBinder
import android.databinding.tool.expr.Expr
import android.databinding.tool.expr.ExprModel
import android.databinding.tool.expr.FieldAccessExpr
import android.databinding.tool.expr.IdentifierExpr
import android.databinding.tool.expr.LambdaExpr
import android.databinding.tool.expr.ListenerExpr
import android.databinding.tool.expr.ResourceExpr
import android.databinding.tool.expr.TernaryExpr
import android.databinding.tool.expr.localizeGlobalVariables
import android.databinding.tool.expr.shouldLocalizeInCallbacks
import android.databinding.tool.expr.toCode
import android.databinding.tool.ext.androidId
import android.databinding.tool.ext.br
import android.databinding.tool.ext.joinToCamelCaseAsVar
import android.databinding.tool.ext.lazyProp
import android.databinding.tool.ext.versionedLazy
import android.databinding.tool.processing.ErrorMessages
import android.databinding.tool.reflection.ModelAnalyzer
import android.databinding.tool.reflection.ModelClass
import android.databinding.tool.util.L
import android.databinding.tool.util.Preconditions
import java.util.ArrayList
import java.util.Arrays
import java.util.BitSet
import java.util.HashMap

fun String.stripNonJava() = this.split("[^a-zA-Z0-9]".toRegex()).map{ it.trim() }.joinToCamelCaseAsVar()

enum class Scope {
    GLOBAL,
    FIELD,
    METHOD,
    FLAG,
    EXECUTE_PENDING_METHOD,
    CONSTRUCTOR_PARAM,
    CALLBACK;
    companion object {
        var currentScope = GLOBAL;
        private val scopeStack = arrayListOf<Scope>()
        fun enter(scope : Scope) {
            scopeStack.add(currentScope)
            currentScope = scope
        }

        fun exit() {
            currentScope = scopeStack.removeAt(scopeStack.size - 1)
        }

        fun reset() {
            scopeStack.clear()
            currentScope = GLOBAL
        }
    }
}

class ExprModelExt {
    val usedFieldNames = hashMapOf<Scope, MutableSet<String>>();
    init {
        Scope.values().forEach { usedFieldNames[it] = hashSetOf<String>() }
    }

    internal val forceLocalize = hashSetOf<Expr>()

    val localizedFlags = arrayListOf<FlagSet>()

    fun localizeFlag(set : FlagSet, name:String) : FlagSet {
        localizedFlags.add(set)
        val result = getUniqueName(name, Scope.FLAG, false)
        set.localName = result
        return set
    }

    fun getUniqueName(base : String, scope : Scope, isPublic : kotlin.Boolean) : String {
        var candidateBase = base
        if (!isPublic && candidateBase.length > 20) {
            candidateBase = candidateBase.substring(0, 20);
        }
        var candidate = candidateBase
        if (scope == Scope.CALLBACK || scope == Scope.EXECUTE_PENDING_METHOD) {
            candidate = candidate.decapitalize()
        }
        var i = 0
        while (usedFieldNames[scope]!!.contains(candidate)) {
            i ++
            candidate = candidateBase + i
        }
        usedFieldNames[scope]!!.add(candidate)
        return candidate
    }
}

fun ModelClass.defaultValue() = ModelAnalyzer.getInstance().getDefaultValue(toJavaCode())
fun ExprModel.getUniqueFieldName(base : String, isPublic : kotlin.Boolean) : String = ext.getUniqueName(base, Scope.FIELD, isPublic)
fun ExprModel.getUniqueMethodName(base : String, isPublic : kotlin.Boolean) : String = ext.getUniqueName(base, Scope.METHOD, isPublic)
fun ExprModel.getConstructorParamName(base : String) : String = ext.getUniqueName(base, Scope.CONSTRUCTOR_PARAM, false)
fun ExprModel.localizeFlag(set : FlagSet, base : String) : FlagSet = ext.localizeFlag(set, base)

val Expr.needsLocalField by lazyProp { expr : Expr ->
    expr.canBeEvaluatedToAVariable() && !(expr.isVariable() && !expr.isUsed) && (expr.isDynamic || expr is ResourceExpr)
}

fun Expr.isForcedToLocalize() = model.ext.forceLocalize.contains(this)

// not necessarily unique. Uniqueness is solved per scope
val BindingTarget.readableName by lazyProp { target: BindingTarget ->
    if (target.id == null) {
        "boundView" + indexFromTag(target.tag)
    } else {
        target.id.androidId().stripNonJava()
    }
}

fun BindingTarget.superConversion(variable : String) : String {
    if (resolvedType != null && resolvedType.extendsViewStub()) {
        return "new android.databinding.ViewStubProxy((android.view.ViewStub) $variable)"
    } else {
        return "($interfaceClass) $variable"
    }
}

val BindingTarget.fieldName : String by lazyProp { target : BindingTarget ->
    val name : String
    val isPublic : kotlin.Boolean
    if (target.id == null) {
        name = "m${target.readableName}"
        isPublic = false
    } else {
        name = target.readableName
        isPublic = true
    }
    target.model.getUniqueFieldName(name, isPublic)
}

val BindingTarget.androidId by lazyProp { target : BindingTarget ->
    if (target.id.startsWith("@android:id/")) {
        "android.R.id.${target.id.androidId()}"
    } else {
        "R.id.${target.id.androidId()}"
    }
}

val BindingTarget.interfaceClass by lazyProp { target : BindingTarget ->
    if (target.resolvedType != null && target.resolvedType.extendsViewStub()) {
        "android.databinding.ViewStubProxy"
    } else {
        target.interfaceType
    }
}

val BindingTarget.constructorParamName by lazyProp { target : BindingTarget ->
    target.model.getConstructorParamName(target.readableName)
}

// not necessarily unique. Uniqueness is decided per scope
val Expr.readableName by lazyProp { expr : Expr ->
    val stripped = expr.uniqueKey.stripNonJava()
    L.d("readableUniqueName for [%s] %s is %s", System.identityHashCode(expr), expr.uniqueKey, stripped)
    stripped
}

val Expr.fieldName by lazyProp { expr : Expr ->
    expr.model.getUniqueFieldName("m${expr.readableName.capitalize()}", false)
}

val InverseBinding.fieldName by lazyProp { inverseBinding : InverseBinding ->
    val targetName = inverseBinding.target.fieldName;
    val eventName = inverseBinding.eventAttribute.stripNonJava()
    inverseBinding.model.getUniqueFieldName("$targetName$eventName", false)
}

val Expr.listenerClassName by lazyProp { expr : Expr ->
    expr.model.getUniqueFieldName("${expr.resolvedType.simpleName}Impl", false)
}

val Expr.oldValueName by lazyProp { expr : Expr ->
    expr.model.getUniqueFieldName("mOld${expr.readableName.capitalize()}", false)
}

fun Expr.scopedName() : String = when(Scope.currentScope) {
    Scope.CALLBACK -> callbackLocalName
    else -> executePendingLocalName
}

val Expr.callbackLocalName by lazyProp { expr : Expr ->
    if(expr.shouldLocalizeInCallbacks()) "${expr.model.ext.getUniqueName(expr.readableName, Scope.CALLBACK, false)}"
    else expr.toCode().generate()
}

val Expr.executePendingLocalName by lazyProp { expr : Expr ->
    if(expr.isDynamic || expr.needsLocalField) "${expr.model.ext.getUniqueName(expr.readableName, Scope.EXECUTE_PENDING_METHOD, false)}"
    else expr.toCode().generate()
}

val Expr.setterName by lazyProp { expr : Expr ->
    expr.model.getUniqueMethodName("set${expr.readableName.capitalize()}", true)
}

val Expr.onChangeName by lazyProp { expr : Expr ->
    expr.model.getUniqueMethodName("onChange${expr.readableName.capitalize()}", false)
}

val Expr.getterName by lazyProp { expr : Expr ->
    expr.model.getUniqueMethodName("get${expr.readableName.capitalize()}", true)
}

fun Expr.isVariable() = this is IdentifierExpr && this.isDynamic

val Expr.dirtyFlagSet by lazyProp { expr : Expr ->
    FlagSet(expr.invalidFlags, expr.model.flagBucketCount)
}

val Expr.invalidateFlagSet by lazyProp { expr : Expr ->
    FlagSet(expr.id)
}

val Expr.shouldReadFlagSet by versionedLazy { expr : Expr ->
    FlagSet(expr.shouldReadFlags, expr.model.flagBucketCount)
}

val Expr.shouldReadWithConditionalsFlagSet by versionedLazy { expr : Expr ->
    FlagSet(expr.shouldReadFlagsWithConditionals, expr.model.flagBucketCount)
}

val Expr.conditionalFlags by lazyProp { expr : Expr ->
    arrayListOf(FlagSet(expr.getRequirementFlagIndex(false)),
            FlagSet(expr.getRequirementFlagIndex(true)))
}

fun Binding.toAssignmentCode() : String {
    val fieldName: String
    if (this.target.viewClass.
            equals(this.target.interfaceType)) {
        fieldName = "this.${this.target.fieldName}"
    } else {
        fieldName = "((${this.target.viewClass}) this.${this.target.fieldName})"
    }
    return this.toJavaCode(fieldName, "this.mBindingComponent")
}

val LayoutBinder.requiredComponent by lazyProp { layoutBinder: LayoutBinder ->
    val requiredFromBindings = layoutBinder.
            bindingTargets.
            flatMap { it.bindings }.
            firstOrNull { it.bindingAdapterInstanceClass != null }?.bindingAdapterInstanceClass
    val requiredFromInverse = layoutBinder.
            bindingTargets.
            flatMap { it.inverseBindings }.
            firstOrNull { it.bindingAdapterInstanceClass != null }?.bindingAdapterInstanceClass
    requiredFromBindings ?: requiredFromInverse
}

fun Expr.getRequirementFlagSet(expected : Boolean) : FlagSet = conditionalFlags[if(expected) 1 else 0]

fun FlagSet.notEmpty(cb : (suffix : String, value : Long) -> Unit) {
    buckets.withIndex().forEach {
        if (it.value != 0L) {
            cb(getWordSuffix(it.index), buckets[it.index])
        }
    }
}

fun getWordSuffix(wordIndex : Int) : String {
    return if(wordIndex == 0) "" else "_$wordIndex"
}

fun FlagSet.localValue(bucketIndex : Int) =
        if (localName == null) binaryCode(bucketIndex)
        else "$localName${getWordSuffix(bucketIndex)}"

fun FlagSet.binaryCode(bucketIndex : Int) = longToBinary(buckets[bucketIndex])


fun longToBinary(l : Long) = "0x${java.lang.Long.toHexString(l)}L"

fun <T> FlagSet.mapOr(other : FlagSet, cb : (suffix : String, index : Int) -> T) : List<T> {
    val min = Math.min(buckets.size, other.buckets.size)
    val result = arrayListOf<T>()
    for (i in 0..(min - 1)) {
        // if these two can match by any chance, call the callback
        if (intersect(other, i)) {
            result.add(cb(getWordSuffix(i), i))
        }
    }
    return result
}

fun indexFromTag(tag : String) : kotlin.Int {
    val startIndex : kotlin.Int
    if (tag.startsWith("binding_")) {
        startIndex = "binding_".length;
    } else {
        startIndex = tag.lastIndexOf('_') + 1
    }
    return Integer.parseInt(tag.substring(startIndex))
}

class LayoutBinderWriter(val layoutBinder : LayoutBinder) {
    val model = layoutBinder.model
    val indices = HashMap<BindingTarget, kotlin.Int>()
    val mDirtyFlags by lazy {
        val fs = FlagSet(BitSet(), model.flagBucketCount);
        Arrays.fill(fs.buckets, -1)
        fs.isDynamic = true
        model.localizeFlag(fs, "mDirtyFlags")
        fs
    }

    val className = layoutBinder.implementationName

    val baseClassName = "${layoutBinder.className}"

    val includedBinders by lazy {
        layoutBinder.bindingTargets.filter { it.isBinder }
    }

    val variables by lazy {
        model.exprMap.values.filterIsInstance(IdentifierExpr::class.java).filter { it.isVariable() }
    }

    val usedVariables by lazy {
        variables.filter {it.isUsed || it.isIsUsedInCallback }
    }

    val callbacks by lazy {
        model.exprMap.values.filterIsInstance(LambdaExpr::class.java)
    }

    public fun write(minSdk : kotlin.Int) : String  {
        Scope.reset()
        layoutBinder.resolveWhichExpressionsAreUsed()
        calculateIndices();
        return kcode("package ${layoutBinder.`package`};") {
            nl("import ${layoutBinder.modulePackage}.R;")
            nl("import ${layoutBinder.modulePackage}.BR;")
            nl("import android.view.View;")
            val classDeclaration : String
            if (layoutBinder.hasVariations()) {
                classDeclaration = "$className extends $baseClassName"
            } else {
                classDeclaration = "$className extends android.databinding.ViewDataBinding"
            }
            block("public class $classDeclaration ${buildImplements()}") {
                nl(declareIncludeViews())
                nl(declareViews())
                nl(declareVariables())
                nl(declareBoundValues())
                nl(declareListeners())
                try {
                    Scope.enter(Scope.GLOBAL)
                    nl(declareInverseBindingImpls());
                } finally {
                    Scope.exit()
                }
                nl(declareConstructor(minSdk))
                nl(declareInvalidateAll())
                nl(declareHasPendingBindings())
                nl(declareSetVariable())
                nl(variableSettersAndGetters())
                nl(onFieldChange())
                try {
                    Scope.enter(Scope.GLOBAL)
                    nl(executePendingBindings())
                } finally {
                    Scope.exit()
                }

                nl(declareListenerImpls())
                try {
                    Scope.enter(Scope.CALLBACK)
                    nl(declareCallbackImplementations())
                } finally {
                    Scope.exit()
                }

                nl(declareDirtyFlags())
                if (!layoutBinder.hasVariations()) {
                    nl(declareFactories())
                }
                nl(flagMapping())
                nl("//end")
            }
        }.generate()
    }
    fun buildImplements() : String {
        return if (callbacks.isEmpty()) {
            ""
        } else {
            "implements " + callbacks.map { it.callbackWrapper.cannonicalListenerName }.distinct().joinToString(", ")
        }
    }

    fun calculateIndices() : Unit {
        val taggedViews = layoutBinder.bindingTargets.filter{
            it.isUsed && it.tag != null && !it.isBinder
        }
        taggedViews.forEach {
            indices.put(it, indexFromTag(it.tag))
        }
        val indexStart = maxIndex() + 1
        layoutBinder.bindingTargets.filter{
            it.isUsed && !taggedViews.contains(it)
        }.withIndex().forEach {
            indices.put(it.value, it.index + indexStart)
        }
    }
    fun declareIncludeViews() = kcode("") {
        nl("private static final android.databinding.ViewDataBinding.IncludedLayouts sIncludes;")
        nl("private static final android.util.SparseIntArray sViewsWithIds;")
        nl("static {") {
            val hasBinders = layoutBinder.bindingTargets.firstOrNull{ it.isUsed && it.isBinder } != null
            if (!hasBinders) {
                tab("sIncludes = null;")
            } else {
                val numBindings = layoutBinder.bindingTargets.filter{ it.isUsed }.count()
                tab("sIncludes = new android.databinding.ViewDataBinding.IncludedLayouts($numBindings);")
                val includeMap = HashMap<BindingTarget, ArrayList<BindingTarget>>()
                layoutBinder.bindingTargets.filter{ it.isUsed && it.isBinder }.forEach {
                    val includeTag = it.tag;
                    val parent = layoutBinder.bindingTargets.firstOrNull {
                        it.isUsed && !it.isBinder && includeTag.equals(it.tag)
                    } ?: throw IllegalStateException("Could not find parent of include file")
                    var list = includeMap[parent]
                    if (list == null) {
                        list = ArrayList<BindingTarget>()
                        includeMap.put(parent, list)
                    }
                    list.add(it)
                }

                includeMap.keys.forEach {
                    val index = indices[it]
                    tab("sIncludes.setIncludes($index, ") {
                        tab ("new String[] {${
                        includeMap[it]!!.map {
                            "\"${it.includedLayout}\""
                        }.joinToString(", ")
                        }},")
                        tab("new int[] {${
                        includeMap[it]!!.map {
                            "${indices[it]}"
                        }.joinToString(", ")
                        }},")
                        tab("new int[] {${
                        includeMap[it]!!.map {
                            "R.layout.${it.includedLayout}"
                        }.joinToString(", ")
                        }});")
                    }
                }
            }
            val viewsWithIds = layoutBinder.bindingTargets.filter {
                it.isUsed && !it.isBinder && (!it.supportsTag() || (it.id != null && it.tag == null))
            }
            if (viewsWithIds.isEmpty()) {
                tab("sViewsWithIds = null;")
            } else {
                tab("sViewsWithIds = new android.util.SparseIntArray();")
                viewsWithIds.forEach {
                    tab("sViewsWithIds.put(${it.androidId}, ${indices[it]});")
                }
            }
        }
        nl("}")
    }

    fun maxIndex() : kotlin.Int {
        val maxIndex = indices.values.max()
        if (maxIndex == null) {
            return -1
        } else {
            return maxIndex
        }
    }

    fun declareConstructor(minSdk : kotlin.Int) = kcode("") {
        val bindingCount = maxIndex() + 1
        val parameterType : String
        val superParam : String
        if (layoutBinder.isMerge) {
            parameterType = "View[]"
            superParam = "root[0]"
        } else {
            parameterType = "View"
            superParam = "root"
        }
        val rootTagsSupported = minSdk >= 14
        if (layoutBinder.hasVariations()) {
            nl("")
            nl("public $className(android.databinding.DataBindingComponent bindingComponent, $parameterType root) {") {
                tab("this(bindingComponent, $superParam, mapBindings(bindingComponent, root, $bindingCount, sIncludes, sViewsWithIds));")
            }
            nl("}")
            nl("private $className(android.databinding.DataBindingComponent bindingComponent, $parameterType root, Object[] bindings) {") {
                tab("super(bindingComponent, $superParam, ${model.observables.size}") {
                    layoutBinder.sortedTargets.filter { it.id != null }.forEach {
                        tab(", ${fieldConversion(it)}")
                    }
                    tab(");")
                }
            }
        } else {
            nl("public $baseClassName(android.databinding.DataBindingComponent bindingComponent, $parameterType root) {") {
                tab("super(bindingComponent, $superParam, ${model.observables.size});")
                tab("final Object[] bindings = mapBindings(bindingComponent, root, $bindingCount, sIncludes, sViewsWithIds);")
            }
        }
        if (layoutBinder.requiredComponent != null) {
            tab("ensureBindingComponentIsNotNull(${layoutBinder.requiredComponent}.class);")
        }
        val taggedViews = layoutBinder.sortedTargets.filter{it.isUsed }
        taggedViews.forEach {
            if (!layoutBinder.hasVariations() || it.id == null) {
                tab("this.${it.fieldName} = ${fieldConversion(it)};")
            }
            if (!it.isBinder) {
                if (it.resolvedType != null && it.resolvedType.extendsViewStub()) {
                    tab("this.${it.fieldName}.setContainingBinding(this);")
                }
                if (it.supportsTag() && it.tag != null &&
                        (rootTagsSupported || it.tag.startsWith("binding_"))) {
                    val originalTag = it.originalTag;
                    var tagValue = "null"
                    if (originalTag != null && !originalTag.startsWith("@{")) {
                        tagValue = "\"$originalTag\""
                        if (originalTag.startsWith("@")) {
                            var packageName = layoutBinder.modulePackage
                            if (originalTag.startsWith("@android:")) {
                                packageName = "android"
                            }
                            val slashIndex = originalTag.indexOf('/')
                            val resourceId = originalTag.substring(slashIndex + 1)
                            tagValue = "root.getResources().getString($packageName.R.string.$resourceId)"
                        }
                    }
                    tab("this.${it.fieldName}.setTag($tagValue);")
                } else if (it.tag != null && !it.tag.startsWith("binding_") &&
                    it.originalTag != null) {
                    L.e(ErrorMessages.ROOT_TAG_NOT_SUPPORTED, it.originalTag)
                }
            }
        }
        tab("setRootTag(root);")
        tab(declareCallbackInstances())
        tab("invalidateAll();");
        nl("}")
    }

    fun declareCallbackInstances() = kcode("// listeners") {
        callbacks.groupBy { it.callbackWrapper.minApi }
                .forEach {
                    if (it.key > 1) {
                        block("if(getBuildSdkInt() < ${it.key})") {
                            it.value.forEach { lambda ->
                                nl("${lambda.fieldName} = null;")
                            }
                        }
                        block("else") {
                            it.value.forEach { lambda ->
                                nl("${lambda.fieldName} = ${lambda.generateConstructor()};")
                            }
                        }
                    } else {
                        it.value.forEach { lambda ->
                            nl("${lambda.fieldName} = ${lambda.generateConstructor()};")
                        }
                    }
                }
    }

    fun declareCallbackImplementations() = kcode("// callback impls") {
        callbacks.groupBy { it.callbackWrapper }.forEach {
            val wrapper = it.key
            val lambdas = it.value
            val shouldReturn = !wrapper.method.returnType.isVoid
            if (shouldReturn) {
                lambdas.forEach {
                    it.callbackExprModel.ext.forceLocalize.add(it.expr)
                }
            }
            block("public final ${wrapper.method.returnType.canonicalName} ${wrapper.listenerMethodName}(${wrapper.allArgsWithTypes()})") {
                Preconditions.check(lambdas.size > 0, "bindings list should not be empty")
                if (lambdas.size == 1) {
                    val lambda = lambdas[0]
                    nl(lambda.callbackExprModel.localizeGlobalVariables(lambda))
                    nl(lambda.executionPath.toCode())
                    if (shouldReturn) {
                        nl("return ${lambda.expr.scopedName()};")
                    }
                } else {
                    block("switch(${CallbackWrapper.SOURCE_ID})") {
                        lambdas.forEach { lambda ->
                            block("case ${lambda.callbackId}:") {
                                nl(lambda.callbackExprModel.localizeGlobalVariables(lambda))
                                nl(lambda.executionPath.toCode())
                                if (shouldReturn) {
                                    nl("return ${lambda.expr.scopedName()};")
                                } else {
                                    nl("break;")
                                }
                            }
                        }
                        if (shouldReturn) {
                            block("default:") {
                                nl("return ${wrapper.method.returnType.defaultValue()};")
                            }
                        }
                    }
                }
            }
        }
    }

    fun fieldConversion(target : BindingTarget) : String {
        if (!target.isUsed) {
            return "null"
        } else {
            val index = indices[target] ?: throw IllegalStateException("Unknown binding target")
            val variableName = "bindings[$index]"
            return target.superConversion(variableName)
        }
    }

    fun declareInvalidateAll() = kcode("") {
        nl("@Override")
        block("public void invalidateAll()") {
            val fs = FlagSet(layoutBinder.model.invalidateAnyBitSet,
                    layoutBinder.model.flagBucketCount);
            block("synchronized(this)") {
                for (i in (0..(mDirtyFlags.buckets.size - 1))) {
                    tab("${mDirtyFlags.localValue(i)} = ${fs.localValue(i)};")
                }
            }
            includedBinders.filter{it.isUsed }.forEach { binder ->
                nl("${binder.fieldName}.invalidateAll();")
            }
            nl("requestRebind();");
        }
    }

    fun declareHasPendingBindings()  = kcode("") {
        nl("@Override")
        nl("public boolean hasPendingBindings() {") {
            if (mDirtyFlags.buckets.size > 0) {
                tab("synchronized(this) {") {
                    val flagCheck = 0.rangeTo(mDirtyFlags.buckets.size - 1).map {
                            "${mDirtyFlags.localValue(it)} != 0"
                    }.joinToString(" || ")
                    tab("if ($flagCheck) {") {
                        tab("return true;")
                    }
                    tab("}")
                }
                tab("}")
            }
            includedBinders.filter{it.isUsed }.forEach { binder ->
                tab("if (${binder.fieldName}.hasPendingBindings()) {") {
                    tab("return true;")
                }
                tab("}")
            }
            tab("return false;")
        }
        nl("}")
    }

    fun declareSetVariable() = kcode("") {
        nl("public boolean setVariable(int variableId, Object variable) {") {
            tab("switch(variableId) {") {
                usedVariables.forEach {
                    tab ("case ${it.name.br()} :") {
                        tab("${it.setterName}((${it.resolvedType.toJavaCode()}) variable);")
                        tab("return true;")
                    }
                }
                val declaredOnly = variables.filter { !it.isUsed && !it.isIsUsedInCallback && it.isDeclared };
                declaredOnly.forEachIndexed { i, identifierExpr ->
                    tab ("case ${identifierExpr.name.br()} :") {
                        if (i == declaredOnly.size - 1) {
                            tab("return true;")
                        }
                    }
                }
            }
            tab("}")
            tab("return false;")
        }
        nl("}")
    }

    fun variableSettersAndGetters() = kcode("") {
        variables.filterNot{ usedVariables.contains(it) }.forEach {
            nl("public void ${it.setterName}(${it.resolvedType.toJavaCode()} ${it.readableName}) {") {
                tab("// not used, ignore")
            }
            nl("}")
            nl("")
            nl("public ${it.resolvedType.toJavaCode()} ${it.getterName}() {") {
                tab("return ${it.defaultValue};")
            }
            nl("}")
        }
        usedVariables.forEach {
            if (it.userDefinedType != null) {
                block("public void ${it.setterName}(${it.resolvedType.toJavaCode()} ${it.readableName})") {
                    if (it.isObservable) {
                        nl("updateRegistration(${it.id}, ${it.readableName});");
                    }
                    nl("this.${it.fieldName} = ${it.readableName};")
                    // set dirty flags!
                    val flagSet = it.invalidateFlagSet
                    block("synchronized(this)") {
                        mDirtyFlags.mapOr(flagSet) { suffix, index ->
                            nl("${mDirtyFlags.localName}$suffix |= ${flagSet.localValue(index)};")
                        }
                    }
                    // TODO: Remove this condition after releasing version 1.1 of SDK
                    if (ModelAnalyzer.getInstance().findClass("android.databinding.ViewDataBinding", null).isObservable) {
                        nl("notifyPropertyChanged(${it.name.br()});")
                    }
                    nl("super.requestRebind();")
                }
                nl("")
                block("public ${it.resolvedType.toJavaCode()} ${it.getterName}()") {
                    nl("return ${it.fieldName};")
                }
            }
        }
    }

    fun onFieldChange() = kcode("") {
        nl("@Override")
        nl("protected boolean onFieldChange(int localFieldId, Object object, int fieldId) {") {
            tab("switch (localFieldId) {") {
                model.observables.forEach {
                    tab("case ${it.id} :") {
                        tab("return ${it.onChangeName}((${it.resolvedType.toJavaCode()}) object, fieldId);")
                    }
                }
            }
            tab("}")
            tab("return false;")
        }
        nl("}")
        nl("")

        model.observables.forEach {
            block("private boolean ${it.onChangeName}(${it.resolvedType.toJavaCode()} ${it.readableName}, int fieldId)") {
                block("switch (fieldId)", {
                    val accessedFields: List<FieldAccessExpr> = it.parents.filterIsInstance(FieldAccessExpr::class.java)
                    accessedFields.filter { it.isUsed && it.hasBindableAnnotations() }
                            .groupBy { it.brName }
                            .forEach {
                                // If two expressions look different but resolve to the same method,
                                // we are not yet able to merge them. This is why we merge their
                                // flags below.
                                block("case ${it.key}:") {
                                    block("synchronized(this)") {
                                        val flagSet = it.value.foldRight(FlagSet()) { l, r -> l.invalidateFlagSet.or(r) }

                                        mDirtyFlags.mapOr(flagSet) { suffix, index ->
                                            tab("${mDirtyFlags.localValue(index)} |= ${flagSet.localValue(index)};")
                                        }
                                    }
                                    nl("return true;")
                                }

                            }
                    block("case ${"".br()}:") {
                        val flagSet = it.invalidateFlagSet
                        block("synchronized(this)") {
                            mDirtyFlags.mapOr(flagSet) { suffix, index ->
                                tab("${mDirtyFlags.localName}$suffix |= ${flagSet.localValue(index)};")
                            }
                        }
                        nl("return true;")
                    }
                })
                nl("return false;")
            }
            nl("")
        }
    }

    fun declareViews() = kcode("// views") {
        val oneLayout = !layoutBinder.hasVariations();
        layoutBinder.sortedTargets.filter {it.isUsed && (oneLayout || it.id == null)}.forEach {
            val access : String
            if (oneLayout && it.id != null) {
                access = "public"
            } else {
                access = "private"
            }
            nl("$access final ${it.interfaceClass} ${it.fieldName};")
        }
    }

    fun declareVariables() = kcode("// variables") {
        usedVariables.forEach {
            nl("private ${it.resolvedType.toJavaCode()} ${it.fieldName};")
        }
        callbacks.forEach {
            val wrapper = it.callbackWrapper
            nl("private final ${wrapper.klass.canonicalName} ${it.fieldName}").app(";")
        }
    }

    fun declareBoundValues() = kcode("// values") {
        layoutBinder.sortedTargets.filter { it.isUsed }
                .flatMap { it.bindings }
                .filter { it.requiresOldValue() }
                .flatMap{ it.componentExpressions.toList() }
                .groupBy { it }
                .forEach {
                    val expr = it.key
                    nl("private ${expr.resolvedType.toJavaCode()} ${expr.oldValueName};")
                }
    }

    fun declareListeners() = kcode("// listeners") {
        model.exprMap.values.filter {
            it is ListenerExpr
        }.groupBy { it }.forEach {
            val expr = it.key as ListenerExpr
            nl("private ${expr.listenerClassName} ${expr.fieldName};")
        }
    }

    fun declareInverseBindingImpls() = kcode("// Inverse Binding Event Handlers") {
        layoutBinder.sortedTargets.filter { it.isUsed }.forEach { target ->
            target.inverseBindings.forEach { inverseBinding ->
                val className : String
                val param : String
                if (inverseBinding.isOnBinder) {
                    className = "android.databinding.ViewDataBinding.PropertyChangedInverseListener"
                    param = "BR.${inverseBinding.eventAttribute}"
                } else {
                    className = "android.databinding.InverseBindingListener"
                    param = ""
                }
                block("private $className ${inverseBinding.fieldName} = new $className($param)") {
                    nl("@Override")
                    block("public void onChange()") {
                        if (inverseBinding.inverseExpr != null) {
                            val valueExpr = inverseBinding.variableExpr
                            val getterCall = inverseBinding.getterCall
                            nl("// Inverse of ${inverseBinding.expr}")
                            nl("//         is ${inverseBinding.inverseExpr}")
                            nl("${valueExpr.resolvedType.toJavaCode()} ${valueExpr.name} = ${getterCall.toJava("mBindingComponent", target.fieldName)};")
                            nl(inverseBinding.callbackExprModel.localizeGlobalVariables(valueExpr))
                            nl(inverseBinding.executionPath.toCode())
                        } else {
                            block("synchronized(this)") {
                                val flagSet = inverseBinding.chainedExpressions.fold(FlagSet(), { initial, expr ->
                                    initial.or(FlagSet(expr.id))
                                })
                                mDirtyFlags.mapOr(flagSet) { suffix, index ->
                                    tab("${mDirtyFlags.localValue(index)} |= ${flagSet.binaryCode(index)};")
                                }
                            }
                            nl("requestRebind();")
                        }
                    }
                }.app(";")
            }
        }
    }
    fun declareDirtyFlags() = kcode("// dirty flag") {
        model.ext.localizedFlags.forEach { flag ->
            flag.notEmpty { suffix, value ->
                nl("private")
                app(" ", if(flag.isDynamic) null else "static final");
                app(" ", " ${flag.type} ${flag.localName}$suffix = ${longToBinary(value)};")
            }
        }
    }

    fun flagMapping() = kcode("/* flag mapping") {
        if (model.flagMapping != null) {
            val mapping = model.flagMapping
            for (i in mapping.indices) {
                tab("flag $i (${longToBinary(1L + i)}): ${model.findFlagExpression(i)}")
            }
        }
        nl("flag mapping end*/")
    }

    fun executePendingBindings() = kcode("") {
        nl("@Override")
        block("protected void executeBindings()") {
            val tmpDirtyFlags = FlagSet(mDirtyFlags.buckets)
            tmpDirtyFlags.localName = "dirtyFlags";
            for (i in (0..mDirtyFlags.buckets.size - 1)) {
                nl("${tmpDirtyFlags.type} ${tmpDirtyFlags.localValue(i)} = 0;")
            }
            block("synchronized(this)") {
                for (i in (0..mDirtyFlags.buckets.size - 1)) {
                    nl("${tmpDirtyFlags.localValue(i)} = ${mDirtyFlags.localValue(i)};")
                    nl("${mDirtyFlags.localValue(i)} = 0;")
                }
            }
            model.pendingExpressions.filter { it.needsLocalField }.forEach {
                nl("${it.resolvedType.toJavaCode()} ${it.executePendingLocalName} = ${if (it.isVariable()) it.fieldName else it.defaultValue};")
            }
            L.d("writing executePendingBindings for %s", className)
            do {
                val batch = ExprModel.filterShouldRead(model.pendingExpressions)
                val justRead = arrayListOf<Expr>()
                L.d("batch: %s", batch)
                while (!batch.none()) {
                    val readNow = batch.filter { it.shouldReadNow(justRead) }
                    if (readNow.isEmpty()) {
                        throw IllegalStateException("do not know what I can read. bailing out ${batch.joinToString("\n")}")
                    }
                    L.d("new read now. batch size: %d, readNow size: %d", batch.size, readNow.size)
                    nl(readWithDependants(readNow, justRead, batch, tmpDirtyFlags))
                    batch.removeAll(justRead)
                }
                nl("// batch finished")
            } while (model.markBitsRead())
            // verify everything is read.
            val batch = ExprModel.filterShouldRead(model.pendingExpressions)
            if (batch.isNotEmpty()) {
                L.e("could not generate code for %s. This might be caused by circular dependencies."
                        + "Please report on b.android.com. %d %s %s", layoutBinder.layoutname,
                        batch.size, batch[0], batch[0].toCode().generate())
            }
            //
            layoutBinder.sortedTargets.filter { it.isUsed }
                    .flatMap { it.bindings }
                    .groupBy {
                        "${tmpDirtyFlags.mapOr(it.expr.dirtyFlagSet) { suffix, index ->
                            "(${tmpDirtyFlags.localValue(index)} & ${it.expr.dirtyFlagSet.localValue(index)}) != 0"
                        }.joinToString(" || ") }"
                    }.forEach {
                block("if (${it.key})") {
                    it.value.groupBy { Math.max(1, it.minApi) }.forEach {
                        val setterValues = kcode("") {
                            it.value.forEach { binding ->
                                nl(binding.toAssignmentCode()).app(";")
                            }
                        }
                        nl("// api target ${it.key}")
                        if (it.key > 1) {
                            block("if(getBuildSdkInt() >= ${it.key})") {
                                nl(setterValues)
                            }
                        } else {
                            nl(setterValues)
                        }
                    }
                }
            }


            layoutBinder.sortedTargets.filter { it.isUsed }
                    .flatMap { it.bindings }
                    .filter { it.requiresOldValue() }
                    .groupBy {"${tmpDirtyFlags.mapOr(it.expr.dirtyFlagSet) { suffix, index ->
                        "(${tmpDirtyFlags.localValue(index)} & ${it.expr.dirtyFlagSet.localValue(index)}) != 0"
                    }.joinToString(" || ")
                    }"}.forEach {
                block("if (${it.key})") {
                    it.value.groupBy { it.expr }.map { it.value.first() }.forEach {
                        it.componentExpressions.forEach { expr ->
                            nl("this.${expr.oldValueName} = ${expr.toCode().generate()};")
                        }
                    }
                }
            }
            includedBinders.filter{it.isUsed }.forEach { binder ->
                nl("${binder.fieldName}.executePendingBindings();")
            }
            layoutBinder.sortedTargets.filter{
                it.isUsed && it.resolvedType != null && it.resolvedType.extendsViewStub()
            }.forEach {
                block("if (${it.fieldName}.getBinding() != null)") {
                    nl("${it.fieldName}.getBinding().executePendingBindings();")
                }
            }
        }
    }

    fun readWithDependants(expressionList: List<Expr>, justRead: MutableList<Expr>,
            batch: MutableList<Expr>, tmpDirtyFlags: FlagSet,
            inheritedFlags: FlagSet? = null) : KCode = kcode("") {
        expressionList.groupBy { it.shouldReadFlagSet }.forEach {
            val flagSet = it.key
            val needsIfWrapper = inheritedFlags == null || !flagSet.bitsEqual(inheritedFlags)
            val expressions = it.value
            val ifClause = "if (${tmpDirtyFlags.mapOr(flagSet){ suffix, index ->
                "(${tmpDirtyFlags.localValue(index)} & ${flagSet.localValue(index)}) != 0"
            }.joinToString(" || ")
            })"
            val readCode = kcode("") {
                val dependants = ArrayList<Expr>()
                expressions.groupBy { condition(it) }.forEach {
                    val condition = it.key
                    val assignedValues = it.value.filter { it.needsLocalField && !it.isVariable() }
                    if (!assignedValues.isEmpty()) {
                        val assignment = kcode("") {
                            assignedValues.forEach { expr: Expr ->
                                tab("// read ${expr}")
                                tab("${expr.executePendingLocalName}").app(" = ", expr.toFullCode()).app(";")
                            }
                        }
                        if (condition != null) {
                            tab("if ($condition) {") {
                                app("", assignment)
                            }
                            tab ("}")
                        } else {
                            app("", assignment)
                        }
                        it.value.filter { it.isObservable }.forEach { expr: Expr ->
                            tab("updateRegistration(${expr.id}, ${expr.executePendingLocalName});")
                        }
                    }

                    it.value.forEach { expr: Expr ->
                        justRead.add(expr)
                        L.d("%s / readWithDependants %s", className, expr);
                        L.d("flag set:%s . inherited flags: %s. need another if: %s", flagSet, inheritedFlags, needsIfWrapper);

                        // if I am the condition for an expression, set its flag
                        expr.dependants.filter {
                            !it.isConditional && it.dependant is TernaryExpr &&
                                    (it.dependant as TernaryExpr).pred == expr
                        }.map { it.dependant }.groupBy {
                            // group by when those ternaries will be evaluated (e.g. don't set conditional flags for no reason)
                            val ternaryBitSet = it.shouldReadFlagsWithConditionals
                            val isBehindTernary = ternaryBitSet.nextSetBit(model.invalidateAnyFlagIndex) == -1
                            if (!isBehindTernary) {
                                val ternaryFlags = it.shouldReadWithConditionalsFlagSet
                                "if(${tmpDirtyFlags.mapOr(ternaryFlags){ suffix, index ->
                                    "(${tmpDirtyFlags.localValue(index)} & ${ternaryFlags.localValue(index)}) != 0"
                                }.joinToString(" || ")}) {"
                            } else {
                                // TODO if it is behind a ternary, we should set it when its predicate is elevated
                                // Normally, this would mean that there is another code path to re-read our current expression.
                                // Unfortunately, this may not be true due to the coverage detection in `expr#markAsReadIfDone`, this may never happen.
                                // for v1.0, we'll go with always setting it and suffering an unnecessary calculation for this edge case.
                                // we can solve this by listening to elevation events from the model.
                                ""
                            }
                        }.forEach {
                            val hasAnotherIf = it.key != ""
                            if (hasAnotherIf) {
                                tab(it.key) {
                                    tab("if (${expr.executePendingLocalName}) {") {
                                        it.value.forEach {
                                            val set = it.getRequirementFlagSet(true)
                                            mDirtyFlags.mapOr(set) { suffix, index ->
                                                tab("${tmpDirtyFlags.localValue(index)} |= ${set.localValue(index)};")
                                            }
                                        }
                                    }
                                    tab("} else {") {
                                        it.value.forEach {
                                            val set = it.getRequirementFlagSet(false)
                                            mDirtyFlags.mapOr(set) { suffix, index ->
                                                tab("${tmpDirtyFlags.localValue(index)} |= ${set.localValue(index)};")
                                            }
                                        }
                                    }.tab("}")
                                }.app("}")
                            } else {
                                tab("if (${expr.executePendingLocalName}) {") {
                                    it.value.forEach {
                                        val set = it.getRequirementFlagSet(true)
                                        mDirtyFlags.mapOr(set) { suffix, index ->
                                            tab("${tmpDirtyFlags.localValue(index)} |= ${set.localValue(index)};")
                                        }
                                    }
                                }
                                tab("} else {") {
                                    it.value.forEach {
                                        val set = it.getRequirementFlagSet(false)
                                        mDirtyFlags.mapOr(set) { suffix, index ->
                                            tab("${tmpDirtyFlags.localValue(index)} |= ${set.localValue(index)};")
                                        }
                                    }
                                } app("}")
                            }
                        }
                        val chosen = expr.dependants.filter {
                            val dependant = it.dependant
                            batch.contains(dependant) &&
                                    dependant.shouldReadFlagSet.andNot(flagSet).isEmpty &&
                                    dependant.shouldReadNow(justRead)
                        }
                        if (chosen.isNotEmpty()) {
                            dependants.addAll(chosen.map { it.dependant })
                        }
                    }
                }
                if (dependants.isNotEmpty()) {
                    val nextInheritedFlags = if (needsIfWrapper) flagSet else inheritedFlags
                    nl(readWithDependants(dependants, justRead, batch, tmpDirtyFlags, nextInheritedFlags))
                }
            }

            if (needsIfWrapper) {
                block(ifClause) {
                    nl(readCode)
                }
            } else {
                nl(readCode)
            }
        }
    }

    fun condition(expr : Expr) : String? {
        if (expr.canBeEvaluatedToAVariable() && !expr.isVariable()) {
            // create an if case for all dependencies that might be null
            val nullables = expr.dependencies.filter {
                it.isMandatory && it.other.resolvedType.isNullable
            }.map { it.other }
            if (!expr.isEqualityCheck && nullables.isNotEmpty()) {
                return "${nullables.map { "${it.executePendingLocalName} != null" }.joinToString(" && ")}"
            } else {
                return null
            }
        } else {
            return null
        }
    }

    fun declareListenerImpls() = kcode("// Listener Stub Implementations") {
        model.exprMap.values.filter {
            it.isUsed && it is ListenerExpr
        }.groupBy { it }.forEach {
            val expr = it.key as ListenerExpr
            val listenerType = expr.resolvedType;
            val extendsImplements : String
            if (listenerType.isInterface) {
                extendsImplements = "implements"
            } else {
                extendsImplements = "extends"
            }
            nl("public static class ${expr.listenerClassName} $extendsImplements ${listenerType.canonicalName}{") {
                if (expr.target.isDynamic) {
                    tab("private ${expr.target.resolvedType.toJavaCode()} value;")
                    tab("public ${expr.listenerClassName} setValue(${expr.target.resolvedType.toJavaCode()} value) {") {
                        tab("this.value = value;")
                        tab("return value == null ? null : this;")
                    }
                    tab("}")
                }
                val listenerMethod = expr.method
                val parameterTypes = listenerMethod.parameterTypes
                val returnType = listenerMethod.getReturnType(parameterTypes.toList())
                tab("@Override")
                tab("public $returnType ${listenerMethod.name}(${
                    parameterTypes.withIndex().map {
                        "${it.value.toJavaCode()} arg${it.index}"
                    }.joinToString(", ")
                }) {") {
                    val obj : String
                    if (expr.target.isDynamic) {
                        obj = "this.value"
                    } else {
                        obj = expr.target.toCode().generate();
                    }
                    val returnStr : String
                    if (!returnType.isVoid) {
                        returnStr = "return "
                    } else {
                        returnStr = ""
                    }
                    val args = parameterTypes.withIndex().map {
                        "arg${it.index}"
                    }.joinToString(", ")
                    tab("$returnStr$obj.${expr.name}($args);")
                }
                tab("}")
            }
            nl("}")
        }
    }

    fun declareFactories() = kcode("") {
        block("public static $baseClassName inflate(android.view.LayoutInflater inflater, android.view.ViewGroup root, boolean attachToRoot)") {
            nl("return inflate(inflater, root, attachToRoot, android.databinding.DataBindingUtil.getDefaultComponent());")
        }
        block("public static $baseClassName inflate(android.view.LayoutInflater inflater, android.view.ViewGroup root, boolean attachToRoot, android.databinding.DataBindingComponent bindingComponent)") {
            nl("return android.databinding.DataBindingUtil.<$baseClassName>inflate(inflater, ${layoutBinder.modulePackage}.R.layout.${layoutBinder.layoutname}, root, attachToRoot, bindingComponent);")
        }
        if (!layoutBinder.isMerge) {
            block("public static $baseClassName inflate(android.view.LayoutInflater inflater)") {
                nl("return inflate(inflater, android.databinding.DataBindingUtil.getDefaultComponent());")
            }
            block("public static $baseClassName inflate(android.view.LayoutInflater inflater, android.databinding.DataBindingComponent bindingComponent)") {
                nl("return bind(inflater.inflate(${layoutBinder.modulePackage}.R.layout.${layoutBinder.layoutname}, null, false), bindingComponent);")
            }
            block("public static $baseClassName bind(android.view.View view)") {
                nl("return bind(view, android.databinding.DataBindingUtil.getDefaultComponent());")
            }
            block("public static $baseClassName bind(android.view.View view, android.databinding.DataBindingComponent bindingComponent)") {
                block("if (!\"${layoutBinder.tag}_0\".equals(view.getTag()))") {
                    nl("throw new RuntimeException(\"view tag isn't correct on view:\" + view.getTag());")
                }
                nl("return new $baseClassName(bindingComponent, view);")
            }
        }
    }

    /**
     * When called for a library compilation, we do not generate real implementations
     */
    public fun writeBaseClass(forLibrary : Boolean) : String =
        kcode("package ${layoutBinder.`package`};") {
            Scope.reset()
            nl("import android.databinding.Bindable;")
            nl("import android.databinding.DataBindingUtil;")
            nl("import android.databinding.ViewDataBinding;")
            nl("public abstract class $baseClassName extends ViewDataBinding {")
            layoutBinder.sortedTargets.filter{it.id != null}.forEach {
                tab("public final ${it.interfaceClass} ${it.fieldName};")
            }
            nl("")
            tab("protected $baseClassName(android.databinding.DataBindingComponent bindingComponent, android.view.View root_, int localFieldCount") {
                layoutBinder.sortedTargets.filter{it.id != null}.forEach {
                    tab(", ${it.interfaceClass} ${it.constructorParamName}")
                }
            }
            tab(") {") {
                tab("super(bindingComponent, root_, localFieldCount);")
                layoutBinder.sortedTargets.filter{it.id != null}.forEach {
                    tab("this.${it.fieldName} = ${it.constructorParamName};")
                }
            }
            tab("}")
            nl("")
            variables.forEach {
                if (it.userDefinedType != null) {
                    val type = ModelAnalyzer.getInstance().applyImports(it.userDefinedType, model.imports)
                    tab("public abstract void ${it.setterName}($type ${it.readableName});")
                }
            }
            tab("public static $baseClassName inflate(android.view.LayoutInflater inflater, android.view.ViewGroup root, boolean attachToRoot) {") {
                tab("return inflate(inflater, root, attachToRoot, android.databinding.DataBindingUtil.getDefaultComponent());")
            }
            tab("}")
            tab("public static $baseClassName inflate(android.view.LayoutInflater inflater) {") {
                tab("return inflate(inflater, android.databinding.DataBindingUtil.getDefaultComponent());")
            }
            tab("}")
            tab("public static $baseClassName bind(android.view.View view) {") {
                if (forLibrary) {
                    tab("return null;")
                } else {
                    tab("return bind(view, android.databinding.DataBindingUtil.getDefaultComponent());")
                }
            }
            tab("}")
            tab("public static $baseClassName inflate(android.view.LayoutInflater inflater, android.view.ViewGroup root, boolean attachToRoot, android.databinding.DataBindingComponent bindingComponent) {") {
                if (forLibrary) {
                    tab("return null;")
                } else {
                    tab("return DataBindingUtil.<$baseClassName>inflate(inflater, ${layoutBinder.modulePackage}.R.layout.${layoutBinder.layoutname}, root, attachToRoot, bindingComponent);")
                }
            }
            tab("}")
            tab("public static $baseClassName inflate(android.view.LayoutInflater inflater, android.databinding.DataBindingComponent bindingComponent) {") {
                if (forLibrary) {
                    tab("return null;")
                } else {
                    tab("return DataBindingUtil.<$baseClassName>inflate(inflater, ${layoutBinder.modulePackage}.R.layout.${layoutBinder.layoutname}, null, false, bindingComponent);")
                }
            }
            tab("}")
            tab("public static $baseClassName bind(android.view.View view, android.databinding.DataBindingComponent bindingComponent) {") {
                if (forLibrary) {
                    tab("return null;")
                } else {
                    tab("return ($baseClassName)bind(bindingComponent, view, ${layoutBinder.modulePackage}.R.layout.${layoutBinder.layoutname});")
                }
            }
            tab("}")
            nl("}")
        }.generate()
}
