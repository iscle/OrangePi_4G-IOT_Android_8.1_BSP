/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#include <glib.h>
#include <string.h>
#include "mixlog.h"

#define MIX_DELOG_COMPS "MIX_DELOG_COMPS"
#define MIX_DELOG_FILES "MIX_DELOG_FILES"
#define MIX_DELOG_FUNCS "MIX_DELOG_FUNCS"
#define MIX_LOG_ENABLE "MIX_LOG_ENABLE"
#define MIX_DELOG_DELIMITERS " ,;"

#define MIX_LOG_LEVEL "MIX_LOG_LEVEL"

static GStaticMutex g_mutex = G_STATIC_MUTEX_INIT;

#ifdef MIX_LOG_USE_HT
static GHashTable *g_defile_ht = NULL, *g_defunc_ht = NULL, *g_decom_ht = NULL;
static gint g_mix_log_level = MIX_LOG_LEVEL_VERBOSE;
static gint g_refcount = 0;

#define mix_log_destroy_ht(ht) if(ht) { g_hash_table_destroy(ht); ht = NULL; }

void mix_log_get_ht(GHashTable **ht, const gchar *var) {

	const char *delog_list = NULL;
	char *item = NULL;
	if (!ht || !var) {
		return;
	}

	delog_list = g_getenv(var);
	if (!delog_list) {
		return;
	}

	if (*ht == NULL) {
		*ht = g_hash_table_new(g_str_hash, g_str_equal);
		if (*ht == NULL) {
			return;
		}
	}

	item = strtok((char *) delog_list, MIX_DELOG_DELIMITERS);
	while (item != NULL) {
		g_hash_table_insert(*ht, item, "true");
		item = strtok(NULL, MIX_DELOG_DELIMITERS);
	}
}

void mix_log_initialize_func() {

	const gchar *mix_log_level = NULL;
	g_static_mutex_lock(&g_mutex);

	if (g_refcount == 0) {

		mix_log_level = g_getenv(MIX_LOG_LEVEL);
		if (mix_log_level) {
			g_mix_log_level = atoi(mix_log_level);
		}

		mix_log_get_ht(&g_decom_ht, MIX_DELOG_COMPS);
		mix_log_get_ht(&g_defile_ht, MIX_DELOG_FILES);
		mix_log_get_ht(&g_defunc_ht, MIX_DELOG_FUNCS);
	}

	g_refcount++;

	g_static_mutex_unlock(&g_mutex);
}

void mix_log_finalize_func() {

	g_static_mutex_lock(&g_mutex);

	g_refcount--;

	if (g_refcount == 0) {
		mix_log_destroy_ht(g_decom_ht);
		mix_log_destroy_ht(g_defile_ht);
		mix_log_destroy_ht(g_defunc_ht);

		g_mix_log_level = MIX_LOG_LEVEL_VERBOSE;
	}

	if (g_refcount < 0) {
		g_refcount = 0;
	}

	g_static_mutex_unlock(&g_mutex);
}

void mix_log_func(const gchar* comp, gint level, const gchar *file,
		const gchar *func, gint line, const gchar *format, ...) {

	va_list args;
	static gchar* loglevel[4] = {"**ERROR", "*WARNING", "INFO", "VERBOSE"};

	if (!format) {
		return;
	}

	g_static_mutex_lock(&g_mutex);

	if (level > g_mix_log_level) {
		goto exit;
	}

	if (g_decom_ht) {
		if (g_hash_table_lookup(g_decom_ht, comp)) {
			goto exit;
		}
	}

	if (g_defile_ht) {
		if (g_hash_table_lookup(g_defile_ht, file)) {
			goto exit;
		}
	}

	if (g_defunc_ht) {
		if (g_hash_table_lookup(g_defunc_ht, func)) {
			goto exit;
		}
	}

	if(level > MIX_LOG_LEVEL_VERBOSE) {
		level = MIX_LOG_LEVEL_VERBOSE;
	}
	if(level < MIX_LOG_LEVEL_ERROR) {
		level = MIX_LOG_LEVEL_ERROR;
	}

	g_print("%s : %s : %s : ", loglevel[level - 1], file, func);

	va_start(args, format);
	g_vprintf(format, args);
	va_end(args);

	exit: g_static_mutex_unlock(&g_mutex);
}

#else /* MIX_LOG_USE_HT */

gboolean mix_shall_delog(const gchar *name, const gchar *var) {

	const char *delog_list = NULL;
	char *item = NULL;
	gboolean delog = FALSE;

	if (!name || !var) {
		return delog;
	}

	delog_list = g_getenv(var);
	if (!delog_list) {
		return delog;
	}

	item = strtok((char *) delog_list, MIX_DELOG_DELIMITERS);
	while (item != NULL) {
		if (strcmp(item, name) == 0) {
			delog = TRUE;
			break;
		}
		item = strtok(NULL, MIX_DELOG_DELIMITERS);
	}

	return delog;
}

gboolean mix_log_enabled() {

	const char *value = NULL;
	value = g_getenv(MIX_LOG_ENABLE);
	if(!value) {
		return FALSE;
	}
	
	if(value[0] == '0') {
		return FALSE;
	}
	return TRUE;
}

void mix_log_func(const gchar* comp, gint level, const gchar *file,
		const gchar *func, gint line, const gchar *format, ...) {

	va_list args;
	static gchar* loglevel[4] = { "**ERROR", "*WARNING", "INFO", "VERBOSE" };

	const gchar *env_mix_log_level = NULL;
	gint mix_log_level_threhold = MIX_LOG_LEVEL_VERBOSE;

	if(!mix_log_enabled()) {
		return;
	}

	if (!format) {
		return;
	}

	g_static_mutex_lock(&g_mutex);

	/* log level */
	env_mix_log_level = g_getenv(MIX_LOG_LEVEL);
	if (env_mix_log_level) {
		mix_log_level_threhold = atoi(env_mix_log_level);
	}

	if (level > mix_log_level_threhold) {
		goto exit;
	}

	/* component */
	if (mix_shall_delog(comp, MIX_DELOG_COMPS)) {
		goto exit;
	}

	/* files */
	if (mix_shall_delog(file, MIX_DELOG_FILES)) {
		goto exit;
	}

	/* functions */
	if (mix_shall_delog(func, MIX_DELOG_FUNCS)) {
		goto exit;
	}

	if (level > MIX_LOG_LEVEL_VERBOSE) {
		level = MIX_LOG_LEVEL_VERBOSE;
	}
	if (level < MIX_LOG_LEVEL_ERROR) {
		level = MIX_LOG_LEVEL_ERROR;
	}

	g_print("%s : %s : %s : ", loglevel[level - 1], file, func);

	va_start(args, format);
	g_vprintf(format, args);
	va_end(args);

exit:
	g_static_mutex_unlock(&g_mutex);
}


#endif /* MIX_LOG_USE_HT */


