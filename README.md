# DocumentFileCompat

[![Maven Central](https://img.shields.io/maven-central/v/com.lazygeniouz/dfc?label=Maven%20Central)](https://central.sonatype.com/artifact/com.lazygeniouz/dfc)
[![Build](https://github.com/ItzNotABug/DocumentFileCompat/actions/workflows/build.yml/badge.svg)](https://github.com/ItzNotABug/DocumentFileCompat/actions/workflows/build.yml)
[![Min SDK](https://img.shields.io/badge/minSdk-21%2B-brightgreen)](dfc/build.gradle)
[![License](https://img.shields.io/github/license/ItzNotABug/DocumentFileCompat)](LICENSE.md)

A faster alternative to AndroidX `DocumentFile`.

`DocumentFile` is convenient, but it can get painfully slow with Storage Access Framework
directories. Methods like `findFile()`, `getName()`, `length()`, and custom model building can turn
into a lot of repeated `ContentResolver` queries.

`DocumentFileCompat` keeps the familiar API, but gathers useful metadata while listing files so you
do not keep paying for the same queries again and again.

## What it supports

- Tree URIs via `fromTreeUri(...)`.
- Single document URIs via `fromSingleUri(...)`.
- Raw `File` access via `fromFile(...)`.
- Common `DocumentFile`-style methods and getters.
- Faster directory listing and metadata access.
- Custom projections for lighter queries.
- Convenience APIs like `count()`, `copyTo(destination)`, and `copyFrom(source)`.

## Installation

Use the latest version shown in the Maven Central badge above.

### Gradle

```groovy
dependencies {
    implementation "com.lazygeniouz:dfc:<version>"
}
```

### Maven

```xml

<dependency>
    <groupId>com.lazygeniouz</groupId>
    <artifactId>dfc</artifactId>
    <version>${dfc.version}</version>
    <type>aar</type>
</dependency>
```

## Usage

The API is mostly the same as AndroidX `DocumentFile`; the main change is how you build the initial
instance.

```kotlin
import com.lazygeniouz.dfc.file.DocumentFileCompat

val directory = DocumentFileCompat.fromTreeUri(context, treeUri) ?: return
val files = directory.listFiles()

val file = directory.findFile("report.pdf")
```

Other entry points:

- `DocumentFileCompat.fromSingleUri(context, uri)`
- `DocumentFileCompat.fromFile(context, file)`

Additional helpers like `count()`, `copyTo(destination)`, `copyFrom(source)`, and
`listFiles(projection)` are available when you need them.

## Performance

The sample app includes simple comparisons against AndroidX `DocumentFile`. Results depend on the
provider, device, and folder size, but large directories are where the difference shows up the most.

| Directory listing                                                                                                   | File metadata                                                                                             |
|---------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| [<img src="/screenshots/filecompat_directory_perf.jpeg" width="360"/>](/screenshots/filecompat_directory_perf.jpeg) | [<img src="/screenshots/filecompat_file_perf.jpeg" width="360"/>](/screenshots/filecompat_file_perf.jpeg) |

One sample run had directory listing at roughly 48 seconds with `DocumentFile` compared to roughly
3.5 seconds with `DocumentFileCompat`.

Obviously, this is not trying to compete with the native `File` API. It is meant to make SAF-backed
file access less painful.

## References

- [StackOverflow: Faster file lookup with document IDs](https://stackoverflow.com/a/42187419/6819340)
- [StackOverflow: Storage Access Framework performance notes](https://stackoverflow.com/a/63466997/6819340)

## Issues and Suggestions

Create an issue if you run into a problem or have a suggestion. PRs are appreciated too, especially
when they include a clear behavior change.

And finally, if this saves you some time, a star on the repository would be appreciated.