/*
 * Copyright (C) 2010 The Android Open Source Project
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

#define LOG_TAG "BitmapRegionDecoder"

#include "BitmapFactory.h"
#include "CreateJavaOutputStreamAdaptor.h"
#include "GraphicsJNI.h"
#include "Utils.h"

#include "SkBitmap.h"
#include "SkBitmapRegionDecoder.h"
#include "SkCodec.h"
#include "SkData.h"
#include "SkUtils.h"
#include "SkPixelRef.h"
#include "SkStream.h"
#include "SkImageDecoder.h"

#include "android_nio_utils.h"
#include "android_util_Binder.h"
#include "core_jni_helpers.h"

#include <nativehelper/JNIHelp.h>
#include <androidfw/Asset.h>
#include <binder/Parcel.h>
#include <jni.h>
#include <sys/stat.h>

#include <memory>

using namespace android;

class SkBitmapRegionDecoder_MTK : public SkBitmapRegionDecoder {
public:

    SkBitmapRegionDecoder_MTK(int width, int height, SkImageDecoder *decoder)
        : SkBitmapRegionDecoder(width, height)
        , fDecoder(decoder)
    {}

    ~SkBitmapRegionDecoder_MTK() {
        if (fDecoder) delete fDecoder;
    }

    bool decodeRegion(SkBitmap* bitmap, SkBRDAllocator* allocator,
                                  const SkIRect& desiredSubset, int sampleSize,
                                  SkColorType colorType, bool requireUnpremul,
                                  sk_sp<SkColorSpace> prefColorSpace = nullptr)
    { return false; }

    bool decodeRegion(SkBitmap* bitmap, const SkIRect& rect,
                           SkColorType pref, int sampleSize, void* dc) {
    fDecoder->setSampleSize(sampleSize);
#ifdef MTK_SKIA_MULTI_THREAD_JPEG_REGION
    #ifdef MTK_IMAGE_DC_SUPPORT
    if (fDecoder->getFormat() == SkImageDecoder::kJPEG_Format)
        return fDecoder->decodeSubset(bitmap, rect, pref, sampleSize, dc);
    else
        return fDecoder->decodeSubset(bitmap, rect, pref);
    #else
    if (fDecoder->getFormat() == SkImageDecoder::kJPEG_Format)
        return fDecoder->decodeSubset(bitmap, rect, pref, sampleSize, NULL);
    else
        return fDecoder->decodeSubset(bitmap, rect, pref);
    #endif
#else
    return fDecoder->decodeSubset(bitmap, rect, pref);
#endif
    }

    bool conversionSupported(SkColorType colorType) { return false; }

    SkEncodedImageFormat getEncodedFormat() { return SkEncodedImageFormat::kJPEG_MTK;}

    SkColorType computeOutputColorType(SkColorType requestedColorType) override {
        return fDecoder->computeOutputColorType(requestedColorType);
    }

    sk_sp<SkColorSpace> computeOutputColorSpace(SkColorType outputColorType,
            sk_sp<SkColorSpace> prefColorSpace = nullptr) override {
        return fDecoder->computeOutputColorSpace(outputColorType, prefColorSpace);
    }

#ifdef MTK_IMAGE_ENABLE_PQ_FOR_JPEG
    void setPostProcFlag(int flag) { UNUSED(flag);}
#endif

    SkImageDecoder* getDecoder() const { return fDecoder; }

    void regionDecodeLock() {
      /* SkDebugf("SKIA_REGION: wait regionDecodeLock!!\n");0 */
      fRegionDecodeMutex.acquire();
      /* SkDebugf("SKIA_REGION: get regionDecodeLock!!\n"); */
      return;
    }
    void regionDecodeUnlock() {
      /* SkDebugf("SKIA_REGION: release regionDecodeUnlock!!\n"); */
      fRegionDecodeMutex.release();
      return;
    }

private:
    SkImageDecoder* fDecoder;
    SkMutex fRegionDecodeMutex;
};

