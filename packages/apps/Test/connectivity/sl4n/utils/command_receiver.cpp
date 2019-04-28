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

#include <rapidjson/document.h>
#include <rapidjson/writer.h>
#include <rapidjson/stringbuffer.h>
#include <map>
#include <string>
#include <tuple>

#include <base.h>
#include <utils/command_receiver.h>
#include <utils/common_utils.h>

typedef std::map<std::string, MFP> function_map;
function_map* _funcMap = NULL;

void _clean_result(rapidjson::Document &doc) {
  doc.RemoveMember(sl4n::kMethodStr);
  doc.RemoveMember(sl4n::kParamsStr);
}

void initiate(rapidjson::Document &doc) {
  doc.AddMember(sl4n::kStatusStr, sl4n::kSuccessStr, doc.GetAllocator());
}

CommandReceiver::CommandReceiver() {
  if (_funcMap == NULL) {
    _funcMap = new function_map();
  }
  _funcMap->insert(std::make_pair("initiate", &initiate));
  _funcMap->insert(std::make_pair("continue", &initiate));
}

void CommandReceiver::Call(rapidjson::Document& doc) {
  std::string cmd;
  if (doc.HasMember(sl4n::kCmdStr)) {
    cmd = doc[sl4n::kCmdStr].GetString();
  } else if (doc.HasMember(sl4n::kMethodStr)) {
    cmd = doc[sl4n::kMethodStr].GetString();
  }

  function_map::const_iterator iter = _funcMap->find(cmd);
  if (iter != _funcMap->end()) {
    iter->second(doc);
  }
  _clean_result(doc);
}

void CommandReceiver::RegisterCommand(std::string name, MFP command) {
  if (_funcMap == NULL) {
    _funcMap = new function_map();
  }

  _funcMap->insert(std::make_pair(name, command));
}
