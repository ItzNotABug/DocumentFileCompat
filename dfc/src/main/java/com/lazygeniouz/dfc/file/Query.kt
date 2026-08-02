package com.lazygeniouz.dfc.file

import android.provider.DocumentsContract.Document

/**
 * Query clauses for [DocumentFileCompat.listFiles].
 *
 * For tree-backed SAF directories:
 *
 * - API 21-25: only [Query.select], [Query.orderByAsc], and [Query.orderByDesc] are forwarded.
 * - API 26+: filter queries, [Query.limit], and [Query.offset] are also forwarded.
 *
 * Unsupported clauses are ignored and logged. Providers may still ignore forwarded clauses.
 * Filter values must be null, String, Number, or Boolean. Float and Double values must be finite.
 *
 * When multiple queries are passed to the same `listFiles(...)` call:
 *
 * - [Query.select] clauses are unioned.
 * - top-level filter clauses are joined with AND. Use [Query.anyOf], [Query.allOf], and
 *   [Query.not] for grouped filter logic.
 * - [Query.orderByAsc] and [Query.orderByDesc] clauses are applied in the order passed.
 * - repeated [Query.limit] or [Query.offset] clauses use the last value passed.
 */
class Query private constructor(
    private val spec: Spec,
) {

    private class Spec(
        val projectionColumns: List<String>? = null,
        val sortClause: String? = null,
        val limitCount: Int? = null,
        val offsetCount: Int? = null,
        val selectionPart: Pair<String, List<String>>? = null,
        val description: String,
    )

    companion object {

        /**
         * Fetch the given columns, plus columns required internally to build results.
         *
         * Use this when combining projection with sort, filter, limit, or offset query clauses.
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
            return Query(
                Spec(
                    projectionColumns = columns.toList(),
                    description = "select",
                ),
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
         * If more than one limit is passed to the same `listFiles(...)` call, the last one wins.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun limit(count: Int): Query {
            require(count >= 0) { "limit must be >= 0" }
            return Query(
                Spec(
                    limitCount = count,
                    description = "limit($count)",
                ),
            )
        }

        /**
         * Skip the first [count] child documents.
         *
         * If more than one offset is passed to the same `listFiles(...)` call, the last one wins.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun offset(count: Int): Query {
            require(count >= 0) { "offset must be >= 0" }
            return Query(
                Spec(
                    offsetCount = count,
                    description = "offset($count)",
                ),
            )
        }

        /**
         * Attribute equals [value].
         *
         * A null [value] is compiled as `IS NULL`.
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
         * A null [value] is compiled as `IS NOT NULL`.
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
         * Null values add an `IS NULL` branch.
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
         * Null values add an `IS NOT NULL` guard.
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
         * The pattern is forwarded as-is. Escape literal `%`, `_`, and `\` yourself, or use
         * [nameContains] for display-name contains matching.
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
         * The pattern is forwarded as-is. Escape literal `%`, `_`, and `\` yourself, or use
         * [nameContains] for display-name contains matching.
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
         * Match documents that satisfy every [filter].
         *
         * Only filter queries can be nested. Projection, sort, limit, and offset queries are
         * not accepted.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun allOf(vararg filters: Query): Query {
            return combineFilters("allOf", "AND", filters)
        }

        /**
         * Match documents that satisfy at least one [filter].
         *
         * Only filter queries can be nested. Projection, sort, limit, and offset queries are
         * not accepted.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun anyOf(vararg filters: Query): Query {
            return combineFilters("anyOf", "OR", filters)
        }

        /**
         * Match documents that do not satisfy [filter].
         *
         * Only filter queries can be nested. Projection, sort, limit, and offset queries are
         * not accepted.
         *
         * Forwarded on API 26+.
         */
        @JvmStatic
        fun not(filter: Query): Query {
            val selectionPart = requireFilterQuery(filter, "not")
            return Query(
                Spec(
                    selectionPart = compiledSelection(
                        "(NOT ${selectionPart.first})",
                        selectionPart.second,
                    ),
                    description = "not(${describe(filter)})",
                ),
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
         * SQL LIKE wildcards in [value] are escaped before forwarding.
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
            return query.spec.projectionColumns
        }

        @JvmSynthetic
        internal fun sortClause(query: Query): String? {
            return query.spec.sortClause
        }

        @JvmSynthetic
        internal fun limitCount(query: Query): Int? {
            return query.spec.limitCount
        }

        @JvmSynthetic
        internal fun offsetCount(query: Query): Int? {
            return query.spec.offsetCount
        }

        @JvmSynthetic
        internal fun selectionPart(query: Query): Pair<String, List<String>>? {
            return query.spec.selectionPart
        }

        @JvmSynthetic
        internal fun describe(query: Query): String {
            return query.spec.description
        }

        private fun sortQuery(column: String, descending: Boolean): Query {
            requireAndroidColumnName(column, "column")
            return Query(
                Spec(
                    sortClause = "$column ${if (descending) "DESC" else "ASC"}",
                    description = if (descending) {
                        "orderByDesc($column)"
                    } else {
                        "orderByAsc($column)"
                    },
                ),
            )
        }

        private fun selection(
            attribute: String,
            description: String,
            build: (String) -> Pair<String, List<String>>,
        ): Query {
            requireAndroidColumnName(attribute, "attribute")
            return Query(
                Spec(
                    selectionPart = build(attribute),
                    description = description,
                ),
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

        private fun combineFilters(
            description: String,
            operator: String,
            filters: Array<out Query>,
        ): Query {
            require(filters.isNotEmpty()) { "$description requires at least one filter" }

            val selectionParts = filters.map { filter ->
                requireFilterQuery(filter, description)
            }
            val filterDescription = filters.joinToString { filter -> describe(filter) }
            return Query(
                Spec(
                    selectionPart = compiledSelection(
                        selectionParts.joinToString(
                            separator = " $operator ",
                            prefix = "(",
                            postfix = ")",
                        ) { selectionPart -> selectionPart.first },
                        selectionParts.flatMap { selectionPart -> selectionPart.second },
                    ),
                    description = "$description($filterDescription)",
                ),
            )
        }

        private fun requireFilterQuery(
            query: Query,
            parentDescription: String,
        ): Pair<String, List<String>> {
            return query.spec.selectionPart ?: throw IllegalArgumentException(
                "$parentDescription accepts only filter queries."
            )
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
}
