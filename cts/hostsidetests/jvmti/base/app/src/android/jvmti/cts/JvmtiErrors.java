/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.jvmti.cts;

public interface JvmtiErrors {
  public static int NONE                                              = 0;

  public static int INVALID_THREAD                                    = 10;
  public static int INVALID_THREAD_GROUP                              = 11;
  public static int INVALID_PRIORITY                                  = 12;
  public static int THREAD_NOT_SUSPENDED                              = 13;
  public static int THREAD_SUSPENDED                                  = 14;
  public static int THREAD_NOT_ALIVE                                  = 15;

  public static int INVALID_OBJECT                                    = 20;
  public static int INVALID_CLASS                                     = 21;
  public static int CLASS_NOT_PREPARED                                = 22;
  public static int INVALID_METHODID                                  = 23;
  public static int INVALID_LOCATION                                  = 24;
  public static int INVALID_FIELDID                                   = 25;

  public static int NO_MORE_FRAMES                                    = 31;
  public static int OPAQUE_FRAME                                      = 32;
  public static int TYPE_MISMATCH                                     = 34;
  public static int INVALID_SLOT                                      = 35;

  public static int DUPLICATE                                         = 40;
  public static int NOT_FOUND                                         = 41;

  public static int INVALID_MONITOR                                   = 50;
  public static int NOT_MONITOR_OWNER                                 = 51;
  public static int INTERRUPT                                         = 52;

  public static int INVALID_CLASS_FORMAT                              = 60;
  public static int CIRCULAR_CLASS_DEFINITION                         = 61;
  public static int FAILS_VERIFICATION                                = 62;
  public static int UNSUPPORTED_REDEFINITION_METHOD_ADDED             = 63;
  public static int UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED           = 64;
  public static int INVALID_TYPESTATE                                 = 65;
  public static int UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED        = 66;
  public static int UNSUPPORTED_REDEFINITION_METHOD_DELETED           = 67;
  public static int UNSUPPORTED_VERSION                               = 68;
  public static int NAMES_DONT_MATCH                                  = 69;

  public static int UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED  = 70;
  public static int UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED = 71;
  public static int UNMODIFIABLE_CLASS                                = 79;

  public static int NOT_AVAILABLE                                     = 98;
  public static int MUST_POSSESS_CAPABILITY                           = 99;

  public static int NULL_POINTER                                      = 100;
  public static int ABSENT_INFORMATION                                = 101;
  public static int INVALID_EVENT_TYPE                                = 102;
  public static int ILLEGAL_ARGUMENT                                  = 103;
  public static int NATIVE_METHOD                                     = 104;
  public static int CLASS_LOADER_UNSUPPORTED                          = 106;

  public static int OUT_OF_MEMORY                                     = 110;
  public static int ACCESS_DENIED                                     = 111;
  public static int WRONG_PHASE                                       = 112;
  public static int INTERNAL                                          = 113;
  public static int UNATTACHED_THREAD                                 = 115;
  public static int INVALID_ENVIRONMENT                               = 116;
}
