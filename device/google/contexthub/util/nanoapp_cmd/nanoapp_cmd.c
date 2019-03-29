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

#define LOG_TAG "nanoapp_cmd"

#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <signal.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <string.h>
#include <unistd.h>

#include <android/log.h>

#include <nanohub/nanohub.h>
#include <eventnums.h>
#include <sensType.h>

#define SENSOR_RATE_ONCHANGE    0xFFFFFF01UL
#define SENSOR_RATE_ONESHOT     0xFFFFFF02UL
#define SENSOR_HZ(_hz)          ((uint32_t)((_hz) * 1024.0f))
#define MAX_APP_NAME_LEN        32
#define MAX_INSTALL_CNT         8
#define MAX_UNINSTALL_CNT       8
#define MAX_DOWNLOAD_RETRIES    4
#define UNINSTALL_CMD           "uninstall"

#define NANOHUB_EXT_APP_DELETE  2

#define LOGE(fmt, ...) do { \
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, ##__VA_ARGS__); \
        printf(fmt "\n", ##__VA_ARGS__); \
    } while (0)

enum ConfigCmds
{
    CONFIG_CMD_DISABLE      = 0,
    CONFIG_CMD_ENABLE       = 1,
    CONFIG_CMD_FLUSH        = 2,
    CONFIG_CMD_CFG_DATA     = 3,
    CONFIG_CMD_CALIBRATE    = 4,
};

struct ConfigCmd
{
    uint32_t evtType;
    uint64_t latency;
    uint32_t rate;
    uint8_t sensorType;
    uint8_t cmd;
    uint16_t flags;
    uint8_t data[];
} __attribute__((packed));

struct App
{
    uint32_t num;
    uint64_t id;
    uint32_t version;
    uint32_t size;
};

struct LedsCfg {
    uint32_t led_num;
    uint32_t value;
} __attribute__((packed));

