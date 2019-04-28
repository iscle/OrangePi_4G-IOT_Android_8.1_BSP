/*
 * Copyright 2015 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "MediaResourceManager_test"
#include <utils/Log.h>

#include <gtest/gtest.h>
#include "MediaResourceArbitrator.h"

using namespace android;


class MediaResourceManagerTest : public ::testing::Test {
public:
    MediaResourceManagerTest()
        : mArbitrator(new MediaResourceArbitrator) {
        mArbitrator->Config(NULL);
    }

    ~MediaResourceManagerTest() {
        delete mArbitrator;
    }

protected:
    void addDefaultResourceByN(int N) {
/*
        CodecInfo codec1;
	codec1.codecType = CODEC_TYPE_AVC;
	codec1.isEncoder = false;
        codec1.isSecured = false;
        codec1.resolution = Resolution_1080;
	codec1.frameRate = 30;
*/
        int i;
        ArbitratorErrorType err = ArbitratorErrorNone;
        for (i=0; i<N; i++) {
	    err = mArbitrator->AddResource(CODEC_TYPE_AVC,
                                           false,
                                           false,
                                           Resolution_1080,
                                           30);
            if (err == ArbitratorErrorInsufficientResources) {
                ALOGE("%dth codec can not be added anymore.", i);
                return;
            }
        }
    }

    void testAddResource(void) {
        addDefaultResourceByN(10);
        EXPECT_EQ(2u, mArbitrator->GetLivingCodecsNum());
    }


    void testRemoveResource(void) {
        addDefaultResourceByN(5);
        EXPECT_EQ(2u, mArbitrator->GetLivingCodecsNum());
        EXPECT_TRUE(mArbitrator->CheckIfFullLoad(false));
        ArbitratorErrorType err = ArbitratorErrorNone;
        err = mArbitrator->RemoveResource(CODEC_TYPE_AVC,
                                       false,
                                       false,
                                       Resolution_1080,
                                       30);
        EXPECT_EQ(1u, mArbitrator->GetLivingCodecsNum());
        EXPECT_FALSE(mArbitrator->CheckIfFullLoad(false));
    }


    void testCheckFullLoad(void) {
        EXPECT_FALSE(mArbitrator->CheckIfFullLoad(false));
        addDefaultResourceByN(5);
        EXPECT_TRUE(mArbitrator->CheckIfFullLoad(false));
    }


    void testConfigByXML(void) {
    }


    MediaResourceArbitrator* mArbitrator;
};


TEST_F(MediaResourceManagerTest, config) {
}


TEST_F(MediaResourceManagerTest, addResource) {
    testAddResource();
}


TEST_F(MediaResourceManagerTest, removeResource) {
    testRemoveResource();
}


TEST_F(MediaResourceManagerTest, checkFullLoad) {
    testCheckFullLoad();
}


TEST_F(MediaResourceManagerTest, configByXML) {
    testConfigByXML();
}



