/*
 * Copyright (C) 2016 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "DngValidateCamera"
#include <log/log.h>
#include <jni.h>

#include <string>
#include <sstream>
#include <iostream>

/**
 * Use DNG SDK to validate captured DNG file.
 *
 * This code is largely based on the dng_validate.cpp implementation included
 * with the DNG SDK. The portions of this file that are from the DNG SDK are
 * covered by the the DNG SDK license in /external/dng_sdk/LICENSE
 */

#include "dng_color_space.h"
#include "dng_date_time.h"
#include "dng_exceptions.h"
#include "dng_file_stream.h"
#include "dng_globals.h"
#include "dng_host.h"
#include "dng_ifd.h"
#include "dng_image_writer.h"
#include "dng_info.h"
#include "dng_linearization_info.h"
#include "dng_mosaic_info.h"
#include "dng_negative.h"
#include "dng_preview.h"
#include "dng_render.h"
#include "dng_simple_image.h"
#include "dng_tag_codes.h"
#include "dng_tag_types.h"
#include "dng_tag_values.h"

// Version of DNG validate referenced for this implementation
#define kDNGValidateVersion "1.4"

static bool gFourColorBayer = false;

static int32 gMosaicPlane = -1;

static uint32 gPreferredSize = 0;
static uint32 gMinimumSize   = 0;
static uint32 gMaximumSize   = 0;

static uint32 gProxyDNGSize = 0;

static const dng_color_space *gFinalSpace = &dng_space_sRGB::Get();

static uint32 gFinalPixelType = ttByte;

static dng_string gDumpStage1;
static dng_string gDumpStage2;
static dng_string gDumpStage3;
static dng_string gDumpTIF;
static dng_string gDumpDNG;

/**
 * Validate DNG file in provided buffer.
 *
 * Returns dng_error_none (0) on success, otherwise one of the
 * dng_error_code enum values is returned.
 *
 * Warnings and errors found during validation are printed to stderr
 */
