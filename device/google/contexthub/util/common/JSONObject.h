/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef JSON_OBJECT_H_

#define JSON_OBJECT_H_

#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/foundation/AString.h>
#include <utils/Errors.h>
#include <utils/KeyedVector.h>
#include <utils/RefBase.h>
#include <utils/Vector.h>

namespace android {

struct JSONArray;
struct JSONCompound;
struct JSONObject;

struct JSONValue {
    enum FieldType {
        TYPE_STRING,
        TYPE_INT32,
        TYPE_FLOAT,
        TYPE_BOOLEAN,
        TYPE_NULL,
        TYPE_OBJECT,
        TYPE_ARRAY,
    };

    // Returns the number of bytes consumed or an error.
    static ssize_t Parse(const char *data, size_t size, JSONValue *out);

    JSONValue();
    JSONValue(const JSONValue &);
    JSONValue &operator=(const JSONValue &);
    ~JSONValue();

    FieldType type() const;
    bool getInt32(int32_t *value) const;
    bool getFloat(float *value) const;
    bool getString(AString *value) const;
    bool getBoolean(bool *value) const;
    bool getObject(sp<JSONObject> *value) const;
    bool getArray(sp<JSONArray> *value) const;

    void setInt32(int32_t value);
    void setFloat(float value);
    void setString(const AString &value);
    void setBoolean(bool value);
    void setObject(const sp<JSONObject> &obj);
    void setArray(const sp<JSONArray> &array);
    void unset();  // i.e. setNull()

    AString toString(size_t depth = 0, bool indentFirstLine = true) const;

private:
    FieldType mType;

    union {
        int32_t mInt32;
        float mFloat;
        AString *mString;
        bool mBoolean;
        JSONCompound *mObjectOrArray;
    } mValue;
};

struct JSONCompound : public RefBase {
    static sp<JSONCompound> Parse(const char *data, size_t size);

    AString toString(size_t depth = 0, bool indentFirstLine = true) const;

    virtual bool isObject() const = 0;

protected:
    virtual ~JSONCompound() {}

    virtual AString internalToString(size_t depth) const = 0;

    JSONCompound() {}

private:
    friend struct JSONValue;

    DISALLOW_EVIL_CONSTRUCTORS(JSONCompound);
};

template<class KEY>
struct JSONBase : public JSONCompound {
    JSONBase() {}

#define PREAMBLE()                              \
    JSONValue value;                            \
    if (!getValue(key, &value)) {               \
        return false;                           \
    }

    bool getFieldType(KEY key, JSONValue::FieldType *type) const {
        PREAMBLE()
        *type = value.type();
        return true;
    }

    bool getInt32(KEY key, int32_t *out) const {
        PREAMBLE()
        return value.getInt32(out);
    }

    bool getFloat(KEY key, float *out) const {
        PREAMBLE()
        return value.getFloat(out);
    }

    bool getString(KEY key, AString *out) const {
        PREAMBLE()
        return value.getString(out);
    }

    bool getBoolean(KEY key, bool *out) const {
        PREAMBLE()
        return value.getBoolean(out);
    }

    bool getObject(KEY key, sp<JSONObject> *obj) const {
        PREAMBLE()
        return value.getObject(obj);
    }

    bool getArray(KEY key, sp<JSONArray> *obj) const {
        PREAMBLE()
        return value.getArray(obj);
    }

#undef PREAMBLE

protected:
    virtual ~JSONBase() {}

    virtual bool getValue(KEY key, JSONValue *value) const = 0;

private:
    DISALLOW_EVIL_CONSTRUCTORS(JSONBase);
};

struct JSONObject : public JSONBase<const char *> {
    JSONObject();

    virtual bool isObject() const;
    void setValue(const char *key, const JSONValue &value);

    void setInt32(const char *key, int32_t in) {
        JSONValue val;
        val.setInt32(in);
        setValue(key, val);
    }

    void setFloat(const char *key, float in) {
        JSONValue val;
        val.setFloat(in);
        setValue(key, val);
    }

    void setString(const char *key, AString in) {
        JSONValue val;
        val.setString(in);
        setValue(key, val);
    }

    void setBoolean(const char *key, bool in) {
        JSONValue val;
        val.setBoolean(in);
        setValue(key, val);
    }

    void setObject(const char *key, const sp<JSONObject> &obj) {
        JSONValue val;
        val.setObject(obj);
        setValue(key, val);
    }

    void setArray(const char *key, const sp<JSONArray> &obj) {
        JSONValue val;
        val.setArray(obj);
        setValue(key, val);
    }

protected:
    virtual ~JSONObject();

    virtual bool getValue(const char *key, JSONValue *value) const;
    virtual AString internalToString(size_t depth) const;

private:
    KeyedVector<AString, JSONValue> mValues;

    DISALLOW_EVIL_CONSTRUCTORS(JSONObject);
};

struct JSONArray : public JSONBase<size_t> {
    JSONArray();

    virtual bool isObject() const;
    size_t size() const;
    void addValue(const JSONValue &value);

    void addInt32(int32_t in) {
        JSONValue val;
        val.setInt32(in);
        addValue(val);
    }

    void addFloat(float in) {
        JSONValue val;
        val.setFloat(in);
        addValue(val);
    }

    void addString(AString in) {
        JSONValue val;
        val.setString(in);
        addValue(val);
    }

    void addBoolean(bool in) {
        JSONValue val;
        val.setBoolean(in);
        addValue(val);
    }

    void addObject(const sp<JSONObject> &obj) {
        JSONValue val;
        val.setObject(obj);
        addValue(val);
    }

    void addArray(const sp<JSONArray> &obj) {
        JSONValue val;
        val.setArray(obj);
        addValue(val);
    }

protected:
    virtual ~JSONArray();

    virtual bool getValue(size_t key, JSONValue *value) const;
    virtual AString internalToString(size_t depth) const;


private:
    Vector<JSONValue> mValues;

    DISALLOW_EVIL_CONSTRUCTORS(JSONArray);
};

}  // namespace android

#endif  // JSON_OBJECT_H_
