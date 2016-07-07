package org.lmsdbg
package utils

import org.scaladebugger.api.profiles.traits.info._
import scala.language.dynamics
import scala.language.implicitConversions
import scala.util.{Try, Failure}

import Localizers._

/**
 * Helper wrappers that allow you to access elements in the remote vm 
 * as if they were local
 */
object DynamicWrappers {
  private val _ = implicitConversions // shut up ensime

  sealed trait Scope extends Dynamic {
    def lookupValue(name: String): Scope = {
      ErrorScope(s"Could not find $name in current scope")
    }
    def selectDynamic(fieldName: String) = lookupValue(fieldName)
    def as[T: ValueLocalizer]: T = tryAs[T].get
    def tryAs[T: ValueLocalizer]: Try[T]
  }

  final case class ErrorScope(msg: String) extends Scope {
    override def lookupValue(name: String) = this
    def tryAs[T: ValueLocalizer] = Failure(new Exception(msg))

    override def toString = s"Error: $msg"
  }

  class GlobalScope(frame: FrameInfoProfile) extends Scope {
    override def lookupValue(name: String): Scope = {
      frame.allVariables
        .find(_.name == name)
        .fold[Scope] {
          ErrorScope(s"Variable $name is not available in current scope")
        } { v =>
          new ValueScope(v.toValueInfo)
        }
    }

    def tryAs[T: ValueLocalizer] = Failure(new Exception("Cannot convert global scope to value"))
    override def toString = {
      val names = frame.allVariables.map(_.name)
      val strings = for (name <- names) yield s"  $name = ${lookupValue(name).toString.take(30)}"
      strings.mkString("GlobalScope {\n", "\n", "\n}")
    }
  }

  // TODO: find better name for this thing: is more like a Dynamic Value + Scope trait
  class ValueScope(valueInfo: ValueInfoProfile) extends Scope {

    def value: ValueInfoProfile = valueInfo

    override def lookupValue(name: String) = {
      value.tryToObjectInfo.toOption.fold[Scope] {
        ErrorScope(s"${value.toPrettyString} is not an object")
      } { obj =>
        obj
          .fieldOption(name)
          .fold[Scope] {
            ErrorScope(s"${value.toPrettyString} has no field $name")
          } { field =>
            new ValueScope(field.toValueInfo)
          }
      }
    }

    def tryAs[T: ValueLocalizer] = Localizers.localizer[T].tryLocal(valueInfo)

    def toValueInfo = valueInfo
    def fieldNames = value.toObjectInfo.fields.map(_.name)

    override def toString = Printer.str(value)
    def toPrettyString = Printer.strln(value)
  }

  /**
   * LMS scopes do automatically resolve symbols so that you don't need to
   */
  trait LMSScope extends Scope {
    override def lookupValue(name: String): Scope = super.lookupValue(name) match {
      case v: ValueScope => new LMSValueScope(v.toValueInfo)
      case s             => s
    }
  }

  class LMSGlobalScope(frame: FrameInfoProfile) extends GlobalScope(frame) with LMSScope

  class LMSValueScope(valueInfo: ValueInfoProfile) extends ValueScope(valueInfo) with LMSScope {
    // TODO: I think we might want to keep the symbol sometimes, not sure this is the best way to do it
    def symbolId: Option[Int] = {
      if (valueInfo.typeInfo.name == Definitions.SymClassName) {
        Some(new ValueScope(valueInfo).id.as[Int])
      } else {
        None
      }
    }
    override def value = symbolId.flatMap(LMSInfo.defForId).getOrElse(valueInfo)
  }

  implicit class PimpedValue(value: ValueInfoProfile) {
    def toDynamic = new ValueScope(value)
  }

  implicit class PimpedScope(value: Scope) {
    // TODO: find a way to inject LMS dependency in here
    def asList = value.as[List[ValueInfoProfile]].map(new LMSValueScope(_))

    // TODO
    def symbolId: Option[Int] = value match {
      case l: LMSValueScope => l.symbolId
      case _                => None
    }
  }

}
