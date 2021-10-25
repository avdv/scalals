val scalaNativeVersion = "0.4.1"

addSbtPlugin("org.scala-native" % "sbt-scala-native" % scalaNativeVersion)
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.1.0")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")
