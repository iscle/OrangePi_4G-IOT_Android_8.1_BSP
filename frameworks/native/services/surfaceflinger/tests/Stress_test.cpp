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

#include <gtest/gtest.h>

#include <gui/SurfaceComposerClient.h>

#include <utils/String8.h>

#include <thread>
#include <functional>


namespace android {

TEST(SurfaceFlingerStress, create_and_destroy) {
    auto do_stress = []() {
        sp<SurfaceComposerClient> client = new SurfaceComposerClient;
        ASSERT_EQ(NO_ERROR, client->initCheck());
        for (int j = 0; j < 1000; j++) {
            auto surf = client->createSurface(String8("t"), 100, 100,
                    PIXEL_FORMAT_RGBA_8888, 0);
            ASSERT_TRUE(surf != nullptr);
            client->destroySurface(surf->getHandle());
        }
    };

    std::vector<std::thread> threads;
    for (int i = 0; i < 10; i++) {
        threads.push_back(std::thread(do_stress));
    }
    for (auto& thread : threads) {
        thread.join();
    }
}

}