static dng_error_code dng_validate(const void* data, uint32_t count) {

    ALOGI("Validating DNG buffer");

    try {
        dng_stream stream(data, count);

        dng_host host;

        host.SetPreferredSize(gPreferredSize);
        host.SetMinimumSize(gMinimumSize);
        host.SetMaximumSize(gMaximumSize);

        host.ValidateSizes();

        if (host.MinimumSize()) {
            host.SetForPreview(true);
            gDumpDNG.Clear();
        }

        if (gDumpDNG.NotEmpty()) {
            host.SetSaveDNGVersion(dngVersion_SaveDefault);
            host.SetSaveLinearDNG(false);
            host.SetKeepOriginalFile(false);
        }

        // Read into the negative.

        AutoPtr<dng_negative> negative;
        {
            dng_info info;
            info.Parse(host, stream);
            info.PostParse(host);
            if (!info.IsValidDNG()) {
                return dng_error_bad_format;
            }

            negative.Reset(host.Make_dng_negative());
            negative->Parse(host, stream, info);
            negative->PostParse(host, stream, info);

            {
                dng_timer timer("Raw image read time");
                negative->ReadStage1Image(host, stream, info);
            }

            if (info.fMaskIndex != -1) {
                dng_timer timer("Transparency mask read time");
                negative->ReadTransparencyMask(host, stream, info);
            }

            negative->ValidateRawImageDigest(host);
        }

        // Option to write stage 1 image.

        if (gDumpStage1.NotEmpty()) {
            dng_file_stream stream2 (gDumpStage1.Get(), true);
            const dng_image &stage1 = *negative->Stage1Image();
            dng_image_writer writer;

            writer.WriteTIFF(host,
                    stream2,
                    stage1,
                    stage1.Planes() >= 3 ? piRGB
                    : piBlackIsZero);

            gDumpStage1.Clear();
        }

        // Metadata.

        negative->SynchronizeMetadata();

        // Four color Bayer option.

        if (gFourColorBayer) {
            negative->SetFourColorBayer();
        }

        // Build stage 2 image.

        {
            dng_timer timer("Linearization time");
            negative->BuildStage2Image(host);
        }

        if (gDumpStage2.NotEmpty()) {
            dng_file_stream stream2(gDumpStage2.Get(), true);
            const dng_image &stage2 = *negative->Stage2Image();
            dng_image_writer writer;

            writer.WriteTIFF (host,
                    stream2,
                    stage2,
                    stage2.Planes() >= 3 ? piRGB
                    : piBlackIsZero);

            gDumpStage2.Clear();
        }

        // Build stage 3 image.

        {
            dng_timer timer("Interpolate time");
            negative->BuildStage3Image(host,
                    gMosaicPlane);
        }

        // Convert to proxy, if requested.

        if (gProxyDNGSize) {
            dng_timer timer("ConvertToProxy time");
            dng_image_writer writer;

            negative->ConvertToProxy(host,
                    writer,
                    gProxyDNGSize);
        }

        // Flatten transparency, if required.

        if (negative->NeedFlattenTransparency(host)) {
            dng_timer timer("FlattenTransparency time");
            negative->FlattenTransparency(host);
        }

        if (gDumpStage3.NotEmpty()) {
            dng_file_stream stream2(gDumpStage3.Get(), true);
            const dng_image &stage3 = *negative->Stage3Image();
            dng_image_writer writer;

            writer.WriteTIFF (host,
                    stream2,
                    stage3,
                    stage3.Planes () >= 3 ? piRGB
                    : piBlackIsZero);

            gDumpStage3.Clear();
        }

        // Output DNG file if requested.

        if (gDumpDNG.NotEmpty()) {
            // Build the preview list.
            dng_preview_list previewList;
            dng_date_time_info dateTimeInfo;
            CurrentDateTimeAndZone(dateTimeInfo);

            for (uint32 previewIndex = 0; previewIndex < 2; previewIndex++) {

                // Skip preview if writing a compresssed main image to save space
                // in this example code.
                if (negative->RawJPEGImage() != NULL && previewIndex > 0) {
                    break;
                }

                // Report timing.
                dng_timer timer(previewIndex == 0 ? "Build thumbnail time"
                        : "Build preview time");

                // Render a preview sized image.
                AutoPtr<dng_image> previewImage;

                {
                    dng_render render (host, *negative);
                    render.SetFinalSpace (negative->IsMonochrome() ?
                            dng_space_GrayGamma22::Get() : dng_space_sRGB::Get());
                    render.SetFinalPixelType (ttByte);
                    render.SetMaximumSize (previewIndex == 0 ? 256 : 1024);

                    previewImage.Reset (render.Render());
                }

                // Don't write the preview if it is same size as thumbnail.

                if (previewIndex > 0 &&
                        Max_uint32(previewImage->Bounds().W(),
                                previewImage->Bounds().H()) <= 256) {
                    break;
                }

                // If we have compressed JPEG data, create a compressed thumbnail.  Otherwise
                // save a uncompressed thumbnail.
                bool useCompressedPreview = (negative->RawJPEGImage() != NULL) ||
                        (previewIndex > 0);

                AutoPtr<dng_preview> preview (useCompressedPreview ?
                        (dng_preview *) new dng_jpeg_preview :
                        (dng_preview *) new dng_image_preview);

                // Setup up preview info.

                preview->fInfo.fApplicationName.Set("dng_validate");
                preview->fInfo.fApplicationVersion.Set(kDNGValidateVersion);

                preview->fInfo.fSettingsName.Set("Default");

                preview->fInfo.fColorSpace = previewImage->Planes() == 1 ?
                        previewColorSpace_GrayGamma22 :
                        previewColorSpace_sRGB;

                preview->fInfo.fDateTime = dateTimeInfo.Encode_ISO_8601();

                if (!useCompressedPreview) {
                    dng_image_preview *imagePreview = static_cast<dng_image_preview *>(preview.Get());
                    imagePreview->fImage.Reset(previewImage.Release());
                } else {
                    dng_jpeg_preview *jpegPreview = static_cast<dng_jpeg_preview *>(preview.Get());
                    int32 quality = (previewIndex == 0 ? 8 : 5);
                    dng_image_writer writer;
                    writer.EncodeJPEGPreview (host,
                            *previewImage,
                            *jpegPreview,
                            quality);
                }
                previewList.Append (preview);
            }

            // Write DNG file.

            dng_file_stream stream2(gDumpDNG.Get(), true);

            {
                dng_timer timer("Write DNG time");
                dng_image_writer writer;

                writer.WriteDNG(host,
                        stream2,
                        *negative.Get(),
                        &previewList,
                        dngVersion_Current,
                        false);
            }

            gDumpDNG.Clear();
        }

        // Output TIF file if requested.
        if (gDumpTIF.NotEmpty()) {

            // Render final image.

            dng_render render(host, *negative);

            render.SetFinalSpace(*gFinalSpace   );
            render.SetFinalPixelType(gFinalPixelType);

            if (host.MinimumSize()) {
                dng_point stage3Size = negative->Stage3Image()->Size();
                render.SetMaximumSize (Max_uint32(stage3Size.v,
                                stage3Size.h));
            }

            AutoPtr<dng_image> finalImage;

            {
                dng_timer timer("Render time");
                finalImage.Reset(render.Render());
            }

            finalImage->Rotate(negative->Orientation());

            // Now that Camera Raw supports non-raw formats, we should
            // not keep any Camera Raw settings in the XMP around when
            // writing rendered files.
#if qDNGUseXMP
            if (negative->GetXMP()) {
                negative->GetXMP()->RemoveProperties(XMP_NS_CRS);
                negative->GetXMP()->RemoveProperties(XMP_NS_CRSS);
            }
#endif

            // Write TIF file.
            dng_file_stream stream2(gDumpTIF.Get(), true);

            {
                dng_timer timer("Write TIFF time");
                dng_image_writer writer;

                writer.WriteTIFF(host,
                        stream2,
                        *finalImage.Get(),
                        finalImage->Planes() >= 3 ? piRGB
                        : piBlackIsZero,
                        ccUncompressed,
                        negative.Get(),
                        &render.FinalSpace());
            }
            gDumpTIF.Clear();
        }
    } catch (const dng_exception &except) {
        return except.ErrorCode();
    } catch (...) {
        return dng_error_unknown;
    }

    ALOGI("DNG validation complete");

    return dng_error_none;
}

