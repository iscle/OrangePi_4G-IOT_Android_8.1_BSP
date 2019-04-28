/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
 * Copyright (C) 2013 Hewlett-Packard Development Company, L.P.
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

#ifndef __PLUGIN_DB_H__
#define __PLUGIN_DB_H__

#include "lib_wprint.h"
#include "wprint_debug.h"

/*
 * Reset the plugin
 */
void plugin_reset();

/*
 * Adds wprint mime types and print formats to the plugin
 */
int plugin_add(wprint_plugin_t *plugin);

/*
 * Find mime type and print format match
 */
wprint_plugin_t *plugin_search(const char *mt, const char *pf);

/*
 * Returns a bit that represents a mime type
 */
unsigned long long plugin_get_mime_type_bit(const char *mime_type);

/*
 * Outputs wprint's supported input formats into input_formats
 */
void plugin_get_passthru_input_formats(unsigned long long *input_formats);

#endif // __PLUGIN_DB_H__