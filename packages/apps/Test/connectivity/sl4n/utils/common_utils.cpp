//
//  Copyright (C) 2015 Google, Inc.
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

#include "base.h"
#include <base/logging.h>
#include "common_utils.h"
#include <rapidjson/document.h>

bool CommonUtils::IsParamLengthMatching(rapidjson::Document& doc,
  int expected_param_size) {

  if ((int)doc[sl4n::kParamsStr].Size() != expected_param_size) {
    LOG(ERROR) << sl4n::kTagStr << ": Invalid parameter length - found: "
      << doc[sl4n::kParamsStr].Size();
    doc.AddMember(sl4n::kResultStr, false, doc.GetAllocator());
    doc.AddMember(sl4n::kErrorStr, sl4n::kInvalidParamStr, doc.GetAllocator());
    return false;
  }
  return true;
}
