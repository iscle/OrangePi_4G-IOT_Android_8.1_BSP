package com.android.documentsui.testing;

import static junit.framework.Assert.assertEquals;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;

import java.util.List;

/**
 * Helpers for assertions on {@link DocumentStack}.
 */
public class DocumentStackAsserts {

    private DocumentStackAsserts() {}

    public static void assertEqualsTo(DocumentStack stack, RootInfo root, List<DocumentInfo> docs) {
        assertEquals(root, stack.getRoot());
        assertEquals(docs.size(), stack.size());
        for (int i = 0; i < docs.size(); ++i) {
            assertEquals(docs.get(i), stack.get(i));
        }
    }
}
