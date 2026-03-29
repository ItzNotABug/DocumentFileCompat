package com.lazygeniouz.dfc.file

import android.provider.DocumentsContract.Document
import com.lazygeniouz.dfc.file.Query.Companion.limit
import com.lazygeniouz.dfc.file.Query.Companion.offset
import com.lazygeniouz.dfc.file.Query.Companion.orderByAsc
import com.lazygeniouz.dfc.file.Query.Companion.orderByDesc
import com.lazygeniouz.dfc.file.Query.Companion.projection
import com.lazygeniouz.dfc.file.Query.Companion.rawSelection

/**
 * Query clauses for [DocumentFileCompat.listFiles].
 *
 * For tree-backed SAF directories:
 *
 * - API 21-25: only [projection], [orderByAsc], and [orderByDesc] are honored.
 * - API 26+: filter queries, [limit], [offset], and [rawSelection] are also forwarded.
 *
 * Unsupported clauses are ignored and logged.
 */
sealed class Query {

    internal data class Projection(val columns: List<String>) : Query()

    internal data class Sort(val column: String, val descending: Boolean) : Query()

    internal data class Limit(val count: Int) : Query()

    internal data class Offset(val count: Int) : Query()

    internal data class Selection(
        val attribute: String,
        val operator: Operator,
        val values: List<Any?>,
    ) : Query()

    internal data class RawSelection(
        val selection: String,
        val args: List<String>,
    ) : Query()

    internal enum class Operator {
        EQUAL,
        NOT_EQUAL,
        IN,
        NOT_IN,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        BETWEEN,
        IS_NULL,
        IS_NOT_NULL,
        LIKE,
        NOT_LIKE,
    }

