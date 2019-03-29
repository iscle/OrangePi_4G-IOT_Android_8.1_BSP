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

#include <getopt.h>
#include <signal.h>
#include <unistd.h>

#include <cstdlib>
#include <cstring>
#include <memory>
#include <sstream>
#include <tuple>
#include <vector>

#include "contexthub.h"
#include "log.h"

#ifdef __ANDROID__
#include "androidcontexthub.h"
#else
#include "cp2130.h"
#include "usbcontext.h"
#include "usbcontexthub.h"
#endif

using namespace android;

enum class NanotoolCommand {
    Invalid,
    Disable,
    DisableAll,
    Calibrate,
    Test,
    Read,
    Poll,
    LoadCalibration,
    Flash,
    GetBridgeVer,
};

struct ParsedArgs {
    NanotoolCommand command = NanotoolCommand::Poll;
    std::vector<SensorSpec> sensors;
    int count = 0;
    bool logging_enabled = false;
    std::string filename;
    int device_index = 0;
};

static NanotoolCommand StrToCommand(const char *command_name) {
    static const std::vector<std::tuple<std::string, NanotoolCommand>> cmds = {
        std::make_tuple("disable",     NanotoolCommand::Disable),
        std::make_tuple("disable_all", NanotoolCommand::DisableAll),
        std::make_tuple("calibrate",   NanotoolCommand::Calibrate),
        std::make_tuple("cal",         NanotoolCommand::Calibrate),
        std::make_tuple("test",        NanotoolCommand::Test),
        std::make_tuple("read",        NanotoolCommand::Read),
        std::make_tuple("poll",        NanotoolCommand::Poll),
        std::make_tuple("load_cal",    NanotoolCommand::LoadCalibration),
        std::make_tuple("flash",       NanotoolCommand::Flash),
        std::make_tuple("bridge_ver",  NanotoolCommand::GetBridgeVer),
    };

    if (!command_name) {
        return NanotoolCommand::Invalid;
    }

    for (size_t i = 0; i < cmds.size(); i++) {
        std::string name;
        NanotoolCommand cmd;

        std::tie(name, cmd) = cmds[i];
        if (name.compare(command_name) == 0) {
            return cmd;
        }
    }

    return NanotoolCommand::Invalid;
}

static void PrintUsage(const char *name) {
    const char *help_text =
        "options:\n"
        "  -x, --cmd          Argument must be one of:\n"
        "                        bridge_ver: retrieve bridge version information (not\n"
        "                           supported on all devices)\n"
        "                        disable: send a disable request for one sensor\n"
        "                        disable_all: send a disable request for all sensors\n"
        "                        calibrate: disable the sensor, then perform the sensor\n"
        "                           calibration routine\n"
        "                        test: run a sensor's self-test routine\n"
#ifndef __ANDROID__
        "                        flash: load a new firmware image to the hub\n"
#endif
        "                        load_cal: send data from calibration file to hub\n"
        "                        poll (default): enable the sensor, output received\n"
        "                           events, then disable the sensor before exiting\n"
        "                        read: output events for the given sensor, or all events\n"
        "                           if no sensor specified\n"
        "\n"
        "  -s, --sensor       Specify sensor type, and parameters for the command.\n"
        "                     Format is sensor_type[:rate[:latency_ms]][=cal_ref].\n"
        "                     See below for a complete list sensor types. A rate is\n"
        "                     required when enabling a sensor, but latency is optional\n"
        "                     and defaults to 0. Rate can be specified in Hz, or as one\n"
        "                     of the special values \"onchange\", \"ondemand\", or\n"
        "                     \"oneshot\".\n"
        "                     Some sensors require a ground truth value for calibration.\n"
        "                     Use the cal_ref parameter for this purpose (it's parsed as\n"
        "                     a float).\n"
        "                     This argument can be repeated to perform a command on\n"
        "                     multiple sensors.\n"
        "\n"
        "  -c, --count        Number of samples to read before exiting, or set to 0 to\n"
        "                     read indefinitely (the default behavior)\n"
        "\n"
        "  -f, --file\n"
        "                     Specifies the file to be used with flash.\n"
        "\n"
        "  -l, --log          Outputs logs from the sensor hub as they become available.\n"
        "                     The logs will be printed inline with sensor samples.\n"
        "                     The default is for log messages to be ignored.\n"
#ifndef __ANDROID__
        // This option is only applicable when connecting over USB
        "\n"
        "  -i, --index        Selects the device to work with by specifying the index\n"
        "                     into the device list (default: 0)\n"
#endif
        "\n"
        "  -v, -vv            Output verbose/extra verbose debugging information\n";

    fprintf(stderr, "%s %s\n\n", name, NANOTOOL_VERSION_STR);
    fprintf(stderr, "Usage: %s [options]\n\n%s\n", name, help_text);
    fprintf(stderr, "Supported sensors: %s\n\n",
            ContextHub::ListAllSensorAbbrevNames().c_str());
    fprintf(stderr, "Examples:\n"
                    "  %s -s accel:50\n"
                    "  %s -s accel:50:1000 -s gyro:50:1000\n"
                    "  %s -s prox:onchange\n"
                    "  %s -x calibrate -s baro=1000\n",
            name, name, name, name);
}

