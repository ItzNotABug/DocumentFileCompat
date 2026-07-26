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
 * - API 21-25: only [projection], [orderByAsc], and [orderByDesc] are forwarded.
 * - API 26+: filter queries, [limit], [offset], and [rawSelection] are also forwarded.
 *
 * Unsupported clauses are ignored and logged. Providers may still ignore forwarded clauses.
 */
class Query private constructor(
    private val kind: Kind,
    private val columns: List<String>,
    private val sortColumn: String,
    private val sortDescending: Boolean,
    private val count: Int,
    private val attribute: String,
    private val operator: Operator?,
    private val values: List<Any?>,
    private val rawSelectionValue: String,
    private val rawSelectionArgs: List<String>,
) {

    private enum class Kind {
        PROJECTION,
        SORT,
        LIMIT,
        OFFSET,
        SELECTION,
        RAW_SELECTION,
    }

    private enum class Operator {
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

    @JvmSynthetic
    internal fun projectionColumns(): List<String>? {
        return if (kind == Kind.PROJECTION) columns else null
    }

    @JvmSynthetic
    internal fun sortClause(): String? {
        return if (kind == Kind.SORT) {
            "$sortColumn ${if (sortDescending) "DESC" else "ASC"}"
        } else {
            null
        }
    }

    @JvmSynthetic
    internal fun limitCount(): Int? {
        return if (kind == Kind.LIMIT) count else null
    }

    @JvmSynthetic
    internal fun offsetCount(): Int? {
        return if (kind == Kind.OFFSET) count else null
    }

    @JvmSynthetic
    internal fun selectionPart(): SelectionPart? {
        return if (kind == Kind.SELECTION) toSelectionPart() else null
    }

    @JvmSynthetic
    internal fun rawSelectionPart(): SelectionPart? {
        return if (kind == Kind.RAW_SELECTION) {
            SelectionPart("($rawSelectionValue)", rawSelectionArgs)
        } else {
            null
        }
    }

    @JvmSynthetic
    internal fun describe(): String {
        return when (kind) {
            Kind.PROJECTION -> "projection"
            Kind.SORT -> if (sortDescending) "orderByDesc($sortColumn)" else "orderByAsc($sortColumn)"
            Kind.LIMIT -> "limit($count)"
            Kind.OFFSET -> "offset($count)"
            Kind.SELECTION -> when (operator) {
                Operator.EQUAL -> "equal($attribute)"
                Operator.NOT_EQUAL -> "notEqual($attribute)"
                Operator.IN -> "in($attribute)"
                Operator.NOT_IN -> "notIn($attribute)"
                Operator.GREATER_THAN -> "greaterThan($attribute)"
                Operator.GREATER_THAN_OR_EQUAL -> "greaterThanOrEqual($attribute)"
                Operator.LESS_THAN -> "lessThan($attribute)"
                Operator.LESS_THAN_OR_EQUAL -> "lessThanOrEqual($attribute)"
                Operator.BETWEEN -> "between($attribute)"
                Operator.IS_NULL -> "isNull($attribute)"
                Operator.IS_NOT_NULL -> "isNotNull($attribute)"
                Operator.LIKE -> "like($attribute)"
                Operator.NOT_LIKE -> "notLike($attribute)"
                null -> "selection"
            }

            Kind.RAW_SELECTION -> "rawSelection"
        }
    }

    private fun toSelectionPart(): SelectionPart {
        return when (operator) {
            Operator.EQUAL -> SelectionPart("($attribute = ?)", listOf(values.first().toSqlArg()))
            Operator.NOT_EQUAL -> SelectionPart(
                "($attribute != ?)",
                listOf(values.first().toSqlArg()),
            )

            Operator.IN -> buildInSelection(attribute, values)
            Operator.NOT_IN -> buildNotInSelection(attribute, values)

            Operator.GREATER_THAN ->
                SelectionPart("($attribute > ?)", listOf(values.first().toSqlArg()))

            Operator.GREATER_THAN_OR_EQUAL ->
                SelectionPart("($attribute >= ?)", listOf(values.first().toSqlArg()))

            Operator.LESS_THAN ->
                SelectionPart("($attribute < ?)", listOf(values.first().toSqlArg()))

            Operator.LESS_THAN_OR_EQUAL ->
                SelectionPart("($attribute <= ?)", listOf(values.first().toSqlArg()))

            Operator.BETWEEN -> SelectionPart(
                "($attribute BETWEEN ? AND ?)",
                listOf(values[0].toSqlArg(), values[1].toSqlArg()),
            )

            Operator.IS_NULL -> SelectionPart("($attribute IS NULL)", emptyList())
            Operator.IS_NOT_NULL -> SelectionPart("($attribute IS NOT NULL)", emptyList())
            Operator.LIKE ->
                SelectionPart("($attribute LIKE ? ESCAPE '\\')", listOf(values.first().toSqlArg()))

            Operator.NOT_LIKE ->
                SelectionPart("($attribute NOT LIKE ? ESCAPE '\\')", listOf(values.first().toSqlArg()))

            null -> error("Selection query is missing an operator.")
        }
    }

    companion object {

        /**
         * Fetch the given columns, plus columns required internally to build results.
         *
         * Forwarded on API 21+.
         */
        @JvmStatic
        fun select(vararg columns: String): Query {
            return projectionQuery(columns.toList())
        }

        /**
         * Fetch the given columns, plus columns required internally to build results.
         *
         * Forwarded on API 21+.
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
         * Forwarded on API 21+.
         */
        @JvmStatic
        fun orderByAsc(column: String): Query {
            requireAndroidColumnName(column, "column")
            return sortQuery(column, descending = false)
        }

        /**
         * Sort descending by the given column.
         *
         * Forwarded on API 21+.
         */
        @JvmStatic
        fun orderByDesc(column: String): Query {
            requireAndroidColumnName(column, "column")
            return sortQuery(column, descending = true)
        }

        /**
         * Limit the number of returned child documents.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun limit(count: Int): Query {
            require(count >= 0) { "limit must be >= 0" }
            return countQuery(Kind.LIMIT, count)
        }

        /**
         * Skip the first [count] child documents.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun offset(count: Int): Query {
            require(count >= 0) { "offset must be >= 0" }
            return countQuery(Kind.OFFSET, count)
        }

        /**
         * Attribute equals [value].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun equal(attribute: String, value: Any?): Query {
            return if (value == null) isNull(attribute)
            else selection(attribute, Operator.EQUAL, listOf(value))
        }

        /**
         * Attribute does not equal [value].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun notEqual(attribute: String, value: Any?): Query {
            return if (value == null) isNotNull(attribute)
            else selection(attribute, Operator.NOT_EQUAL, listOf(value))
        }

        /**
         * Attribute equals one of [values].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun `in`(attribute: String, vararg values: Any?): Query {
            require(values.isNotEmpty()) { "in requires at least one value" }
            return selection(attribute, Operator.IN, values.toList())
        }

        /**
         * Attribute does not equal any of [values].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun notIn(attribute: String, vararg values: Any?): Query {
            require(values.isNotEmpty()) { "notIn requires at least one value" }
            return selection(attribute, Operator.NOT_IN, values.toList())
        }

        /**
         * Attribute is greater than [value].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun greaterThan(attribute: String, value: Any): Query {
            return selection(attribute, Operator.GREATER_THAN, listOf(value))
        }

        /**
         * Attribute is greater than or equal to [value].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun greaterThanOrEqual(attribute: String, value: Any): Query {
            return selection(attribute, Operator.GREATER_THAN_OR_EQUAL, listOf(value))
        }

        /**
         * Attribute is less than [value].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun lessThan(attribute: String, value: Any): Query {
            return selection(attribute, Operator.LESS_THAN, listOf(value))
        }

        /**
         * Attribute is less than or equal to [value].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun lessThanOrEqual(attribute: String, value: Any): Query {
            return selection(attribute, Operator.LESS_THAN_OR_EQUAL, listOf(value))
        }

        /**
         * Attribute is between [start] and [endInclusive].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun between(attribute: String, start: Any, endInclusive: Any): Query {
            return selection(attribute, Operator.BETWEEN, listOf(start, endInclusive))
        }

        /**
         * Attribute is null.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun isNull(attribute: String): Query {
            return selection(attribute, Operator.IS_NULL, emptyList())
        }

        /**
         * Attribute is not null.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun isNotNull(attribute: String): Query {
            return selection(attribute, Operator.IS_NOT_NULL, emptyList())
        }

        /**
         * Attribute matches the SQL LIKE [pattern].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun like(attribute: String, pattern: String): Query {
            return selection(attribute, Operator.LIKE, listOf(pattern))
        }

        /**
         * Attribute does not match the SQL LIKE [pattern].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun notLike(attribute: String, pattern: String): Query {
            return selection(attribute, Operator.NOT_LIKE, listOf(pattern))
        }

        /**
         * Pass a raw SQL-style selection expression.
         *
         * The selection is forwarded as-is; callers must keep column names trusted.
         * Use this for provider-specific expressions or qualified column references.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun rawSelection(selection: String, vararg args: String): Query {
            require(selection.isNotBlank()) { "selection must not be blank" }
            return rawSelectionQuery(selection, args.toList())
        }

        /**
         * Exclude directories.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun filesOnly(): Query {
            return notEqual(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
        }

        /**
         * Include only directories.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun directoriesOnly(): Query {
            return equal(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
        }

        /**
         * Name equals [value].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun nameEquals(value: String): Query {
            return equal(Document.COLUMN_DISPLAY_NAME, value)
        }

        /**
         * Name contains [value].
         *
         * Forwarded on API 26+.
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
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun mimeType(value: String): Query {
            return equal(Document.COLUMN_MIME_TYPE, value)
        }

        /**
         * MIME type equals one of [values].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun mimeTypeIn(vararg values: String): Query {
            return `in`(Document.COLUMN_MIME_TYPE, *values)
        }

        /**
         * Size is greater than [bytes].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun sizeGreaterThan(bytes: Long): Query {
            return greaterThan(Document.COLUMN_SIZE, bytes)
        }

        /**
         * Size is less than [bytes].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun sizeLessThan(bytes: Long): Query {
            return lessThan(Document.COLUMN_SIZE, bytes)
        }

        /**
         * Last modified time is after [timestampMillis].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun lastModifiedAfter(timestampMillis: Long): Query {
            return greaterThan(Document.COLUMN_LAST_MODIFIED, timestampMillis)
        }

        /**
         * Last modified time is before [timestampMillis].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun lastModifiedBefore(timestampMillis: Long): Query {
            return lessThan(Document.COLUMN_LAST_MODIFIED, timestampMillis)
        }

        private fun projectionQuery(columns: List<String>): Query {
            return Query(
                Kind.PROJECTION,
                columns,
                "",
                false,
                0,
                "",
                null,
                emptyList(),
                "",
                emptyList(),
            )
        }

        private fun sortQuery(column: String, descending: Boolean): Query {
            return Query(
                Kind.SORT,
                emptyList(),
                column,
                descending,
                0,
                "",
                null,
                emptyList(),
                "",
                emptyList(),
            )
        }

        private fun countQuery(kind: Kind, count: Int): Query {
            return Query(
                kind,
                emptyList(),
                "",
                false,
                count,
                "",
                null,
                emptyList(),
                "",
                emptyList(),
            )
        }

        private fun selection(attribute: String, operator: Operator, values: List<Any?>): Query {
            requireAndroidColumnName(attribute, "attribute")
            return Query(
                Kind.SELECTION,
                emptyList(),
                "",
                false,
                0,
                attribute,
                operator,
                values,
                "",
                emptyList(),
            )
        }

        private fun rawSelectionQuery(selection: String, args: List<String>): Query {
            return Query(
                Kind.RAW_SELECTION,
                emptyList(),
                "",
                false,
                0,
                "",
                null,
                emptyList(),
                selection,
                args,
            )
        }

        private fun escapeLikePattern(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
        }
    }
}

private val ANDROID_COLUMN_NAME_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")

private fun requireAndroidColumnName(identifier: String, label: String) {
    require(ANDROID_COLUMN_NAME_PATTERN.matches(identifier)) {
        "$label must be a simple Android contract column name."
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