/*
 * nine patch not supported
 *
 * purgeable not supported
 * reportSizeToVM not supported
 */

jstring getMimeTypeString(JNIEnv* env, SkImageDecoder::Format format) {
    static const struct {
        SkImageDecoder::Format fFormat;
        const char*            fMimeType;
    } gMimeTypes[] = {
        { SkImageDecoder::kBMP_Format,  "image/bmp" },
        { SkImageDecoder::kGIF_Format,  "image/gif" },
        { SkImageDecoder::kICO_Format,  "image/x-ico" },
        { SkImageDecoder::kJPEG_Format, "image/jpeg" },
        { SkImageDecoder::kPNG_Format,  "image/png" },
        { SkImageDecoder::kWEBP_Format, "image/webp" },
        { SkImageDecoder::kWBMP_Format, "image/vnd.wap.wbmp" }
    };

    const char* cstr = nullptr;
    for (size_t i = 0; i < SK_ARRAY_COUNT(gMimeTypes); i++) {
        if (gMimeTypes[i].fFormat == format) {
            cstr = gMimeTypes[i].fMimeType;
            break;
        }
    }

    jstring jstr = nullptr;
    if (cstr != nullptr) {
        // NOTE: Caller should env->ExceptionCheck() for OOM
        // (can't check for nullptr as it's a valid return value)
        jstr = env->NewStringUTF(cstr);
    }
    return jstr;
}

void MTK_getProcessCmdline(char* acProcName, int iSize)
{
    long pid = getpid();
    char procPath[128];
    snprintf(procPath, 128, "/proc/%ld/cmdline", pid);
    FILE * file = fopen(procPath, "r");
    if (file)
    {
       fgets(acProcName, iSize - 1, file);
       fclose(file);
    }
}

extern "C" int MTK_CheckAppName(const char* acAppName)
{
    char appName[128];
    MTK_getProcessCmdline(appName, sizeof(appName));
    /// MTK_LOGD("appName=%s, acAppName=%s", appName, acAppName);
    if (strstr(appName, acAppName))
    {
        /// MTK_LOGD("1");
        return 1;
    }
    /// MTK_LOGD("0");
    return 0;
}

static jobject createBitmapRegionDecoder(JNIEnv* env, std::unique_ptr<SkStreamRewindable> stream) {
    SkStreamRewindable *streamPtr = stream.release();
    // use SkImageDecoder::Factory for checking decode format, only jpeg will get decoder object
    SkImageDecoder* decoder = SkImageDecoder::Factory(streamPtr);

    // only jpeg format will use enhanced region decode flow
    //if (decoder && (MTK_CheckAppName("com.android.gallery3d") || MTK_CheckAppName("com.google.android.apps.photos")))
    if (decoder)
    {
        int width, height;

        HeapAllocator *allocator = new HeapAllocator();
        decoder->setAllocator(allocator);
        allocator->unref();

        // This call passes ownership of stream to the decoder, or deletes on failure.
        if (!decoder->buildTileIndex(streamPtr, &width, &height)) {
            char msg[100];
            snprintf(msg, sizeof(msg), "Image failed to decode using %s decoder",
                    decoder->getFormatName());
            doThrowIOE(env, msg);
            delete decoder;
            return nullObjectReturn("decoder->buildTileIndex returned false");
        }

        std::unique_ptr<SkBitmapRegionDecoder_MTK> brd(new SkBitmapRegionDecoder_MTK(width, height, decoder));
        return GraphicsJNI::createBitmapRegionDecoder(env, brd.release());
    }
    // use AOSP region decode flow
    else
    {
        // delete the decoder previouly created since we don't need it
        //if (decoder)
        //    delete decoder;

        std::unique_ptr<SkBitmapRegionDecoder> brd(
                  SkBitmapRegionDecoder::Create(streamPtr,
                                                SkBitmapRegionDecoder::kAndroidCodec_Strategy));
        if (!brd) {
            doThrowIOE(env, "Image format not supported");
            return nullObjectReturn("CreateBitmapRegionDecoder returned null");
        }

        return GraphicsJNI::createBitmapRegionDecoder(env, brd.release());
    }
}

