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

#ifndef LOG_H_
#define LOG_H_

#include <stdarg.h>

#include <chrono>
#include <vector>

namespace android {

/*
 * Prefer to use these macros instead of calling Log::Error, etc. directly, in
 * case we want to add tracing of the source file and line number, or compile
 * out logging completely, etc.
 */
#define LOGE(fmt, ...) Log::Error(fmt, ##__VA_ARGS__)
#define LOGW(fmt, ...) Log::Warn(fmt, ##__VA_ARGS__)
#define LOGI(fmt, ...) Log::Info(fmt, ##__VA_ARGS__)
#define LOGD(fmt, ...) Log::Debug(fmt, ##__VA_ARGS__)

#define LOGD_BUF(buf, len) Log::DebugBuf((const uint8_t *) buf, len)
#define LOGD_VEC(vec) Log::DebugBuf(vec)

// Interface for a log output method
class Logger {
  public:
    virtual ~Logger() {};
    virtual void Output(const char *str) = 0;
    virtual void Output(const char *format, va_list arg_list) = 0;
};

// Singleton used to log messages to an arbitrary output
class Log {
  public:
    enum class LogLevel {
        // Use with SetLevel to disable logging
        Disable,
        Error,
        Warn,
        Info,
        Debug,
    };

    // Define the logging mechanism and minimum log level that will be printed
    static void Initialize(Logger *logger, LogLevel level);

    __attribute__((__format__ (printf, 1, 2)))
    static void Error(const char *format, ...);

    __attribute__((__format__ (printf, 1, 2)))
    static void Warn(const char *format, ...);

    __attribute__((__format__ (printf, 1, 2)))
    static void Info(const char *format, ...);

    __attribute__((__format__ (printf, 1, 2)))
    static void Debug(const char *format, ...);

    static void DebugBuf(std::vector<uint8_t> vec);
    static void DebugBuf(const uint8_t *buffer, size_t size);

    // Allows for updating the logging level after initialization
    static void SetLevel(LogLevel level);

  private:
    static char LevelAbbrev(LogLevel level);
    static void LogEx(LogLevel level, const char *format, va_list arg_list);

    static Logger* logger_;
    static LogLevel level_;
    static std::chrono::time_point<std::chrono::steady_clock> init_time_;
};

class PrintfLogger : public Logger {
  public:
    void Output(const char *str);
    void Output(const char *format, va_list arg_list);
};

}  // namespace android

#endif // LOG_H_
