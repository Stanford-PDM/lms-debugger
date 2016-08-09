package org.lmsdbg

import org.scaladebugger.api.debuggers._
import org.scaladebugger.api.profiles.traits.info._
import org.scaladebugger.api.virtualmachines._

import com.sun.jdi.event._
import org.scaladebugger.api.pipelines.Pipeline._

import scala.concurrent.duration._
import scala.util.{Success, Failure}
import utils.DynamicWrappers._
import utils.Printer
import utils.Definitions

// TODO: clean this crap out into different files
// separate between synchronous/async methods
object Main {

  import utils.Definitions._

  def main(args: Array[String]): Unit = {
    val attachingDebugger = AttachingDebugger(port = 5006)

    attachingDebugger.start { implicit vm =>
      println("Attached to JVM: " + vm.uniqueId)

      vm.suspend
      LMSInfo.init

      break("MultiLoopSoA.scala", 149, { e =>
        println(e.thread().name())
      })

      vm.resume
    }

    attachingDebugger.stop()
  }

  // TODO: find a better way to do what I was trying to do here
  private var debugger: AttachingDebugger = _
  private var vm: ScalaVirtualMachine = _

  private implicit var frame: FrameInfoProfile = _

  def clean(vm: ScalaVirtualMachine): ScalaVirtualMachine = {
    val reqManager = vm.underlyingVirtualMachine.eventRequestManager
    reqManager.deleteAllBreakpoints
    vm
  }

  /** Used to get elements from remote vm
   * eg: if variable 'a' is in scope in current frame, use &a
   * problem : Doesn't chain unless you only use postfix operators (&a b c or &.a.b.c but no mix)
   */
  import scala.language.postfixOps
  def & : Scope = {
    val _ = postfixOps // clear ensime warning
    new LMSGlobalScope(getTopDeliteFrame(vm).get)
  }

  def showMultiloopSoaThings(e: BreakpointEvent)(implicit vm: ScalaVirtualMachine): Unit = {
    val frame = vm.thread(e.thread).topFrame
    val scope = new GlobalScope(frame)
    val alloc = scope.body.buf.alloc
    println(alloc)

  }

  def showEffectDeps(reifyNode: Scope): Unit = {
    val effects = reifyNode.effects.asList
    for (reflect <- effects) {
      println(reflect.x)
      println(reflect.summary)
      val deps = reflect.deps.asList
      for (dep <- deps) {
        println(s"  $dep")
      }
      println()
    }
  }

  // Helper functions for the console
  def attach(): ScalaVirtualMachine = {
    debugger = AttachingDebugger(port = 5006)
    vm = clean(debugger.start(10.seconds))
    vm
  }

  def detach(): Unit = {
    debugger.stop()
  }

  def c(implicit vm: ScalaVirtualMachine) = vm.resume

  def getClasses(prefix: String)(
      implicit vm: ScalaVirtualMachine): Seq[ReferenceTypeInfoProfile] = {
    vm.classes.filter(_.name.startsWith(prefix))
  }

  def getInstances(classes: Seq[ReferenceTypeInfoProfile], max: Int): Seq[ObjectInfoProfile] = {
    classes.map { x =>
      try {
        x.instances(1)
      } catch {
        case _: Throwable => Seq.empty
      }
    } filter (_.nonEmpty) map (_.head) take (max)
  }

  var printOn = false

  def str(field: VariableInfoProfile): String = {
    if (printOn) println(s"Printing field -> ${field.toPrettyString}")
    val typ = field.typeInfo
    val name = field.name
    val value = field.toValueInfo
    val badNames = Set("IR", "scala$Enumeration$$outerEnum")
    val valueString = if (badNames.contains(name)) "*skipped*" else Printer.str(value)
    s"$name = $valueString"
  }

