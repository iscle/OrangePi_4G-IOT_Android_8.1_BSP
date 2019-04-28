/**
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "dumpstate"

#include "DumpstateService.h"

#include <android-base/stringprintf.h>

#include "android/os/BnDumpstate.h"

#include "DumpstateInternal.h"

namespace android {
namespace os {

namespace {
class DumpstateToken : public BnDumpstateToken {};
}

DumpstateService::DumpstateService() : ds_(Dumpstate::GetInstance()) {
}

char const* DumpstateService::getServiceName() {
    return "dumpstate";
}

status_t DumpstateService::Start() {
    IPCThreadState::self()->disableBackgroundScheduling(true);
    status_t ret = BinderService<DumpstateService>::publish();
    if (ret != android::OK) {
        return ret;
    }
    sp<ProcessState> ps(ProcessState::self());
    ps->startThreadPool();
    ps->giveThreadPoolName();
    return android::OK;
}

binder::Status DumpstateService::setListener(const std::string& name,
                                             const sp<IDumpstateListener>& listener,
                                             sp<IDumpstateToken>* returned_token) {
    *returned_token = nullptr;
    if (name.empty()) {
        MYLOGE("setListener(): name not set\n");
        return binder::Status::ok();
    }
    if (listener == nullptr) {
        MYLOGE("setListener(): listener not set\n");
        return binder::Status::ok();
    }
    std::lock_guard<std::mutex> lock(lock_);
    if (ds_.listener_ != nullptr) {
        MYLOGE("setListener(%s): already set (%s)\n", name.c_str(), ds_.listener_name_.c_str());
        return binder::Status::ok();
    }

    ds_.listener_name_ = name;
    ds_.listener_ = listener;
    *returned_token = new DumpstateToken();

    return binder::Status::ok();
}

status_t DumpstateService::dump(int fd, const Vector<String16>&) {
    dprintf(fd, "id: %d\n", ds_.id_);
    dprintf(fd, "pid: %d\n", ds_.pid_);
    dprintf(fd, "update_progress: %s\n", ds_.update_progress_ ? "true" : "false");
    dprintf(fd, "update_progress_threshold: %d\n", ds_.update_progress_threshold_);
    dprintf(fd, "last_updated_progress: %d\n", ds_.last_updated_progress_);
    dprintf(fd, "progress:\n");
    ds_.progress_->Dump(fd, "  ");
    dprintf(fd, "args: %s\n", ds_.args_.c_str());
    dprintf(fd, "extra_options: %s\n", ds_.extra_options_.c_str());
    dprintf(fd, "version: %s\n", ds_.version_.c_str());
    dprintf(fd, "bugreport_dir: %s\n", ds_.bugreport_dir_.c_str());
    dprintf(fd, "screenshot_path: %s\n", ds_.screenshot_path_.c_str());
    dprintf(fd, "log_path: %s\n", ds_.log_path_.c_str());
    dprintf(fd, "tmp_path: %s\n", ds_.tmp_path_.c_str());
    dprintf(fd, "path: %s\n", ds_.path_.c_str());
    dprintf(fd, "extra_options: %s\n", ds_.extra_options_.c_str());
    dprintf(fd, "base_name: %s\n", ds_.base_name_.c_str());
    dprintf(fd, "name: %s\n", ds_.name_.c_str());
    dprintf(fd, "now: %ld\n", ds_.now_);
    dprintf(fd, "is_zipping: %s\n", ds_.IsZipping() ? "true" : "false");
    dprintf(fd, "listener: %s\n", ds_.listener_name_.c_str());
    dprintf(fd, "notification title: %s\n", ds_.notification_title.c_str());
    dprintf(fd, "notification description: %s\n", ds_.notification_description.c_str());

    return NO_ERROR;
}
}  // namespace os
}  // namespace android
