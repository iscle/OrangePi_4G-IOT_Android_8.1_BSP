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

//#define LOG_NDEBUG 0
#define LOG_TAG "ItemTable"

#include <include/ItemTable.h>
#include <media/MediaDefs.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/Utils.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/foundation/hexdump.h>
#include <utils/Log.h>

namespace android {

namespace heif {

/////////////////////////////////////////////////////////////////////
//
//  struct to keep track of one image item
//

struct ImageItem {
    friend struct ItemReference;
    friend struct ItemProperty;

    ImageItem() : ImageItem(0) {}
    ImageItem(uint32_t _type) : type(_type),
            rows(0), columns(0), width(0), height(0), rotation(0),
            offset(0), size(0), nextTileIndex(0) {}

    bool isGrid() const {
        return type == FOURCC('g', 'r', 'i', 'd');
    }

    status_t getNextTileItemId(uint32_t *nextTileItemId, bool reset) {
        if (reset) {
            nextTileIndex = 0;
        }
        if (nextTileIndex >= dimgRefs.size()) {
            return ERROR_END_OF_STREAM;
        }
        *nextTileItemId = dimgRefs[nextTileIndex++];
        return OK;
    }

    uint32_t type;
    int32_t rows;
    int32_t columns;
    int32_t width;
    int32_t height;
    int32_t rotation;
    off64_t offset;
    size_t size;
    sp<ABuffer> hvcc;
    sp<ABuffer> icc;

    Vector<uint32_t> thumbnails;
    Vector<uint32_t> dimgRefs;
    size_t nextTileIndex;
};


/////////////////////////////////////////////////////////////////////
//
//  ISO boxes
//

struct Box {
protected:
    Box(const sp<DataSource> source, uint32_t type) :
        mDataSource(source), mType(type) {}

    virtual ~Box() {}

    virtual status_t onChunkData(
            uint32_t /*type*/, off64_t /*offset*/, size_t /*size*/) {
        return OK;
    }

    inline uint32_t type() const { return mType; }

    inline sp<DataSource> source() const { return mDataSource; }

    status_t parseChunk(off64_t *offset);

    status_t parseChunks(off64_t offset, size_t size);

private:
    sp<DataSource> mDataSource;
    uint32_t mType;
};

status_t Box::parseChunk(off64_t *offset) {
    if (*offset < 0) {
        ALOGE("b/23540914");
        return ERROR_MALFORMED;
    }
    uint32_t hdr[2];
    if (mDataSource->readAt(*offset, hdr, 8) < 8) {
        return ERROR_IO;
    }
    uint64_t chunk_size = ntohl(hdr[0]);
    int32_t chunk_type = ntohl(hdr[1]);
    off64_t data_offset = *offset + 8;

    if (chunk_size == 1) {
        if (mDataSource->readAt(*offset + 8, &chunk_size, 8) < 8) {
            return ERROR_IO;
        }
        chunk_size = ntoh64(chunk_size);
        data_offset += 8;

        if (chunk_size < 16) {
            // The smallest valid chunk is 16 bytes long in this case.
            return ERROR_MALFORMED;
        }
    } else if (chunk_size == 0) {
        // This shouldn't happen since we should never be top level
        ALOGE("invalid chunk size 0 for non-top level box");
        return ERROR_MALFORMED;
    } else if (chunk_size < 8) {
        // The smallest valid chunk is 8 bytes long.
        ALOGE("invalid chunk size: %lld", (long long)chunk_size);
        return ERROR_MALFORMED;
    }

    char chunk[5];
    MakeFourCCString(chunk_type, chunk);
    ALOGV("chunk: %s @ %lld", chunk, (long long)*offset);

    off64_t chunk_data_size = chunk_size - (data_offset - *offset);
    if (chunk_data_size < 0) {
        ALOGE("b/23540914");
        return ERROR_MALFORMED;
    }

    status_t err = onChunkData(chunk_type, data_offset, chunk_data_size);

    if (err != OK) {
        return err;
    }
    *offset += chunk_size;
    return OK;
}

status_t Box::parseChunks(off64_t offset, size_t size) {
    off64_t stopOffset = offset + size;
    while (offset < stopOffset) {
        status_t err = parseChunk(&offset);
        if (err != OK) {
            return err;
        }
    }
    if (offset != stopOffset) {
        return ERROR_MALFORMED;
    }
    return OK;
}

///////////////////////////////////////////////////////////////////////

struct FullBox : public Box {
protected:
    FullBox(const sp<DataSource> source, uint32_t type) :
        Box(source, type), mVersion(0), mFlags(0) {}

    inline uint8_t version() const { return mVersion; }

    inline uint32_t flags() const { return mFlags; }

    status_t parseFullBoxHeader(off64_t *offset, size_t *size);

private:
    uint8_t mVersion;
    uint32_t mFlags;
};

status_t FullBox::parseFullBoxHeader(off64_t *offset, size_t *size) {
    if (*size < 4) {
        return ERROR_MALFORMED;
    }
    if (!source()->readAt(*offset, &mVersion, 1)) {
        return ERROR_IO;
    }
    if (!source()->getUInt24(*offset + 1, &mFlags)) {
        return ERROR_IO;
    }
    *offset += 4;
    *size -= 4;
    return OK;
}

/////////////////////////////////////////////////////////////////////
//
//  PrimaryImage box
//

struct PitmBox : public FullBox {
    PitmBox(const sp<DataSource> source) :
        FullBox(source, FOURCC('p', 'i', 't', 'm')) {}

