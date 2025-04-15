// build.sbt

// --- Project Definition ---
ThisBuild / organization := "com.yourcompany" // เปลี่ยนเป็นชื่อองค์กรของคุณ
ThisBuild / version      := "0.1.0-SNAPSHOT" // เวอร์ชั่นเริ่มต้น
ThisBuild / scalaVersion := "3.3.5" // อัปเดตเป็น LTS ล่าสุด (หรือคง 3.3.3 ไว้ก็ได้)

lazy val root = (project in file("."))
  .settings(
    name := "provchain", // ชื่อโปรเจกต์

    // --- Library Dependencies ---
    libraryDependencies ++= Seq(
      // Logging Facade (SLF4j) and Implementation (Logback)
      "org.slf4j" % "slf4j-api" % "2.0.17", // <--- Updated
      "ch.qos.logback" % "logback-classic" % "1.5.18" % Runtime, // <--- Updated
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5", // Still latest
      // Configuration
      "com.typesafe" % "config" % "1.4.3", // Still latest

      // JSON Handling (Circe)
      "io.circe" %% "circe-core"    % "0.14.12", // <--- Updated
      "io.circe" %% "circe-generic" % "0.14.12", // <--- Updated
      "io.circe" %% "circe-parser"  % "0.14.12", // <--- Updated
      "io.circe" %% "circe-java8"   % "0.14.12", // <--- Updated

      // Testing (ScalaTest)
      "org.scalatest" %% "scalatest" % "3.2.19" % Test // <--- Updated
    ),

    // --- Compiler Options ---
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings",
      "-language:implicitConversions",
      "-language:higherKinds"
      // Add other options as needed
    ),

    // --- sbt Settings ---
    // Add other settings like assembly plugin configuration, etc. later
  )

// You can add more settings or subprojects here later
