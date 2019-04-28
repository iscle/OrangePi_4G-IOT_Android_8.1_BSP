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

#include "../../media.h"
#include <PCLmGenerator.h>

#include <assert.h>
#include <math.h>
#include <zlib.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <genPCLm.h>

#define TAG "genPCLm"

#define STRIP_HEIGHT 16
#define JPEG_QUALITY 100
#define TEMP_BUFF_SIZE 10000000
#define DEFAULT_OUTBUFF_SIZE 64*5120*3*10
#define STANDARD_SCALE_FOR_PDF 72.0
#define KID_STRING_SIZE 1000
#define CATALOG_OBJ_NUMBER 1
#define PAGES_OBJ_NUMBER   2
#define ADOBE_RGB_SIZE 284

#define rgb_2_gray(r, g, b) (ubyte)(0.299*(double)r+0.587*(double)g+0.114*(double)b)

static PCLmSUserSettingsType PCLmSSettings;

/*
 * Shift the strip image right in the strip buffer by leftMargin pixels.
 *
 * Assumptions: The strip buffer was allocated large enough to handle the shift; if not
 * then the image data on the right will get clipped.
 *
 * We allocate a full strip (height and width), but then only copy numLinesThisCall from
 * the original buffer to the newly allocated buffer.  This pads the strips for JPEG processing.
 */
static ubyte *shiftStripByLeftMargin(ubyte *ptrToStrip, sint32 currSourceWidth,
        sint32 currStripHeight, sint32 numLinesThisCall, sint32 currMediaWidth, sint32 leftMargin,
        colorSpaceDisposition destColorSpace) {
    ubyte *fromPtr, *toPtr, *newStrip;
    sint32 scanLineWidth;

    if (destColorSpace == grayScale) {
        scanLineWidth = currMediaWidth;

        // Allocate a full strip
        newStrip = (ubyte *) malloc(scanLineWidth * currStripHeight);
        memset(newStrip, 0xff, scanLineWidth * currStripHeight);
        for (int i = 0; i < numLinesThisCall; i++) {
            toPtr = newStrip + leftMargin + (i * currMediaWidth);
            fromPtr = ptrToStrip + (i * currSourceWidth);
            memcpy(toPtr, fromPtr, currSourceWidth);
        }
    } else {
        scanLineWidth = currMediaWidth * 3;
        sint32 srcScanlineWidth = currSourceWidth * 3;
        sint32 shiftAmount = leftMargin * 3;
        newStrip = (ubyte *) malloc(scanLineWidth * currStripHeight);
        memset(newStrip, 0xff, scanLineWidth * currStripHeight);
        for (int i = 0; i < numLinesThisCall; i++) {
            toPtr = newStrip + shiftAmount + (i * scanLineWidth);
            fromPtr = ptrToStrip + (i * srcScanlineWidth);
            memcpy(toPtr, fromPtr, srcScanlineWidth);
        }
    }

    return newStrip;
}

#ifdef SUPPORT_WHITE_STRIPS

bool PCLmGenerator::isWhiteStrip(void *pInBuffer, int inBufferSize) {
    uint32 *ptr = (uint32 *) pInBuffer;
    for (int i = 0; i < inBufferSize / 4; i++, ptr++) {
        if (*ptr != 0xffffffff) {
            return false;
        }
    }
    return true;
}

#endif

void PCLmGenerator::Cleanup(void) {
    if (allocatedOutputBuffer) {
        free(allocatedOutputBuffer);
        allocatedOutputBuffer = NULL;
        currOutBuffSize = 0;
    }

    if (leftoverScanlineBuffer) {
        free(leftoverScanlineBuffer);
        leftoverScanlineBuffer = NULL;
    }
    if (scratchBuffer) {
        free(scratchBuffer);
        scratchBuffer = NULL;
    }
    if (xRefTable) {
        free(xRefTable);
        xRefTable = NULL;
    }
    if (KidsArray) {
        free(KidsArray);
        KidsArray = NULL;
    }
}

int PCLmGenerator::errorOutAndCleanUp() {
    Cleanup();
    jobOpen = job_errored;
    return genericFailure;
}

static sint32 startXRef = 0;
static sint32 endXRef = 0;

/*
 * DO NOT EDIT UNTIL YOU READ THE HEADER FILE DESCRIPTION.
 */
void PCLmGenerator::fixXRef() {
    if (!startXRef || !mirrorBackside) {
        return;
    }

    if (currDuplexDisposition == duplex_longEdge && !(pageCount % 2) && mirrorBackside) {
        assert(startXRef);
        sint32 start = startXRef;
        sint32 end = endXRef - 1;
        sint32 aSize = endXRef - startXRef - 1;

        sint32 *tmpArray = (sint32 *) malloc(aSize * 20);

        sint32 xRefI = startXRef;
        for (int i = 0; i < aSize + 1; i++, xRefI++) {
            *(tmpArray + i) = xRefTable[xRefI + 1] - xRefTable[xRefI];
        }

        // Reorder header and image sizes
        for (int i = 0; i < aSize + 1; i += 2, xRefI++) {
            sint32 t = *(tmpArray + i);
            *(tmpArray + i) = *(tmpArray + i + 1);
            *(tmpArray + i + 1) = t;
        }

        xRefI = aSize;
        for (int i = start + 1, j = aSize; i < end + 2; i++, start++, xRefI--, j--) {
            xRefTable[i] = (xRefTable[i - 1] + *(tmpArray + j));
        }

        for (int i = startXRef + 2; i < endXRef; i++) {
            xRefTable[i] += 2;
        }

        sint32 k = endXRef - 1;
        int i;
        sint32 lSize = (endXRef - startXRef) / 2;
        for (i = startXRef; i < startXRef + lSize; i++, k--) {
            sint32 t = xRefTable[i];
            xRefTable[i] = xRefTable[k];
            xRefTable[k] = t;
        }
        free(tmpArray);
    }

    startXRef = 0;
}

bool PCLmGenerator::addXRef(sint32 xRefObj) {
#define XREF_ARRAY_SIZE 100
    if (!xRefTable) {
        xRefTable = (sint32 *) malloc(XREF_ARRAY_SIZE * sizeof(sint32));
        assert(xRefTable);
        xRefTable[0] = 0;
        xRefIndex++;
    }

    xRefTable[xRefIndex] = xRefObj;
    xRefIndex++;

    if (!(xRefIndex % XREF_ARRAY_SIZE)) {
        xRefTable = (sint32 *) realloc(xRefTable, (((xRefIndex + XREF_ARRAY_SIZE) *
                sizeof(sint32))));
    }
    return true;
}

bool PCLmGenerator::addKids(sint32 kidObj) {
#define KID_ARRAY_SIZE 20
    if (!KidsArray) {
        KidsArray = (sint32 *) malloc(KID_ARRAY_SIZE * sizeof(sint32));
        assert(KidsArray);
    }

    KidsArray[numKids] = kidObj;
    numKids++;

    if (!(numKids % KID_ARRAY_SIZE)) {
        KidsArray = (sint32 *) realloc(KidsArray, ((numKids + KID_ARRAY_SIZE) * sizeof(sint32)));
    }
    return true;
}

void PCLmGenerator::initOutBuff(char *buff, sint32 size) {
    currBuffPtr = outBuffPtr = buff;
    outBuffSize = size;
    totalBytesWrittenToCurrBuff = 0;
    memset(buff, 0, size);
}

void PCLmGenerator::writeStr2OutBuff(char *str) {
    sint32 strSize = strlen(str);
    // Make sure we have enough room for the copy
    char *maxSize = currBuffPtr + strSize;
    assert(maxSize - outBuffPtr < outBuffSize);
    memcpy(currBuffPtr, str, strSize);
    currBuffPtr += strSize;
    totalBytesWrittenToCurrBuff += strSize;
    totalBytesWrittenToPCLmFile += strSize;
}

void PCLmGenerator::write2Buff(ubyte *buff, int buffSize) {
    char *maxSize = currBuffPtr + buffSize;
    if (maxSize - outBuffPtr > outBuffSize) {
        assert(0);
    }
    memcpy(currBuffPtr, buff, buffSize);
    currBuffPtr += buffSize;
    totalBytesWrittenToCurrBuff += buffSize;
    totalBytesWrittenToPCLmFile += buffSize;
}

int PCLmGenerator::statOutputFileSize() {
    addXRef(totalBytesWrittenToPCLmFile);
    return (1);
}

