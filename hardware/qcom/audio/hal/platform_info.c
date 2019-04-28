/*
 * Copyright (C) 2014 The Android Open Source Project
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

#define LOG_TAG "platform_info"
#define LOG_NDDEBUG 0

#include <errno.h>
#include <stdio.h>
#include <expat.h>
#include <cutils/log.h>
#include <audio_hw.h>
#include "platform_api.h"
#include <platform.h>
#include <math.h>

typedef enum {
    ROOT,
    ACDB,
    PCM_ID,
    BACKEND_NAME,
    CONFIG_PARAMS,
    OPERATOR_SPECIFIC,
    GAIN_LEVEL_MAPPING,
    APP_TYPE,
} section_t;

typedef void (* section_process_fn)(const XML_Char **attr);

static void process_acdb_id(const XML_Char **attr);
static void process_pcm_id(const XML_Char **attr);
static void process_backend_name(const XML_Char **attr);
static void process_config_params(const XML_Char **attr);
static void process_root(const XML_Char **attr);
static void process_operator_specific(const XML_Char **attr);
static void process_gain_db_to_level_map(const XML_Char **attr);
static void process_app_type(const XML_Char **attr);

static section_process_fn section_table[] = {
    [ROOT] = process_root,
    [ACDB] = process_acdb_id,
    [PCM_ID] = process_pcm_id,
    [BACKEND_NAME] = process_backend_name,
    [CONFIG_PARAMS] = process_config_params,
    [OPERATOR_SPECIFIC] = process_operator_specific,
    [GAIN_LEVEL_MAPPING] = process_gain_db_to_level_map,
    [APP_TYPE] = process_app_type,
};

static set_parameters_fn set_parameters = &platform_set_parameters;

static section_t section;

struct platform_info {
    bool              do_full_parse;
    void             *platform;
    struct str_parms *kvpairs;
};

static struct platform_info my_data = {true, NULL, NULL};

/*
 * <audio_platform_info>
 * <acdb_ids>
 * <device name="???" acdb_id="???"/>
 * ...
 * ...
 * </acdb_ids>
 * <backend_names>
 * <device name="???" backend="???"/>
 * ...
 * ...
 * </backend_names>
 * <pcm_ids>
 * <usecase name="???" type="in/out" id="???"/>
 * ...
 * ...
 * </pcm_ids>
 * <config_params>
 *      <param key="snd_card_name" value="msm8994-tomtom-mtp-snd-card"/>
 *      <param key="operator_info" value="tmus;aa;bb;cc"/>
 *      <param key="operator_info" value="sprint;xx;yy;zz"/>
 *      ...
 *      ...
 * </config_params>
 *
 * <operator_specific>
 *      <device name="???" operator="???" mixer_path="???" acdb_id="???"/>
 *      ...
 *      ...
 * </operator_specific>
 *
 * </audio_platform_info>
 */

static void process_root(const XML_Char **attr __unused)
{
}

/* mapping from usecase to pcm dev id */
static void process_pcm_id(const XML_Char **attr)
{
    int index;

    if (strcmp(attr[0], "name") != 0) {
        ALOGE("%s: 'name' not found, no pcm_id set!", __func__);
        goto done;
    }

    index = platform_get_usecase_index((char *)attr[1]);
    if (index < 0) {
        ALOGE("%s: usecase %s in %s not found!",
              __func__, attr[1], PLATFORM_INFO_XML_PATH);
        goto done;
    }

    if (strcmp(attr[2], "type") != 0) {
        ALOGE("%s: usecase type not mentioned", __func__);
        goto done;
    }

    int type = -1;

    if (!strcasecmp((char *)attr[3], "in")) {
        type = 1;
    } else if (!strcasecmp((char *)attr[3], "out")) {
        type = 0;
    } else {
        ALOGE("%s: type must be IN or OUT", __func__);
        goto done;
    }

    if (strcmp(attr[4], "id") != 0) {
        ALOGE("%s: usecase id not mentioned", __func__);
        goto done;
    }

    int id = atoi((char *)attr[5]);

    if (platform_set_usecase_pcm_id(index, type, id) < 0) {
        ALOGE("%s: usecase %s in %s, type %d id %d was not set!",
              __func__, attr[1], PLATFORM_INFO_XML_PATH, type, id);
        goto done;
    }

done:
    return;
}

