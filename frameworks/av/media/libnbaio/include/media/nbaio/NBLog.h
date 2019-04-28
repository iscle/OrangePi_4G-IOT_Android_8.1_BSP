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

// Non-blocking event logger intended for safe communication between processes via shared memory

#ifndef ANDROID_MEDIA_NBLOG_H
#define ANDROID_MEDIA_NBLOG_H

#include <binder/IMemory.h>
#include <audio_utils/fifo.h>
#include <utils/Mutex.h>
#include <utils/threads.h>

#include <map>
#include <deque>
#include <set>
#include <vector>

namespace android {

class String8;

class NBLog {

public:

typedef uint64_t log_hash_t;

// FIXME Everything needed for client (writer API and registration) should be isolated
//       from the rest of the implementation.
class Writer;
class Reader;

enum Event : uint8_t {
    EVENT_RESERVED,
    EVENT_STRING,               // ASCII string, not NUL-terminated
    // TODO: make timestamp optional
    EVENT_TIMESTAMP,            // clock_gettime(CLOCK_MONOTONIC)
    EVENT_INTEGER,              // integer value entry
    EVENT_FLOAT,                // floating point value entry
    EVENT_PID,                  // process ID and process name
    EVENT_AUTHOR,               // author index (present in merged logs) tracks entry's original log
    EVENT_START_FMT,            // logFormat start event: entry includes format string, following
                                // entries contain format arguments
    EVENT_HASH,                 // unique HASH of log origin, originates from hash of file name
                                // and line number
    EVENT_HISTOGRAM_ENTRY_TS,   // single datum for timestamp histogram
    EVENT_AUDIO_STATE,          // audio on/off event: logged upon FastMixer::onStateChange() call
    EVENT_END_FMT,              // end of logFormat argument list

    EVENT_UPPER_BOUND,          // to check for invalid events
};

private:

// ---------------------------------------------------------------------------
// API for handling format entry operations

// a formatted entry has the following structure:
//    * START_FMT entry, containing the format string
//    * TIMESTAMP entry
//    * HASH entry
//    * author entry of the thread that generated it (optional, present in merged log)
//    * format arg1
//    * format arg2
//    * ...
//    * END_FMT entry

// entry representation in memory
struct entry {
    const uint8_t type;
    const uint8_t length;
    const uint8_t data[0];
};

// entry tail representation (after data)
struct ending {
    uint8_t length;
    uint8_t next[0];
};

// entry iterator
class EntryIterator {
public:
    EntryIterator();
    explicit EntryIterator(const uint8_t *entry);
    EntryIterator(const EntryIterator &other);

    // dereference underlying entry
    const entry&    operator*() const;
    const entry*    operator->() const;
    // advance to next entry
    EntryIterator&       operator++(); // ++i
    // back to previous entry
    EntryIterator&       operator--(); // --i
    EntryIterator        next() const;
    EntryIterator        prev() const;
    bool            operator!=(const EntryIterator &other) const;
    int             operator-(const EntryIterator &other) const;

    bool            hasConsistentLength() const;
    void            copyTo(std::unique_ptr<audio_utils_fifo_writer> &dst) const;
    void            copyData(uint8_t *dst) const;

    template<typename T>
    inline const T& payload() {
        return *reinterpret_cast<const T *>(ptr + offsetof(entry, data));
    }

    inline operator const uint8_t*() const {
        return ptr;
    }

private:
    const uint8_t  *ptr;
};

class AbstractEntry {
public:

    // Entry starting in the given pointer
    explicit AbstractEntry(const uint8_t *entry);
    virtual ~AbstractEntry() {}

    // build concrete entry of appropriate class from pointer
    static std::unique_ptr<AbstractEntry> buildEntry(const uint8_t *ptr);

    // get format entry timestamp
    // TODO consider changing to uint64_t
    virtual int64_t      timestamp() const = 0;

    // get format entry's unique id
    virtual log_hash_t   hash() const = 0;