static jobject nativeNewInstanceFromByteArray(JNIEnv* env, jobject, jbyteArray byteArray,
                                     jint offset, jint length, jboolean isShareable) {
    /*  If isShareable we could decide to just wrap the java array and
        share it, but that means adding a globalref to the java array object
        For now we just always copy the array's data if isShareable.
     */
    AutoJavaByteArray ar(env, byteArray);
    std::unique_ptr<SkMemoryStream> stream(new SkMemoryStream(ar.ptr() + offset, length, true));

    // the decoder owns the stream.
    jobject brd = createBitmapRegionDecoder(env, std::move(stream));
    return brd;
}

static jobject nativeNewInstanceFromFileDescriptor(JNIEnv* env, jobject clazz,
                                          jobject fileDescriptor, jboolean isShareable) {
    NPE_CHECK_RETURN_ZERO(env, fileDescriptor);

    jint descriptor = jniGetFDFromFileDescriptor(env, fileDescriptor);

    struct stat fdStat;
    if (fstat(descriptor, &fdStat) == -1) {
        doThrowIOE(env, "broken file descriptor");
        return nullObjectReturn("fstat return -1");
    }

    sk_sp<SkData> data(SkData::MakeFromFD(descriptor));
    std::unique_ptr<SkMemoryStream> stream(new SkMemoryStream(std::move(data)));

    // the decoder owns the stream.
    jobject brd = createBitmapRegionDecoder(env, std::move(stream));
    return brd;
}

static jobject nativeNewInstanceFromStream(JNIEnv* env, jobject clazz,
                                  jobject is,       // InputStream
                                  jbyteArray storage, // byte[]
                                  jboolean isShareable) {
    jobject brd = NULL;
    // for now we don't allow shareable with java inputstreams
    std::unique_ptr<SkStreamRewindable> stream(CopyJavaInputStream(env, is, storage));

    if (stream) {
        // the decoder owns the stream.
        brd = createBitmapRegionDecoder(env, std::move(stream));
    }
    return brd;
}

static jobject nativeNewInstanceFromAsset(JNIEnv* env, jobject clazz,
                                 jlong native_asset, // Asset
                                 jboolean isShareable) {
    Asset* asset = reinterpret_cast<Asset*>(native_asset);
    std::unique_ptr<SkMemoryStream> stream(CopyAssetToStream(asset));
    if (NULL == stream) {
        return NULL;
    }

    // the decoder owns the stream.
    jobject brd = createBitmapRegionDecoder(env, std::move(stream));
    return brd;
}

static SkMutex gRegionDecodeMutex;
/*
 * nine patch not supported
 * purgeable not supported
 * reportSizeToVM not supported
 */
