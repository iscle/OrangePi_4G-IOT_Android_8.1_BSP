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

#include <base.h>
#include <base/logging.h>
#include <rapidjson/document.h>
#include <rapidjson/writer.h>
#include <rapidjson/stringbuffer.h>
#include "test_facade.h"
#include <tuple>
#include <utils/command_receiver.h>
#include <utils/common_utils.h>

std::tuple<bool, int> TestFacade::TestBoolTrueReturn() {
  return std::make_tuple(true, sl4n_error_codes::kPassInt);
}

std::tuple<bool, int> TestFacade::TestBoolFalseReturn() {
  return std::make_tuple(false, sl4n_error_codes::kPassInt);
}

std::tuple<bool, int> TestFacade::TestErrorCodeFail() {
  return std::make_tuple(true, sl4n_error_codes::kFailInt);
}

std::tuple<int, int> TestFacade::TestNullReturn() {
  return std::make_tuple(NULL, sl4n_error_codes::kPassInt);
}

std::tuple<std::string, int> TestFacade::TestStringEmptyReturn() {
  return std::make_tuple("", sl4n_error_codes::kPassInt);
}

std::tuple<std::string, int> TestFacade::TestStringMaxReturn(
  std::string max_string) {

  return std::make_tuple(max_string, sl4n_error_codes::kPassInt);
}

std::tuple<bool, int> TestFacade::TestSpecificParamNaming(
  std::string test_string, int test_int) {

  return std::make_tuple(true, sl4n_error_codes::kPassInt);
}

//////////////////
// wrappers
//////////////////

static TestFacade facade;  // triggers registration with CommandReceiver

void test_bool_true_return_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 0;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  bool result;
  int error_code;
  std::tie(result, error_code) = facade.TestBoolTrueReturn();
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
  } else {
    doc.AddMember(sl4n::kResultStr, result, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
}

void test_bool_false_return_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 0;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  int result;
  int error_code;
  std::tie(result, error_code) = facade.TestBoolFalseReturn();
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
  } else {
    doc.AddMember(sl4n::kResultStr, result, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
}

void test_null_return_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 0;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  int result;
  int error_code;
  std::tie(result, error_code) = facade.TestNullReturn();
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
  } else {
    doc.AddMember(sl4n::kResultStr, result, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
}

void test_string_empty_return_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 0;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  std::string result;
  int error_code;
  std::tie(result, error_code) = facade.TestStringEmptyReturn();
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
  } else {
    rapidjson::Value tmp;
    tmp.SetString(result.c_str(), doc.GetAllocator());
    doc.AddMember(sl4n::kResultStr, tmp, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
}

void test_string_max_return_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 1;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  std::string max_string;
  if (!doc[sl4n::kParamsStr][0].IsString()) {
    LOG(ERROR) << sl4n::kTagStr << ": Expected String input for name";
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
    return;
  } else {
    max_string = doc[sl4n::kParamsStr][0].GetString();
  }
  std::string result;
  int error_code;
  std::tie(result, error_code) = facade.TestStringMaxReturn(max_string);
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
  } else {
    rapidjson::Value tmp;
    tmp.SetString(result.c_str(), doc.GetAllocator());
    doc.AddMember(sl4n::kResultStr, tmp, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
}

void test_specific_param_naming_wrapper(rapidjson::Document &doc) {
  int expected_param_size = 1;
  if (!CommonUtils::IsParamLengthMatching(doc, expected_param_size)) {
    return;
  }
  std::string string_test;
  int int_test;
  std::string string_member = "string_test";
  std::string int_member = "int_test";
  if (!doc[sl4n::kParamsStr][0][0].HasMember(string_member.c_str())) {
    LOG(ERROR) << sl4n::kTagStr << ": Expected member " << string_member;
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
    return;
  } else {
    if (!doc[sl4n::kParamsStr][0][0][string_member.c_str()].IsString()) {
      LOG(ERROR) << sl4n::kTagStr << ": Expected String input for "
        << string_member;
      doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
      doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
      return;
    }
    string_test = doc[sl4n::kParamsStr][0][0][string_member.c_str()].GetString();
  }
  if (!doc[sl4n::kParamsStr][0][0].HasMember(int_member.c_str())) {
    LOG(ERROR) << sl4n::kTagStr << ": Expected member " << int_member;
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
    return;
  } else {
    if (!doc[sl4n::kParamsStr][0][0][int_member.c_str()].IsInt()) {
      LOG(ERROR) << sl4n::kTagStr << ": Expected Int input for "
        << int_member;
      doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
      doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
      return;
    }
    int_test = doc[sl4n::kParamsStr][0][0][int_member.c_str()].GetInt();
  }
  bool result;
  int error_code;
  std::tie(result, error_code) = facade.TestSpecificParamNaming(
    string_test, int_test);
  if (error_code == sl4n_error_codes::kFailInt) {
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kFailStr, doc.GetAllocator());
  } else {
    doc.AddMember(sl4n::kResultStr, result, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, NULL, doc.GetAllocator());
  }
}

////////////////
// constructor
////////////////

TestFacade::TestFacade() {

  CommandReceiver::RegisterCommand("TestBoolTrueReturn",
    &test_bool_true_return_wrapper);
  CommandReceiver::RegisterCommand("TestBoolFalseReturn",
    &test_bool_false_return_wrapper);
  CommandReceiver::RegisterCommand("TestNullReturn",
    &test_null_return_wrapper);
  CommandReceiver::RegisterCommand("TestStringEmptyReturn",
    &test_string_empty_return_wrapper);
  CommandReceiver::RegisterCommand("TestStringMaxReturn",
    &test_string_max_return_wrapper);
  CommandReceiver::RegisterCommand("TestSpecificParamNaming",
    &test_specific_param_naming_wrapper);
}

