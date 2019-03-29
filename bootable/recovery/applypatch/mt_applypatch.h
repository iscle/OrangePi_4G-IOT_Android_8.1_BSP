/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

#ifndef MT_APPLYPATCH_H_
#define MT_APPLYPATCH_H_

//Tee Update
int LoadTeeContents(const char* filename, FileContents* file);
int TeeUpdate(const char* tee_image, const char* target_filename);

#endif
