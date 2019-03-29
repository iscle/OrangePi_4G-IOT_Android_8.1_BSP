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

#define LOG_NDEBUG 0
#define LOG_TAG "AslrMallocTest"

#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <linux/limits.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <unordered_set>

#include <gtest/gtest.h>
#include <string>
#include <utils/Log.h>

/* minimum entropy for malloc return addresses */
const size_t minEntropyBits = 8;

/* test using the following allocation sizes */
const size_t allocSizes[] = {
    1 << 8,     // small
    1 << 16,    // large
    1 << 23     // huge
};

/* when started using this argument followed by the allocation size,
 * performs malloc(size) and prints out the address */
static const std::string argPrint = "--print-malloc-address";

class AslrMallocTest : public ::testing::Test
{
protected:
    std::string self_;

    AslrMallocTest() {}
    virtual ~AslrMallocTest() {}

    virtual void SetUp()
    {
        /* path to self for exec */
        char path[PATH_MAX];
        auto size = readlink("/proc/self/exe", path, sizeof(path));
        ASSERT_TRUE(size > 0 && size < PATH_MAX);
        path[size] = '\0';
        self_ = path;
    }

    void GetAddress(size_t allocSize, uintptr_t& address)
    {
        int fds[2];
        ASSERT_TRUE(pipe(fds) != -1);

        auto pid = fork();
        ASSERT_TRUE(pid != -1);

        if (pid == 0) {
            /* child process */
            ASSERT_TRUE(TEMP_FAILURE_RETRY(dup2(fds[1], STDOUT_FILENO)) != -1);

            for (auto fd : fds) {
                TEMP_FAILURE_RETRY(close(fd));
            }

            /* exec self to print malloc output */
            ASSERT_TRUE(execl(self_.c_str(), self_.c_str(), argPrint.c_str(),
                android::base::StringPrintf("%zu", allocSize).c_str(),
                nullptr) != -1);
        }

        /* parent process */
        TEMP_FAILURE_RETRY(close(fds[1]));

        std::string output;
        ASSERT_TRUE(android::base::ReadFdToString(fds[0], &output));
        TEMP_FAILURE_RETRY(close(fds[0]));

        int status;
        ASSERT_TRUE(waitpid(pid, &status, 0) != -1);
        ASSERT_TRUE(WEXITSTATUS(status) == EXIT_SUCCESS);

        ASSERT_TRUE(android::base::ParseUint(output.c_str(), &address));
    }

    void TestRandomization()
    {
        /* should be sufficient to see minEntropyBits when rounded up */
        size_t iterations = 2 * (1 << minEntropyBits);

        for (auto size : allocSizes) {
            ALOGV("running %zu iterations for allocation size %zu",
                iterations, size);

            /* collect unique return addresses */
            std::unordered_set<uintptr_t> addresses;

            for (size_t i = 0; i < iterations; ++i) {
                uintptr_t address;
                GetAddress(size, address);

                addresses.emplace(address);
            }

            size_t entropy = static_cast<size_t>(0.5 +
                                log2(static_cast<double>(addresses.size())));

            ALOGV("%zu bits of entropy for allocation size %zu (minimum %zu)",
                entropy, size, minEntropyBits);
            ALOGE_IF(entropy < minEntropyBits,
                "insufficient entropy for malloc(%zu)", size);
            ASSERT_TRUE(entropy >= minEntropyBits);
        }
    }
};

TEST_F(AslrMallocTest, testMallocRandomization) {
    TestRandomization();
}

int main(int argc, char **argv)
{
    if (argc == 3 && argPrint == argv[1]) {
        size_t size;

        if (!android::base::ParseUint(argv[2], &size)) {
            return EXIT_FAILURE;
        }

        void* p = malloc(size);
        printf("%p", p);
        free(p);
        return EXIT_SUCCESS;
    }

    testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
