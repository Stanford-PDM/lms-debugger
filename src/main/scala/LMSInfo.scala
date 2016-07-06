import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

import Definitions._
import com.sun.jdi.event._
import org.scaladebugger.api.virtualmachines._
import org.scaladebugger.api.profiles.traits.info._

object LMSInfo {
  val symbols: mutable.Map[Int, ValueInfoProfile] = mutable.Map()

  /**
   * Extract all the lms symbols when they are created, 
   * so they can be remapped later when we are trying to print
   */
  def populateSymbolsMap(implicit vm: ScalaVirtualMachine): Unit = {
    val breaks = vm.getOrCreateBreakpointRequest(ExpressionsFileName, 129)
    breaks.foreach { e =>
      implicit val frame = vm.thread(e.thread).topFrame
      val id = Main.value(_.s.id).get.toLocalValue.asInstanceOf[Int]
      val deff = Main.value(_.d).get
      if (symbols.contains(id)) {
        // TODO: find a better way to do this
        println(s"Symbols contains id: $id, resetting map")
        symbols.clear()
      }
      symbols(id) = deff
    }
  }

  def breakOnFirstLMSClassLoad(implicit vm: ScalaVirtualMachine): Unit = {
    val pipeline = vm.getOrCreateClassPrepareRequest()
    val lmsClass = pipeline.filter(_.referenceType.name.startsWith(LMSPrefix))
    lmsClass.toFuture.map { (e: ClassPrepareEvent) =>
      println(s"Stopped on load of ${e.referenceType.name}")
      vm.suspend
    }
  }

  def init(implicit vm: ScalaVirtualMachine): Unit = {
    populateSymbolsMap(vm)
    //breakOnFirstLMSClassLoad(vm)
  }

  def getDefOrSame(valueInfo: ValueInfoProfile): ValueInfoProfile = {
    if (valueInfo.typeInfo.name != Definitions.SymClassName) {
      valueInfo
    } else {
      val id = valueInfo.toObjectInfo.field("id").toValueInfo.toLocalValue.asInstanceOf[Int]
      symbols.getOrElse(id, valueInfo)
    }
  }

  def defForId(id: Int) = symbols.get(id)
}