    status_t parse(off64_t offset, size_t size, uint32_t *primaryItemId);
};

status_t PitmBox::parse(off64_t offset, size_t size, uint32_t *primaryItemId) {
    status_t err = parseFullBoxHeader(&offset, &size);
    if (err != OK) {
        return err;
    }

    size_t itemIdSize = (version() == 0) ? 2 : 4;
    if (size < itemIdSize) {
        return ERROR_MALFORMED;
    }
    uint32_t itemId;
    if (!source()->getUInt32Var(offset, &itemId, itemIdSize)) {
        return ERROR_IO;
    }

    ALOGV("primary id %d", itemId);
    *primaryItemId = itemId;

    return OK;
}

/////////////////////////////////////////////////////////////////////
//
//  ItemLocation related boxes
//

struct ExtentEntry {
    uint64_t extentIndex;
    uint64_t extentOffset;
    uint64_t extentLength;
};

struct ItemLoc {
    ItemLoc() : ItemLoc(0, 0, 0, 0) {}
    ItemLoc(uint32_t item_id, uint16_t construction_method,
            uint16_t data_reference_index, uint64_t base_offset) :
        itemId(item_id),
        constructionMethod(construction_method),
        dataReferenceIndex(data_reference_index),
        baseOffset(base_offset) {}

    void addExtent(const ExtentEntry& extent) {
        extents.push_back(extent);
    }

    status_t getLoc(off64_t *offset, size_t *size,
            off64_t idatOffset, size_t idatSize) const {
        // TODO: fix extent handling, fix constructionMethod = 2
        CHECK(extents.size() == 1);
        if (constructionMethod == 0) {
            *offset = baseOffset + extents[0].extentOffset;
            *size = extents[0].extentLength;
            return OK;
        } else if (constructionMethod == 1) {
            if (baseOffset + extents[0].extentOffset + extents[0].extentLength
                    > idatSize) {
                return ERROR_MALFORMED;
            }
            *offset = baseOffset + extents[0].extentOffset + idatOffset;
            *size = extents[0].extentLength;
            return OK;
        }
        return ERROR_UNSUPPORTED;
    }

    // parsed info
    uint32_t itemId;
    uint16_t constructionMethod;
    uint16_t dataReferenceIndex;
    off64_t baseOffset;
    Vector<ExtentEntry> extents;
};

struct IlocBox : public FullBox {
    IlocBox(const sp<DataSource> source, KeyedVector<uint32_t, ItemLoc> *itemLocs) :
        FullBox(source, FOURCC('i', 'l', 'o', 'c')),
        mItemLocs(itemLocs), mHasConstructMethod1(false) {}

    status_t parse(off64_t offset, size_t size);

    bool hasConstructMethod1() { return mHasConstructMethod1; }

private:
    static bool isSizeFieldValid(uint32_t offset_size) {
        return offset_size == 0 || offset_size == 4 || offset_size == 8;
    }
    KeyedVector<uint32_t, ItemLoc> *mItemLocs;
    bool mHasConstructMethod1;
};

status_t IlocBox::parse(off64_t offset, size_t size) {
    status_t err = parseFullBoxHeader(&offset, &size);
    if (err != OK) {
        return err;
    }
    if (version() > 2) {
        ALOGE("%s: invalid version %d", __FUNCTION__, version());
        return ERROR_MALFORMED;
    }

    if (size < 2) {
        return ERROR_MALFORMED;
    }
    uint8_t offset_size;
    if (!source()->readAt(offset++, &offset_size, 1)) {
        return ERROR_IO;
    }
    uint8_t length_size = (offset_size & 0xF);
    offset_size >>= 4;

    uint8_t base_offset_size;
    if (!source()->readAt(offset++, &base_offset_size, 1)) {
        return ERROR_IO;
    }
    uint8_t index_size = 0;
    if (version() == 1 || version() == 2) {
        index_size = (base_offset_size & 0xF);
    }
    base_offset_size >>= 4;
    size -= 2;

    if (!isSizeFieldValid(offset_size)
            || !isSizeFieldValid(length_size)
            || !isSizeFieldValid(base_offset_size)
            || !isSizeFieldValid((index_size))) {
        ALOGE("%s: offset size not valid: %d, %d, %d, %d", __FUNCTION__,
                offset_size, length_size, base_offset_size, index_size);
        return ERROR_MALFORMED;
    }

    uint32_t item_count;
    size_t itemFieldSize = version() < 2 ? 2 : 4;
    if (size < itemFieldSize) {
        return ERROR_MALFORMED;
    }
    if (!source()->getUInt32Var(offset, &item_count, itemFieldSize)) {
        return ERROR_IO;
    }

    ALOGV("item_count %lld", (long long) item_count);
    offset += itemFieldSize;
    size -= itemFieldSize;

    for (size_t i = 0; i < item_count; i++) {
        uint32_t item_id;
        if (!source()->getUInt32Var(offset, &item_id, itemFieldSize)) {
            return ERROR_IO;
        }
        ALOGV("item[%zu]: id %lld", i, (long long)item_id);
        offset += itemFieldSize;

        uint8_t construction_method = 0;
        if (version() == 1 || version() == 2) {
            uint8_t buf[2];
            if (!source()->readAt(offset, buf, 2)) {
                return ERROR_IO;
            }
            construction_method = (buf[1] & 0xF);
            ALOGV("construction_method %d", construction_method);
            if (construction_method == 1) {
                mHasConstructMethod1 = true;
            }

            offset += 2;
        }

        uint16_t data_reference_index;
        if (!source()->getUInt16(offset, &data_reference_index)) {
            return ERROR_IO;
        }
        ALOGV("data_reference_index %d", data_reference_index);
        if (data_reference_index != 0) {
            // we don't support reference to other files
            return ERROR_UNSUPPORTED;
        }
        offset += 2;

        uint64_t base_offset = 0;
        if (base_offset_size != 0) {
            if (!source()->getUInt64Var(offset, &base_offset, base_offset_size)) {
                return ERROR_IO;
            }
            offset += base_offset_size;
        }
        ALOGV("base_offset %lld", (long long) base_offset);

        ssize_t index = mItemLocs->add(item_id, ItemLoc(
                item_id, construction_method, data_reference_index, base_offset));
        ItemLoc &item = mItemLocs->editValueAt(index);

        uint16_t extent_count;
        if (!source()->getUInt16(offset, &extent_count)) {
            return ERROR_IO;
        }
        ALOGV("extent_count %d", extent_count);

        if (extent_count > 1 && (offset_size == 0 || length_size == 0)) {
            // if the item is dividec into more than one extents, offset and
            // length must be present.
            return ERROR_MALFORMED;
        }
        offset += 2;

        for (size_t j = 0; j < extent_count; j++) {
            uint64_t extent_index = 1; // default=1
            if ((version() == 1 || version() == 2) && (index_size > 0)) {
                if (!source()->getUInt64Var(offset, &extent_index, index_size)) {
                    return ERROR_IO;
                }
                // TODO: add support for this mode
                offset += index_size;
                ALOGV("extent_index %lld", (long long)extent_index);
            }

            uint64_t extent_offset = 0; // default=0
            if (offset_size > 0) {
                if (!source()->getUInt64Var(offset, &extent_offset, offset_size)) {
                    return ERROR_IO;
                }
                offset += offset_size;
            }
            ALOGV("extent_offset %lld", (long long)extent_offset);

            uint64_t extent_length = 0; // this indicates full length of file
            if (length_size > 0) {
                if (!source()->getUInt64Var(offset, &extent_length, length_size)) {
                    return ERROR_IO;
                }
                offset += length_size;
            }
            ALOGV("extent_length %lld", (long long)extent_length);

            item.addExtent({ extent_index, extent_offset, extent_length });
        }
    }
    return OK;
}

/////////////////////////////////////////////////////////////////////
//
//  ItemReference related boxes
//

struct ItemReference : public Box, public RefBase {
    ItemReference(const sp<DataSource> source, uint32_t type, uint32_t itemIdSize) :
        Box(source, type), mItemId(0), mRefIdSize(itemIdSize) {}

