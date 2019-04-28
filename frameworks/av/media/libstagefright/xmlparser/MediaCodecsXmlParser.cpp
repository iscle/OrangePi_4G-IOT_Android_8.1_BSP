/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "MediaCodecsXmlParser"

#include <media/stagefright/xmlparser/MediaCodecsXmlParser.h>

#include <utils/Log.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/omx/OMXUtils.h>
#include <sys/stat.h>
#include <expat.h>

#include <cctype>
#include <algorithm>

namespace android {

namespace {

/**
 * Search for a file in a list of search directories.
 *
 * For each string `searchDir` in `searchDirs`, `searchDir/fileName` will be
 * tested whether it is a valid file name or not. If it is a valid file name,
 * the concatenated name (`searchDir/fileName`) will be stored in the output
 * variable `outPath`, and the function will return `true`. Otherwise, the
 * search continues until the `nullptr` element in `searchDirs` is reached, at
 * which point the function returns `false`.
 *
 * \param[in] searchDirs Null-terminated array of search paths.
 * \param[in] fileName Name of the file to search.
 * \param[out] outPath Full path of the file. `outPath` will hold a valid file
 * name if the return value of this function is `true`.
 * \return `true` if some element in `searchDirs` combined with `fileName` is a
 * valid file name; `false` otherwise.
 */
bool findFileInDirs(
        const char* const* searchDirs,
        const char *fileName,
        std::string *outPath) {
    for (; *searchDirs != nullptr; ++searchDirs) {
        *outPath = std::string(*searchDirs) + "/" + fileName;
        struct stat fileStat;
        if (stat(outPath->c_str(), &fileStat) == 0 &&
                S_ISREG(fileStat.st_mode)) {
            return true;
        }
    }
    return false;
}

bool strnEq(const char* s1, const char* s2, size_t count) {
    return strncmp(s1, s2, count) == 0;
}

bool strEq(const char* s1, const char* s2) {
    return strcmp(s1, s2) == 0;
}

bool striEq(const char* s1, const char* s2) {
    return strcasecmp(s1, s2) == 0;
}

bool strHasPrefix(const char* test, const char* prefix) {
    return strnEq(test, prefix, strlen(prefix));
}

bool parseBoolean(const char* s) {
    return striEq(s, "y") ||
            striEq(s, "yes") ||
            striEq(s, "t") ||
            striEq(s, "true") ||
            striEq(s, "1");
}

status_t limitFoundMissingAttr(const char* name, const char *attr, bool found = true) {
    ALOGE("limit '%s' with %s'%s' attribute", name,
            (found ? "" : "no "), attr);
    return -EINVAL;
}

status_t limitError(const char* name, const char *msg) {
    ALOGE("limit '%s' %s", name, msg);
    return -EINVAL;
}

status_t limitInvalidAttr(const char* name, const char* attr, const char* value) {
    ALOGE("limit '%s' with invalid '%s' attribute (%s)", name,
            attr, value);
    return -EINVAL;
}

}; // unnamed namespace

constexpr char const* MediaCodecsXmlParser::defaultSearchDirs[];
constexpr char const* MediaCodecsXmlParser::defaultMainXmlName;
constexpr char const* MediaCodecsXmlParser::defaultPerformanceXmlName;
constexpr char const* MediaCodecsXmlParser::defaultProfilingResultsXmlPath;

MediaCodecsXmlParser::MediaCodecsXmlParser(
        const char* const* searchDirs,
        const char* mainXmlName,
        const char* performanceXmlName,
        const char* profilingResultsXmlPath) :
    mParsingStatus(NO_INIT),
    mUpdate(false),
    mCodecCounter(0) {
    std::string path;
    if (findFileInDirs(searchDirs, mainXmlName, &path)) {
        parseTopLevelXMLFile(path.c_str(), false);
    } else {
        ALOGE("Cannot find %s", mainXmlName);
        mParsingStatus = NAME_NOT_FOUND;
    }
    if (findFileInDirs(searchDirs, performanceXmlName, &path)) {
        parseTopLevelXMLFile(path.c_str(), true);
    }
    if (profilingResultsXmlPath != nullptr) {
        parseTopLevelXMLFile(profilingResultsXmlPath, true);
    }
}

bool MediaCodecsXmlParser::parseTopLevelXMLFile(
        const char *codecs_xml,
        bool ignore_errors) {
    // get href_base
    const char *href_base_end = strrchr(codecs_xml, '/');
    if (href_base_end != nullptr) {
        mHrefBase = std::string(codecs_xml, href_base_end - codecs_xml + 1);
    }

    mParsingStatus = OK; // keeping this here for safety
    mCurrentSection = SECTION_TOPLEVEL;

    parseXMLFile(codecs_xml);

    if (mParsingStatus != OK) {
        ALOGW("parseTopLevelXMLFile(%s) failed", codecs_xml);
        if (ignore_errors) {
            mParsingStatus = OK;
            return false;
        }
        mCodecMap.clear();
        return false;
    }
    return true;
}

MediaCodecsXmlParser::~MediaCodecsXmlParser() {
}

void MediaCodecsXmlParser::parseXMLFile(const char *path) {
    FILE *file = fopen(path, "r");

    if (file == nullptr) {
        ALOGW("unable to open media codecs configuration xml file: %s", path);
        mParsingStatus = NAME_NOT_FOUND;
        return;
    }

    XML_Parser parser = ::XML_ParserCreate(nullptr);
    LOG_FATAL_IF(parser == nullptr, "XML_MediaCodecsXmlParserCreate() failed.");

    ::XML_SetUserData(parser, this);
    ::XML_SetElementHandler(
            parser, StartElementHandlerWrapper, EndElementHandlerWrapper);

    static constexpr int BUFF_SIZE = 512;
    while (mParsingStatus == OK) {
        void *buff = ::XML_GetBuffer(parser, BUFF_SIZE);
        if (buff == nullptr) {
            ALOGE("failed in call to XML_GetBuffer()");
            mParsingStatus = UNKNOWN_ERROR;
            break;
        }

        int bytes_read = ::fread(buff, 1, BUFF_SIZE, file);
        if (bytes_read < 0) {
            ALOGE("failed in call to read");
            mParsingStatus = ERROR_IO;
            break;
        }

        XML_Status status = ::XML_ParseBuffer(parser, bytes_read, bytes_read == 0);
        if (status != XML_STATUS_OK) {
            ALOGE("malformed (%s)", ::XML_ErrorString(::XML_GetErrorCode(parser)));
            mParsingStatus = ERROR_MALFORMED;
            break;
        }

        if (bytes_read == 0) {
            break;
        }
    }

    ::XML_ParserFree(parser);

    fclose(file);
    file = nullptr;
}

// static
void MediaCodecsXmlParser::StartElementHandlerWrapper(
        void *me, const char *name, const char **attrs) {
    static_cast<MediaCodecsXmlParser*>(me)->startElementHandler(name, attrs);
}

// static
void MediaCodecsXmlParser::EndElementHandlerWrapper(void *me, const char *name) {
    static_cast<MediaCodecsXmlParser*>(me)->endElementHandler(name);
}

status_t MediaCodecsXmlParser::includeXMLFile(const char **attrs) {
    const char *href = nullptr;
    size_t i = 0;
    while (attrs[i] != nullptr) {
        if (strEq(attrs[i], "href")) {
            if (attrs[++i] == nullptr) {
                return -EINVAL;
            }
            href = attrs[i];
        } else {
            ALOGE("includeXMLFile: unrecognized attribute: %s", attrs[i]);
            return -EINVAL;
        }
        ++i;
    }

    // For security reasons and for simplicity, file names can only contain
    // [a-zA-Z0-9_.] and must start with  media_codecs_ and end with .xml
    for (i = 0; href[i] != '\0'; i++) {
        if (href[i] == '.' || href[i] == '_' ||
                (href[i] >= '0' && href[i] <= '9') ||
                (href[i] >= 'A' && href[i] <= 'Z') ||
                (href[i] >= 'a' && href[i] <= 'z')) {
            continue;
        }
        ALOGE("invalid include file name: %s", href);
        return -EINVAL;
    }

    std::string filename = href;
    if (filename.compare(0, 13, "media_codecs_") != 0 ||
            filename.compare(filename.size() - 4, 4, ".xml") != 0) {
        ALOGE("invalid include file name: %s", href);
        return -EINVAL;
    }
    filename.insert(0, mHrefBase);

    parseXMLFile(filename.c_str());
    return mParsingStatus;
}

void MediaCodecsXmlParser::startElementHandler(
        const char *name, const char **attrs) {
    if (mParsingStatus != OK) {
        return;
    }

    bool inType = true;

    if (strEq(name, "Include")) {
        mParsingStatus = includeXMLFile(attrs);
        if (mParsingStatus == OK) {
            mSectionStack.push_back(mCurrentSection);
            mCurrentSection = SECTION_INCLUDE;
        }
        return;
    }

    switch (mCurrentSection) {
        case SECTION_TOPLEVEL:
        {
            if (strEq(name, "Decoders")) {
                mCurrentSection = SECTION_DECODERS;
            } else if (strEq(name, "Encoders")) {
                mCurrentSection = SECTION_ENCODERS;
            } else if (strEq(name, "Settings")) {
                mCurrentSection = SECTION_SETTINGS;
            }
            break;
        }

        case SECTION_SETTINGS:
        {
            if (strEq(name, "Setting")) {
                mParsingStatus = addSettingFromAttributes(attrs);
            }
            break;
        }

        case SECTION_DECODERS:
        {
            if (strEq(name, "MediaCodec")) {
                mParsingStatus =
                    addMediaCodecFromAttributes(false /* encoder */, attrs);

                mCurrentSection = SECTION_DECODER;
            }
            break;
        }

        case SECTION_ENCODERS:
        {
            if (strEq(name, "MediaCodec")) {
                mParsingStatus =
                    addMediaCodecFromAttributes(true /* encoder */, attrs);

                mCurrentSection = SECTION_ENCODER;
            }
            break;
        }

        case SECTION_DECODER:
        case SECTION_ENCODER:
        {
            if (strEq(name, "Quirk")) {
                mParsingStatus = addQuirk(attrs);
            } else if (strEq(name, "Type")) {
                mParsingStatus = addTypeFromAttributes(attrs,
                        (mCurrentSection == SECTION_ENCODER));
                mCurrentSection =
                        (mCurrentSection == SECTION_DECODER ?
                        SECTION_DECODER_TYPE : SECTION_ENCODER_TYPE);
            }
        }
        inType = false;
        // fall through

        case SECTION_DECODER_TYPE:
        case SECTION_ENCODER_TYPE:
        {
            // ignore limits and features specified outside of type
            bool outside = !inType &&
                    mCurrentType == mCurrentCodec->second.typeMap.end();
            if (outside &&
                    (strEq(name, "Limit") || strEq(name, "Feature"))) {
                ALOGW("ignoring %s specified outside of a Type", name);
            } else if (strEq(name, "Limit")) {
                mParsingStatus = addLimit(attrs);
            } else if (strEq(name, "Feature")) {
                mParsingStatus = addFeature(attrs);
            }
            break;
        }

        default:
            break;
    }

}

void MediaCodecsXmlParser::endElementHandler(const char *name) {
    if (mParsingStatus != OK) {
        return;
    }

    switch (mCurrentSection) {
        case SECTION_SETTINGS:
        {
            if (strEq(name, "Settings")) {
                mCurrentSection = SECTION_TOPLEVEL;
            }
            break;
        }

        case SECTION_DECODERS:
        {
            if (strEq(name, "Decoders")) {
                mCurrentSection = SECTION_TOPLEVEL;
            }
            break;
        }

        case SECTION_ENCODERS:
        {
            if (strEq(name, "Encoders")) {
                mCurrentSection = SECTION_TOPLEVEL;
            }
            break;
        }

        case SECTION_DECODER_TYPE:
        case SECTION_ENCODER_TYPE:
        {
            if (strEq(name, "Type")) {
                mCurrentSection =
                        (mCurrentSection == SECTION_DECODER_TYPE ?
                        SECTION_DECODER : SECTION_ENCODER);

                mCurrentType = mCurrentCodec->second.typeMap.end();
            }
            break;
        }

        case SECTION_DECODER:
        {
            if (strEq(name, "MediaCodec")) {
                mCurrentSection = SECTION_DECODERS;
                mCurrentName.clear();
            }
            break;
        }

        case SECTION_ENCODER:
        {
            if (strEq(name, "MediaCodec")) {
                mCurrentSection = SECTION_ENCODERS;
                mCurrentName.clear();
            }
            break;
        }

        case SECTION_INCLUDE:
        {
            if (strEq(name, "Include") && (mSectionStack.size() > 0)) {
                mCurrentSection = mSectionStack.back();
                mSectionStack.pop_back();
            }
            break;
        }

        default:
            break;
    }

}

status_t MediaCodecsXmlParser::addSettingFromAttributes(const char **attrs) {
    const char *name = nullptr;
    const char *value = nullptr;
    const char *update = nullptr;

    size_t i = 0;
    while (attrs[i] != nullptr) {
        if (strEq(attrs[i], "name")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addSettingFromAttributes: name is null");
                return -EINVAL;
            }
            name = attrs[i];
        } else if (strEq(attrs[i], "value")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addSettingFromAttributes: value is null");
                return -EINVAL;
            }
            value = attrs[i];
        } else if (strEq(attrs[i], "update")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addSettingFromAttributes: update is null");
                return -EINVAL;
            }
            update = attrs[i];
        } else {
            ALOGE("addSettingFromAttributes: unrecognized attribute: %s", attrs[i]);
            return -EINVAL;
        }
        ++i;
    }

    if (name == nullptr || value == nullptr) {
        ALOGE("addSettingFromAttributes: name or value unspecified");
        return -EINVAL;
    }

    // Boolean values are converted to "0" or "1".
    if (strHasPrefix(name, "supports-")) {
        value = parseBoolean(value) ? "1" : "0";
    }

    mUpdate = (update != nullptr) && parseBoolean(update);
    auto attribute = mServiceAttributeMap.find(name);
    if (attribute == mServiceAttributeMap.end()) { // New attribute name
        if (mUpdate) {
            ALOGE("addSettingFromAttributes: updating non-existing setting");
            return -EINVAL;
        }
        mServiceAttributeMap.insert(Attribute(name, value));
    } else { // Existing attribute name
        if (!mUpdate) {
            ALOGE("addSettingFromAttributes: adding existing setting");
        }
        attribute->second = value;
    }

    return OK;
}