static jobject nativeDecodeRegion(JNIEnv* env, jobject, jlong brdHandle, jint inputX,
        jint inputY, jint inputWidth, jint inputHeight, jobject options) {

    // use enhanced region decode for jpeg format
    //if ((reinterpret_cast<SkBitmapRegionDecoder*>(brdHandle))->getEncodedFormat() == SkEncodedImageFormat::kJPEG &&
    //     (MTK_CheckAppName("com.android.gallery3d") || MTK_CheckAppName("com.google.android.apps.photos")))
    if ((reinterpret_cast<SkBitmapRegionDecoder*>(brdHandle))->getEncodedFormat() == SkEncodedImageFormat::kJPEG_MTK)
    {
        SkBitmapRegionDecoder_MTK *brd = reinterpret_cast<SkBitmapRegionDecoder_MTK*>(brdHandle);
        jobject tileBitmap = NULL;
        SkImageDecoder *decoder = brd->getDecoder();
        int sampleSize = 1;
    #ifdef MTK_IMAGE_ENABLE_PQ_FOR_JPEG
        int postproc = 0;
        int postprocflag = 0;
    #endif
    #ifdef MTK_IMAGE_DC_SUPPORT
        void* dc = NULL;
        bool dcflag = false;
        jint* pdynamicCon = NULL;
        jintArray dynamicCon;
        jsize size = 0;
    #endif

        SkColorType colorType = kN32_SkColorType;
        bool isHardware = false;
        sk_sp<SkColorSpace> colorSpace = nullptr;
        bool doDither = true;
        bool preferQualityOverSpeed = false;
        bool requireUnpremultiplied = false;

        if (NULL != options) {
            sampleSize = env->GetIntField(options, gOptions_sampleSizeFieldID);
            // initialize these, in case we fail later on
            env->SetIntField(options, gOptions_widthFieldID, -1);
            env->SetIntField(options, gOptions_heightFieldID, -1);
            env->SetObjectField(options, gOptions_mimeFieldID, 0);

            jobject jconfig = env->GetObjectField(options, gOptions_configFieldID);
            colorType = GraphicsJNI::getNativeBitmapColorType(env, jconfig);
            jobject jcolorSpace = env->GetObjectField(options, gOptions_colorSpaceFieldID);
            colorSpace = GraphicsJNI::getNativeColorSpace(env, jcolorSpace);
            isHardware = GraphicsJNI::isHardwareConfig(env, jconfig);
            doDither = env->GetBooleanField(options, gOptions_ditherFieldID);
        #ifdef MTK_IMAGE_ENABLE_PQ_FOR_JPEG
            postproc = env->GetBooleanField(options, gOptions_postprocFieldID);
            postprocflag = env->GetIntField(options, gOptions_postprocflagFieldID);
            decoder->setPostProcFlag((postproc | (postprocflag << 4)));
        #endif
        #ifdef MTK_IMAGE_DC_SUPPORT
            dcflag = env->GetBooleanField(options, gOptions_dynamicConflagFieldID);
            dynamicCon = (jintArray)env->GetObjectField(options, gOptions_dynamicConFieldID);
            pdynamicCon = env->GetIntArrayElements(dynamicCon, NULL);
            size = env->GetArrayLength(dynamicCon);
        #endif
            preferQualityOverSpeed = env->GetBooleanField(options,
                    gOptions_preferQualityOverSpeedFieldID);
            // Get the bitmap for re-use if it exists.
            tileBitmap = env->GetObjectField(options, gOptions_bitmapFieldID);
            requireUnpremultiplied = !env->GetBooleanField(options, gOptions_premultipliedFieldID);
        }

    #ifdef MTK_IMAGE_ENABLE_PQ_FOR_JPEG
        decoder->setPostProcFlag((postproc | (postprocflag << 4)));
    #endif
    #ifdef MTK_IMAGE_DC_SUPPORT
        if (dcflag == true) {
            dc = (void*)pdynamicCon;
            int len = (int)size;
            decoder->setDynamicCon(dc, len);
        } else {
            dc = NULL;
            decoder->setDynamicCon(dc, 0);
        }
        ALOGD("nativeDecodeRegion dcflag %d, dc %p", dcflag, dc);
    #endif

        decoder->setDitherImage(doDither);
        decoder->setPreferQualityOverSpeed(preferQualityOverSpeed);
        decoder->setRequireUnpremultipliedColors(requireUnpremultiplied);
        //AutoDecoderCancel adc(options, decoder);

        // To fix the race condition in case "requestCancelDecode"
        // happens earlier than AutoDecoderCancel object is added
        // to the gAutoDecoderCancelMutex linked list.
        if (NULL != options && env->GetBooleanField(options, gOptions_mCancelID)) {
            return nullObjectReturn("gOptions_mCancelID");
        }

        SkColorType decodeColorType = brd->computeOutputColorType(colorType);
        sk_sp<SkColorSpace> decodeColorSpace = brd->computeOutputColorSpace(
                decodeColorType, colorSpace);

        SkIRect region;
        region.fLeft = inputX;
        region.fTop = inputY;
        region.fRight = inputX + inputWidth;
        region.fBottom = inputY + inputHeight;
        SkBitmap bitmap;

        if (tileBitmap != NULL) {
            // Re-use bitmap.
            GraphicsJNI::getSkBitmap(env, tileBitmap, &bitmap);
        }

        #ifdef MTK_IMAGE_DC_SUPPORT
        if (!brd->decodeRegion(&bitmap, region, colorType, sampleSize, dc))
        #else
        if (!brd->decodeRegion(&bitmap, region, colorType, sampleSize, NULL))
        #endif
        {
            return nullObjectReturn("decoder->decodeRegion returned false");
        }

    #if 0 //mtk skia multi thread jpeg region decode support
        if (!brd->decodeRegion(&bitmap, region, colorType, sampleSize)) {
          return nullObjectReturn("decoder->decodeRegion returned false");
        }
    #endif

        // update options (if any)
        if (NULL != options) {
            env->SetIntField(options, gOptions_widthFieldID, bitmap.width());
            env->SetIntField(options, gOptions_heightFieldID, bitmap.height());
            // TODO: set the mimeType field with the data from the codec.
            // but how to reuse a set of strings, rather than allocating new one
            // each time?
            env->SetObjectField(options, gOptions_mimeFieldID,
                                getMimeTypeString(env, decoder->getFormat()));
            if (env->ExceptionCheck()) {
                return nullObjectReturn("OOM in encodedFormatToString()");
            }

            jint configID = GraphicsJNI::colorTypeToLegacyBitmapConfig(decodeColorType);
            if (isHardware) {
                configID = GraphicsJNI::kHardware_LegacyBitmapConfig;
            }
            jobject config = env->CallStaticObjectMethod(gBitmapConfig_class,
                    gBitmapConfig_nativeToConfigMethodID, configID);
            env->SetObjectField(options, gOptions_outConfigFieldID, config);

            env->SetObjectField(options, gOptions_outColorSpaceFieldID,
                    GraphicsJNI::getColorSpace(env, decodeColorSpace, decodeColorType));
        }

        if (tileBitmap != NULL) {
            bitmap.notifyPixelsChanged();
            return tileBitmap;
        }

        HeapAllocator* allocator = (HeapAllocator*) decoder->getAllocator();

        int bitmapCreateFlags = 0;
        if (!requireUnpremultiplied) bitmapCreateFlags |= android::bitmap::kBitmapCreateFlag_Premultiplied;

        if (isHardware) {
            sk_sp<Bitmap> hardwareBitmap = Bitmap::allocateHardwareBitmap(bitmap);
            return bitmap::createBitmap(env, hardwareBitmap.release(), bitmapCreateFlags);
        }
        return android::bitmap::createBitmap(env, allocator->getStorageObjAndReset(),bitmapCreateFlags);
    }
    else
    {    // Set default options.
        int sampleSize = 1;
        SkColorType colorType = kN32_SkColorType;
        bool requireUnpremul = false;
        jobject javaBitmap = NULL;
    #ifdef MTK_IMAGE_ENABLE_PQ_FOR_JPEG
        int postproc = 0;
        int postprocflag = 0;
    #endif

        bool isHardware = false;
        sk_sp<SkColorSpace> colorSpace = nullptr;
        // Update the default options with any options supplied by the client.
        if (NULL != options) {
            sampleSize = env->GetIntField(options, gOptions_sampleSizeFieldID);
            jobject jconfig = env->GetObjectField(options, gOptions_configFieldID);
            colorType = GraphicsJNI::getNativeBitmapColorType(env, jconfig);
            jobject jcolorSpace = env->GetObjectField(options, gOptions_colorSpaceFieldID);
            colorSpace = GraphicsJNI::getNativeColorSpace(env, jcolorSpace);
            isHardware = GraphicsJNI::isHardwareConfig(env, jconfig);
            requireUnpremul = !env->GetBooleanField(options, gOptions_premultipliedFieldID);
            javaBitmap = env->GetObjectField(options, gOptions_bitmapFieldID);
        #ifdef MTK_IMAGE_ENABLE_PQ_FOR_JPEG
            postproc = env->GetBooleanField(options, gOptions_postprocFieldID);
            postprocflag = env->GetIntField(options, gOptions_postprocflagFieldID);
        #endif
            // The Java options of ditherMode and preferQualityOverSpeed are deprecated.  We will
            // ignore the values of these fields.

            // Initialize these fields to indicate a failure.  If the decode succeeds, we
            // will update them later on.
            env->SetIntField(options, gOptions_widthFieldID, -1);
            env->SetIntField(options, gOptions_heightFieldID, -1);
            env->SetObjectField(options, gOptions_mimeFieldID, 0);
            env->SetObjectField(options, gOptions_outConfigFieldID, 0);
            env->SetObjectField(options, gOptions_outColorSpaceFieldID, 0);
        }

        SkBitmapRegionDecoder* brd = reinterpret_cast<SkBitmapRegionDecoder*>(brdHandle);

        SkColorType decodeColorType = brd->computeOutputColorType(colorType);
        sk_sp<SkColorSpace> decodeColorSpace = brd->computeOutputColorSpace(
                decodeColorType, colorSpace);

        // Recycle a bitmap if possible.
        android::Bitmap* recycledBitmap = nullptr;
        size_t recycledBytes = 0;
        if (javaBitmap) {
            recycledBitmap = &bitmap::toBitmap(env, javaBitmap);
            if (recycledBitmap->isImmutable()) {
                ALOGW("Warning: Reusing an immutable bitmap as an image decoder target.");
            }
            recycledBytes = bitmap::getBitmapAllocationByteCount(env, javaBitmap);
        }

        // Set up the pixel allocator
        SkBRDAllocator* allocator = nullptr;
        RecyclingClippingPixelAllocator recycleAlloc(recycledBitmap, recycledBytes);
        HeapAllocator heapAlloc;
        if (javaBitmap) {
            allocator = &recycleAlloc;
            // We are required to match the color type of the recycled bitmap.
            decodeColorType = recycledBitmap->info().colorType();
        } else {
            allocator = &heapAlloc;
        }

        // Decode the region.
        SkIRect subset = SkIRect::MakeXYWH(inputX, inputY, inputWidth, inputHeight);

    #ifdef MTK_IMAGE_ENABLE_PQ_FOR_JPEG
        if (brd->getEncodedFormat() == SkEncodedImageFormat::kJPEG) {
            brd->setPostProcFlag(postproc | (postprocflag << 4));
        }
    #endif

        // add mutex to avoid multi-thread region decode for codec other than jpeg
        gRegionDecodeMutex.acquire();
        SkBitmap bitmap;
        if (!brd->decodeRegion(&bitmap, allocator, subset, sampleSize,
                decodeColorType, requireUnpremul, decodeColorSpace)) {
            gRegionDecodeMutex.release();
            return nullObjectReturn("Failed to decode region.");
        }

        // If the client provided options, indicate that the decode was successful.
        if (NULL != options) {
            env->SetIntField(options, gOptions_widthFieldID, bitmap.width());
            env->SetIntField(options, gOptions_heightFieldID, bitmap.height());

            env->SetObjectField(options, gOptions_mimeFieldID,
                    encodedFormatToString(env, (SkEncodedImageFormat)brd->getEncodedFormat()));
            if (env->ExceptionCheck()) {
                gRegionDecodeMutex.release();
                return nullObjectReturn("OOM in encodedFormatToString()");
            }

            jint configID = GraphicsJNI::colorTypeToLegacyBitmapConfig(decodeColorType);
            if (isHardware) {
                configID = GraphicsJNI::kHardware_LegacyBitmapConfig;
            }
            jobject config = env->CallStaticObjectMethod(gBitmapConfig_class,
                    gBitmapConfig_nativeToConfigMethodID, configID);
            env->SetObjectField(options, gOptions_outConfigFieldID, config);

            env->SetObjectField(options, gOptions_outColorSpaceFieldID,
                    GraphicsJNI::getColorSpace(env, decodeColorSpace, decodeColorType));
        }

        // If we may have reused a bitmap, we need to indicate that the pixels have changed.
        if (javaBitmap) {
            recycleAlloc.copyIfNecessary();
            bitmap::reinitBitmap(env, javaBitmap, recycledBitmap->info(), !requireUnpremul);
            gRegionDecodeMutex.release();
            return javaBitmap;
        }
        gRegionDecodeMutex.release();

        int bitmapCreateFlags = 0;
        if (!requireUnpremul) {
            bitmapCreateFlags |= android::bitmap::kBitmapCreateFlag_Premultiplied;
        }
        if (isHardware) {
            sk_sp<Bitmap> hardwareBitmap = Bitmap::allocateHardwareBitmap(bitmap);
            return bitmap::createBitmap(env, hardwareBitmap.release(), bitmapCreateFlags);
        }
        return android::bitmap::createBitmap(env, heapAlloc.getStorageObjAndReset(), bitmapCreateFlags);
    }
}

