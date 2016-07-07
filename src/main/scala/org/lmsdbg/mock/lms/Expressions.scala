package org.lmsdbg
package mock.lms

import utils.Localizers._
import utils.DynamicWrappers.{Scope, ValueScope}
import utils.Definitions
import scala.util.Try
import utils._

import org.scaladebugger.api.profiles.traits.info._

object Expressions {

  case class Const[T](x: T)
  case class Sym(id: Int)
  case class TP(sym: Sym, rhs: Scope)

  implicit object SymLocalizer extends NamedObjectLocalizer[Sym] {
    def className = Definitions.SymClassName
    def tryLocal(obj: ObjectInfoProfile): Try[Sym] = {
      for {
        id <- obj.tryField("id")
        idValue <- IntLocalizer.tryLocal(id.toValueInfo)
      } yield {
        Sym(idValue)
      }
    }
  }

  implicit def constLocalizer[T: ValueLocalizer] = new NamedObjectLocalizer[Const[T]] {
    def className = Definitions.ConstClassName
    def tryLocal(obj: ObjectInfoProfile): Try[Const[T]] = {
      for {
        x <- obj.tryField("x")
        xValue <- localizer[T].tryLocal(x.toValueInfo)
      } yield {
        Const(xValue)
      }
    }
  }

  implicit object TPLocalizer extends NamedObjectLocalizer[TP] {
    def className = Definitions.TPClassName
    def tryLocal(obj: ObjectInfoProfile): Try[TP] = {
      for {
        sym <- obj.tryField("sym")
        symValue <- SymLocalizer.tryLocal(sym.toValueInfo)
        rhs <- obj.tryField("rhs")
      } yield {
        TP(symValue, new ValueScope(rhs.toValueInfo))
      }
    }
  }
}