status_t MediaCodecsXmlParser::addMediaCodecFromAttributes(
        bool encoder, const char **attrs) {
    const char *name = nullptr;
    const char *type = nullptr;
    const char *update = nullptr;

    size_t i = 0;
    while (attrs[i] != nullptr) {
        if (strEq(attrs[i], "name")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addMediaCodecFromAttributes: name is null");
                return -EINVAL;
            }
            name = attrs[i];
        } else if (strEq(attrs[i], "type")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addMediaCodecFromAttributes: type is null");
                return -EINVAL;
            }
            type = attrs[i];
        } else if (strEq(attrs[i], "update")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addMediaCodecFromAttributes: update is null");
                return -EINVAL;
            }
            update = attrs[i];
        } else {
            ALOGE("addMediaCodecFromAttributes: unrecognized attribute: %s", attrs[i]);
            return -EINVAL;
        }
        ++i;
    }

    if (name == nullptr) {
        ALOGE("addMediaCodecFromAttributes: name not found");
        return -EINVAL;
    }

    mUpdate = (update != nullptr) && parseBoolean(update);
    mCurrentCodec = mCodecMap.find(name);
    if (mCurrentCodec == mCodecMap.end()) { // New codec name
        if (mUpdate) {
            ALOGE("addMediaCodecFromAttributes: updating non-existing codec");
            return -EINVAL;
        }
        // Create a new codec in mCodecMap
        mCurrentCodec = mCodecMap.insert(
                Codec(name, CodecProperties())).first;
        if (type != nullptr) {
            mCurrentType = mCurrentCodec->second.typeMap.insert(
                    Type(type, AttributeMap())).first;
        } else {
            mCurrentType = mCurrentCodec->second.typeMap.end();
        }
        mCurrentCodec->second.isEncoder = encoder;
        mCurrentCodec->second.order = mCodecCounter++;
    } else { // Existing codec name
        if (!mUpdate) {
            ALOGE("addMediaCodecFromAttributes: adding existing codec");
            return -EINVAL;
        }
        if (type != nullptr) {
            mCurrentType = mCurrentCodec->second.typeMap.find(type);
            if (mCurrentType == mCurrentCodec->second.typeMap.end()) {
                ALOGE("addMediaCodecFromAttributes: updating non-existing type");
                return -EINVAL;
            }
        } else {
            // This should happen only when the codec has at most one type.
            mCurrentType = mCurrentCodec->second.typeMap.begin();
        }
    }

    return OK;
}

