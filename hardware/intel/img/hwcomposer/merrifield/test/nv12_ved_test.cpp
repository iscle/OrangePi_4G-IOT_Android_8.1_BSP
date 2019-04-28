/*
// Copyright (c) 2014 Intel Corporation 
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/
#include <gtest/gtest.h>

#include <binder/IMemory.h>

#include <gui/ISurfaceComposer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>
#include <private/gui/ComposerService.h>

#include <utils/String8.h>

using namespace android;
const char * filename = "/data/my_640x480.nv12";
#define PIXEL_FORMAT_NV12 0x7FA00E00

// Fill a YV12 buffer with a multi-colored checkerboard pattern
void fillYUVBuffer(uint8_t* buf, int w, int h, int stride) {
	const int blockWidth = w > 16 ? w / 16 : 1;
	const int blockHeight = h > 16 ? h / 16 : 1;
	const int yuvTexOffsetY = 0;
	int yuvTexStrideY = stride;
	int yuvTexOffsetV = yuvTexStrideY * h;
	int yuvTexStrideV = (yuvTexStrideY / 2 + 0xf) & ~0xf;
	int yuvTexOffsetU = yuvTexOffsetV + yuvTexStrideV * h / 2;
	int yuvTexStrideU = yuvTexStrideV;
	for (int x = 0; x < w; x++) {
		for (int y = 0; y < h; y++) {
			int parityX = (x / blockWidth) & 1;
			int parityY = (y / blockHeight) & 1;
			unsigned char intensity = (parityX ^ parityY) ? 63 : 191;
			buf[yuvTexOffsetY + (y * yuvTexStrideY) + x] = intensity;
			if (x < w / 2 && y < h / 2) {
				buf[yuvTexOffsetU + (y * yuvTexStrideU) + x] = intensity;
				if (x * 2 < w / 2 && y * 2 < h / 2) {
					buf[yuvTexOffsetV + (y * 2 * yuvTexStrideV) + x * 2 + 0] =
							buf[yuvTexOffsetV + (y * 2 * yuvTexStrideV) + x * 2
									+ 1] =
									buf[yuvTexOffsetV
											+ ((y * 2 + 1) * yuvTexStrideV)
											+ x * 2 + 0] = buf[yuvTexOffsetV
											+ ((y * 2 + 1) * yuvTexStrideV)
											+ x * 2 + 1] = intensity;
				}
			}
		}
	}
}

void loadYUVBufferFromFile(uint8_t* buf, int w, int h, int stride) {
	FILE *fp = fopen(filename, "r");
	int line = 0;
	int offset = 0;
	int buffer_height = h * 1.5;

	if (!fp) {
		printf("%s: failed to open %s\n", __func__, filename);
		return;
	}

	printf("buf=%p, w=%d,h=%d,stride=%d\n", buf, w, h, stride);

	for (line = 0; line < buffer_height; line++) {
		printf("reading line %d...\n", line);
		offset = line * stride;
		fread(buf + offset, w, 1, fp);
	}

	fclose(fp);
}

int main(int argc, char **argv) {
	sp < SurfaceControl > sc;
	sp < Surface > s;
	sp < ANativeWindow > anw;
	ANativeWindowBuffer *anb;
	uint8_t* img = NULL;
	sp < SurfaceComposerClient > composerClient = new SurfaceComposerClient;
	if (composerClient->initCheck() != NO_ERROR)
		return 0;

	sc = composerClient->createSurface(String8("FG Test Surface"), 640, 480,
			PIXEL_FORMAT_RGBA_8888, 0);
	if (sc == NULL)
		return 0;;
	if (!sc->isValid())
		return 0;

	s = sc->getSurface();
	anw = s.get();
	if (native_window_set_buffers_geometry(anw.get(), 640, 480,
			PIXEL_FORMAT_NV12) != NO_ERROR)
		return 0;
	if (native_window_set_usage(anw.get(),
			GRALLOC_USAGE_SW_READ_OFTEN | GRALLOC_USAGE_SW_WRITE_OFTEN)
			!= NO_ERROR)
		return 0;
	
	/*
	 * load buffer 
	 */
	if (native_window_dequeue_buffer_and_wait(anw.get(), &anb))
		return 0;
	if (anb == NULL)
		return 0;
	sp <GraphicBuffer> buf(GraphicBuffer::from(anb));
	//if (anw->lockBuffer(anw.get(), buf->getNativeBuffer()) != NO_ERROR)
	//	return 0;
	buf->lock(GRALLOC_USAGE_SW_WRITE_OFTEN, (void**) (&img));
	if (!img) {
		printf("failed to lock buffer\n");
		exit(-1);
	}

	loadYUVBufferFromFile(img, 640, 480, buf->getStride());
	buf->unlock();
	printf("querying buffer...\n");
	if (anw->queueBuffer(anw.get(), buf->getNativeBuffer(), -1) != NO_ERROR)
		return 0;

	// loop it to continuously display??
	while (1) {
		SurfaceComposerClient::openGlobalTransaction();
		if (sc->setLayer(INT_MAX - 1) != NO_ERROR)
			return 0;
		if (sc->show() != NO_ERROR)
			return 0;

		SurfaceComposerClient::closeGlobalTransaction();
	}
	return 0;
}

