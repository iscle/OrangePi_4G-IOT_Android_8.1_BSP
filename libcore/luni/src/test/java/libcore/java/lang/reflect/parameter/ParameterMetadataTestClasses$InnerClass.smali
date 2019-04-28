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

.class Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$InnerClass;
.super Ljava/lang/Object;
.source "ParameterMetadataTestClasses.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = "InnerClass"
.end annotation


# instance fields
.field final synthetic this$0:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;


# direct methods
.method public constructor <init>(Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;)V
    .registers 2
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010
        }
        names = {
            "this$0"
        }
    .end annotation


    .prologue
    .line 32
    iput-object p1, p0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$InnerClass;->this$0:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public constructor <init>(Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;Ljava/lang/String;)V
    .registers 3
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010, 0x0
        }
        names = {
            "this$0", "p1"
        }
    .end annotation

    .prologue
    .line 34
    iput-object p1, p0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$InnerClass;->this$0:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public constructor <init>(Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;Ljava/util/function/Function;)V
    .registers 3
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010, 0x0
        }
        names = {
            "this$0", "p1"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/function/Function",
            "<",
            "Ljava/lang/String;",
            "Ljava/lang/Integer;",
            ">;)V"
        }
    .end annotation

    .prologue
    .line 36
    iput-object p1, p0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$InnerClass;->this$0:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method
