/*
 * Copyright (C) 2015 The Android Open Source Project
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

// This test the case where a kernel does not use a double but there is a global variable of type
// double present in the file see bug:23213925

#pragma version(1)
#pragma rs java_package_name(android.renderscript.cts)
#pragma rs_fp_relaxed

float pi2;
double pi = 3.14159265359;

void func_setup() {
  pi2 = pi * 2;
}

float RS_KERNEL times2pi(uint32_t x) {
  return pi2 * x;
}