    status_t parse(off64_t offset, size_t size);

    uint32_t itemId() { return mItemId; }

    void apply(KeyedVector<uint32_t, ImageItem> &itemIdToImageMap) const {
        ssize_t imageIndex = itemIdToImageMap.indexOfKey(mItemId);

        // ignore non-image items
        if (imageIndex < 0) {
            return;
        }

        ALOGV("attach reference type 0x%x to item id %d)", type(), mItemId);

        if (type() == FOURCC('d', 'i', 'm', 'g')) {
            ImageItem &image = itemIdToImageMap.editValueAt(imageIndex);
            if (!image.dimgRefs.empty()) {
                ALOGW("dimgRefs if not clean!");
            }
            image.dimgRefs.appendVector(mRefs);
        } else if (type() == FOURCC('t', 'h', 'm', 'b')) {
            for (size_t i = 0; i < mRefs.size(); i++) {
                imageIndex = itemIdToImageMap.indexOfKey(mRefs[i]);

                // ignore non-image items
                if (imageIndex < 0) {
                    continue;
                }
                ALOGV("Image item id %d uses thumbnail item id %d", mRefs[i], mItemId);
                ImageItem &image = itemIdToImageMap.editValueAt(imageIndex);
                if (!image.thumbnails.empty()) {
                    ALOGW("already has thumbnails!");
                }
                image.thumbnails.push_back(mItemId);
            }
        } else {
            ALOGW("ignoring unsupported ref type 0x%x", type());
        }
    }

private:
    uint32_t mItemId;
    uint32_t mRefIdSize;
    Vector<uint32_t> mRefs;

    DISALLOW_EVIL_CONSTRUCTORS(ItemReference);
};

status_t ItemReference::parse(off64_t offset, size_t size) {
    if (size < mRefIdSize + 2) {
        return ERROR_MALFORMED;
    }
    if (!source()->getUInt32Var(offset, &mItemId, mRefIdSize)) {
        return ERROR_IO;
    }
    offset += mRefIdSize;

    uint16_t count;
    if (!source()->getUInt16(offset, &count)) {
        return ERROR_IO;
    }
    offset += 2;
    size -= (mRefIdSize + 2);

    if (size < count * mRefIdSize) {
        return ERROR_MALFORMED;
    }

    for (size_t i = 0; i < count; i++) {
        uint32_t refItemId;
        if (!source()->getUInt32Var(offset, &refItemId, mRefIdSize)) {
            return ERROR_IO;
        }
        offset += mRefIdSize;
        mRefs.push_back(refItemId);
        ALOGV("item id %d: referencing item id %d", mItemId, refItemId);
    }

    return OK;
}

struct IrefBox : public FullBox {
    IrefBox(const sp<DataSource> source, Vector<sp<ItemReference> > *itemRefs) :
        FullBox(source, FOURCC('i', 'r', 'e', 'f')), mRefIdSize(0), mItemRefs(itemRefs) {}

    status_t parse(off64_t offset, size_t size);

protected:
    status_t onChunkData(uint32_t type, off64_t offset, size_t size) override;

private:
    uint32_t mRefIdSize;
    Vector<sp<ItemReference> > *mItemRefs;
};

status_t IrefBox::parse(off64_t offset, size_t size) {
    ALOGV("%s: offset %lld, size %zu", __FUNCTION__, (long long)offset, size);
    status_t err = parseFullBoxHeader(&offset, &size);
    if (err != OK) {
        return err;
    }

    mRefIdSize = (version() == 0) ? 2 : 4;
    return parseChunks(offset, size);
}

status_t IrefBox::onChunkData(uint32_t type, off64_t offset, size_t size) {
    sp<ItemReference> itemRef = new ItemReference(source(), type, mRefIdSize);

    status_t err = itemRef->parse(offset, size);
    if (err != OK) {
        return err;
    }
    mItemRefs->push_back(itemRef);
    return OK;
}

/////////////////////////////////////////////////////////////////////
//
//  ItemProperty related boxes
//

struct AssociationEntry {
    uint32_t itemId;
    bool essential;
    uint16_t index;
};

struct ItemProperty : public RefBase {
    ItemProperty() {}

