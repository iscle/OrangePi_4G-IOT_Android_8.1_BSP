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
#define LOG_TAG "BroadcastRadioDefault.VirtualRadio"
//#define LOG_NDEBUG 0

#include "VirtualRadio.h"

#include <broadcastradio-utils/Utils.h>
#include <log/log.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_1 {
namespace implementation {

using V1_0::Band;
using V1_0::Class;

using std::lock_guard;
using std::move;
using std::mutex;
using std::vector;

using utils::make_selector;

static const vector<VirtualProgram> gInitialFmPrograms{
    {make_selector(Band::FM, 94900), "Wild 94.9", "Drake ft. Rihanna", "Too Good"},
    {make_selector(Band::FM, 96500), "KOIT", "Celine Dion", "All By Myself"},
    {make_selector(Band::FM, 97300), "Alice@97.3", "Drops of Jupiter", "Train"},
    {make_selector(Band::FM, 99700), "99.7 Now!", "The Chainsmokers", "Closer"},
    {make_selector(Band::FM, 101300), "101-3 KISS-FM", "Justin Timberlake", "Rock Your Body"},
    {make_selector(Band::FM, 103700), "iHeart80s @ 103.7", "Michael Jackson", "Billie Jean"},
    {make_selector(Band::FM, 106100), "106 KMEL", "Drake", "Marvins Room"},
};

static VirtualRadio gEmptyRadio({});
static VirtualRadio gFmRadio(gInitialFmPrograms);

VirtualRadio::VirtualRadio(const vector<VirtualProgram> initialList) : mPrograms(initialList) {}

vector<VirtualProgram> VirtualRadio::getProgramList() {
    lock_guard<mutex> lk(mMut);
    return mPrograms;
}

bool VirtualRadio::getProgram(const ProgramSelector& selector, VirtualProgram& programOut) {
    lock_guard<mutex> lk(mMut);
    for (auto&& program : mPrograms) {
        if (utils::tunesTo(selector, program.selector)) {
            programOut = program;
            return true;
        }
    }
    return false;
}

VirtualRadio& getRadio(V1_0::Class classId) {
    switch (classId) {
        case Class::AM_FM:
            return getFmRadio();
        case Class::SAT:
            return getSatRadio();
        case Class::DT:
            return getDigitalRadio();
        default:
            ALOGE("Invalid class ID");
            return gEmptyRadio;
    }
}

VirtualRadio& getAmRadio() {
    return gEmptyRadio;
}

VirtualRadio& getFmRadio() {
    return gFmRadio;
}

VirtualRadio& getSatRadio() {
    return gEmptyRadio;
}

VirtualRadio& getDigitalRadio() {
    return gEmptyRadio;
}

}  // namespace implementation
}  // namespace V1_1
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android
