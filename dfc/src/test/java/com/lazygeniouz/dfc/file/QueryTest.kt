package com.lazygeniouz.dfc.file

import android.provider.DocumentsContract.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryTest {

    @Test
    fun `nameContains escapes sql like wildcards`() {
        val selectionPart = Query.nameContains("100%_done\\ready").selectionPart()!!

        assertEquals(
            "(${Document.COLUMN_DISPLAY_NAME} LIKE ? ESCAPE '\\')",
            selectionPart.selection,
        )
        assertEquals(listOf("%100\\%\\_done\\\\ready%"), selectionPart.args)
    }

    @Test
    fun `in with null adds is null clause`() {
        val selectionPart = Query.`in`(Document.COLUMN_MIME_TYPE, null, "image/png")
            .selectionPart()!!

        assertEquals(
            "((${Document.COLUMN_MIME_TYPE} IN (?)) OR (${Document.COLUMN_MIME_TYPE} IS NULL))",
            selectionPart.selection,
        )
        assertEquals(listOf("image/png"), selectionPart.args)
    }

    @Test
    fun `notIn with null adds is not null clause`() {
        val selectionPart = Query.notIn(Document.COLUMN_MIME_TYPE, null, "image/png")
            .selectionPart()!!

        assertEquals(
            "((${Document.COLUMN_MIME_TYPE} NOT IN (?)) AND (${Document.COLUMN_MIME_TYPE} IS NOT NULL))",
            selectionPart.selection,
        )
        assertEquals(listOf("image/png"), selectionPart.args)
    }

    @Test
    fun `equal with null becomes isNull selection`() {
        val selectionPart = Query.equal(Document.COLUMN_MIME_TYPE, null).selectionPart()!!

        assertEquals("(${Document.COLUMN_MIME_TYPE} IS NULL)", selectionPart.selection)
        assertTrue(selectionPart.args.isEmpty())
    }

    @Test
    fun `notEqual with null becomes isNotNull selection`() {
        val selectionPart = Query.notEqual(Document.COLUMN_MIME_TYPE, null).selectionPart()!!

        assertEquals("(${Document.COLUMN_MIME_TYPE} IS NOT NULL)", selectionPart.selection)
        assertTrue(selectionPart.args.isEmpty())
    }

    @Test
    fun `equal compiles to equality selection`() {
        val selectionPart = Query.equal(Document.COLUMN_DISPLAY_NAME, "report.pdf")
            .selectionPart()!!

        assertEquals("(${Document.COLUMN_DISPLAY_NAME} = ?)", selectionPart.selection)
        assertEquals(listOf("report.pdf"), selectionPart.args)
    }

    @Test
    fun `notEqual compiles to inequality selection`() {
        val selectionPart = Query.notEqual(Document.COLUMN_DISPLAY_NAME, "report.pdf")
            .selectionPart()!!

        assertEquals("(${Document.COLUMN_DISPLAY_NAME} != ?)", selectionPart.selection)
        assertEquals(listOf("report.pdf"), selectionPart.args)
    }

    @Test
    fun `greaterThan compiles correctly`() {
        val selectionPart = Query.greaterThan(Document.COLUMN_SIZE, 1024L).selectionPart()!!

        assertEquals("(${Document.COLUMN_SIZE} > ?)", selectionPart.selection)
        assertEquals(listOf("1024"), selectionPart.args)
    }

    @Test
    fun `greaterThanOrEqual compiles correctly`() {
        val selectionPart = Query.greaterThanOrEqual(Document.COLUMN_SIZE, 1024L)
            .selectionPart()!!

        assertEquals("(${Document.COLUMN_SIZE} >= ?)", selectionPart.selection)
        assertEquals(listOf("1024"), selectionPart.args)
    }

    @Test
    fun `lessThan compiles correctly`() {
        val selectionPart = Query.lessThan(Document.COLUMN_SIZE, 1024L).selectionPart()!!

        assertEquals("(${Document.COLUMN_SIZE} < ?)", selectionPart.selection)
        assertEquals(listOf("1024"), selectionPart.args)
    }

    @Test
    fun `lessThanOrEqual compiles correctly`() {
        val selectionPart = Query.lessThanOrEqual(Document.COLUMN_SIZE, 1024L)
            .selectionPart()!!

        assertEquals("(${Document.COLUMN_SIZE} <= ?)", selectionPart.selection)
        assertEquals(listOf("1024"), selectionPart.args)
    }

    @Test
    fun `between compiles correctly`() {
        val selectionPart = Query.between(Document.COLUMN_SIZE, 10L, 20L).selectionPart()!!

        assertEquals("(${Document.COLUMN_SIZE} BETWEEN ? AND ?)", selectionPart.selection)
        assertEquals(listOf("10", "20"), selectionPart.args)
    }

    @Test
    fun `isNull compiles correctly`() {
        val selectionPart = Query.isNull(Document.COLUMN_MIME_TYPE).selectionPart()!!

        assertEquals("(${Document.COLUMN_MIME_TYPE} IS NULL)", selectionPart.selection)
        assertTrue(selectionPart.args.isEmpty())
    }

    @Test
    fun `isNotNull compiles correctly`() {
        val selectionPart = Query.isNotNull(Document.COLUMN_MIME_TYPE).selectionPart()!!

        assertEquals("(${Document.COLUMN_MIME_TYPE} IS NOT NULL)", selectionPart.selection)
        assertTrue(selectionPart.args.isEmpty())
    }

    @Test
    fun `like compiles correctly`() {
        val selectionPart = Query.like(Document.COLUMN_DISPLAY_NAME, "report%").selectionPart()!!

        assertEquals(
            "(${Document.COLUMN_DISPLAY_NAME} LIKE ? ESCAPE '\\')",
            selectionPart.selection,
        )
        assertEquals(listOf("report%"), selectionPart.args)
    }

    @Test
    fun `notLike compiles correctly`() {
        val selectionPart = Query.notLike(Document.COLUMN_DISPLAY_NAME, "report%")
            .selectionPart()!!

        assertEquals(
            "(${Document.COLUMN_DISPLAY_NAME} NOT LIKE ? ESCAPE '\\')",
            selectionPart.selection,
        )
        assertEquals(listOf("report%"), selectionPart.args)
    }

    @Test
    fun `filesOnly maps to mime type not equal directory`() {
        val selectionPart = Query.filesOnly().selectionPart()!!

        assertEquals("(${Document.COLUMN_MIME_TYPE} != ?)", selectionPart.selection)
        assertEquals(listOf(Document.MIME_TYPE_DIR), selectionPart.args)
    }

    @Test
    fun `directoriesOnly maps to mime type equal directory`() {
        val selectionPart = Query.directoriesOnly().selectionPart()!!

        assertEquals("(${Document.COLUMN_MIME_TYPE} = ?)", selectionPart.selection)
        assertEquals(listOf(Document.MIME_TYPE_DIR), selectionPart.args)
    }

    @Test
    fun `mimeTypeIn maps to in selection`() {
        val selectionPart = Query.mimeTypeIn("image/png", "image/jpeg").selectionPart()!!

        assertEquals("(${Document.COLUMN_MIME_TYPE} IN (?,?))", selectionPart.selection)
        assertEquals(listOf("image/png", "image/jpeg"), selectionPart.args)
    }

    @Test
    fun `select returns projection query`() {
        val query = Query.select(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE)

        assertEquals(
            listOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE),
            query.projectionColumns(),
        )
    }

    @Test
    fun `projection delegates to select`() {
        @Suppress("DEPRECATION")
        val query = Query.projection(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE)

        assertEquals(
            listOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE),
            query.projectionColumns(),
        )
    }

    @Test
    fun `orderByAsc returns ascending sort query`() {
        assertEquals(
            "${Document.COLUMN_DISPLAY_NAME} ASC",
            Query.orderByAsc(Document.COLUMN_DISPLAY_NAME).sortClause(),
        )
    }

    @Test
    fun `orderByDesc returns descending sort query`() {
        assertEquals(
            "${Document.COLUMN_DISPLAY_NAME} DESC",
            Query.orderByDesc(Document.COLUMN_DISPLAY_NAME).sortClause(),
        )
    }

    @Test
    fun `document contract columns are accepted as identifiers`() {
        listOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_ICON,
            Document.COLUMN_SUMMARY,
        ).forEach { column ->
            assertEquals("$column ASC", Query.orderByAsc(column).sortClause())
        }
    }

    @Test
    fun `limit returns limit query`() {
        assertEquals(25, Query.limit(25).limitCount())
    }

    @Test
    fun `offset returns offset query`() {
        assertEquals(10, Query.offset(10).offsetCount())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `limit rejects negative values`() {
        Query.limit(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `offset rejects negative values`() {
        Query.offset(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `in rejects empty values`() {
        Query.`in`(Document.COLUMN_DISPLAY_NAME)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `notIn rejects empty values`() {
        Query.notIn(Document.COLUMN_DISPLAY_NAME)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rawSelection rejects blank selection`() {
        Query.rawSelection("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `orderByAsc rejects unsafe column name`() {
        Query.orderByAsc("${Document.COLUMN_DISPLAY_NAME}; DROP TABLE documents")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `selection rejects unsafe attribute name`() {
        Query.equal("${Document.COLUMN_DISPLAY_NAME}) OR 1=1 --", "report.pdf")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `orderByAsc rejects qualified column name`() {
        Query.orderByAsc("documents.${Document.COLUMN_DISPLAY_NAME}")
    }
}