    virtual void attachTo(ImageItem &/*image*/) const {
        ALOGW("Unrecognized property");
    }
    virtual status_t parse(off64_t /*offset*/, size_t /*size*/) {
        ALOGW("Unrecognized property");
        return OK;
    }

private:
    DISALLOW_EVIL_CONSTRUCTORS(ItemProperty);
};

struct IspeBox : public FullBox, public ItemProperty {
    IspeBox(const sp<DataSource> source) :
        FullBox(source, FOURCC('i', 's', 'p', 'e')), mWidth(0), mHeight(0) {}

    status_t parse(off64_t offset, size_t size) override;

    void attachTo(ImageItem &image) const override {
        image.width = mWidth;
        image.height = mHeight;
    }

private:
    uint32_t mWidth;
    uint32_t mHeight;
};

status_t IspeBox::parse(off64_t offset, size_t size) {
    ALOGV("%s: offset %lld, size %zu", __FUNCTION__, (long long)offset, size);

    status_t err = parseFullBoxHeader(&offset, &size);
    if (err != OK) {
        return err;
    }

    if (size < 8) {
        return ERROR_MALFORMED;
    }
    if (!source()->getUInt32(offset, &mWidth)
            || !source()->getUInt32(offset + 4, &mHeight)) {
        return ERROR_IO;
    }
    ALOGV("property ispe: %dx%d", mWidth, mHeight);

    return OK;
}

struct HvccBox : public Box, public ItemProperty {
    HvccBox(const sp<DataSource> source) :
        Box(source, FOURCC('h', 'v', 'c', 'C')) {}

    status_t parse(off64_t offset, size_t size) override;

    void attachTo(ImageItem &image) const override {
        image.hvcc = mHVCC;
    }

private:
    sp<ABuffer> mHVCC;
};

status_t HvccBox::parse(off64_t offset, size_t size) {
    ALOGV("%s: offset %lld, size %zu", __FUNCTION__, (long long)offset, size);

    mHVCC = new ABuffer(size);

    if (mHVCC->data() == NULL) {
        ALOGE("b/28471206");
        return NO_MEMORY;
    }

    if (source()->readAt(offset, mHVCC->data(), size) < (ssize_t)size) {
        return ERROR_IO;
    }

    ALOGV("property hvcC");

    return OK;
}

struct IrotBox : public Box, public ItemProperty {
    IrotBox(const sp<DataSource> source) :
        Box(source, FOURCC('i', 'r', 'o', 't')), mAngle(0) {}

    status_t parse(off64_t offset, size_t size) override;

    void attachTo(ImageItem &image) const override {
        image.rotation = mAngle * 90;
    }

private:
    uint8_t mAngle;
};

status_t IrotBox::parse(off64_t offset, size_t size) {
    ALOGV("%s: offset %lld, size %zu", __FUNCTION__, (long long)offset, size);

    if (size < 1) {
        return ERROR_MALFORMED;
    }
    if (source()->readAt(offset, &mAngle, 1) != 1) {
        return ERROR_IO;
    }
    mAngle &= 0x3;
    ALOGV("property irot: %d", mAngle);

    return OK;
}

struct ColrBox : public Box, public ItemProperty {
    ColrBox(const sp<DataSource> source) :
        Box(source, FOURCC('c', 'o', 'l', 'r')) {}

    status_t parse(off64_t offset, size_t size) override;

    void attachTo(ImageItem &image) const override {
        image.icc = mICCData;
    }

private:
    sp<ABuffer> mICCData;
};

status_t ColrBox::parse(off64_t offset, size_t size) {
    ALOGV("%s: offset %lld, size %zu", __FUNCTION__, (long long)offset, size);

    if (size < 4) {
        return ERROR_MALFORMED;
    }
    uint32_t colour_type;
    if (!source()->getUInt32(offset, &colour_type)) {
        return ERROR_IO;
    }
    offset += 4;
    size -= 4;
    if (colour_type == FOURCC('n', 'c', 'l', 'x')) {
        return OK;
    }
    if ((colour_type != FOURCC('r', 'I', 'C', 'C')) &&
        (colour_type != FOURCC('p', 'r', 'o', 'f'))) {
        return ERROR_MALFORMED;
    }

    mICCData = new ABuffer(size);
    if (mICCData->data() == NULL) {
        ALOGE("b/28471206");
        return NO_MEMORY;
    }

    if (source()->readAt(offset, mICCData->data(), size) != (ssize_t)size) {
        return ERROR_IO;
    }

    ALOGV("property Colr: size %zd", size);
    return OK;
}

struct IpmaBox : public FullBox {
    IpmaBox(const sp<DataSource> source, Vector<AssociationEntry> *associations) :
        FullBox(source, FOURCC('i', 'p', 'm', 'a')), mAssociations(associations) {}

    status_t parse(off64_t offset, size_t size);
private:
    Vector<AssociationEntry> *mAssociations;
};

status_t IpmaBox::parse(off64_t offset, size_t size) {
    status_t err = parseFullBoxHeader(&offset, &size);
    if (err != OK) {
        return err;
    }

    if (size < 4) {
        return ERROR_MALFORMED;
    }
    uint32_t entryCount;
    if (!source()->getUInt32(offset, &entryCount)) {
        return ERROR_IO;
    }
    offset += 4;
    size -= 4;

    for (size_t k = 0; k < entryCount; ++k) {
        uint32_t itemId = 0;
        size_t itemIdSize = (version() < 1) ? 2 : 4;

        if (size < itemIdSize + 1) {
            return ERROR_MALFORMED;
        }

        if (!source()->getUInt32Var(offset, &itemId, itemIdSize)) {
            return ERROR_IO;
        }
        offset += itemIdSize;
        size -= itemIdSize;

        uint8_t associationCount;
        if (!source()->readAt(offset, &associationCount, 1)) {
            return ERROR_IO;
        }
        offset++;
        size--;

        for (size_t i = 0; i < associationCount; ++i) {
            size_t propIndexSize = (flags() & 1) ? 2 : 1;
            if (size < propIndexSize) {
                return ERROR_MALFORMED;
            }
            uint16_t propIndex;
            if (!source()->getUInt16Var(offset, &propIndex, propIndexSize)) {
                return ERROR_IO;
            }
            offset += propIndexSize;
            size -= propIndexSize;
            uint16_t bitmask = (1 << (8 * propIndexSize - 1));
            AssociationEntry entry = {
                    .itemId = itemId,
                    .essential = !!(propIndex & bitmask),
                    .index = (uint16_t) (propIndex & ~bitmask)
            };

            ALOGV("item id %d associated to property %d (essential %d)",
                    itemId, entry.index, entry.essential);

            mAssociations->push_back(entry);
        }
    }

    return OK;
}

struct IpcoBox : public Box {
    IpcoBox(const sp<DataSource> source, Vector<sp<ItemProperty> > *properties) :
        Box(source, FOURCC('i', 'p', 'c', 'o')), mItemProperties(properties) {}

