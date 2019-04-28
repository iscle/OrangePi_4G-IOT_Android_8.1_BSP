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

#ifndef FRAMEWORKS_ML_NN_COMMON_OPERATIONS_INTERNAL_OPTIMIZED_CPU_CHECK_
#define FRAMEWORKS_ML_NN_COMMON_OPERATIONS_INTERNAL_OPTIMIZED_CPU_CHECK_

// NEON_OR_PORTABLE(SomeFunc, arcs) calls NeonSomeFunc(args) if NEON is
// enabled at build time, or PortableSomeFunc(args) otherwise.
#if defined(__ARM_NEON__) || defined(__ARM_NEON)
#define NEON_OR_PORTABLE(funcname, ...) Neon##funcname(__VA_ARGS__)
#else
#define NEON_OR_PORTABLE(funcname, ...) Portable##funcname(__VA_ARGS__)
#endif

#endif  // FRAMEWORKS_ML_NN_COMMON_OPERATIONS_INTERNAL_OPTIMIZED_CPU_CHECK_