    // entry's author index (-1 if none present)
    // a Merger has a vector of Readers, author simply points to the index of the
    // Reader that originated the entry
    // TODO consider changing to uint32_t
    virtual int          author() const = 0;

    // copy entry, adding author before timestamp, returns iterator to end of entry
    virtual EntryIterator    copyWithAuthor(std::unique_ptr<audio_utils_fifo_writer> &dst,
                                       int author) const = 0;

protected:
    // copies ordinary entry from src to dst, and returns length of entry
    // size_t      copyEntry(audio_utils_fifo_writer *dst, const iterator &it);
    const uint8_t  *mEntry;
};

class FormatEntry : public AbstractEntry {
public:
    // explicit FormatEntry(const EntryIterator &it);
    explicit FormatEntry(const uint8_t *ptr) : AbstractEntry(ptr) {}
    virtual ~FormatEntry() {}

    EntryIterator begin() const;

    // Entry's format string
    const   char* formatString() const;

    // Enrty's format string length
            size_t      formatStringLength() const;

    // Format arguments (excluding format string, timestamp and author)
            EntryIterator    args() const;

    // get format entry timestamp
    virtual int64_t     timestamp() const override;

    // get format entry's unique id
    virtual log_hash_t  hash() const override;

    // entry's author index (-1 if none present)
    // a Merger has a vector of Readers, author simply points to the index of the
    // Reader that originated the entry
    virtual int         author() const override;

    // copy entry, adding author before timestamp, returns size of original entry
    virtual EntryIterator    copyWithAuthor(std::unique_ptr<audio_utils_fifo_writer> &dst,
                                       int author) const override;

};

class HistogramEntry : public AbstractEntry {
public:
    explicit HistogramEntry(const uint8_t *ptr) : AbstractEntry(ptr) {
    }
    virtual ~HistogramEntry() {}

    virtual int64_t     timestamp() const override;

    virtual log_hash_t  hash() const override;

    virtual int         author() const override;

    virtual EntryIterator    copyWithAuthor(std::unique_ptr<audio_utils_fifo_writer> &dst,
                                       int author) const override;

};

// ---------------------------------------------------------------------------

// representation of a single log entry in private memory
struct Entry {
    Entry(Event event, const void *data, size_t length)
        : mEvent(event), mLength(length), mData(data) { }
    /*virtual*/ ~Entry() { }

    // used during writing to format Entry information as follows: [type][length][data ... ][length]
    int     copyEntryDataAt(size_t offset) const;

private:
    friend class Writer;
    Event       mEvent;     // event type
    uint8_t     mLength;    // length of additional data, 0 <= mLength <= kMaxLength
    const void *mData;      // event type-specific data
    static const size_t kMaxLength = 255;
public:
    // mEvent, mLength, mData[...], duplicate mLength
    static const size_t kOverhead = sizeof(entry) + sizeof(ending);
    // endind length of previous entry
    static const size_t kPreviousLengthOffset = - sizeof(ending) +
                                                offsetof(ending, length);
};

struct HistTsEntry {
    log_hash_t hash;
    int64_t ts;
}; //TODO __attribute__((packed));

struct HistTsEntryWithAuthor {
    log_hash_t hash;
    int64_t ts;
    int author;
}; //TODO __attribute__((packed));

using StateTsEntryWithAuthor = HistTsEntryWithAuthor;

struct HistIntEntry {
    log_hash_t hash;
    int value;
}; //TODO __attribute__((packed));

// representation of a single log entry in shared memory
//  byte[0]             mEvent
//  byte[1]             mLength
//  byte[2]             mData[0]
//  ...
//  byte[2+i]           mData[i]
//  ...
//  byte[2+mLength-1]   mData[mLength-1]
//  byte[2+mLength]     duplicate copy of mLength to permit reverse scan
//  byte[3+mLength]     start of next log entry

