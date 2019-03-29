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

#include <stdlib.h>
#include <string.h>

#include <seos.h>

static bool start_task(uint32_t myTid)
{
    //todo
    (void)myTid;
    return true;
}

static void end_task(void)
{
    //todo
}

static void handle_event(uint32_t evtType, const void* evtData)
{
    //todo
    (void)evtType;
    (void)evtData;
}

APP_INIT(0, start_task, end_task, handle_event);
