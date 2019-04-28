/*
 * Copyright (C) 2015 The Android Open Source Project
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

#define LOG_TAG "cmd"

#include <utils/Log.h>
#include <binder/Parcel.h>
#include <binder/ProcessState.h>
#include <binder/IResultReceiver.h>
#include <binder/IServiceManager.h>
#include <binder/IShellCallback.h>
#include <binder/TextOutput.h>
#include <utils/Condition.h>
#include <utils/Mutex.h>
#include <utils/Vector.h>

#include <getopt.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/time.h>
#include <errno.h>
#include <memory>

#include "selinux/selinux.h"
#include "selinux/android.h"

#define DEBUG 0

using namespace android;

static int sort_func(const String16* lhs, const String16* rhs)
{
    return lhs->compare(*rhs);
}

struct SecurityContext_Delete {
    void operator()(security_context_t p) const {
        freecon(p);
    }
};
typedef std::unique_ptr<char[], SecurityContext_Delete> Unique_SecurityContext;

class MyShellCallback : public BnShellCallback
{
public:
    bool mActive = true;

    virtual int openOutputFile(const String16& path, const String16& seLinuxContext) {
        String8 path8(path);
        char cwd[256];
        getcwd(cwd, 256);
        String8 fullPath(cwd);
        fullPath.appendPath(path8);
        if (!mActive) {
            aerr << "Open attempt after active for: " << fullPath << endl;
            return -EPERM;
        }
        int fd = open(fullPath.string(), O_WRONLY|O_CREAT|O_TRUNC, S_IRWXU|S_IRWXG);
        if (fd < 0) {
            return fd;
        }
        if (is_selinux_enabled() && seLinuxContext.size() > 0) {
            String8 seLinuxContext8(seLinuxContext);
            security_context_t tmp = NULL;
            int ret = getfilecon(fullPath.string(), &tmp);
            Unique_SecurityContext context(tmp);
            int accessGranted = selinux_check_access(seLinuxContext8.string(), context.get(),
                    "file", "write", NULL);
            if (accessGranted != 0) {
                close(fd);
                aerr << "System server has no access to file context " << context.get()
                        << " (from path " << fullPath.string() << ", context "
                        << seLinuxContext8.string() << ")" << endl;
                return -EPERM;
            }
        }
        return fd;
    }
};

class MyResultReceiver : public BnResultReceiver
{
public:
    Mutex mMutex;
    Condition mCondition;
    bool mHaveResult = false;
    int32_t mResult = 0;

    virtual void send(int32_t resultCode) {
        AutoMutex _l(mMutex);
        mResult = resultCode;
        mHaveResult = true;
        mCondition.signal();
    }

    int32_t waitForResult() {
        AutoMutex _l(mMutex);
        while (!mHaveResult) {
            mCondition.wait(mMutex);
        }
        return mResult;
    }
};

int main(int argc, char* const argv[])
{
    signal(SIGPIPE, SIG_IGN);
    sp<ProcessState> proc = ProcessState::self();
    // setThreadPoolMaxThreadCount(0) actually tells the kernel it's
    // not allowed to spawn any additional threads, but we still spawn
    // a binder thread from userspace when we call startThreadPool().
    // This is safe because we only have 2 callbacks, neither of which
    // block.
    // See b/36066697 for rationale
    proc->setThreadPoolMaxThreadCount(0);
    proc->startThreadPool();

    sp<IServiceManager> sm = defaultServiceManager();
    fflush(stdout);
    if (sm == NULL) {
        ALOGW("Unable to get default service manager!");
        aerr << "cmd: Unable to get default service manager!" << endl;
        // M: Avoid to running static destructors.
        _exit(20);
    }

    if (argc == 1) {
        aerr << "cmd: No service specified; use -l to list all services" << endl;
        // M: Avoid to running static destructors.
        _exit(20);
    }

    if ((argc == 2) && (strcmp(argv[1], "-l") == 0)) {
        Vector<String16> services = sm->listServices();
        services.sort(sort_func);
        aout << "Currently running services:" << endl;

        for (size_t i=0; i<services.size(); i++) {
            sp<IBinder> service = sm->checkService(services[i]);
            if (service != NULL) {
                aout << "  " << services[i] << endl;
            }
        }
        // M: Avoid to running static destructors.
        _exit(0);
    }

    Vector<String16> args;
    for (int i=2; i<argc; i++) {
        args.add(String16(argv[i]));
    }
    String16 cmd = String16(argv[1]);
    sp<IBinder> service = sm->checkService(cmd);
    if (service == NULL) {
        ALOGW("Can't find service %s", argv[1]);
        aerr << "cmd: Can't find service: " << argv[1] << endl;
        // M: Avoid to running static destructors.
        _exit(20);
    }

    sp<MyShellCallback> cb = new MyShellCallback();
    sp<MyResultReceiver> result = new MyResultReceiver();

#if DEBUG
    ALOGD("cmd: Invoking %s in=%d, out=%d, err=%d", argv[1], STDIN_FILENO, STDOUT_FILENO,
            STDERR_FILENO);
#endif

    // TODO: block until a result is returned to MyResultReceiver.
    status_t err = IBinder::shellCommand(service, STDIN_FILENO, STDOUT_FILENO, STDERR_FILENO, args,
            cb, result);
    if (err < 0) {
        const char* errstr;
        switch (err) {
            case BAD_TYPE: errstr = "Bad type"; break;
            case FAILED_TRANSACTION: errstr = "Failed transaction"; break;
            case FDS_NOT_ALLOWED: errstr = "File descriptors not allowed"; break;
            case UNEXPECTED_NULL: errstr = "Unexpected null"; break;
            default: errstr = strerror(-err); break;
        }
        ALOGW("Failure calling service %s: %s (%d)", argv[1], errstr, -err);
        aout << "cmd: Failure calling service " << argv[1] << ": " << errstr << " ("
                << (-err) << ")" << endl;
        _exit(err);
    }

    cb->mActive = false;
    status_t res = result->waitForResult();
#if DEBUG
    ALOGD("result=%d", (int)res);
#endif
    // M: Avoid to running static destructors.
    _exit(res);
}