static jint nativeGetHeight(JNIEnv* env, jobject, jlong brdHandle) {
    SkBitmapRegionDecoder* brd =
            reinterpret_cast<SkBitmapRegionDecoder*>(brdHandle);
    return static_cast<jint>(brd->height());
}

static jint nativeGetWidth(JNIEnv* env, jobject, jlong brdHandle) {
    SkBitmapRegionDecoder* brd =
            reinterpret_cast<SkBitmapRegionDecoder*>(brdHandle);
    return static_cast<jint>(brd->width());
}

static void nativeClean(JNIEnv* env, jobject, jlong brdHandle) {
    SkBitmapRegionDecoder* brd =
            reinterpret_cast<SkBitmapRegionDecoder*>(brdHandle);
    delete brd;
}

///////////////////////////////////////////////////////////////////////////////

static const JNINativeMethod gBitmapRegionDecoderMethods[] = {
    {   "nativeDecodeRegion",
        "(JIIIILandroid/graphics/BitmapFactory$Options;)Landroid/graphics/Bitmap;",
        (void*)nativeDecodeRegion},

    {   "nativeGetHeight", "(J)I", (void*)nativeGetHeight},

    {   "nativeGetWidth", "(J)I", (void*)nativeGetWidth},

    {   "nativeClean", "(J)V", (void*)nativeClean},

    {   "nativeNewInstance",
        "([BIIZ)Landroid/graphics/BitmapRegionDecoder;",
        (void*)nativeNewInstanceFromByteArray
    },

    {   "nativeNewInstance",
        "(Ljava/io/InputStream;[BZ)Landroid/graphics/BitmapRegionDecoder;",
        (void*)nativeNewInstanceFromStream
    },

    {   "nativeNewInstance",
        "(Ljava/io/FileDescriptor;Z)Landroid/graphics/BitmapRegionDecoder;",
        (void*)nativeNewInstanceFromFileDescriptor
    },

    {   "nativeNewInstance",
        "(JZ)Landroid/graphics/BitmapRegionDecoder;",
        (void*)nativeNewInstanceFromAsset
    },
};

int register_android_graphics_BitmapRegionDecoder(JNIEnv* env)
{
    return android::RegisterMethodsOrDie(env, "android/graphics/BitmapRegionDecoder",
            gBitmapRegionDecoderMethods, NELEM(gBitmapRegionDecoderMethods));
}