    static void    appendInt(String8 *body, const void *data);
    static void    appendFloat(String8 *body, const void *data);
    static void    appendPID(String8 *body, const void *data, size_t length);
    static void    appendTimestamp(String8 *body, const void *data);
    static size_t  fmtEntryLength(const uint8_t *data);
    static String8 bufferDump(const uint8_t *buffer, size_t size);
    static String8 bufferDump(const EntryIterator &it);
public:

// Located in shared memory, must be POD.
// Exactly one process must explicitly call the constructor or use placement new.
// Since this is a POD, the destructor is empty and unnecessary to call it explicitly.
struct Shared {
    Shared() /* mRear initialized via default constructor */ { }
    /*virtual*/ ~Shared() { }

    audio_utils_fifo_index  mRear;  // index one byte past the end of most recent Entry
    char    mBuffer[0];             // circular buffer for entries
};

public:

// ---------------------------------------------------------------------------

// FIXME Timeline was intended to wrap Writer and Reader, but isn't actually used yet.
// For now it is just a namespace for sharedSize().
class Timeline : public RefBase {
public:
#if 0
    Timeline(size_t size, void *shared = NULL);
    virtual ~Timeline();
#endif

    // Input parameter 'size' is the desired size of the timeline in byte units.
    // Returns the size rounded up to a power-of-2, plus the constant size overhead for indices.
    static size_t sharedSize(size_t size);

#if 0
private:
    friend class    Writer;
    friend class    Reader;

    const size_t    mSize;      // circular buffer size in bytes, must be a power of 2
    bool            mOwn;       // whether I own the memory at mShared
    Shared* const   mShared;    // pointer to shared memory
#endif
};

// ---------------------------------------------------------------------------

// Writer is thread-safe with respect to Reader, but not with respect to multiple threads
// calling Writer methods.  If you need multi-thread safety for writing, use LockedWriter.
class Writer : public RefBase {
public:
    Writer();                   // dummy nop implementation without shared memory

    // Input parameter 'size' is the desired size of the timeline in byte units.
    // The size of the shared memory must be at least Timeline::sharedSize(size).
    Writer(void *shared, size_t size);
    Writer(const sp<IMemory>& iMemory, size_t size);

    virtual ~Writer();

    // FIXME needs comments, and some should be private
    virtual void    log(const char *string);
    virtual void    logf(const char *fmt, ...) __attribute__ ((format (printf, 2, 3)));
    virtual void    logvf(const char *fmt, va_list ap);
    virtual void    logTimestamp();
    virtual void    logTimestamp(const int64_t ts);
    virtual void    logInteger(const int x);
    virtual void    logFloat(const float x);
    virtual void    logPID();
    virtual void    logFormat(const char *fmt, log_hash_t hash, ...);
    virtual void    logVFormat(const char *fmt, log_hash_t hash, va_list ap);
    virtual void    logStart(const char *fmt);
    virtual void    logEnd();
    virtual void    logHash(log_hash_t hash);
    virtual void    logEventHistTs(Event event, log_hash_t hash);

    virtual bool    isEnabled() const;

    // return value for all of these is the previous isEnabled()
    virtual bool    setEnabled(bool enabled);   // but won't enable if no shared memory
            bool    enable()    { return setEnabled(true); }
            bool    disable()   { return setEnabled(false); }

    sp<IMemory>     getIMemory() const  { return mIMemory; }

private:
    // 0 <= length <= kMaxLength
    // writes a single Entry to the FIFO
    void    log(Event event, const void *data, size_t length);
    // checks validity of an event before calling log above this one
    void    log(const Entry *entry, bool trusted = false);

    Shared* const   mShared;    // raw pointer to shared memory
    sp<IMemory>     mIMemory;   // ref-counted version, initialized in constructor and then const
    audio_utils_fifo * const mFifo;                 // FIFO itself,
                                                    // non-NULL unless constructor fails
    audio_utils_fifo_writer * const mFifoWriter;    // used to write to FIFO,
                                                    // non-NULL unless dummy constructor used
    bool            mEnabled;   // whether to actually log

