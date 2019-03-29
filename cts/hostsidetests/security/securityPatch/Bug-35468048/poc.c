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

#define _GNU_SOURCE
#include <string.h>
#include <sys/wait.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
// for syscall
#include <sys/syscall.h>
// for futex
#include <linux/futex.h>
#include <sys/time.h>

#define LOG(fmt, ...) printf(fmt "\n", ##__VA_ARGS__)
#define ERR(fmt, ...) \
  printf(fmt ": %d(%s)\n", ##__VA_ARGS__, errno, strerror(errno))
#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))

static int set_affinity(int num) {
  int ret = 0;
  cpu_set_t mask;
  CPU_ZERO(&mask);
  CPU_SET(num, &mask);
  ret = sched_setaffinity(0, sizeof(cpu_set_t), &mask);
  if (ret == -1) {
    ERR("[-] set affinity failed");
  }
  return ret;
}

#define TARGET "/sys/devices/virtual/htc_sensorhub/sensor_hub/enable"
#define DISABLE "/sys/module/CwMcuSensor/parameters/DEBUG_DISABLE"
int main(int argc, char *argv[]) {
  int i, ret, tmpfd;
  char buf[PAGE_SIZE] = {0};

  /* bind_cpu */
  set_affinity(0);

  /* disable debug */
  tmpfd = open(DISABLE, O_RDWR);
  if (tmpfd == -1) {
    ERR("[-] open %s failed", TARGET);
    return -1;
  }

  write(tmpfd, "1", 1);
  close(tmpfd);

  tmpfd = open(TARGET, O_RDWR);

  if (tmpfd == -1)
    ERR("[-] open %s failed", TARGET);
  else
    LOG("[+] open %s OK", TARGET);

  /* read */
  ret = read(tmpfd, buf, PAGE_SIZE);
  if (ret == -1)
    ERR("[-] read %s failed", TARGET);
  else {
    LOG("[+] read succeeded: %d bytes", ret);
    LOG("[+] content: %s", buf);
  }

  close(tmpfd);
  return 0;
}