/* backend to be used for a device */
static void process_backend_name(const XML_Char **attr)
{
    int index;
    char *hw_interface = NULL;

    if (strcmp(attr[0], "name") != 0) {
        ALOGE("%s: 'name' not found, no ACDB ID set!", __func__);
        goto done;
    }

    index = platform_get_snd_device_index((char *)attr[1]);
    if (index < 0) {
        ALOGE("%s: Device %s in %s not found, no ACDB ID set!",
              __func__, attr[1], PLATFORM_INFO_XML_PATH);
        goto done;
    }

    if (strcmp(attr[2], "backend") != 0) {
        ALOGE("%s: Device %s in %s has no backed set!",
              __func__, attr[1], PLATFORM_INFO_XML_PATH);
        goto done;
    }

    if (attr[4] != NULL) {
        if (strcmp(attr[4], "interface") != 0) {
            hw_interface = NULL;
        } else {
            hw_interface = (char *)attr[5];
        }
    }

    if (platform_set_snd_device_backend(index, attr[3], hw_interface) < 0) {
        ALOGE("%s: Device %s in %s, backend %s was not set!",
              __func__, attr[1], PLATFORM_INFO_XML_PATH, attr[3]);
        goto done;
    }

done:
    return;
}

static void process_gain_db_to_level_map(const XML_Char **attr)
{
    struct amp_db_and_gain_table tbl_entry;

    if ((strcmp(attr[0], "db") != 0) ||
        (strcmp(attr[2], "level") != 0)) {
        ALOGE("%s: invalid attribute passed  %s %sexpected amp db level",
               __func__, attr[0], attr[2]);
        goto done;
    }

    tbl_entry.db = atof(attr[1]);
    tbl_entry.amp = exp(tbl_entry.db * 0.115129f);
    tbl_entry.level = atoi(attr[3]);

    //custome level should be > 0. Level 0 is fixed for default
    CHECK(tbl_entry.level > 0);

    ALOGV("%s: amp [%f]  db [%f] level [%d]", __func__,
           tbl_entry.amp, tbl_entry.db, tbl_entry.level);
    platform_add_gain_level_mapping(&tbl_entry);

done:
    return;
}

static void process_acdb_id(const XML_Char **attr)
{
    int index;

    if (strcmp(attr[0], "name") != 0) {
        ALOGE("%s: 'name' not found, no ACDB ID set!", __func__);
        goto done;
    }

    index = platform_get_snd_device_index((char *)attr[1]);
    if (index < 0) {
        ALOGE("%s: Device %s in %s not found, no ACDB ID set!",
              __func__, attr[1], PLATFORM_INFO_XML_PATH);
        goto done;
    }

    if (strcmp(attr[2], "acdb_id") != 0) {
        ALOGE("%s: Device %s in %s has no acdb_id, no ACDB ID set!",
              __func__, attr[1], PLATFORM_INFO_XML_PATH);
        goto done;
    }

    if (platform_set_snd_device_acdb_id(index, atoi((char *)attr[3])) < 0) {
        ALOGE("%s: Device %s in %s, ACDB ID %d was not set!",
              __func__, attr[1], PLATFORM_INFO_XML_PATH, atoi((char *)attr[3]));
        goto done;
    }

done:
    return;
}


