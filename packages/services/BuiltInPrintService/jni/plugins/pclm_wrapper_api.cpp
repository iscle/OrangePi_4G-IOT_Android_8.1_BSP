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

#include "PCLmGenerator.h"
#include "pclm_wrapper_api.h"

void *CreatePCLmGen() {
    return new PCLmGenerator();
}

int PCLmStartJob(void *thisClass, void **pOutBuffer, int *iOutBufferSize) {
    return static_cast<PCLmGenerator *>(thisClass)->StartJob(pOutBuffer, iOutBufferSize);
}

int PCLmEndJob(void *thisClass, void **pOutBuffer, int *iOutBufferSize) {
    return static_cast<PCLmGenerator *>(thisClass)->EndJob(pOutBuffer, iOutBufferSize);
}

int PCLmStartPage(void *thisClass, PCLmPageSetup *PCLmPageContent, void **pOutBuffer,
        int *iOutBufferSize) {
    return static_cast<PCLmGenerator *>(thisClass)->StartPage(PCLmPageContent, pOutBuffer,
            iOutBufferSize);
}

int PCLmEndPage(void *thisClass, void **pOutBuffer, int *iOutBufferSize) {
    return static_cast<PCLmGenerator *>(thisClass)->EndPage(pOutBuffer, iOutBufferSize);
}

int PCLmEncapsulate(void *thisClass, void *pInBuffer, int inBufferSize, int numLines,
        void **pOutBuffer, int *iOutBufferSize) {
    return static_cast<PCLmGenerator *>(thisClass)->Encapsulate(pInBuffer, inBufferSize, numLines,
            pOutBuffer, iOutBufferSize);
}

void PCLmFreeBuffer(void *thisClass, void *pBuffer) {
    return static_cast<PCLmGenerator *>(thisClass)->FreeBuffer(pBuffer);
}

void DestroyPCLmGen(void *thisClass) {
    delete static_cast<PCLmGenerator *>(thisClass);
}

int PCLmGetMediaDimensions(void *thisClass, const char *mediaRequested, PCLmPageSetup *myPageInfo) {
    return static_cast<PCLmGenerator *>(thisClass)->GetPclmMediaDimensions(mediaRequested,
            myPageInfo);
}