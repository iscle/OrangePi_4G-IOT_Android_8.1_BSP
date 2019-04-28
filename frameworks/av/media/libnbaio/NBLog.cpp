/*
 * Copyright (C) 2013 The Android Open Source Project
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

/*
* Documentation: Workflow summary for histogram data processing:
* For more details on FIFO, please see system/media/audio_utils; doxygen
* TODO: add this documentation to doxygen once it is further developed
* 1) Writing buffer period timestamp to the circular buffer
* onWork()
*     Called every period length (e.g., 4ms)
*     Calls LOG_HIST_TS
* LOG_HIST_TS
*     Hashes file name and line number, and writes single timestamp to buffer
*     calls NBLOG::Writer::logEventHistTS once
* NBLOG::Writer::logEventHistTS
*     calls NBLOG::Writer::log on hash and current timestamp
*     time is in CLOCK_MONOTONIC converted to ns
* NBLOG::Writer::log(Event, const void*, size_t)
*     Initializes Entry, a struct containing one log entry
*     Entry contains the event type (mEvent), data length (mLength),
*     and data pointer (mData)
*     TODO: why mLength (max length of buffer data)  must be <= kMaxLength = 255?
*     calls NBLOG::Writer::log(Entry *, bool)
* NBLog::Writer::log(Entry *, bool)
*     Calls copyEntryDataAt to format data as follows in temp array:
*     [type][length][data ... ][length]
*     calls audio_utils_fifo_writer.write on temp
* audio_utils_fifo_writer.write
*     calls obtain(), memcpy (reference in doxygen)
*     returns number of frames written
* ssize_t audio_utils_fifo_reader::obtain
*     Determines readable buffer section via pointer arithmetic on reader
*     and writer pointers
* Similarly, LOG_AUDIO_STATE() is called by onStateChange whenever audio is
* turned on or off, and writes this notification to the FIFO.
*
* 2) reading the data from shared memory
* Thread::threadloop()
*     TODO: add description?
* NBLog::MergeThread::threadLoop()
*     calls NBLog::Merger::merge
* NBLog::Merger::merge
*     Merges snapshots sorted by timestamp
*     for each reader in vector of class NamedReader,
*     callsNamedReader::reader()->getSnapshot
*     TODO: check whether the rest of this function is relevant
* NBLog::Reader::getSnapshot
*     copies snapshot of reader's fifo buffer into its own buffer
*     calls mFifoReader->obtain to find readable data
*     sets snapshot.begin() and .end() iterators to boundaries of valid entries
*     moves the fifo reader index to after the last entry read
*     in this case, the buffer is in shared memory. in (4), the buffer is private
*
* 3) reading the data from private buffer
* MediaLogService::dump
*     calls NBLog::Reader::dump(CONSOLE)
*     The private buffer contains all logs for all readers in shared memory
* NBLog::Reader::dump(int)
*     calls getSnapshot on the current reader
*     calls dump(int, size_t, Snapshot)
* NBLog::Reader::dump(int, size, snapshot)
*     iterates through snapshot's events and switches based on their type
*     (string, timestamp, etc...)
*     In the case of EVENT_HISTOGRAM_ENTRY_TS, adds a list of timestamp sequences
*     (histogram entry) to NBLog::mHists
*     TODO: add every HISTOGRAM_ENTRY_TS to two
*     circular buffers: one short-term and one long-term (can add even longer-term
*     structures in the future). When dump is called, print everything currently
*     in the buffer.
* NBLog::drawHistogram
*     input: timestamp array
*     buckets this to a histogram and prints
*
*/

#define LOG_TAG "NBLog"
// #define LOG_NDEBUG 0

#include <algorithm>
#include <climits>
#include <deque>
#include <fstream>
// #include <inttypes.h>
#include <iostream>
#include <math.h>
#include <numeric>
#include <vector>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <time.h>
#include <new>
#include <audio_utils/roundup.h>
#include <media/nbaio/NBLog.h>
#include <media/nbaio/PerformanceAnalysis.h>
#include <media/nbaio/ReportPerformance.h>
// #include <utils/CallStack.h> // used to print callstack
#include <utils/Log.h>
#include <utils/String8.h>

#include <queue>
#include <utility>