static void process_operator_specific(const XML_Char **attr)
{
    snd_device_t snd_device = SND_DEVICE_NONE;

    if (strcmp(attr[0], "name") != 0) {
        ALOGE("%s: 'name' not found", __func__);
        goto done;
    }

    snd_device = platform_get_snd_device_index((char *)attr[1]);
    if (snd_device < 0) {
        ALOGE("%s: Device %s in %s not found, no ACDB ID set!",
              __func__, (char *)attr[3], PLATFORM_INFO_XML_PATH);
        goto done;
    }

    if (strcmp(attr[2], "operator") != 0) {
        ALOGE("%s: 'operator' not found", __func__);
        goto done;
    }

    if (strcmp(attr[4], "mixer_path") != 0) {
        ALOGE("%s: 'mixer_path' not found", __func__);
        goto done;
    }

    if (strcmp(attr[6], "acdb_id") != 0) {
        ALOGE("%s: 'acdb_id' not found", __func__);
        goto done;
    }

    platform_add_operator_specific_device(snd_device, (char *)attr[3], (char *)attr[5], atoi((char *)attr[7]));

done:
    return;
}

/* platform specific configuration key-value pairs */
static void process_config_params(const XML_Char **attr)
{
    if (strcmp(attr[0], "key") != 0) {
        ALOGE("%s: 'key' not found", __func__);
        goto done;
    }

    if (strcmp(attr[2], "value") != 0) {
        ALOGE("%s: 'value' not found", __func__);
        goto done;
    }

    str_parms_add_str(my_data.kvpairs, (char*)attr[1], (char*)attr[3]);
    set_parameters(my_data.platform, my_data.kvpairs);
done:
    return;
}

static void process_app_type(const XML_Char **attr)
{
    if (strcmp(attr[0], "uc_type")) {
        ALOGE("%s: uc_type not found", __func__);
        goto done;
    }

    if (strcmp(attr[2], "mode")) {
        ALOGE("%s: mode not found", __func__);
        goto done;
    }

    if (strcmp(attr[4], "bit_width")) {
        ALOGE("%s: bit_width not found", __func__);
        goto done;
    }

    if (strcmp(attr[6], "id")) {
        ALOGE("%s: id not found", __func__);
        goto done;
    }

    if (strcmp(attr[8], "max_rate")) {
        ALOGE("%s: max rate not found", __func__);
        goto done;
    }

    platform_add_app_type(attr[1], attr[3], atoi(attr[5]), atoi(attr[7]),
                          atoi(attr[9]));
done:
    return;
}

static void start_tag(void *userdata __unused, const XML_Char *tag_name,
                      const XML_Char **attr)
{
    const XML_Char              *attr_name = NULL;
    const XML_Char              *attr_value = NULL;
    unsigned int                i;


    if (my_data.do_full_parse) {
        if (strcmp(tag_name, "acdb_ids") == 0) {
            section = ACDB;
        } else if (strcmp(tag_name, "pcm_ids") == 0) {
            section = PCM_ID;
        } else if (strcmp(tag_name, "backend_names") == 0) {
            section = BACKEND_NAME;
        } else if (strcmp(tag_name, "config_params") == 0) {
            section = CONFIG_PARAMS;
        } else if (strcmp(tag_name, "operator_specific") == 0) {
            section = OPERATOR_SPECIFIC;
        } else if (strcmp(tag_name, "gain_db_to_level_mapping") == 0) {
            section = GAIN_LEVEL_MAPPING;
        } else if (strcmp(tag_name, "app_types") == 0) {
            section = APP_TYPE;
        } else if (strcmp(tag_name, "device") == 0) {
            if ((section != ACDB) && (section != BACKEND_NAME) && (section != OPERATOR_SPECIFIC)) {
                ALOGE("device tag only supported for acdb/backend names");
                return;
            }

            /* call into process function for the current section */
            section_process_fn fn = section_table[section];
            fn(attr);
        } else if (strcmp(tag_name, "usecase") == 0) {
            if (section != PCM_ID) {
                ALOGE("usecase tag only supported with PCM_ID section");
                return;
            }

            section_process_fn fn = section_table[PCM_ID];
            fn(attr);
        } else if (strcmp(tag_name, "param") == 0) {
            if (section != CONFIG_PARAMS) {
                ALOGE("param tag only supported with CONFIG_PARAMS section");
                return;
            }

            section_process_fn fn = section_table[section];
            fn(attr);
        } else if (strcmp(tag_name, "gain_level_map") == 0) {
            if (section != GAIN_LEVEL_MAPPING) {
                ALOGE("usecase tag only supported with GAIN_LEVEL_MAPPING section");
                return;
            }

            section_process_fn fn = section_table[GAIN_LEVEL_MAPPING];
            fn(attr);
        } else if (!strcmp(tag_name, "app")) {
            if (section != APP_TYPE) {
                ALOGE("app tag only valid in section APP_TYPE");
                return;
            }

            section_process_fn fn = section_table[APP_TYPE];
            fn(attr);
        }
    } else {
        if(strcmp(tag_name, "config_params") == 0) {
            section = CONFIG_PARAMS;
        } else if (strcmp(tag_name, "param") == 0) {
            if (section != CONFIG_PARAMS) {
                ALOGE("param tag only supported with CONFIG_PARAMS section");
                return;
            }

            section_process_fn fn = section_table[section];
            fn(attr);
        }
    }

    return;
}