    status_t parse(off64_t offset, size_t size);
protected:
    status_t onChunkData(uint32_t type, off64_t offset, size_t size) override;

private:
    Vector<sp<ItemProperty> > *mItemProperties;
};

status_t IpcoBox::parse(off64_t offset, size_t size) {
    ALOGV("%s: offset %lld, size %zu", __FUNCTION__, (long long)offset, size);
    // push dummy as the index is 1-based
    mItemProperties->push_back(new ItemProperty());
    return parseChunks(offset, size);
}

status_t IpcoBox::onChunkData(uint32_t type, off64_t offset, size_t size) {
    sp<ItemProperty> itemProperty;
    switch(type) {
        case FOURCC('h', 'v', 'c', 'C'):
        {
            itemProperty = new HvccBox(source());
            break;
        }
        case FOURCC('i', 's', 'p', 'e'):
        {
            itemProperty = new IspeBox(source());
            break;
        }
        case FOURCC('i', 'r', 'o', 't'):
        {
            itemProperty = new IrotBox(source());
            break;
        }
        case FOURCC('c', 'o', 'l', 'r'):
        {
            itemProperty = new ColrBox(source());
            break;
        }
        default:
        {
            // push dummy to maintain correct item property index
            itemProperty = new ItemProperty();
            break;
        }
    }
    status_t err = itemProperty->parse(offset, size);
    if (err != OK) {
        return err;
    }
    mItemProperties->push_back(itemProperty);
    return OK;
}

struct IprpBox : public Box {
    IprpBox(const sp<DataSource> source,
            Vector<sp<ItemProperty> > *properties,
            Vector<AssociationEntry> *associations) :
        Box(source, FOURCC('i', 'p', 'r', 'p')),
        mProperties(properties), mAssociations(associations) {}

    status_t parse(off64_t offset, size_t size);
protected:
    status_t onChunkData(uint32_t type, off64_t offset, size_t size) override;

private:
    Vector<sp<ItemProperty> > *mProperties;
    Vector<AssociationEntry> *mAssociations;
};

status_t IprpBox::parse(off64_t offset, size_t size) {
    ALOGV("%s: offset %lld, size %zu", __FUNCTION__, (long long)offset, size);

    status_t err = parseChunks(offset, size);
    if (err != OK) {
        return err;
    }
    return OK;
}

status_t IprpBox::onChunkData(uint32_t type, off64_t offset, size_t size) {
    switch(type) {
        case FOURCC('i', 'p', 'c', 'o'):
        {
            IpcoBox ipcoBox(source(), mProperties);
            return ipcoBox.parse(offset, size);
        }
        case FOURCC('i', 'p', 'm', 'a'):
        {
            IpmaBox ipmaBox(source(), mAssociations);
            return ipmaBox.parse(offset, size);
        }
        default:
        {
            ALOGW("Unrecognized box.");
            break;
        }
    }
    return OK;
}

/////////////////////////////////////////////////////////////////////
//
//  ItemInfo related boxes
//
struct ItemInfo {
    uint32_t itemId;
    uint32_t itemType;
};

struct InfeBox : public FullBox {
    InfeBox(const sp<DataSource> source) :
        FullBox(source, FOURCC('i', 'n', 'f', 'e')) {}

