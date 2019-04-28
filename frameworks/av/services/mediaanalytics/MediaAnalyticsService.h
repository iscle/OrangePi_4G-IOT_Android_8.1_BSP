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


#ifndef ANDROID_MEDIAANALYTICSSERVICE_H
#define ANDROID_MEDIAANALYTICSSERVICE_H

#include <arpa/inet.h>

#include <utils/threads.h>
#include <utils/Errors.h>
#include <utils/KeyedVector.h>
#include <utils/String8.h>
#include <utils/List.h>

#include <media/IMediaAnalyticsService.h>

#include "MetricsSummarizer.h"


namespace android {

class MediaAnalyticsService : public BnMediaAnalyticsService
{

 public:

    // on this side, caller surrenders ownership
    virtual int64_t submit(MediaAnalyticsItem *item, bool forcenew);

    static  void            instantiate();
    virtual status_t        dump(int fd, const Vector<String16>& args);

                            MediaAnalyticsService();
    virtual                 ~MediaAnalyticsService();

 private:
    MediaAnalyticsItem::SessionID_t generateUniqueSessionID();

    // statistics about our analytics
    int64_t mItemsSubmitted;
    int64_t mItemsFinalized;
    int64_t mItemsDiscarded;
    int64_t mItemsDiscardedExpire;
    int64_t mItemsDiscardedCount;
    int64_t mSetsDiscarded;
    MediaAnalyticsItem::SessionID_t mLastSessionID;

    // partitioned a bit so we don't over serialize
    mutable Mutex           mLock;
    mutable Mutex           mLock_ids;
    mutable Mutex           mLock_mappings;

    //mtkadd+
    mutable Mutex           mBigLock;

    // limit how many records we'll retain
    // by count (in each queue (open, finalized))
    int32_t mMaxRecords;
    // by time (none older than this long agan
    nsecs_t mMaxRecordAgeNs;
    //
    // # of sets of summaries
    int32_t mMaxRecordSets;
    // nsecs until we start a new record set
    nsecs_t mNewSetInterval;

    // input validation after arrival from client
    bool contentValid(MediaAnalyticsItem *item, bool isTrusted);
    bool rateLimited(MediaAnalyticsItem *);

    // the ones that are still open
    // (newest at front) since we keep looking for them
    List<MediaAnalyticsItem *> *mOpen;
    // the ones we've finalized
    // (oldest at front) so it prints nicely for dumpsys
    List<MediaAnalyticsItem *> *mFinalized;
    // searching within these queues: queue, key
    MediaAnalyticsItem *findItem(List<MediaAnalyticsItem *> *,
                                     MediaAnalyticsItem *, bool removeit);

    // summarizers
    void summarize(MediaAnalyticsItem *item);
    class SummarizerSet {
        nsecs_t mStarted;
        List<MetricsSummarizer *> *mSummarizers;

      public:
        void appendSummarizer(MetricsSummarizer *s) {
            if (s) {
                mSummarizers->push_back(s);
            }
        };
        nsecs_t getStarted() { return mStarted;}
        void setStarted(nsecs_t started) {mStarted = started;}
        List<MetricsSummarizer *> *getSummarizers() { return mSummarizers;}

        SummarizerSet();
        ~SummarizerSet();
    };
    void newSummarizerSet();
    List<SummarizerSet *> *mSummarizerSets;
    SummarizerSet *mCurrentSet;
    List<MetricsSummarizer *> *getFirstSet() {
        List<SummarizerSet *>::iterator first = mSummarizerSets->begin();
        if (first != mSummarizerSets->end()) {
            return (*first)->getSummarizers();
        }
        return NULL;
    }

    void saveItem(MediaAnalyticsItem);
    void saveItem(List<MediaAnalyticsItem *> *, MediaAnalyticsItem *, int);
    void deleteItem(List<MediaAnalyticsItem *> *, MediaAnalyticsItem *);

    // support for generating output
    int mDumpProto;
    String8 dumpQueue(List<MediaAnalyticsItem*> *);
    String8 dumpQueue(List<MediaAnalyticsItem*> *, nsecs_t, const char *only);

    void dumpHeaders(String8 &result, nsecs_t ts_since);
    void dumpSummaries(String8 &result, nsecs_t ts_since, const char * only);
    void dumpRecent(String8 &result, nsecs_t ts_since, const char * only);

    // mapping uids to package names
    struct UidToPkgMap {
        uid_t uid;
        AString pkg;
        AString installer;
        int32_t versionCode;
        nsecs_t expiration;
    };

    KeyedVector<uid_t,struct UidToPkgMap>  mPkgMappings;
    void setPkgInfo(MediaAnalyticsItem *item, uid_t uid, bool setName, bool setVersion);

};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_MEDIAANALYTICSSERVICE_H