    // cached pid and process name to use in %p format specifier
    // total tag length is mPidTagSize and process name is not zero terminated
    char   *mPidTag;
    size_t  mPidTagSize;
};

// ---------------------------------------------------------------------------

// Similar to Writer, but safe for multiple threads to call concurrently
class LockedWriter : public Writer {
public:
    LockedWriter();
    LockedWriter(void *shared, size_t size);

    virtual void    log(const char *string);
    virtual void    logf(const char *fmt, ...) __attribute__ ((format (printf, 2, 3)));
    virtual void    logvf(const char *fmt, va_list ap);
    virtual void    logTimestamp();
    virtual void    logTimestamp(const int64_t ts);
    virtual void    logInteger(const int x);
    virtual void    logFloat(const float x);
    virtual void    logPID();
    virtual void    logStart(const char *fmt);
    virtual void    logEnd();
    virtual void    logHash(log_hash_t hash);

    virtual bool    isEnabled() const;
    virtual bool    setEnabled(bool enabled);

private:
    mutable Mutex   mLock;
};

// ---------------------------------------------------------------------------

class Reader : public RefBase {
public:

    // A snapshot of a readers buffer
    // This is raw data. No analysis has been done on it
    class Snapshot {
    public:
        Snapshot() : mData(NULL), mLost(0) {}

        Snapshot(size_t bufferSize) : mData(new uint8_t[bufferSize]) {}

        ~Snapshot() { delete[] mData; }

        // copy of the buffer
        uint8_t *data() const { return mData; }

        // amount of data lost (given by audio_utils_fifo_reader)
        size_t   lost() const { return mLost; }

        // iterator to beginning of readable segment of snapshot
        // data between begin and end has valid entries
        EntryIterator begin() { return mBegin; }

        // iterator to end of readable segment of snapshot
        EntryIterator end() { return mEnd; }

    private:
        friend class Reader;
        uint8_t              *mData;
        size_t                mLost;
        EntryIterator mBegin;
        EntryIterator mEnd;
    };

    // Input parameter 'size' is the desired size of the timeline in byte units.
    // The size of the shared memory must be at least Timeline::sharedSize(size).
    Reader(const void *shared, size_t size);
    Reader(const sp<IMemory>& iMemory, size_t size);

    virtual ~Reader();

    // get snapshot of readers fifo buffer, effectively consuming the buffer
    std::unique_ptr<Snapshot> getSnapshot();
    // dump a particular snapshot of the reader
    // TODO: move dump to PerformanceAnalysis. Model/view/controller design
    void     dump(int fd, size_t indent, Snapshot & snap);
    // dump the current content of the reader's buffer (call getSnapshot() and previous dump())
    void     dump(int fd, size_t indent = 0);
    bool     isIMemory(const sp<IMemory>& iMemory) const;

private:

    static const std::set<Event> startingTypes;
    static const std::set<Event> endingTypes;
    /*const*/ Shared* const mShared;    // raw pointer to shared memory, actually const but not
                                        // declared as const because audio_utils_fifo() constructor
    sp<IMemory> mIMemory;       // ref-counted version, assigned only in constructor
    int     mFd;                // file descriptor
    int     mIndent;            // indentation level
    audio_utils_fifo * const mFifo;                 // FIFO itself,
                                                    // non-NULL unless constructor fails
    audio_utils_fifo_reader * const mFifoReader;    // used to read from FIFO,
                                                    // non-NULL unless constructor fails

    // TODO: it might be clearer, instead of a direct map from source location to vector of
    // timestamps, if we instead first mapped from source location to an object that
    // represented that location. And one_of its fields would be a vector of timestamps.
    // That would allow us to record other information about the source location beyond timestamps.
    void    dumpLine(const String8& timestamp, String8& body);

