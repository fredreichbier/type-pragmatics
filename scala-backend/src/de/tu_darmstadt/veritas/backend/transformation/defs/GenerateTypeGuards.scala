package de.tu_darmstadt.veritas.backend.transformation.defs

import de.tu_darmstadt.veritas.backend.util.FreshNames
import de.tu_darmstadt.veritas.backend.veritas._
import de.tu_darmstadt.veritas.backend.veritas.FunctionExpJudgment._
import de.tu_darmstadt.veritas.backend.transformation.TransformationError
import de.tu_darmstadt.veritas.backend.transformation.ModuleTransformation
import de.tu_darmstadt.veritas.backend.transformation.collect.CollectTypes
import de.tu_darmstadt.veritas.backend.veritas.function._
import de.tu_darmstadt.veritas.backend.Configuration

/**
 * For each SortDef we generate a type guard and for each ConstructorDecl we generate an axiom for the guard.
 *
 * cons D : T
 * ==>
 * axiom $T(D)
 *
 * cons E : T * U -> V
 * ==>
 * axiom $T(x), $U(y) <-> $V(E(x,y))
 *
 * Also works with Local/Strategy blocks.
 */
class GenerateTypeGuards extends ModuleTransformation {

  val ruleprefix = "guard$"

  /**
   * function that determines where and which type guards are inserted
   * override to control at which point and in which order guard axioms are inserted
   *
   * default: does not insert anything!
   */
  def insertGuardAxsHere(mdef: ModuleDef): Seq[ModuleDef] = Seq(mdef)

  override def transModuleDefs(mdef: ModuleDef): Seq[ModuleDef] = {
    withSuper(super.transModuleDefs(mdef)) {
      case mdef => insertGuardAxsHere(mdef)
    }
  }

  def guard(name: String): String = "guard" + name

  def guardCall(sort: String, arg: FunctionExpMeta): FunctionExpApp =
    FunctionExpApp(guard(sort), Seq(arg))

  def makeGuardSignature(dataType: String): FunctionSig = {
    FunctionSig(guard(dataType), Seq(SortRef(dataType)), SortRef(DataType.Bool))
  }

  def makeGuardAxiom(cd: DataTypeConstructor, dataType: String): TypingRule = {
    val fresh = new FreshNames
    val vars = cd.in.map(sort => FunctionMeta(MetaVar(fresh.freshName(sort.name))))

    // all vars are well-typed
    val argGuards = cd.in.zip(vars).map {
      case (sort, v) =>
        guardCall(sort.name, v)
    }
    val argCond = FunctionExpAnd(argGuards)

    // the constructor call yields something well-typed
    val consCall = FunctionExpApp(cd.name, vars)
    val consCond = guardCall(dataType, consCall)

    val consequence = FunctionExpJudgment(FunctionExpBiImpl(argCond, consCond))

    val name = s"$ruleprefix-$dataType-${cd.name}"
    val rule = TypingRule(name, Seq(), Seq(consequence))
    rule
  }

  def makeDomainAxiom(dataType: String, constrs: Seq[DataTypeConstructor]): TypingRule = {
    val name = s"$ruleprefix-dom-$dataType"
    val v = FunctionMeta(MetaVar("X"))

    // all v. guard(v) => (guard(c1_i)&v=c1(c1_1...c1_k) | ... | guard(cn_i)&v=cn(cn_1...cn_k))
    // for n=0, simplifies to all v. not guard(v)
    TypingRule(
      name,
      Seq(FunctionExpJudgment(guardCall(dataType, v))),
      Seq(OrJudgment(constrs map (c => Seq(makeEqConsFormula(c, v))))))
  }

  def makeEqConsFormula(cd: DataTypeConstructor, v: FunctionMeta): TypingRuleJudgment = {
    val fresh = new FreshNames
    val vars = cd.in.map(sort => MetaVar(fresh.freshName(sort.name)))

    val eq = FunctionExpEq(v, FunctionExpApp(cd.name, vars map (FunctionMeta(_))))

    ExistsJudgment(vars, Seq(FunctionExpJudgment(eq)))
  }
}

