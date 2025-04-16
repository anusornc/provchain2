// build.sbt (Try removing circe-java8)

// --- Project Definition ---
ThisBuild / organization := "ac.th.cmu.cs"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.5"

lazy val root = (project in file("."))
  .settings(
    name := "provchain",

    // --- Library Dependencies ---
    libraryDependencies ++= Seq(
      // Logging Facade (SLF4j) and Implementation (Logback)
      "org.slf4j" % "slf4j-api" % "2.0.17",
      "ch.qos.logback" % "logback-classic" % "1.5.18" % Runtime,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",

      // Configuration
      "com.typesafe" % "config" % "1.4.3",

      // JSON Handling (Circe)
      "io.circe" %% "circe-core"    % "0.14.12",
      "io.circe" %% "circe-generic" % "0.14.12",
      "io.circe" %% "circe-parser"  % "0.14.12",
      // "io.circe" %% "circe-java8"   % "0.14.0", // <-- ลบบรรทัดนี้ออก

      // Bouncy Castle (for Cryptography)
      "org.bouncycastle" % "bcprov-jdk18on" % "1.80",
      "org.bouncycastle" % "bcpkix-jdk18on" % "1.80",

      // RocksDB JNI driver
      "org.rocksdb" % "rocksdbjni" % "9.10.0", // <-- เพิ่มบรรทัดนี้

      // Testing (ScalaTest)
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalatestplus" %% "mockito-4-11" % "3.2.18.0" % Test,
    ),

    // --- Compiler Options ---
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings",
      "-language:implicitConversions",
      "-language:higherKinds"
    ),

    // --- sbt Settings ---
    // Add other settings here later
  )