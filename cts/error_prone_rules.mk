# Copyright (C) 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Set of error prone rules to ensure code quality
LOCAL_ERROR_PRONE_FLAGS:= -Xep:ArrayToString:ERROR \
                          -Xep:BoxedPrimitiveConstructor:ERROR \
                          -Xep:ConstantField:ERROR \
                          -Xep:EqualsIncompatibleType:ERROR \
                          -Xep:FormatString:ERROR \
                          -Xep:GetClassOnClass:ERROR \
                          -Xep:IdentityBinaryExpression:ERROR \
                          -Xep:JUnit3TestNotRun:ERROR \
                          -Xep:JUnitAmbiguousTestClass:ERROR \
                          -Xep:MissingFail:ERROR \
                          -Xep:MissingOverride:ERROR \
                          -Xep:Overrides:ERROR \
                          -Xep:ReferenceEquality:ERROR \
                          -Xep:RemoveUnusedImports:ERROR \
                          -Xep:ReturnValueIgnored:ERROR \
                          -Xep:SelfEquals:ERROR \
                          -Xep:SizeGreaterThanOrEqualsZero:ERROR \
                          -Xep:TryFailThrowable:ERROR

