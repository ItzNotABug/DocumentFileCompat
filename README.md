# DocumentFileCompat

A faster alternative to AndroidX's DocumentFile.

### The Problem with DocumentFile

It is horribly slow!\
For **almost** every method, there is a query to **ContentResolver**.

The most common one is `DocumentFile.findFile()`, `DocumentFile.getName()` and other is building a
Custom Data Model with multiple parameters.\
This can take like a horrible amount of time.

### Solution

`DocumentFileCompat` is a drop-in replacement which gathers relevant parameters when querying for
files.\
The performance can sometimes peak to 2x or quite higher, depending on the size of the folder.

Check the screenshots below:

[<img src="/screenshots/filecompat_directory_perf.jpeg" height="500"/>](/screenshots/filecompat_directory_perf.jpeg)
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
[<img src="/screenshots/filecompat_file_perf.jpeg" height="500"/>](/screenshots/filecompat_file_perf.jpeg)

**48 whopping seconds for directory listing compared to 3.5!** (Obviously, No competition with the
Native File API).\
Also extracting file information does not take that much time but the improvement is still
significant.

**Note:** `DocumentFileCompat` is something that I used internally for some projects & therefore I
didn't do much of file manipulation with it (only delete files) <strike>and therefore this API does
not offer too much out of the box</strike>.\
This is now a completely usable alternative to `DocumentFile`.

### Installation

#### Gradle

```gradle
dependencies {
    implementation "com.lazygeniouz:documentfile_compat:$latest_version"
}
```

#### Maven

```maven
<dependency>
  <groupId>com.lazygeniouz</groupId>
  <artifactId>documentfile_compat</artifactId>
  <version>$latest_version</version>
  <type>aar</type>
</dependency>
```

### Usage

Almost all of the methods & getters are identical to `DocumentFile`, you'll just have to replace the
imports.\
Additional methods like `copyTo(destination: Uri)` & `copyFrom(source: Uri)` are added as well.

#### Reference:

1. https://stackoverflow.com/a/42187419/6819340
2. https://stackoverflow.com/a/63466997/6819340

### Issues & Suggestions

Create a new issue if you experience any problem or have any suggestions.\
I'll appreciate if you create a PR as well (if possible).

Finally, don't forget to ⭐️ the library! :)