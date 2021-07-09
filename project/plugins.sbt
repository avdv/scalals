// N.B. we are using a `0.4.1-SNAPSHOT` version by default, which is always set
//      via nix. Solely for scala-steward, which cannot resolve the snapshot
//      version this needs to be set to `0.4.0`.
val scalaNativeVersion = sys.env.get("SN_VERSION").getOrElse("0.4.0")

addSbtPlugin("org.scala-native" % "sbt-scala-native" % scalaNativeVersion)
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.1.0")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")