namespace android {

int NBLog::Entry::copyEntryDataAt(size_t offset) const
{
    // FIXME This is too slow
    if (offset == 0)
        return mEvent;
    else if (offset == 1)
        return mLength;
    else if (offset < (size_t) (mLength + 2))
        return ((char *) mData)[offset - 2];
    else if (offset == (size_t) (mLength + 2))
        return mLength;
    else
        return 0;
}

// ---------------------------------------------------------------------------

/*static*/
std::unique_ptr<NBLog::AbstractEntry> NBLog::AbstractEntry::buildEntry(const uint8_t *ptr) {
    const uint8_t type = EntryIterator(ptr)->type;
    switch (type) {
    case EVENT_START_FMT:
        return std::make_unique<FormatEntry>(FormatEntry(ptr));
    case EVENT_AUDIO_STATE:
    case EVENT_HISTOGRAM_ENTRY_TS:
        return std::make_unique<HistogramEntry>(HistogramEntry(ptr));
    default:
        ALOGW("Tried to create AbstractEntry of type %d", type);
        return nullptr;
    }
}

NBLog::AbstractEntry::AbstractEntry(const uint8_t *entry) : mEntry(entry) {
}

// ---------------------------------------------------------------------------

NBLog::EntryIterator NBLog::FormatEntry::begin() const {
    return EntryIterator(mEntry);
}

const char *NBLog::FormatEntry::formatString() const {
    return (const char*) mEntry + offsetof(entry, data);
}

size_t NBLog::FormatEntry::formatStringLength() const {
    return mEntry[offsetof(entry, length)];
}

NBLog::EntryIterator NBLog::FormatEntry::args() const {
    auto it = begin();
    // skip start fmt
    ++it;
    // skip timestamp
    ++it;
    // skip hash
    ++it;
    // Skip author if present
    if (it->type == EVENT_AUTHOR) {
        ++it;
    }
    return it;
}

int64_t NBLog::FormatEntry::timestamp() const {
    auto it = begin();
    // skip start fmt
    ++it;
    return it.payload<int64_t>();
}

NBLog::log_hash_t NBLog::FormatEntry::hash() const {
    auto it = begin();
    // skip start fmt
    ++it;
    // skip timestamp
    ++it;
    // unaligned 64-bit read not supported
    log_hash_t hash;
    memcpy(&hash, it->data, sizeof(hash));
    return hash;
}

int NBLog::FormatEntry::author() const {
    auto it = begin();
    // skip start fmt
    ++it;
    // skip timestamp
    ++it;
    // skip hash
    ++it;
    // if there is an author entry, return it, return -1 otherwise
    if (it->type == EVENT_AUTHOR) {
        return it.payload<int>();
    }
    return -1;
}

NBLog::EntryIterator NBLog::FormatEntry::copyWithAuthor(
        std::unique_ptr<audio_utils_fifo_writer> &dst, int author) const {
    auto it = begin();
    // copy fmt start entry
    it.copyTo(dst);
    // copy timestamp
    (++it).copyTo(dst);    // copy hash
    (++it).copyTo(dst);
    // insert author entry
    size_t authorEntrySize = NBLog::Entry::kOverhead + sizeof(author);
    uint8_t authorEntry[authorEntrySize];
    authorEntry[offsetof(entry, type)] = EVENT_AUTHOR;
    authorEntry[offsetof(entry, length)] =
        authorEntry[authorEntrySize + NBLog::Entry::kPreviousLengthOffset] =
        sizeof(author);
    *(int*) (&authorEntry[offsetof(entry, data)]) = author;
    dst->write(authorEntry, authorEntrySize);
    // copy rest of entries
    while ((++it)->type != EVENT_END_FMT) {
        it.copyTo(dst);
    }
    it.copyTo(dst);
    ++it;
    return it;
}

void NBLog::EntryIterator::copyTo(std::unique_ptr<audio_utils_fifo_writer> &dst) const {
    size_t length = ptr[offsetof(entry, length)] + NBLog::Entry::kOverhead;
    dst->write(ptr, length);
}

void NBLog::EntryIterator::copyData(uint8_t *dst) const {
    memcpy((void*) dst, ptr + offsetof(entry, data), ptr[offsetof(entry, length)]);
}

NBLog::EntryIterator::EntryIterator()
    : ptr(nullptr) {}

NBLog::EntryIterator::EntryIterator(const uint8_t *entry)
    : ptr(entry) {}

NBLog::EntryIterator::EntryIterator(const NBLog::EntryIterator &other)
    : ptr(other.ptr) {}

const NBLog::entry& NBLog::EntryIterator::operator*() const {
    return *(entry*) ptr;
}

const NBLog::entry* NBLog::EntryIterator::operator->() const {
    return (entry*) ptr;
}

NBLog::EntryIterator& NBLog::EntryIterator::operator++() {
    ptr += ptr[offsetof(entry, length)] + NBLog::Entry::kOverhead;
    return *this;
}

NBLog::EntryIterator& NBLog::EntryIterator::operator--() {
    ptr -= ptr[NBLog::Entry::kPreviousLengthOffset] + NBLog::Entry::kOverhead;
    return *this;
}

NBLog::EntryIterator NBLog::EntryIterator::next() const {
    EntryIterator aux(*this);
    return ++aux;
}

NBLog::EntryIterator NBLog::EntryIterator::prev() const {
    EntryIterator aux(*this);
    return --aux;
}

int NBLog::EntryIterator::operator-(const NBLog::EntryIterator &other) const {
    return ptr - other.ptr;
}

bool NBLog::EntryIterator::operator!=(const EntryIterator &other) const {
    return ptr != other.ptr;
}

bool NBLog::EntryIterator::hasConsistentLength() const {
    return ptr[offsetof(entry, length)] == ptr[ptr[offsetof(entry, length)] +
        NBLog::Entry::kOverhead + NBLog::Entry::kPreviousLengthOffset];
}

// ---------------------------------------------------------------------------

int64_t NBLog::HistogramEntry::timestamp() const {
    return EntryIterator(mEntry).payload<HistTsEntry>().ts;
}

NBLog::log_hash_t NBLog::HistogramEntry::hash() const {
    return EntryIterator(mEntry).payload<HistTsEntry>().hash;
}

int NBLog::HistogramEntry::author() const {
    EntryIterator it(mEntry);
    if (it->length == sizeof(HistTsEntryWithAuthor)) {
        return it.payload<HistTsEntryWithAuthor>().author;
    } else {
        return -1;
    }
}

NBLog::EntryIterator NBLog::HistogramEntry::copyWithAuthor(
        std::unique_ptr<audio_utils_fifo_writer> &dst, int author) const {
    // Current histogram entry has {type, length, struct HistTsEntry, length}.
    // We now want {type, length, struct HistTsEntryWithAuthor, length}
    uint8_t buffer[Entry::kOverhead + sizeof(HistTsEntryWithAuthor)];
    // Copy content until the point we want to add the author
    memcpy(buffer, mEntry, sizeof(entry) + sizeof(HistTsEntry));
    // Copy the author
    *(int*) (buffer + sizeof(entry) + sizeof(HistTsEntry)) = author;
    // Update lengths
    buffer[offsetof(entry, length)] = sizeof(HistTsEntryWithAuthor);
    buffer[sizeof(buffer) + Entry::kPreviousLengthOffset] = sizeof(HistTsEntryWithAuthor);
    // Write new buffer into FIFO
    dst->write(buffer, sizeof(buffer));
    return EntryIterator(mEntry).next();
}

// ---------------------------------------------------------------------------

#if 0   // FIXME see note in NBLog.h
NBLog::Timeline::Timeline(size_t size, void *shared)
    : mSize(roundup(size)), mOwn(shared == NULL),
      mShared((Shared *) (mOwn ? new char[sharedSize(size)] : shared))
{
    new (mShared) Shared;
}

NBLog::Timeline::~Timeline()
{
    mShared->~Shared();
    if (mOwn) {
        delete[] (char *) mShared;
    }
}
#endif

/*static*/
size_t NBLog::Timeline::sharedSize(size_t size)
{
    // TODO fifo now supports non-power-of-2 buffer sizes, so could remove the roundup
    return sizeof(Shared) + roundup(size);
}

// ---------------------------------------------------------------------------

NBLog::Writer::Writer()
    : mShared(NULL), mFifo(NULL), mFifoWriter(NULL), mEnabled(false), mPidTag(NULL), mPidTagSize(0)
{
}

NBLog::Writer::Writer(void *shared, size_t size)
    : mShared((Shared *) shared),
      mFifo(mShared != NULL ?
        new audio_utils_fifo(size, sizeof(uint8_t),
            mShared->mBuffer, mShared->mRear, NULL /*throttlesFront*/) : NULL),
      mFifoWriter(mFifo != NULL ? new audio_utils_fifo_writer(*mFifo) : NULL),
      mEnabled(mFifoWriter != NULL)
{
    // caching pid and process name
    pid_t id = ::getpid();
    char procName[16];
    int status = prctl(PR_GET_NAME, procName);
    if (status) {  // error getting process name
        procName[0] = '\0';
    }
    size_t length = strlen(procName);
    mPidTagSize = length + sizeof(pid_t);
    mPidTag = new char[mPidTagSize];
    memcpy(mPidTag, &id, sizeof(pid_t));
    memcpy(mPidTag + sizeof(pid_t), procName, length);
}

NBLog::Writer::Writer(const sp<IMemory>& iMemory, size_t size)
    : Writer(iMemory != 0 ? (Shared *) iMemory->pointer() : NULL, size)
{
    mIMemory = iMemory;
}

NBLog::Writer::~Writer()
{
    delete mFifoWriter;
    delete mFifo;
    delete[] mPidTag;
}

void NBLog::Writer::log(const char *string)
{
    if (!mEnabled) {
        return;
    }
    LOG_ALWAYS_FATAL_IF(string == NULL, "Attempted to log NULL string");
    size_t length = strlen(string);
    if (length > Entry::kMaxLength) {
        length = Entry::kMaxLength;
    }
    log(EVENT_STRING, string, length);
}

void NBLog::Writer::logf(const char *fmt, ...)
{
    if (!mEnabled) {
        return;
    }
    va_list ap;
    va_start(ap, fmt);
    Writer::logvf(fmt, ap);     // the Writer:: is needed to avoid virtual dispatch for LockedWriter
    va_end(ap);
}

void NBLog::Writer::logvf(const char *fmt, va_list ap)
{
    if (!mEnabled) {
        return;
    }
    char buffer[Entry::kMaxLength + 1 /*NUL*/];
    int length = vsnprintf(buffer, sizeof(buffer), fmt, ap);
    if (length >= (int) sizeof(buffer)) {
        length = sizeof(buffer) - 1;
        // NUL termination is not required
        // buffer[length] = '\0';
    }
    if (length >= 0) {
        log(EVENT_STRING, buffer, length);
    }
}

void NBLog::Writer::logTimestamp()
{
    if (!mEnabled) {
        return;
    }
    int64_t ts = get_monotonic_ns();
    if (ts > 0) {
        log(EVENT_TIMESTAMP, &ts, sizeof(ts));
    } else {
        ALOGE("Failed to get timestamp");
    }
}

void NBLog::Writer::logTimestamp(const int64_t ts)
{
    if (!mEnabled) {
        return;
    }
    log(EVENT_TIMESTAMP, &ts, sizeof(ts));
}

void NBLog::Writer::logInteger(const int x)
{
    if (!mEnabled) {
        return;
    }
    log(EVENT_INTEGER, &x, sizeof(x));
}

void NBLog::Writer::logFloat(const float x)
{
    if (!mEnabled) {
        return;
    }
    log(EVENT_FLOAT, &x, sizeof(x));
}

void NBLog::Writer::logPID()
{
    if (!mEnabled) {
        return;
    }
    log(EVENT_PID, mPidTag, mPidTagSize);
}

void NBLog::Writer::logStart(const char *fmt)
{
    if (!mEnabled) {
        return;
    }
    size_t length = strlen(fmt);
    if (length > Entry::kMaxLength) {
        length = Entry::kMaxLength;
    }
    log(EVENT_START_FMT, fmt, length);
}

void NBLog::Writer::logEnd()
{
    if (!mEnabled) {
        return;
    }
    Entry entry = Entry(EVENT_END_FMT, NULL, 0);
    log(&entry, true);
}

void NBLog::Writer::logHash(log_hash_t hash)
{
    if (!mEnabled) {
        return;
    }
    log(EVENT_HASH, &hash, sizeof(hash));
}

void NBLog::Writer::logEventHistTs(Event event, log_hash_t hash)
{
    if (!mEnabled) {
        return;
    }
    HistTsEntry data;
    data.hash = hash;
    data.ts = get_monotonic_ns();
    if (data.ts > 0) {
        log(event, &data, sizeof(data));
    } else {
        ALOGE("Failed to get timestamp");
    }
}

void NBLog::Writer::logFormat(const char *fmt, log_hash_t hash, ...)
{
    if (!mEnabled) {
        return;
    }

    va_list ap;
    va_start(ap, hash);
    Writer::logVFormat(fmt, hash, ap);
    va_end(ap);
}

void NBLog::Writer::logVFormat(const char *fmt, log_hash_t hash, va_list argp)
{
    if (!mEnabled) {
        return;
    }
    Writer::logStart(fmt);
    int i;
    double f;
    char* s;
    int64_t t;
    Writer::logTimestamp();
    Writer::logHash(hash);
    for (const char *p = fmt; *p != '\0'; p++) {
        // TODO: implement more complex formatting such as %.3f
        if (*p != '%') {
            continue;
        }
        switch(*++p) {
        case 's': // string
            s = va_arg(argp, char *);
            Writer::log(s);
            break;

        case 't': // timestamp
            t = va_arg(argp, int64_t);
            Writer::logTimestamp(t);
            break;

        case 'd': // integer
            i = va_arg(argp, int);
            Writer::logInteger(i);
            break;

        case 'f': // float
            f = va_arg(argp, double); // float arguments are promoted to double in vararg lists
            Writer::logFloat((float)f);
            break;

        case 'p': // pid
            Writer::logPID();
            break;

        // the "%\0" case finishes parsing
        case '\0':
            --p;
            break;

        case '%':
            break;

        default:
            ALOGW("NBLog Writer parsed invalid format specifier: %c", *p);
            break;
        }
    }
    Writer::logEnd();
}

void NBLog::Writer::log(Event event, const void *data, size_t length)
{
    if (!mEnabled) {
        return;
    }
    if (data == NULL || length > Entry::kMaxLength) {
        // TODO Perhaps it makes sense to display truncated data or at least a
        //      message that the data is too long?  The current behavior can create
        //      a confusion for a programmer debugging their code.
        return;
    }
    // Ignore if invalid event
    if (event == EVENT_RESERVED || event >= EVENT_UPPER_BOUND) {
        return;
    }
    Entry etr(event, data, length);
    log(&etr, true /*trusted*/);
}

void NBLog::Writer::log(const NBLog::Entry *etr, bool trusted)
{
    if (!mEnabled) {
        return;
    }
    if (!trusted) {
        log(etr->mEvent, etr->mData, etr->mLength);
        return;
    }
    size_t need = etr->mLength + Entry::kOverhead;    // mEvent, mLength, data[mLength], mLength
                                                      // need = number of bytes written to FIFO

    // FIXME optimize this using memcpy for the data part of the Entry.
    // The Entry could have a method copyTo(ptr, offset, size) to optimize the copy.
    // checks size of a single log Entry: type, length, data pointer and ending
    uint8_t temp[Entry::kMaxLength + Entry::kOverhead];
    // write this data to temp array
    for (size_t i = 0; i < need; i++) {
        temp[i] = etr->copyEntryDataAt(i);
    }
    // write to circular buffer
    mFifoWriter->write(temp, need);
}

bool NBLog::Writer::isEnabled() const
{
    return mEnabled;
}

bool NBLog::Writer::setEnabled(bool enabled)
{
    bool old = mEnabled;
    mEnabled = enabled && mShared != NULL;
    return old;
}

// ---------------------------------------------------------------------------

NBLog::LockedWriter::LockedWriter()
    : Writer()
{
}

NBLog::LockedWriter::LockedWriter(void *shared, size_t size)
    : Writer(shared, size)
{
}

void NBLog::LockedWriter::log(const char *string)
{
    Mutex::Autolock _l(mLock);
    Writer::log(string);
}

void NBLog::LockedWriter::logf(const char *fmt, ...)
{
    // FIXME should not take the lock until after formatting is done
    Mutex::Autolock _l(mLock);
    va_list ap;
    va_start(ap, fmt);
    Writer::logvf(fmt, ap);
    va_end(ap);
}

void NBLog::LockedWriter::logvf(const char *fmt, va_list ap)
{
    // FIXME should not take the lock until after formatting is done
    Mutex::Autolock _l(mLock);
    Writer::logvf(fmt, ap);
}

void NBLog::LockedWriter::logTimestamp()
{
    // FIXME should not take the lock until after the clock_gettime() syscall
    Mutex::Autolock _l(mLock);
    Writer::logTimestamp();
}

void NBLog::LockedWriter::logTimestamp(const int64_t ts)
{
    Mutex::Autolock _l(mLock);
    Writer::logTimestamp(ts);
}

void NBLog::LockedWriter::logInteger(const int x)
{
    Mutex::Autolock _l(mLock);
    Writer::logInteger(x);
}

void NBLog::LockedWriter::logFloat(const float x)
{
    Mutex::Autolock _l(mLock);
    Writer::logFloat(x);
}

void NBLog::LockedWriter::logPID()
{
    Mutex::Autolock _l(mLock);
    Writer::logPID();
}

void NBLog::LockedWriter::logStart(const char *fmt)
{
    Mutex::Autolock _l(mLock);
    Writer::logStart(fmt);
}


void NBLog::LockedWriter::logEnd()
{
    Mutex::Autolock _l(mLock);
    Writer::logEnd();
}

void NBLog::LockedWriter::logHash(log_hash_t hash)
{
    Mutex::Autolock _l(mLock);
    Writer::logHash(hash);
}

bool NBLog::LockedWriter::isEnabled() const
{
    Mutex::Autolock _l(mLock);
    return Writer::isEnabled();
}

bool NBLog::LockedWriter::setEnabled(bool enabled)
{
    Mutex::Autolock _l(mLock);
    return Writer::setEnabled(enabled);
}

// ---------------------------------------------------------------------------

const std::set<NBLog::Event> NBLog::Reader::startingTypes {NBLog::Event::EVENT_START_FMT,
                                                           NBLog::Event::EVENT_HISTOGRAM_ENTRY_TS};
const std::set<NBLog::Event> NBLog::Reader::endingTypes   {NBLog::Event::EVENT_END_FMT,
                                                           NBLog::Event::EVENT_HISTOGRAM_ENTRY_TS,
                                                           NBLog::Event::EVENT_AUDIO_STATE};

NBLog::Reader::Reader(const void *shared, size_t size)
    : mShared((/*const*/ Shared *) shared), /*mIMemory*/
      mFd(-1), mIndent(0),
      mFifo(mShared != NULL ?
        new audio_utils_fifo(size, sizeof(uint8_t),
            mShared->mBuffer, mShared->mRear, NULL /*throttlesFront*/) : NULL),
      mFifoReader(mFifo != NULL ? new audio_utils_fifo_reader(*mFifo) : NULL)
{
}

NBLog::Reader::Reader(const sp<IMemory>& iMemory, size_t size)
    : Reader(iMemory != 0 ? (Shared *) iMemory->pointer() : NULL, size)
{
    mIMemory = iMemory;
}

NBLog::Reader::~Reader()
{
    delete mFifoReader;
    delete mFifo;
}

const uint8_t *NBLog::Reader::findLastEntryOfTypes(const uint8_t *front, const uint8_t *back,
                                            const std::set<Event> &types) {
    while (back + Entry::kPreviousLengthOffset >= front) {
        const uint8_t *prev = back - back[Entry::kPreviousLengthOffset] - Entry::kOverhead;
        if (prev < front || prev + prev[offsetof(entry, length)] +
                            Entry::kOverhead != back) {

            // prev points to an out of limits or inconsistent entry
            return nullptr;
        }
        if (types.find((const Event) prev[offsetof(entry, type)]) != types.end()) {
            return prev;
        }
        back = prev;
    }
    return nullptr; // no entry found
}

std::unique_ptr<NBLog::Reader::Snapshot> NBLog::Reader::getSnapshot()
{
    if (mFifoReader == NULL) {
        return std::unique_ptr<NBLog::Reader::Snapshot>(new Snapshot());
    }
    // make a copy to avoid race condition with writer
    size_t capacity = mFifo->capacity();

    // This emulates the behaviour of audio_utils_fifo_reader::read, but without incrementing the
    // reader index. The index is incremented after handling corruption, to after the last complete
    // entry of the buffer
    size_t lost;
    audio_utils_iovec iovec[2];
    ssize_t availToRead = mFifoReader->obtain(iovec, capacity, NULL /*timeout*/, &lost);
    if (availToRead <= 0) {
        return std::unique_ptr<NBLog::Reader::Snapshot>(new Snapshot());
    }

    std::unique_ptr<Snapshot> snapshot(new Snapshot(availToRead));
    memcpy(snapshot->mData, (const char *) mFifo->buffer() + iovec[0].mOffset, iovec[0].mLength);
    if (iovec[1].mLength > 0) {
        memcpy(snapshot->mData + (iovec[0].mLength),
            (const char *) mFifo->buffer() + iovec[1].mOffset, iovec[1].mLength);
    }

    // Handle corrupted buffer
    // Potentially, a buffer has corrupted data on both beginning (due to overflow) and end
    // (due to incomplete format entry). But even if the end format entry is incomplete,
    // it ends in a complete entry (which is not an END_FMT). So is safe to traverse backwards.
    // TODO: handle client corruption (in the middle of a buffer)

    const uint8_t *back = snapshot->mData + availToRead;
    const uint8_t *front = snapshot->mData;

    // Find last END_FMT. <back> is sitting on an entry which might be the middle of a FormatEntry.
    // We go backwards until we find an EVENT_END_FMT.
    const uint8_t *lastEnd = findLastEntryOfTypes(front, back, endingTypes);
    if (lastEnd == nullptr) {
        snapshot->mEnd = snapshot->mBegin = EntryIterator(front);
    } else {
        // end of snapshot points to after last END_FMT entry
        snapshot->mEnd = EntryIterator(lastEnd).next();
        // find first START_FMT
        const uint8_t *firstStart = nullptr;
        const uint8_t *firstStartTmp = snapshot->mEnd;
        while ((firstStartTmp = findLastEntryOfTypes(front, firstStartTmp, startingTypes))
                != nullptr) {
            firstStart = firstStartTmp;
        }
        // firstStart is null if no START_FMT entry was found before lastEnd
        if (firstStart == nullptr) {
            snapshot->mBegin = snapshot->mEnd;
        } else {
            snapshot->mBegin = EntryIterator(firstStart);
        }
    }

    // advance fifo reader index to after last entry read.
    mFifoReader->release(snapshot->mEnd - front);

    snapshot->mLost = lost;
    return snapshot;

}

// TODO: move this to PerformanceAnalysis
// TODO: make call to dump periodic so that data in shared FIFO does not get overwritten
void NBLog::Reader::dump(int fd, size_t indent, NBLog::Reader::Snapshot &snapshot)
{
    mFd = fd;
    mIndent = indent;
    String8 timestamp, body;
    // FIXME: this is not thread safe
    // TODO: need a separate instance of performanceAnalysis for each thread
    // used to store data and to call analysis functions
    static ReportPerformance::PerformanceAnalysis performanceAnalysis;
    size_t lost = snapshot.lost() + (snapshot.begin() - EntryIterator(snapshot.data()));
    if (lost > 0) {
        body.appendFormat("warning: lost %zu bytes worth of events", lost);
        // TODO timestamp empty here, only other choice to wait for the first timestamp event in the
        //      log to push it out.  Consider keeping the timestamp/body between calls to copyEntryDataAt().
        dumpLine(timestamp, body);
    }

    for (auto entry = snapshot.begin(); entry != snapshot.end();) {
        switch (entry->type) {
        case EVENT_START_FMT:
            entry = handleFormat(FormatEntry(entry), &timestamp, &body);
            break;
        case EVENT_HISTOGRAM_ENTRY_TS: {
            HistTsEntryWithAuthor *data = (HistTsEntryWithAuthor *) (entry->data);
            // TODO This memcpies are here to avoid unaligned memory access crash.
            // There's probably a more efficient way to do it
            log_hash_t hash;
            memcpy(&hash, &(data->hash), sizeof(hash));
            int64_t ts;
            memcpy(&ts, &data->ts, sizeof(ts));
            performanceAnalysis.logTsEntry(ts);
            ++entry;
            break;
        }
        case EVENT_AUDIO_STATE: {
            performanceAnalysis.handleStateChange();
            ++entry;
            break;
        }
        case EVENT_END_FMT:
            body.appendFormat("warning: got to end format event");
            ++entry;
            break;
        case EVENT_RESERVED:
        default:
            body.appendFormat("warning: unexpected event %d", entry->type);
            ++entry;
            break;
        }
    }
    performanceAnalysis.reportPerformance(&body);
    if (!body.isEmpty()) {
        dumpLine(timestamp, body);
    }
}

void NBLog::Reader::dump(int fd, size_t indent)
{
    // get a snapshot, dump it
    std::unique_ptr<Snapshot> snap = getSnapshot();
    dump(fd, indent, *snap);
}

void NBLog::Reader::dumpLine(const String8 &timestamp, String8 &body)
{
    if (mFd >= 0) {
        dprintf(mFd, "%.*s%s %s\n", mIndent, "", timestamp.string(), body.string());
    } else {
        ALOGI("%.*s%s %s", mIndent, "", timestamp.string(), body.string());
    }
    body.clear();
}

bool NBLog::Reader::isIMemory(const sp<IMemory>& iMemory) const
{
    return iMemory != 0 && mIMemory != 0 && iMemory->pointer() == mIMemory->pointer();
}

// ---------------------------------------------------------------------------

void NBLog::appendTimestamp(String8 *body, const void *data) {
    int64_t ts;
    memcpy(&ts, data, sizeof(ts));
    body->appendFormat("[%d.%03d]", (int) (ts / (1000 * 1000 * 1000)),
                    (int) ((ts / (1000 * 1000)) % 1000));
}

void NBLog::appendInt(String8 *body, const void *data) {
    int x = *((int*) data);
    body->appendFormat("<%d>", x);
}

void NBLog::appendFloat(String8 *body, const void *data) {
    float f;
    memcpy(&f, data, sizeof(float));
    body->appendFormat("<%f>", f);
}

void NBLog::appendPID(String8 *body, const void* data, size_t length) {
    pid_t id = *((pid_t*) data);
    char * name = &((char*) data)[sizeof(pid_t)];
    body->appendFormat("<PID: %d, name: %.*s>", id, (int) (length - sizeof(pid_t)), name);
}

String8 NBLog::bufferDump(const uint8_t *buffer, size_t size)
{
    String8 str;
    str.append("[ ");
    for(size_t i = 0; i < size; i++)
    {
        str.appendFormat("%d ", buffer[i]);
    }
    str.append("]");
    return str;
}

String8 NBLog::bufferDump(const EntryIterator &it)
{
    return bufferDump(it, it->length + Entry::kOverhead);
}

NBLog::EntryIterator NBLog::Reader::handleFormat(const FormatEntry &fmtEntry,
                                                         String8 *timestamp,
                                                         String8 *body) {
    // log timestamp
    int64_t ts = fmtEntry.timestamp();
    timestamp->clear();
    timestamp->appendFormat("[%d.%03d]", (int) (ts / (1000 * 1000 * 1000)),
                    (int) ((ts / (1000 * 1000)) % 1000));

    // log unique hash
    log_hash_t hash = fmtEntry.hash();
    // print only lower 16bit of hash as hex and line as int to reduce spam in the log
    body->appendFormat("%.4X-%d ", (int)(hash >> 16) & 0xFFFF, (int) hash & 0xFFFF);

    // log author (if present)
    handleAuthor(fmtEntry, body);

    // log string
    NBLog::EntryIterator arg = fmtEntry.args();

    const char* fmt = fmtEntry.formatString();
    size_t fmt_length = fmtEntry.formatStringLength();

    for (size_t fmt_offset = 0; fmt_offset < fmt_length; ++fmt_offset) {
        if (fmt[fmt_offset] != '%') {
            body->append(&fmt[fmt_offset], 1); // TODO optimize to write consecutive strings at once
            continue;
        }
        // case "%%""
        if (fmt[++fmt_offset] == '%') {
            body->append("%");
            continue;
        }
        // case "%\0"
        if (fmt_offset == fmt_length) {
            continue;
        }

        NBLog::Event event = (NBLog::Event) arg->type;
        size_t length = arg->length;

        // TODO check length for event type is correct

        if (event == EVENT_END_FMT) {
            break;
        }

        // TODO: implement more complex formatting such as %.3f
        const uint8_t *datum = arg->data; // pointer to the current event args
        switch(fmt[fmt_offset])
        {
        case 's': // string
            ALOGW_IF(event != EVENT_STRING,
                "NBLog Reader incompatible event for string specifier: %d", event);
            body->append((const char*) datum, length);
            break;

        case 't': // timestamp
            ALOGW_IF(event != EVENT_TIMESTAMP,
                "NBLog Reader incompatible event for timestamp specifier: %d", event);
            appendTimestamp(body, datum);
            break;

        case 'd': // integer
            ALOGW_IF(event != EVENT_INTEGER,
                "NBLog Reader incompatible event for integer specifier: %d", event);
            appendInt(body, datum);
            break;

        case 'f': // float
            ALOGW_IF(event != EVENT_FLOAT,
                "NBLog Reader incompatible event for float specifier: %d", event);
            appendFloat(body, datum);
            break;

        case 'p': // pid
            ALOGW_IF(event != EVENT_PID,
                "NBLog Reader incompatible event for pid specifier: %d", event);
            appendPID(body, datum, length);
            break;

        default:
            ALOGW("NBLog Reader encountered unknown character %c", fmt[fmt_offset]);
        }
        ++arg;
    }
    ALOGW_IF(arg->type != EVENT_END_FMT, "Expected end of format, got %d", arg->type);
    ++arg;
    return arg;
}

NBLog::Merger::Merger(const void *shared, size_t size):
      mShared((Shared *) shared),
      mFifo(mShared != NULL ?
        new audio_utils_fifo(size, sizeof(uint8_t),
            mShared->mBuffer, mShared->mRear, NULL /*throttlesFront*/) : NULL),
      mFifoWriter(mFifo != NULL ? new audio_utils_fifo_writer(*mFifo) : NULL)
      {}

void NBLog::Merger::addReader(const NBLog::NamedReader &reader) {
    // FIXME This is called by binder thread in MediaLogService::registerWriter
    //       but the access to shared variable mNamedReaders is not yet protected by a lock.
    mNamedReaders.push_back(reader);
}

// items placed in priority queue during merge
// composed by a timestamp and the index of the snapshot where the timestamp came from
struct MergeItem
{
    int64_t ts;
    int index;
    MergeItem(int64_t ts, int index): ts(ts), index(index) {}
};

// operators needed for priority queue in merge
// bool operator>(const int64_t &t1, const int64_t &t2) {
//     return t1.tv_sec > t2.tv_sec || (t1.tv_sec == t2.tv_sec && t1.tv_nsec > t2.tv_nsec);
// }

bool operator>(const struct MergeItem &i1, const struct MergeItem &i2) {
    return i1.ts > i2.ts || (i1.ts == i2.ts && i1.index > i2.index);
}

// Merge registered readers, sorted by timestamp
void NBLog::Merger::merge() {
    // FIXME This is called by merge thread
    //       but the access to shared variable mNamedReaders is not yet protected by a lock.
    int nLogs = mNamedReaders.size();
    std::vector<std::unique_ptr<NBLog::Reader::Snapshot>> snapshots(nLogs);
    std::vector<NBLog::EntryIterator> offsets(nLogs);
    for (int i = 0; i < nLogs; ++i) {
        snapshots[i] = mNamedReaders[i].reader()->getSnapshot();
        offsets[i] = snapshots[i]->begin();
    }
    // initialize offsets
    // TODO custom heap implementation could allow to update top, improving performance
    // for bursty buffers
    std::priority_queue<MergeItem, std::vector<MergeItem>, std::greater<MergeItem>> timestamps;
    for (int i = 0; i < nLogs; ++i)
    {
        if (offsets[i] != snapshots[i]->end()) {
            int64_t ts = AbstractEntry::buildEntry(offsets[i])->timestamp();
            timestamps.emplace(ts, i);
        }
    }

    while (!timestamps.empty()) {
        // find minimum timestamp
        int index = timestamps.top().index;
        // copy it to the log, increasing offset
        offsets[index] = AbstractEntry::buildEntry(offsets[index])->copyWithAuthor(mFifoWriter,
                                                                                   index);
        // update data structures
        timestamps.pop();
        if (offsets[index] != snapshots[index]->end()) {
            int64_t ts = AbstractEntry::buildEntry(offsets[index])->timestamp();
            timestamps.emplace(ts, index);
        }
    }
}

const std::vector<NBLog::NamedReader>& NBLog::Merger::getNamedReaders() const {
    // FIXME This is returning a reference to a shared variable that needs a lock
    return mNamedReaders;
}

// ---------------------------------------------------------------------------

NBLog::MergeReader::MergeReader(const void *shared, size_t size, Merger &merger)
    : Reader(shared, size), mNamedReaders(merger.getNamedReaders()) {}

void NBLog::MergeReader::handleAuthor(const NBLog::AbstractEntry &entry, String8 *body) {
    int author = entry.author();
    // FIXME Needs a lock
    const char* name = mNamedReaders[author].name();
    body->appendFormat("%s: ", name);
}

// ---------------------------------------------------------------------------

NBLog::MergeThread::MergeThread(NBLog::Merger &merger)
    : mMerger(merger),
      mTimeoutUs(0) {}

NBLog::MergeThread::~MergeThread() {
    // set exit flag, set timeout to 0 to force threadLoop to exit and wait for the thread to join
    requestExit();
    setTimeoutUs(0);
    join();
}

bool NBLog::MergeThread::threadLoop() {
    bool doMerge;
    {
        AutoMutex _l(mMutex);
        // If mTimeoutUs is negative, wait on the condition variable until it's positive.
        // If it's positive, wait kThreadSleepPeriodUs and then merge
        nsecs_t waitTime = mTimeoutUs > 0 ? kThreadSleepPeriodUs * 1000 : LLONG_MAX;
        mCond.waitRelative(mMutex, waitTime);
        doMerge = mTimeoutUs > 0;
        mTimeoutUs -= kThreadSleepPeriodUs;
    }
    if (doMerge) {
        mMerger.merge();
    }
    return true;
}

void NBLog::MergeThread::wakeup() {
    setTimeoutUs(kThreadWakeupPeriodUs);
}

void NBLog::MergeThread::setTimeoutUs(int time) {
    AutoMutex _l(mMutex);
    mTimeoutUs = time;
    mCond.signal();
}

}   // namespace android
