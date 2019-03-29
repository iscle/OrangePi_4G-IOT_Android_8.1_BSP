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

#include <asm/ioctl.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/wait.h>
#include <unistd.h>

char *pci_msm_path = "/sys/kernel/debug/pci-msm/";

#define SIZE 64

int write_file(int fd, char *str) {
  int ret;
  ret = write(fd, str, SIZE);
  return 0;
}

int open_file(char *filename) {
  int fd;
  char file[125] = {0};

  sprintf(file, "%s%s", pci_msm_path, filename);

  fd = open(file, O_RDWR);
  if (fd < 0) {
    exit(1);
  }
  return fd;
}

void set_aer_enable() {
  int fd;
  char buf[SIZE] = {0};

  fd = open_file("aer_enable");

  write_file(fd, buf);

  close(fd);
}

void set_wr_offset() {
  int fd;
  char buf[SIZE] = {0};

  fd = open_file("wr_offset");

  sprintf(buf, "%s", "9999999");

  write_file(fd, buf);

  close(fd);
}

void set_test_case() {
  int fd;
  char buf[SIZE] = {0};
  buf[0] = '1';
  buf[1] = '2';

  fd = open_file("case");

  write_file(fd, buf);

  close(fd);
}

void set_base_sel() {
  int fd;
  char buf[SIZE] = {0};
  buf[0] = '1';

  fd = open_file("base_sel");

  write_file(fd, buf);

  close(fd);
}

int main(int argc, char *argv[]) {
  set_wr_offset();
  set_base_sel();
  set_test_case();
  return 0;
}