/*
 * Performs higher-level argument validation beyond just parsing the parameters,
 * for example check whether a required argument is present when the command is
 * set to a specific value.
 */
static bool ValidateArgs(std::unique_ptr<ParsedArgs>& args, const char *name) {
    if (!args->sensors.size()
          && (args->command == NanotoolCommand::Disable
                || args->command == NanotoolCommand::Calibrate
                || args->command == NanotoolCommand::Test
                || args->command == NanotoolCommand::Poll)) {
        fprintf(stderr, "%s: At least 1 sensor must be specified for this "
                        "command (use -s)\n",
                name);
        return false;
    }

    if (args->command == NanotoolCommand::Flash
            && args->filename.empty()) {
        fprintf(stderr, "%s: A filename must be specified for this command "
                        "(use -f)\n",
                name);
        return false;
    }

    if (args->command == NanotoolCommand::Poll) {
        for (unsigned int i = 0; i < args->sensors.size(); i++) {
            if (args->sensors[i].special_rate == SensorSpecialRate::None
                  && args->sensors[i].rate_hz < 0) {
                fprintf(stderr, "%s: Sample rate must be specified for sensor "
                        "%s\n", name,
                        ContextHub::SensorTypeToAbbrevName(
                            args->sensors[i].sensor_type).c_str());
                return false;
            }
        }
    }

    if (args->command == NanotoolCommand::Calibrate) {
        for (unsigned int i = 0; i < args->sensors.size(); i++) {
            if (!args->sensors[i].have_cal_ref
                  && (args->sensors[i].sensor_type == SensorType::Barometer
                        || args->sensors[i].sensor_type ==
                             SensorType::AmbientLightSensor)) {
                fprintf(stderr, "%s: Calibration reference required for sensor "
                                "%s (for example: -s baro=1000)\n", name,
                        ContextHub::SensorTypeToAbbrevName(
                            args->sensors[i].sensor_type).c_str());
                return false;
            }
        }
    }

    return true;
}

static bool ParseRate(const std::string& param, SensorSpec& spec) {
    static const std::vector<std::tuple<std::string, SensorSpecialRate>> rates = {
        std::make_tuple("ondemand", SensorSpecialRate::OnDemand),
        std::make_tuple("onchange", SensorSpecialRate::OnChange),
        std::make_tuple("oneshot",  SensorSpecialRate::OneShot),
    };

    for (size_t i = 0; i < rates.size(); i++) {
        std::string name;
        SensorSpecialRate rate;

        std::tie(name, rate) = rates[i];
        if (param == name) {
            spec.special_rate = rate;
            return true;
        }
    }

    spec.rate_hz = std::stof(param);
    if (spec.rate_hz < 0) {
        return false;
    }

    return true;
}