void PCLmGenerator::writePDFGrammarTrailer(int imageWidth, int imageHeight) {
    int i;
    char KidsString[KID_STRING_SIZE];

    sprintf(pOutStr, "%%============= PCLm: FileBody: Object 1 - Catalog\n");
    writeStr2OutBuff(pOutStr);
    statOutputFileSize();
    sprintf(pOutStr, "%d 0 obj\n", CATALOG_OBJ_NUMBER);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "<<\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Type /Catalog\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Pages %d 0 R\n", PAGES_OBJ_NUMBER);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, ">>\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "endobj\n");
    writeStr2OutBuff(pOutStr);

    sprintf(pOutStr, "%%============= PCLm: FileBody: Object 2 - page tree \n");
    writeStr2OutBuff(pOutStr);
    statOutputFileSize();
    sprintf(pOutStr, "%d 0 obj\n", PAGES_OBJ_NUMBER);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "<<\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Count %ld\n", numKids);
    writeStr2OutBuff(pOutStr);

    // Define the Kids for this document as an indirect array
    sprintf(KidsString, "/Kids [ ");
    writeStr2OutBuff(KidsString);
    for (i = 0; i < numKids; i++) {
        sprintf(KidsString, "%ld 0 R ", KidsArray[i]);
        writeStr2OutBuff(KidsString);
    }

    sprintf(KidsString, "]\n");
    writeStr2OutBuff(KidsString);

    sprintf(pOutStr, "/Type /Pages\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, ">>\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "endobj\n");
    writeStr2OutBuff(pOutStr);

    sprintf(pOutStr, "%%============= PCLm: cross-reference section: object 0, 6 entries\n");
    writeStr2OutBuff(pOutStr);
    statOutputFileSize();

    // Fix up the xref table for backside duplex
    fixXRef();

    xRefStart = xRefIndex - 1;

    sprintf(pOutStr, "xref\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "0 %d\n", 1);
    writeStr2OutBuff(pOutStr);

    // Note the attempt to write exactly 20 bytes
    sprintf(pOutStr, "0000000000 65535 f \n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%d %ld\n", PAGES_OBJ_NUMBER + 1, xRefIndex - 4);
    writeStr2OutBuff(pOutStr);
    for (i = 1; i < xRefIndex - 3; i++) {
        sprintf(pOutStr, "%010ld %05d n \n", xRefTable[i], 0);
        writeStr2OutBuff(pOutStr);
    }

    // sprintf(pOutStr,"<</AIMetaData 32 0 R/AIPDFPrivateData1 33 0 R/AIPDFPrivateData10 34 0\n");

    // Now add the catalog and page object
    sprintf(pOutStr, "%d 2\n", CATALOG_OBJ_NUMBER);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%010ld %05d n \n", xRefTable[xRefIndex - 3], 0);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%010ld %05d n \n", xRefTable[xRefIndex - 2], 0);
    writeStr2OutBuff(pOutStr);

    sprintf(pOutStr, "%%============= PCLm: File Trailer\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "trailer\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "<<\n");
    writeStr2OutBuff(pOutStr);
    // sprintf(pOutStr,"/Info %d 0\n", infoObj); writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Size %ld\n", xRefIndex - 1);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Root %d 0 R\n", CATALOG_OBJ_NUMBER);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, ">>\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "startxref\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%ld\n", xRefTable[xRefStart]);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%%%EOF\n");
    writeStr2OutBuff(pOutStr);
}

bool PCLmGenerator::injectAdobeRGBCS() {
    if (adobeRGBCS_firstTime) {
        // We need to inject the ICC object for AdobeRGB
        sprintf(pOutStr, "%%============= PCLm: ICC Profile\n");
        writeStr2OutBuff(pOutStr);
        statOutputFileSize();
        sprintf(pOutStr, "%ld 0 obj\n", objCounter);
        objCounter++;
        writeStr2OutBuff(pOutStr);
        sprintf(pOutStr, "[/ICCBased %ld 0 R]\n", objCounter);
        writeStr2OutBuff(pOutStr);

        sprintf(pOutStr, "endobj\n");
        writeStr2OutBuff(pOutStr);
        statOutputFileSize();
        sprintf(pOutStr, "%ld 0 obj\n", objCounter);
        objCounter++;
        writeStr2OutBuff(pOutStr);
        sprintf(pOutStr, "<<\n");
        writeStr2OutBuff(pOutStr);
        sprintf(pOutStr, "/N 3\n");
        writeStr2OutBuff(pOutStr);
        sprintf(pOutStr, "/Alternate /DeviceRGB\n");
        writeStr2OutBuff(pOutStr);
        sprintf(pOutStr, "/Length %u\n", ADOBE_RGB_SIZE + 1);
        writeStr2OutBuff(pOutStr);
        sprintf(pOutStr, "/Filter /FlateDecode\n");
        writeStr2OutBuff(pOutStr);
        sprintf(pOutStr, ">>\n");
        writeStr2OutBuff(pOutStr);
        sprintf(pOutStr, "stream\n");
        writeStr2OutBuff(pOutStr);

        FILE *inFile;
        if (!(inFile = fopen("flate_colorspace.bin", "rb"))) {
            fprintf(stderr, "can't open %s\n", "flate_colorspace.bin");
            return 0;
        }

        ubyte *buffIn = (unsigned char *) malloc(ADOBE_RGB_SIZE);
        assert(buffIn);

        sint32 bytesRead = fread(buffIn, 1, ADOBE_RGB_SIZE, inFile);
        assert(bytesRead == ADOBE_RGB_SIZE);
        fclose(inFile);
        write2Buff(buffIn, bytesRead);
        if (buffIn) {
            free(buffIn);
        }

        sprintf(pOutStr, "\nendstream\n");
        writeStr2OutBuff(pOutStr);
        sprintf(pOutStr, "endobj\n");
        writeStr2OutBuff(pOutStr);
    }

    adobeRGBCS_firstTime = false;
    return true;
}

bool PCLmGenerator::colorConvertSource(colorSpaceDisposition srcCS, colorSpaceDisposition dstCS,
        ubyte *strip, sint32 stripWidth, sint32 stripHeight) {
    if (srcCS == deviceRGB && dstCS == grayScale) {
        // Do an inplace conversion from RGB -> 8 bpp gray
        ubyte *srcPtr = strip;
        ubyte *dstPtr = strip;
        for (int h = 0; h < stripHeight; h++) {
            for (int w = 0; w < stripWidth; w++, dstPtr++, srcPtr += 3) {
                *dstPtr = (ubyte) rgb_2_gray(*srcPtr, *(srcPtr + 1), *(srcPtr + 2));
            }
        }
        dstNumComponents = 1;
    } else {
        assert(0);
    }

    return true;
}

int PCLmGenerator::injectRLEStrip(ubyte *RLEBuffer, int numBytes, int imageWidth, int imageHeight,
        colorSpaceDisposition destColorSpace, bool whiteStrip) {
    bool printedImageTransform = false;

    if (currDuplexDisposition == duplex_longEdge && !(pageCount % 2) && mirrorBackside) {
        if (!startXRef) {
            startXRef = xRefIndex;
        }

        injectImageTransform();
        printedImageTransform = true;
    }

    if (destColorSpace == adobeRGB) {
        injectAdobeRGBCS();
    }

    // Inject LZ compressed image into PDF file
    sprintf(pOutStr, "%%============= PCLm: FileBody: Strip Stream: RLE Image \n");
    writeStr2OutBuff(pOutStr);
    statOutputFileSize();

    if (currDuplexDisposition == duplex_longEdge && !(pageCount % 2) && mirrorBackside) {
        sprintf(pOutStr, "%ld 0 obj\n", objCounter - 1);
        objCounter++;
        writeStr2OutBuff(pOutStr);
    } else {
        sprintf(pOutStr, "%ld 0 obj\n", objCounter);
        objCounter++;
        writeStr2OutBuff(pOutStr);
    }

    sprintf(pOutStr, "<<\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Width %d\n", imageWidth);
    writeStr2OutBuff(pOutStr);
    if (destColorSpace == deviceRGB) {
        sprintf(pOutStr, "/ColorSpace /DeviceRGB\n");
        writeStr2OutBuff(pOutStr);
    } else if (destColorSpace == adobeRGB) {
        sprintf(pOutStr, "/ColorSpace 5 0 R\n");
        writeStr2OutBuff(pOutStr);
    } else {
        sprintf(pOutStr, "/ColorSpace /DeviceGray\n");
        writeStr2OutBuff(pOutStr);
    }
    sprintf(pOutStr, "/Height %d\n", imageHeight);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Filter /RunLengthDecode\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Subtype /Image\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Length %d\n", numBytes);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Type /XObject\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/BitsPerComponent 8\n");
    writeStr2OutBuff(pOutStr);
