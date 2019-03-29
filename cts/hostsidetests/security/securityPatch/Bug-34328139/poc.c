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
#define _GNU_SOURCE

#include <pthread.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <stdio.h>
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <string.h>
#include "local_poc.h"


int fd;
struct mdp_rotation_config config;
int id;
int status[10];
int cmd = 0;

void *threadForConfig(void *arg)
{
    int index = (int) (unsigned long)arg;

    status[index] = 1;

    while (cmd != 1) {
        usleep(10);
    }

    if (cmd == -1)
        goto failed;

    usleep(5 * index);
    ioctl(fd, MDSS_ROTATION_CONFIG, &config);
failed:
    status[index] = 2;
    return NULL;

}

void *threadForClose()
{
    status[0] = 1;

    while (cmd != 1) {
        usleep(10);
    }

     if (cmd == -1)
        goto failed;

    usleep(50);
    ioctl(fd, MDSS_ROTATION_CLOSE, id);
failed:
    status[0] = 2;
    return NULL;
}

int main()
{
    int ret, i, count;
    pthread_t tid[5];
    int p = 5;

    count = 0;
retry:
    if (p-- > 0){
        fork();
    }

    cmd = 0;
    for (i = 0; i < 10; i++)
        status[i] = 0;

    fd = open("/dev/mdss_rotator", O_RDONLY, 0);
    if (fd < 0) {
         return -1;
    }

    ret = ioctl(fd, MDSS_ROTATION_OPEN, &config);
    if (ret < 0) {
            goto failed;
    } else {
        id = config.session_id;
    }

    ret = pthread_create(&tid[0], NULL, threadForClose, NULL);
    if (ret != 0) {
        goto failed;
    }

    for (i = 1; i < 10; i++) {
        ret = pthread_create(&tid[1], NULL, threadForConfig, (void *)(unsigned long)i);
        if (ret != 0) {
            cmd = -1;
            goto failed;
        }
    }

    while (status[0] != 1 || status[1] != 1 || status[2] != 1
        || status[3] != 1 || status[4] != 1 || status[5] != 1
        || status[6] != 1 || status[7] != 1 || status[8] != 1
        || status[9] != 1) {
        usleep(50);
    }

    cmd = 1;
    usleep(10);
    ioctl(fd, MDSS_ROTATION_CONFIG, &config);

    while (status[0] != 2 || status[1] != 2 || status[2] != 2
        || status[3] != 2 || status[4] != 2 || status[5] != 2
        || status[6] != 2 || status[7] != 2 || status[8] != 2
        || status[9] != 2) {
        usleep(50);
    }

failed:
    close(fd);
    goto retry;

    return 0;
}
