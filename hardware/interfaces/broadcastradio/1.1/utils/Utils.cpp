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
#define LOG_TAG "BroadcastRadioDefault.utils"
//#define LOG_NDEBUG 0

#include <broadcastradio-utils/Utils.h>

#include <log/log.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_1 {
namespace utils {

using V1_0::Band;

static bool isCompatibleProgramType(const uint32_t ia, const uint32_t ib) {
    auto a = static_cast<ProgramType>(ia);
    auto b = static_cast<ProgramType>(ib);

    if (a == b) return true;
    if (a == ProgramType::AM && b == ProgramType::AM_HD) return true;
    if (a == ProgramType::AM_HD && b == ProgramType::AM) return true;
    if (a == ProgramType::FM && b == ProgramType::FM_HD) return true;
    if (a == ProgramType::FM_HD && b == ProgramType::FM) return true;
    return false;
}

static bool bothHaveId(const ProgramSelector& a, const ProgramSelector& b,
                       const IdentifierType type) {
    return hasId(a, type) && hasId(b, type);
}

static bool anyHaveId(const ProgramSelector& a, const ProgramSelector& b,
                      const IdentifierType type) {
    return hasId(a, type) || hasId(b, type);
}

static bool haveEqualIds(const ProgramSelector& a, const ProgramSelector& b,
                         const IdentifierType type) {
    if (!bothHaveId(a, b, type)) return false;
    /* We should check all Ids of a given type (ie. other AF),
     * but it doesn't matter for default implementation.
     */
    auto aId = getId(a, type);
    auto bId = getId(b, type);
    return aId == bId;
}

bool tunesTo(const ProgramSelector& a, const ProgramSelector& b) {
    if (!isCompatibleProgramType(a.programType, b.programType)) return false;

    auto type = getType(a);

    switch (type) {
        case ProgramType::AM:
        case ProgramType::AM_HD:
        case ProgramType::FM:
        case ProgramType::FM_HD:
            if (haveEqualIds(a, b, IdentifierType::HD_STATION_ID_EXT)) return true;

            // if HD Radio subchannel is specified, it must match
            if (anyHaveId(a, b, IdentifierType::HD_SUBCHANNEL)) {
                // missing subchannel (analog) is an equivalent of first subchannel (MPS)
                auto aCh = getId(a, IdentifierType::HD_SUBCHANNEL, 0);
                auto bCh = getId(b, IdentifierType::HD_SUBCHANNEL, 0);
                if (aCh != bCh) return false;
            }

            if (haveEqualIds(a, b, IdentifierType::RDS_PI)) return true;

            return haveEqualIds(a, b, IdentifierType::AMFM_FREQUENCY);
        case ProgramType::DAB:
            return haveEqualIds(a, b, IdentifierType::DAB_SIDECC);
        case ProgramType::DRMO:
            return haveEqualIds(a, b, IdentifierType::DRMO_SERVICE_ID);
        case ProgramType::SXM:
            if (anyHaveId(a, b, IdentifierType::SXM_SERVICE_ID)) {
                return haveEqualIds(a, b, IdentifierType::SXM_SERVICE_ID);
            }
            return haveEqualIds(a, b, IdentifierType::SXM_CHANNEL);
        default:  // includes all vendor types
            ALOGW("Unsupported program type: %s", toString(type).c_str());
            return false;
    }
}

ProgramType getType(const ProgramSelector& sel) {
    return static_cast<ProgramType>(sel.programType);
}

bool isAmFm(const ProgramType type) {
    switch (type) {
        case ProgramType::AM:
        case ProgramType::FM:
        case ProgramType::AM_HD:
        case ProgramType::FM_HD:
            return true;
        default:
            return false;
    }
}

bool isAm(const Band band) {
    return band == Band::AM || band == Band::AM_HD;
}

bool isFm(const Band band) {
    return band == Band::FM || band == Band::FM_HD;
}

bool hasId(const ProgramSelector& sel, const IdentifierType type) {
    auto itype = static_cast<uint32_t>(type);
    if (sel.primaryId.type == itype) return true;
    // not optimal, but we don't care in default impl
    for (auto&& id : sel.secondaryIds) {
        if (id.type == itype) return true;
    }
    return false;
}

uint64_t getId(const ProgramSelector& sel, const IdentifierType type) {
    auto itype = static_cast<uint32_t>(type);
    if (sel.primaryId.type == itype) return sel.primaryId.value;
    // not optimal, but we don't care in default impl
    for (auto&& id : sel.secondaryIds) {
        if (id.type == itype) return id.value;
    }
    ALOGW("Identifier %s not found", toString(type).c_str());
    return 0;
}

uint64_t getId(const ProgramSelector& sel, const IdentifierType type, uint64_t defval) {
    if (!hasId(sel, type)) return defval;
    return getId(sel, type);
}

ProgramSelector make_selector(Band band, uint32_t channel, uint32_t subChannel) {
    ProgramSelector sel = {};

    ALOGW_IF((subChannel > 0) && (band == Band::AM || band == Band::FM),
             "got subChannel for non-HD AM/FM");

    // we can't use ProgramType::AM_HD or FM_HD, because we don't know HD station ID
    ProgramType type;
    if (isAm(band)) {
        type = ProgramType::AM;
    } else if (isFm(band)) {
        type = ProgramType::FM;
    } else {
        LOG_ALWAYS_FATAL("Unsupported band: %s", toString(band).c_str());
    }

    sel.programType = static_cast<uint32_t>(type);
    sel.primaryId.type = static_cast<uint32_t>(IdentifierType::AMFM_FREQUENCY);
    sel.primaryId.value = channel;
    if (subChannel > 0) {
        /* stating sub channel for AM/FM channel does not give any guarantees,
         * but we can't do much more without HD station ID
         *
         * The legacy APIs had 1-based subChannels, while ProgramSelector is 0-based.
         */
        sel.secondaryIds = hidl_vec<ProgramIdentifier>{
            {static_cast<uint32_t>(IdentifierType::HD_SUBCHANNEL), subChannel - 1},
        };
    }

    return sel;
}

bool getLegacyChannel(const ProgramSelector& sel, uint32_t* channelOut, uint32_t* subChannelOut) {
    if (channelOut) *channelOut = 0;
    if (subChannelOut) *subChannelOut = 0;
    if (isAmFm(getType(sel))) {
        if (channelOut) *channelOut = getId(sel, IdentifierType::AMFM_FREQUENCY);
        if (subChannelOut && hasId(sel, IdentifierType::HD_SUBCHANNEL)) {
            // The legacy APIs had 1-based subChannels, while ProgramSelector is 0-based.
            *subChannelOut = getId(sel, IdentifierType::HD_SUBCHANNEL) + 1;
        }
        return true;
    }
    return false;
}

bool isDigital(const ProgramSelector& sel) {
    switch (getType(sel)) {
        case ProgramType::AM:
        case ProgramType::FM:
            return false;
        default:
            // VENDOR might not be digital, but it doesn't matter for default impl.
            return true;
    }
}

}  // namespace utils
}  // namespace V1_1

namespace V1_0 {

bool operator==(const BandConfig& l, const BandConfig& r) {
    if (l.type != r.type) return false;
    if (l.antennaConnected != r.antennaConnected) return false;
    if (l.lowerLimit != r.lowerLimit) return false;
    if (l.upperLimit != r.upperLimit) return false;
    if (l.spacings != r.spacings) return false;
    if (V1_1::utils::isAm(l.type)) {
        return l.ext.am == r.ext.am;
    } else if (V1_1::utils::isFm(l.type)) {
        return l.ext.fm == r.ext.fm;
    } else {
        ALOGW("Unsupported band config type: %s", toString(l.type).c_str());
        return false;
    }
}

}  // namespace V1_0
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android
