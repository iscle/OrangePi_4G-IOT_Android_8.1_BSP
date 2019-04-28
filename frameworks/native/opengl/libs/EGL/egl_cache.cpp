/*
 ** Copyright 2011, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

#include "egl_cache.h"

#include "../egl_impl.h"

#include "egl_display.h"


#include <private/EGL/cache.h>

#include <inttypes.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <thread>

#include <log/log.h>

// Cache size limits.
static const size_t maxKeySize = 12 * 1024;
static const size_t maxValueSize = 64 * 1024;
static const size_t maxTotalSize = 2 * 1024 * 1024;

// Cache file header
static const char* cacheFileMagic = "EGL$";
static const size_t cacheFileHeaderSize = 8;

// The time in seconds to wait before saving newly inserted cache entries.
static const unsigned int deferredSaveDelay = 4;

// ----------------------------------------------------------------------------
namespace android {
// ----------------------------------------------------------------------------

#define BC_EXT_STR "EGL_ANDROID_blob_cache"

// called from android_view_ThreadedRenderer.cpp
void egl_set_cache_filename(const char* filename) {
    egl_cache_t::get()->setCacheFilename(filename);
}

//
// Callback functions passed to EGL.
//
static void setBlob(const void* key, EGLsizeiANDROID keySize,
        const void* value, EGLsizeiANDROID valueSize) {
    egl_cache_t::get()->setBlob(key, keySize, value, valueSize);
}

static EGLsizeiANDROID getBlob(const void* key, EGLsizeiANDROID keySize,
        void* value, EGLsizeiANDROID valueSize) {
    return egl_cache_t::get()->getBlob(key, keySize, value, valueSize);
}

//
// egl_cache_t definition
//
egl_cache_t::egl_cache_t() :
        mInitialized(false) {
}

egl_cache_t::~egl_cache_t() {
}

egl_cache_t egl_cache_t::sCache;

egl_cache_t* egl_cache_t::get() {
    return &sCache;
}

void egl_cache_t::initialize(egl_display_t *display) {
    std::lock_guard<std::mutex> lock(mMutex);

    egl_connection_t* const cnx = &gEGLImpl;
    if (cnx->dso && cnx->major >= 0 && cnx->minor >= 0) {
        const char* exts = display->disp.queryString.extensions;
        size_t bcExtLen = strlen(BC_EXT_STR);
        size_t extsLen = strlen(exts);
        bool equal = !strcmp(BC_EXT_STR, exts);
        bool atStart = !strncmp(BC_EXT_STR " ", exts, bcExtLen+1);
        bool atEnd = (bcExtLen+1) < extsLen &&
                !strcmp(" " BC_EXT_STR, exts + extsLen - (bcExtLen+1));
        bool inMiddle = strstr(exts, " " BC_EXT_STR " ") != nullptr;
        if (equal || atStart || atEnd || inMiddle) {
            PFNEGLSETBLOBCACHEFUNCSANDROIDPROC eglSetBlobCacheFuncsANDROID;
            eglSetBlobCacheFuncsANDROID =
                    reinterpret_cast<PFNEGLSETBLOBCACHEFUNCSANDROIDPROC>(
                            cnx->egl.eglGetProcAddress(
                                    "eglSetBlobCacheFuncsANDROID"));
            if (eglSetBlobCacheFuncsANDROID == NULL) {
                ALOGE("EGL_ANDROID_blob_cache advertised, "
                        "but unable to get eglSetBlobCacheFuncsANDROID");
                return;
            }

            eglSetBlobCacheFuncsANDROID(display->disp.dpy,
                    android::setBlob, android::getBlob);
            EGLint err = cnx->egl.eglGetError();
            if (err != EGL_SUCCESS) {
                ALOGE("eglSetBlobCacheFuncsANDROID resulted in an error: "
                        "%#x", err);
            }
        }
    }

    mInitialized = true;
}

void egl_cache_t::terminate() {
    std::lock_guard<std::mutex> lock(mMutex);
    saveBlobCacheLocked();
    mBlobCache = NULL;
}

void egl_cache_t::setBlob(const void* key, EGLsizeiANDROID keySize,
        const void* value, EGLsizeiANDROID valueSize) {
    std::lock_guard<std::mutex> lock(mMutex);

    if (keySize < 0 || valueSize < 0) {
        ALOGW("EGL_ANDROID_blob_cache set: negative sizes are not allowed");
        return;
    }

    if (mInitialized) {
        BlobCache* bc = getBlobCacheLocked();
        bc->set(key, keySize, value, valueSize);

        if (!mSavePending) {
            mSavePending = true;
            std::thread deferredSaveThread([this]() {
                sleep(deferredSaveDelay);
                std::lock_guard<std::mutex> lock(mMutex);
                if (mInitialized) {
                    saveBlobCacheLocked();
                }
                mSavePending = false;
            });
            deferredSaveThread.detach();
        }
    }
}

EGLsizeiANDROID egl_cache_t::getBlob(const void* key, EGLsizeiANDROID keySize,
        void* value, EGLsizeiANDROID valueSize) {
    std::lock_guard<std::mutex> lock(mMutex);

    if (keySize < 0 || valueSize < 0) {
        ALOGW("EGL_ANDROID_blob_cache set: negative sizes are not allowed");
        return 0;
    }

    if (mInitialized) {
        BlobCache* bc = getBlobCacheLocked();
        return bc->get(key, keySize, value, valueSize);
    }
    return 0;
}

void egl_cache_t::setCacheFilename(const char* filename) {
    std::lock_guard<std::mutex> lock(mMutex);
    mFilename = filename;
}

BlobCache* egl_cache_t::getBlobCacheLocked() {
    if (mBlobCache == nullptr) {
        mBlobCache.reset(new BlobCache(maxKeySize, maxValueSize, maxTotalSize));
        loadBlobCacheLocked();
    }
    return mBlobCache.get();
}

static uint32_t crc32c(const uint8_t* buf, size_t len) {
    const uint32_t polyBits = 0x82F63B78;
    uint32_t r = 0;
    for (size_t i = 0; i < len; i++) {
        r ^= buf[i];
        for (int j = 0; j < 8; j++) {
            if (r & 1) {
                r = (r >> 1) ^ polyBits;
            } else {
                r >>= 1;
            }
        }
    }
    return r;
}

void egl_cache_t::saveBlobCacheLocked() {
    if (mFilename.length() > 0 && mBlobCache != NULL) {
        size_t cacheSize = mBlobCache->getFlattenedSize();
        size_t headerSize = cacheFileHeaderSize;
        const char* fname = mFilename.c_str();

        // Try to create the file with no permissions so we can write it
        // without anyone trying to read it.
        int fd = open(fname, O_CREAT | O_EXCL | O_RDWR, 0);
        if (fd == -1) {
            if (errno == EEXIST) {
                // The file exists, delete it and try again.
                if (unlink(fname) == -1) {
                    // No point in retrying if the unlink failed.
                    ALOGE("error unlinking cache file %s: %s (%d)", fname,
                            strerror(errno), errno);
                    return;
                }
                // Retry now that we've unlinked the file.
                fd = open(fname, O_CREAT | O_EXCL | O_RDWR, 0);
            }
            if (fd == -1) {
                ALOGE("error creating cache file %s: %s (%d)", fname,
                        strerror(errno), errno);
                return;
            }
        }

        size_t fileSize = headerSize + cacheSize;

        uint8_t* buf = new uint8_t [fileSize];
        if (!buf) {
            ALOGE("error allocating buffer for cache contents: %s (%d)",
                    strerror(errno), errno);
            close(fd);
            unlink(fname);
            return;
        }

        int err = mBlobCache->flatten(buf + headerSize, cacheSize);
        if (err < 0) {
            ALOGE("error writing cache contents: %s (%d)", strerror(-err),
                    -err);
            delete [] buf;
            close(fd);
            unlink(fname);
            return;
        }

        // Write the file magic and CRC
        memcpy(buf, cacheFileMagic, 4);
        uint32_t* crc = reinterpret_cast<uint32_t*>(buf + 4);
        *crc = crc32c(buf + headerSize, cacheSize);

        if (write(fd, buf, fileSize) == -1) {
            ALOGE("error writing cache file: %s (%d)", strerror(errno),
                    errno);
            delete [] buf;
            close(fd);
            unlink(fname);
            return;
        }

        delete [] buf;
        fchmod(fd, S_IRUSR);
        close(fd);
    }
}

void egl_cache_t::loadBlobCacheLocked() {
    if (mFilename.length() > 0) {
        size_t headerSize = cacheFileHeaderSize;

        int fd = open(mFilename.c_str(), O_RDONLY, 0);
        if (fd == -1) {
            if (errno != ENOENT) {
                ALOGE("error opening cache file %s: %s (%d)", mFilename.c_str(),
                        strerror(errno), errno);
            }
            return;
        }

        struct stat statBuf;
        if (fstat(fd, &statBuf) == -1) {
            ALOGE("error stat'ing cache file: %s (%d)", strerror(errno), errno);
            close(fd);
            return;
        }

        // Sanity check the size before trying to mmap it.
        size_t fileSize = statBuf.st_size;
        if (fileSize > maxTotalSize * 2) {
            ALOGE("cache file is too large: %#" PRIx64,
                  static_cast<off64_t>(statBuf.st_size));
            close(fd);
            return;
        }

        uint8_t* buf = reinterpret_cast<uint8_t*>(mmap(NULL, fileSize,
                PROT_READ, MAP_PRIVATE, fd, 0));
        if (buf == MAP_FAILED) {
            ALOGE("error mmaping cache file: %s (%d)", strerror(errno),
                    errno);
            close(fd);
            return;
        }

        // Check the file magic and CRC
        size_t cacheSize = fileSize - headerSize;
        if (memcmp(buf, cacheFileMagic, 4) != 0) {
            ALOGE("cache file has bad mojo");
            close(fd);
            return;
        }
        uint32_t* crc = reinterpret_cast<uint32_t*>(buf + 4);
        if (crc32c(buf + headerSize, cacheSize) != *crc) {
            ALOGE("cache file failed CRC check");
            close(fd);
            return;
        }

        int err = mBlobCache->unflatten(buf + headerSize, cacheSize);
        if (err < 0) {
            ALOGE("error reading cache contents: %s (%d)", strerror(-err),
                    -err);
            munmap(buf, fileSize);
            close(fd);
            return;
        }

        munmap(buf, fileSize);
        close(fd);
    }
}

// ----------------------------------------------------------------------------
}; // namespace android
// ----------------------------------------------------------------------------
