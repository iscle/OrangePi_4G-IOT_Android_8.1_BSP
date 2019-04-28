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


#define LOG_TAG "WebViewZygote"

#include <sys/prctl.h>

#include <android_runtime/AndroidRuntime.h>
#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <utils/Log.h>
#include <utils/String8.h>
#include <utils/Vector.h>

namespace android {

class WebViewRuntime : public AndroidRuntime {
public:
    WebViewRuntime(char* argBlockStart, size_t argBlockSize)
        : AndroidRuntime(argBlockStart, argBlockSize) {}

    ~WebViewRuntime() override {}

    void onStarted() override {
        // Nothing to do since this is a zygote server.
    }

    void onVmCreated(JNIEnv*) override {
        // Nothing to do when the VM is created in the zygote.
    }

    void onZygoteInit() override {
        // Called after a new process is forked.
        sp<ProcessState> proc = ProcessState::self();
        proc->startThreadPool();
    }

    void onExit(int code) override {
        IPCThreadState::self()->stopProcess();
        AndroidRuntime::onExit(code);
    }
};

}  // namespace android

int main(int argc, char* const argv[]) {
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) < 0) {
        LOG_ALWAYS_FATAL("PR_SET_NO_NEW_PRIVS failed: %s", strerror(errno));
        return 12;
    }

    /// M: Ignore DEBUGGER_SIGNAL (signal 35) for WebView sandboxed process
    /// WebView M61 enables SECCOMP-BPF for sandboxed process, only certain prctl flags
    /// can be used, which caused debuggerd crash during handling DEBUGGER_SIGNAL.
    /// To prevent sandboxed process crashing during ANR dumps, we ignore DEBUGGER_SIGNAL.
    signal(35, SIG_IGN);

    size_t argBlockSize = 0;
    for (int i = 0; i < argc; ++i) {
        argBlockSize += strlen(argv[i]) + 1;
    }

    android::WebViewRuntime runtime(argv[0], argBlockSize);
    runtime.addOption("-Xzygote");

    android::Vector<android::String8> args;
    runtime.start("com.android.internal.os.WebViewZygoteInit", args, /*zygote=*/ true);
}