    EntryIterator   handleFormat(const FormatEntry &fmtEntry,
                                         String8 *timestamp,
                                         String8 *body);
    // dummy method for handling absent author entry
    virtual void handleAuthor(const AbstractEntry& /*fmtEntry*/, String8* /*body*/) {}

    // Searches for the last entry of type <type> in the range [front, back)
    // back has to be entry-aligned. Returns nullptr if none enconuntered.
    static const uint8_t *findLastEntryOfTypes(const uint8_t *front, const uint8_t *back,
                                         const std::set<Event> &types);

    static const size_t kSquashTimestamp = 5; // squash this many or more adjacent timestamps
};

// Wrapper for a reader with a name. Contains a pointer to the reader and a pointer to the name
class NamedReader {
public:
    NamedReader() { mName[0] = '\0'; } // for Vector
    NamedReader(const sp<NBLog::Reader>& reader, const char *name) :
        mReader(reader)
        { strlcpy(mName, name, sizeof(mName)); }
    ~NamedReader() { }
    const sp<NBLog::Reader>&  reader() const { return mReader; }
    const char*               name() const { return mName; }

private:
    sp<NBLog::Reader>   mReader;
    static const size_t kMaxName = 32;
    char                mName[kMaxName];
};

// ---------------------------------------------------------------------------

class Merger : public RefBase {
public:
    Merger(const void *shared, size_t size);

    virtual ~Merger() {}

    void addReader(const NamedReader &reader);
    // TODO add removeReader
    void merge();
    // FIXME This is returning a reference to a shared variable that needs a lock
    const std::vector<NamedReader>& getNamedReaders() const;
private:
    // vector of the readers the merger is supposed to merge from.
    // every reader reads from a writer's buffer
    // FIXME Needs to be protected by a lock
    std::vector<NamedReader> mNamedReaders;

    // TODO Need comments on all of these
    Shared * const mShared;
    std::unique_ptr<audio_utils_fifo> mFifo;
    std::unique_ptr<audio_utils_fifo_writer> mFifoWriter;
};

class MergeReader : public Reader {
public:
    MergeReader(const void *shared, size_t size, Merger &merger);
private:
    // FIXME Needs to be protected by a lock,
    //       because even though our use of it is read-only there may be asynchronous updates
    const std::vector<NamedReader>& mNamedReaders;
    // handle author entry by looking up the author's name and appending it to the body
    // returns number of bytes read from fmtEntry
    void handleAuthor(const AbstractEntry &fmtEntry, String8 *body);
};

// MergeThread is a thread that contains a Merger. It works as a retriggerable one-shot:
// when triggered, it awakes for a lapse of time, during which it periodically merges; if
// retriggered, the timeout is reset.
// The thread is triggered on AudioFlinger binder activity.
class MergeThread : public Thread {
public:
    MergeThread(Merger &merger);
    virtual ~MergeThread() override;

    // Reset timeout and activate thread to merge periodically if it's idle
    void wakeup();

    // Set timeout period until the merging thread goes idle again
    void setTimeoutUs(int time);

private:
    virtual bool threadLoop() override;

    // the merger who actually does the work of merging the logs
    Merger&     mMerger;

    // mutex for the condition variable
    Mutex       mMutex;

    // condition variable to activate merging on timeout >= 0
    Condition   mCond;

    // time left until the thread blocks again (in microseconds)
    int         mTimeoutUs;

    // merging period when the thread is awake
    static const int  kThreadSleepPeriodUs = 1000000 /*1s*/;

    // initial timeout value when triggered
    static const int  kThreadWakeupPeriodUs = 3000000 /*3s*/;
};

};  // class NBLog

// TODO put somewhere else
static inline int64_t get_monotonic_ns() {
    timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) == 0) {
        return (uint64_t) ts.tv_sec * 1000 * 1000 * 1000 + ts.tv_nsec;
    }
    return 0; // should not happen.
}

}   // namespace android

#endif  // ANDROID_MEDIA_NBLOG_H
