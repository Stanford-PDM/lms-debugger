package org.lmsdbg
package utils

import org.scaladebugger.api.profiles.traits.info._
import java.io.File
import scala.io.Source
import Utils._

object Definitions {

  val LMSPrefix = "scala.virtualization.lms"
  val DelitePrefix = "ppl"

  // TODO: extract these with macros from a lms library dependency ?

  val ExpressionsClassName = "scala.virtualization.lms.internal.Expressions"
  val DefClassName = "scala.virtualization.lms.internal.Expressions$Def"
  val SymClassName = "scala.virtualization.lms.internal.Expressions$Sym"
  val ConstClassName = "scala.virtualization.lms.internal.Expressions$Const"
  val ClassTypeManifestClassName = "scala.reflect.ManifestFactory$ClassTypeManifest"
  val BlockClassName = "scala.virtualization.lms.internal.Blocks$Block"
  val DeliteConfigObjectName = "ppl.delite.framework.Config$"
  val SummaryClassName = "scala.virtualization.lms.internal.Effects$Summary"
  val TPClassName = "scala.virtualization.lms.internal.Expressions$TP"
  val ReflectClassName = "scala.virtualization.lms.internal.Effects$Reflect"

  val Map2ClassName = "scala.collection.immutable.Map$Map2"
  val NilObjectClassName = "scala.collection.immutable.Nil$"
  val ColonColonClassName = "scala.collection.immutable.$colon$colon"

  def isSubtypeOfList(typ: TypeInfoProfile): Boolean = {
    typ.name == NilObjectClassName || typ.name == ColonColonClassName
  }

  val ExpressionsFileName = "scala/virtualization/lms/internal/Expressions.scala"

  val IntegerClassName = "java.lang.Integer"
  val CharacterClassName = "java.lang.Character"

  val BoxedPrimitives = Seq(IntegerClassName, CharacterClassName)

  // TODO: find easier way to get symbols from process being debugged (sbt or other)
  val HyperDSLFolder = new File("/Users/dengels/Documents/EPFL/PDM/Projects/mine-hyperdsl")
  val LMSFolder = new File("/Users/dengels/Documents/EPFL/PDM/Projects/lms-core")
  val DeliteFolder = new File("/Users/dengels/Documents/EPFL/PDM/Projects/mine-delite")
  val ProjectFolders = Seq(HyperDSLFolder) //Seq(LMSFolder, DeliteFolder)

  trait ScalaSource {
    def fullName: String
    def name: String
    def lines: Seq[String]
  }

  case class ScalaSourceFile(file: File) extends ScalaSource {
    val lines = Source.fromFile(file).getLines.toSeq

    private def packageElements: Seq[String] = {
      val filteredLines = lines.filter(_.trim.startsWith("package "))
      filteredLines.flatMap(_.trim.stripPrefix("package ").split('.'))
    }

    def name = file.getName
    def fullName = (packageElements :+ name).mkString("/")
  }

  def allFiles(root: File): Seq[ScalaSource] = {
    for {
      srcDir <- findFoldersNamed(root, "src")
      scalaFile <- filesWithExtension(srcDir, "scala")
    } yield {
      ScalaSourceFile(scalaFile)
    }
  }

  def allClassNames(root: File): Seq[String] = {
    for {
      classDir <- findFoldersNamed(root, "classes")
      dirPath = classDir.toPath
      classFile <- filesWithExtension(root, "class")
    } yield {
      val path = dirPath.relativize(classFile.toPath).toString
      path.replaceAllLiterally("/", ".").substring(0, path.length - 6)
    }
  }

  val files = ProjectFolders.flatMap(allFiles).toSet
  val classes = ProjectFolders.flatMap(allClassNames).toSet

  val oldFiles = Seq(ExpressionsFileName)
  val oldClasses = Seq(ExpressionsClassName, DefClassName, SymClassName, DeliteConfigObjectName)

  /**
   * Transforms boxed types (ex. java.lang.Integer) to equivalent
   * native types (ex. int)
   */
  def unbox(obj: ObjectInfoProfile): PrimitiveInfoProfile = {
    assert(BoxedPrimitives.contains(obj.typeInfo.name))
    obj.fields.find(_.name == "value").get.toValueInfo.toPrimitiveInfo
  }

  def reMapSymbols(s: String): String = s.replaceAll("$colon", ":")

  def classPrefix(fullName: String) = {
    val lastDot = fullName.lastIndexOf('.')
    val short = fullName.substring(lastDot + 1)

    reMapSymbols(short)
  }
}
