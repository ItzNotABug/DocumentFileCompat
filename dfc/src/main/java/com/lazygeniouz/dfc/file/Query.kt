package com.lazygeniouz.dfc.file

import android.provider.DocumentsContract.Document
import com.lazygeniouz.dfc.file.Query.Companion.limit
import com.lazygeniouz.dfc.file.Query.Companion.offset
import com.lazygeniouz.dfc.file.Query.Companion.orderByAsc
import com.lazygeniouz.dfc.file.Query.Companion.orderByDesc
import com.lazygeniouz.dfc.file.Query.Companion.rawSelection

/**
 * Query clauses for [DocumentFileCompat.listFiles].
 *
 * For tree-backed SAF directories:
 *
 * - API 21-25: only [select], [orderByAsc], and [orderByDesc] are forwarded.
 * - API 26+: filter queries, [limit], [offset], and [rawSelection] are also forwarded.
 *
 * Unsupported clauses are ignored and logged. Providers may still ignore forwarded clauses.
 * Filter values must be null, String, Number, or Boolean.
 */
sealed class Query private constructor() {

    companion object {

        /**
         * Fetch the given columns, plus columns required internally to build results.
         *
         * Forwarded on API 21+.
         */
        @JvmStatic
        fun select(vararg columns: String): Query {
            require(columns.isNotEmpty()) { "select requires at least one column" }
            columns.forEach { column -> requireAndroidColumnName(column, "column") }
            return projection(*columns)
        }

        @JvmSynthetic
        internal fun projection(vararg columns: String): Query {
            return QuerySpec(
                projectionColumnsValue = columns.toList(),
                description = "select",
            )
        }

        /**
         * Sort ascending by the given column.
         *
         * Forwarded on API 21+.
         */
        @JvmStatic
        fun orderByAsc(column: String): Query {
            return sortQuery(column, descending = false)
        }

        /**
         * Sort descending by the given column.
         *
         * Forwarded on API 21+.
         */
        @JvmStatic
        fun orderByDesc(column: String): Query {
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
            return QuerySpec(
                limitCountValue = count,
                description = "limit($count)",
            )
        }

        /**
         * Skip the first [count] child documents.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun offset(count: Int): Query {
            require(count >= 0) { "offset must be >= 0" }
            return QuerySpec(
                offsetCountValue = count,
                description = "offset($count)",
            )
        }

        /**
         * Attribute equals [value].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun equal(attribute: String, value: Any?): Query {
            return if (value == null) isNull(attribute)
            else selection(attribute, "equal($attribute)") { column ->
                compiledSelection("($column = ?)", listOf(value.toSqlArg()))
            }
        }

        /**
         * Attribute does not equal [value].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun notEqual(attribute: String, value: Any?): Query {
            return if (value == null) isNotNull(attribute)
            else selection(attribute, "notEqual($attribute)") { column ->
                compiledSelection("($column != ?)", listOf(value.toSqlArg()))
            }
        }

        /**
         * Attribute equals one of [values].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun `in`(attribute: String, vararg values: Any?): Query {
            require(values.isNotEmpty()) { "in requires at least one value" }
            return selection(attribute, "in($attribute)") { column ->
                buildInSelection(column, values.toList())
            }
        }

        /**
         * Attribute does not equal any of [values].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun notIn(attribute: String, vararg values: Any?): Query {
            require(values.isNotEmpty()) { "notIn requires at least one value" }
            return selection(attribute, "notIn($attribute)") { column ->
                buildNotInSelection(column, values.toList())
            }
        }

        /**
         * Attribute is greater than [value].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun greaterThan(attribute: String, value: Any): Query {
            return selection(attribute, "greaterThan($attribute)") { column ->
                compiledSelection("($column > ?)", listOf(value.toSqlArg()))
            }
        }

        /**
         * Attribute is greater than or equal to [value].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun greaterThanOrEqual(attribute: String, value: Any): Query {
            return selection(attribute, "greaterThanOrEqual($attribute)") { column ->
                compiledSelection("($column >= ?)", listOf(value.toSqlArg()))
            }
        }

        /**
         * Attribute is less than [value].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun lessThan(attribute: String, value: Any): Query {
            return selection(attribute, "lessThan($attribute)") { column ->
                compiledSelection("($column < ?)", listOf(value.toSqlArg()))
            }
        }

        /**
         * Attribute is less than or equal to [value].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun lessThanOrEqual(attribute: String, value: Any): Query {
            return selection(attribute, "lessThanOrEqual($attribute)") { column ->
                compiledSelection("($column <= ?)", listOf(value.toSqlArg()))
            }
        }

        /**
         * Attribute is between [start] and [endInclusive].
         *
         * Numeric ranges require [start] <= [endInclusive].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun between(attribute: String, start: Any, endInclusive: Any): Query {
            requireBetweenOrder(start, endInclusive)
            return selection(attribute, "between($attribute)") { column ->
                compiledSelection(
                    "($column BETWEEN ? AND ?)",
                    listOf(start.toSqlArg(), endInclusive.toSqlArg()),
                )
            }
        }

        /**
         * Attribute is null.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun isNull(attribute: String): Query {
            return selection(attribute, "isNull($attribute)") { column ->
                compiledSelection("($column IS NULL)", emptyList())
            }
        }

        /**
         * Attribute is not null.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun isNotNull(attribute: String): Query {
            return selection(attribute, "isNotNull($attribute)") { column ->
                compiledSelection("($column IS NOT NULL)", emptyList())
            }
        }

        /**
         * Attribute matches the SQL LIKE [pattern].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun like(attribute: String, pattern: String): Query {
            return selection(attribute, "like($attribute)") { column ->
                compiledSelection("($column LIKE ? ESCAPE '\\')", listOf(pattern))
            }
        }

        /**
         * Attribute does not match the SQL LIKE [pattern].
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun notLike(attribute: String, pattern: String): Query {
            return selection(attribute, "notLike($attribute)") { column ->
                compiledSelection("($column NOT LIKE ? ESCAPE '\\')", listOf(pattern))
            }
        }

        /**
         * Pass a raw SQL-style selection expression.
         *
         * The selection is grouped and forwarded as a SQL selection clause.
         * Callers must keep column names trusted.
         * Use this for provider-specific expressions or qualified column references.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun rawSelection(selection: String, vararg args: String): Query {
            require(selection.isNotBlank()) { "selection must not be blank" }
            return QuerySpec(
                rawSelectionPartValue = compiledSelection("($selection)", args.toList()),
                description = "rawSelection",
            )
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

        @JvmSynthetic
        internal fun projectionColumns(query: Query): List<String>? {
            return query.asSpec().projectionColumnsValue
        }

        @JvmSynthetic
        internal fun sortClause(query: Query): String? {
            return query.asSpec().sortClauseValue
        }

        @JvmSynthetic
        internal fun limitCount(query: Query): Int? {
            return query.asSpec().limitCountValue
        }

        @JvmSynthetic
        internal fun offsetCount(query: Query): Int? {
            return query.asSpec().offsetCountValue
        }

        @JvmSynthetic
        internal fun selectionPart(query: Query): Pair<String, List<String>>? {
            return query.asSpec().selectionPartValue
        }

        @JvmSynthetic
        internal fun rawSelectionPart(query: Query): Pair<String, List<String>>? {
            return query.asSpec().rawSelectionPartValue
        }

        @JvmSynthetic
        internal fun describe(query: Query): String {
            return query.asSpec().description
        }

        private fun Query.asSpec(): QuerySpec {
            return this as QuerySpec
        }

        private fun sortQuery(column: String, descending: Boolean): Query {
            requireAndroidColumnName(column, "column")
            return QuerySpec(
                sortClauseValue = "$column ${if (descending) "DESC" else "ASC"}",
                description = if (descending) "orderByDesc($column)" else "orderByAsc($column)",
            )
        }

        private fun selection(
            attribute: String,
            description: String,
            build: (String) -> Pair<String, List<String>>,
        ): Query {
            requireAndroidColumnName(attribute, "attribute")
            return QuerySpec(
                selectionPartValue = build(attribute),
                description = description,
            )
        }

        private fun escapeLikePattern(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
        }

        private val androidColumnNamePattern = Regex("[A-Za-z_][A-Za-z0-9_]*")

        private fun requireAndroidColumnName(identifier: String, label: String) {
            require(androidColumnNamePattern.matches(identifier)) {
                "$label must be a simple Android contract column name."
            }
        }

        private fun buildInSelection(
            attribute: String,
            values: List<Any?>,
        ): Pair<String, List<String>> {
            val nonNullValues = values.filterNotNull()
            val hasNull = values.any { it == null }

            return when {
                nonNullValues.isEmpty() && hasNull -> compiledSelection(
                    "($attribute IS NULL)",
                    emptyList(),
                )

                hasNull -> compiledSelection(
                    "(($attribute IN (${nonNullValues.joinToString(",") { "?" }})) OR ($attribute IS NULL))",
                    nonNullValues.map { it.toSqlArg() },
                )

                else -> compiledSelection(
                    "($attribute IN (${nonNullValues.joinToString(",") { "?" }}))",
                    nonNullValues.map { it.toSqlArg() },
                )
            }
        }

        private fun buildNotInSelection(
            attribute: String,
            values: List<Any?>,
        ): Pair<String, List<String>> {
            val nonNullValues = values.filterNotNull()
            val hasNull = values.any { it == null }

            return when {
                nonNullValues.isEmpty() && hasNull -> compiledSelection(
                    "($attribute IS NOT NULL)",
                    emptyList(),
                )

                hasNull -> compiledSelection(
                    "(($attribute NOT IN (${nonNullValues.joinToString(",") { "?" }})) AND ($attribute IS NOT NULL))",
                    nonNullValues.map { it.toSqlArg() },
                )

                else -> compiledSelection(
                    "($attribute NOT IN (${nonNullValues.joinToString(",") { "?" }}))",
                    nonNullValues.map { it.toSqlArg() },
                )
            }
        }

        private fun compiledSelection(
            selection: String,
            args: List<String>,
        ): Pair<String, List<String>> {
            return selection to args
        }

        private fun Any?.toSqlArg(): String {
            return when (this) {
                null -> "null"
                is String -> this
                is Boolean -> if (this) "1" else "0"
                is Float -> {
                    require(isFinite()) { "query value must be finite." }
                    toString()
                }

                is Double -> {
                    require(isFinite()) { "query value must be finite." }
                    toString()
                }

                is Number -> toString()
                else -> throw IllegalArgumentException(
                    "query value must be null, String, Number, or Boolean."
                )
            }
        }

        private fun requireBetweenOrder(start: Any, endInclusive: Any) {
            if (start !is Number || endInclusive !is Number) return

            val startValue = start.toSqlArg().toBigDecimal()
            val endValue = endInclusive.toSqlArg().toBigDecimal()
            require(startValue <= endValue) {
                "between start must be <= endInclusive."
            }
        }
    }

    private class QuerySpec(
        val projectionColumnsValue: List<String>? = null,
        val sortClauseValue: String? = null,
        val limitCountValue: Int? = null,
        val offsetCountValue: Int? = null,
        val selectionPartValue: Pair<String, List<String>>? = null,
        val rawSelectionPartValue: Pair<String, List<String>>? = null,
        val description: String,
    ) : Query()
}