#ifdef SUPPORT_WHITE_STRIPS
    if (whiteStrip) {
        sprintf(pOutStr, "/Name /WhiteStrip\n");
        writeStr2OutBuff(pOutStr);
    } else {
        sprintf(pOutStr, "/Name /ColorStrip\n");
        writeStr2OutBuff(pOutStr);
    }
#endif

    sprintf(pOutStr, ">>\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "stream\n");
    writeStr2OutBuff(pOutStr);

    // Write the zlib compressed strip to the PDF output file
    write2Buff(RLEBuffer, numBytes);
    sprintf(pOutStr, "\nendstream\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "endobj\n");
    writeStr2OutBuff(pOutStr);

    if (!printedImageTransform) {
        injectImageTransform();
    }

    endXRef = xRefIndex;

    return (1);
}

int PCLmGenerator::injectLZStrip(ubyte *LZBuffer, int numBytes, int imageWidth, int imageHeight,
        colorSpaceDisposition destColorSpace, bool whiteStrip) {
    bool printedImageTransform = false;

    if (currDuplexDisposition == duplex_longEdge && !(pageCount % 2) && mirrorBackside) {
        if (!startXRef) {
            startXRef = xRefIndex;
        }

        injectImageTransform();
        printedImageTransform = true;
    }

    if (destColorSpace == adobeRGB) {
        injectAdobeRGBCS();
    }

    // Inject LZ compressed image into PDF file
    sprintf(pOutStr, "%%============= PCLm: FileBody: Strip Stream: zlib Image \n");
    writeStr2OutBuff(pOutStr);
    statOutputFileSize();

    if (currDuplexDisposition == duplex_longEdge && !(pageCount % 2) && mirrorBackside) {
        sprintf(pOutStr, "%ld 0 obj\n", objCounter - 1);
        objCounter++;
        writeStr2OutBuff(pOutStr);
    } else {
        sprintf(pOutStr, "%ld 0 obj\n", objCounter);
        objCounter++;
        writeStr2OutBuff(pOutStr);
    }

    sprintf(pOutStr, "<<\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Width %d\n", imageWidth);
    writeStr2OutBuff(pOutStr);
    if (destColorSpace == deviceRGB) {
        sprintf(pOutStr, "/ColorSpace /DeviceRGB\n");
        writeStr2OutBuff(pOutStr);
    } else if (destColorSpace == adobeRGB) {
        sprintf(pOutStr, "/ColorSpace 5 0 R\n");
        writeStr2OutBuff(pOutStr);
    } else {
        sprintf(pOutStr, "/ColorSpace /DeviceGray\n");
        writeStr2OutBuff(pOutStr);
    }
    sprintf(pOutStr, "/Height %d\n", imageHeight);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Filter /FlateDecode\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Subtype /Image\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Length %d\n", numBytes);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Type /XObject\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/BitsPerComponent 8\n");
    writeStr2OutBuff(pOutStr);
#ifdef SUPPORT_WHITE_STRIPS
    if (whiteStrip) {
        sprintf(pOutStr, "/Name /WhiteStrip\n");
        writeStr2OutBuff(pOutStr);
    } else {
        sprintf(pOutStr, "/Name /ColorStrip\n");
        writeStr2OutBuff(pOutStr);
    }
#endif

    sprintf(pOutStr, ">>\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "stream\n");
    writeStr2OutBuff(pOutStr);

    // Write the zlib compressed strip to the PDF output file
    write2Buff(LZBuffer, numBytes);
    sprintf(pOutStr, "\nendstream\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "endobj\n");
    writeStr2OutBuff(pOutStr);

    if (!printedImageTransform) {
        injectImageTransform();
    }

    endXRef = xRefIndex;

    return (1);
}

void PCLmGenerator::injectImageTransform() {
    char str[512];
    int strLength;
    sprintf(str, "q /image Do Q\n");
    strLength = strlen(str);

    // Output image transformation information
    sprintf(pOutStr, "%%============= PCLm: Object - Image Transformation \n");
    writeStr2OutBuff(pOutStr);
    statOutputFileSize();
    if (currDuplexDisposition == duplex_longEdge && !(pageCount % 2) && mirrorBackside) {
        sprintf(pOutStr, "%ld 0 obj\n", objCounter + 1);
        objCounter++;
        writeStr2OutBuff(pOutStr);
    } else {
        sprintf(pOutStr, "%ld 0 obj\n", objCounter);
        objCounter++;
        writeStr2OutBuff(pOutStr);
    }
    sprintf(pOutStr, "<<\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Length %d\n", strLength);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, ">>\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "stream\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%s", str);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "endstream\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "endobj\n");
    writeStr2OutBuff(pOutStr);
}

int PCLmGenerator::injectJPEG(char *jpeg_Buff, int imageWidth, int imageHeight, int numCompBytes,
        colorSpaceDisposition destColorSpace, bool whiteStrip) {
    char str[512];

    bool printedImageTransform = false;

    if (currDuplexDisposition == duplex_longEdge && !(pageCount % 2) && mirrorBackside) {
        if (!startXRef) {
            startXRef = xRefIndex;
        }

        injectImageTransform();
        printedImageTransform = true;
    }

    yPosition += imageHeight;

    if (destColorSpace == adobeRGB) {
        injectAdobeRGBCS();
    }

    // Inject PDF JPEG into output file
    sprintf(pOutStr, "%%============= PCLm: FileBody: Strip Stream: jpeg Image \n");
    writeStr2OutBuff(pOutStr);
    statOutputFileSize();
    if (currDuplexDisposition == duplex_longEdge && !(pageCount % 2) && mirrorBackside) {
        sprintf(pOutStr, "%ld 0 obj\n", objCounter - 1);
        objCounter++;
        writeStr2OutBuff(pOutStr);
    } else {
        sprintf(pOutStr, "%ld 0 obj\n", objCounter);
        objCounter++;
        writeStr2OutBuff(pOutStr);
    }
    sprintf(pOutStr, "<<\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Width %d\n", imageWidth);
    writeStr2OutBuff(pOutStr);
    if (destColorSpace == deviceRGB) {
        sprintf(pOutStr, "/ColorSpace /DeviceRGB\n");
        writeStr2OutBuff(pOutStr);
    } else if (destColorSpace == adobeRGB) {
        sprintf(pOutStr, "/ColorSpace 5 0 R\n");
        writeStr2OutBuff(pOutStr);
    } else {
        sprintf(pOutStr, "/ColorSpace /DeviceGray\n");
        writeStr2OutBuff(pOutStr);
    }
    sprintf(pOutStr, "/Height %d\n", imageHeight);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Filter /DCTDecode\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Subtype /Image\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Length %d\n", numCompBytes);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Type /XObject\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/BitsPerComponent 8\n");
    writeStr2OutBuff(pOutStr);
#ifdef SUPPORT_WHITE_STRIPS
    if (whiteStrip) {
        sprintf(pOutStr, "/Name /WhiteStrip\n");
        writeStr2OutBuff(pOutStr);
    } else {
        sprintf(pOutStr, "/Name /ColorStrip\n");
        writeStr2OutBuff(pOutStr);
    }
#endif
    sprintf(pOutStr, ">>\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "stream\n");
    writeStr2OutBuff(pOutStr);

    write2Buff((ubyte *) jpeg_Buff, numCompBytes);
    sprintf(pOutStr, "\nendstream\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "endobj\n");
    writeStr2OutBuff(pOutStr);

    sprintf(str, "q /image Do Q\n");

    if (!printedImageTransform) {
        injectImageTransform();
    }

    endXRef = xRefIndex;

    return (1);
}

/*
 * Writes str to buffer if the size of buffer is less than TEMP_BUFF_SIZE
 */
void writeStr2Buff(char *buffer, char *str) {
    int buffSize;
    char *buffPos;

    buffSize = strlen(buffer) + strlen(str);
    if (buffSize > TEMP_BUFF_SIZE) {
        assert(0);
    }

    buffSize = strlen(buffer);
    buffPos = buffer + buffSize;
    sprintf(buffPos, "%s", str);

    buffSize = strlen(buffer);
    if (buffSize > TEMP_BUFF_SIZE) {
        printf("tempBuff size exceeded: buffSize=%d\n", buffSize);
        assert(0);
    }
}

void PCLmGenerator::writePDFGrammarPage(int imageWidth, int imageHeight, int numStrips,
        colorSpaceDisposition destColorSpace) {
    int i, imageRef = objCounter + 2, buffSize;
    int yAnchor;
    char str[512];
    char *tempBuffer;
    int startImageIndex = 0;
    int numLinesLeft = 0;

    if (destColorSpace == adobeRGB && 1 == pageCount) {
        imageRef += 2; // Add 2 for AdobeRGB
    }

    tempBuffer = (char *) malloc(TEMP_BUFF_SIZE);
    assert(tempBuffer);
    memset(tempBuffer, 0x0, TEMP_BUFF_SIZE);

    sprintf(pOutStr, "%%============= PCLm: FileBody: Object 3 - page object\n");
    writeStr2OutBuff(pOutStr);
    statOutputFileSize();
    sprintf(pOutStr, "%ld 0 obj\n", objCounter);
    writeStr2OutBuff(pOutStr);
    addKids(objCounter);
    objCounter++;
    sprintf(pOutStr, "<<\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Type /Page\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Parent %d 0 R\n", PAGES_OBJ_NUMBER);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/Resources <<\n");
    writeStr2OutBuff(pOutStr);
    // sprintf(pOutStr,"/ProcSet [ /PDF /ImageC ]\n"); writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "/XObject <<\n");
    writeStr2OutBuff(pOutStr);

    if (topMarginInPix) {
        for (i = 0; i < numFullInjectedStrips; i++, startImageIndex++) {
            sprintf(str, "/Image%d %d 0 R\n", startImageIndex, imageRef);
            sprintf(pOutStr, "%s", str);
            writeStr2OutBuff(pOutStr);
            imageRef += 2;
        }
        if (numPartialScanlinesToInject) {
            sprintf(str, "/Image%d %d 0 R\n", startImageIndex, imageRef);
            sprintf(pOutStr, "%s", str);
            writeStr2OutBuff(pOutStr);
            imageRef += 2;
            startImageIndex++;
        }
    }

    for (i = startImageIndex; i < numStrips + startImageIndex; i++) {
        sprintf(str, "/Image%d %d 0 R\n", i, imageRef);
        // sprintf(pOutStr,"/ImageA 4 0 R /ImageB 6 0 R >>\n"); writeStr2OutBuff(pOutStr);
        sprintf(pOutStr, "%s", str);
        writeStr2OutBuff(pOutStr);
        imageRef += 2;
    }
    sprintf(pOutStr, ">>\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, ">>\n");
    writeStr2OutBuff(pOutStr);
    if (currMediaOrientationDisposition == landscapeOrientation) {
        pageOrigin = mediaWidth;
        sprintf(pOutStr, "/MediaBox [ 0 0 %d %d ]\n", mediaHeight, mediaWidth);
        writeStr2OutBuff(pOutStr);
    } else {
        pageOrigin = mediaHeight;
        sprintf(pOutStr, "/MediaBox [ 0 0 %d %d ]\n", mediaWidth, mediaHeight);
        writeStr2OutBuff(pOutStr);
    }
    sprintf(pOutStr, "/Contents [ %ld 0 R ]\n", objCounter);
    writeStr2OutBuff(pOutStr);
#ifdef PIECEINFO_SUPPORTED
    sprintf(pOutStr,"/PieceInfo <</HPAddition %d 0 R >> \n",9997); writeStr2OutBuff(pOutStr);
#endif
    sprintf(pOutStr, ">>\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "endobj\n");
    writeStr2OutBuff(pOutStr);

    // Create the FileBody stream first, so we know the Length of the stream
    if (reverseOrder) {
        yAnchor = 0;
    } else {
        yAnchor = (int) ((pageOrigin * STANDARD_SCALE) + 0.99); // Round up
    }

    // Setup the CTM so that we can send device-resolution coordinates
    sprintf(pOutStr,
            "%%Image Transformation Matrix: width, skewX, skewY, height, xAnchor, yAnchor\n");
    writeStr2OutBuff(pOutStr);
    sprintf(str, "%f 0 0 %f 0 0 cm\n", STANDARD_SCALE_FOR_PDF / currRenderResolutionInteger,
            STANDARD_SCALE_FOR_PDF / currRenderResolutionInteger);
    writeStr2Buff(tempBuffer, str);

    startImageIndex = 0;
    if (topMarginInPix) {
        for (i = 0; i < numFullInjectedStrips; i++) {
            if (reverseOrder) {
                yAnchor += numFullScanlinesToInject;
            } else {
                yAnchor -= numFullScanlinesToInject;
            }

            sprintf(str, "/P <</MCID 0>> BDC q\n");
            writeStr2Buff(tempBuffer, str);

            sprintf(str, "%%Image Transformation Matrix: width, skewX, skewY, height, "
                    "xAnchor, yAnchor\n");
            writeStr2Buff(tempBuffer, str);

            sprintf(str, "%d 0 0 %ld 0 %d cm\n", imageWidth * scaleFactor,
                    numFullScanlinesToInject * scaleFactor, yAnchor * scaleFactor);
            writeStr2Buff(tempBuffer, str);

            sprintf(str, "/Image%d Do Q\n", startImageIndex);
            writeStr2Buff(tempBuffer, str);

            startImageIndex++;
        }
        if (numPartialScanlinesToInject) {
            if (reverseOrder) {
                yAnchor += numPartialScanlinesToInject;
            } else {
                yAnchor -= numPartialScanlinesToInject;
            }

            sprintf(str, "/P <</MCID 0>> BDC q\n");
            writeStr2Buff(tempBuffer, str);

            sprintf(str, "%%Image Transformation Matrix: width, skewX, skewY, height, xAnchor, "
                    "yAnchor\n");

            writeStr2Buff(tempBuffer, str);

            sprintf(str, "%d 0 0 %ld 0 %d cm\n", imageWidth * scaleFactor,
                    numPartialScanlinesToInject * scaleFactor, yAnchor * scaleFactor);
            writeStr2Buff(tempBuffer, str);

            sprintf(str, "/Image%d Do Q\n", startImageIndex);
            writeStr2Buff(tempBuffer, str);

            startImageIndex++;
        }
    }

    for (i = startImageIndex; i < numStrips + startImageIndex; i++) {
        // last strip may have less lines than currStripHeight. Update yAnchor using left over lines
        if (i == (numStrips + startImageIndex - 1)) {
            numLinesLeft = currSourceHeight - ((numStrips - 1) * currStripHeight);

            if (reverseOrder) {
                yAnchor += numLinesLeft;
            } else {
                yAnchor -= numLinesLeft;
            }
        } else {
            if (reverseOrder) {
                yAnchor += currStripHeight;
            } else {
                yAnchor -= currStripHeight;
            }
        }

        sprintf(str, "/P <</MCID 0>> BDC q\n");
        writeStr2Buff(tempBuffer, str);

        sprintf(str,
                "%%Image Transformation Matrix: width, skewX, skewY, height, xAnchor, yAnchor\n");
        writeStr2Buff(tempBuffer, str);

        // last strip may have less lines than currStripHeight
        if (i == (numStrips + startImageIndex - 1)) {
            sprintf(str, "%d 0 0 %d 0 %d cm\n", imageWidth * scaleFactor,
                    numLinesLeft * scaleFactor, yAnchor * scaleFactor);
            writeStr2Buff(tempBuffer, str);
        } else if (yAnchor < 0) {
            sint32 newH = currStripHeight + yAnchor;
            sprintf(str, "%d 0 0 %ld 0 %d cm\n", imageWidth * scaleFactor, newH * scaleFactor,
                    0 * scaleFactor);
            writeStr2Buff(tempBuffer, str);
        } else {
            sprintf(str, "%d 0 0 %ld 0 %d cm\n", imageWidth * scaleFactor,
                    currStripHeight * scaleFactor, yAnchor * scaleFactor);
            writeStr2Buff(tempBuffer, str);
        }

        sprintf(str, "/Image%d Do Q\n", i);
        writeStr2Buff(tempBuffer, str);
    }

    // Resulting buffer size
    buffSize = strlen(tempBuffer);

    sprintf(pOutStr, "%%============= PCLm: FileBody: Page Content Stream object\n");
    writeStr2OutBuff(pOutStr);
    statOutputFileSize();
    sprintf(pOutStr, "%ld 0 obj\n", objCounter);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "<<\n");
    writeStr2OutBuff(pOutStr);

    sprintf(pOutStr, "/Length %d\n", buffSize);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, ">>\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "stream\n");
    writeStr2OutBuff(pOutStr);

    // Now write the FileBody stream
    write2Buff((ubyte *) tempBuffer, buffSize);

    sprintf(pOutStr, "endstream\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "endobj\n");
    writeStr2OutBuff(pOutStr);
    objCounter++;
    if (tempBuffer) {
        free(tempBuffer);
    }
}

/*
 * Mirrors the source image in preparation for backside duplex support
 */
static bool prepImageForBacksideDuplex(ubyte *imagePtr, sint32 imageHeight, sint32 imageWidth,
        sint32 numComponents) {
    sint32 numBytes = imageHeight * imageWidth * numComponents;
    ubyte *head, *tail, t0, t1, t2;

    if (numComponents == 3) {
        for (head = imagePtr, tail = imagePtr + numBytes - 1; tail > head;) {
            t0 = *head;
            t1 = *(head + 1);
            t2 = *(head + 2);

            *head = *(tail - 2);
            *(head + 1) = *(tail - 1);
            *(head + 2) = *(tail - 0);
            *tail = t2;
            *(tail - 1) = t1;
            *(tail - 2) = t0;

            head += 3;
            tail -= 3;
        }
    } else {
        for (head = imagePtr, tail = imagePtr + numBytes; tail > head;) {
            t0 = *head;
            *head = *tail;
            *tail = t0;
            head++;
            tail--;
        }
    }
//origTail++;
    return true;
}

bool PCLmGenerator::getInputBinString(jobInputBin bin, char *returnStr) {
    returnStr[0] = '\0';

    switch (bin) {
        case alternate:
            strcpy(returnStr, "alternate");
            break;
        case alternate_roll:
            strcpy(returnStr, "alternate_roll");
            break;
        case auto_select:
            strcpy(returnStr, "auto_select");
            break;
        case bottom:
            strcpy(returnStr, "bottom");
            break;
        case center:
            strcpy(returnStr, "center");
            break;
        case disc:
            strcpy(returnStr, "disc");
            break;
        case envelope:
            strcpy(returnStr, "envelope");
            break;
        case hagaki:
            strcpy(returnStr, "hagaki");
            break;
        case large_capacity:
            strcpy(returnStr, "large_capacity");
            break;
        case left:
            strcpy(returnStr, "left");
            break;
        case main_tray:
            strcpy(returnStr, "main_tray");
            break;
        case main_roll:
            strcpy(returnStr, "main_roll");
            break;
        case manual:
            strcpy(returnStr, "manual");
            break;
        case middle:
            strcpy(returnStr, "middle");
            break;
        case photo:
            strcpy(returnStr, "photo");
            break;
        case rear:
            strcpy(returnStr, "rear");
            break;
        case right:
            strcpy(returnStr, "right");
            break;
        case side:
            strcpy(returnStr, "side");
            break;
        case top:
            strcpy(returnStr, "top");
            break;
        case tray_1:
            strcpy(returnStr, "tray_1");
            break;
        case tray_2:
            strcpy(returnStr, "tray_2");
            break;
        case tray_3:
            strcpy(returnStr, "tray_3");
            break;
        case tray_4:
            strcpy(returnStr, "tray_4");
            break;
        case tray_5:
            strcpy(returnStr, "tray_5");
            break;
        case tray_N:
            strcpy(returnStr, "tray_N");
            break;
        default:
            assert(0);
            break;
    }
    return true;
}

bool PCLmGenerator::getOutputBin(jobOutputBin bin, char *returnStr) {
    if (returnStr) {
        returnStr[0] = '\0';
    }

    switch (bin) {
        case top_output:
            strcpy(returnStr, "top_output");
            break;
        case middle_output:
            strcpy(returnStr, "middle_output");
            break;
        case bottom_output:
            strcpy(returnStr, "bottom_output");
            break;
        case side_output:
            strcpy(returnStr, "side_output");
            break;
        case center_output:
            strcpy(returnStr, "center_output");
            break;
        case rear_output:
            strcpy(returnStr, "rear_output");
            break;
        case face_up:
            strcpy(returnStr, "face_up");
            break;
        case face_down:
            strcpy(returnStr, "face_down");
            break;
        case large_capacity_output:
            strcpy(returnStr, "large_capacity_output");
            break;
        case stacker_N:
            strcpy(returnStr, "stacker_N");
            break;
        case mailbox_N:
            strcpy(returnStr, "mailbox_N");
            break;
        case tray_1_output:
            strcpy(returnStr, "tray_1_output");
            break;
        case tray_2_output:
            strcpy(returnStr, "tray_2_output");
            break;
        case tray_3_output:
            strcpy(returnStr, "tray_3_output");
            break;
        case tray_4_output:
            strcpy(returnStr, "tray_4_output");
            break;
        default:
            assert(0);
            break;
    }
    return true;
}

void PCLmGenerator::writeJobTicket() {
    // Write JobTicket
    char inputBin[256];
    char outputBin[256];

    if (!m_pPCLmSSettings) {
        return;
    }

    getInputBinString(m_pPCLmSSettings->userInputBin, &inputBin[0]);
    getOutputBin(m_pPCLmSSettings->userOutputBin, &outputBin[0]);
    strcpy(inputBin, inputBin);
    strcpy(outputBin, outputBin);

    sprintf(pOutStr, "%%  genPCLm (Ver: %f)\n", PCLM_Ver);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%============= Job Ticket =============\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%  PCLmS-Job-Ticket\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%      job-ticket-version: 0.1\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%      epcl-version: 1.01\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%    JobSection\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%      job-id: xxx\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%    MediaHandlingSection\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%      media-size-name: %s\n", currMediaName);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%      media-type: %s\n", m_pPCLmSSettings->userMediaType);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%      media-source: %s\n", inputBin);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%      sides: xxx\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%      output-bin: %s\n", outputBin);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%    RenderingSection\n");
    writeStr2OutBuff(pOutStr);
    if (currCompressionDisposition == compressDCT) {
        sprintf(pOutStr, "%%      pclm-compression-method: JPEG\n");
        writeStr2OutBuff(pOutStr);
    } else if (currCompressionDisposition == compressFlate) {
        sprintf(pOutStr, "%%      pclm-compression-method: FLATE\n");
        writeStr2OutBuff(pOutStr);
    } else {
        sprintf(pOutStr, "%%      pclm-compression-method: RLE\n");
        writeStr2OutBuff(pOutStr);
    }
    sprintf(pOutStr, "%%      strip-height: %ld\n", currStripHeight);
    writeStr2OutBuff(pOutStr);

    if (destColorSpace == deviceRGB) {
        sprintf(pOutStr, "%%      print-color-mode: deviceRGB\n");
        writeStr2OutBuff(pOutStr);
    } else if (destColorSpace == adobeRGB) {
        sprintf(pOutStr, "%%      print-color-mode: adobeRGB\n");
        writeStr2OutBuff(pOutStr);
    } else if (destColorSpace == grayScale) {
        sprintf(pOutStr, "%%      print-color-mode: gray\n");
        writeStr2OutBuff(pOutStr);
    }

    sprintf(pOutStr, "%%      print-quality: %d\n", m_pPCLmSSettings->userPageQuality);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%      printer-resolution: %d\n", currRenderResolutionInteger);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%      print-content-optimized: xxx\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%      orientation-requested: %d\n", m_pPCLmSSettings->userOrientation);
    writeStr2OutBuff(pOutStr);

    if (PCLmSSettings.userCopies == 0) {
        PCLmSSettings.userCopies = 1;
    }

    sprintf(pOutStr, "%%      copies: %d\n", m_pPCLmSSettings->userCopies);
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%      pclm-raster-back-side: xxx\n");
    writeStr2OutBuff(pOutStr);
    if (currRenderResolutionInteger) {
        sprintf(pOutStr, "%%      margins-pre-applied: TRUE\n");
        writeStr2OutBuff(pOutStr);
    } else {
        sprintf(pOutStr, "%%      margins-pre-applied: FALSE\n");
        writeStr2OutBuff(pOutStr);
    }
    sprintf(pOutStr, "%%  PCLmS-Job-Ticket-End\n");
    writeStr2OutBuff(pOutStr);
}

void PCLmGenerator::writePDFGrammarHeader() {
    // sprintf(pOutStr,"%%============= PCLm: File Header \n"); writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%PDF-1.7\n");
    writeStr2OutBuff(pOutStr);
    sprintf(pOutStr, "%%PCLm 1.0\n");
    writeStr2OutBuff(pOutStr);
}

int PCLmGenerator::RLEEncodeImage(ubyte *in, ubyte *out, int inLength) {
    ubyte *imgPtr = in;
    ubyte *endPtr = in + inLength;
    ubyte *origOut = out;
    ubyte c;
    sint32 cnt = 0;

    while (imgPtr < endPtr) {
        c = *imgPtr++;
        cnt = 1;

        // Figure out how many repeating bytes are in the image
        while (*imgPtr == c && cnt < inLength) {
            if (imgPtr > endPtr) {
                break;
            }
            cnt++;
            imgPtr++;
        }

        if (cnt > 1) {
            /* If cnt > 1, then output repeating byte specification
             * The syntax is "byte-count repeateByte", where byte-count is 257-byte-count.
             * Since the cnt value is a byte, if the repeateCnt is > 128 then we need to put
             * out multiple repeat-blocks (Referred to as method 1) range is 128-256
             */
            while (cnt > 128) {
                *out++ = 129; // i.e. 257-129==128
                *out++ = c;
                cnt -= 128;
            }
            // Now handle the repeats that are < 128
            if (cnt) {
                *out++ = (257 - cnt); // i.e. cnt==2: 257-255=2
                *out++ = c;
            }
        } else {
            /* If cnt==1, then this is a literal run - no repeating bytes found.
             * The syntax is "byte-count literal-run", where byte-count is < 128 and
             * literal-run is the non-repeating bytes of the input stream.
             * Referred to as method 2, range is 0-127
             */
            ubyte *start, *p;
            sint32 i;
            start = (imgPtr - 1);  // The first byte of the literal run

            // Now find the end of the literal run
            for (cnt = 1, p = start; *p != *imgPtr; p++, imgPtr++, cnt++) {
                if (imgPtr >= endPtr) break;
            }
            if (!(imgPtr == endPtr)) {
                imgPtr--;
                // imgPtr incremented 1 too many
            }
            cnt--;
            // Blocks of literal bytes can't exceed 128 bytes, so output multiple
            //    literal-run blocks if > 128
            while (cnt > 128) {
                *out++ = 127;
                for (i = 0; i < 128; i++) {
                    *out++ = *start++;
                }
                cnt -= 128;
            }
            // Now output the leftover literal run
            *out++ = cnt - 1;
            for (i = 0; i < cnt; i++) {
                *out++ = *start++;
            }
        }
    }
    // Now, write the end-of-compression marker (byte 128) into the output stream
    *out++ = 128;
    // Return the compressed size
    return ((int) (out - origOut));
}

PCLmGenerator::PCLmGenerator() {
    strcpy(currMediaName, "LETTER");
    currDuplexDisposition = simplex;
    currCompressionDisposition = compressDCT;
    currMediaOrientationDisposition = portraitOrientation;
    currRenderResolution = res600;
    currStripHeight = STRIP_HEIGHT;

    // Default media h/w to letter specification
    mediaWidthInPixels = 0;
    mediaHeightInPixels = 0;
    mediaWidth = 612;
    mediaHeight = 792;
    destColorSpace = deviceRGB;
    sourceColorSpace = deviceRGB;
    scaleFactor = 1;
    jobOpen = job_closed;
    scratchBuffer = NULL;
    pageCount = 0;

    currRenderResolutionInteger = 600;
    STANDARD_SCALE = (float) currRenderResolutionInteger / (float) STANDARD_SCALE_FOR_PDF;
    yPosition = 0;
    numKids = 0;

    // XRefTable storage
    xRefIndex = 0;
    xRefStart = 0;

    objCounter = PAGES_OBJ_NUMBER + 1;
    totalBytesWrittenToPCLmFile = 0;

    // Initialize first index in xRefTable
    xRefTable = NULL;
    KidsArray = NULL;

    // Initialize the output Buffer
    allocatedOutputBuffer = NULL;

    // Initialize the leftover scanline logic
    leftoverScanlineBuffer = 0;

    adobeRGBCS_firstTime = true;
    mirrorBackside = true;

    topMarginInPix = 0;
    leftMarginInPix = 0;
    m_pPCLmSSettings = NULL;
}

PCLmGenerator::~PCLmGenerator() {
    Cleanup();
}

int PCLmGenerator::StartJob(void **pOutBuffer, int *iOutBufferSize) {
    /* Allocate the output buffer; we don't know much at this point, so make the output buffer size
     * the worst case dimensions; when we get a startPage, we will resize it appropriately
     */
    outBuffSize = DEFAULT_OUTBUFF_SIZE;
    *iOutBufferSize = outBuffSize;
    *pOutBuffer = (ubyte *) malloc(outBuffSize); // This multipliy by 10 needs to be removed...

    if (NULL == *pOutBuffer) {
        return (errorOutAndCleanUp());
    }

    currOutBuffSize = outBuffSize;

    if (NULL == *pOutBuffer) {
        return (errorOutAndCleanUp());
    }

    allocatedOutputBuffer = *pOutBuffer;
    initOutBuff((char *) *pOutBuffer, outBuffSize);
    writePDFGrammarHeader();
    *iOutBufferSize = totalBytesWrittenToCurrBuff;
    jobOpen = job_open;

    return success;
}

int PCLmGenerator::EndJob(void **pOutBuffer, int *iOutBufferSize) {
    if (NULL == allocatedOutputBuffer) {
        return (errorOutAndCleanUp());
    }

    *pOutBuffer = allocatedOutputBuffer;

    initOutBuff((char *) *pOutBuffer, outBuffSize);

    // Write PDF trailer
    writePDFGrammarTrailer(currSourceWidth, currSourceHeight);

    *iOutBufferSize = totalBytesWrittenToCurrBuff;

    jobOpen = job_closed;

    if (xRefTable) {
        free(xRefTable);
        xRefTable = NULL;
    }
    if (KidsArray) {
        free(KidsArray);
        KidsArray = NULL;
    }

    return success;
}

int PCLmGenerator::StartPage(PCLmPageSetup *PCLmPageContent, void **pOutBuffer,
        int *iOutBufferSize) {
    int numImageStrips;
    // Save the resolution information
    currRenderResolution = PCLmPageContent->destinationResolution;

    *pOutBuffer = allocatedOutputBuffer;

    if (currRenderResolution == res300) {
        currRenderResolutionInteger = 300;
    } else if (currRenderResolution == res600) {
        currRenderResolutionInteger = 600;
    } else if (currRenderResolution == res1200) {
        currRenderResolutionInteger = 1200;
    } else {
        assert(0);
    }

    // Recalculate STANDARD_SCALE to reflect the job resolution
    STANDARD_SCALE = (float) currRenderResolutionInteger / (float) STANDARD_SCALE_FOR_PDF;

    // Use the values set by the caller
    currSourceWidth = PCLmPageContent->SourceWidthPixels;
    currSourceHeight = PCLmPageContent->SourceHeightPixels;

    // Save off the media information
    mediaWidth = (int) (PCLmPageContent->mediaWidth);
    mediaHeight = (int) (PCLmPageContent->mediaHeight);

    // Use the values set by the caller
    mediaWidthInPixels = PCLmPageContent->mediaWidthInPixels;
    mediaHeightInPixels = PCLmPageContent->mediaHeightInPixels;

    topMarginInPix = (int) (((PCLmPageContent->mediaHeightOffset / STANDARD_SCALE_FOR_PDF) *
            currRenderResolutionInteger) + 0.50);
    leftMarginInPix = (int) (((PCLmPageContent->mediaWidthOffset / STANDARD_SCALE_FOR_PDF) *
            currRenderResolutionInteger) + 0.50);

    if (topMarginInPix % 16) {
        // Round to nearest 16 scanline boundary to ensure decompressability.
        int i = topMarginInPix % 16;
        if (i < (16 / 2)) {
            topMarginInPix -= i;
        } else {
            topMarginInPix += (16 - i);
        }
    }

    if (leftMarginInPix % 16) {
        // Round to nearest 16 scanline boundary to ensure decompressability.
        int i = leftMarginInPix % 16;
        if (i < (16 / 2)) {
            leftMarginInPix -= i;
        } else {
            leftMarginInPix += (16 - i);
        }
    }

    currCompressionDisposition = PCLmPageContent->compTypeRequested;

    if (strlen(PCLmPageContent->mediaSizeName)) {
        strcpy(currMediaName, PCLmPageContent->mediaSizeName);
    }

    currStripHeight = PCLmPageContent->stripHeight;
    if (!currStripHeight) {
        numImageStrips = 1;
        currStripHeight = currSourceHeight;
    } else {
        // Need to know how many strips will be inserted into PDF file
        float numImageStripsReal = ceil((float) currSourceHeight / (float) currStripHeight);
        numImageStrips = (int) numImageStripsReal;
    }

    if (PCLmPageContent->srcColorSpaceSpefication == grayScale) {
        srcNumComponents = 1;
    } else {
        srcNumComponents = 3;
    }

    if (PCLmPageContent->dstColorSpaceSpefication == grayScale) {
        dstNumComponents = 1;
    } else {
        dstNumComponents = 3;
    }

    currDuplexDisposition = PCLmPageContent->duplexDisposition;

    destColorSpace = PCLmPageContent->dstColorSpaceSpefication;

    // Calculate how large the output buffer needs to be based upon the page specifications
    int tmp_outBuffSize = mediaWidthInPixels * currStripHeight * dstNumComponents;

    if (tmp_outBuffSize > currOutBuffSize) {
        // Realloc the pOutBuffer to the correct size
        *pOutBuffer = realloc(*pOutBuffer, tmp_outBuffSize);

        if (*pOutBuffer == NULL) {
            // realloc failed and prev buffer not freed
            return errorOutAndCleanUp();
        }

        outBuffSize = currOutBuffSize = tmp_outBuffSize;
        allocatedOutputBuffer = *pOutBuffer;
        if (NULL == allocatedOutputBuffer) {
            return (errorOutAndCleanUp());
        }
    }

    initOutBuff((char *) *pOutBuffer, outBuffSize);

    // Keep track of the page count
    pageCount++;

    // If we are on a backside and doing duplex, prep for reverse strip order
    if (currDuplexDisposition == duplex_longEdge && !(pageCount % 2) && mirrorBackside) {
        reverseOrder = true;
    } else {
        reverseOrder = false;
    }

    // Calculate the number of injected strips, if any
    if (topMarginInPix) {
        if (topMarginInPix <= currStripHeight) {
            numFullInjectedStrips = 1;
            numFullScanlinesToInject = topMarginInPix;
            numPartialScanlinesToInject = 0;
        } else {
            numFullInjectedStrips = topMarginInPix / currStripHeight;
            numFullScanlinesToInject = currStripHeight;
            numPartialScanlinesToInject =
                    topMarginInPix - (numFullInjectedStrips * currStripHeight);
        }
    }

    writeJobTicket();
    writePDFGrammarPage(mediaWidthInPixels, mediaHeightInPixels, numImageStrips, destColorSpace);
    *iOutBufferSize = totalBytesWrittenToCurrBuff;

    if (!scratchBuffer) {
        // We need to pad the scratchBuffer size to allow for compression expansion (RLE can create
        // compressed segments that are slightly larger than the source.
        size_t len = (size_t) currStripHeight * mediaWidthInPixels * srcNumComponents * 2;
        scratchBuffer = (ubyte *) malloc(len);
        if (!scratchBuffer) {
            return errorOutAndCleanUp();
        }
    }

    mirrorBackside = PCLmPageContent->mirrorBackside;
    firstStrip = true;

    return success;
}

int PCLmGenerator::EndPage(void **pOutBuffer, int *iOutBufferSize) {
    *pOutBuffer = allocatedOutputBuffer;
    initOutBuff((char *) *pOutBuffer, outBuffSize);
    *iOutBufferSize = totalBytesWrittenToCurrBuff;

    // Free up the scratchbuffer at endpage, to allow the next page to have a different size
    if (scratchBuffer) {
        free(scratchBuffer);
        scratchBuffer = NULL;
    }

    return success;
}

int PCLmGenerator::Encapsulate(void *pInBuffer, int inBufferSize, int thisHeight,
        void **pOutBuffer, int *iOutBufferSize) {
    int numCompBytes;
    int scanlineWidth = mediaWidthInPixels * srcNumComponents;
    int numLinesThisCall = thisHeight;
    void *savedInBufferPtr = NULL;
    void *tmpBuffer = NULL;
    void *localInBuffer;
    ubyte *newStripPtr = NULL;

    if (leftoverScanlineBuffer) {
        ubyte *whereAreWe;
        sint32 scanlinesThisTime;
        // The leftover scanlines have already been processed (color-converted and flipped), so
        // just put them into the output buffer.

        // Allocate a temporary buffer to copy leftover and new data into
        tmpBuffer = malloc(scanlineWidth * currStripHeight);
        if (!tmpBuffer) {
            return (errorOutAndCleanUp());
        }

        // Copy leftover scanlines into tmpBuffer
        memcpy(tmpBuffer, leftoverScanlineBuffer, scanlineWidth * numLeftoverScanlines);

        whereAreWe = (ubyte *) tmpBuffer + (scanlineWidth * numLeftoverScanlines);

        scanlinesThisTime = currStripHeight - numLeftoverScanlines;

        // Copy enough scanlines from the real inBuffer to fill out the tmpBuffer
        memcpy(whereAreWe, pInBuffer, scanlinesThisTime * scanlineWidth);

        // Now copy the remaining scanlines from pInBuffer to the leftoverBuffer
        numLeftoverScanlines = thisHeight - scanlinesThisTime;
        assert(leftoverScanlineBuffer);
        whereAreWe = (ubyte *) pInBuffer + (scanlineWidth * numLeftoverScanlines);
        memcpy(leftoverScanlineBuffer, whereAreWe, scanlineWidth * numLeftoverScanlines);
        numLinesThisCall = thisHeight = currStripHeight;

        savedInBufferPtr = pInBuffer;
        localInBuffer = tmpBuffer;
    } else {
        localInBuffer = pInBuffer;
    }

    if (thisHeight > currStripHeight) {
        // Copy raw raster into leftoverScanlineBuffer
        ubyte *ptr;
        numLeftoverScanlines = thisHeight - currStripHeight;
        leftoverScanlineBuffer = malloc(scanlineWidth * numLeftoverScanlines);
        if (!leftoverScanlineBuffer) {
            return (errorOutAndCleanUp());
        }
        ptr = (ubyte *) localInBuffer + scanlineWidth * numLeftoverScanlines;
        memcpy(leftoverScanlineBuffer, ptr, scanlineWidth * numLeftoverScanlines);
        thisHeight = currStripHeight;
    }

    if (NULL == allocatedOutputBuffer) {
        if (tmpBuffer) {
            free(tmpBuffer);
        }
        return (errorOutAndCleanUp());
    }
    *pOutBuffer = allocatedOutputBuffer;
    initOutBuff((char *) *pOutBuffer, outBuffSize);

    if (currDuplexDisposition == duplex_longEdge && !(pageCount % 2)) {
        if (mirrorBackside) {
            prepImageForBacksideDuplex((ubyte *) localInBuffer, numLinesThisCall, currSourceWidth,
                    srcNumComponents);
        }
    }

    if (destColorSpace == grayScale &&
            (sourceColorSpace == deviceRGB || sourceColorSpace == adobeRGB)) {
        colorConvertSource(sourceColorSpace, grayScale, (ubyte *) localInBuffer, currSourceWidth,
                numLinesThisCall);
        // Adjust the scanline width accordingly
        scanlineWidth = mediaWidthInPixels * dstNumComponents;
    }

    if (leftMarginInPix) {
        newStripPtr = shiftStripByLeftMargin((ubyte *) localInBuffer, currSourceWidth,
                currStripHeight, numLinesThisCall, mediaWidthInPixels, leftMarginInPix,
                destColorSpace);
    }

    bool whiteStrip = false;
#ifdef SUPPORT_WHITE_STRIPS
    if (!firstStrip) {
        // PCLm does not print a blank page if all the strips are marked as "/Name /WhiteStrip"
        // so only apply /WhiteStrip to strips after the first
        whiteStrip = isWhiteStrip(pInBuffer, thisHeight * currSourceWidth * srcNumComponents);
    }
#endif

    if (currCompressionDisposition == compressDCT) {
        if (firstStrip && topMarginInPix) {
            ubyte whitePt = 0xff;

            ubyte *tmpStrip = (ubyte *) malloc(scanlineWidth * topMarginInPix);
            memset(tmpStrip, whitePt, scanlineWidth * topMarginInPix);

            for (sint32 stripCntr = 0; stripCntr < numFullInjectedStrips; stripCntr++) {
                write_JPEG_Buff(scratchBuffer, JPEG_QUALITY, mediaWidthInPixels,
                        (sint32) numFullScanlinesToInject, tmpStrip, currRenderResolutionInteger,
                        destColorSpace, &numCompBytes);
                injectJPEG((char *) scratchBuffer, mediaWidthInPixels,
                        (sint32) numFullScanlinesToInject, numCompBytes, destColorSpace, true);
            }

            if (numPartialScanlinesToInject) {
                // Handle the leftover strip
                write_JPEG_Buff(scratchBuffer, JPEG_QUALITY, mediaWidthInPixels,
                        numPartialScanlinesToInject, tmpStrip, currRenderResolutionInteger,
                        destColorSpace, &numCompBytes);
                injectJPEG((char *) scratchBuffer, mediaWidthInPixels, numPartialScanlinesToInject,
                        numCompBytes, destColorSpace, true);
            }

            free(tmpStrip);
        }
        firstStrip = false;

        // We are always going to compress the full strip height, even though the image may be less;
        // this allows the compressed images to be symmetric
        if (numLinesThisCall < currStripHeight) {
            sint32 numLeftoverBytes = (currStripHeight - numLinesThisCall) * currSourceWidth * 3;
            sint32 numImagedBytes = numLinesThisCall * currSourceWidth * 3;

            // End-of-page: we have to white-out the unused section of the source image
            memset((ubyte *) localInBuffer + numImagedBytes, 0xff, numLeftoverBytes);
        }

        if (newStripPtr) {
            write_JPEG_Buff(scratchBuffer, JPEG_QUALITY, mediaWidthInPixels, currStripHeight,
                    newStripPtr, currRenderResolutionInteger, destColorSpace, &numCompBytes);

            free(newStripPtr);
            newStripPtr = NULL;
        } else {
            write_JPEG_Buff(scratchBuffer, JPEG_QUALITY, mediaWidthInPixels, currStripHeight,
                    (JSAMPLE *) localInBuffer, currRenderResolutionInteger, destColorSpace,
                    &numCompBytes);
        }

        injectJPEG((char *) scratchBuffer, mediaWidthInPixels, currStripHeight, numCompBytes,
                destColorSpace, whiteStrip);
    } else if (currCompressionDisposition == compressFlate) {
        uint32 len = numLinesThisCall * scanlineWidth;
        uLongf destSize = len;
        int result;

        if (firstStrip && topMarginInPix) {
            ubyte whitePt = 0xff;

            // We need to inject a blank image-strip with a height==topMarginInPix
            ubyte *tmpStrip = (ubyte *) malloc(scanlineWidth * topMarginInPix);
            uLongf tmpDestSize = destSize;
            memset(tmpStrip, whitePt, scanlineWidth * topMarginInPix);

            for (sint32 stripCntr = 0; stripCntr < numFullInjectedStrips; stripCntr++) {
                result = compress(scratchBuffer, &tmpDestSize, (const Bytef *) tmpStrip,
                        scanlineWidth * numFullScanlinesToInject);
                injectLZStrip(scratchBuffer, tmpDestSize, mediaWidthInPixels,
                        numFullScanlinesToInject, destColorSpace, true);
            }
            if (numPartialScanlinesToInject) {
                result = compress(scratchBuffer, &tmpDestSize, (const Bytef *) tmpStrip,
                        scanlineWidth * numPartialScanlinesToInject);
                injectLZStrip(scratchBuffer, tmpDestSize, mediaWidthInPixels,
                        numPartialScanlinesToInject, destColorSpace, true);
            }
            free(tmpStrip);
        }
        firstStrip = false;

        if (newStripPtr) {
            result = compress(scratchBuffer, &destSize, (const Bytef *) newStripPtr,
                    scanlineWidth * numLinesThisCall);
            free(newStripPtr);
            newStripPtr = NULL;
        } else {
            // Dump the source data
            result = compress(scratchBuffer, &destSize, (const Bytef *) localInBuffer,
                    scanlineWidth * numLinesThisCall);
        }
        injectLZStrip(scratchBuffer, destSize, mediaWidthInPixels, numLinesThisCall, destColorSpace,
                whiteStrip);
    } else if (currCompressionDisposition == compressRLE) {
        int compSize;
        if (firstStrip && topMarginInPix) {
            ubyte whitePt = 0xff;

            // We need to inject a blank image-strip with a height==topMarginInPix

            ubyte *tmpStrip = (ubyte *) malloc(scanlineWidth * topMarginInPix);
            memset(tmpStrip, whitePt, scanlineWidth * topMarginInPix);

            for (sint32 stripCntr = 0; stripCntr < numFullInjectedStrips; stripCntr++) {
                compSize = RLEEncodeImage(tmpStrip, scratchBuffer,
                        scanlineWidth * numFullScanlinesToInject);
                injectRLEStrip(scratchBuffer, compSize, mediaWidthInPixels,
                        numFullScanlinesToInject, destColorSpace, true);
            }

            if (numPartialScanlinesToInject) {
                compSize = RLEEncodeImage(tmpStrip, scratchBuffer,
                        scanlineWidth * numPartialScanlinesToInject);
                injectRLEStrip(scratchBuffer, compSize, mediaWidthInPixels,
                        numPartialScanlinesToInject, destColorSpace, true);
            }

            free(tmpStrip);
        }
        firstStrip = false;

        if (newStripPtr) {
            compSize = RLEEncodeImage(newStripPtr, scratchBuffer,
                    scanlineWidth * numLinesThisCall);
            free(newStripPtr);
            newStripPtr = NULL;
        } else {
            compSize = RLEEncodeImage((ubyte *) localInBuffer, scratchBuffer,
                    scanlineWidth * numLinesThisCall);
        }

        injectRLEStrip(scratchBuffer, compSize, mediaWidthInPixels, numLinesThisCall,
                destColorSpace, whiteStrip);
    } else {
        assert(0);
    }

    *iOutBufferSize = totalBytesWrittenToCurrBuff;

    if (savedInBufferPtr) {
        pInBuffer = savedInBufferPtr;
    }

    if (tmpBuffer) {
        free(tmpBuffer);
    }

    if (newStripPtr) {
        free(newStripPtr);
    }

    return success;
}

int PCLmGenerator::GetPclmMediaDimensions(const char *mediaRequested,
        PCLmPageSetup *myPageInfo) {
    int i = 0;
    int result = 99;

    int iRenderResolutionInteger = 0;
    if (myPageInfo->destinationResolution == res300) {
        iRenderResolutionInteger = 300;
    } else if (myPageInfo->destinationResolution == res600) {
        iRenderResolutionInteger = 600;
    } else if (myPageInfo->destinationResolution == res1200) {
        iRenderResolutionInteger = 1200;
    } else {
        assert(0);
    }

    for (i = 0; i < SUPPORTED_MEDIA_SIZE_COUNT; i++) {
        if (strcasecmp(mediaRequested, SupportedMediaSizes[i].PCL6Name) == 0) {
            myPageInfo->mediaWidth = floorf(
                    _MI_TO_POINTS(SupportedMediaSizes[i].WidthInInches));
            myPageInfo->mediaHeight = floorf(
                    _MI_TO_POINTS(SupportedMediaSizes[i].HeightInInches));
            myPageInfo->mediaWidthInPixels = floorf(
                    _MI_TO_PIXELS(SupportedMediaSizes[i].WidthInInches,
                            iRenderResolutionInteger));
            myPageInfo->mediaHeightInPixels = floorf(
                    _MI_TO_PIXELS(SupportedMediaSizes[i].HeightInInches,
                            iRenderResolutionInteger));
            result = i;
            break;  // we found a match, so break out of loop
        }
    }

    if (i == SUPPORTED_MEDIA_SIZE_COUNT) {
        // media size not found, defaulting to letter
        printf("PCLmGenerator get_pclm_media_size(): media size, %s, NOT FOUND, setting to letter",
                mediaRequested);
        result = GetPclmMediaDimensions("LETTER", myPageInfo);
    }

    return result;
}

void PCLmGenerator::FreeBuffer(void *pBuffer) {
    if (jobOpen == job_closed && pBuffer) {
        if (pBuffer == allocatedOutputBuffer) {
            allocatedOutputBuffer = NULL;
        }
        free(pBuffer);
    }
}
