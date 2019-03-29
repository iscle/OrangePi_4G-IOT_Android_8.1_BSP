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
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <asm/ioctl.h>
#include <pthread.h>

#define DEBUG
#ifdef DEBUG
#define LOG(fmt, ...) do { \
        printf("%s:%d: "fmt "\n", __FUNCTION__, \
        __LINE__, ##__VA_ARGS__); \
} while (0)
#else
#define LOG(fmt, ...)
#endif

char *infopath = "/sys/kernel/debug/mdp/reg";
int fd1 = -1;
int fd2 = -1;

#define SIZE 2048

void Thread1(void)
{
    int ret;
    char buf[SIZE] = {0};
    fd1 = open(infopath, O_RDWR);
    while (1) {
        ret = read(fd1, buf, SIZE);
        sleep(0.1);
    }
    close(fd1);
}

void Thread2(void)
{
    int i;
    while(1) {
        fd2 = open(infopath, O_RDWR);
        if(fd2 > 0)
        {
            close(fd2);
            fd2 = -1;
        }
        sleep(0.1);
    }
}

void trigger()
{
    int i, ret;
    pthread_t tid_a;
    pthread_t tid_b;

    ret = pthread_create((pthread_t *) &tid_a, NULL, (void *) Thread1, NULL);
    ret = pthread_create((pthread_t *) &tid_b, NULL, (void *) Thread2, NULL);

    i = 200;
    do {
        sleep(1);
    } while(i-- > 0);

    pthread_join(tid_a, NULL);
    pthread_join(tid_b, NULL);
}

int main()
{
    trigger();
    return 0;
}
