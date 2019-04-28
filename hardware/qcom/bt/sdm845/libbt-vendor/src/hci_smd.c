/*
 * Copyright 2012 The Android Open Source Project
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

/******************************************************************************
 *
 *  Filename:      hci_smd.c
 *
 *  Description:   Contains vendor-specific userial functions
 *
 ******************************************************************************/

#define LOG_TAG "bt_vendor"

#include <utils/Log.h>
#include <termios.h>
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include "bt_vendor_qcom.h"
#include "hci_smd.h"
#include <string.h>
#include <cutils/properties.h>

/*****************************************************************************
**   Macros & Constants
*****************************************************************************/
#define NUM_OF_DEVS 2
static char *s_pszDevSmd[] = {
    "/dev/smd3",
    "/dev/smd2"
};

/******************************************************************************
**  Externs
******************************************************************************/
extern int is_bt_ssr_hci;


/*****************************************************************************
**   Functions
*****************************************************************************/

int bt_hci_init_transport_id (int chId )
{
  struct termios   term;
  int fd = -1;
  int retry = 0;
  char ssrvalue[92]= {'\0'};

  ssrvalue[0] = '0';
  if(chId >= 2 || chId <0)
     return -1;

  fd = open(s_pszDevSmd[chId], (O_RDWR | O_NOCTTY));

  while ((-1 == fd) && (retry < 7)) {
    ALOGE("init_transport: Cannot open %s: %s\n. Retry after 2 seconds",
        s_pszDevSmd[chId], strerror(errno));
    usleep(2000000);
    fd = open(s_pszDevSmd[chId], (O_RDWR | O_NOCTTY));
    retry++;
  }

  if (-1 == fd)
  {
    ALOGE("init_transport: Cannot open %s: %s\n",
        s_pszDevSmd[chId], strerror(errno));
    return -1;
  }

  /* Sleep (0.5sec) added giving time for the smd port to be successfully
     opened internally. Currently successful return from open doesn't
     ensure the smd port is successfully opened.
     TODO: Following sleep to be removed once SMD port is successfully
     opened immediately on return from the aforementioned open call */

  property_get("bluetooth.isSSR", ssrvalue, "");

  if(ssrvalue[0] == '1')
  {
      /*reset the SSR flag */
      if(chId == 1)
      {
          if(property_set("bluetooth.isSSR", "0") < 0)
          {
              ALOGE("SSR: hci_smd.c:SSR case : error in setting up property new\n ");
          }
          else
          {
              ALOGE("SSR: hci_smd.c:SSR case : Reset the SSr Flag new\n ");
          }
      }
      ALOGE("hci_smd.c:IN SSR sleep for 500 msec New \n");
      usleep(500000);
  }

  if (tcflush(fd, TCIOFLUSH) < 0)
  {
    ALOGE("init_uart: Cannot flush %s\n", s_pszDevSmd[chId]);
    close(fd);
    return -1;
  }

  if (tcgetattr(fd, &term) < 0)
  {
    ALOGE("init_uart: Error while getting attributes\n");
    close(fd);
    return -1;
  }

  cfmakeraw(&term);

  /* JN: Do I need to make flow control configurable, since 4020 cannot
   * disable it?
   */
  term.c_cflag |= (CRTSCTS | CLOCAL);

  if (tcsetattr(fd, TCSANOW, &term) < 0)
  {
    ALOGE("init_uart: Error while getting attributes\n");
    close(fd);
    return -1;
  }

  ALOGI("Done intiailizing UART\n");
  return fd;
}

int bt_hci_init_transport(int *pFd)
{
  int i = 0;
  int fd;
  for(i=0; i < NUM_OF_DEVS; i++){
    fd = bt_hci_init_transport_id(i);
    if(fd < 0 ){
      return -1;
    }
    pFd[i] = fd;
   }
   return 0;
}

int bt_hci_deinit_transport(int *pFd)
{
    close(pFd[0]);
    close(pFd[1]);
    return TRUE;
}
