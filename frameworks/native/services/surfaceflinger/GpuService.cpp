/*
 * Copyright 2016 The Android Open Source Project
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

#include "GpuService.h"

#include <binder/IResultReceiver.h>
#include <binder/Parcel.h>
#include <utils/String8.h>
#include <vkjson.h>

namespace android {

// ----------------------------------------------------------------------------

class BpGpuService : public BpInterface<IGpuService>
{
public:
    explicit BpGpuService(const sp<IBinder>& impl) : BpInterface<IGpuService>(impl) {}
};

IMPLEMENT_META_INTERFACE(GpuService, "android.ui.IGpuService");

status_t BnGpuService::onTransact(uint32_t code, const Parcel& data,
        Parcel* reply, uint32_t flags)
{
    status_t status;
    switch (code) {
    case SHELL_COMMAND_TRANSACTION: {
        int in = data.readFileDescriptor();
        int out = data.readFileDescriptor();
        int err = data.readFileDescriptor();
        int argc = data.readInt32();
        Vector<String16> args;
        for (int i = 0; i < argc && data.dataAvail() > 0; i++) {
           args.add(data.readString16());
        }
        sp<IBinder> unusedCallback;
        sp<IResultReceiver> resultReceiver;
        if ((status = data.readNullableStrongBinder(&unusedCallback)) != OK)
            return status;
        if ((status = data.readNullableStrongBinder(&resultReceiver)) != OK)
            return status;
        status = shellCommand(in, out, err, args);
        if (resultReceiver != nullptr)
            resultReceiver->send(status);
        return OK;
    }

    default:
        return BBinder::onTransact(code, data, reply, flags);
    }
}

// ----------------------------------------------------------------------------

namespace {
    status_t cmd_help(int out);
    status_t cmd_vkjson(int out, int err);
}

const char* const GpuService::SERVICE_NAME = "gpu";

GpuService::GpuService() {}

status_t GpuService::shellCommand(int /*in*/, int out, int err,
        Vector<String16>& args)
{
    ALOGV("GpuService::shellCommand");
    for (size_t i = 0, n = args.size(); i < n; i++)
        ALOGV("  arg[%zu]: '%s'", i, String8(args[i]).string());

    if (args.size() >= 1) {
        if (args[0] == String16("vkjson"))
            return cmd_vkjson(out, err);
        if (args[0] == String16("help"))
            return cmd_help(out);
    }
    // no command, or unrecognized command
    cmd_help(err);
    return BAD_VALUE;
}

// ----------------------------------------------------------------------------

namespace {

status_t cmd_help(int out) {
    FILE* outs = fdopen(out, "w");
    if (!outs) {
        ALOGE("vkjson: failed to create out stream: %s (%d)", strerror(errno),
            errno);
        return BAD_VALUE;
    }
    fprintf(outs,
        "GPU Service commands:\n"
        "  vkjson   dump Vulkan properties as JSON\n");
    fclose(outs);
    return NO_ERROR;
}

void vkjsonPrint(FILE* out) {
    std::string json = VkJsonInstanceToJson(VkJsonGetInstance());
    fwrite(json.data(), 1, json.size(), out);
    fputc('\n', out);
}

status_t cmd_vkjson(int out, int /*err*/) {
    FILE* outs = fdopen(out, "w");
    if (!outs) {
        int errnum = errno;
        ALOGE("vkjson: failed to create output stream: %s", strerror(errnum));
        return -errnum;
    }
    vkjsonPrint(outs);
    fclose(outs);
    return NO_ERROR;
}

} // anonymous namespace

} // namespace android
