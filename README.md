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

## Using nix

1. run `cachix use cbley` (optional, but reduces build time significantly)
2. run `nix-shell --run sbt`

_Note_: this project uses a scala-native version build off the master branch,
        versioned as 0.4.0 and patched to make it a drop-in replacement for the
        official 0.4.0 release.
