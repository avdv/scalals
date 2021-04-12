[![Test](https://github.com/avdv/scalals/actions/workflows/test.yml/badge.svg)](https://github.com/avdv/scalals/actions/workflows/test.yml)

# About

This is yet another [colorls](https://github.com/athityakumar/colorls) clone.

![screenshot](images/screenshot1.png)

# Features

1. fast (compiled to native code)
2. aims to be a drop-in replacement to GNU ls
3. supports `LS_COLORS`

# Install

1. download a pre-built [binary](https://github.com/avdv/scalals/releases/latest) for your platform (currently only Linux and macOS 64bit supported)
2. install a Nerd Font from [here](https://www.nerdfonts.com/font-downloads) and use it in your terminal emulator
3. set up your dircolors (see https://www.nordtheme.com/ports/dircolors for example)

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