    status_t parse(off64_t offset, size_t size, ItemInfo *itemInfo);

private:
    bool parseNullTerminatedString(off64_t *offset, size_t *size, String8 *out);
};

bool InfeBox::parseNullTerminatedString(
        off64_t *offset, size_t *size, String8 *out) {
    char tmp[256];
    size_t len = 0;
    off64_t newOffset = *offset;
    off64_t stopOffset = *offset + *size;
    while (newOffset < stopOffset) {
        if (!source()->readAt(newOffset++, &tmp[len], 1)) {
            return false;
        }
        if (tmp[len] == 0) {
            out->append(tmp, len);

            *offset = newOffset;
            *size = stopOffset - newOffset;

            return true;
        }
        if (++len >= sizeof(tmp)) {
            out->append(tmp, len);
            len = 0;
        }
    }
    return false;
}

status_t InfeBox::parse(off64_t offset, size_t size, ItemInfo *itemInfo) {
    status_t err = parseFullBoxHeader(&offset, &size);
    if (err != OK) {
        return err;
    }

    if (version() == 0 || version() == 1) {
        if (size < 4) {
            return ERROR_MALFORMED;
        }
        uint16_t item_id;
        if (!source()->getUInt16(offset, &item_id)) {
            return ERROR_IO;
        }
        ALOGV("item_id %d", item_id);
        uint16_t item_protection_index;
        if (!source()->getUInt16(offset + 2, &item_protection_index)) {
            return ERROR_IO;
        }
        offset += 4;
        size -= 4;

        String8 item_name;
        if (!parseNullTerminatedString(&offset, &size, &item_name)) {
            return ERROR_MALFORMED;
        }

        String8 content_type;
        if (!parseNullTerminatedString(&offset, &size, &content_type)) {
            return ERROR_MALFORMED;
        }

        String8 content_encoding;
        if (!parseNullTerminatedString(&offset, &size, &content_encoding)) {
            return ERROR_MALFORMED;
        }

        if (version() == 1) {
            uint32_t extension_type;
            if (!source()->getUInt32(offset, &extension_type)) {
                return ERROR_IO;
            }
            offset++;
            size--;
            // TODO: handle this case
        }
    } else { // version >= 2
        uint32_t item_id;
        size_t itemIdSize = (version() == 2) ? 2 : 4;
        if (size < itemIdSize + 6) {
            return ERROR_MALFORMED;
        }
        if (!source()->getUInt32Var(offset, &item_id, itemIdSize)) {
            return ERROR_IO;
        }
        ALOGV("item_id %d", item_id);
        offset += itemIdSize;
        uint16_t item_protection_index;
        if (!source()->getUInt16(offset, &item_protection_index)) {
            return ERROR_IO;
        }
        ALOGV("item_protection_index %d", item_protection_index);
        offset += 2;
        uint32_t item_type;
        if (!source()->getUInt32(offset, &item_type)) {
            return ERROR_IO;
        }

        itemInfo->itemId = item_id;
        itemInfo->itemType = item_type;

        char itemTypeString[5];
        MakeFourCCString(item_type, itemTypeString);
        ALOGV("item_type %s", itemTypeString);
        offset += 4;
        size -= itemIdSize + 6;

        String8 item_name;
        if (!parseNullTerminatedString(&offset, &size, &item_name)) {
            return ERROR_MALFORMED;
        }
        ALOGV("item_name %s", item_name.c_str());

        if (item_type == FOURCC('m', 'i', 'm', 'e')) {
            String8 content_type;
            if (!parseNullTerminatedString(&offset, &size, &content_type)) {
                return ERROR_MALFORMED;
            }

            String8 content_encoding;
            if (!parseNullTerminatedString(&offset, &size, &content_encoding)) {
                return ERROR_MALFORMED;
            }
        } else if (item_type == FOURCC('u', 'r', 'i', ' ')) {
            String8 item_uri_type;
            if (!parseNullTerminatedString(&offset, &size, &item_uri_type)) {
                return ERROR_MALFORMED;
            }
        }
    }
    return OK;
}

struct IinfBox : public FullBox {
    IinfBox(const sp<DataSource> source, Vector<ItemInfo> *itemInfos) :
        FullBox(source, FOURCC('i', 'i', 'n', 'f')),
        mItemInfos(itemInfos), mHasGrids(false) {}

    status_t parse(off64_t offset, size_t size);

