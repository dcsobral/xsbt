import sbt._

import Project.Initialize
import Keys._
import classpath.ClasspathUtilities
import java.lang.reflect.Method
import java.util.Properties

object ScriptedPlugin extends Plugin {
	def scriptedConf = config("scripted-sbt")

	val scriptedSbt = SettingKey[String]("scripted-sbt")
	val sbtLauncher = SettingKey[File]("sbt-launcher")
	val sbtTestDirectory = SettingKey[File]("sbt-test-directory")
	val scriptedBufferLog = SettingKey[Boolean]("scripted-buffer-log")
	final case class ScriptedScalas(build: String, versions: String)
	val scriptedScalas = SettingKey[ScriptedScalas]("scripted-scalas")

	val scriptedClasspath = TaskKey[PathFinder]("scripted-classpath")
	val scriptedTests = TaskKey[AnyRef]("scripted-tests")
	val scriptedRun = TaskKey[Method]("scripted-run")
	val scriptedDependencies = TaskKey[Unit]("scripted-dependencies")
	val scripted = InputKey[Unit]("scripted")

	def scriptedTestsTask: Initialize[Task[AnyRef]] = (scriptedClasspath, scalaInstance) map {
		(classpath, scala) =>
		val loader = ClasspathUtilities.toLoader(classpath, scala.loader)
		ModuleUtilities.getObject("sbt.test.ScriptedTests", loader)
	}

	def scriptedRunTask: Initialize[Task[Method]] = (scriptedTests) map {
		(m) =>
		m.getClass.getMethod("run", classOf[File], classOf[Boolean], classOf[String], classOf[String], classOf[String], classOf[Array[String]], classOf[File]) 
	}

	def scriptedTask: Initialize[InputTask[Unit]] = InputTask(_ => complete.Parsers.spaceDelimited("<arg>")) { result =>
		(scriptedDependencies, scriptedTests, scriptedRun, sbtTestDirectory, scriptedBufferLog, scriptedSbt, scriptedScalas, sbtLauncher, result) map {
			(deps, m, r, testdir, bufferlog, version, scriptedScalas, launcher, args) =>
			try { r.invoke(m, testdir, bufferlog: java.lang.Boolean, version.toString, scriptedScalas.build, scriptedScalas.versions, args.toArray, launcher) }
			catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
		}
	}

	val scriptedSettings = Seq(
		ivyConfigurations += scriptedConf,
		scriptedSbt <<= (appConfiguration)(_.provider.id.version),
		libraryDependencies <<= (libraryDependencies, scriptedSbt) {(deps, version) => deps :+ "org.scala-tools.sbt" %% "scripted-sbt" % version % scriptedConf.toString },
		sbtLauncher <<= (appConfiguration)(app => IO.classLocationFile(app.provider.scalaProvider.launcher.getClass)),
		sbtTestDirectory <<= sourceDirectory / "sbt-test",
		scriptedBufferLog := true,
		scriptedScalas <<= (scalaInstance) { (scala) => ScriptedScalas(scala.version, scala.version) },
		scriptedClasspath <<= (classpathTypes, update) map { (ct, report) => Path.finder(Classpaths.managedJars(scriptedConf, ct, report).map(_.data)) },
		scriptedTests <<= scriptedTestsTask,
		scriptedRun <<= scriptedRunTask,
		scriptedDependencies <<= (compile in Test, publishLocal) map { (analysis, pub) => Unit },
		scripted <<= scriptedTask
	)
}