    companion object {

        /**
         * Fetch only the given columns.
         *
         * Honored on API 21+.
         */
        @JvmStatic
        fun select(vararg columns: String): Query {
            return Projection(columns.toList())
        }

        /**
         * Fetch only the given columns.
         *
         * Honored on API 21+.
         */
        @Deprecated(
            message = "Use select(...) instead.",
            replaceWith = ReplaceWith("select(*columns)"),
        )
        @JvmStatic
        fun projection(vararg columns: String): Query {
            return select(*columns)
        }

        /**
         * Sort ascending by the given column.
         *
         * Honored on API 21+.
         */
        @JvmStatic
        fun orderByAsc(column: String): Query {
            return Sort(column, descending = false)
        }

        /**
         * Sort descending by the given column.
         *
         * Honored on API 21+.
         */
        @JvmStatic
        fun orderByDesc(column: String): Query {
            return Sort(column, descending = true)
        }

        /**
         * Limit the number of returned child documents.
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun limit(count: Int): Query {
            require(count >= 0) { "limit must be >= 0" }
            return Limit(count)
        }

        /**
         * Skip the first [count] child documents.
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun offset(count: Int): Query {
            require(count >= 0) { "offset must be >= 0" }
            return Offset(count)
        }

        /**
         * Attribute equals [value].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun equal(attribute: String, value: Any?): Query {
            return if (value == null) isNull(attribute) else Selection(
                attribute,
                Operator.EQUAL,
                listOf(value),
            )
        }

        /**
         * Attribute does not equal [value].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun notEqual(attribute: String, value: Any?): Query {
            return if (value == null) isNotNull(attribute) else Selection(
                attribute,
                Operator.NOT_EQUAL,
                listOf(value),
            )
        }

        /**
         * Attribute equals one of [values].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun `in`(attribute: String, vararg values: Any?): Query {
            require(values.isNotEmpty()) { "in requires at least one value" }
            return Selection(attribute, Operator.IN, values.toList())
        }

        /**
         * Attribute does not equal any of [values].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun notIn(attribute: String, vararg values: Any?): Query {
            require(values.isNotEmpty()) { "notIn requires at least one value" }
            return Selection(attribute, Operator.NOT_IN, values.toList())
        }

        /**
         * Attribute is greater than [value].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun greaterThan(attribute: String, value: Any): Query {
            return Selection(attribute, Operator.GREATER_THAN, listOf(value))
        }

        /**
         * Attribute is greater than or equal to [value].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun greaterThanOrEqual(attribute: String, value: Any): Query {
            return Selection(attribute, Operator.GREATER_THAN_OR_EQUAL, listOf(value))
        }

        /**
         * Attribute is less than [value].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun lessThan(attribute: String, value: Any): Query {
            return Selection(attribute, Operator.LESS_THAN, listOf(value))
        }

        /**
         * Attribute is less than or equal to [value].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun lessThanOrEqual(attribute: String, value: Any): Query {
            return Selection(attribute, Operator.LESS_THAN_OR_EQUAL, listOf(value))
        }

        /**
         * Attribute is between [start] and [endInclusive].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun between(attribute: String, start: Any, endInclusive: Any): Query {
            return Selection(attribute, Operator.BETWEEN, listOf(start, endInclusive))
        }

        /**
         * Attribute is null.
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun isNull(attribute: String): Query {
            return Selection(attribute, Operator.IS_NULL, emptyList())
        }

        /**
         * Attribute is not null.
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun isNotNull(attribute: String): Query {
            return Selection(attribute, Operator.IS_NOT_NULL, emptyList())
        }

        /**
         * Attribute matches the SQL LIKE [pattern].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun like(attribute: String, pattern: String): Query {
            return Selection(attribute, Operator.LIKE, listOf(pattern))
        }

        /**
         * Attribute does not match the SQL LIKE [pattern].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun notLike(attribute: String, pattern: String): Query {
            return Selection(attribute, Operator.NOT_LIKE, listOf(pattern))
        }

        /**
         * Pass a raw SQL-style selection expression.
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun rawSelection(selection: String, vararg args: String): Query {
            require(selection.isNotBlank()) { "selection must not be blank" }
            return RawSelection(selection, args.toList())
        }

        /**
         * Exclude directories.
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun filesOnly(): Query {
            return notEqual(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
        }

        /**
         * Include only directories.
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun directoriesOnly(): Query {
            return equal(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
        }

        /**
         * Name equals [value].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun nameEquals(value: String): Query {
            return equal(Document.COLUMN_DISPLAY_NAME, value)
        }

        /**
         * Name contains [value].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun nameContains(value: String): Query {
            return like(
                Document.COLUMN_DISPLAY_NAME,
                "%${escapeLikePattern(value)}%",
            )
        }

        /**
         * MIME type equals [value].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun mimeType(value: String): Query {
            return equal(Document.COLUMN_MIME_TYPE, value)
        }

        /**
         * MIME type equals one of [values].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun mimeTypeIn(vararg values: String): Query {
            return `in`(Document.COLUMN_MIME_TYPE, *values)
        }

        /**
         * Size is greater than [bytes].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun sizeGreaterThan(bytes: Long): Query {
            return greaterThan(Document.COLUMN_SIZE, bytes)
        }

        /**
         * Size is less than [bytes].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun sizeLessThan(bytes: Long): Query {
            return lessThan(Document.COLUMN_SIZE, bytes)
        }

        /**
         * Last modified time is after [timestampMillis].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun lastModifiedAfter(timestampMillis: Long): Query {
            return greaterThan(Document.COLUMN_LAST_MODIFIED, timestampMillis)
        }

        /**
         * Last modified time is before [timestampMillis].
         *
         * Honored on API 26+.
         */
        @JvmStatic
        fun lastModifiedBefore(timestampMillis: Long): Query {
            return lessThan(Document.COLUMN_LAST_MODIFIED, timestampMillis)
        }

        private fun escapeLikePattern(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
        }
    }
}

internal object QueryDefaults {
    val DEFAULT_PROJECTION = listOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_FLAGS,
    )
}

internal data class SelectionPart(
    val selection: String,
    val args: List<String>,
)