static int setType(struct ConfigCmd *cmd, char *sensor)
{
    if (strcmp(sensor, "accel") == 0) {
        cmd->sensorType = SENS_TYPE_ACCEL;
    } else if (strcmp(sensor, "gyro") == 0) {
        cmd->sensorType = SENS_TYPE_GYRO;
    } else if (strcmp(sensor, "mag") == 0) {
        cmd->sensorType = SENS_TYPE_MAG;
    } else if (strcmp(sensor, "uncal_gyro") == 0) {
        cmd->sensorType = SENS_TYPE_GYRO;
    } else if (strcmp(sensor, "uncal_mag") == 0) {
        cmd->sensorType = SENS_TYPE_MAG;
    } else if (strcmp(sensor, "als") == 0) {
        cmd->sensorType = SENS_TYPE_ALS;
    } else if (strcmp(sensor, "prox") == 0) {
        cmd->sensorType = SENS_TYPE_PROX;
    } else if (strcmp(sensor, "baro") == 0) {
        cmd->sensorType = SENS_TYPE_BARO;
    } else if (strcmp(sensor, "temp") == 0) {
        cmd->sensorType = SENS_TYPE_TEMP;
    } else if (strcmp(sensor, "orien") == 0) {
        cmd->sensorType = SENS_TYPE_ORIENTATION;
    } else if (strcmp(sensor, "gravity") == 0) {
        cmd->sensorType = SENS_TYPE_GRAVITY;
    } else if (strcmp(sensor, "geomag") == 0) {
        cmd->sensorType = SENS_TYPE_GEO_MAG_ROT_VEC;
    } else if (strcmp(sensor, "linear_acc") == 0) {
        cmd->sensorType = SENS_TYPE_LINEAR_ACCEL;
    } else if (strcmp(sensor, "rotation") == 0) {
        cmd->sensorType = SENS_TYPE_ROTATION_VECTOR;
    } else if (strcmp(sensor, "game") == 0) {
        cmd->sensorType = SENS_TYPE_GAME_ROT_VECTOR;
    } else if (strcmp(sensor, "win_orien") == 0) {
        cmd->sensorType = SENS_TYPE_WIN_ORIENTATION;
        cmd->rate = SENSOR_RATE_ONCHANGE;
    } else if (strcmp(sensor, "tilt") == 0) {
        cmd->sensorType = SENS_TYPE_TILT;
        cmd->rate = SENSOR_RATE_ONCHANGE;
    } else if (strcmp(sensor, "step_det") == 0) {
        cmd->sensorType = SENS_TYPE_STEP_DETECT;
        cmd->rate = SENSOR_RATE_ONCHANGE;
    } else if (strcmp(sensor, "step_cnt") == 0) {
        cmd->sensorType = SENS_TYPE_STEP_COUNT;
        cmd->rate = SENSOR_RATE_ONCHANGE;
    } else if (strcmp(sensor, "double_tap") == 0) {
        cmd->sensorType = SENS_TYPE_DOUBLE_TAP;
        cmd->rate = SENSOR_RATE_ONCHANGE;
    } else if (strcmp(sensor, "flat") == 0) {
        cmd->sensorType = SENS_TYPE_FLAT;
        cmd->rate = SENSOR_RATE_ONCHANGE;
    } else if (strcmp(sensor, "anymo") == 0) {
        cmd->sensorType = SENS_TYPE_ANY_MOTION;
        cmd->rate = SENSOR_RATE_ONCHANGE;
    } else if (strcmp(sensor, "nomo") == 0) {
        cmd->sensorType = SENS_TYPE_NO_MOTION;
        cmd->rate = SENSOR_RATE_ONCHANGE;
    } else if (strcmp(sensor, "sigmo") == 0) {
        cmd->sensorType = SENS_TYPE_SIG_MOTION;
        cmd->rate = SENSOR_RATE_ONESHOT;
    } else if (strcmp(sensor, "gesture") == 0) {
        cmd->sensorType = SENS_TYPE_GESTURE;
        cmd->rate = SENSOR_RATE_ONESHOT;
    } else if (strcmp(sensor, "hall") == 0) {
        cmd->sensorType = SENS_TYPE_HALL;
        cmd->rate = SENSOR_RATE_ONCHANGE;
    } else if (strcmp(sensor, "vsync") == 0) {
        cmd->sensorType = SENS_TYPE_VSYNC;
        cmd->rate = SENSOR_RATE_ONCHANGE;
    } else if (strcmp(sensor, "activity") == 0) {
        cmd->sensorType = SENS_TYPE_ACTIVITY;
        cmd->rate = SENSOR_RATE_ONCHANGE;
    } else if (strcmp(sensor, "twist") == 0) {
        cmd->sensorType = SENS_TYPE_DOUBLE_TWIST;
        cmd->rate = SENSOR_RATE_ONCHANGE;
    } else if (strcmp(sensor, "leds") == 0) {
        cmd->sensorType = SENS_TYPE_LEDS;
    } else if (strcmp(sensor, "leds_i2c") == 0) {
        cmd->sensorType = SENS_TYPE_LEDS_I2C;
    } else if (strcmp(sensor, "humidity") == 0) {
        cmd->sensorType = SENS_TYPE_HUMIDITY;
    } else {
        return 1;
    }

    return 0;
}

bool drain = false;
bool stop = false;
char *buf;
int nread, buf_size = 2048;
struct App apps[32];
uint8_t appCount;
char appsToInstall[MAX_INSTALL_CNT][MAX_APP_NAME_LEN+1];
uint64_t appsToUninstall[MAX_UNINSTALL_CNT];

void sig_handle(__attribute__((unused)) int sig)
{
    assert(sig == SIGINT);
    printf("Terminating...\n");
    stop = true;
}

FILE *openFile(const char *fname, const char *mode)
{
    FILE *f = fopen(fname, mode);
    if (f == NULL) {
        LOGE("Failed to open %s: err=%d [%s]", fname, errno, strerror(errno));
    }
    return f;
}

