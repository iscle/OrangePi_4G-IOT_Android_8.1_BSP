/*
 * Copyright (C) 2010 The Android Open Source Project
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

#define LOG_TAG "TimeZoneNames"

#include <memory>

#include <nativehelper/JNIHelp.h>
#include <nativehelper/JniConstants.h>
#include <nativehelper/ScopedLocalRef.h>
#include <nativehelper/ScopedUtfChars.h>

#include "IcuUtilities.h"
#include "JniException.h"
#include "ScopedIcuLocale.h"
#include "ScopedJavaUnicodeString.h"
#include "unicode/calendar.h"
#include "unicode/timezone.h"
#include "unicode/tznames.h"

static bool isUtc(const icu::UnicodeString& id) {
  static const icu::UnicodeString kEtcUct("Etc/UCT", 7, US_INV);
  static const icu::UnicodeString kEtcUtc("Etc/UTC", 7, US_INV);
  static const icu::UnicodeString kEtcUniversal("Etc/Universal", 13, US_INV);
  static const icu::UnicodeString kEtcZulu("Etc/Zulu", 8, US_INV);

  static const icu::UnicodeString kUct("UCT", 3, US_INV);
  static const icu::UnicodeString kUtc("UTC", 3, US_INV);
  static const icu::UnicodeString kUniversal("Universal", 9, US_INV);
  static const icu::UnicodeString kZulu("Zulu", 4, US_INV);

  return id == kEtcUct || id == kEtcUtc || id == kEtcUniversal || id == kEtcZulu ||
      id == kUct || id == kUtc || id == kUniversal || id == kZulu;
}

static bool setStringArrayElement(JNIEnv* env, jobjectArray array, int i, const icu::UnicodeString& s) {
  // Don't use "GMT" string, for backwards compatibility.
  static const icu::UnicodeString kGmt("GMT", 3, US_INV);
  if (!s.isBogus() && !s.startsWith(kGmt)) {
    ScopedLocalRef<jstring> javaString(env, jniCreateString(env, s.getBuffer(), s.length()));
    if (javaString.get() == NULL) {
      return false;
    }
    env->SetObjectArrayElement(array, i, javaString.get());
  }
  return true;
}

static void TimeZoneNames_fillZoneStrings(JNIEnv* env, jclass, jstring javaLocaleName, jobjectArray result) {
  ScopedIcuLocale icuLocale(env, javaLocaleName);
  if (!icuLocale.valid()) {
    return;
  }

  UErrorCode status = U_ZERO_ERROR;
  std::unique_ptr<icu::TimeZoneNames> names(icu::TimeZoneNames::createInstance(icuLocale.locale(), status));
  if (maybeThrowIcuException(env, "TimeZoneNames::createInstance", status)) {
    return;
  }

  const UDate now(icu::Calendar::getNow());

  static const icu::UnicodeString kUtc("UTC", 3, US_INV);

  size_t id_count = env->GetArrayLength(result);
  for (size_t i = 0; i < id_count; ++i) {
    ScopedLocalRef<jobjectArray> java_row(env,
                                          reinterpret_cast<jobjectArray>(env->GetObjectArrayElement(result, i)));
    ScopedLocalRef<jstring> java_zone_id(env,
                                         reinterpret_cast<jstring>(env->GetObjectArrayElement(java_row.get(), 0)));
    ScopedJavaUnicodeString zone_id(env, java_zone_id.get());
    if (!zone_id.valid()) {
      return;
    }

    icu::UnicodeString long_std;
    names->getDisplayName(zone_id.unicodeString(), UTZNM_LONG_STANDARD, now, long_std);
    icu::UnicodeString short_std;
    names->getDisplayName(zone_id.unicodeString(), UTZNM_SHORT_STANDARD, now, short_std);
    icu::UnicodeString long_dst;
    names->getDisplayName(zone_id.unicodeString(), UTZNM_LONG_DAYLIGHT, now, long_dst);
    icu::UnicodeString short_dst;
    names->getDisplayName(zone_id.unicodeString(), UTZNM_SHORT_DAYLIGHT, now, short_dst);

    if (isUtc(zone_id.unicodeString())) {
      // ICU doesn't have names for the UTC zones; it just says "GMT+00:00" for both
      // long and short names. We don't want this. The best we can do is use "UTC"
      // for everything (since we don't know how to say "Universal Coordinated Time" in
      // every language).
      // TODO: check CLDR doesn't actually have this somewhere.
      long_std = short_std = long_dst = short_dst = kUtc;
    }

    bool okay =
        setStringArrayElement(env, java_row.get(), 1, long_std) &&
        setStringArrayElement(env, java_row.get(), 2, short_std) &&
        setStringArrayElement(env, java_row.get(), 3, long_dst) &&
        setStringArrayElement(env, java_row.get(), 4, short_dst);
    if (!okay) {
      return;
    }
  }
}

static jstring TimeZoneNames_getExemplarLocation(JNIEnv* env, jclass, jstring javaLocaleName, jstring javaTz) {
  ScopedIcuLocale icuLocale(env, javaLocaleName);
  if (!icuLocale.valid()) {
    return NULL;
  }

  UErrorCode status = U_ZERO_ERROR;
  std::unique_ptr<icu::TimeZoneNames> names(icu::TimeZoneNames::createInstance(icuLocale.locale(), status));
  if (maybeThrowIcuException(env, "TimeZoneNames::createInstance", status)) {
    return NULL;
  }

  ScopedJavaUnicodeString tz(env, javaTz);
  if (!tz.valid()) {
    return NULL;
  }

  icu::UnicodeString s;
  const UDate now(icu::Calendar::getNow());
  names->getDisplayName(tz.unicodeString(), UTZNM_EXEMPLAR_LOCATION, now, s);
  return jniCreateString(env, s.getBuffer(), s.length());
}

static JNINativeMethod gMethods[] = {
  NATIVE_METHOD(TimeZoneNames, fillZoneStrings, "(Ljava/lang/String;[[Ljava/lang/String;)V"),
  NATIVE_METHOD(TimeZoneNames, getExemplarLocation, "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
};
void register_libcore_icu_TimeZoneNames(JNIEnv* env) {
  jniRegisterNativeMethods(env, "libcore/icu/TimeZoneNames", gMethods, NELEM(gMethods));
}
