# Building

Currently, you need to build scala-native locally from [here](https://github.com/avdv/scala-native/tree/integ),
since this branch has integrated some crucial patches to fix problems with java.time.Instant, file times
and closing files.

```console
$ git clone -b integ https://github.com/avdv/scala-native.git
$ cd scala-native
$ sbt
> ^^1.2.8
> rebuild
```

You also need to build scopt for this scala-native version:

```console
$ git clone -b scala-native-snapshot https://github.com/avdv/scopt.git
$ cd scopt
$ sbt scoptNative/publishLocal
```

