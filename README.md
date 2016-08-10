# lms-debugger
It is well known that lms can sometime be very confusing for people working on it. This project is aimed at providing some debugging utility for lms to step through the staging process.


## Installation and usage
Add this line to your `.sbtopts` file in your lms project directory:

```sh
-J-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006
```

Add your source directories to the definitions file (`src/main/scala/org/lmsdbg/utils/Definitions.scala`)

```scala
val HyperDSLFolder = new File("<path to hyperdsl>")
val ProjectFolders = Seq(HyperDSLFolder)
// or
val LMSFolder = new File("<path to lms>")
val DeliteFolder = new File("<path to delite>")
val ProjectFolders = Seq(LMSFolder, DeliteFolder)
```

Run sbt in the project you want to debug (!! make sure you see the second line !!)

```bash
hyperdsl> sbt
Listening for transport dt_socket at address: 5006 
[info] Loading global plugins from /Users/dengels/.sbt/0.13/plugins
[info] Updating {file:/Users/dengels/.sbt/0.13/plugins/}global-plugins...
```

Run sbt console in the debugger project & register breakpoints

```scala
lms-debugger> sbt console
[info] Loading global plugins from /Users/dengels/.sbt/0.13/plugins

...

[info] Starting scala interpreter...
[info] 
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
SLF4J: Defaulting to no-operation (NOP) logger implementation
SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.
import org.lmsdbg._
import Main._
import utils._
import Printer._
import DynamicWrappers._
vm: org.scaladebugger.api.virtualmachines.ScalaVirtualMachine = org.scaladebugger.api.virtualmachines.StandardScalaVirtualMachine@795bf675
import scala.language.postfixOps
import Localizers._
import LMSInfo._
import org.scaladebugger.api.profiles.traits.info.ValueInfoProfile
Welcome to Scala 2.11.8 (Java HotSpot(TM) 64-Bit Server VM, Java 1.8.0_20).
Type in expressions for evaluation. Or try :help.

scala> break("CodeMotion.scala", 14)
File not loaded yet, no gurantees made on correct line
Breakpoint set at scala/virtualization/lms/internal/CodeMotion.scala:14
```

You can the run the debugged program until it hits the breakpoint 

```scala
scala> Hit breakpoint at CodeMotion.scala:14
```

You can then inspect the local variables with `p()`

```scala
scala> p()
$this = Instance of scala.virtualization.lms.util.ExportTransforms$$anon$1 (0x2737)
currentScope = Instance of scala.collection.immutable.$colon$colon (0x2738)
result = Instance of scala.collection.immutable.$colon$colon (0x2739)

scala> p(_.currentScope)
currentScope = $colon$colon(
  head = Instance of scala.virtualization.lms.internal.Expressions$TP (0x273A)
  tl = Instance of scala.collection.immutable.$colon$colon (0x273B)
)
```

To get useful helper methods you can also use `&` instead

```scala
scala> &.currentScope
res3: org.lmsdbg.utils.DynamicWrappers.Scope = List(...)

scala> &.currentScope.asList
res4: List[org.lmsdbg.utils.DynamicWrappers.LMSValueScope] = List(class Expressions$TP{sym = Sym(5), rhs = Instance of scala.virtualization.lms.internal.Effects$Reflect (0x2642)}, ...
```

We are heavily biased towards definition, so each time a symbol is inspected, it is automatically translated to the corresponding `Def` if it defines one. The symbol can be retrieved by using the `symbolId` method

```scala
scala> &.currentScope.asList.map(_.sym)
res5: List[org.lmsdbg.utils.DynamicWrappers.Scope] = List(class Effects$Reflect{x = Instance of ppl.delite.framework.datastructures.DeliteArrayOpsExp$DeliteArrayNew (0x2786), ...

scala> &.currentScope.asList.map(_.sym.symbolId)
res6: List[Option[Int]] = List(Some(5), Some(6), ...)
```

You can also define mock classes to reify remote objects on the debugging VM. 

```scala
scala> &.currentScope.asList.head.as[mock.lms.Expressions.TP]
res10: org.lmsdbg.mock.lms.Expressions.TP = TP(Sym(5),class Effects$Reflect{x = Instance of ppl.delite.framework.datastructures.DeliteArrayOpsExp$DeliteArrayNew (0x2787), summary = Summary.Alloc, deps = Nil})
```

To do so, just define a mock class (see org.lmsdbg.mock.lms) with an associated [Localizer](https://github.com/Stanford-PDM/lms-debugger/blob/master/src/main/scala/org/lmsdbg/utils/Localizers.scala). The mock class can be arbitrarily precise, you can always fallback to a dynamic value by defining it's type as `Scope`, as for example is the first field in the above example.

## Design
We rely heavily on the [scala-debugger](http://scala-debugger.org/) project to provide basic debugging capabilities. We then add a few lightweight wrappers for simplified use with lms:

- Automatic connection to lms process on launch of the console
- Symbols recording & automatic `Sym` to `Def` mapping
- Simple DSL to examine remote vm state
- Mock lms classes to reify remote trees in local vm for further inspection