/*
**
** Copyright 2016, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#include "keymaster_tags.h"

namespace android {
namespace hardware {
namespace keymaster {
namespace V3_0 {

template <typename TagList> struct TagStringifier;

template <typename... Tags> struct TagStringifier<MetaList<Tags...>> {
    template <TagType tag_type, Tag tag>
    static TypedTag<tag_type, tag> chooseString(TypedTag<tag_type, tag> ttag, Tag runtime_tag,
                                                const char** result) {
        if (tag == runtime_tag) {
            *result = Tag2String<tag>::value();
        }
        return ttag;
    }
    static const char* stringify(Tag tag) {
        const char* result = "unknown tag";
        [](Tags&&...) {}(chooseString(Tags(), tag, &result)...);
        return result;
    }
};

const char* stringifyTag(Tag tag) {
    return TagStringifier<all_tags_t>::stringify(tag);
}

}  // namespace V3_0
}  // namespace keymaster
}  // namespace hardware
}  // namespace android