  def printField(field: VariableInfoProfile): Unit = {
    val typ = field.typeInfo
    if (field.name == "MODULE$") {
      // ignore
    } else if (typ.isPrimitiveType || typ.isStringType) {
      println(field.toPrettyString)
    } else {
      val name = field.name
      val value = field.toValueInfo
      println(typ.name)
      value.typeInfo.name match {
        case Map2ClassName =>
          val obj = value.toObjectInfo
          val key1 = Printer.str(obj.field("key1").toValueInfo)
          val key2 = Printer.str(obj.field("key2").toValueInfo)
          val value1 = Printer.str(obj.field("value1").toValueInfo)
          val value2 = Printer.str(obj.field("value2").toValueInfo)
          println(s"$name = {")
          println(s"  $key1 -> $value1")
          println(s"  $key2 -> $value1")
          println("}")
        case _ =>
          println(s"$name = ${Printer.str(value)}")
      }
    }
  }

  def printDeliteConfig(implicit vm: ScalaVirtualMachine): Unit = {

    getDeliteConfigObject(vm) match {
      case None         => println("Could not find config object")
      case Some(config) => config.fields.foreach(printField)
    }
  }

  def getDeliteConfigObject(implicit vm: ScalaVirtualMachine): Option[ObjectInfoProfile] = {
    val configObjOpt = vm.classes.find(_.name == DeliteConfigObjectName)
    for {
      typeInfo <- configObjOpt
      instances <- typeInfo.tryInstances(1).toOption
      instance <- instances.headOption
    } yield instance
  }

  def break(file: String, line: Int)(implicit vm: ScalaVirtualMachine): Unit = {
    break(file, line, { event =>
      val loc = event.location
      vm.suspend
      // TODO: figure out why this does not work
      val currentThread = vm.thread(event.thread)
      this.frame = currentThread.topFrame
      println(s"Hit breakpoint at ${loc.sourceName}:${loc.lineNumber}")
    })
  }

  // Transform with Pipeline filter, seems more appropriate 
  def breakIfVar(file: String, line: Int)(varPath: CurrentContext => LocalVariablePath,
      cond: ValueInfoProfile => Boolean)(implicit vm: ScalaVirtualMachine): Unit = {

    break(file, line, { event =>
      val loc = event.location
      val currentThread = vm.thread(event.thread)
      val frame = currentThread.topFrame
      val path = varPath(CurrentContext())

      val info = resolvePath(frame.allVariables, path)

      info match {
        case None if printOn =>
          println(s"Hit conditional breakpoint at ${loc.sourceName}:${loc.lineNumber}")
          println(s"But could not find $path variable")
        case Some(t) if !cond(t) && printOn =>
          println(s"Hit conditional breakpoint at ${loc.sourceName}:${loc.lineNumber}")
          println(s"But condition not satisfied")
        case Some(t) if cond(t) =>
          println(s"Hit conditional breakpoint at ${loc.sourceName}:${loc.lineNumber}")
          vm.suspend
      }
    })
  }

  def break(file: String, line: Int, block: BreakpointEvent => Unit)(
      implicit vm: ScalaVirtualMachine): Unit = {
    val filenameOpt = vm.sourceNameToPaths(file).headOption

    val filename = filenameOpt.getOrElse {
      println("File not loaded yet, no gurantees made on correct line")
      val fullName = Definitions.files.find(_.name == file).map(_.fullName).getOrElse(file)
      val pipeline = vm.getOrCreateBreakpointRequest(fullName, line)
      pipeline.foreach(block)
      println(s"Breakpoint set at $fullName:$line")
      return
    }
    val realLine = vm.availableLinesForFile(filename) match {
      case None =>
        println(s"Could not find file: $file (no breakpoint set)")
      case Some(lines) =>
        lines.dropWhile(_ < line).headOption match {
          case None =>
            println(s"No breakpoint can be set at $filename:$line (no such line)")
          case Some(l) =>
            val pipeline = vm.getOrCreateBreakpointRequest(filename, l)
            pipeline.foreach(block)
            println(s"Breakpoint set at $filename:$l")
        }
    }
  }

