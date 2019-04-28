//
// Copyright 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#define LOG_TAG "properties"

#include <ctype.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <cutils/properties.h>
#include <log/log.h>

static const int MAX_PROPERTIES = 5;

struct property {
  char key[PROP_KEY_MAX + 2];
  char value[PROP_VALUE_MAX + 2];
};

int num_properties = 0;
struct property properties[MAX_PROPERTIES];

// Find the correct entry.
static int property_find(const char *key) {
  for (int i = 0; i < num_properties; i++) {
    if (strncmp(properties[i].key, key, PROP_KEY_MAX) == 0) {
      return i;
    }
  }
  return MAX_PROPERTIES;
}

int property_set(const char *key, const char *value) {
  if (strnlen(value, PROP_VALUE_MAX) > PROP_VALUE_MAX) return -1;

  // Check to see if the property exists.
  int prop_index = property_find(key);

  if (prop_index == MAX_PROPERTIES) {
    if (num_properties >= MAX_PROPERTIES) return -1;
    prop_index = num_properties;
    num_properties += 1;
  }

  // This is test code.  Be nice and don't push the boundary cases!
  strncpy(properties[prop_index].key, key, PROP_KEY_MAX + 1);
  strncpy(properties[prop_index].value, value, PROP_VALUE_MAX + 1);
  return 0;
}

int property_get(const char *key, char *value, const char *default_value) {
  // This doesn't mock the behavior of default value
  if (default_value != NULL) ALOGE("%s: default_value is ignored!", __func__);

  // Check to see if the property exists.
  int prop_index = property_find(key);

  if (prop_index == MAX_PROPERTIES) return 0;

  int len = strlen(properties[prop_index].value);
  memcpy(value, properties[prop_index].value, len);
  value[len] = '\0';
  return len;
}
