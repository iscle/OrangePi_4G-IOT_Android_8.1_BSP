#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Originally generated using baksmali and edited. See README.txt in this directory.

.class final synthetic Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$-java_lang_Class_getLambdaClassWith1ParameterConstructor__LambdaImpl0;
.super Ljava/lang/Object;
.source "ParameterMetadataTestClasses.java"

# interfaces
.implements Ljava/util/concurrent/Callable;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x1010
    name = "-java_lang_Class_getLambdaClassWith1ParameterConstructor__LambdaImpl0"
.end annotation


# instance fields
.field private synthetic val$this:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;


# direct methods
.method public synthetic constructor <init>(Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;)V
    .registers 2

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$-java_lang_Class_getLambdaClassWith1ParameterConstructor__LambdaImpl0;->val$this:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;

    return-void
.end method


# virtual methods
.method public call()Ljava/lang/Object;
    .registers 2

    iget-object v0, p0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$-java_lang_Class_getLambdaClassWith1ParameterConstructor__LambdaImpl0;->val$this:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;

    invoke-virtual {v0}, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;->-libcore_java_lang_reflect_parameter_ParameterMetadataTestClasses-mthref-0()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method
