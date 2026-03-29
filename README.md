# DocumentFileCompat

AndroidX `DocumentFile`, without the usual performance tax.

`DocumentFileCompat` is a faster, practical alternative to AndroidX's `DocumentFile` for working
with SAF tree and document Uris. It reduces repeated `ContentResolver` lookups by fetching the
metadata you actually need up front, so directory listing and file inspection stay usable even in
large folders.

### The Problem with DocumentFile

`DocumentFile` is convenient, but it can get painfully slow.

For many common operations, it repeatedly queries `ContentResolver`. That cost adds up fast when
you:

- list large directories
- call `findFile()`
- read names, sizes, MIME types, and timestamps for many children
- build your own file models from SAF results

### Why DocumentFileCompat

`DocumentFileCompat` keeps the API familiar, but fetches relevant file metadata in a single pass
when possible.

That means you get:

- faster directory listing
- fewer redundant SAF queries
- custom projections when you only need a few columns
- query support for filtering, sorting, paging, and projection
- a drop-in-friendly replacement for most `DocumentFile` usage

Check the screenshots below:

[<img src="/screenshots/filecompat_directory_perf.jpeg" height="500"/>](/screenshots/filecompat_directory_perf.jpeg)
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
[<img src="/screenshots/filecompat_file_perf.jpeg" height="500"/>](/screenshots/filecompat_file_perf.jpeg)

One local benchmark dropped a large directory listing from **48 seconds to 3.5 seconds**.\
It still does not beat the native `File` API, but for SAF-heavy code it is a major improvement.

What started as an internal utility is now a solid, usable alternative to `DocumentFile` for
real-world SAF workflows.

### Installation

#### Gradle

```gradle
dependencies {
    implementation "com.lazygeniouz:dfc:$latest_version"
}
```

#### Maven

```xml

<dependency>
    <groupId>com.lazygeniouz</groupId>
    <artifactId>dfc</artifactId>
    <version>$latest_version</version>
    <type>aar</type>
</dependency>
```

### Usage

Most methods and getters are intentionally close to AndroidX `DocumentFile`, so migration is mostly
about swapping imports.

Extras include:

- `copyTo(destination: Uri)`
- `copyFrom(source: Uri)`
- custom projections
- query-based child listing

Basic example:

```kotlin
val directory = DocumentFileCompat.fromTreeUri(context, treeUri) ?: return

val recentFiles = directory.listFiles()
```

#### Querying Child Documents

`DocumentFileCompat` now supports `listFiles(vararg queries: Query)` for tree-backed directories
when you need filtering, sorting, paging, or projection without dropping down to raw resolver code.

```kotlin
import android.provider.DocumentsContract
import com.lazygeniouz.dfc.file.Query

val directory = DocumentFileCompat.fromTreeUri(context, treeUri) ?: return

val files = directory.listFiles(
    Query.filesOnly(),
    Query.orderByDesc(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
    Query.limit(100),
    Query.select(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_SIZE,
    ),
)
```

Notes:

- `listFiles(vararg queries: Query)` is only supported for tree-backed `DocumentsProvider`
  directories.
- On API 21-25, only `Query.select(...)`, `Query.projection(...)`, `Query.orderByAsc(...)`, and
  `Query.orderByDesc(...)` are honored.
- On API 26+, filter queries, `Query.limit(...)`, `Query.offset(...)`, and
  `Query.rawSelection(...)` are also forwarded.
- Unsupported queries are ignored and logged.
- Providers may still ignore supported query arguments. `DocumentFileCompat` forwards them, but the
  underlying provider decides what gets honored.

#### Reference:

1. https://stackoverflow.com/a/42187419/6819340
2. https://stackoverflow.com/a/63466997/6819340

### Issues & Suggestions

Create a new issue if you experience any problem or have any suggestions.\
I'll appreciate if you create a PR as well (if possible).

Finally, don't forget to ⭐️ the library! :)