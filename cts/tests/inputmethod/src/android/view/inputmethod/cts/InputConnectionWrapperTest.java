/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static com.android.compatibility.common.util.WidgetTestUtils.sameCharSequence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ClipDescription;
import android.net.Uri;
import android.os.Bundle;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputContentInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InputConnectionWrapperTest {
    @Test
    public void testInputConnectionWrapper() {
        InputConnection inputConnection = mock(InputConnection.class);
        doReturn(true).when(inputConnection).commitContent(any(InputContentInfo.class),
                anyInt(), any(Bundle.class));
        InputConnectionWrapper wrapper = new InputConnectionWrapper(null, true);
        try {
            wrapper.beginBatchEdit();
            fail("Failed to throw NullPointerException!");
        } catch (NullPointerException e) {
            // expected
        }
        wrapper.setTarget(inputConnection);

        wrapper.beginBatchEdit();
        verify(inputConnection, times(1)).beginBatchEdit();

        wrapper.clearMetaKeyStates(KeyEvent.META_ALT_ON);
        verify(inputConnection, times(1)).clearMetaKeyStates(KeyEvent.META_ALT_ON);

        wrapper.commitCompletion(new CompletionInfo(1, 1, "testText"));
        ArgumentCaptor<CompletionInfo> completionInfoCaptor =
                ArgumentCaptor.forClass(CompletionInfo.class);
        verify(inputConnection, times(1)).commitCompletion(completionInfoCaptor.capture());
        assertEquals(1, completionInfoCaptor.getValue().getId());
        assertEquals(1, completionInfoCaptor.getValue().getPosition());
        assertEquals("testText", completionInfoCaptor.getValue().getText());

        wrapper.commitCorrection(new CorrectionInfo(0, "oldText", "newText"));
        ArgumentCaptor<CorrectionInfo> correctionInfoCaptor =
                ArgumentCaptor.forClass(CorrectionInfo.class);
        verify(inputConnection, times(1)).commitCorrection(correctionInfoCaptor.capture());
        assertEquals(0, correctionInfoCaptor.getValue().getOffset());
        assertEquals("oldText", correctionInfoCaptor.getValue().getOldText());
        assertEquals("newText", correctionInfoCaptor.getValue().getNewText());

        wrapper.commitText("Text", 1);
        verify(inputConnection, times(1)).commitText(sameCharSequence("Text"), eq(1));

        wrapper.deleteSurroundingText(10, 100);
        verify(inputConnection, times(1)).deleteSurroundingText(10, 100);

        wrapper.deleteSurroundingTextInCodePoints(10, 100);
        verify(inputConnection, times(1)).deleteSurroundingTextInCodePoints(10, 100);

        wrapper.endBatchEdit();
        verify(inputConnection, times(1)).endBatchEdit();

        wrapper.finishComposingText();
        verify(inputConnection, times(1)).finishComposingText();

        wrapper.getCursorCapsMode(TextUtils.CAP_MODE_CHARACTERS);
        verify(inputConnection, times(1)).getCursorCapsMode(TextUtils.CAP_MODE_CHARACTERS);

        wrapper.getExtractedText(new ExtractedTextRequest(), 0);
        verify(inputConnection, times(1)).getExtractedText(any(ExtractedTextRequest.class), eq(0));

        wrapper.getTextAfterCursor(5, 0);
        verify(inputConnection, times(1)).getTextAfterCursor(5, 0);

        wrapper.getTextBeforeCursor(3, 0);
        verify(inputConnection, times(1)).getTextBeforeCursor(3, 0);

        wrapper.performContextMenuAction(1);
        verify(inputConnection, times(1)).performContextMenuAction(1);

        wrapper.performEditorAction(EditorInfo.IME_ACTION_GO);
        verify(inputConnection, times(1)).performEditorAction(EditorInfo.IME_ACTION_GO);

        wrapper.performPrivateCommand("com.android.action.MAIN", new Bundle());
        verify(inputConnection, times(1)).performPrivateCommand(eq("com.android.action.MAIN"),
                any(Bundle.class));

        wrapper.reportFullscreenMode(true);
        verify(inputConnection, times(1)).reportFullscreenMode(true);

        wrapper.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0));
        ArgumentCaptor<KeyEvent> keyEventCaptor = ArgumentCaptor.forClass(KeyEvent.class);
        verify(inputConnection, times(1)).sendKeyEvent(keyEventCaptor.capture());
        assertEquals(KeyEvent.ACTION_DOWN, keyEventCaptor.getValue().getAction());
        assertEquals(KeyEvent.KEYCODE_0, keyEventCaptor.getValue().getKeyCode());

        wrapper.setComposingText("Text", 1);
        verify(inputConnection, times(1)).setComposingText("Text", 1);

        wrapper.setSelection(0, 10);
        verify(inputConnection, times(1)).setSelection(0, 10);

        wrapper.getSelectedText(0);
        verify(inputConnection, times(1)).getSelectedText(0);

        wrapper.setComposingRegion(0, 3);
        verify(inputConnection, times(1)).setComposingRegion(0, 3);

        wrapper.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE);
        verify(inputConnection, times(1))
                .requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE);

        wrapper.closeConnection();
        verify(inputConnection, times(1)).closeConnection();

        verify(inputConnection, never()).getHandler();
        assertNull(wrapper.getHandler());
        verify(inputConnection, times(1)).getHandler();

        verify(inputConnection, never()).commitContent(any(InputContentInfo.class), anyInt(),
                any(Bundle.class));

        final InputContentInfo inputContentInfo = new InputContentInfo(
                Uri.parse("content://com.example/path"),
                new ClipDescription("sample content", new String[]{"image/png"}),
                Uri.parse("https://example.com"));
        wrapper.commitContent(inputContentInfo, 0 /* flags */, null /* opt */);
        verify(inputConnection, times(1)).commitContent(inputContentInfo, 0, null);
    }
}
