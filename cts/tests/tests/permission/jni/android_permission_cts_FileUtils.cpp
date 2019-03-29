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

#include <android/log.h>
#include <jni.h>
#include <stdio.h>
#include <linux/xattr.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/xattr.h>
#include <sys/capability.h>
#include <grp.h>
#include <pwd.h>
#include <string.h>
#include <ScopedLocalRef.h>
#include <ScopedPrimitiveArray.h>
#include <ScopedUtfChars.h>

static jfieldID gFileStatusDevFieldID;
static jfieldID gFileStatusInoFieldID;
static jfieldID gFileStatusModeFieldID;
static jfieldID gFileStatusNlinkFieldID;
static jfieldID gFileStatusUidFieldID;
static jfieldID gFileStatusGidFieldID;
static jfieldID gFileStatusSizeFieldID;
static jfieldID gFileStatusBlksizeFieldID;
static jfieldID gFileStatusBlocksFieldID;
static jfieldID gFileStatusAtimeFieldID;
static jfieldID gFileStatusMtimeFieldID;
static jfieldID gFileStatusCtimeFieldID;

/*
 * Native methods used by
 * cts/tests/tests/permission/src/android/permission/cts/FileUtils.java
 *
 * Copied from hidden API: frameworks/base/core/jni/android_os_FileUtils.cpp
 */

jboolean android_permission_cts_FileUtils_getFileStatus(JNIEnv* env,
        jobject /* thiz */, jstring path, jobject fileStatus, jboolean statLinks)
{
    ScopedUtfChars cPath(env, path);
    jboolean ret = false;
    struct stat s;

    int res = statLinks == true ? lstat(cPath.c_str(), &s)
            : stat(cPath.c_str(), &s);

    if (res == 0) {
        ret = true;
        if (fileStatus != NULL) {
            env->SetIntField(fileStatus, gFileStatusDevFieldID, s.st_dev);
            env->SetIntField(fileStatus, gFileStatusInoFieldID, s.st_ino);
            env->SetIntField(fileStatus, gFileStatusModeFieldID, s.st_mode);
            env->SetIntField(fileStatus, gFileStatusNlinkFieldID, s.st_nlink);
            env->SetIntField(fileStatus, gFileStatusUidFieldID, s.st_uid);
            env->SetIntField(fileStatus, gFileStatusGidFieldID, s.st_gid);
            env->SetLongField(fileStatus, gFileStatusSizeFieldID, s.st_size);
            env->SetIntField(fileStatus, gFileStatusBlksizeFieldID, s.st_blksize);
            env->SetLongField(fileStatus, gFileStatusBlocksFieldID, s.st_blocks);
            env->SetLongField(fileStatus, gFileStatusAtimeFieldID, s.st_atime);
            env->SetLongField(fileStatus, gFileStatusMtimeFieldID, s.st_mtime);
            env->SetLongField(fileStatus, gFileStatusCtimeFieldID, s.st_ctime);
        }
    }

    return ret;
}

jstring android_permission_cts_FileUtils_getUserName(JNIEnv* env,
        jobject /* thiz */, jint uid)
{
    struct passwd *pwd = getpwuid(uid);
    return env->NewStringUTF(pwd->pw_name);
}

jstring android_permission_cts_FileUtils_getGroupName(JNIEnv* env,
        jobject /* thiz */, jint gid)
{
    struct group *grp = getgrgid(gid);
    return env->NewStringUTF(grp->gr_name);
}

static jboolean isPermittedCapBitSet(JNIEnv* env, jstring path, size_t capId)
{
    struct vfs_cap_data capData;
    memset(&capData, 0, sizeof(capData));

    ScopedUtfChars cPath(env, path);
    ssize_t result = getxattr(cPath.c_str(), XATTR_NAME_CAPS, &capData,
                              sizeof(capData));
    if (result <= 0)
    {
          __android_log_print(ANDROID_LOG_DEBUG, NULL,
                  "isPermittedCapBitSet(): getxattr(\"%s\") call failed: "
                  "return %d (error: %s (%d))\n",
                  cPath.c_str(), result, strerror(errno), errno);
          return false;
    }

    return (capData.data[CAP_TO_INDEX(capId)].permitted &
            CAP_TO_MASK(capId)) != 0;
}

jboolean android_permission_cts_FileUtils_hasSetUidCapability(JNIEnv* env,
        jobject /* clazz */, jstring path)
{
    return isPermittedCapBitSet(env, path, CAP_SETUID);
}

jboolean android_permission_cts_FileUtils_hasSetGidCapability(JNIEnv* env,
        jobject /* clazz */, jstring path)
{
    return isPermittedCapBitSet(env, path, CAP_SETGID);
}

static bool throwNamedException(JNIEnv* env, const char* className,
        const char* message)
{
    ScopedLocalRef<jclass> eClazz(env, env->FindClass(className));
    if (eClazz.get() == NULL)
    {
        __android_log_print(ANDROID_LOG_ERROR, NULL,
                "throwNamedException(): failed to find class %s, cannot throw",
                className);
        return false;
    }

    env->ThrowNew(eClazz.get(), message);
    return true;
}