extern "C" jboolean
Java_android_hardware_camera2_cts_DngCreatorTest_validateDngNative(
    JNIEnv* env, jclass /*clazz*/, jbyteArray dngBuffer) {

    jbyte* buffer = env->GetByteArrayElements(dngBuffer, NULL);
    jsize bufferCount = env->GetArrayLength(dngBuffer);
    if (buffer == nullptr) {
        ALOGE("Unable to map DNG buffer to native");
        return JNI_FALSE;
    }

    // DNG parsing warnings/errors fprintfs are spread throughout the DNG SDK,
    // guarded by the qDNGValidate define flag. To avoid modifying the SDK,
    // redirect stderr to a pipe to capture output locally.

    int pipeFds[2];
    int err;

    err = pipe(pipeFds);
    if (err != 0) {
        ALOGE("Error redirecting dng_validate output: %d", errno);
        env->ReleaseByteArrayElements(dngBuffer, buffer, 0);
        return JNI_FALSE;
    }

    int stderrFd = dup(fileno(stderr));
    dup2(pipeFds[1], fileno(stderr));
    close(pipeFds[1]);

    // Actually run the validation
    dng_error_code dng_err = dng_validate(buffer, bufferCount);

    env->ReleaseByteArrayElements(dngBuffer, buffer, 0);

    // Restore stderr and read out pipe
    dup2(stderrFd, fileno(stderr));

    std::stringstream errorStream;
    const size_t BUF_SIZE = 256;
    char readBuf[BUF_SIZE];

    ssize_t count = 0;
    while((count = read(pipeFds[0], readBuf, BUF_SIZE)) > 0) {
        errorStream.write(readBuf, count);
    }
    if (count < 0) {
        ALOGE("Error reading from dng_validate output pipe: %d", errno);
        return JNI_FALSE;
    }
    close(pipeFds[1]);

    std::string line;
    int lineCount = 0;
    ALOGI("Output from DNG validation:");
    // dng_validate doesn't actually propagate all errors/warnings to the
    // return error code, so look for an error pattern in output to detect
    // problems. Also make sure the output is long enough since some non-error
    // content should always be printed.
    while(std::getline(errorStream, line, '\n')) {
        lineCount++;
        if ( (line.size() > 3) &&
                (line[0] == line[1]) &&
                (line[1] == line[2]) &&
                (line[2] == '*') ) {
            // Found a warning or error, so need to fail the test
            if (dng_err == dng_error_none) {
                dng_err = dng_error_bad_format;
            }
            ALOGE("**|%s", line.c_str());
        } else {
            ALOGI("  |%s", line.c_str());
        }
    }
    // If no output is produced, assume something went wrong
    if (lineCount < 3) {
        ALOGE("Validation output less than expected!");
        dng_err = dng_error_unknown;
    }
    if (dng_err != dng_error_none) {
        ALOGE("DNG validation failed!");
    }

    return (dng_err == dng_error_none) ? JNI_TRUE : JNI_FALSE;
}
