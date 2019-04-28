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

.class Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$1MethodClass;
.super Ljava/lang/Object;
.source "ParameterMetadataTestClasses.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;->getMethodClassWith1ImplicitParameterConstructor()Ljava/lang/Class;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = "MethodClass"
.end annotation


# instance fields
.field final synthetic this$0:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;


# direct methods
.method constructor <init>(Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;)V
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
    .line 81
    iput-object p1, p0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$1MethodClass;->this$0:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 82
    invoke-static {p1}, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;->-wrap0(Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;)Ljava/lang/String;

    .line 81
    return-void
.end method
