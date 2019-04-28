/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef MUTEX_H_
#define MUTEX_H_

#include "pthread.h"

// Based on utils/threads.h, but tailored to build with the NDK and used unbundled.
// This is a simple wrapper over the pthread_mutex_t type.
class Mutex {
public:
    Mutex() {
        pthread_mutex_init(&mMutex, NULL);
    }
    int lock() {
        return -pthread_mutex_lock(&mMutex);
    }
    void unlock() {
        pthread_mutex_unlock(&mMutex);
    }
    ~Mutex() {
        pthread_mutex_destroy(&mMutex);
    }

    // A simple class that locks a given mutex on construction
    // and unlocks it when it goes out of scope.
    class Autolock {
    public:
        Autolock(Mutex &mutex) : lock(&mutex) {
            lock->lock();
        }
        ~Autolock() {
            lock->unlock();
        }
    private:
        Mutex *lock;
    };

private:
    pthread_mutex_t mMutex;

    // Disallow copy and assign.
    Mutex(const Mutex&);
    Mutex& operator=(const Mutex&);
};

#endif  // MUTEX_H_
