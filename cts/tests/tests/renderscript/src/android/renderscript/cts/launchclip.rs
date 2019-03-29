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

#include "shared.rsh"

rs_allocation passfail;

int dimX;
int dimY;
int dimZ;
bool hasFaces;
bool hasLod;
int dimA0;
int dimA1;
int dimA2;
int dimA3;

int biasX = 0;
int biasY = 0;
int biasZ = 0;


int RS_KERNEL zero() {
    return 0;
}

int RS_KERNEL write1d(uint32_t x) {
    return 0x80000000 | (x + biasX) | (biasY << 8) | (biasZ << 16);
}

int RS_KERNEL write2d(uint32_t x, uint32_t y) {
    return 0x80000000 | (x + biasX) | ((y + biasY) << 8) | (biasZ << 16);
}

int RS_KERNEL write3d(uint32_t x, uint32_t y, uint32_t z) {
    return 0x80000000 | (x + biasX) | ((y + biasY) << 8) | ((z + biasZ) << 16);
}


