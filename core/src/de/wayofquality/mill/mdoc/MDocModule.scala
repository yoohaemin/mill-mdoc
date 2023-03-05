package de.wayofquality.mill.mdoc

import mill._
import mill.define.{Sources, Target}
import mill.scalalib._
import mill.modules.Jvm
import os.Path

trait MDocModule extends ScalaModule {

  def scalaMdocVersion: T[String] = T("2.3.7")

  def scalaMdocDep: T[Dep] = T(ivy"org.scalameta::mdoc:${scalaMdocVersion()}")

  def watchedMDocsDestination: T[Option[Path]] = T(None)

  def mdocVariables: T[Map[String, String]] = T(Map.empty[String, String])

  override def ivyDeps = T {
    super.ivyDeps() ++ Agg(scalaMdocDep())
  }

  // where do the mdoc sources live ?
  def mdocSources: Sources = T.sources {
    super.millSourcePath
  }

  private def cp: Target[Seq[Path]] = T {
    runClasspath().map(_.path)
  }

  def mdoc: T[PathRef] = T {
    val separator = java.io.File.pathSeparatorChar

    val p = mdocSources().flatMap(pr => List(
      "--classpath", cp().mkString(s"$separator"),
      "--in", pr.path.toIO.getAbsolutePath,
      "--out", T.dest.toIO.getAbsolutePath) ++
      mdocVariables().flatMap { case (k, v) => List(s"--site.$k", v) }
    )

    Jvm.runLocal("mdoc.Main", cp(), p)
    PathRef(T.dest)
  }

  def mdocWatch() = T.command {

    watchedMDocsDestination() match {
      case None => throw new Exception("watchedMDocsDestination is not set, so we dant know where to put compiled md files")
      case Some(dest) =>
        val separator = java.io.File.pathSeparatorChar

        val p = mdocSources().flatMap(pr => List(
          "--classpath", cp().mkString(s"$separator"),
          "--in", pr.path.toIO.getAbsolutePath,
          "--out", dest.toIO.getAbsolutePath) ++
          mdocVariables().flatMap { case (k, v) => List(s"--site.$k", v) }
        )

        Jvm.runLocal("mdoc.Main", cp(), p :+ "--watch")
    }

  }
}

