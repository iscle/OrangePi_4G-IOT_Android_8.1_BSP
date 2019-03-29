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

#include "log.h"

#include <cctype>
#include <chrono>
#include <cstdio>
#include <string>

namespace android {

Log::LogLevel Log::level_;
Logger* Log::logger_;
std::chrono::time_point<std::chrono::steady_clock> Log::init_time_;

void Log::Initialize(Logger *logger, LogLevel level) {
    if (Log::logger_) {
        Log::Warn("Re-initializing logger");
    }
    Log::init_time_ = std::chrono::steady_clock::now();
    Log::logger_ = logger;
    Log::SetLevel(level);
}

void Log::SetLevel(LogLevel level) {
    Log::level_ = level;
}

#define LOG_EX_VARARGS(level, format) \
    do { \
        va_list arg_list; \
        va_start(arg_list, format); \
        Log::LogEx(level, format, arg_list); \
        va_end(arg_list); \
    } while (0)

void Log::Error(const char *format, ...) {
    LOG_EX_VARARGS(LogLevel::Error, format);
}

void Log::Warn(const char *format, ...) {
    LOG_EX_VARARGS(LogLevel::Warn, format);
}

void Log::Info(const char *format, ...) {
    LOG_EX_VARARGS(LogLevel::Info, format);
}

void Log::Debug(const char *format, ...) {
    LOG_EX_VARARGS(LogLevel::Debug, format);
}

void Log::DebugBuf(std::vector<uint8_t> vec) {
    Log::DebugBuf(vec.data(), vec.size());
}

void Log::DebugBuf(const uint8_t *buffer, size_t size) {
    if (Log::level_ < LogLevel::Debug) {
        return;
    }

    char line[32];
    int offset = 0;
    char line_chars[32];
    int offset_chars = 0;

    Log::Debug("Dumping buffer of size %zu bytes", size);
    for (size_t i = 1; i <= size; ++i) {
        offset += snprintf(&line[offset], sizeof(line) - offset, "%02x ",
                           buffer[i - 1]);
        offset_chars += snprintf(
            &line_chars[offset_chars], sizeof(line_chars) - offset_chars,
            "%c", (isprint(buffer[i - 1])) ? buffer[i - 1] : '.');
        if ((i % 8) == 0) {
            Log::Debug("  %s\t%s", line, line_chars);
            offset = 0;
            offset_chars = 0;
        } else if ((i % 4) == 0) {
            offset += snprintf(&line[offset], sizeof(line) - offset, " ");
        }
    }

    if (offset > 0) {
        std::string tabs;
        while (offset < 28) {
            tabs += "\t";
            offset += 8;
        }
        Log::Debug("  %s%s%s", line, tabs.c_str(), line_chars);
    }
}

char Log::LevelAbbrev(LogLevel level) {
    switch (level) {
    case LogLevel::Error:
        return 'E';
    case LogLevel::Warn:
        return 'W';
    case LogLevel::Info:
        return 'I';
    case LogLevel::Debug:
        return 'D';
    default:
        return '?';
    }
}

void Log::LogEx(LogLevel level, const char *format, va_list arg_list) {
    if (Log::level_ < level) {
        return;
    }

    std::chrono::duration<float> log_time =
        (std::chrono::steady_clock::now() - Log::init_time_);

    // Can add colorization here if desired (should be configurable)
    char prefix[20];
    snprintf(prefix, sizeof(prefix), "%c %6.03f: ", Log::LevelAbbrev(level),
             log_time.count());

    Log::logger_->Output(prefix);
    Log::logger_->Output(format, arg_list);
    Log::logger_->Output("\n");
}

void PrintfLogger::Output(const char *str) {
    printf("%s", str);
}

void PrintfLogger::Output(const char *format, va_list arg_list) {
    vprintf(format, arg_list);
}

}  // namespace android
