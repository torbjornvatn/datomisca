name := "datomisca-demo-fagdag"

version := "0.1.0"

scalaVersion := "2.10.2"

resolvers ++= Seq(
  // to get Datomisca
  "datomisca-repo snapshots" at "https://github.com/pellucidanalytics/datomisca-repo/raw/master/snapshots",
  "datomisca-repo releases"  at "https://github.com/pellucidanalytics/datomisca-repo/raw/master/releases",
  // to get Datomic free (for pro, you must put in your own repo or local)
  "clojars" at "https://clojars.org/repo"
)

libraryDependencies ++= Seq(
  "pellucidanalytics" %% "datomisca" % "0.5.1",
  "com.datomic" % "datomic-free" % "0.8.4020.26"
)
