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

#ifndef _PCLM_GENERATOR
#define _PCLM_GENERATOR
#define SUPPORT_WHITE_STRIPS

#include "common_defines.h"

/*
 * Generates a stream of PCLm output.
 *
 * Public APIs supply data output data into pOutBuffer with length in iOutBufferSize, to be
 * delivered to the printer.
 */
class PCLmGenerator {
public:

    PCLmGenerator();

    ~PCLmGenerator();

    /*
     * Started a PCLm job. Initializes buffers.
     */
    int StartJob(void **pOutBuffer, int *iOutBufferSize);

    /*
     * Ends the PCLm job. Writes trailer, frees buffers and arrays
     */
    int EndJob(void **pOutBuffer, int *iOutBufferSize);

    /*
     * Starts rendering a page of a PCLm job.
     */
    int StartPage(PCLmPageSetup *PCLmPageContent, void **pOutBuffer, int *iOutBufferSize);

    /*
     * Ends rendering a page. Frees scratch buffer.
     */
    int EndPage(void **pOutBuffer, int *iOutBufferSize);

    /*
     * Compresses output buffer in Flate, RLE, or JPEG compression
     */
    int Encapsulate(void *pInBuffer, int inBufferSize, int numLines, void **pOutBuffer,
            int *iOutBufferSize);

    /*
     * Returns index of matched media size, else returns index for letter
     */
    int GetPclmMediaDimensions(const char *mediaRequested, PCLmPageSetup *myPageInfo);

    /*
     * Free the supplied output buffer (after EndJob)
     */
    void FreeBuffer(void *pBuffer);

private:
    /*
     * Convert an image from one color space to another.
     * Currently, only supports RGB->GRAY
     */
    bool colorConvertSource(colorSpaceDisposition srcCS, colorSpaceDisposition dstCS, ubyte *strip,
            sint32 stripWidth, sint32 stripHeight);

    /*
     * Generates the PDF page construct(s), which includes the image information. The /Length
     * definition is required for PDF, so we write the stream to a RAM buffer first, then calculate
     * the Buffer size, insert the PDF /Length construct, then write the buffer to the PDF file.
     */
    void writePDFGrammarPage
            (int imageWidth, int imageHeight, int numStrips, colorSpaceDisposition destColorSpace);

    /*
     * Writes the PDF and PCLm versions to the output buffer as the header
     */
    void writePDFGrammarHeader();

    /*
     * Injects RLE compression strip into the output buffer
     */
    int injectRLEStrip(ubyte *RLEBuffer, int numBytes, int imageWidth, int imageHeight,
            colorSpaceDisposition destColorSpace, bool);

    /*
     * Injects zlib compressed strip to the output buffer
     */
    int injectLZStrip(ubyte *LZBuffer, int numBytes, int imageWidth, int imageHeight,
            colorSpaceDisposition destColorSpace, bool);

    /*
     * Injects jpeg compressed image to the output buffer
     */
    int injectJPEG(char *jpeg_Buff, int imageWidth, int imageHeight, int numCompBytes,
            colorSpaceDisposition destColorSpace, bool);

    /*
     * Initializes the output buffer with buff and size
     */
    void initOutBuff(char *buff, sint32 size);

    /*
     * Writes str to the outputBuffer
     */
    void writeStr2OutBuff(char *str);

    /*
     * Writes buff to the outputBuffer
     */
    void write2Buff(ubyte *buff, int buffSize);

    /*
     * Adds totalBytesWrittenToPCLmFile to the xRefTable for output
     */
    int statOutputFileSize();

    /*
     * Writes file information to the outputbuffer as the trailer.
     */
    void writePDFGrammarTrailer(int imageWidth, int imageHeight);

    /*
     * Injects Adobe RGBCS into the output buffer
     */
    bool injectAdobeRGBCS();

    /*
     * Adds kidObj to KidsArray
     */
    bool addKids(sint32 kidObj);

    /*
     * Adds xRefObj to the xRefTable
     */
    bool addXRef(sint32 xRefObj);

    /*
     * Warning: take extreme care in modifying this unless you understand what is going on. This
     * function attempts to fix the xref table, based upon the strips getting inserted in reverse
     * order (on the backside page). It does the following:
     *   1) Calculates the new object reference size (using tmpArray)
     *   2) Adds 2 to the object size to compensate for the offset
     *   3) Reorders the Image FileBody and the ImageTransformation, as these are 1 PDF object
     *   4) Frees the tmp array
     */
    void fixXRef();

    /*
     * Calls cleanup and returns an error
     */
    int errorOutAndCleanUp();

    /*
     * Cleans up allocatedOutputBuffer, leftoverScanlineBuffer, scratchBuffer, xRefTable, and
     * KidsArray
     */
    void Cleanup(void);

    /*
     * Writes job information to the output buffer
     */
    void writeJobTicket(void);

    /*
     * Transforms image for duplexing, writes to output buffer
     */
    void injectImageTransform();

#ifdef SUPPORT_WHITE_STRIPS

    /*
     * Checks if the given buffer is a white strip
     */
    bool isWhiteStrip(void *, int);

#endif

    /*
     * Outputs the string associated with the given bin into returnStr
     */
    bool getInputBinString(jobInputBin bin, char *);

    /*
     * Outputs the string associated with the given bin into returnStr
     */
    bool getOutputBin(jobOutputBin bin, char *);

    /*
     * compress input by identifying repeating bytes (not sequences)
     * Compression ratio good for grayscale images, not great on RGB
     * Output:
     *     1-127:   literal run
     *     128:     end of compression block
     *     129-256: repeating byte sequence
     */
    int RLEEncodeImage(ubyte *in, ubyte *out, int inLength);

    sint32 currStripHeight;
    char currMediaName[256];
    duplexDispositionEnum currDuplexDisposition;
    compressionDisposition currCompressionDisposition;
    mediaOrientationDisposition currMediaOrientationDisposition;
    renderResolution currRenderResolution;
    int currRenderResolutionInteger;
    void *allocatedOutputBuffer;
    void *leftoverScanlineBuffer;

    int mediaWidth;
    int mediaHeight;
    int mediaWidthInPixels;
    int mediaHeightInPixels;
    colorSpaceDisposition destColorSpace;
    colorSpaceDisposition sourceColorSpace;
    int scaleFactor;
    jobStateEnum jobOpen;
    int currSourceWidth;
    int currSourceHeight;
    int srcNumComponents;
    int dstNumComponents;
    int numLeftoverScanlines;
    ubyte *scratchBuffer;
    int pageCount;
    bool reverseOrder;
    int outBuffSize;
    int currOutBuffSize;
    int totalBytesWrittenToPCLmFile;
    int totalBytesWrittenToCurrBuff;
    char *outBuffPtr;
    char *currBuffPtr;
    float STANDARD_SCALE;
    sint32 objCounter;

    sint32 yPosition;
    sint32 pageOrigin;
    sint32 *KidsArray;
    sint32 numKids;

    // XRefTable storage
    sint32 *xRefTable;
    sint32 xRefIndex;
    sint32 xRefStart;
    char pOutStr[256];
    bool adobeRGBCS_firstTime;
    bool mirrorBackside;
    sint32 topMarginInPix;
    sint32 leftMarginInPix;
    bool firstStrip;
    sint32 numFullInjectedStrips;
    sint32 numFullScanlinesToInject;
    sint32 numPartialScanlinesToInject;

    PCLmSUserSettingsType *m_pPCLmSSettings;
};

#endif // _PCLM_PARSER_