// Parse a sensor argument in the form of "sensor_name[:rate[:latency]][=cal_ref]"
// into a SensorSpec, and add it to ParsedArgs.
static bool ParseSensorArg(std::vector<SensorSpec>& sensors, const char *arg_str,
        const char *name) {
    SensorSpec spec;
    std::string param;
    std::string pre_cal_ref;
    std::stringstream full_arg_ss(arg_str);
    unsigned int index = 0;

    while (std::getline(full_arg_ss, param, '=')) {
        if (index == 0) {
            pre_cal_ref = param;
        } else if (index == 1) {
            spec.cal_ref = std::stof(param);
            spec.have_cal_ref = true;
        } else {
            fprintf(stderr, "%s: Only one calibration reference may be "
                            "supplied\n", name);
            return false;
        }
        index++;
    }

    index = 0;
    std::stringstream pre_cal_ref_ss(pre_cal_ref);
    while (std::getline(pre_cal_ref_ss, param, ':')) {
        if (index == 0) { // Parse sensor type
            spec.sensor_type = ContextHub::SensorAbbrevNameToType(param);
            if (spec.sensor_type == SensorType::Invalid_) {
                fprintf(stderr, "%s: Invalid sensor name '%s'\n",
                        name, param.c_str());
                return false;
            }
        } else if (index == 1) { // Parse sample rate
            if (!ParseRate(param, spec)) {
                fprintf(stderr, "%s: Invalid sample rate %s\n", name,
                        param.c_str());
                return false;
            }
        } else if (index == 2) { // Parse latency
            long long latency_ms = std::stoll(param);
            if (latency_ms < 0) {
                fprintf(stderr, "%s: Invalid latency %lld\n", name, latency_ms);
                return false;
            }
            spec.latency_ns = static_cast<uint64_t>(latency_ms) * 1000000;
        } else {
            fprintf(stderr, "%s: Too many arguments in -s", name);
            return false;
        }
        index++;
    }

    sensors.push_back(spec);
    return true;
}

static std::unique_ptr<ParsedArgs> ParseArgs(int argc, char **argv) {
    static const struct option long_opts[] = {
        {"cmd",     required_argument, nullptr, 'x'},
        {"sensor",  required_argument, nullptr, 's'},
        {"count",   required_argument, nullptr, 'c'},
        {"flash",   required_argument, nullptr, 'f'},
        {"log",     no_argument,       nullptr, 'l'},
        {"index",   required_argument, nullptr, 'i'},
        {}  // Indicates the end of the option list
    };

    auto args = std::unique_ptr<ParsedArgs>(new ParsedArgs());
    int index = 0;
    while (42) {
        int c = getopt_long(argc, argv, "x:s:c:f:v::li:", long_opts, &index);
        if (c == -1) {
            break;
        }

        switch (c) {
          case 'x': {
            args->command = StrToCommand(optarg);
            if (args->command == NanotoolCommand::Invalid) {
                fprintf(stderr, "%s: Invalid command '%s'\n", argv[0], optarg);
                return nullptr;
            }
            break;
          }
          case 's': {
            if (!ParseSensorArg(args->sensors, optarg, argv[0])) {
                return nullptr;
            }
            break;
          }
          case 'c': {
            args->count = atoi(optarg);
            if (args->count < 0) {
                fprintf(stderr, "%s: Invalid sample count %d\n",
                        argv[0], args->count);
                return nullptr;
            }
            break;
          }
          case 'v': {
            if (optarg && optarg[0] == 'v') {
                Log::SetLevel(Log::LogLevel::Debug);
            } else {
                Log::SetLevel(Log::LogLevel::Info);
            }
            break;
          }
          case 'l': {
            args->logging_enabled = true;
            break;
          }
          case 'f': {
            if (optarg) {
                args->filename = std::string(optarg);
            } else {
                fprintf(stderr, "File requires a filename\n");
                return nullptr;
            }
            break;
          }
          case 'i': {
            args->device_index = atoi(optarg);
            if (args->device_index < 0) {
                fprintf(stderr, "%s: Invalid device index %d\n", argv[0],
                        args->device_index);
                return nullptr;
            }
            break;
          }
          default:
            return nullptr;
        }
    }

    if (!ValidateArgs(args, argv[0])) {
        return nullptr;
    }
    return args;
}