internal fun Query.Selection.toSelectionPart(): SelectionPart {
    val attribute = attribute

    return when (operator) {
        Query.Operator.EQUAL -> SelectionPart("($attribute = ?)", listOf(values.first().toSqlArg()))
        Query.Operator.NOT_EQUAL -> SelectionPart(
            "($attribute != ?)",
            listOf(values.first().toSqlArg())
        )

        Query.Operator.IN -> buildInSelection(attribute, values)
        Query.Operator.NOT_IN -> buildNotInSelection(attribute, values)

        Query.Operator.GREATER_THAN ->
            SelectionPart("($attribute > ?)", listOf(values.first().toSqlArg()))

        Query.Operator.GREATER_THAN_OR_EQUAL ->
            SelectionPart("($attribute >= ?)", listOf(values.first().toSqlArg()))

        Query.Operator.LESS_THAN ->
            SelectionPart("($attribute < ?)", listOf(values.first().toSqlArg()))

        Query.Operator.LESS_THAN_OR_EQUAL ->
            SelectionPart("($attribute <= ?)", listOf(values.first().toSqlArg()))

        Query.Operator.BETWEEN -> SelectionPart(
            "($attribute BETWEEN ? AND ?)",
            listOf(values[0].toSqlArg(), values[1].toSqlArg()),
        )

        Query.Operator.IS_NULL -> SelectionPart("($attribute IS NULL)", emptyList())
        Query.Operator.IS_NOT_NULL -> SelectionPart("($attribute IS NOT NULL)", emptyList())
        Query.Operator.LIKE ->
            SelectionPart("($attribute LIKE ? ESCAPE '\\')", listOf(values.first().toSqlArg()))

        Query.Operator.NOT_LIKE ->
            SelectionPart("($attribute NOT LIKE ? ESCAPE '\\')", listOf(values.first().toSqlArg()))
    }
}

private fun buildInSelection(attribute: String, values: List<Any?>): SelectionPart {
    val nonNullValues = values.filterNotNull()
    val hasNull = values.any { it == null }

    return when {
        nonNullValues.isEmpty() && hasNull -> SelectionPart("($attribute IS NULL)", emptyList())
        hasNull -> SelectionPart(
            "(($attribute IN (${nonNullValues.joinToString(",") { "?" }})) OR ($attribute IS NULL))",
            nonNullValues.map { it.toSqlArg() },
        )

        else -> SelectionPart(
            "($attribute IN (${nonNullValues.joinToString(",") { "?" }}))",
            nonNullValues.map { it.toSqlArg() },
        )
    }
}

private fun buildNotInSelection(attribute: String, values: List<Any?>): SelectionPart {
    val nonNullValues = values.filterNotNull()
    val hasNull = values.any { it == null }

    return when {
        nonNullValues.isEmpty() && hasNull -> SelectionPart("($attribute IS NOT NULL)", emptyList())
        hasNull -> SelectionPart(
            "(($attribute NOT IN (${nonNullValues.joinToString(",") { "?" }})) AND ($attribute IS NOT NULL))",
            nonNullValues.map { it.toSqlArg() },
        )

        else -> SelectionPart(
            "($attribute NOT IN (${nonNullValues.joinToString(",") { "?" }}))",
            nonNullValues.map { it.toSqlArg() },
        )
    }
}

private fun Any?.toSqlArg(): String {
    return when (this) {
        null -> "null"
        is Boolean -> if (this) "1" else "0"
        else -> toString()
    }
}

internal fun Query.describe(): String {
    return when (this) {
        is Query.Projection -> "projection"
        is Query.Sort -> if (descending) "orderByDesc($column)" else "orderByAsc($column)"
        is Query.Limit -> "limit($count)"
        is Query.Offset -> "offset($count)"
        is Query.Selection -> when (operator) {
            Query.Operator.EQUAL -> "equal($attribute)"
            Query.Operator.NOT_EQUAL -> "notEqual($attribute)"
            Query.Operator.IN -> "in($attribute)"
            Query.Operator.NOT_IN -> "notIn($attribute)"
            Query.Operator.GREATER_THAN -> "greaterThan($attribute)"
            Query.Operator.GREATER_THAN_OR_EQUAL -> "greaterThanOrEqual($attribute)"
            Query.Operator.LESS_THAN -> "lessThan($attribute)"
            Query.Operator.LESS_THAN_OR_EQUAL -> "lessThanOrEqual($attribute)"
            Query.Operator.BETWEEN -> "between($attribute)"
            Query.Operator.IS_NULL -> "isNull($attribute)"
            Query.Operator.IS_NOT_NULL -> "isNotNull($attribute)"
            Query.Operator.LIKE -> "like($attribute)"
            Query.Operator.NOT_LIKE -> "notLike($attribute)"
        }

        is Query.RawSelection -> "rawSelection"
    }
}
