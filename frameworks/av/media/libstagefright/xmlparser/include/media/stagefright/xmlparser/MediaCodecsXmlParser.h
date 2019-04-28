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

#ifndef MEDIA_STAGEFRIGHT_XMLPARSER_H_
#define MEDIA_STAGEFRIGHT_XMLPARSER_H_

#include <sys/types.h>
#include <utils/Errors.h>
#include <utils/Vector.h>
#include <utils/StrongPointer.h>

#include <string>
#include <set>
#include <map>
#include <vector>

namespace android {

class MediaCodecsXmlParser {
public:

    // Treblized media codec list will be located in /odm/etc or /vendor/etc.
    static constexpr char const* defaultSearchDirs[] =
            {"/odm/etc", "/vendor/etc", "/etc", nullptr};
    static constexpr char const* defaultMainXmlName =
            "media_codecs.xml";
    static constexpr char const* defaultPerformanceXmlName =
            "media_codecs_performance.xml";
    static constexpr char const* defaultProfilingResultsXmlPath =
            "/data/misc/media/media_codecs_profiling_results.xml";

    MediaCodecsXmlParser(
            const char* const* searchDirs = defaultSearchDirs,
            const char* mainXmlName = defaultMainXmlName,
            const char* performanceXmlName = defaultPerformanceXmlName,
            const char* profilingResultsXmlPath = defaultProfilingResultsXmlPath);
    ~MediaCodecsXmlParser();

    typedef std::pair<std::string, std::string> Attribute;
    typedef std::map<std::string, std::string> AttributeMap;

    typedef std::pair<std::string, AttributeMap> Type;
    typedef std::map<std::string, AttributeMap> TypeMap;

    typedef std::set<std::string> QuirkSet;

    /**
     * Properties of a codec (node)
     */
    struct CodecProperties {
        bool isEncoder;    ///< Whether this codec is an encoder or a decoder
        size_t order;      ///< Order of appearance in the file (starting from 0)
        QuirkSet quirkSet; ///< Set of quirks requested by this codec
        TypeMap typeMap;   ///< Map of types supported by this codec
    };

    typedef std::pair<std::string, CodecProperties> Codec;
    typedef std::map<std::string, CodecProperties> CodecMap;

    /**
     * Properties of a node (for IOmxStore)
     */
    struct NodeInfo {
        std::string name;
        std::vector<Attribute> attributeList;
    };

    /**
     * Properties of a role (for IOmxStore)
     */
    struct RoleProperties {
        std::string type;
        bool isEncoder;
        std::multimap<size_t, NodeInfo> nodeList;
    };

    typedef std::pair<std::string, RoleProperties> Role;
    typedef std::map<std::string, RoleProperties> RoleMap;

    /**
     * Return a map for attributes that are service-specific.
     */
    const AttributeMap& getServiceAttributeMap() const;

    /**
     * Return a map for codecs and their properties.
     */
    const CodecMap& getCodecMap() const;

    /**
     * Return a map for roles and their properties.
     * This map is generated from the CodecMap.
     */
    const RoleMap& getRoleMap() const;

    /**
     * Return a common prefix of all node names.
     *
     * The prefix is not provided in the xml, so it has to be computed by taking
     * the longest common prefix of all node names.
     */
    const char* getCommonPrefix() const;

    status_t getParsingStatus() const;

private:
    enum Section {
        SECTION_TOPLEVEL,
        SECTION_SETTINGS,
        SECTION_DECODERS,
        SECTION_DECODER,
        SECTION_DECODER_TYPE,
        SECTION_ENCODERS,
        SECTION_ENCODER,
        SECTION_ENCODER_TYPE,
        SECTION_INCLUDE,
    };

    status_t mParsingStatus;
    Section mCurrentSection;
    bool mUpdate;
    std::vector<Section> mSectionStack;
    std::string mHrefBase;

    // Service attributes
    AttributeMap mServiceAttributeMap;

    // Codec attributes
    std::string mCurrentName;
    std::set<std::string> mCodecSet;
    Codec mCodecListTemp[2048];
    CodecMap mCodecMap;
    size_t mCodecCounter;
    CodecMap::iterator mCurrentCodec;
    TypeMap::iterator mCurrentType;

    // Role map
    mutable RoleMap mRoleMap;

    // Computed longest common prefix
    mutable std::string mCommonPrefix;

    bool parseTopLevelXMLFile(const char *path, bool ignore_errors = false);

    void parseXMLFile(const char *path);

    static void StartElementHandlerWrapper(
            void *me, const char *name, const char **attrs);

    static void EndElementHandlerWrapper(void *me, const char *name);

    void startElementHandler(const char *name, const char **attrs);
    void endElementHandler(const char *name);

    status_t includeXMLFile(const char **attrs);
    status_t addSettingFromAttributes(const char **attrs);
    status_t addMediaCodecFromAttributes(bool encoder, const char **attrs);
    void addMediaCodec(bool encoder, const char *name,
            const char *type = nullptr);

    status_t addQuirk(const char **attrs);
    status_t addTypeFromAttributes(const char **attrs, bool encoder);
    status_t addLimit(const char **attrs);
    status_t addFeature(const char **attrs);
    void addType(const char *name);

    void generateRoleMap() const;
    void generateCommonPrefix() const;

    MediaCodecsXmlParser(const MediaCodecsXmlParser&) = delete;
    MediaCodecsXmlParser& operator=(const MediaCodecsXmlParser&) = delete;
};

} // namespace android

#endif // MEDIA_STAGEFRIGHT_XMLPARSER_H_

