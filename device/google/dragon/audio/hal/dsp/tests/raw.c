/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

float *read_raw(const char *filename, size_t *frames)
{
	struct stat st;
	int16_t *buf;
	size_t f, n;
	int fd;
	float *data;
	int i;

	if (stat(filename, &st) < 0) {
		fprintf(stderr, "cannot stat file %s\n", filename);
		return NULL;
	}

	fd = open(filename, O_RDONLY);
	if (fd < 0) {
		fprintf(stderr, "cannot open file %s\n", filename);
		return NULL;
	}

	f = st.st_size / 4;
	n = f * 4;
	buf = (int16_t *)malloc(n);
	if (read(fd, buf, n) != n) {
		fprintf(stderr, "short read %zu\n", n);
		free(buf);
		return NULL;
	}
	close(fd);

	/* deinterleave and convert to float */
	data = (float *)malloc(sizeof(float) * f * 2);
	for (i = 0; i < f; i++) {
		data[i] = buf[2*i] / 32768.0f;
		data[i + f] = buf[2*i+1] / 32768.0f;
	}
	free(buf);
	*frames = f;
	return data;
}

static int16_t f2s16(float f)
{
	int i;
	f *= 32768;
	i = (int)((f > 0) ? (f + 0.5f) : (f - 0.5f));
	if (i < -32768)
		i = -32768;
	else if (i > 32767)
		i = 32767;
	return (int16_t)i;
}

int write_raw(const char *filename, float *input, size_t frames)
{
	int16_t *buf;
	int rc = -1;
	int n = frames * 4;
	int i;

	buf = (int16_t *)malloc(n);
	for (i = 0; i < frames; i++) {
		buf[2*i] = f2s16(input[i]);
		buf[2*i+1] = f2s16(input[i + frames]);
	}

	int fd = open(filename, O_WRONLY | O_CREAT, 0644);
	if (fd < 0) {
		fprintf(stderr, "cannot open file %s\n", filename);
		goto quit;
	}
	if (write(fd, buf, n) != n) {
		fprintf(stderr, "short write file %s\n", filename);
		goto quit;
	}
	rc = 0;
quit:
	close(fd);
	free(buf);
	return rc;
}
