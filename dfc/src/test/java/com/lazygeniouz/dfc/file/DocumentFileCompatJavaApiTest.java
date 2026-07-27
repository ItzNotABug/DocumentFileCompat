package com.lazygeniouz.dfc.file;

import android.provider.DocumentsContract.Document;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DocumentFileCompatJavaApiTest {

    @Test
    public void queryFactoriesAreCallableFromJavaSource() {
        Query[] queries = new Query[] {
            Query.filesOnly(),
            Query.orderByDesc(Document.COLUMN_LAST_MODIFIED),
            Query.in(Document.COLUMN_MIME_TYPE, "image/png", "image/jpeg"),
            Query.notIn(Document.COLUMN_MIME_TYPE, "application/pdf"),
            Query.limit(100),
        };

        assertEquals(5, queries.length);
    }

    @SuppressWarnings("unused")
    private static void listFilesOverloadsAreCallableFromJavaSource(DocumentFileCompat file) {
        file.listFiles();
        file.listFiles(new String[] { Document.COLUMN_DISPLAY_NAME });
        file.listFiles(Query.limit(1));
        file.listFiles(new Query[] { Query.limit(1), Query.filesOnly() });
    }

    @Test
    public void queryListingUsesOnlyListFilesAsPublicName() {
        Method[] methods = DocumentFileCompat.class.getMethods();

        assertFalse(
            Arrays.stream(methods).anyMatch(method -> method.getName().equals("queryFiles"))
        );
        assertTrue(
            Arrays.stream(methods).anyMatch(method ->
                method.getName().equals("listFiles")
                    && Arrays.equals(method.getParameterTypes(), new Class<?>[] { Query[].class })
            )
        );
    }

    @Test
    public void internalCursorHelperIsSyntheticOnJavaSide() {
        Method[] methods = DocumentFileCompat.Companion.getClass().getMethods();

        assertTrue(
            Arrays.stream(methods).anyMatch(method ->
                method.getName().startsWith("makeFromCursor") && method.isSynthetic()
            )
        );
        assertFalse(
            Arrays.stream(methods).anyMatch(method ->
                method.getName().equals("makeFromCursor") && !method.isSynthetic()
            )
        );
    }
}