    bool hasGrids() { return mHasGrids; }

protected:
    status_t onChunkData(uint32_t type, off64_t offset, size_t size) override;

private:
    Vector<ItemInfo> *mItemInfos;
    bool mHasGrids;
};

status_t IinfBox::parse(off64_t offset, size_t size) {
    ALOGV("%s: offset %lld, size %zu", __FUNCTION__, (long long)offset, size);

    status_t err = parseFullBoxHeader(&offset, &size);
    if (err != OK) {
        return err;
    }

    size_t entryCountSize = version() == 0 ? 2 : 4;
    if (size < entryCountSize) {
        return ERROR_MALFORMED;
    }
    uint32_t entry_count;
    if (!source()->getUInt32Var(offset, &entry_count, entryCountSize)) {
        return ERROR_IO;
    }
    ALOGV("entry_count %d", entry_count);

    off64_t stopOffset = offset + size;
    offset += entryCountSize;
    for (size_t i = 0; i < entry_count && offset < stopOffset; i++) {
        ALOGV("entry %zu", i);
        status_t err = parseChunk(&offset);
        if (err != OK) {
            return err;
        }
    }
    if (offset != stopOffset) {
        return ERROR_MALFORMED;
    }

    return OK;
}

status_t IinfBox::onChunkData(uint32_t type, off64_t offset, size_t size) {
    if (type != FOURCC('i', 'n', 'f', 'e')) {
        return OK;
    }

    InfeBox infeBox(source());
    ItemInfo itemInfo;
    status_t err = infeBox.parse(offset, size, &itemInfo);
    if (err != OK) {
        return err;
    }
    mItemInfos->push_back(itemInfo);
    mHasGrids |= (itemInfo.itemType == FOURCC('g', 'r', 'i', 'd'));
    return OK;
}

//////////////////////////////////////////////////////////////////

ItemTable::ItemTable(const sp<DataSource> &source)
    : mDataSource(source),
      mPrimaryItemId(0),
      mIdatOffset(0),
      mIdatSize(0),
      mImageItemsValid(false),
      mCurrentImageIndex(0) {
    mRequiredBoxes.insert('iprp');
    mRequiredBoxes.insert('iloc');
    mRequiredBoxes.insert('pitm');
    mRequiredBoxes.insert('iinf');
}

ItemTable::~ItemTable() {}

status_t ItemTable::parse(uint32_t type, off64_t data_offset, size_t chunk_data_size) {
    switch(type) {
        case FOURCC('i', 'l', 'o', 'c'):
        {
            return parseIlocBox(data_offset, chunk_data_size);
        }
        case FOURCC('i', 'i', 'n', 'f'):
        {
            return parseIinfBox(data_offset, chunk_data_size);
        }
        case FOURCC('i', 'p', 'r', 'p'):
        {
            return parseIprpBox(data_offset, chunk_data_size);
        }
        case FOURCC('p', 'i', 't', 'm'):
        {
            return parsePitmBox(data_offset, chunk_data_size);
        }
        case FOURCC('i', 'd', 'a', 't'):
        {
            return parseIdatBox(data_offset, chunk_data_size);
        }
        case FOURCC('i', 'r', 'e', 'f'):
        {
            return parseIrefBox(data_offset, chunk_data_size);
        }
        case FOURCC('i', 'p', 'r', 'o'):
        {
            ALOGW("ipro box not supported!");
            break;
        }
        default:
        {
            ALOGW("unrecognized box type: 0x%x", type);
            break;
        }
    }
    return ERROR_UNSUPPORTED;
}

status_t ItemTable::parseIlocBox(off64_t offset, size_t size) {
    ALOGV("%s: offset %lld, size %zu", __FUNCTION__, (long long)offset, size);

    IlocBox ilocBox(mDataSource, &mItemLocs);
    status_t err = ilocBox.parse(offset, size);
    if (err != OK) {
        return err;
    }

    if (ilocBox.hasConstructMethod1()) {
        mRequiredBoxes.insert('idat');
    }

    return buildImageItemsIfPossible('iloc');
}

status_t ItemTable::parseIinfBox(off64_t offset, size_t size) {
    ALOGV("%s: offset %lld, size %zu", __FUNCTION__, (long long)offset, size);

    IinfBox iinfBox(mDataSource, &mItemInfos);
    status_t err = iinfBox.parse(offset, size);
    if (err != OK) {
        return err;
    }

    if (iinfBox.hasGrids()) {
        mRequiredBoxes.insert('iref');
    }

    return buildImageItemsIfPossible('iinf');
}

status_t ItemTable::parsePitmBox(off64_t offset, size_t size) {
    ALOGV("%s: offset %lld, size %zu", __FUNCTION__, (long long)offset, size);

    PitmBox pitmBox(mDataSource);
    status_t err = pitmBox.parse(offset, size, &mPrimaryItemId);
    if (err != OK) {
        return err;
    }

    return buildImageItemsIfPossible('pitm');
}

status_t ItemTable::parseIprpBox(off64_t offset, size_t size) {
    ALOGV("%s: offset %lld, size %zu", __FUNCTION__, (long long)offset, size);

    IprpBox iprpBox(mDataSource, &mItemProperties, &mAssociations);
    status_t err = iprpBox.parse(offset, size);
    if (err != OK) {
        return err;
    }

    return buildImageItemsIfPossible('iprp');
}

status_t ItemTable::parseIdatBox(off64_t offset, size_t size) {
    ALOGV("%s: idat offset %lld, size %zu", __FUNCTION__, (long long)offset, size);

    // only remember the offset and size of idat box for later use
    mIdatOffset = offset;
    mIdatSize = size;

    return buildImageItemsIfPossible('idat');
}

status_t ItemTable::parseIrefBox(off64_t offset, size_t size) {
    ALOGV("%s: offset %lld, size %zu", __FUNCTION__, (long long)offset, size);

    IrefBox irefBox(mDataSource, &mItemReferences);
    status_t err = irefBox.parse(offset, size);
    if (err != OK) {
        return err;
    }

    return buildImageItemsIfPossible('iref');
}

status_t ItemTable::buildImageItemsIfPossible(uint32_t type) {
    if (mImageItemsValid) {
        return OK;
    }

    mBoxesSeen.insert(type);

    // need at least 'iprp', 'iloc', 'pitm', 'iinf';
    // need 'idat' if any items used construction_method of 2;
    // need 'iref' if there are grids.
    if (!std::includes(
            mBoxesSeen.begin(), mBoxesSeen.end(),
            mRequiredBoxes.begin(), mRequiredBoxes.end())) {
        return OK;
    }

    ALOGV("building image table...");

    for (size_t i = 0; i < mItemInfos.size(); i++) {
        const ItemInfo &info = mItemInfos[i];


        // ignore non-image items
        if (info.itemType != FOURCC('g', 'r', 'i', 'd') &&
            info.itemType != FOURCC('h', 'v', 'c', '1')) {
            continue;
        }

        ssize_t imageIndex = mItemIdToImageMap.indexOfKey(info.itemId);
        if (imageIndex >= 0) {
            ALOGW("ignoring duplicate image item id %d", info.itemId);
            continue;
        }

        ssize_t ilocIndex = mItemLocs.indexOfKey(info.itemId);
        if (ilocIndex < 0) {
            ALOGE("iloc missing for image item id %d", info.itemId);
            continue;
        }
        const ItemLoc &iloc = mItemLocs[ilocIndex];

        off64_t offset;
        size_t size;
        if (iloc.getLoc(&offset, &size, mIdatOffset, mIdatSize) != OK) {
            return ERROR_MALFORMED;
        }

        ImageItem image(info.itemType);

        ALOGV("adding %s: itemId %d", image.isGrid() ? "grid" : "image", info.itemId);

        if (image.isGrid()) {
            if (size > 12) {
                return ERROR_MALFORMED;
            }
            uint8_t buf[12];
            if (!mDataSource->readAt(offset, buf, size)) {
                return ERROR_IO;
            }

            image.rows = buf[2] + 1;
            image.columns = buf[3] + 1;

            ALOGV("rows %d, columans %d", image.rows, image.columns);
        } else {
            image.offset = offset;
            image.size = size;
        }
        mItemIdToImageMap.add(info.itemId, image);
    }

    for (size_t i = 0; i < mAssociations.size(); i++) {
        attachProperty(mAssociations[i]);
    }

    for (size_t i = 0; i < mItemReferences.size(); i++) {
        mItemReferences[i]->apply(mItemIdToImageMap);
    }

    mImageItemsValid = true;
    return OK;
}

void ItemTable::attachProperty(const AssociationEntry &association) {
    ssize_t imageIndex = mItemIdToImageMap.indexOfKey(association.itemId);

    // ignore non-image items
    if (imageIndex < 0) {
        return;
    }

    uint16_t propertyIndex = association.index;
    if (propertyIndex >= mItemProperties.size()) {
        ALOGW("Ignoring invalid property index %d", propertyIndex);
        return;
    }

    ALOGV("attach property %d to item id %d)",
            propertyIndex, association.itemId);

    mItemProperties[propertyIndex]->attachTo(
            mItemIdToImageMap.editValueAt(imageIndex));
}

sp<MetaData> ItemTable::getImageMeta() {
    if (!mImageItemsValid) {
        return NULL;
    }

    ssize_t imageIndex = mItemIdToImageMap.indexOfKey(mPrimaryItemId);
    if (imageIndex < 0) {
        ALOGE("Primary item id %d not found!", mPrimaryItemId);
        return NULL;
    }

    ALOGV("primary image index %zu", imageIndex);

    const ImageItem *image = &mItemIdToImageMap[imageIndex];

    sp<MetaData> meta = new MetaData;
    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_HEVC);

