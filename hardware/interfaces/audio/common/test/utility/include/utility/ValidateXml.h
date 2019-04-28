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

#ifndef ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_VALIDATE_XML_H
#define ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_VALIDATE_XML_H

#include <gtest/gtest.h>

namespace android {
namespace hardware {
namespace audio {
namespace common {
namespace test {
namespace utility {

/** Validate the provided XmlFile with the provided xsdFile.
 * Intended to use with ASSERT_PRED_FORMAT2 as such:
 *   ASSERT_PRED_FORMAT2(validateXml, pathToXml, pathToXsd);
 * See ASSERT_VALID_XML for a helper macro.
 */
::testing::AssertionResult validateXml(const char* xmlFilePathExpr, const char* xsdFilePathExpr,
                                       const char* xmlFilePath, const char* xsdPathName);

/** Helper gtest ASSERT to test xml validity against an xsd. */
#define ASSERT_VALID_XML(xmlFilePath, xsdFilePath)                                      \
    ASSERT_PRED_FORMAT2(::android::hardware::audio::common::test::utility::validateXml, \
                        xmlFilePath, xsdFilePath)

}  // utility
}  // test
}  // common
}  // audio
}  // test
}  // utility

#endif  // ANDROID_HARDWARE_AUDIO_COMMON_TEST_UTILITY_VALIDATE_XML_H
