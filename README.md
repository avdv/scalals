[![Build Status](https://travis-ci.com/avdv/scalals.svg?branch=master)](https://travis-ci.com/avdv/scalals)

# About

This is yet another [colorls](https://github.com/athityakumar/colorls) clone.

![screenshot](images/screenshot1.png)

# Features

1. fast (compiled to native code)
2. aims to be a drop-in replacement to GNU ls
3. supports `LS_COLORS`

# Building

Currently, you need to build scala-native locally from [here](https://github.com/avdv/scala-native/tree/integ),
since this branch has integrated some crucial patches to fix problems with java.time.Instant, file times
and closing files.

```console
$ git clone -b integ https://github.com/avdv/scala-native.git
$ cd scala-native
$ sbt
> rebuild
```

You also need to build scopt for this scala-native version:

```console
$ git clone -b scala-native-snapshot https://github.com/avdv/scopt.git
$ cd scopt
$ sbt scoptNative/publishLocal
```