object GenerateAllTypeGuards extends GenerateTypeGuards {
  override def insertGuardAxsHere(mdef: ModuleDef): Seq[ModuleDef] =
    mdef match {
      case dt @ DataType(open, name, constrs) =>
        val guardFunctions = Functions(Seq(FunctionDef(makeGuardSignature(name), Seq())))
        val guardAxioms = constrs map (makeGuardAxiom(_, name))
        if (open)
          Seq(dt, guardFunctions, Axioms(guardAxioms))
        else {
          val domAxiom = makeDomainAxiom(name, constrs)
          Seq(dt, guardFunctions, Axioms(guardAxioms :+ domAxiom))
        }
      case _ => super.insertGuardAxsHere(mdef)
    }
}

/**
 * inserts lightweight type guards (i.e. domain axiom only!) for existentially quantified variables
 * in execution goals
 */
object GenerateExecutionGuards extends GenerateTypeGuards with CollectTypes {
  override def insertGuardAxsHere(mdef: ModuleDef): Seq[ModuleDef] =
    mdef match {
      case goals @ Goals(gs, t) => {
        val axioms = for (tr <- gs) yield {
          tr match {
            case tr @ TypingRule(n, prems, conss) if (n.startsWith("execution")) => {
              findExGuardsforTypingRule(tr).distinct //rule out potential duplicates!
            }
            case _ => Seq()
          }
        }
        Seq(Local(axioms.flatten :+ goals))
      }
      case _ => super.insertGuardAxsHere(mdef)
    }

  /**
   * creates (lightweight) type guards for existentially quantified variables in goal
   */
  private def findExGuardsforTypingRule(tr: TypingRule): Seq[ModuleDef] = {
    val vars = inferMetavarTypes(tr)
    val extypes = (ex_quantifiedTypesIn(tr.premises) ++ ex_quantifiedTypesIn(tr.consequences)).distinct

    val guardmdefs = for (name <- extypes) yield {
      val guardFunctions = Functions(Seq(FunctionDef(makeGuardSignature(name), Seq())))
      val open = dataTypes(name)._1
      val constrs = dataTypes(name)._2
      if (!open) {
        val domAxioms = makeTwoDomainAxioms(name, constrs)
        Seq(guardFunctions, Axioms(domAxioms))
      } else Seq(guardFunctions)
    }
    guardmdefs.flatten
  }

  private def ex_quantifiedTypesIn(trjseq: Seq[TypingRuleJudgment]): Seq[String] = {
    (for (trj <- trjseq) yield {
      trj match {
        case ForallJudgment(vl, jdg) => ex_quantifiedTypesIn(jdg)
        case ExistsJudgment(vl, jdg) => (vl map { v => v.sortType.name }) ++ ex_quantifiedTypesIn(jdg)
        case NotJudgment(jdg)        => ex_quantifiedTypesIn(Seq(jdg))
        case OrJudgment(orc)         => orc flatMap (or => ex_quantifiedTypesIn(or))
        case _                       => Seq()
      }
    }).flatten
  }

  def makeTwoDomainAxioms(dataType: String, constrs: Seq[DataTypeConstructor]): Seq[TypingRule] = {
    val name = s"$ruleprefix-dom-$dataType"
    val v = FunctionMeta(MetaVar("X"))

    // for execution guards, provide domain axioms in both directions
    // TODO: is this really sound, for barefof and tff? (at least for tff, it should be ok)
    Seq(TypingRule(
      name,
      Seq(FunctionExpJudgment(guardCall(dataType, v))),
      Seq(OrJudgment(constrs map (c => Seq(makeEqConsFormula(c, v)))))),
      TypingRule(
      name,
      Seq(OrJudgment(constrs map (c => Seq(makeEqConsFormula(c, v))))),
      Seq(FunctionExpJudgment(guardCall(dataType, v)))))
  }

}

