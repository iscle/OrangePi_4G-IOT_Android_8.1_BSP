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
#include <sys/ioctl.h>
#include <sys/mount.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <fcntl.h>
#include <errno.h>
#include <pthread.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

enum qcedev_sha_alg_enum {
  QCEDEV_ALG_SHA1 = 0,
  QCEDEV_ALG_SHA256 = 1,
  QCEDEV_ALG_SHA1_HMAC = 2,
  QCEDEV_ALG_SHA256_HMAC = 3,
  QCEDEV_ALG_AES_CMAC = 4,
  QCEDEV_ALG_SHA_ALG_LAST
};

struct buf_info {
  union {
    uint32_t offset;
    uint8_t *vaddr;
  };
  uint32_t len;
};

struct qcedev_sha_op_req {
  struct buf_info data[16];
  uint32_t entries;
  uint32_t data_len;
  uint8_t digest[32];
  uint32_t diglen;
  uint8_t *authkey;
  uint32_t authklen;
  enum qcedev_sha_alg_enum alg;
};

#define QCEDEV_IOC_MAGIC 0x87

#define QCEDEV_IOCTL_SHA_INIT_REQ \
  _IOWR(QCEDEV_IOC_MAGIC, 3, struct qcedev_sha_op_req)
#define QCEDEV_IOCTL_SHA_UPDATE_REQ \
  _IOWR(QCEDEV_IOC_MAGIC, 4, struct qcedev_sha_op_req)
#define QCEDEV_IOCTL_SHA_FINAL_REQ \
  _IOWR(QCEDEV_IOC_MAGIC, 5, struct qcedev_sha_op_req)

void main() {
  int f = open("/dev/qce", 0);

  struct qcedev_sha_op_req arg;
  memset(&arg, 0, sizeof(arg));
  arg.alg = QCEDEV_ALG_AES_CMAC;
  arg.entries = 1;
  arg.authklen = 16;
  char *key = malloc(arg.authklen);
  arg.authkey = key;
  arg.data_len = 256;

  arg.data[0].len = arg.data_len;
  char *data = malloc(arg.data_len);
  arg.data[0].vaddr = data;
  int r = ioctl(f, QCEDEV_IOCTL_SHA_INIT_REQ, &arg);

  arg.diglen = 0x8000;
  r = ioctl(f, QCEDEV_IOCTL_SHA_UPDATE_REQ, &arg);
  r = ioctl(f, QCEDEV_IOCTL_SHA_FINAL_REQ, &arg);
}
