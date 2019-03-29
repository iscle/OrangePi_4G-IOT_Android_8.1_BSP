/*
 * Copyright (C) 2010 The Android Open Source Project
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


#define LOG_TAG "MultinetworkApiTest"
#include <utils/Log.h>

#include <arpa/inet.h>
#include <errno.h>
#include <inttypes.h>
#include <jni.h>
#include <netdb.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <android/multinetwork.h>

#define UNUSED(X) ((void) (X))

static const char kHostname[] = "connectivitycheck.android.com";


JNIEXPORT jint Java_android_net_cts_MultinetworkApiTest_runGetaddrinfoCheck(
        JNIEnv* env, jclass class, jlong nethandle) {
    UNUSED(env);
    UNUSED(class);
    net_handle_t handle = (net_handle_t) nethandle;
    struct addrinfo *res = NULL;

    errno = 0;
    int rval = android_getaddrinfofornetwork(handle, kHostname, NULL, NULL, &res);
    const int saved_errno = errno;
    freeaddrinfo(res);

    ALOGD("android_getaddrinfofornetwork(%llu, %s) returned rval=%d errno=%d",
          handle, kHostname, rval, saved_errno);
    return rval == 0 ? 0 : -saved_errno;
}

JNIEXPORT jint Java_android_net_cts_MultinetworkApiTest_runSetprocnetwork(
        JNIEnv* env, jclass class, jlong nethandle) {
    UNUSED(env);
    UNUSED(class);
    net_handle_t handle = (net_handle_t) nethandle;

    errno = 0;
    int rval = android_setprocnetwork(handle);
    const int saved_errno = errno;
    ALOGD("android_setprocnetwork(%llu) returned rval=%d errno=%d",
          handle, rval, saved_errno);
    return rval == 0 ? 0 : -saved_errno;
}

JNIEXPORT jint Java_android_net_cts_MultinetworkApiTest_runSetsocknetwork(
        JNIEnv* env, jclass class, jlong nethandle) {
    UNUSED(env);
    UNUSED(class);
    net_handle_t handle = (net_handle_t) nethandle;

    errno = 0;
    int fd = socket(AF_INET6, SOCK_DGRAM, IPPROTO_UDP);
    if (fd < 0) {
        ALOGD("socket() failed, errno=%d", errno);
        return -errno;
    }

    errno = 0;
    int rval = android_setsocknetwork(handle, fd);
    const int saved_errno = errno;
    ALOGD("android_setprocnetwork(%llu, %d) returned rval=%d errno=%d",
          handle, fd, rval, saved_errno);
    close(fd);
    return rval == 0 ? 0 : -saved_errno;
}

// Use sizeof("x") - 1 because we need a compile-time constant, and strlen("x")
// isn't guaranteed to fold to a constant.
static const int kSockaddrStrLen = INET6_ADDRSTRLEN + sizeof("[]:65535") - 1;

void sockaddr_ntop(const struct sockaddr *sa, socklen_t salen, char *dst, const size_t size) {
    char addrstr[INET6_ADDRSTRLEN];
    char portstr[sizeof("65535")];
    char buf[kSockaddrStrLen+1];

    int ret = getnameinfo(sa, salen,
                          addrstr, sizeof(addrstr),
                          portstr, sizeof(portstr),
                          NI_NUMERICHOST | NI_NUMERICSERV);
    if (ret == 0) {
        snprintf(buf, sizeof(buf),
                 (sa->sa_family == AF_INET6) ? "[%s]:%s" : "%s:%s",
                 addrstr, portstr);
    } else {
        sprintf(buf, "???");
    }

    strlcpy(dst, buf, size);
}

JNIEXPORT jint Java_android_net_cts_MultinetworkApiTest_runDatagramCheck(
        JNIEnv* env, jclass class, jlong nethandle) {
    UNUSED(env);
    UNUSED(class);
    const struct addrinfo kHints = {
        .ai_flags = AI_ADDRCONFIG,
        .ai_family = AF_UNSPEC,
        .ai_socktype = SOCK_DGRAM,
        .ai_protocol = IPPROTO_UDP,
    };
    struct addrinfo *res = NULL;
    net_handle_t handle = (net_handle_t) nethandle;

    static const char kPort[] = "443";
    int rval = android_getaddrinfofornetwork(handle, kHostname, kPort, &kHints, &res);
    if (rval != 0) {
        ALOGD("android_getaddrinfofornetwork(%llu, %s) returned rval=%d errno=%d",
              handle, kHostname, rval, errno);
        freeaddrinfo(res);
        return -errno;
    }

    // Rely upon getaddrinfo sorting the best destination to the front.
    int fd = socket(res->ai_family, res->ai_socktype, res->ai_protocol);
    if (fd < 0) {
        ALOGD("socket(%d, %d, %d) failed, errno=%d",
              res->ai_family, res->ai_socktype, res->ai_protocol, errno);
        freeaddrinfo(res);
        return -errno;
    }

    rval = android_setsocknetwork(handle, fd);
    ALOGD("android_setprocnetwork(%llu, %d) returned rval=%d errno=%d",
          handle, fd, rval, errno);
    if (rval != 0) {
        close(fd);
        freeaddrinfo(res);
        return -errno;
    }

    char addrstr[kSockaddrStrLen+1];
    sockaddr_ntop(res->ai_addr, res->ai_addrlen, addrstr, sizeof(addrstr));
    ALOGD("Attempting connect() to %s ...", addrstr);

    rval = connect(fd, res->ai_addr, res->ai_addrlen);
    if (rval != 0) {
        close(fd);
        freeaddrinfo(res);
        return -errno;
    }
    freeaddrinfo(res);

    struct sockaddr_storage src_addr;
    socklen_t src_addrlen = sizeof(src_addr);
    if (getsockname(fd, (struct sockaddr *)&src_addr, &src_addrlen) != 0) {
        close(fd);
        return -errno;
    }
    sockaddr_ntop((const struct sockaddr *)&src_addr, sizeof(src_addr), addrstr, sizeof(addrstr));
    ALOGD("... from %s", addrstr);

    // Don't let reads or writes block indefinitely.
    const struct timeval timeo = { 2, 0 };  // 2 seconds
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeo, sizeof(timeo));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeo, sizeof(timeo));

    // For reference see:
    //     https://tools.ietf.org/html/draft-tsvwg-quic-protocol-01#section-6.1
    uint8_t quic_packet[] = {
        0x0c,                    // public flags: 64bit conn ID, 8bit sequence number
        0, 0, 0, 0, 0, 0, 0, 0,  // 64bit connection ID
        0x01,                    // sequence number
        0x00,                    // private flags
        0x07,                    // type: regular frame type "PING"
    };

    arc4random_buf(quic_packet + 1, 8);  // random connection ID

    uint8_t response[1500];
    ssize_t sent, rcvd;
    static const int MAX_RETRIES = 5;
    int i, errnum = 0;

    for (i = 0; i < MAX_RETRIES; i++) {
        sent = send(fd, quic_packet, sizeof(quic_packet), 0);
        if (sent < (ssize_t)sizeof(quic_packet)) {
            errnum = errno;
            ALOGD("send(QUIC packet) returned sent=%zd, errno=%d", sent, errnum);
            close(fd);
            return -errnum;
        }

        rcvd = recv(fd, response, sizeof(response), 0);
        if (rcvd > 0) {
            break;
        } else {
            errnum = errno;
            ALOGD("[%d/%d] recv(QUIC response) returned rcvd=%zd, errno=%d",
                  i + 1, MAX_RETRIES, rcvd, errnum);
        }
    }
    if (rcvd < sent) {
        ALOGD("QUIC UDP %s: sent=%zd but rcvd=%zd, errno=%d", kPort, sent, rcvd, errnum);
        if (rcvd <= 0) {
            ALOGD("Does this network block UDP port %s?", kPort);
        }
        close(fd);
        return -EPROTO;
    }

    int conn_id_cmp = memcmp(quic_packet + 1, response + 1, 8);
    if (conn_id_cmp != 0) {
        ALOGD("sent and received connection IDs do not match");
        close(fd);
        return -EPROTO;
    }

    // TODO: log, and compare to the IP address encoded in the
    // response, since this should be a public reset packet.

    close(fd);
    return 0;
}