    ALOGV("setting image size %dx%d", image->width, image->height);
    meta->setInt32(kKeyWidth, image->width);
    meta->setInt32(kKeyHeight, image->height);
    if (image->rotation != 0) {
        // Rotation angle in HEIF is CCW, convert to CW here to be
        // consistent with the other media formats.
        switch(image->rotation) {
            case 90: meta->setInt32(kKeyRotation, 270); break;
            case 180: meta->setInt32(kKeyRotation, 180); break;
            case 270: meta->setInt32(kKeyRotation, 90); break;
            default: break; // don't set if invalid
        }
    }
    meta->setInt32(kKeyMaxInputSize, image->width * image->height * 1.5);

    if (!image->thumbnails.empty()) {
        ssize_t thumbnailIndex = mItemIdToImageMap.indexOfKey(image->thumbnails[0]);
        if (thumbnailIndex >= 0) {
            const ImageItem &thumbnail = mItemIdToImageMap[thumbnailIndex];

            meta->setInt32(kKeyThumbnailWidth, thumbnail.width);
            meta->setInt32(kKeyThumbnailHeight, thumbnail.height);
            meta->setData(kKeyThumbnailHVCC, kTypeHVCC,
                    thumbnail.hvcc->data(), thumbnail.hvcc->size());
            ALOGV("thumbnail meta: %dx%d, index %zd",
                    thumbnail.width, thumbnail.height, thumbnailIndex);
        } else {
            ALOGW("Referenced thumbnail does not exist!");
        }
    }

    if (image->isGrid()) {
        ssize_t tileIndex = mItemIdToImageMap.indexOfKey(image->dimgRefs[0]);
        if (tileIndex < 0) {
            return NULL;
        }
        // when there are tiles, (kKeyWidth, kKeyHeight) is the full tiled area,
        // and (kKeyDisplayWidth, kKeyDisplayHeight) may be smaller than that.
        meta->setInt32(kKeyDisplayWidth, image->width);
        meta->setInt32(kKeyDisplayHeight, image->height);
        int32_t gridRows = image->rows, gridCols = image->columns;

        // point image to the first tile for grid size and HVCC
        image = &mItemIdToImageMap.editValueAt(tileIndex);
        meta->setInt32(kKeyWidth, image->width * gridCols);
        meta->setInt32(kKeyHeight, image->height * gridRows);
        meta->setInt32(kKeyGridWidth, image->width);
        meta->setInt32(kKeyGridHeight, image->height);
        meta->setInt32(kKeyMaxInputSize, image->width * image->height * 1.5);
    }

    if (image->hvcc == NULL) {
        ALOGE("hvcc is missing!");
        return NULL;
    }
    meta->setData(kKeyHVCC, kTypeHVCC, image->hvcc->data(), image->hvcc->size());

    if (image->icc != NULL) {
        meta->setData(kKeyIccProfile, 0, image->icc->data(), image->icc->size());
    }
    return meta;
}

uint32_t ItemTable::countImages() const {
    return mImageItemsValid ? mItemIdToImageMap.size() : 0;
}

status_t ItemTable::findPrimaryImage(uint32_t *imageIndex) {
    if (!mImageItemsValid) {
        return INVALID_OPERATION;
    }

    ssize_t index = mItemIdToImageMap.indexOfKey(mPrimaryItemId);
    if (index < 0) {
        return ERROR_MALFORMED;
    }

    *imageIndex = index;
    return OK;
}

status_t ItemTable::findThumbnail(uint32_t *imageIndex) {
    if (!mImageItemsValid) {
        return INVALID_OPERATION;
    }

    ssize_t primaryIndex = mItemIdToImageMap.indexOfKey(mPrimaryItemId);
    if (primaryIndex < 0) {
        ALOGE("Primary item id %d not found!", mPrimaryItemId);
        return ERROR_MALFORMED;
    }

    const ImageItem &primaryImage = mItemIdToImageMap[primaryIndex];
    if (primaryImage.thumbnails.empty()) {
        ALOGW("Using primary in place of thumbnail.");
        *imageIndex = primaryIndex;
        return OK;
    }

    ssize_t thumbnailIndex = mItemIdToImageMap.indexOfKey(
            primaryImage.thumbnails[0]);
    if (thumbnailIndex < 0) {
        ALOGE("Thumbnail item id %d not found!", primaryImage.thumbnails[0]);
        return ERROR_MALFORMED;
    }

    *imageIndex = thumbnailIndex;
    return OK;
}

status_t ItemTable::getImageOffsetAndSize(
        uint32_t *imageIndex, off64_t *offset, size_t *size) {
    if (!mImageItemsValid) {
        return INVALID_OPERATION;
    }

    if (imageIndex != NULL) {
        if (*imageIndex >= mItemIdToImageMap.size()) {
            ALOGE("Bad image index!");
            return BAD_VALUE;
        }
        mCurrentImageIndex = *imageIndex;
    }

    ImageItem &image = mItemIdToImageMap.editValueAt(mCurrentImageIndex);
    if (image.isGrid()) {
        uint32_t tileItemId;
        status_t err = image.getNextTileItemId(&tileItemId, imageIndex != NULL);
        if (err != OK) {
            return err;
        }
        ssize_t tileImageIndex = mItemIdToImageMap.indexOfKey(tileItemId);
        if (tileImageIndex < 0) {
            return ERROR_END_OF_STREAM;
        }
        *offset = mItemIdToImageMap[tileImageIndex].offset;
        *size = mItemIdToImageMap[tileImageIndex].size;
    } else {
        if (imageIndex == NULL) {
            // For single images, we only allow it to be read once, after that
            // it's EOS.  New image index must be requested each time.
            return ERROR_END_OF_STREAM;
        }
        *offset = mItemIdToImageMap[mCurrentImageIndex].offset;
        *size = mItemIdToImageMap[mCurrentImageIndex].size;
    }

    return OK;
}

} // namespace heif

}  // namespace android