// fill vfs_cap_data's permitted caps given a Java int[] of cap ids
static bool fillPermittedCaps(vfs_cap_data* capData, JNIEnv* env, jintArray capIds)
{
    ScopedIntArrayRO cCapIds(env, capIds);
    const size_t capCount = cCapIds.size();

    for (size_t i = 0; i < capCount; ++i)
    {
        const jint capId = cCapIds[i];
        if (!cap_valid(capId))
        {
            char message[64];
            snprintf(message, sizeof(message),
                    "capability id %d out of valid range", capId);
            throwNamedException(env, "java/lang/IllegalArgumentException",
                    message);

            return false;
        }
        capData->data[CAP_TO_INDEX(capId)].permitted |= CAP_TO_MASK(capId);
    }
    return true;
}

jboolean android_permission_cts_FileUtils_CapabilitySet_fileHasOnly(JNIEnv* env,
        jobject /* clazz */, jstring path, jintArray capIds)
{
    struct vfs_cap_data expectedCapData;
    memset(&expectedCapData, 0, sizeof(expectedCapData));

    expectedCapData.magic_etc = VFS_CAP_REVISION | VFS_CAP_FLAGS_EFFECTIVE;
    if (!fillPermittedCaps(&expectedCapData, env, capIds))
    {
        // exception thrown
        return false;
    }

    struct vfs_cap_data actualCapData;
    memset(&actualCapData, 0, sizeof(actualCapData));

    ScopedUtfChars cPath(env, path);
    ssize_t result = getxattr(cPath.c_str(), XATTR_NAME_CAPS, &actualCapData,
            sizeof(actualCapData));
    if (result <= 0)
    {
        __android_log_print(ANDROID_LOG_DEBUG, NULL,
                "fileHasOnly(): getxattr(\"%s\") call failed: "
                "return %d (error: %s (%d))\n",
                cPath.c_str(), result, strerror(errno), errno);
        return false;
    }

    return (memcmp(&expectedCapData, &actualCapData,
            sizeof(struct vfs_cap_data)) == 0);
}

static JNINativeMethod gMethods[] = {
    {  "getFileStatus", "(Ljava/lang/String;Landroid/permission/cts/FileUtils$FileStatus;Z)Z",
            (void *) android_permission_cts_FileUtils_getFileStatus  },
    {  "getUserName", "(I)Ljava/lang/String;",
            (void *) android_permission_cts_FileUtils_getUserName  },
    {  "getGroupName", "(I)Ljava/lang/String;",
            (void *) android_permission_cts_FileUtils_getGroupName  },
    {  "hasSetUidCapability", "(Ljava/lang/String;)Z",
            (void *) android_permission_cts_FileUtils_hasSetUidCapability   },
    {  "hasSetGidCapability", "(Ljava/lang/String;)Z",
            (void *) android_permission_cts_FileUtils_hasSetGidCapability   },
};

static JNINativeMethod gCapabilitySetMethods[] = {
    {  "fileHasOnly", "(Ljava/lang/String;[I)Z",
            (void *) android_permission_cts_FileUtils_CapabilitySet_fileHasOnly  },
};

int register_android_permission_cts_FileUtils(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/permission/cts/FileUtils");

    jclass fileStatusClass = env->FindClass("android/permission/cts/FileUtils$FileStatus");
    gFileStatusDevFieldID = env->GetFieldID(fileStatusClass, "dev", "I");
    gFileStatusInoFieldID = env->GetFieldID(fileStatusClass, "ino", "I");
    gFileStatusModeFieldID = env->GetFieldID(fileStatusClass, "mode", "I");
    gFileStatusNlinkFieldID = env->GetFieldID(fileStatusClass, "nlink", "I");
    gFileStatusUidFieldID = env->GetFieldID(fileStatusClass, "uid", "I");
    gFileStatusGidFieldID = env->GetFieldID(fileStatusClass, "gid", "I");
    gFileStatusSizeFieldID = env->GetFieldID(fileStatusClass, "size", "J");
    gFileStatusBlksizeFieldID = env->GetFieldID(fileStatusClass, "blksize", "I");
    gFileStatusBlocksFieldID = env->GetFieldID(fileStatusClass, "blocks", "J");
    gFileStatusAtimeFieldID = env->GetFieldID(fileStatusClass, "atime", "J");
    gFileStatusMtimeFieldID = env->GetFieldID(fileStatusClass, "mtime", "J");
    gFileStatusCtimeFieldID = env->GetFieldID(fileStatusClass, "ctime", "J");

    jint result = env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
    if (result)
    {
      return result;
    }

    // register FileUtils.CapabilitySet native methods
    jclass capClazz = env->FindClass("android/permission/cts/FileUtils$CapabilitySet");

    return env->RegisterNatives(capClazz, gCapabilitySetMethods,
            sizeof(gCapabilitySetMethods) / sizeof(JNINativeMethod));
}