status_t MediaCodecsXmlParser::addQuirk(const char **attrs) {
    const char *name = nullptr;

    size_t i = 0;
    while (attrs[i] != nullptr) {
        if (strEq(attrs[i], "name")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addQuirk: name is null");
                return -EINVAL;
            }
            name = attrs[i];
        } else {
            ALOGE("addQuirk: unrecognized attribute: %s", attrs[i]);
            return -EINVAL;
        }
        ++i;
    }

    if (name == nullptr) {
        ALOGE("addQuirk: name not found");
        return -EINVAL;
    }

    mCurrentCodec->second.quirkSet.emplace(name);
    return OK;
}

status_t MediaCodecsXmlParser::addTypeFromAttributes(const char **attrs, bool encoder) {
    const char *name = nullptr;
    const char *update = nullptr;

    size_t i = 0;
    while (attrs[i] != nullptr) {
        if (strEq(attrs[i], "name")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addTypeFromAttributes: name is null");
                return -EINVAL;
            }
            name = attrs[i];
        } else if (strEq(attrs[i], "update")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addTypeFromAttributes: update is null");
                return -EINVAL;
            }
            update = attrs[i];
        } else {
            ALOGE("addTypeFromAttributes: unrecognized attribute: %s", attrs[i]);
            return -EINVAL;
        }
        ++i;
    }

    if (name == nullptr) {
        return -EINVAL;
    }

    mCurrentCodec->second.isEncoder = encoder;
    mCurrentType = mCurrentCodec->second.typeMap.find(name);
    if (!mUpdate) {
        if (mCurrentType != mCurrentCodec->second.typeMap.end()) {
            ALOGE("addTypeFromAttributes: re-defining existing type without update");
            return -EINVAL;
        }
        mCurrentType = mCurrentCodec->second.typeMap.insert(
                Type(name, AttributeMap())).first;
    } else if (mCurrentType == mCurrentCodec->second.typeMap.end()) {
        ALOGE("addTypeFromAttributes: updating non-existing type");
    }
    return OK;
}

