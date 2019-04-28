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

.class public Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;
.super Ljava/lang/Object;
.source "ParameterMetadataTestClasses.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$-java_lang_Class_getLambdaClassWith1ParameterConstructor__LambdaImpl0;,
        Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$FinalParameter;,
        Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$GenericParameter;,
        Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$InnerClass;,
        Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$MixedVarArgs;,
        Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$NonIdenticalParameters;,
        Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$NonVarArgs;,
        Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$SingleParameter;,
        Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$SingleVarArgs;,
        Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;,
        Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TwoParameters;
    }
.end annotation


# direct methods
.method static synthetic -wrap0(Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;)Ljava/lang/String;
    .registers 2

    invoke-direct {p0}, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;->outerClassMethod()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method

.method public constructor <init>()V
    .registers 1

    .prologue
    .line 6
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method private outerClassMethod()Ljava/lang/String;
    .registers 2

    .prologue
    .line 191
    const-string/jumbo v0, "Howdy"

    return-object v0
.end method


# virtual methods
.method synthetic -libcore_java_lang_reflect_parameter_ParameterMetadataTestClasses-mthref-0()Ljava/lang/String;
    .registers 2

    .prologue
    .line 89
    invoke-direct {p0}, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;->outerClassMethod()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method

.method public getAnonymousClassWith1ParameterConstructor()Ljava/lang/Class;
    .registers 2
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/lang/Class",
            "<*>;"
        }
    .end annotation

    .prologue
    .line 70
    new-instance v0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$1;

    invoke-direct {v0, p0}, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$1;-><init>(Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;)V

    .line 76
    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    move-result-object v0

    return-object v0
.end method

.method public getLambdaClassWith1ParameterConstructor()Ljava/lang/Class;
    .registers 2
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/lang/Class",
            "<*>;"
        }
    .end annotation

    .prologue
    .line 89
    new-instance v0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$-java_lang_Class_getLambdaClassWith1ParameterConstructor__LambdaImpl0;

    invoke-direct {v0, p0}, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$-java_lang_Class_getLambdaClassWith1ParameterConstructor__LambdaImpl0;-><init>(Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;)V

    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    move-result-object v0

    return-object v0
.end method

.method public getMethodClassWith1ImplicitParameterConstructor()Ljava/lang/Class;
    .registers 2
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/lang/Class",
            "<*>;"
        }
    .end annotation

    .prologue
    .line 85
    const-class v0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$1MethodClass;

    return-object v0
.end method
