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

#define LOG_TAG "crash_dispatcher"

#include <vector>

#include <elf.h>
#include <errno.h>
#include <unistd.h>

#include <android-base/file.h>
#include <log/log.h>

namespace {

const char kCrashCollector32Path[] = "/system/bin/crash_collector32";
const char kCrashCollector64Path[] = "/system/bin/crash_collector64";

}  // namespace

// Reads the coredump from STDIN and checks whether it's 32-bit or 64-bit,
// and dispatches it to the appropriate version of crash_collector.
int main(int argc, char** argv) {
  // Do not abort on a write error caused by a broken pipe.
  signal(SIGPIPE, SIG_IGN);
  // Read the ELF header until EI_CLASS.
  char buf_head[EI_CLASS + 1];
  if (!android::base::ReadFully(STDIN_FILENO, buf_head, sizeof(buf_head))) {
    ALOGE("Failed to read. errno = %d", errno);
    return 1;
  }
  // Set up pipe to feed coredump to crash_collector.
  int pipe_fds[2];
  if (pipe(pipe_fds) == -1) {
    ALOGE("Failed to pipe. errno = %d", errno);
    return 1;
  }
  // Prepare crash_collector arguments.
  const char* crash_collector_path = buf_head[EI_CLASS] == ELFCLASS64 ?
      kCrashCollector64Path : kCrashCollector32Path;
  std::vector<const char*> args(argc + 1);
  args[0] = crash_collector_path;
  std::copy(argv + 1, argv + argc, args.begin() + 1);
  args.back() = nullptr;
  // Exec crash_collector.
  const pid_t pid = fork();
  if (pid == -1) {
    ALOGE("Failed to fork. errno = %d", errno);
    return 1;
  }
  if (pid == 0) {
    if (close(pipe_fds[1]) != 0 && errno != EINTR) {
      ALOGE("Failed to close the pipe's write end. errno = %d", errno);
      _exit(1);
    }
    if (TEMP_FAILURE_RETRY(dup2(pipe_fds[0], STDIN_FILENO)) != 0) {
      ALOGE("Failed to dup the pipe's read end. errno = %d", errno);
      _exit(1);
    }
    execve(args[0], const_cast<char**>(&args[0]), environ);
    ALOGE("Failed to execute crash_collector. errno = %d", errno);
    _exit(1);
  }
  // Send buf_head to crash_collector.
  if (close(pipe_fds[0]) != 0 && errno != EINTR) {
    ALOGE("Failed to close the pipe's read end.");
    return 1;
  }
  if (!android::base::WriteFully(pipe_fds[1], buf_head, sizeof(buf_head))) {
    return 1;
  }
  // Send the rest of coredump to crash_collector.
  const size_t kBufSize = 32768;
  char buf[kBufSize];
  while (true) {
    int rv = TEMP_FAILURE_RETRY(read(STDIN_FILENO, buf, kBufSize));
    if (rv == -1) {
      ALOGE("Failed to read. errno = %d", errno);
      return 1;
    }
    if (rv == 0)
      break;
    if (!android::base::WriteFully(pipe_fds[1], buf, rv)) {
      return 1;
    }
  }
  return 0;
}
