# FileCompat
A faster alternative to AndroidX's DocumentFile.

### The Problem with DocumentFile
It is horribly slow!\
For **almost** every method, there is a query to **ContentResolver**.

The most common one is `DocumentFile.findFile()` and other is\
building a Custom Data Model with a `Document Uri`, `Document Name`, & `Last Modified` parameters.\
This can take like a horrible amount of time.

### Solution
`FileCompat` gathers relevant parameters on each call to the ContentResolver when querying for files.\
The performance can sometimes peak to 2x or quite higher, depending on the size of the folder.

Check the screenshots below:

[<img src="/screenshots/filecompat_perf.jpeg" height="500"/>](/screenshots/filecompat_perf.jpeg)

**28 whopping seconds!**\
Obviously, No competition with the Native File API.

**Note:** `FileCompat` is something that I used internally for some projects & therefore I don't do much of file manipulation with it (only delete files),
so this API does not offer too much out of the box. This is fine to get someone started with an alternative to `DocumentFile`.

Reference:
1. https://stackoverflow.com/a/42187419/6819340
2. https://stackoverflow.com/a/63466997/6819340
