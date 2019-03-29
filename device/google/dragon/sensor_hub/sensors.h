/*
 * Copyright (C) 2008-2015 The Android Open Source Project
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

#ifndef SENSORS_H_

#include <poll.h>

#include <hardware/hardware.h>
#include <hardware/sensors.h>

/*
 * cros_ec_sensors_poll_context_t:
 *
 * Responsible for implementing the pool functions.
 * We are currently polling on 2 files:
 * - the IIO ring buffer (via CrosECSensor object)
 * - a pipe to sleep on. a call to activate() will wake up
 *   the context poll() is running in.
 *
 * This code could accomodate more than one ring buffer.
 * If we implement wake up/non wake up sensors, we would lister to
 * iio buffer woken up by sysfs triggers.
 */
struct cros_ec_sensors_poll_context_t {
    sensors_poll_device_1_t device; // must be first

    cros_ec_sensors_poll_context_t(
            const struct hw_module_t *module,
            const char *ring_device_name,
            const char *ring_trigger_name);

    private:
    enum {
        crosEcRingFd           = 0,
        crosEcWakeFd,
        numFds,
    };

    static const char WAKE_MESSAGE = 'W';
    struct pollfd mPollFds[numFds];
    int mWritePipeFd;
    CrosECSensor *mSensor;

    ~cros_ec_sensors_poll_context_t();

    int activate(int handle, int enabled);
    int setDelay(int handle, int64_t ns);
    int pollEvents(sensors_event_t* data, int count);
    int batch(int handle, int flags, int64_t period_ns, int64_t timeout);
    int flush(int handle);

    static int wrapper_close(struct hw_device_t *dev);
    static int wrapper_activate(struct sensors_poll_device_t *dev,
            int handle, int enabled);
    static int wrapper_setDelay(struct sensors_poll_device_t *dev,
            int handle, int64_t ns);
    static int wrapper_poll(struct sensors_poll_device_t *dev,
            sensors_event_t* data, int count);
    static int wrapper_batch(struct sensors_poll_device_1 *dev,
            int handle, int flags, int64_t period_ns, int64_t timeout);
    static int wrapper_flush(struct sensors_poll_device_1 *dev,
            int handle);

};



#define SENSORS_H_
#endif  /* SENSORS_H_ */
