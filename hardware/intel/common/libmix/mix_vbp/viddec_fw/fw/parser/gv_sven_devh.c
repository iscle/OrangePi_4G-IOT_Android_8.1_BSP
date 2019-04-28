/*

  This file is provided under a dual BSD/GPLv2 license.  When using or
  redistributing this file, you may do so under either license.

  GPL LICENSE SUMMARY

  Copyright(c) 2005-2008 Intel Corporation. All rights reserved.

  This program is free software; you can redistribute it and/or modify
  it under the terms of version 2 of the GNU General Public License as
  published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful, but
  WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 51 Franklin St - Fifth Floor, Boston, MA 02110-1301 USA.
  The full GNU General Public License is included in this distribution
  in the file called LICENSE.GPL.

  Contact Information:
    Intel Corporation
    2200 Mission College Blvd.
    Santa Clara, CA  97052

  BSD LICENSE

  Copyright(c) 2005-2008 Intel Corporation. All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions
  are met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in
      the documentation and/or other materials provided with the
      distribution.
    * Neither the name of Intel Corporation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

#ifndef SVEN_FW_H
#include "sven_fw.h"
#endif

#define _OSAL_IO_MEMMAP_H  /* to prevent errors when including sven_devh.h */
#define _OSAL_ASSERT_H     /* to prevent errors when including sven_devh.h */
#include "sven_devh.h"

#include "fw_pvt.h"

static os_devhandle_t         g_svenh;

#define FW_SVEN_DEVH_DISABLE_SVEN_REGISTER_IO
//#define SVEN_DEVH_DISABLE_SVEN

extern int sven_fw_is_tx_enabled(
   struct SVENHandle       *svenh );

#ifndef SVEN_DEVH_DISABLE_SVEN
static void sven_write_event(
   struct SVENHandle        *svenh,
   struct SVENEvent         *ev )
{
   if ( NULL == svenh )
      svenh = &g_svenh.devh_svenh;

   if ( NULL != svenh->phot )
      sven_fw_write_event(svenh,ev);
}

static void sven_fw_initialize_event_top(
   struct SVENEvent         *ev,
    int                      module,
    int                      unit,
    int                      event_type,
    int                      event_subtype )
{
    ev->se_et.et_gencount = 0;
    ev->se_et.et_module = module;
    ev->se_et.et_unit = unit;
    ev->se_et.et_type = event_type;
    ev->se_et.et_subtype = event_subtype;
}
#endif

uint32_t sven_get_timestamp()
{
   uint32_t    value = 0;

   if ( NULL != g_svenh.devh_svenh.ptime )
   {
      value = sven_fw_read_external_register( &g_svenh.devh_svenh, g_svenh.devh_svenh.ptime );
   }

   return(value);
}

/* ---------------------------------------------------------------------- */
/* ---------------------------------------------------------------------- */

void devh_SVEN_SetModuleUnit(
    os_devhandle_t          *devh,
    int                      sven_module,
    int                      sven_unit )
{
#ifndef SVEN_DEVH_DISABLE_SVEN
   if ( NULL == devh )
      devh = &g_svenh;
   devh->devh_sven_module = sven_module;
   devh->devh_sven_unit = sven_unit;
#endif
}

os_devhandle_t *devhandle_factory( const char *desc )
{
   /* pointer to global vsparc local registers */
   g_svenh.devh_regs_ptr = (void *) 0x10000000;   /* firmware address to Local (GV) registers */

   return( &g_svenh );
}

int devhandle_connect_name(
    os_devhandle_t          *devh,
    const char              *devname )
{
   return(1);
}

/* ---------------------------------------------------------------------- */
/* ---------------------------------------------------------------------- */

void devh_SVEN_WriteModuleEvent(
    os_devhandle_t  *devh,
    int              module_event_subtype,
    unsigned int     payload0,
    unsigned int     payload1,
    unsigned int     payload2,
    unsigned int     payload3,
    unsigned int     payload4,
    unsigned int     payload5 )
{
#ifndef SVEN_DEVH_DISABLE_SVEN
    struct SVENEvent        ev __attribute__ ((aligned(8)));

    devh = (NULL != devh) ? devh :  &g_svenh;

    if ( ! sven_fw_is_tx_enabled( &devh->devh_svenh ) )
        return;

    sven_fw_initialize_event_top( &ev,
        devh->devh_sven_module,
        1 /* devh->devh_sven_unit */,
        SVEN_event_type_module_specific,
        module_event_subtype );

    ev.u.se_uint[0]        = payload0;
    ev.u.se_uint[1]        = payload1;
    ev.u.se_uint[2]        = payload2;
    ev.u.se_uint[3]        = payload3;
    ev.u.se_uint[4]        = payload4;
    ev.u.se_uint[5]        = payload5;

    sven_write_event( &devh->devh_svenh, &ev );
#endif
}

/* ---------------------------------------------------------------------- */
/* SVEN FW TX: Required custom routines to enable FW TX                   */
/* ---------------------------------------------------------------------- */
int sven_fw_set_globals(
   struct SVEN_FW_Globals  *fw_globals )
{
   sven_fw_attach( &g_svenh.devh_svenh, fw_globals );
   devh_SVEN_SetModuleUnit( &g_svenh, SVEN_module_GEN4_GV, 1 );
   return(0);
}

uint32_t cp_using_dma_phys(uint32_t ddr_addr, uint32_t local_addr, uint32_t size, char to_ddr, char swap);

unsigned int sven_fw_read_external_register(
   struct SVENHandle       *svenh,
   volatile unsigned int   *preg )
{
   unsigned int      reg __attribute__ ((aligned(8)));

   (void)svenh;   // argument unused

   cp_using_dma_phys( (uint32_t) preg, (uint32_t) &reg, 4, 0, 0 );

   return( reg );
}

void sven_fw_copy_event_to_host_mem(
   struct SVENHandle          *svenh,
   volatile struct SVENEvent  *to,
   const struct SVENEvent     *from )
{
   (void)svenh;   // argument unused

   cp_using_dma_phys( (uint32_t) to, (uint32_t) from, sizeof(*to), 1, 0 );
}
/* ---------------------------------------------------------------------- */
/* ---------------------------------------------------------------------- */
