/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <stdlib.h>
#include "ota_io.h"
#include "applypatch.h"
#include "mt_applypatch.h"

//TEE update related
int LoadTeeContents(const char* filename, FileContents* file) {

    if (stat(filename, &file->st) != 0) {
        printf("failed to stat \"%s\": %s\n", filename, strerror(errno));
        return -1;
    }

    std::vector<unsigned char> data(file->st.st_size);
    FILE* f = ota_fopen(filename, "rb");
    if (f == NULL) {
        printf("failed to open \"%s\": %s\n", filename, strerror(errno));
        return -1;
    }

    size_t bytes_read = ota_fread(data.data(), 1, data.size(), f);
    if (bytes_read != data.size()) {
        printf("short read of \"%s\" (%zu bytes of %zd)\n", filename, bytes_read, data.size());
        ota_fclose(f);
        return -1;
    }
    ota_fclose(f);

    return 0;
}

int TeeUpdate(const char* tee_image, const char* target_filename) {

    FileContents source_file;

    if (LoadTeeContents(tee_image, &source_file) == 0) {
        if (WriteToPartition(source_file.data.data(), source_file.data.size(), target_filename) != 0) {
            printf("write of patched data to %s failed\n", target_filename);
            return 1;
        }
    }

    // Success!
    return 0;
}
