package de.tu_darmstadt.veritas.backend.transformation.lowlevel

import de.tu_darmstadt.veritas.backend.veritas._
import de.tu_darmstadt.veritas.backend.transformation.ModuleTransformation

/**
 * (blindly) desugars FunctionPatVar and FunctionExpVar to FunctionPatApp/FunctionExpApp with zero arguments
 */
object VarToApp0 extends ModuleTransformation with CollectConstructorNames {
  override def transFunctionExps(f: FunctionExp): Seq[FunctionExp] =
    withSuper(super.transFunctionExps(f)) {
      case v @ FunctionExpVar(n) => if (consNames contains n) Seq(FunctionExpApp(n)) else Seq(v)
    }

  override def transFunctionExp(f: FunctionExp): FunctionExp =
    withSuper(super.transFunctionExp(f)) {
      case v @ FunctionExpVar(n) => if (consNames contains n) FunctionExpApp(n) else v
    }

  override def transFunctionPatterns(p: FunctionPattern): Seq[FunctionPattern] =
    withSuper(super.transFunctionPatterns(p)) {
      case p @ FunctionPatVar(n) => if (consNames contains n) Seq(FunctionPatApp(n)) else Seq(p)
    }
}
