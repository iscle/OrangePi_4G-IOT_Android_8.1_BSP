/*
 * Copyright (C) 2012 The Android Open Source Project
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

package util.build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import javax.annotation.Nonnull;

/**
 * Class that continuously read an {@link InputStream} and optionally could print the input in a
 * {@link PrintStream}.
 */
public class CharactersStreamSucker {

  @Nonnull
  private final BufferedReader ir;

  @Nonnull
  private final PrintStream os;

  private final boolean toBeClose;

  public CharactersStreamSucker(
      @Nonnull InputStream is, @Nonnull PrintStream os, boolean toBeClose) {
    this.ir = new BufferedReader(new InputStreamReader(is));
    this.os = os;
    this.toBeClose = toBeClose;
  }

  public CharactersStreamSucker(@Nonnull InputStream is, @Nonnull PrintStream os) {
    this(is, os, false);
  }

  public void suck() throws IOException {
    String line;

    try {
      while ((line = ir.readLine()) != null) {
        os.println(line);
      }
    } finally {
      if (toBeClose) {
        os.close();
      }
    }
  }
}