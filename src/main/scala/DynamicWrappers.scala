import org.scaladebugger.api.profiles.traits.info._
import scala.language.dynamics
import scala.language.implicitConversions
import scala.util.{Try, Failure}

import Definitions._
import Main._
import Localizers._

/**
 * Helper wrappers that allow you to access elements in the remote vm 
 * as if they were local
 */
object DynamicWrappers {

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
  }

  class ValueScope(valueInfo: ValueInfoProfile) extends Scope {

    override def lookupValue(name: String) = {
      valueInfo.tryToObjectInfo.toOption.fold[Scope] {
        ErrorScope(s"$valueInfo is not an object")
      } { obj =>
        obj
          .fieldOption(name)
          .fold[Scope] {
            ErrorScope(s"$valueInfo has no field $name")
          } { field =>
            new ValueScope(field.toValueInfo)
          }
      }
    }

    def tryAs[T: ValueLocalizer] = Localizers.localizer[T].tryLocal(valueInfo)

    def toValueInfo = valueInfo
    def fieldNames = valueInfo.toObjectInfo.fields.map(_.name)

    override def toString = Printer.str(valueInfo)
    def toPrettyString = Printer.strln(valueInfo)
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
// TODO: I think we might want to keep the symbol sometimes
  class LMSValueScope(valueInfo: ValueInfoProfile)
      extends ValueScope(LMSInfo.getDefOrSame(valueInfo)) with LMSScope

  implicit class PimpedValue(value: ValueInfoProfile) {
    def toDynamic = new ValueScope(value)
  }

  implicit class PimpedScope(value: Scope) {
    // TODO: find a way to inject LMS dependency in here
    def asList = value.as[List[ValueInfoProfile]].map(new LMSValueScope(_))
  }

}
