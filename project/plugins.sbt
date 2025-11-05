val scalaNativeVersion = "0.5.8"

addSbtPlugin("org.scala-native" % "sbt-scala-native" % scalaNativeVersion)
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.1")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.6")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.4")
addSbtPlugin("org.typelevel" % "sbt-tpolecat" % "0.5.2")