void parseInstalledAppInfo()
{
    FILE *fp;
    char *line = NULL;
    size_t len;
    ssize_t numRead;

    appCount = 0;

    fp = openFile("/sys/class/nanohub/nanohub/app_info", "r");
    if (!fp)
        return;

    while ((numRead = getline(&line, &len, fp)) != -1) {
        struct App *currApp = &apps[appCount++];
        sscanf(line, "app: %d id: %" PRIx64 " ver: %" PRIx32 " size: %" PRIx32 "\n", &currApp->num, &currApp->id, &currApp->version, &currApp->size);
    }

    fclose(fp);

    if (line)
        free(line);
}

struct App *findApp(uint64_t appId)
{
    uint8_t i;

    for (i = 0; i < appCount; i++) {
        if (apps[i].id == appId) {
            return &apps[i];
        }
    }

    return NULL;
}

int parseConfigAppInfo(int *installCnt, int *uninstallCnt)
{
    FILE *fp;
    char *line = NULL;
    size_t len;
    ssize_t numRead;

    fp = openFile("/vendor/firmware/napp_list.cfg", "r");
    if (!fp)
        return -1;

    parseInstalledAppInfo();

    *installCnt = *uninstallCnt = 0;
    while (((numRead = getline(&line, &len, fp)) != -1) && (*installCnt < MAX_INSTALL_CNT) && (*uninstallCnt < MAX_UNINSTALL_CNT)) {
        uint64_t appId;
        uint32_t appVersion;
        struct App *installedApp;

        sscanf(line, "%" STRINGIFY(MAX_APP_NAME_LEN) "s %" PRIx64 " %" PRIx32 "\n", appsToInstall[*installCnt], &appId, &appVersion);

        installedApp = findApp(appId);
        if (strncmp(appsToInstall[*installCnt], UNINSTALL_CMD, MAX_APP_NAME_LEN) == 0) {
            if (installedApp) {
                appsToUninstall[*uninstallCnt] = appId;
                (*uninstallCnt)++;
            }
        } else if (!installedApp || (installedApp->version < appVersion)) {
            (*installCnt)++;
        }
    }

    fclose(fp);

    if (line)
        free(line);

    return *installCnt + *uninstallCnt;
}

bool fileWriteData(const char *fname, const void *data, size_t size)
{
    int fd;
    bool result;

    fd = open(fname, O_WRONLY);
    if (fd < 0) {
        LOGE("Failed to open %s: err=%d [%s]", fname, errno, strerror(errno));
        return false;
    }

    result = true;
    if ((size_t)write(fd, data, size) != size) {
        LOGE("Failed to write to %s; err=%d [%s]", fname, errno, strerror(errno));
        result = false;
    }
    close(fd);

    return result;
}

void downloadNanohub()
{
    char c = '1';

    printf("Updating nanohub OS [if required]...");
    fflush(stdout);
    if (fileWriteData("/sys/class/nanohub/nanohub/download_bl", &c, sizeof(c)))
        printf("done\n");
}

void removeApps(int updateCnt)
{
    uint8_t buffer[sizeof(struct HostMsgHdr) + 1 + sizeof(uint64_t)];
    struct HostMsgHdr *mHostMsgHdr = (struct HostMsgHdr *)(&buffer[0]);
    uint8_t *cmd = (uint8_t *)(&buffer[sizeof(struct HostMsgHdr)]);
    uint64_t *appId = (uint64_t *)(&buffer[sizeof(struct HostMsgHdr) + 1]);
    int i;

    for (i = 0; i < updateCnt; i++) {
        mHostMsgHdr->eventId = EVT_APP_FROM_HOST;
        mHostMsgHdr->appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0);
        mHostMsgHdr->len = 1 + sizeof(uint64_t);
        *cmd = NANOHUB_EXT_APP_DELETE;
        memcpy(appId, &appsToUninstall[i], sizeof(uint64_t));
        printf("Deleting \"%016" PRIx64 "\"...", appsToUninstall[i]);
        fflush(stdout);
        if (fileWriteData("/dev/nanohub", buffer, sizeof(buffer)))
            printf("done\n");
    }
}

