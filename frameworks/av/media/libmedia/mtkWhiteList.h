#ifndef MTK_MP3_WHITE_LIST_H
#define MTK_MP3_WHITE_LIST_H

#include <media/IMediaPlayer.h>

namespace android {

int addWriteList(const sp<IMediaPlayer>& player);

}

#endif
