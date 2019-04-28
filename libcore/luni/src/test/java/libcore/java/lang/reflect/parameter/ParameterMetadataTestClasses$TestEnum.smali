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

.class final enum Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;
.super Ljava/lang/Enum;
.source "ParameterMetadataTestClasses.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x4018
    name = "TestEnum"
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ljava/lang/Enum",
        "<",
        "Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;",
        ">;"
    }
.end annotation


# static fields
.field private static final synthetic $VALUES:[Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;

.field public static final enum ONE:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;

.field public static final enum TWO:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;


# direct methods
.method static constructor <clinit>()V
    .registers 4

    .prologue
    const/4 v3, 0x1

    const/4 v2, 0x0

    .line 39
    new-instance v0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;

    const-string/jumbo v1, "ONE"

    invoke-direct {v0, v1, v2}, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;-><init>(Ljava/lang/String;I)V

    sput-object v0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;->ONE:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;

    new-instance v0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;

    const-string/jumbo v1, "TWO"

    invoke-direct {v0, v1, v3}, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;-><init>(Ljava/lang/String;I)V

    sput-object v0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;->TWO:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;

    const/4 v0, 0x2

    new-array v0, v0, [Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;

    sget-object v1, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;->ONE:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;

    aput-object v1, v0, v2

    sget-object v1, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;->TWO:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;

    aput-object v1, v0, v3

    sput-object v0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;->$VALUES:[Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;

    return-void
.end method

.method private constructor <init>(Ljava/lang/String;I)V
    .registers 3
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x1000, 0x1000
        }
        names = {
            "$enum$name", "$enum$ordinal"
        }
    .end annotation

    .prologue
    .line 39
    invoke-direct {p0, p1, p2}, Ljava/lang/Enum;-><init>(Ljava/lang/String;I)V

    return-void
.end method

.method public static valueOf(Ljava/lang/String;)Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;
    .registers 2
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8000
        }
        names = {
            "name"
        }
    .end annotation

    .prologue
    .line 39
    const-class v0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;

    invoke-static {v0, p0}, Ljava/lang/Enum;->valueOf(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;

    move-result-object v0

    check-cast v0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;

    return-object v0
.end method

.method public static values()[Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;
    .registers 1

    .prologue
    .line 39
    sget-object v0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;->$VALUES:[Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$TestEnum;

    return-object v0
.end method
