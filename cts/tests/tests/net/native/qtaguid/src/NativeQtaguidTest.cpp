/*
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

#include <arpa/inet.h>
#include <errno.h>
#include <inttypes.h>
#include <string.h>
#include <sys/socket.h>

#include <gtest/gtest.h>
#include <cutils/qtaguid.h>

int getCtrlSkInfo(int tag, uid_t uid, uint64_t* sk_addr, int* ref_cnt) {
    FILE *fp;
    fp = fopen("/proc/net/xt_qtaguid/ctrl", "r");
    if (!fp)
        return -ENOENT;
    uint64_t full_tag = (uint64_t)tag << 32 | uid;
    char pattern[40];
    snprintf(pattern, sizeof(pattern), " tag=0x%" PRIx64 " (uid=%" PRIu32 ")", full_tag, uid);

    size_t len;
    char *line_buffer = NULL;
    while(getline(&line_buffer, &len, fp) != -1) {
        if (strstr(line_buffer, pattern) == NULL)
            continue;
        int res;
        pid_t dummy_pid;
        uint64_t k_tag;
        uint32_t k_uid;
        const int TOTAL_PARAM = 5;
        res = sscanf(line_buffer, "sock=%" PRIx64 " tag=0x%" PRIx64 " (uid=%" PRIu32 ") "
                     "pid=%u f_count=%u", sk_addr, &k_tag, &k_uid,
                     &dummy_pid, ref_cnt);
        if (!(res == TOTAL_PARAM && k_tag == full_tag && k_uid == uid))
            return -EINVAL;
        free(line_buffer);
        return 0;
    }
    free(line_buffer);
    return -ENOENT;
}

void checkNoSocketPointerLeaks(int family) {
    int sockfd = socket(family, SOCK_STREAM, 0);
    uid_t uid = getuid();
    int tag = arc4random();
    int ref_cnt;
    uint64_t sk_addr;
    uint64_t expect_addr = 0;

    EXPECT_EQ(0, qtaguid_tagSocket(sockfd, tag, uid));
    EXPECT_EQ(0, getCtrlSkInfo(tag, uid, &sk_addr, &ref_cnt));
    EXPECT_EQ(expect_addr, sk_addr);
    close(sockfd);
    EXPECT_EQ(-ENOENT, getCtrlSkInfo(tag, uid, &sk_addr, &ref_cnt));
}

TEST (NativeQtaguidTest, close_socket_without_untag) {
    int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    uid_t uid = getuid();
    int tag = arc4random();
    int ref_cnt;
    uint64_t dummy_sk;
    EXPECT_EQ(0, qtaguid_tagSocket(sockfd, tag, uid));
    EXPECT_EQ(0, getCtrlSkInfo(tag, uid, &dummy_sk, &ref_cnt));
    EXPECT_EQ(2, ref_cnt);
    close(sockfd);
    EXPECT_EQ(-ENOENT, getCtrlSkInfo(tag, uid, &dummy_sk, &ref_cnt));
}

TEST (NativeQtaguidTest, close_socket_without_untag_ipv6) {
    int sockfd = socket(AF_INET6, SOCK_STREAM, 0);
    uid_t uid = getuid();
    int tag = arc4random();
    int ref_cnt;
    uint64_t dummy_sk;
    EXPECT_EQ(0, qtaguid_tagSocket(sockfd, tag, uid));
    EXPECT_EQ(0, getCtrlSkInfo(tag, uid, &dummy_sk, &ref_cnt));
    EXPECT_EQ(2, ref_cnt);
    close(sockfd);
    EXPECT_EQ(-ENOENT, getCtrlSkInfo(tag, uid, &dummy_sk, &ref_cnt));
}

TEST (NativeQtaguidTest, no_socket_addr_leak) {
  checkNoSocketPointerLeaks(AF_INET);
  checkNoSocketPointerLeaks(AF_INET6);
}

int main(int argc, char **argv) {
      testing::InitGoogleTest(&argc, argv);

      return RUN_ALL_TESTS();
}