static void end_tag(void *userdata __unused, const XML_Char *tag_name)
{
    if (strcmp(tag_name, "acdb_ids") == 0) {
        section = ROOT;
    } else if (strcmp(tag_name, "pcm_ids") == 0) {
        section = ROOT;
    } else if (strcmp(tag_name, "backend_names") == 0) {
        section = ROOT;
    } else if (strcmp(tag_name, "config_params") == 0) {
        section = ROOT;
    } else if (strcmp(tag_name, "operator_specific") == 0) {
        section = ROOT;
    } else if (strcmp(tag_name, "gain_db_to_level_mapping") == 0) {
        section = ROOT;
    } else if (strcmp(tag_name, "app_types") == 0) {
        section = ROOT;
    }
}

int snd_card_info_init(const char *filename, void *platform, set_parameters_fn fn)
{
    set_parameters = fn;
    my_data.do_full_parse = false;
    return platform_info_init(filename, platform);
}

int platform_info_init(const char *filename, void *platform)
{
    XML_Parser      parser;
    FILE            *file;
    int             ret = 0;
    int             bytes_read;
    void            *buf;
    static const uint32_t kBufSize = 1024;
    char   platform_info_file_name[MIXER_PATH_MAX_LENGTH]= {0};
    section = ROOT;

    if (filename == NULL) {
        strlcpy(platform_info_file_name, PLATFORM_INFO_XML_PATH, MIXER_PATH_MAX_LENGTH);
    } else {
        strlcpy(platform_info_file_name, filename, MIXER_PATH_MAX_LENGTH);
    }

    ALOGV("%s: platform info file name is %s", __func__, platform_info_file_name);

    file = fopen(platform_info_file_name, "r");

    if (!file) {
        ALOGD("%s: Failed to open %s, using defaults.",
            __func__, platform_info_file_name);
        ret = -ENODEV;
        goto done;
    }

    parser = XML_ParserCreate(NULL);
    if (!parser) {
        ALOGE("%s: Failed to create XML parser!", __func__);
        ret = -ENODEV;
        goto err_close_file;
    }

    my_data.platform = platform;
    my_data.kvpairs = str_parms_create();

    XML_SetElementHandler(parser, start_tag, end_tag);

    while (1) {
        buf = XML_GetBuffer(parser, kBufSize);
        if (buf == NULL) {
            ALOGE("%s: XML_GetBuffer failed", __func__);
            ret = -ENOMEM;
            goto err_free_parser;
        }

        bytes_read = fread(buf, 1, kBufSize, file);
        if (bytes_read < 0) {
            ALOGE("%s: fread failed, bytes read = %d", __func__, bytes_read);
             ret = bytes_read;
            goto err_free_parser;
        }

        if (XML_ParseBuffer(parser, bytes_read,
                            bytes_read == 0) == XML_STATUS_ERROR) {
            ALOGE("%s: XML_ParseBuffer failed, for %s",
                __func__, platform_info_file_name);
            ret = -EINVAL;
            goto err_free_parser;
        }

        if (bytes_read == 0)
            break;
    }

    set_parameters = &platform_set_parameters;
    my_data.do_full_parse = true;

err_free_parser:
    XML_ParserFree(parser);
err_close_file:
    fclose(file);
done:
    return ret;
}