  // Does not seem to work, always pending
  def watch(cls: String, field: String, block: AccessWatchpointEvent => Unit)(
      implicit vm: ScalaVirtualMachine): Unit = {
    val className = vm.classes
      .filter(x => x.fieldOption(field).isDefined && x.name.contains(cls))
      .map(_.name)
      .headOption
      .getOrElse(cls)
    vm.tryGetOrCreateAccessWatchpointRequest(className, field) match {
      case Failure(_) =>
        println(s"Could not create watchpoint for class $cls.$field")
      case Success(pipeline) =>
        pipeline.foreach(block)
        println(s"Breakpoint set at $className.$field")
    }
  }

  var linesHit = Set.empty[Int]

  def createSimplePipeline(implicit vm: ScalaVirtualMachine): Unit = {
    val filename = vm.sourceNameToPaths("Expressions.scala").head
    val lines = vm.availableLinesForFile(filename).get

    lines.foreach { line =>
      val pipeline: IdentityPipeline[BreakpointEvent] =
        vm.getOrCreateBreakpointRequest(filename, line)

      pipeline.foreach { event =>
        linesHit += line
      //println(s"A breakpoint occured at line $line in $filename!")
      }
    }
  }

  def breakAndStepLine(implicit vm: ScalaVirtualMachine): Unit = {
    break("Expressions.scala", 128, { be =>
      val path = be.location().sourcePath()
      val line = be.location().lineNumber()

      println(s"Reached breakpoint for $path:$line")

      import scala.concurrent.ExecutionContext.Implicits.global

      val curThread: ThreadInfoProfile = vm.thread(be.thread())
      vm.stepOverLine(curThread)
        .foreach(se => {
          val path = se.location().sourcePath()
          val line = se.location().lineNumber()

          println(s"Stepped to $path:$line")
        })

    })
  }

  def getDeliteThread(implicit vm: ScalaVirtualMachine): Option[ThreadInfoProfile] = {
    vm.threads.find(_.name.contains("Delite"))
  }

  def getTopDeliteFrame(implicit vm: ScalaVirtualMachine): Option[FrameInfoProfile] = {
    getDeliteThread(vm).map(_.topFrame)
  }

  def showInfoAboutDeliteThread(implicit vm: ScalaVirtualMachine): Unit = {
    getDeliteThread(vm) match {
      case None => println("Could not find the Delite thread")
      case Some(th) =>
        val topFrame: FrameInfoProfile = th.topFrame
        println("topFrame: " + topFrame.toPrettyString)
        println("topFrame.location: " + topFrame.location.toPrettyString)
        println("totalFrames: " + th.totalFrames)
        // val frames: Seq[FrameInfoProfile] = th.frames
        // for (f <- frames) println(f.location.toPrettyString)
        val localVars = topFrame.localVariables
        println("Number of localVariables: " + localVars.length)
        for (v <- localVars) {
          println(str(v))
        }
    }
  }

  import scala.language.dynamics
  trait LocalVariablePath extends Dynamic {
    val elements: Seq[String]
    def selectDynamic(name: String) = NonEmptyVariablePath(elements :+ name)

    override def toString = elements.mkString(".")
  }

  case class CurrentContext() extends LocalVariablePath {
    val elements = Seq.empty
  }
  case class NonEmptyVariablePath(elements: Seq[String]) extends LocalVariablePath

  /**
   * Looks up recursively the fields defined in path on the current value
   */
  def resolvePath(value: ValueInfoProfile, path: String*): Option[ValueInfoProfile] = {
    if (path.isEmpty) {
      Some(value)
    } else if (!value.isObject) {
      None
    } else {
      resolvePath(value.toObjectInfo.fields, path: _*)
    }
  }
  def resolvePath(value: ValueInfoProfile, path: LocalVariablePath): Option[ValueInfoProfile] = {
    resolvePath(value, path.elements: _*)
  }

