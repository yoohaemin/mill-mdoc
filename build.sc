// mill plugins
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`
// Run integration tests with mill
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.7.1`
// Generate converage reports
import $ivy.`com.lihaoyi::mill-contrib-scoverage:`
import $ivy.`io.chris-kipp::mill-ci-release::0.1.9`

import mill.define.{Command, TaskModule}

import io.kipp.mill.ci.release._

import de.tobiasroeser.mill.integrationtest._

import mill._
import mill.contrib.scoverage.ScoverageModule
import mill.define.{Cross, Target}
import mill.scalalib._
import mill.scalalib.publish._

import os.Path

val mill_0_10 = "0.10"
val mill_0_10_version = "0.10.15" // scala-steward:off
val mill_0_11 = "0.11"
val mill_0_11_version = "0.11.7"  // scala-steward:off

lazy val millVersions = Map(
  mill_0_10 -> mill_0_10_version,
  mill_0_11 -> mill_0_11_version
)

trait Deps {
  // The mill API version used in the project/sources/dependencies, also default for integration tests
  def millVersion: String
  def millPlatform: String
  def scalaVersion: String
  def millTestVersions: Seq[String]
  val scoverageVersion = "2.0.11"

  val bndlib = ivy"biz.aQute.bnd:biz.aQute.bndlib:6.0.0"
  val logbackClassic = ivy"ch.qos.logback:logback-classic:1.1.3"
  def millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
  def millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  val scalaTest = ivy"org.scalatest::scalatest:3.2.10"
  def scalaLibrary = ivy"org.scala-lang:scala-library:${scalaVersion}"
  val scoveragePlugin = ivy"org.scoverage:::scalac-scoverage-plugin:${scoverageVersion}"
  val scoverageRuntime = ivy"org.scoverage::scalac-scoverage-runtime:${scoverageVersion}"
  val slf4j = ivy"org.slf4j:slf4j-api:1.7.32"
}

object Deps_0_11 extends Deps {
  override val millVersion =  mill_0_11_version
  override def millPlatform = mill_0_11
  override val scalaVersion = "2.13.12"
  // keep in sync with .github/workflows/build.yml
  override val millTestVersions = Seq(millVersion)
}
object Deps_0_10 extends Deps {
  override val millVersion = mill_0_10_version
  override def millPlatform = mill_0_10
  override val scalaVersion = "2.13.12"
  // keep in sync with .github/workflows/build.yml
  override val millTestVersions = Seq(millVersion)
}

/** Cross build versions */
val millPlatforms = Seq(Deps_0_11, Deps_0_10).map(x => x.millPlatform -> x)

trait MillMDocModule extends ScalaModule with CiReleaseModule {
  def millPlatform: String
  def deps: Deps = millPlatforms.toMap.apply(millPlatform)
  override def scalaVersion = T { deps.scalaVersion }
  override def ivyDeps = Agg(deps.scalaLibrary)
  override def artifactSuffix = s"_mill${deps.millPlatform}_${artifactScalaVersion()}"
  override def javacOptions = Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF-8")
  override def scalacOptions = Seq("-target:jvm-1.8", "-encoding", "UTF-8")
  override def pomSettings = T {
    PomSettings(
      description = "Mill module to execute Scalameta MDoc",
      organization = "com.yoohaemin",
      url = "https://github.com/yoohaemin/mill-mdoc",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("yoohaemin", "mill-mdoc"),
      developers = Seq(
        Developer("atooni", "Andreas Gies", "https://github.com/atooni"),
        Developer("yoohaemin", "Haemin Yoo", "https://github.com/yoohaemin")
      )
    )
  }
  override def sonatypeHost = Some(SonatypeHost.s01)
}

object core extends Cross[Core](mill_0_10, mill_0_11)
trait Core extends MillMDocModule with ScoverageModule with Cross.Module[String] {
  override def millPlatform: String = crossValue
  override def millSourcePath: Path = super.millSourcePath / os.up
  override def artifactName = "mill-mdoc"
  override def compileIvyDeps = Agg(
    deps.millMain,
    deps.millScalalib
  )

  override def generatedSources: Target[Seq[PathRef]] = T {
    val dest = T.dest
    val infoClass =
      s"""// Generated with mill from build.sc
         |package de.wayofquality.mill.mdoc.internal
         |
         |object BuildInfo {
         |  def millMdocVerison = "${publishVersion()}"
         |  def millVersion = "${deps.millVersion}"
         |}
         |""".stripMargin
    os.write(dest / "BuildInfo.scala", infoClass)
    super.generatedSources() ++ Seq(PathRef(dest))
  }

  override def scoverageVersion = deps.scoverageVersion
  // we need to adapt to changed publishing policy - patch-level
  override def scoveragePluginDeps = T {
    Agg(deps.scoveragePlugin)
  }

  object test extends ScoverageTests with TestModule.ScalaTest {
    override def ivyDeps = Agg(
      deps.scalaTest
    )
  }
}

object testsupport extends Cross[TestSupport](mill_0_10, mill_0_11)
trait TestSupport extends MillMDocModule with Cross.Module[String] {
  def millVersion: String = millVersions(millPlatform)
  override val millPlatform: String = crossValue
  override def millSourcePath: Path = super.millSourcePath / os.up
  override def compileIvyDeps = Agg(
    deps.millMain,
    deps.millScalalib
  )
  override def artifactName = "mill-mdoc-testsupport"
  override def moduleDeps = Seq(core(millPlatform))
}

val testVersions: Seq[(String, Deps)] = millPlatforms.flatMap { case (_, d) => d.millTestVersions.map(_ -> d) }

object itest extends Cross[ItestCross](mill_0_10, mill_0_11) with TaskModule {
  override def defaultCommandName(): String = "test"
  def testCached: T[Seq[TestCase]] = itest(testVersions.map(_._1).head).testCached
  def test(args: String*): Command[Seq[TestCase]] = itest(testVersions.map(_._1).head).test(args: _*)
}

trait ItestCross extends MillIntegrationTestModule with Cross.Module[String]{
  def millVersion = millVersions(millPlatform)
  def millPlatform: String = crossValue
  override def millSourcePath: Path = super.millSourcePath / os.up
  def deps = testVersions.toMap.apply(millVersion)
  override def millTestVersion = T { millVersion }
  override def pluginsUnderTest = Seq(core(deps.millPlatform), testsupport(deps.millPlatform))

  override def pluginUnderTestDetails =
    T.traverse(pluginsUnderTest) { p =>
      val jar = p match {
        case p: ScoverageModule => p.scoverage.jar
        case p => p.jar
      }
      jar zip (p.sourceJar zip (p.docJar zip (p.pom zip (p.ivy zip p.artifactMetadata))))
    }
  override def perTestResources = T.sources { Seq(generatedSharedSrc()) }
  def generatedSharedSrc = T {
    val scov = deps.scoverageRuntime.dep
    os.write(
      T.dest / "shared.sc",
      s"""import $$ivy.`${scov.module.organization.value}::${scov.module.name.value}:${scov.version}`
         |""".stripMargin
    )
    PathRef(T.dest)
  }
}
