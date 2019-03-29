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

#include <ctype.h>
#include <dirent.h>
#include <dlfcn.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/ptrace.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#define FILE_PATH ("/proc/cld/athdiagpfs")

#define TEST_SIZE (8092)

void *read_test(int arg) {
  size_t buffer_size = (size_t)arg;
  FILE *fd = fopen(FILE_PATH, "rb");
  char *buffer = (char *)malloc(sizeof(char) * buffer_size);
  memset(buffer, '0', buffer_size);
  buffer[buffer_size-1] = '\0';
  while (fgets(buffer, buffer_size, fd))  { }
  return NULL;
}

int main() { read_test(TEST_SIZE); }
