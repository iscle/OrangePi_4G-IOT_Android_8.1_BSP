/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef HW_EMULATOR_CAMERA_WORKER_THREAD_H
#define HW_EMULATOR_CAMERA_WORKER_THREAD_H

#include <utils/Thread.h>

namespace android {

class EmulatedCamera;
class EmulatedCameraDevice;

class WorkerThread : public Thread {
public:
    WorkerThread(const char* threadName,
                 EmulatedCameraDevice* camera_dev,
                 EmulatedCamera* cameraHAL);
    virtual ~WorkerThread() {}

    /* Starts the thread
     * Param:
     *  oneBurst - Controls how many times thread loop should run. If
     *      this parameter is 'true', thread routine will run only once
     *      If this parameter is 'false', thread routine will run until
     *      stopThread method is called. See startWorkerThread for more
     *      info.
     * Return:
     *  NO_ERROR on success, or an appropriate error status.
     */
    status_t startThread(bool oneBurst);

    /* Stops the thread, this only requests that the thread exits. The method
     * will return right after the request has been made. Use joinThread to
     * wait for the thread to exit. */
    status_t stopThread();

    /* Wake a thread that's currently waiting to timeout or to be awoken */
    status_t wakeThread();

    /* Join the thread, waits until the thread exits before returning. */
    status_t joinThread();

protected:
    /* Perform whatever work should be done in the worker thread. A subclass
     * needs to implement this.
     * Return:
     *  true To continue thread loop, or false to exit the thread loop and
     *  terminate the thread.
     */
    virtual bool inWorkerThread() = 0;

    /* This provides an opportunity for a subclass to perform some operation
     * when the thread starts. This is run on the newly started thread. If this
     * returns an error the thread will exit and inWorkerThread will never be
     * called.
     */
    virtual status_t onThreadStart() { return NO_ERROR; }

    /* This provides an opportunity for a subclass to perform some operation
     * when the thread exits. This is run on the worker thread. By default this
     * does nothing.
     */
    virtual void onThreadExit() { }

    /* Containing camera device object. */
    EmulatedCameraDevice* mCameraDevice;
    /* The camera HAL from the camera device object */
    EmulatedCamera* mCameraHAL;

    /* Controls number of times the thread loop runs.
     * See startThread for more information. */
    bool mOneBurst;

    /* Running Condition and mutex, use these to sleep the thread, the
     * supporting functions will use these to signal wakes and exits. */
    Condition mRunningCondition;
    Mutex mRunningMutex;
    bool mRunning;
private:
    /* Overriden base class method.
     * It is overriden in order to provide one-time initialization just
     * prior to starting the thread routine.
     */
    status_t readyToRun() final;

    /* Implements abstract method of the base Thread class. */
    bool threadLoop() final;

    const char* mThreadName;
};

}  // namespace android

#endif  // HW_EMULATOR_CAMERA_WORKER_THREAD_H
