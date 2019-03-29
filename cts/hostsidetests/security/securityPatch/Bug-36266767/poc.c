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
#include <sys/timerfd.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <pthread.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sched.h>
#include <signal.h>

#define THREAD_NUM    2

pthread_t thread_id[THREAD_NUM+1] = { 0 };
int thread_ret[THREAD_NUM] = { 0 };
int fd;
struct itimerspec new_value;

void* child_ioctl_0(void* no_use)
{
    int ret = 1;

    while(1){
        timerfd_settime(fd, 0x3, &new_value, NULL);
        timerfd_settime(fd, 0x0, &new_value, NULL);
    }
}

int main(int argc, char *argv[])
{
    int i;
    new_value.it_value.tv_sec = 0;
    new_value.it_value.tv_nsec = 0;
    new_value.it_interval.tv_sec = 0;
    new_value.it_interval.tv_nsec = 0;

    fd = timerfd_create(CLOCK_REALTIME, 0);

    /* create thread */
    for(i = 0; i < THREAD_NUM; i = i+1) {
        thread_ret[i] = pthread_create(thread_id + i, NULL, child_ioctl_0, NULL);
    }

    while(1) {
        fd = timerfd_create(CLOCK_REALTIME, 0);
        usleep(5);
        close(fd);
    }
}
