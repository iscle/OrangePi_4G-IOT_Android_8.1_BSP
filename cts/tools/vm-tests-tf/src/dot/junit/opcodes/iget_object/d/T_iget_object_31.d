; Copyright (C) 2014 The Android Open Source Project
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;      http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

.source T_iget_object_31.java
.class public dot.junit.opcodes.iget_object.d.T_iget_object_31
.super java/lang/Object

.field public st_i1 Ldot/junit/opcodes/iget_object/d/T_iget_object_31;

.method public <init>()V
.limit regs 1
       invoke-direct {v0}, java/lang/Object/<init>()V
       return-void
.end method

.method public run()Ldot/junit/opcodes/iget_object/d/T_iget_object_31;
.limit regs 3
    iget v0, v0, dot.junit.opcodes.iget_object.d.T_iget_object_31.st_i1 Ldot/junit/opcodes/iget_object/d/T_iget_object_31;
    return v0
.end method

