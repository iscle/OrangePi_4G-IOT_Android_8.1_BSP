/*
 * Copyright (C) 2016 The Android Open Source Project
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

/**
 * Contains all the changes needed to the {@code android.support.test.internal.runner} classes.
 *
 * <p>As its name suggests {@code android.support.test.internal.runner} are internal classes that
 * are not designed to be extended from outside those packages. This package encapsulates all the
 * workarounds needed to overcome that limitation, from duplicating classes to using reflection.
 * The intention is that these changes are temporary and they (or a better equivalent) will be
 * quickly integrated into the internal classes.
 */
package com.android.cts.core.runner.support;
