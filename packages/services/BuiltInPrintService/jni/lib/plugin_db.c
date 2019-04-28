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

#include <stdio.h>
#include <string.h>
#include "plugin_db.h"

#define TAG "plugin_db"

#define _MAX_MIME_TYPES      32
#define _MAX_PRINT_FORMATS    8
#define _MAX_PLUGINS         16

typedef struct {
    wprint_plugin_t *plugin;
    const char *name;
    char mime_types[_MAX_MIME_TYPES][MAX_MIME_LENGTH + 1];
    char print_formats[_MAX_PRINT_FORMATS][MAX_MIME_LENGTH + 1];
    int version;
    wprint_priority_t priority;
} _plugin_db_t;

static _plugin_db_t _plugin[_MAX_PLUGINS];
static int _index = 0;

void plugin_reset() {
    _index = 0;
}

int plugin_add(wprint_plugin_t *plugin) {
    char const **mt, **pf;
    int i, j, index;

    if (plugin == NULL) {
        return ERROR;
    }

    index = _index;
    mt = plugin->get_mime_types();
    pf = plugin->get_print_formats();

    if ((mt == NULL) || (pf == NULL)) {
        return ERROR;
    }

    memset(&_plugin[index], 0, sizeof(_plugin_db_t));
    _plugin[index].version = plugin->version;
    _plugin[index].plugin = plugin;
    _plugin[index].priority = plugin->priority;

    LOGI("MIME types:");
    // save a pointer to the name for comparison

    i = j = 0;
    while (mt[i]) {
        if (strlen(mt[i]) < MAX_MIME_LENGTH) {
            LOGI(" %s", mt[i]);
            strncpy(_plugin[index].mime_types[j++], mt[i], MAX_MIME_LENGTH);
        }
        i++;
    }
    if (j < _MAX_MIME_TYPES) {
        _plugin[index].mime_types[j][0] = 0;
    }

    LOGI("print formats:");

    i = j = 0;
    while (pf[i]) {
        if (strlen(pf[i]) < MAX_MIME_LENGTH) {
            LOGI(" %s", pf[i]);
            strncpy(_plugin[index].print_formats[j++], pf[i], MAX_MIME_LENGTH);
        }
        i++;
    }
    if (j < _MAX_PRINT_FORMATS) {
        _plugin[index].print_formats[j][0] = 0;
    }

    _index++;

    return OK;
}

wprint_plugin_t *plugin_search(const char *mt, const char *pf) {
    int i, j, k;
    _plugin_db_t *match = NULL;

    for (i = 0; i < _index; i++) {
        j = 0;
        while (strlen(_plugin[i].print_formats[j])) {
            if (strcmp(_plugin[i].print_formats[j], pf) == 0) {
                k = 0;
                while (strlen(_plugin[i].mime_types[k])) {
                    if (strcmp(_plugin[i].mime_types[k], mt) == 0) {
                        bool use;
                        use = ((match == NULL) || (_plugin[i].priority < match->priority));
                        if (use) {
                            match = &_plugin[i];
                        }
                    }
                    k++;
                }
            }
            j++;
        }
    }
    return ((match != NULL) ? match->plugin : NULL);
}

unsigned long long plugin_get_mime_type_bit(const char *mime_type) {
    unsigned long long bit = 0;
    if (strcmp(MIME_TYPE_PDF, mime_type) == 0) {
        bit = (unsigned long long) (1 << INPUT_MIME_TYPE_PDF);
    } else if (strcmp(MIME_TYPE_PCLM, mime_type) == 0) {
        bit = (unsigned long long) (1 << INPUT_MIME_TYPE_PCLM);
    } else if (strcmp(MIME_TYPE_PWG, mime_type) == 0) {
        bit = (unsigned long long) (1 << INPUT_MIME_TYPE_PWG);
    }
    return bit;
}

void plugin_get_passthru_input_formats(unsigned long long *input_formats) {
    int i;
    *input_formats = 0;
    for (i = 0; i < _index; i++) {
        // is this a passthrough plugin
        if ((strcmp(_plugin[i].mime_types[0], _plugin[i].print_formats[0]) == 0) &&
                (strlen(_plugin[i].print_formats[1]) == 0)
                && (strlen(_plugin[i].mime_types[1]) == 0)) {
            *input_formats |= plugin_get_mime_type_bit(_plugin[i].mime_types[0]);
        }
    }
}