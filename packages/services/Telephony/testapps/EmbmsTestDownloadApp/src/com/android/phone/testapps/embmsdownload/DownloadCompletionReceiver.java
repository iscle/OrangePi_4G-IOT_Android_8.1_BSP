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
 * limitations under the License
 */

package com.android.phone.testapps.embmsdownload;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.MbmsDownloadSession;
import android.telephony.mbms.DownloadRequest;
import android.telephony.mbms.FileInfo;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class DownloadCompletionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (EmbmsTestDownloadApp.DOWNLOAD_DONE_ACTION.equals(intent.getAction())) {
            int result = intent.getIntExtra(MbmsDownloadSession.EXTRA_MBMS_DOWNLOAD_RESULT,
                    MbmsDownloadSession.RESULT_CANCELLED);
            if (result != MbmsDownloadSession.RESULT_SUCCESSFUL) {
                EmbmsTestDownloadApp.getInstance().onDownloadFailed(result);
            }
            Uri completedFile = intent.getParcelableExtra(
                    MbmsDownloadSession.EXTRA_MBMS_COMPLETED_FILE_URI);
            FileInfo completedFileInfo = intent.getParcelableExtra(
                    MbmsDownloadSession.EXTRA_MBMS_FILE_INFO);
            DownloadRequest request = intent.getParcelableExtra(
                    MbmsDownloadSession.EXTRA_MBMS_DOWNLOAD_REQUEST);

            Path destinationFile = getDestinationFile(context,
                    request.getFileServiceId(), completedFileInfo);
            Path sourceFile = FileSystems.getDefault().getPath(completedFile.getPath());
            try {
                Files.move(sourceFile, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                return;
            }

            EmbmsTestDownloadApp.getInstance().onDownloadDone(
                    Uri.fromFile(destinationFile.toFile()));
        }
    }

    private Path getDestinationFile(Context context, String serviceId, FileInfo info) {
        try {
            if (serviceId.contains("2")) {
                String fileName = info.getUri().getLastPathSegment();
                Path destination = FileSystems.getDefault()
                        .getPath(context.getFilesDir().getPath(), "images/animals/", fileName)
                        .normalize();
                if (!Files.isDirectory(destination.getParent())) {
                    Files.createDirectory(destination.getParent());
                }
                return destination;
            } else {
                Path destination = FileSystems.getDefault()
                        .getPath(context.getFilesDir().getPath(), "images/image.png")
                        .normalize();
                if (!Files.isDirectory(destination.getParent())) {
                    Files.createDirectory(destination.getParent());
                }
                return destination;
            }
        } catch (IOException e) {
            return null;
        }
    }
}
