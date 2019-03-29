/**
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 **/


#define _GNU_SOURCE

#include <pthread.h>
#include <sys/types.h>
#include <sys/wait.h>

#include <sys/stat.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <string.h>
#include "local_poc.h"
#include <unistd.h>
#include <stdio.h>

struct ipa_ioc_query_intf_tx_props_2 {
        char name[IPA_RESOURCE_NAME_MAX];
        uint32_t num_tx_props;
        struct ipa_ioc_tx_intf_prop tx[2];
};

int main() {

        int fd = open("/dev/ipa", O_RDWR);

        struct ipa_ioc_query_intf query_intf;
        strlcpy(&(query_intf.name[0]), "rmnet_data0", IPA_RESOURCE_NAME_MAX);

        int result = ioctl(fd, IPA_IOC_QUERY_INTF, &query_intf);

        ipa_ioc_query_intf_tx_props_2 tx_props_2;
        memset(&tx_props_2, 0, sizeof(tx_props_2));
        strlcpy(&(tx_props_2.name[0]), "rmnet_data0", IPA_RESOURCE_NAME_MAX);
        tx_props_2.num_tx_props = 2;

        int result2 = ioctl(fd, IPA_IOC_QUERY_INTF_TX_PROPS, &tx_props_2);

        while (true) {
                ipa_ioc_query_intf_tx_props tx_props;
                memset(&tx_props, 0, sizeof(tx_props));
                strlcpy(&(tx_props.name[0]), "rmnet_data0", IPA_RESOURCE_NAME_MAX);
                tx_props.num_tx_props = 0;

                int result3 = ioctl(fd, IPA_IOC_QUERY_INTF_TX_PROPS, &tx_props);

                usleep(10000);
        }
        return 0;
}