static std::unique_ptr<ContextHub> GetContextHub(std::unique_ptr<ParsedArgs>& args) {
#ifdef __ANDROID__
    (void) args;
    return std::unique_ptr<AndroidContextHub>(new AndroidContextHub());
#else
    return std::unique_ptr<UsbContextHub>(new UsbContextHub(args->device_index));
#endif
}

#ifdef __ANDROID__
static void SignalHandler(int sig) {
    // Catches a signal and does nothing, to allow any pending syscalls to be
    // exited with SIGINT and normal cleanup to occur. If SIGINT is sent a
    // second time, the system will invoke the standard handler.
    (void) sig;
}

static void TerminateHandler() {
    AndroidContextHub::TerminateHandler();
    std::abort();
}

static void SetHandlers() {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = SignalHandler;
    sigaction(SIGINT, &sa, NULL);

    std::set_terminate(TerminateHandler);
}
#endif

int main(int argc, char **argv) {
    Log::Initialize(new PrintfLogger(), Log::LogLevel::Warn);

    // If no arguments given, print usage without any error messages
    if (argc == 1) {
        PrintUsage(argv[0]);
        return 1;
    }

    std::unique_ptr<ParsedArgs> args = ParseArgs(argc, argv);
    if (!args) {
        PrintUsage(argv[0]);
        return 1;
    }

#ifdef __ANDROID__
    SetHandlers();
#endif

    std::unique_ptr<ContextHub> hub = GetContextHub(args);
    if (!hub || !hub->Initialize()) {
        LOGE("Error initializing ContextHub");
        return -1;
    }

    hub->SetLoggingEnabled(args->logging_enabled);

    bool success = true;
    switch (args->command) {
      case NanotoolCommand::Disable:
        success = hub->DisableSensors(args->sensors);
        break;
      case NanotoolCommand::DisableAll:
        success = hub->DisableAllSensors();
        break;
      case NanotoolCommand::Read: {
        if (!args->sensors.size()) {
            hub->PrintAllEvents(args->count);
        } else {
            hub->PrintSensorEvents(args->sensors, args->count);
        }
        break;
      }
      case NanotoolCommand::Poll: {
        success = hub->EnableSensors(args->sensors);
        if (success) {
            hub->PrintSensorEvents(args->sensors, args->count);
        }
        break;
      }
      case NanotoolCommand::Calibrate: {
        hub->DisableSensors(args->sensors);
        success = hub->CalibrateSensors(args->sensors);
        break;
      }
      case NanotoolCommand::Test: {
        hub->DisableSensors(args->sensors);

        /* Most of drivers complete enable/disable after SPI/I2C callback
           transaction return. Since enable/disable functions return immediatly
           before it, some time ensure entire process is completed. */
        usleep(100000);

        success = hub->TestSensors(args->sensors);
        break;
      }
      case NanotoolCommand::LoadCalibration: {
        success = hub->LoadCalibration();
        break;
      }
      case NanotoolCommand::Flash: {
        success = hub->Flash(args->filename);
        break;
      }
      case NanotoolCommand::GetBridgeVer: {
        success = hub->PrintBridgeVersion();
        break;
      }
      default:
        LOGE("Command not implemented");
        return 1;
    }

    if (!success) {
        LOGE("Command failed");
        return -1;
    } else if (args->command != NanotoolCommand::Read
                   && args->command != NanotoolCommand::Poll) {
        printf("Operation completed successfully\n");
    }

    return 0;
}
