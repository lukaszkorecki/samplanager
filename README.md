# What

Find duplicate audio files across directories

# Why

I'm a sample hoarder and I'm quite messy at that. `samplanger` helps by:

- scanning multiple directories of your choice
- using a simple md5 checksum is hashes all files and figures out duplicates
  - it doesn't just look at file names as that's a weak heuristic
- finally produces a JSON file with groups of duplicates

# Demo


```
# bb app.jar -o report.json ./kits ./samples
samplanager
⠦ Checksumming candidates...
   Audio files scanned: 41787
   █████████████████████████████░ 99% (40360/40363)samplanager ✓ complete

  Audio files: 41787
  Total size: 11.65 GB

  Duplicate groups: 10763
  Duplicate files: 39276
  Potential savings: 7.42 GB

  Top directories by duplicates:
  2553 samples/SomeSampleKit/5-ALL EXTRAS/1-KICKS
  2553 kits/SomeSampleKit/5-ALL EXTRAS/1-KICKS
  1822 kits/SomeSampleKit/5-ALL EXTRAS/2-SNARES
  1822 samples/SomeSampleKit/5-ALL EXTRAS/2-SNARES
  1384 kits/SomeSampleKit/5-ALL EXTRAS/3-HATS
```

# How to use?

- bet Babashka (babashka.org) somehow
- clone this repo
- build a jar:

```
bb uberjar app.jar -m samplanager.core
```

- use it:

```
cd my-cool-sample-library
bb ~/path/to/jar -o report.json ./some-dir
```

from here you can analyze the dupes and figure out what can be deleted.
