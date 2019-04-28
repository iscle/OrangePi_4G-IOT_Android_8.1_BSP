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

.class Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$1;
.super Ljava/lang/Object;
.source "ParameterMetadataTestClasses.java"

# interfaces
.implements Ljava/util/concurrent/Callable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;->getAnonymousClassWith1ParameterConstructor()Ljava/lang/Class;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ljava/lang/Object;",
        "Ljava/util/concurrent/Callable",
        "<",
        "Ljava/lang/String;",
        ">;"
    }
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
    .line 70
    iput-object p1, p0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$1;->this$0:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public bridge synthetic call()Ljava/lang/Object;
    .registers 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/Exception;
        }
    .end annotation

    .prologue
    .line 72
    invoke-virtual {p0}, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$1;->call()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method

.method public call()Ljava/lang/String;
    .registers 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/Exception;
        }
    .end annotation

    .prologue
    .line 73
    iget-object v0, p0, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses$1;->this$0:Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;

    invoke-static {v0}, Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;->-wrap0(Llibcore/java/lang/reflect/parameter/ParameterMetadataTestClasses;)Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method
