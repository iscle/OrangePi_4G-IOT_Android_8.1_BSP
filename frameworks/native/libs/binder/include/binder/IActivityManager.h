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

#ifndef ANDROID_IACTIVITY_MANAGER_H
#define ANDROID_IACTIVITY_MANAGER_H

#include <binder/IInterface.h>

namespace android {

// ------------------------------------------------------------------------------------

class IActivityManager : public IInterface
{
public:
    DECLARE_META_INTERFACE(ActivityManager)

    virtual int openContentUri(const String16& /* stringUri */) = 0;

    enum {
        OPEN_CONTENT_URI_TRANSACTION = IBinder::FIRST_CALL_TRANSACTION
    };
};

// ------------------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_IACTIVITY_MANAGER_H