/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "aot_class_linker.h"

#include "class_reference.h"
#include "compiler_callbacks.h"
#include "handle_scope-inl.h"
#include "mirror/class-inl.h"
#include "runtime.h"
#include "verifier/verifier_enums.h"

namespace art {

AotClassLinker::AotClassLinker(InternTable *intern_table) : ClassLinker(intern_table) {}

AotClassLinker::~AotClassLinker() {}

verifier::FailureKind AotClassLinker::PerformClassVerification(Thread* self,
                                                               Handle<mirror::Class> klass,
                                                               verifier::HardFailLogMode log_level,
                                                               std::string* error_msg) {
  Runtime* const runtime = Runtime::Current();
  CompilerCallbacks* callbacks = runtime->GetCompilerCallbacks();
  if (callbacks->CanAssumeVerified(ClassReference(&klass->GetDexFile(),
                                                  klass->GetDexClassDefIndex()))) {
    return verifier::FailureKind::kNoFailure;
  }
  return ClassLinker::PerformClassVerification(self, klass, log_level, error_msg);
}

}  // namespace art
