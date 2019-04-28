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

#pragma once

#include <rapidjson/document.h>

typedef void (*MFP)(rapidjson::Document&);

// This class defines the functions that interact with the input JSON and
// correspondingly calls the facade associated with the input JSON doc. This
// class also contains wrapper functions to the actual SL4N Facades and does
// pre-verification before it directly interacts with the facade. The
// pre-verification includes matching parameter size and verifying each
// parameter type that is expected in the wrapping function.
class CommandReceiver {

 public:
  CommandReceiver();
  ~CommandReceiver();

  static void RegisterCommand(std::string name, MFP command);

  // Function that extracts the method/cmd parameter from the JSON doc and
  // passes the document to the corresponding wrapper function.
  void Call(rapidjson::Document& doc);

};