status_t MediaCodecsXmlParser::addLimit(const char **attrs) {
    const char* a_name = nullptr;
    const char* a_default = nullptr;
    const char* a_in = nullptr;
    const char* a_max = nullptr;
    const char* a_min = nullptr;
    const char* a_range = nullptr;
    const char* a_ranges = nullptr;
    const char* a_scale = nullptr;
    const char* a_value = nullptr;

    size_t i = 0;
    while (attrs[i] != nullptr) {
        if (strEq(attrs[i], "name")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addLimit: name is null");
                return -EINVAL;
            }
            a_name = attrs[i];
        } else if (strEq(attrs[i], "default")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addLimit: default is null");
                return -EINVAL;
            }
            a_default = attrs[i];
        } else if (strEq(attrs[i], "in")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addLimit: in is null");
                return -EINVAL;
            }
            a_in = attrs[i];
        } else if (strEq(attrs[i], "max")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addLimit: max is null");
                return -EINVAL;
            }
            a_max = attrs[i];
        } else if (strEq(attrs[i], "min")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addLimit: min is null");
                return -EINVAL;
            }
            a_min = attrs[i];
        } else if (strEq(attrs[i], "range")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addLimit: range is null");
                return -EINVAL;
            }
            a_range = attrs[i];
        } else if (strEq(attrs[i], "ranges")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addLimit: ranges is null");
                return -EINVAL;
            }
            a_ranges = attrs[i];
        } else if (strEq(attrs[i], "scale")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addLimit: scale is null");
                return -EINVAL;
            }
            a_scale = attrs[i];
        } else if (strEq(attrs[i], "value")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addLimit: value is null");
                return -EINVAL;
            }
            a_value = attrs[i];
        } else {
            ALOGE("addLimit: unrecognized limit: %s", attrs[i]);
            return -EINVAL;
        }
        ++i;
    }

    if (a_name == nullptr) {
        ALOGE("limit with no 'name' attribute");
        return -EINVAL;
    }

    // size, blocks, bitrate, frame-rate, blocks-per-second, aspect-ratio,
    // measured-frame-rate, measured-blocks-per-second: range
    // quality: range + default + [scale]
    // complexity: range + default
    if (mCurrentType == mCurrentCodec->second.typeMap.end()) {
        ALOGW("ignoring null type");
        return OK;
    }

    std::string range;
    if (strEq(a_name, "aspect-ratio") ||
            strEq(a_name, "bitrate") ||
            strEq(a_name, "block-count") ||
            strEq(a_name, "blocks-per-second") ||
            strEq(a_name, "complexity") ||
            strEq(a_name, "frame-rate") ||
            strEq(a_name, "quality") ||
            strEq(a_name, "size") ||
            strEq(a_name, "measured-blocks-per-second") ||
            strHasPrefix(a_name, "measured-frame-rate-")) {
        // "range" is specified in exactly one of the following forms:
        // 1) min-max
        // 2) value-value
        // 3) range
        if (a_min != nullptr && a_max != nullptr) {
            // min-max
            if (a_range != nullptr || a_value != nullptr) {
                return limitError(a_name, "has 'min' and 'max' as well as 'range' or "
                        "'value' attributes");
            }
            range = a_min;
            range += '-';
            range += a_max;
        } else if (a_min != nullptr || a_max != nullptr) {
            return limitError(a_name, "has only 'min' or 'max' attribute");
        } else if (a_value != nullptr) {
            // value-value
            if (a_range != nullptr) {
                return limitError(a_name, "has both 'range' and 'value' attributes");
            }
            range = a_value;
            range += '-';
            range += a_value;
        } else if (a_range == nullptr) {
            return limitError(a_name, "with no 'range', 'value' or 'min'/'max' attributes");
        } else {
            // range
            range = a_range;
        }

        // "aspect-ratio" requires some special treatment.
        if (strEq(a_name, "aspect-ratio")) {
            // "aspect-ratio" must have "in".
            if (a_in == nullptr) {
                return limitFoundMissingAttr(a_name, "in", false);
            }
            // "in" must be either "pixels" or "blocks".
            if (!strEq(a_in, "pixels") && !strEq(a_in, "blocks")) {
                return limitInvalidAttr(a_name, "in", a_in);
            }
            // name will be "pixel-aspect-ratio-range" or
            // "block-aspect-ratio-range".
            mCurrentType->second[
                    std::string(a_in).substr(0, strlen(a_in) - 1) +
                    "-aspect-ratio-range"] = range;
        } else {
            // For everything else (apart from "aspect-ratio"), simply append
            // "-range" to the name for the range-type property.
            mCurrentType->second[std::string(a_name) + "-range"] = range;

            // Only "quality" may have "scale".
            if (!strEq(a_name, "quality") && a_scale != nullptr) {
                return limitFoundMissingAttr(a_name, "scale");
            } else if (strEq(a_name, "quality")) {
                // The default value of "quality-scale" is "linear".
                mCurrentType->second["quality-scale"] = a_scale == nullptr ?
                        "linear" : a_scale;
            }

            // "quality" and "complexity" must have "default".
            // Other limits must not have "default".
            if (strEq(a_name, "quality") || strEq(a_name, "complexity")) {
                if (a_default == nullptr) {
                    return limitFoundMissingAttr(a_name, "default", false);
                }
                // name will be "quality-default" or "complexity-default".
                mCurrentType->second[std::string(a_name) + "-default"] = a_default;
            } else if (a_default != nullptr) {
                return limitFoundMissingAttr(a_name, "default", true);
            }
        }
    } else {
        if (a_default != nullptr) {
            return limitFoundMissingAttr(a_name, "default");
        }
        if (a_in != nullptr) {
            return limitFoundMissingAttr(a_name, "in");
        }
        if (a_scale != nullptr) {
            return limitFoundMissingAttr(a_name, "scale");
        }
        if (a_range != nullptr) {
            return limitFoundMissingAttr(a_name, "range");
        }
        if (a_min != nullptr) {
            return limitFoundMissingAttr(a_name, "min");
        }

        if (a_max != nullptr) {
            // "max" must exist if and only if name is "channel-count" or
            // "concurrent-instances".
            // "min" is not ncessary.
            if (strEq(a_name, "channel-count") ||
                    strEq(a_name, "concurrent-instances")) {
                mCurrentType->second[std::string("max-") + a_name] = a_max;
            } else {
                return limitFoundMissingAttr(a_name, "max", false);
            }
        } else if (strEq(a_name, "channel-count") ||
                strEq(a_name, "concurrent-instances")) {
            return limitFoundMissingAttr(a_name, "max");
        }

        if (a_ranges != nullptr) {
            // "ranges" must exist if and only if name is "sample-rate".
            if (strEq(a_name, "sample-rate")) {
                mCurrentType->second["sample-rate-ranges"] = a_ranges;
            } else {
                return limitFoundMissingAttr(a_name, "ranges", false);
            }
        } else if (strEq(a_name, "sample-rate")) {
            return limitFoundMissingAttr(a_name, "ranges");
        }

        if (a_value != nullptr) {
            // "value" must exist if and only if name is "alignment" or
            // "block-size".
            if (strEq(a_name, "alignment") || strEq(a_name, "block-size")) {
                mCurrentType->second[a_name] = a_value;
            } else {
                return limitFoundMissingAttr(a_name, "value", false);
            }
        } else if (strEq(a_name, "alignment") || strEq(a_name, "block-size")) {
            return limitFoundMissingAttr(a_name, "value", false);
        }

    }

    return OK;
}

