package de.tu_darmstadt.veritas.backend.transformation

import de.tu_darmstadt.veritas.backend.fof._
import de.tu_darmstadt.veritas.backend.util.BackendError
import de.tu_darmstadt.veritas.backend.ast._
import de.tu_darmstadt.veritas.backend.ast.function._
import de.tu_darmstadt.veritas.backend.util.prettyprint.PrettyPrintable
import de.tu_darmstadt.veritas.backend.util.prettyprint.PrettyPrintWriter
import de.tu_darmstadt.veritas.backend.util.FreeVariables
import de.tu_darmstadt.veritas.backend.fof.Variable

/**
 * Transforms Core TSSL (Veritas) Modules to FOF syntax
 *
 * Structure of Core Modules
 * - no imports
 * - section with "symbol declarations" (constructor decls, const decls, function sigs...) (can be empty)
 * - section with n axioms, where typing judgments were already transformed to some typed function! (can be empty)
 * - exactly one goal! (which must not be followed by other axioms, constructors etc, which would be out of scope!)
 */
class ToFof {
  def toFofFile(coreModule: Module): FofFile = coreModule match {
    case Module(name, Seq(), body) => {
      val goal = coreModule.getOnlyGoal
      val axioms = coreModule.defs.collect { case Axioms(axioms) => axioms }.flatten

      val transformedAxioms = axioms map (typingRuleToFof(_, Axiom))
      val transformedGoal = typingRuleToFof(goal, Conjecture)

      FofFile(name + ".fof", goal.name, transformedAxioms :+ transformedGoal)
    }
    case Module(name, _, _) => throw TransformationError(s"(Core TSSL) Module $name still contained imports")
  }

  private def typingRuleToFof(rule: TypingRule, role: FormulaRole): FofAnnotated =
    FofAnnotated(rule.name, role, typingRuleToFof(rule.premises, rule.consequences))

  private def typingRuleToFof(prems: Seq[TypingRuleJudgment], conseqs: Seq[TypingRuleJudgment]): Fof = {
    val quantifiedVars = FreeVariables.freeVariables(prems ++ conseqs) map toUntypedVar
    val transformedprems = prems map jdgToFof

    if (transformedprems == Seq(True) || transformedprems == Seq())
      ForAll(quantifiedVars.toSeq, Parenthesized(And(conseqs map jdgToFof)))
    else
      ForAll(quantifiedVars.toSeq, Parenthesized(
        Impl(Parenthesized(And(transformedprems)), Parenthesized(And(conseqs map jdgToFof)))))
  }

  /**
   * translates individual clauses (premises or conclusion)
   */
  private def jdgToFof(jdg: TypingRuleJudgment): FofUnitary =
    jdg match {
      case FunctionExpJudgment(f) => functionExpToFof(f)
      case ExistsJudgment(vars, jdglist) => {
        val mappedvars = vars map toUntypedVar
        if (mappedvars.isEmpty)
          Parenthesized(And(jdglist map jdgToFof))
        else
          Exists(mappedvars, Parenthesized(And(jdglist map jdgToFof)))
      }
      case ForallJudgment(vars, jdglist) => {
        val mappedvars = vars map toUntypedVar
        if (mappedvars.isEmpty)
          Parenthesized(And(jdglist map jdgToFof))
        else
          ForAll(mappedvars, Parenthesized(And(jdglist map jdgToFof)))
      }
      case NotJudgment(jdg) => Not(jdgToFof(jdg))
      case OrJudgment(ors) => {
        val translatedors = ors map (orcase => Parenthesized(And(orcase map jdgToFof)))
        if (translatedors.isEmpty)
          True
        else if (translatedors.length == 1)
          translatedors.head
        else Parenthesized(Or(translatedors))
      }
      case _ => throw TransformationError("Encountered unsupported (not Core) judgment while translating a goal or axiom (e.g. typing judgment): " + jdg)
    }

  private def toUntypedVar(v: MetaVar): UntypedVariable = UntypedVariable(v.name)

  /**
   * translate individual function expressions
   * outer function expressions cannot be MetaVars, since a MetaVar cannot be translated to a FofUnitary
   */
  private def functionExpToFof(f: FunctionExp): FofUnitary =
    f match {
      case FunctionExpNot(f)       => Not(functionExpToFof(f))
      case FunctionExpEq(f1, f2)   => Eq(functionExpMetaToFof(f1), functionExpMetaToFof(f2))
      case FunctionExpNeq(f1, f2)  => NeqEq(functionExpMetaToFof(f1), functionExpMetaToFof(f2))
      case FunctionExpAnd(l, r)    => Parenthesized(And(Seq(functionExpToFof(l), functionExpToFof(r))))
      case FunctionExpOr(l, r)     => Parenthesized(Or(Seq(functionExpToFof(l), functionExpToFof(r))))
      case FunctionExpBiImpl(l, r) => Parenthesized(BiImpl(functionExpToFof(l), functionExpToFof(r)))
      case FunctionExpApp(n, args) => Appl(UntypedFunSymbol(n), args map functionExpMetaToFof)
      case FunctionExpTrue         => True
      case FunctionExpFalse        => False
      case _                       => throw TransformationError("Encountered unsupported (not Core) function expression while translating (e.g. if or let expression): " + f)
    }

  /**
   * translate function expressions including MetaVars to terms
   */
  private def functionExpMetaToFof(f: FunctionExpMeta): Term =
    // the only two constructs which can be turned into a term are
    // FunctionMeta and FunctionExpApp (Appl is both a Term and a FofUnitary!)
    // therefore, encountering any other FunctionExpMeta must result in an error!
    f match {
      case FunctionMeta(MetaVar(m)) => UntypedVariable(m)
      case FunctionExpApp(n, args)  => Appl(UntypedFunSymbol(n), args map functionExpMetaToFof)
      case _                        => throw TransformationError("Encountered unexpected construct in functionExpMetaToFof: " + f)
    }
}

object ToFof {
  def apply(m: Module) = (new ToFof).toFofFile(m)
}
