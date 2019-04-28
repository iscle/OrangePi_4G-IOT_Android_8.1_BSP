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

package foo.bar.print;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.graphics.pdf.PdfDocument.Page;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;
import android.support.v4.print.PrintHelper;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

/**
 * Simple sample of how to use the print APIs.
 */
public class Main extends Activity {

    public static final String LOG_TAG = "PrintActivity";

    private static final int PAGE_COUNT = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_print:
                printView();
                return true;
            case R.id.menu_print_uri_portrait_force:
            case R.id.menu_print_uri_portrait:
            case R.id.menu_print_uri_landscape:
                try {
                    PrintHelper ph = new PrintHelper(this);
                    Uri uri = null;
                    switch (item.getItemId()) {
                        case R.id.menu_print_uri_portrait_force:
                            ph.setOrientation(PrintHelper.ORIENTATION_PORTRAIT);
                            /* fall through */
                        case R.id.menu_print_uri_portrait:
                            uri = Uri.parse("android.resource://foo.bar.print/raw/portrait");
                            break;
                        case R.id.menu_print_uri_landscape:
                            uri = Uri.parse("android.resource://foo.bar.print/raw/landscape");
                            break;
                    }

                    ph.printBitmap("Print Uri", uri);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void printView() {
        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        final View view = findViewById(R.id.content);

        printManager.print("Print_View",
            new PrintDocumentAdapter() {
                private static final int RESULT_LAYOUT_FAILED = 1;
                private static final int RESULT_LAYOUT_FINISHED = 2;

                private PrintAttributes mPrintAttributes;

                @Override
                public void onLayout(final PrintAttributes oldAttributes,
                        final PrintAttributes newAttributes,
                        final CancellationSignal cancellationSignal,
                        final LayoutResultCallback callback,
                        final Bundle metadata) {

                    new AsyncTask<Void, Void, Integer>() {
                        @Override
                        protected void onPreExecute() {
                            // First register for cancellation requests.
                            cancellationSignal.setOnCancelListener(() -> cancel(true));
                            mPrintAttributes = newAttributes;
                        }

                        @Override
                        protected Integer doInBackground(Void... params) {
                            try {
                                // Pretend we do some layout work.
                                for (int i = 0; i < PAGE_COUNT; i++) {
                                    // Be nice and respond to cancellation.
                                    if (isCancelled()) {
                                        return null;
                                    }
                                    pretendDoingLayoutWork();
                                }
                                return RESULT_LAYOUT_FINISHED;
                            } catch (Exception e) {
                                return RESULT_LAYOUT_FAILED;
                            }
                        }

                        @Override
                        protected void onPostExecute(Integer result) {
                            // The task was not cancelled, so handle the layout result.
                            switch (result) {
                                case RESULT_LAYOUT_FINISHED: {
                                    PrintDocumentInfo info = new PrintDocumentInfo
                                            .Builder("print_view.pdf")
                                            .setContentType(PrintDocumentInfo
                                                    .CONTENT_TYPE_DOCUMENT)
                                            .setPageCount(PAGE_COUNT)
                                            .build();
                                    callback.onLayoutFinished(info, false);
                                } break;

                                case RESULT_LAYOUT_FAILED: {
                                    callback.onLayoutFailed(null);
                                } break;
                            }
                        }

                        @Override
                        protected void onCancelled(Integer result) {
                            // Task was cancelled, report that.
                            callback.onLayoutCancelled();
                        }

                        private void pretendDoingLayoutWork() throws Exception {

                        }
                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
                }

                @Override
                public void onWrite(final PageRange[] pages,
                        final ParcelFileDescriptor destination,
                        final CancellationSignal canclleationSignal,
                        final WriteResultCallback callback) {

                    new AsyncTask<Void, Void, Integer>() {
                        private static final int RESULT_WRITE_FAILED = 1;
                        private static final int RESULT_WRITE_FINISHED = 2;

                        private final SparseIntArray mWrittenPages = new SparseIntArray();
                        private final PrintedPdfDocument mPdfDocument = new PrintedPdfDocument(
                                Main.this, mPrintAttributes);

                        @Override
                        protected void onPreExecute() {
                            // First register for cancellation requests.
                            canclleationSignal.setOnCancelListener(() -> cancel(true));

                            for (int i = 0; i < PAGE_COUNT; i++) {
                                // Be nice and respond to cancellation.
                                if (isCancelled()) {
                                    return;
                                }

                                // Write the page only if it was requested.
                                if (containsPage(pages, i)) {
                                    mWrittenPages.append(mWrittenPages.size(), i);
                                    Page page = mPdfDocument.startPage(i);
                                    // The page of the PDF backed canvas size is in pixels (1/72") and
                                    // smaller that the view. We scale down the drawn content and to
                                    // fit. This does not lead to losing data as PDF is a vector format.
                                    final float scale = (float) Math.min(mPdfDocument.getPageWidth(),
                                            mPdfDocument.getPageHeight()) / Math.max(view.getWidth(), view.getHeight());
                                    page.getCanvas().scale(scale, scale);
                                    view.draw(page.getCanvas());
                                    mPdfDocument.finishPage(page);
                                }
                            }
                        }

                        @Override
                        protected Integer doInBackground(Void... params) {
                            // Write the data and return success or failure.
                            try {
                                mPdfDocument.writeTo(new FileOutputStream(
                                        destination.getFileDescriptor()));
                                return RESULT_WRITE_FINISHED;
                            } catch (IOException ioe) {
                                return RESULT_WRITE_FAILED;
                            }
                        }

                        @Override
                        protected void onPostExecute(Integer result) {
                            // The task was not cancelled, so handle the write result.
                            switch (result) {
                                case RESULT_WRITE_FINISHED: {
                                    PageRange[] pageRanges = computePageRanges(mWrittenPages);
                                    callback.onWriteFinished(pageRanges);
                                } break;

                                case RESULT_WRITE_FAILED: {
                                    callback.onWriteFailed(null);
                                } break;
                            }

                            mPdfDocument.close();
                        }

                        @Override
                        protected void onCancelled(Integer result) {
                            // Task was cancelled, report that.
                            callback.onWriteCancelled();
                            mPdfDocument.close();
                        }
                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
                }

                private PageRange[] computePageRanges(SparseIntArray writtenPages) {
                    List<PageRange> pageRanges = new ArrayList<>();
    
                    int start;
                    int end;
                    final int writtenPageCount = writtenPages.size(); 
                    for (int i = 0; i < writtenPageCount; i++) {
                        start = writtenPages.valueAt(i);
                        int oldEnd = end = start;
                        while (i < writtenPageCount && (end - oldEnd) <= 1) {
                            oldEnd = end;
                            end = writtenPages.valueAt(i);
                            i++;
                        }
                        PageRange pageRange = new PageRange(start, end);
                        pageRanges.add(pageRange);
                    }
    
                    PageRange[] pageRangesArray = new PageRange[pageRanges.size()];
                    pageRanges.toArray(pageRangesArray);
                    return pageRangesArray;
                }

                private boolean containsPage(PageRange[] pageRanges, int page) {
                    final int pageRangeCount = pageRanges.length;
                    for (int i = 0; i < pageRangeCount; i++) {
                        if (pageRanges[i].getStart() <= page
                                && pageRanges[i].getEnd() >= page) {
                            return true;
                        }
                    }
                    return false;
                }
        }, null);
    }
}
