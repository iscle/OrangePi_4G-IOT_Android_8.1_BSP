//
//  Copyright (C) 2016 Google, Inc.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

#pragma once

#include <rapidjson/document.h>
#include <tuple>

class TestFacade {
 public:
  TestFacade();
  std::tuple<bool, int> TestBoolTrueReturn();
  std::tuple<bool, int> TestBoolFalseReturn();
  std::tuple<bool, int> TestErrorCodeFail();
  std::tuple<int, int> TestNullReturn();
  std::tuple<std::string, int> TestStringEmptyReturn();
  std::tuple<std::string, int> TestStringMaxReturn(
    std::string max_string);
  std::tuple<bool, int> TestSpecificParamNaming(
    std::string string_test, int int_test);

};

