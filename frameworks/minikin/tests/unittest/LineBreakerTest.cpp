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

#include "ICUTestBase.h"
#include <minikin/LineBreaker.h>
#include <unicode/locid.h>

namespace minikin {

typedef ICUTestBase LineBreakerTest;

TEST_F(LineBreakerTest, setLocales) {
    {
        LineBreaker lineBreaker;
        Hyphenator hyphenator;
        std::vector<Hyphenator*> hyphenators;
        hyphenators.push_back(&hyphenator);
        lineBreaker.setLocales("en-US", hyphenators);
        EXPECT_EQ(icu::Locale::getUS(), lineBreaker.mLocale);
        EXPECT_EQ(&hyphenator, lineBreaker.mHyphenator);
    }
    {
        LineBreaker lineBreaker;
        Hyphenator hyphenator1, hyphenator2;
        std::vector<Hyphenator*> hyphenators;
        hyphenators.push_back(&hyphenator1);
        hyphenators.push_back(&hyphenator2);
        lineBreaker.setLocales("fr-FR,en-US", hyphenators);
        EXPECT_EQ(icu::Locale::getFrance(), lineBreaker.mLocale);
        EXPECT_EQ(&hyphenator1, lineBreaker.mHyphenator);
    }
    {
        LineBreaker lineBreaker;
        std::vector<Hyphenator*> hyphenators;
        lineBreaker.setLocales("", hyphenators);
        EXPECT_EQ(icu::Locale::getRoot(), lineBreaker.mLocale);
        EXPECT_EQ(nullptr, lineBreaker.mHyphenator);
    }
    {
        LineBreaker lineBreaker;
        std::vector<Hyphenator*> hyphenators;
        Hyphenator hyphenator;
        hyphenators.push_back(&hyphenator);
        lineBreaker.setLocales("THISISABOGUSLANGUAGE", hyphenators);
        EXPECT_EQ(icu::Locale::getRoot(), lineBreaker.mLocale);
        EXPECT_EQ(nullptr, lineBreaker.mHyphenator);
    }
    {
        LineBreaker lineBreaker;
        Hyphenator hyphenator1, hyphenator2;
        std::vector<Hyphenator*> hyphenators;
        hyphenators.push_back(&hyphenator1);
        hyphenators.push_back(&hyphenator2);
        lineBreaker.setLocales("THISISABOGUSLANGUAGE,en-US", hyphenators);
        EXPECT_EQ(icu::Locale::getUS(), lineBreaker.mLocale);
        EXPECT_EQ(&hyphenator2, lineBreaker.mHyphenator);
    }
    {
        LineBreaker lineBreaker;
        Hyphenator hyphenator1, hyphenator2;
        std::vector<Hyphenator*> hyphenators;
        hyphenators.push_back(&hyphenator1);
        hyphenators.push_back(&hyphenator2);
        lineBreaker.setLocales("THISISABOGUSLANGUAGE,ANOTHERBOGUSLANGUAGE", hyphenators);
        EXPECT_EQ(icu::Locale::getRoot(), lineBreaker.mLocale);
        EXPECT_EQ(nullptr, lineBreaker.mHyphenator);
    }
}

}  // namespace minikin
