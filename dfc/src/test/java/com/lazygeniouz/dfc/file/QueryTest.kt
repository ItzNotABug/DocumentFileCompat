package com.lazygeniouz.dfc.file

import android.provider.DocumentsContract.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryTest {

    @Test
    fun `nameContains escapes sql like wildcards`() {
        val query = Query.nameContains("100%_done\\ready") as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals(
            "(${Document.COLUMN_DISPLAY_NAME} LIKE ? ESCAPE '\\')",
            selectionPart.selection,
        )
        assertEquals(listOf("%100\\%\\_done\\\\ready%"), selectionPart.args)
    }

    @Test
    fun `in with null adds is null clause`() {
        val query = Query.`in`(Document.COLUMN_MIME_TYPE, null, "image/png") as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals(
            "((${Document.COLUMN_MIME_TYPE} IN (?)) OR (${Document.COLUMN_MIME_TYPE} IS NULL))",
            selectionPart.selection,
        )
        assertEquals(listOf("image/png"), selectionPart.args)
    }

    @Test
    fun `notIn with null adds is not null clause`() {
        val query = Query.notIn(Document.COLUMN_MIME_TYPE, null, "image/png") as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals(
            "((${Document.COLUMN_MIME_TYPE} NOT IN (?)) AND (${Document.COLUMN_MIME_TYPE} IS NOT NULL))",
            selectionPart.selection,
        )
        assertEquals(listOf("image/png"), selectionPart.args)
    }

    @Test
    fun `equal with null becomes isNull selection`() {
        val query = Query.equal(Document.COLUMN_MIME_TYPE, null) as Query.Selection

        assertEquals(Query.Operator.IS_NULL, query.operator)
        assertTrue(query.values.isEmpty())
    }

    @Test
    fun `notEqual with null becomes isNotNull selection`() {
        val query = Query.notEqual(Document.COLUMN_MIME_TYPE, null) as Query.Selection

        assertEquals(Query.Operator.IS_NOT_NULL, query.operator)
        assertTrue(query.values.isEmpty())
    }

    @Test
    fun `equal compiles to equality selection`() {
        val query = Query.equal(Document.COLUMN_DISPLAY_NAME, "report.pdf") as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals("(${Document.COLUMN_DISPLAY_NAME} = ?)", selectionPart.selection)
        assertEquals(listOf("report.pdf"), selectionPart.args)
    }

    @Test
    fun `notEqual compiles to inequality selection`() {
        val query = Query.notEqual(Document.COLUMN_DISPLAY_NAME, "report.pdf") as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals("(${Document.COLUMN_DISPLAY_NAME} != ?)", selectionPart.selection)
        assertEquals(listOf("report.pdf"), selectionPart.args)
    }

    @Test
    fun `greaterThan compiles correctly`() {
        val query = Query.greaterThan(Document.COLUMN_SIZE, 1024L) as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals("(${Document.COLUMN_SIZE} > ?)", selectionPart.selection)
        assertEquals(listOf("1024"), selectionPart.args)
    }

    @Test
    fun `greaterThanOrEqual compiles correctly`() {
        val query = Query.greaterThanOrEqual(Document.COLUMN_SIZE, 1024L) as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals("(${Document.COLUMN_SIZE} >= ?)", selectionPart.selection)
        assertEquals(listOf("1024"), selectionPart.args)
    }

    @Test
    fun `lessThan compiles correctly`() {
        val query = Query.lessThan(Document.COLUMN_SIZE, 1024L) as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals("(${Document.COLUMN_SIZE} < ?)", selectionPart.selection)
        assertEquals(listOf("1024"), selectionPart.args)
    }

    @Test
    fun `lessThanOrEqual compiles correctly`() {
        val query = Query.lessThanOrEqual(Document.COLUMN_SIZE, 1024L) as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals("(${Document.COLUMN_SIZE} <= ?)", selectionPart.selection)
        assertEquals(listOf("1024"), selectionPart.args)
    }

    @Test
    fun `between compiles correctly`() {
        val query = Query.between(Document.COLUMN_SIZE, 10L, 20L) as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals("(${Document.COLUMN_SIZE} BETWEEN ? AND ?)", selectionPart.selection)
        assertEquals(listOf("10", "20"), selectionPart.args)
    }

    @Test
    fun `isNull compiles correctly`() {
        val query = Query.isNull(Document.COLUMN_MIME_TYPE) as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals("(${Document.COLUMN_MIME_TYPE} IS NULL)", selectionPart.selection)
        assertTrue(selectionPart.args.isEmpty())
    }

    @Test
    fun `isNotNull compiles correctly`() {
        val query = Query.isNotNull(Document.COLUMN_MIME_TYPE) as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals("(${Document.COLUMN_MIME_TYPE} IS NOT NULL)", selectionPart.selection)
        assertTrue(selectionPart.args.isEmpty())
    }

    @Test
    fun `like compiles correctly`() {
        val query = Query.like(Document.COLUMN_DISPLAY_NAME, "report%") as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals(
            "(${Document.COLUMN_DISPLAY_NAME} LIKE ? ESCAPE '\\')",
            selectionPart.selection,
        )
        assertEquals(listOf("report%"), selectionPart.args)
    }

    @Test
    fun `notLike compiles correctly`() {
        val query = Query.notLike(Document.COLUMN_DISPLAY_NAME, "report%") as Query.Selection

        val selectionPart = query.toSelectionPart()

        assertEquals(
            "(${Document.COLUMN_DISPLAY_NAME} NOT LIKE ? ESCAPE '\\')",
            selectionPart.selection,
        )
        assertEquals(listOf("report%"), selectionPart.args)
    }

    @Test
    fun `filesOnly maps to mime type not equal directory`() {
        val query = Query.filesOnly() as Query.Selection

        assertEquals(Document.COLUMN_MIME_TYPE, query.attribute)
        assertEquals(Query.Operator.NOT_EQUAL, query.operator)
        assertEquals(listOf(Document.MIME_TYPE_DIR), query.values)
    }

    @Test
    fun `directoriesOnly maps to mime type equal directory`() {
        val query = Query.directoriesOnly() as Query.Selection

        assertEquals(Document.COLUMN_MIME_TYPE, query.attribute)
        assertEquals(Query.Operator.EQUAL, query.operator)
        assertEquals(listOf(Document.MIME_TYPE_DIR), query.values)
    }

    @Test
    fun `mimeTypeIn maps to in selection`() {
        val query = Query.mimeTypeIn("image/png", "image/jpeg") as Query.Selection

        assertEquals(Document.COLUMN_MIME_TYPE, query.attribute)
        assertEquals(Query.Operator.IN, query.operator)
        assertEquals(listOf("image/png", "image/jpeg"), query.values)
    }

    @Test
    fun `select returns projection query`() {
        val query = Query.select(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE)
                as Query.Projection

        assertEquals(
            listOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE),
            query.columns,
        )
    }

    @Test
    fun `projection delegates to select`() {
        @Suppress("DEPRECATION")
        val query = Query.projection(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE)
                as Query.Projection

        assertEquals(
            listOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE),
            query.columns,
        )
    }

    @Test
    fun `orderByAsc returns ascending sort query`() {
        val query = Query.orderByAsc(Document.COLUMN_DISPLAY_NAME) as Query.Sort

        assertEquals(Document.COLUMN_DISPLAY_NAME, query.column)
        assertFalse(query.descending)
    }

    @Test
    fun `orderByDesc returns descending sort query`() {
        val query = Query.orderByDesc(Document.COLUMN_DISPLAY_NAME) as Query.Sort

        assertEquals(Document.COLUMN_DISPLAY_NAME, query.column)
        assertTrue(query.descending)
    }

    @Test
    fun `limit returns limit query`() {
        val query = Query.limit(25) as Query.Limit

        assertEquals(25, query.count)
    }

    @Test
    fun `offset returns offset query`() {
        val query = Query.offset(10) as Query.Offset

        assertEquals(10, query.count)
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
}
