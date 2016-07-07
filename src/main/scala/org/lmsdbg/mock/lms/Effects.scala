package org.lmsdbg
package mock.lms

import Expressions.Sym
import utils.Localizers._
import utils.Definitions
import scala.util.Try
import utils.DynamicWrappers.Scope

import org.scaladebugger.api.profiles.traits.info._

object Effects {

  // TODO: find a way to get all of this code written by a macro
  case class Summary(maySimple: Boolean, mstSimple: Boolean, mayGlobal: Boolean,
      mstGlobal: Boolean, resAlloc: Boolean, control: Boolean, mayRead: List[Sym],
      mstRead: List[Sym], mayWrite: List[Sym], mstWrite: List[Sym]) {

    override def toString = {
      def toStringBoolField(name: String, value: Boolean): Option[String] =
        if (value) Some(name) else None
      def toStringListField(name: String, value: List[Sym]): Option[String] =
        if (value.nonEmpty) Some(s"$name = $value") else None

      val boolFields =
        List("maySimple" -> maySimple, "mstSimple" -> mstSimple, "mayGlobal" -> mayGlobal,
            "mstGlobal" -> mstGlobal, "resAlloc" -> resAlloc, "control" -> control)

      val listFields = List("mayRead" -> mayRead, "mstRead" -> mstRead, "mayWrite" -> mayWrite,
          "mstWrite" -> mstWrite)

      val bools = boolFields.flatMap { case (name, value) => toStringBoolField(name, value) }
      val lists = listFields.flatMap { case (name, value) => toStringListField(name, value) }
      (bools ++ lists).mkString("Summary(", ", ", ")")
    }
  }

  val Pure = Summary(false, false, false, false, false, false, Nil, Nil, Nil, Nil)
  val Simple = Summary(true, true, false, false, false, false, Nil, Nil, Nil, Nil)
  val Global = Summary(false, false, true, true, false, false, Nil, Nil, Nil, Nil)
  val Alloc = Summary(false, false, false, false, true, false, Nil, Nil, Nil, Nil)
  val Control = Summary(false, false, false, false, false, true, Nil, Nil, Nil, Nil)

  case class Reflect(x: Scope, summary: Summary, deps: List[Scope])
  case class Reify(x: Scope, summary: Summary, effects: List[Scope])

  implicit object SummaryLocalizer extends NamedObjectLocalizer[Summary] {
    def className = Definitions.SummaryClassName
    def tryLocal(obj: ObjectInfoProfile): Try[Summary] = {
      val listSymLocalizer = localizer[List[Sym]]
      for {
        maySimple <- obj.tryField("maySimple")
        maySimpleValue <- BooleanLocalizer.tryLocal(maySimple.toValueInfo)
        mstSimple <- obj.tryField("mstSimple")
        mstSimpleValue <- BooleanLocalizer.tryLocal(mstSimple.toValueInfo)
        mayGlobal <- obj.tryField("mayGlobal")
        mayGlobalValue <- BooleanLocalizer.tryLocal(mayGlobal.toValueInfo)
        mstGlobal <- obj.tryField("mstGlobal")
        mstGlobalValue <- BooleanLocalizer.tryLocal(mstGlobal.toValueInfo)
        resAlloc <- obj.tryField("resAlloc")
        resAllocValue <- BooleanLocalizer.tryLocal(resAlloc.toValueInfo)
        control <- obj.tryField("control")
        controlValue <- BooleanLocalizer.tryLocal(control.toValueInfo)
        mayRead <- obj.tryField("mayRead")
        mayReadValue <- listSymLocalizer.tryLocal(mayRead.toValueInfo)
        mstRead <- obj.tryField("mstRead")
        mstReadValue <- listSymLocalizer.tryLocal(mstRead.toValueInfo)
        mayWrite <- obj.tryField("mayWrite")
        mayWriteValue <- listSymLocalizer.tryLocal(mayWrite.toValueInfo)
        mstWrite <- obj.tryField("mstWrite")
        mstWriteValue <- listSymLocalizer.tryLocal(mstWrite.toValueInfo)
      } yield {
        Summary(maySimpleValue, mstSimpleValue, mayGlobalValue, mstGlobalValue, resAllocValue,
            controlValue, mayReadValue, mstReadValue, mayWriteValue, mstWriteValue)
      }
    }
  }
}
