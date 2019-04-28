/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
 * Copyright (C) 2013 Hewlett-Packard Development Company, L.P.
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

#include "common_defines.h"

#ifndef PCLMWRAPPERAPI_H_
#define PCLMWRAPPERAPI_H_

// uses C calling convention
#ifdef __cplusplus
extern "C" {
#endif

/*
 * C mappings for CPP PCLmGenerator APIs
 */

void *CreatePCLmGen();
int PCLmStartJob(void *thisClass, void **pOutBuffer, int *iOutBufferSize);
int PCLmEndJob(void *thisClass, void **pOutBUffer, int *iOutBifferSize);
int PCLmStartPage(void *thisClass, PCLmPageSetup *PCLmPageContent, void **pOutBuffer,
        int *iOutBufferSize);
int PCLmEndPage(void *thisClass, void **pOutBuffer, int *iOutBufferSize);
int PCLmEncapsulate(void *thisClass, void *pInBuffer, int inBufferSize, int numLines,
        void **pOutBuffer, int *iOutBufferSize);
void PCLmFreeBuffer(void *thisClass, void *pBuffer);
void DestroyPCLmGen(void *thisClass);
int PCLmGetMediaDimensions(void *thisClass, const char *mediaRequested, PCLmPageSetup *myPageInfo);
#ifdef __cplusplus
}
#endif

#endif // PCLMWRAPPERAPI_H_