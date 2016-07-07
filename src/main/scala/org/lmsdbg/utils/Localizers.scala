package org.lmsdbg
package utils

import org.scaladebugger.api.profiles.traits.info._

import scala.util.{Try, Success, Failure}

/** 
 * Type class used to serialize a value from the remote vm to a local value
 */
trait ValueLocalizer[T] {
  def tryLocal(value: ValueInfoProfile): Try[T]
  def toLocal(value: ValueInfoProfile): T = tryLocal(value).get
}

object Localizers {
  def localizer[T: ValueLocalizer] = implicitly[ValueLocalizer[T]]

  /** The scala-debugger lib does this correctly for some of the types */
  trait NativeLocalizer[T] extends ValueLocalizer[T] {
    def tryLocal(value: ValueInfoProfile) = Success(value.toLocalValue.asInstanceOf[T])
  }

  implicit object ByteLocalizer extends NativeLocalizer[Byte]
  implicit object CharLocalizer extends NativeLocalizer[Char]
  implicit object ShortLocalizer extends NativeLocalizer[Short]
  implicit object IntLocalizer extends NativeLocalizer[Int]
  implicit object LongLocalizer extends NativeLocalizer[Long]
  implicit object FloatLocalizer extends NativeLocalizer[Float]
  implicit object DoubleLocalizer extends NativeLocalizer[Double]
  implicit object BooleanLocalizer extends NativeLocalizer[Boolean]
  implicit object UnitLocalizer extends NativeLocalizer[Unit]
  implicit object StringLocalizer extends NativeLocalizer[String]

  /**
   * This localizer is more specific than the general value localizer 
   * and assumes you are trying to serialize an Object
   */
  trait ObjectLocalizer[T] extends ValueLocalizer[T] {
    def tryLocal(obj: ObjectInfoProfile): Try[T]
    def tryLocal(value: ValueInfoProfile): Try[T] = {
      value.tryToObjectInfo.flatMap(tryLocal)
    }
  }

  /**
   * This localizer will make sure you pass in a class with the correct name
   */
  trait NamedObjectLocalizer[T] extends ObjectLocalizer[T] {
    def className: String
    def tryLocal(obj: ObjectInfoProfile): Try[T]
    override def tryLocal(value: ValueInfoProfile): Try[T] = {
      value.tryToObjectInfo
        .flatMap(x =>
              if (x.typeInfo.name == className) {
            Success(x)
          } else {
            Failure(new Throwable(s"${x.toPrettyString} is not an instance of $className"))
        })
        .flatMap(tryLocal)
    }
  }

  /**
   * This localizer can seem useless at first sight, but it is sometimes 
   * quite useful to have only a shallow serialization, to avoid pulling in all the fields
   * (eg. for nested lists)
   */
  implicit object NoOpLocalizer extends ValueLocalizer[ValueInfoProfile] {
    def tryLocal(value: ValueInfoProfile) = Success(value)
  }

  implicit def listLocalizer[T: ValueLocalizer] = new ObjectLocalizer[List[T]] {

    def tryLocal(obj: ObjectInfoProfile): Try[List[T]] = {
      if (Definitions.isSubtypeOfList(obj.typeInfo)) {
        obj.typeInfo.name match {
          case Definitions.NilObjectClassName => Success(Nil)
          case Definitions.ColonColonClassName =>
            for {
              head <- obj.tryField("head")
              headValue <- localizer[T].tryLocal(head.toValueInfo)
              tail <- obj.tryField("tl")
              tailValue <- this.tryLocal(tail.toValueInfo)
            } yield {
              headValue :: tailValue
            }

        }
      } else {
        Failure(new Throwable(s"${obj.typeInfo.toPrettyString} is not a subtype of list"))
      }
    }
  }

}
