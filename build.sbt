name := "flio"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies += "org.typelevel" %% "cats-effect" % "1.0.0"
libraryDependencies += "org.typelevel" %% "cats-effect-laws" % "1.0.0" % "test"
libraryDependencies += "org.typelevel" %% "discipline" % "0.10.0"
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
