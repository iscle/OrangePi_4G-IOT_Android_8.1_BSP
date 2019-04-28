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
#include <rapidjson/document.h>
#include <rapidjson/writer.h>
#include <rapidjson/stringbuffer.h>
#include "utils/command_receiver.h"

#include <arpa/inet.h>
#include <errno.h>
#include <iostream>
#include <netinet/in.h>
#include <pthread.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>

const int kBacklogInt = 10;
#define PORT 8080
// TODO: Set to a lower buffer size and read socket data until termination
#define SOCK_BUF_LEN 4096
#define MEMSET_VALUE 0

int client_sock;
int socket_desc;

void SockTest() {
  char str[SOCK_BUF_LEN];
  int listen_fd, comm_fd, c;
  struct sockaddr_in servaddr, client;
  rapidjson::Document d;
  rapidjson::StringBuffer buffer;
  rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);
  CommandReceiver cr;

  listen_fd = socket(AF_INET, SOCK_STREAM, 0);
  memset (&servaddr, MEMSET_VALUE, sizeof(servaddr));
  servaddr.sin_family = AF_INET;
  servaddr.sin_addr.s_addr = INADDR_ANY;
  servaddr.sin_port = htons(PORT);

  int bind_result = bind(
          listen_fd, (struct sockaddr *) &servaddr, sizeof(servaddr));
  if (bind_result != 0) {
    LOG(ERROR) << sl4n::kTagStr <<
      ": Failed to assign the address to the socket."
      << " Error: " << strerror(errno) << ", " << errno;
    exit(1);
  }

  int listen_result = listen(listen_fd, kBacklogInt);
  if (listen_result != 0) {
    LOG(ERROR) << sl4n::kTagStr << ": Failed to setup the passive socket."
     << " Error: " << strerror(errno) << ", " << errno;
    exit(1);
  }

  comm_fd = accept(listen_fd, (struct sockaddr*)&client, (socklen_t*)&c);
  if (comm_fd == -1) {
    LOG(ERROR) << sl4n::kTagStr << ": Failed to accept the socket."
      << " Error: " << strerror(errno) << ", " << errno;
    exit(1);
  }

  while (true) {
    memset(str, MEMSET_VALUE, sizeof(str));
    int read_result = read(comm_fd, str, SOCK_BUF_LEN);
    if (read_result < 0) {
      LOG(FATAL) << sl4n::kTagStr << ": Failed to write to the socket."
        << " Error: " << strerror(errno) << ", " << errno;
      exit(1);
    }

    d.Parse(str);
    cr.Call(d);
    d.Accept(writer);
    std::string str2 = buffer.GetString();
    str2 += '\n';
    strncpy(str, str2.c_str(), sizeof(str)-1);
    int result = write(comm_fd, str, strlen(str)+1);
    if (result < 0) {
      LOG(FATAL) << sl4n::kTagStr << ": Failed to write to the socket."
        << " Error: " << strerror(errno) << ", " << errno;
      exit(1);
    }
    d.RemoveAllMembers(); // Remove all members from the json object
    buffer.Clear();
  }
}

int main(int argc, char **argv) {
    logging::LoggingSettings log_settings;
    if (!logging::InitLogging(log_settings)) {
      LOG(ERROR) << "Failed to set up logging";
      return EXIT_FAILURE;
    }
    SockTest();
    return 0;
}