void downloadApps(int updateCnt)
{
    int i;

    for (i = 0; i < updateCnt; i++) {
        printf("Downloading \"%s.napp\"...", appsToInstall[i]);
        fflush(stdout);
        if (fileWriteData("/sys/class/nanohub/nanohub/download_app", appsToInstall[i], strlen(appsToInstall[i])))
            printf("done\n");
    }
}

void eraseSharedArea()
{
    char c = '1';

    printf("Erasing entire nanohub shared area...");
    fflush(stdout);
    if (fileWriteData("/sys/class/nanohub/nanohub/erase_shared", &c, sizeof(c)))
        printf("done\n");
}

void resetHub()
{
    char c = '1';

    printf("Resetting nanohub...");
    fflush(stdout);
    if (fileWriteData("/sys/class/nanohub/nanohub/reset", &c, sizeof(c)))
        printf("done\n");
}

int main(int argc, char *argv[])
{
    struct ConfigCmd mConfigCmd;
    struct ConfigCmd *pConfigCmd = &mConfigCmd;
    size_t length = sizeof(mConfigCmd);
    int fd;
    int i;

    if (argc < 3 && (argc < 2 || strcmp(argv[1], "download") != 0)) {
        printf("usage: %s <action> <sensor> <data> -d\n", argv[0]);
        printf("       action: config|cfgdata|calibrate|flush|download\n");
        printf("       sensor: accel|(uncal_)gyro|(uncal_)mag|als|prox|baro|temp|orien\n");
        printf("               gravity|geomag|linear_acc|rotation|game\n");
        printf("               win_orien|tilt|step_det|step_cnt|double_tap\n");
        printf("               flat|anymo|nomo|sigmo|gesture|hall|vsync\n");
        printf("               activity|twist|leds|leds_i2c|humidity\n");
        printf("       data: config: <true|false> <rate in Hz> <latency in u-sec>\n");
        printf("             cfgdata: leds: led_num value\n");
        printf("             calibrate: [N.A.]\n");
        printf("             flush: [N.A.]\n");
        printf("       -d: if specified, %s will keep draining /dev/nanohub until cancelled.\n", argv[0]);

        return 1;
    }

    if (strcmp(argv[1], "config") == 0) {
        if (argc != 6 && argc != 7) {
            printf("Wrong arg number\n");
            return 1;
        }
        if (argc == 7) {
            if(strcmp(argv[6], "-d") == 0) {
                drain = true;
            } else {
                printf("Last arg unsupported, ignored.\n");
            }
        }
        if (strcmp(argv[3], "true") == 0)
            mConfigCmd.cmd = CONFIG_CMD_ENABLE;
        else if (strcmp(argv[3], "false") == 0) {
            mConfigCmd.cmd = CONFIG_CMD_DISABLE;
        } else {
            printf("Unsupported data: %s For action: %s\n", argv[3], argv[1]);
            return 1;
        }
        mConfigCmd.evtType = EVT_NO_SENSOR_CONFIG_EVENT;
        mConfigCmd.rate = SENSOR_HZ((float)atoi(argv[4]));
        mConfigCmd.latency = atoi(argv[5]) * 1000ull;
        if (setType(&mConfigCmd, argv[2])) {
            printf("Unsupported sensor: %s For action: %s\n", argv[2], argv[1]);
            return 1;
        }
    } else if (strcmp(argv[1], "cfgdata") == 0) {
        mConfigCmd.evtType = EVT_NO_SENSOR_CONFIG_EVENT;
        mConfigCmd.rate = 0;
        mConfigCmd.latency = 0;
        mConfigCmd.cmd = CONFIG_CMD_CFG_DATA;
        if (setType(&mConfigCmd, argv[2])) {
            printf("Unsupported sensor: %s For action: %s\n", argv[2], argv[1]);
            return 1;
        }
        if (mConfigCmd.sensorType == SENS_TYPE_LEDS ||
            mConfigCmd.sensorType == SENS_TYPE_LEDS_I2C) {
            struct LedsCfg mLedsCfg;

            if (argc != 5) {
                printf("Wrong arg number\n");
                return 1;
            }
            length = sizeof(struct ConfigCmd) + sizeof(struct LedsCfg *);
            pConfigCmd = (struct ConfigCmd *)malloc(length);
            if (!pConfigCmd)
                return 1;
            mLedsCfg.led_num = atoi(argv[3]);
            mLedsCfg.value = atoi(argv[4]);
            memcpy(pConfigCmd, &mConfigCmd, sizeof(mConfigCmd));
            memcpy(pConfigCmd->data, &mLedsCfg, sizeof(mLedsCfg));
        } else {
            printf("Unsupported sensor: %s For action: %s\n", argv[2], argv[1]);
            return 1;
        }
    } else if (strcmp(argv[1], "calibrate") == 0) {
        if (argc != 3) {
            printf("Wrong arg number\n");
            return 1;
        }
        mConfigCmd.evtType = EVT_NO_SENSOR_CONFIG_EVENT;
        mConfigCmd.rate = 0;
        mConfigCmd.latency = 0;
        mConfigCmd.cmd = CONFIG_CMD_CALIBRATE;
        if (setType(&mConfigCmd, argv[2])) {
            printf("Unsupported sensor: %s For action: %s\n", argv[2], argv[1]);
            return 1;
        }
    } else if (strcmp(argv[1], "flush") == 0) {
        if (argc != 3) {
            printf("Wrong arg number\n");
            return 1;
        }
        mConfigCmd.evtType = EVT_NO_SENSOR_CONFIG_EVENT;
        mConfigCmd.rate = 0;
        mConfigCmd.latency = 0;
        mConfigCmd.cmd = CONFIG_CMD_FLUSH;
        if (setType(&mConfigCmd, argv[2])) {
            printf("Unsupported sensor: %s For action: %s\n", argv[2], argv[1]);
            return 1;
        }
    } else if (strcmp(argv[1], "download") == 0) {
        int installCnt, uninstallCnt;

        if (argc != 2) {
            printf("Wrong arg number\n");
            return 1;
        }
        downloadNanohub();
        for (i = 0; i < MAX_DOWNLOAD_RETRIES; i++) {
            int updateCnt = parseConfigAppInfo(&installCnt, &uninstallCnt);
            if (updateCnt > 0) {
                if (i == MAX_DOWNLOAD_RETRIES - 1) {
                    LOGE("Download failed after %d retries; erasing all apps "
                         "before final attempt", i);
                    eraseSharedArea();
                    parseConfigAppInfo(&installCnt, &uninstallCnt);
                }
                removeApps(uninstallCnt);
                downloadApps(installCnt);
                resetHub();
            } else if (!updateCnt){
                return 0;
            }
        }

        if (parseConfigAppInfo(&installCnt, &uninstallCnt) != 0) {
            LOGE("Failed to download all apps!");
        }
        return 1;
    } else {
        printf("Unsupported action: %s\n", argv[1]);
        return 1;
    }

    while (!fileWriteData("/dev/nanohub", pConfigCmd, length))
        continue;

    if (pConfigCmd != &mConfigCmd)
        free(pConfigCmd);

    if (drain) {
        signal(SIGINT, sig_handle);
        fd = open("/dev/nanohub", O_RDONLY);
        while (!stop) {
            (void) read(fd, buf, buf_size);
        }
        close(fd);
    }
    return 0;
}