status_t MediaCodecsXmlParser::addFeature(const char **attrs) {
    size_t i = 0;
    const char *name = nullptr;
    int32_t optional = -1;
    int32_t required = -1;
    const char *value = nullptr;

    while (attrs[i] != nullptr) {
        if (strEq(attrs[i], "name")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addFeature: name is null");
                return -EINVAL;
            }
            name = attrs[i];
        } else if (strEq(attrs[i], "optional")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addFeature: optional is null");
                return -EINVAL;
            }
            optional = parseBoolean(attrs[i]) ? 1 : 0;
        } else if (strEq(attrs[i], "required")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addFeature: required is null");
                return -EINVAL;
            }
            required = parseBoolean(attrs[i]) ? 1 : 0;
        } else if (strEq(attrs[i], "value")) {
            if (attrs[++i] == nullptr) {
                ALOGE("addFeature: value is null");
                return -EINVAL;
            }
            value = attrs[i];
        } else {
            ALOGE("addFeature: unrecognized attribute: %s", attrs[i]);
            return -EINVAL;
        }
        ++i;
    }

    // Every feature must have a name.
    if (name == nullptr) {
        ALOGE("feature with no 'name' attribute");
        return -EINVAL;
    }

    if (mCurrentType == mCurrentCodec->second.typeMap.end()) {
        ALOGW("ignoring null type");
        return OK;
    }

    if ((optional != -1) || (required != -1)) {
        if (optional == required) {
            ALOGE("feature '%s' is both/neither optional and required", name);
            return -EINVAL;
        }
        if ((optional == 1) || (required == 1)) {
            if (value != nullptr) {
                ALOGE("feature '%s' cannot have extra 'value'", name);
                return -EINVAL;
            }
            mCurrentType->second[std::string("feature-") + name] =
                    optional == 1 ? "0" : "1";
            return OK;
        }
    }
    mCurrentType->second[std::string("feature-") + name] = value == nullptr ?
            "0" : value;
    return OK;
}

