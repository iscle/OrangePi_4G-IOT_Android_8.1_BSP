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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Class to handle the execution of an external process
 */
public class ExecuteFile {
  @Nonnull
  private final String[] cmdLine;

  @CheckForNull
  private File workDir;

  @CheckForNull
  private InputStream inStream;
  private boolean inToBeClose;

  @CheckForNull
  private OutputStream outStream;
  private boolean outToBeClose;

  @CheckForNull
  private OutputStream errStream;
  private boolean errToBeClose;
  private boolean verbose;

  @Nonnull
  private final Logger logger = Logger.getLogger(this.getClass().getName());

  public void setErr(@Nonnull File file) throws FileNotFoundException {
    errStream = new FileOutputStream(file);
    errToBeClose = true;
  }

  public void setOut(@Nonnull File file) throws FileNotFoundException {
    outStream = new FileOutputStream(file);
    outToBeClose = true;
  }

  public void setIn(@Nonnull File file) throws FileNotFoundException {
    inStream = new FileInputStream(file);
    inToBeClose = true;
  }

  public void setErr(@Nonnull OutputStream stream) {
    errStream = stream;
  }

  public void setOut(@Nonnull OutputStream stream) {
    outStream = stream;
  }

  public void setIn(@Nonnull InputStream stream) {
    inStream = stream;
  }

  public void setWorkingDir(@Nonnull File dir, boolean create) throws IOException {
    if (!dir.isDirectory()) {
      if (create && !dir.exists()) {
        if (!dir.mkdirs()) {
          throw new IOException("Directory creation failed");
        }
      } else {
        throw new FileNotFoundException(dir.getPath() + " is not a directory");
      }
    }

    workDir = dir;
  }

  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  public ExecuteFile(@Nonnull File exec, @Nonnull String[] args) {
    cmdLine = new String[args.length + 1];
    System.arraycopy(args, 0, cmdLine, 1, args.length);

    cmdLine[0] = exec.getAbsolutePath();
  }

  public ExecuteFile(@Nonnull String exec, @Nonnull String[] args) {
    cmdLine = new String[args.length + 1];
    System.arraycopy(args, 0, cmdLine, 1, args.length);

    cmdLine[0] = exec;
  }

  public ExecuteFile(@Nonnull File exec) {
    cmdLine = new String[1];
    cmdLine[0] = exec.getAbsolutePath();
  }

  public ExecuteFile(@Nonnull String[] cmdLine) {
    this.cmdLine = cmdLine.clone();
  }

  public ExecuteFile(@Nonnull String cmdLine) throws IOException {
    StringReader reader = new StringReader(cmdLine);
    StreamTokenizer tokenizer = new StreamTokenizer(reader);
    tokenizer.resetSyntax();
    // Only standard spaces are recognized as whitespace chars
    tokenizer.whitespaceChars(' ', ' ');
    // Matches alphanumerical and common special symbols like '(' and ')'
    tokenizer.wordChars('!', 'z');
    // Quote chars will be ignored when parsing strings
    tokenizer.quoteChar('\'');
    tokenizer.quoteChar('\"');
    ArrayList<String> tokens = new ArrayList<String>();
    while (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
      String token = tokenizer.sval;
      if (token != null) {
        tokens.add(token);
      }
    }
    this.cmdLine = tokens.toArray(new String[0]);
  }

  public boolean run() {
    int ret;
    Process proc = null;
    Thread suckOut = null;
    Thread suckErr = null;
    Thread suckIn = null;

    try {
      StringBuilder cmdLineBuilder = new StringBuilder();
      for (String arg : cmdLine) {
        cmdLineBuilder.append(arg).append(' ');
      }
      if (verbose) {
        PrintStream printStream;
        if (outStream instanceof PrintStream) {
          printStream = (PrintStream) outStream;
        } else {
          printStream = System.out;
        }

        if (printStream != null) {
          printStream.println(cmdLineBuilder);
        }
      } else {
        logger.log(Level.FINE, "Execute: {0}", cmdLineBuilder);
      }

      proc = Runtime.getRuntime().exec(cmdLine, null, workDir);

      InputStream localInStream = inStream;
      if (localInStream != null) {
        suckIn = new Thread(
            new ThreadBytesStreamSucker(localInStream, proc.getOutputStream(), inToBeClose));
      } else {
        proc.getOutputStream().close();
      }

      OutputStream localOutStream = outStream;
      if (localOutStream != null) {
        if (localOutStream instanceof PrintStream) {
          suckOut = new Thread(new ThreadCharactersStreamSucker(proc.getInputStream(),
              (PrintStream) localOutStream, outToBeClose));
        } else {
          suckOut = new Thread(
              new ThreadBytesStreamSucker(proc.getInputStream(), localOutStream, outToBeClose));
        }
      }

      OutputStream localErrStream = errStream;
      if (localErrStream != null) {
        if (localErrStream instanceof PrintStream) {
          suckErr = new Thread(new ThreadCharactersStreamSucker(proc.getErrorStream(),
              (PrintStream) localErrStream, errToBeClose));
        } else {
          suckErr = new Thread(
              new ThreadBytesStreamSucker(proc.getErrorStream(), localErrStream, errToBeClose));
        }
      }

      if (suckIn != null) {
        suckIn.start();
      }
      if (suckOut != null) {
        suckOut.start();
      }
      if (suckErr != null) {
        suckErr.start();
      }

      proc.waitFor();
      if (suckIn != null) {
        suckIn.join();
      }
      if (suckOut != null) {
        suckOut.join();
      }
      if (suckErr != null) {
        suckErr.join();
      }

      ret = proc.exitValue();
      proc.destroy();

      return ret == 0;
    } catch (Throwable e) {
      e.printStackTrace();
      return false;
    }
  }

  private static class ThreadBytesStreamSucker extends BytesStreamSucker implements Runnable {

    public ThreadBytesStreamSucker(@Nonnull InputStream is, @Nonnull OutputStream os,
        boolean toBeClose) {
      super(is, os, toBeClose);
    }

    @Override
    public void run() {
      try {
        suck();
      } catch (IOException e) {
        // Best effort
      }
    }
  }

  private static class ThreadCharactersStreamSucker extends CharactersStreamSucker implements
      Runnable {

    public ThreadCharactersStreamSucker(@Nonnull InputStream is, @Nonnull PrintStream ps,
        boolean toBeClose) {
      super(is, ps, toBeClose);
    }

    @Override
    public void run() {
      try {
        suck();
      } catch (IOException e) {
        // Best effort
      }
    }
  }
}