  /**
   * Looks up recursively the fields defined in path starting with the current set 
   * of fields defined by context
   */
  def resolvePath(context: Seq[VariableInfoProfile], path: String*): Option[ValueInfoProfile] = {
    if (path.isEmpty) {
      None
    } else {
      val nextValue = context.find(_.name == path.head).map(_.toValueInfo)
      nextValue.flatMap(resolvePath(_, path.tail: _*))
    }
  }
  def resolvePath(context: Seq[VariableInfoProfile],
      path: LocalVariablePath): Option[ValueInfoProfile] = {
    resolvePath(context, path.elements: _*)
  }

  def printAllLmsNodes(implicit vm: ScalaVirtualMachine): Unit = {
    val pipeline = vm.getOrCreateClassPrepareRequest()
    val loadExpressions = pipeline.filter(_.referenceType.name == ExpressionsClassName)
    for (event <- loadExpressions) {
      println("Expressions class loaded")
      val cls: ReferenceTypeInfoProfile = vm.`class`(event.referenceType)
      val methods: Seq[MethodInfoProfile] = cls.allMethods
      //for(method <- methods) println(method.toPrettyString)
      //val methodCalls = vm.getOrCreateMethodEntryRequest(ExpressionsClassName, "createDefinition")
      //methodCalls.foreach { m => 
      break("Expressions.scala", 129,
          m => {
        val thread = vm.thread(m.thread)
        val frame = thread.topFrame
        p(_.s)(frame)
        p(_.d)(frame)
      })
    }
  }

  implicit def deliteFrameIsAllIWant(implicit vm: ScalaVirtualMachine): FrameInfoProfile =
    getTopDeliteFrame(vm).get

  def lines(implicit frame: FrameInfoProfile): Unit = {
    val loc = frame.location
    println(s"Lines for ${loc.toPrettyString} :")
    Definitions.files.find(_.fullName == loc.sourcePath) match {
      case None => println(s"Could not find source for ${loc.sourceName}")
      case Some(f) =>
        val lines = f.lines.drop(loc.lineNumber - 1).take(10)
        val maxPrefixLen = (loc.lineNumber + 10).toString.length
        lines.zipWithIndex.foreach {
          case (line, idx) =>
            val lineNum = idx + loc.lineNumber
            val padding = " " * (maxPrefixLen - lineNum.toString.length)
            println(s"$padding$lineNum : $line")
        }
    }
  }

  def value(query: CurrentContext => LocalVariablePath = identity)(
      implicit frame: FrameInfoProfile): Option[ValueInfoProfile] = {
    val curContext = CurrentContext()
    val path = query(curContext)
    resolvePath(frame.allVariables, path)
  }

  def v(query: CurrentContext => LocalVariablePath = identity)(
      implicit frame: FrameInfoProfile) = {
    new utils.DynamicWrappers.ValueScope(value(query).get)
  }

  def p(block: CurrentContext => LocalVariablePath = identity)(
      implicit frame: FrameInfoProfile): Unit = {
    val context = CurrentContext()
    val path = block(context)
    if (path.elements.isEmpty) {
      for (value <- frame.allVariables) {
        println(value.toPrettyString)
      }
    } else {
      val prefixString = s"$path = "
      resolvePath(frame.allVariables, path) match {
        case None => println(s"Could not find $path")
        case Some(value) if !value.isObject =>
          println(prefixString + value.toPrettyString)
        case Some(box) if BoxedPrimitives.contains(box.typeInfo.name) =>
          println(prefixString + unbox(box.toObjectInfo).toPrettyString)
        case Some(obj) => {
          val prefix = classPrefix(obj.typeInfo.name)
          println(s"$prefixString$prefix(")
          for (value <- obj.toObjectInfo.fields) {
            println(s"  ${value.toPrettyString}")
          }
          println(")")
        }
      }
    }
  }
}