const MediaCodecsXmlParser::AttributeMap&
        MediaCodecsXmlParser::getServiceAttributeMap() const {
    return mServiceAttributeMap;
}

const MediaCodecsXmlParser::CodecMap&
        MediaCodecsXmlParser::getCodecMap() const {
    return mCodecMap;
}

const MediaCodecsXmlParser::RoleMap&
        MediaCodecsXmlParser::getRoleMap() const {
    if (mRoleMap.empty()) {
        generateRoleMap();
    }
    return mRoleMap;
}

const char* MediaCodecsXmlParser::getCommonPrefix() const {
    if (mCommonPrefix.empty()) {
        generateCommonPrefix();
    }
    return mCommonPrefix.data();
}

status_t MediaCodecsXmlParser::getParsingStatus() const {
    return mParsingStatus;
}

void MediaCodecsXmlParser::generateRoleMap() const {
    for (const auto& codec : mCodecMap) {
        const auto& codecName = codec.first;
        bool isEncoder = codec.second.isEncoder;
        size_t order = codec.second.order;
        const auto& typeMap = codec.second.typeMap;
        for (const auto& type : typeMap) {
            const auto& typeName = type.first;
            const char* roleName = GetComponentRole(isEncoder, typeName.data());
            if (roleName == nullptr) {
                ALOGE("Cannot find the role for %s of type %s",
                        isEncoder ? "an encoder" : "a decoder",
                        typeName.data());
                continue;
            }
            const auto& typeAttributeMap = type.second;

            auto roleIterator = mRoleMap.find(roleName);
            std::multimap<size_t, NodeInfo>* nodeList;
            if (roleIterator == mRoleMap.end()) {
                RoleProperties roleProperties;
                roleProperties.type = typeName;
                roleProperties.isEncoder = isEncoder;
                auto insertResult = mRoleMap.insert(
                        std::make_pair(roleName, roleProperties));
                if (!insertResult.second) {
                    ALOGE("Cannot add role %s", roleName);
                    continue;
                }
                nodeList = &insertResult.first->second.nodeList;
            } else {
                if (roleIterator->second.type != typeName) {
                    ALOGE("Role %s has mismatching types: %s and %s",
                            roleName,
                            roleIterator->second.type.data(),
                            typeName.data());
                    continue;
                }
                if (roleIterator->second.isEncoder != isEncoder) {
                    ALOGE("Role %s cannot be both an encoder and a decoder",
                            roleName);
                    continue;
                }
                nodeList = &roleIterator->second.nodeList;
            }

            NodeInfo nodeInfo;
            nodeInfo.name = codecName;
            nodeInfo.attributeList.reserve(typeAttributeMap.size());
            for (const auto& attribute : typeAttributeMap) {
                nodeInfo.attributeList.push_back(
                        Attribute{attribute.first, attribute.second});
            }
            nodeList->insert(std::make_pair(
                    std::move(order), std::move(nodeInfo)));
        }
    }
}

void MediaCodecsXmlParser::generateCommonPrefix() const {
    if (mCodecMap.empty()) {
        return;
    }
    auto i = mCodecMap.cbegin();
    auto first = i->first.cbegin();
    auto last = i->first.cend();
    for (++i; i != mCodecMap.cend(); ++i) {
        last = std::mismatch(
                first, last, i->first.cbegin(), i->first.cend()).first;
    }
    mCommonPrefix.insert(mCommonPrefix.begin(), first, last);
}

} // namespace android

