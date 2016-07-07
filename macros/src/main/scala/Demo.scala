import language.experimental.macros
import scala.reflect.macros.whitebox.Context

object Macros {

  // Returns the tree of `a` after the typer, printed as source code.
  def desugar(a: Any): String = macro desugarImpl

  def desugarImpl(c: Context)(a: c.Expr[Any]) = {
    import c.universe._

    val s = show(a.tree)
    c.Expr(Literal(Constant(s)))
  }

  def fields[T]: List[String] = macro fieldsImpl[T]

  def fieldsImpl[T: c.WeakTypeTag](c: Context): c.Expr[List[String]] = {
    import c.universe._

    val listApply = Select(reify(List).tree, TermName("apply"))

    val fields = weakTypeOf[T].decls.collect {
      case m: MethodSymbol if m.isCaseAccessor => Literal(Constant(m.name.toString))
    }

    c.Expr[List[String]](Apply(listApply, fields.toList))
  }

  // TODO a macro to create a localizer from the type
  // eg: localize[T]

}
