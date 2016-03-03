package de.tu_darmstadt.veritas.backend

import de.tu_darmstadt.veritas.backend.veritas._
import de.tu_darmstadt.veritas.backend.Configuration._
import scala.collection.immutable.TreeMap
import de.tu_darmstadt.veritas.backend.transformation._
import de.tu_darmstadt.veritas.backend.transformation.defs._
import de.tu_darmstadt.veritas.backend.transformation.imports._
import de.tu_darmstadt.veritas.backend.transformation.lowlevel._
import de.tu_darmstadt.veritas.backend.util.prettyprint._
import de.tu_darmstadt.veritas.backend.fof.FofFile
import de.tu_darmstadt.veritas.backend.transformation.lowlevel.FilterGoalModules

// to change the study parameters, manipulate vals typeEncodings or studyConfiguration in EncodingComparisonStudy below

/**
 * determine the final encoding of module
 */
trait Typing {
  def finalEncoding(m: Module)(implicit config: Configuration): PrettyPrintableFile
}

//just fof, completely untyped
case object FofBare extends Typing {
  override def finalEncoding(m: Module)(implicit config: Configuration) = ToFof.toFofFile(m)
}
//fof with type guards
case object FofGuard extends Typing {
  override def finalEncoding(m: Module)(implicit config: Configuration) = ToFof.toFofFile(m)
}
//tff encoding
case object Tff extends Typing {
  override def finalEncoding(m: Module)(implicit config: Configuration) = ToTff.toTffFile(m)
}

case class AlternativeTyping(select: Configuration => Typing) extends Typing {
  override def finalEncoding(m: Module)(implicit config: Configuration) =
    select(config).finalEncoding(m)

}

object ConstructorTrans extends Alternative(selectConfig(FinalEncoding) {
  case FinalEncoding.BareFOF =>
    GenerateCtorAxiomsUntyped
  case FinalEncoding.GuardedFOF =>
    SeqTrans(GenerateCtorAxiomsTyped, GenerateAllTypeGuards)
  case FinalEncoding.TFF =>
    GenerateCtorAxiomsTyped
})

object BasicTrans extends SeqTrans(
  FilterGoalModules, //optimization: only interested in modules with goals!
  ResolveImports,
  VarToApp0,
  DesugarLemmas,
  ConstructorTrans,
  GenerateDiffAxiomsForConsts,
  FunctionEqToAxiomsSimple,
  TranslateAllTypingJudgments)

/**
 * determine which different problems are encoded ("Fragestellungen")
 */
object ProblemTrans extends Alternative(selectConfig(Problem) {
  case Problem.Consistency =>
    SeqTrans(SplitModulesByGoal(""), MoveDeclsToFront, SetupConsistencyCheck)
  case Problem.Proof =>
    SeqTrans(SplitModulesByGoal("proof"), MoveDeclsToFront)
  case Problem.Test =>
    SeqTrans(SplitModulesByGoal("test"), MoveDeclsToFront)
  case Problem.Execution =>
    SeqTrans(SplitModulesByGoal("execution"), MoveDeclsToFront)
  case Problem.Synthesis =>
    SeqTrans(SplitModulesByGoal("synthesis"), MoveDeclsToFront)
  case Problem.Counterexample =>
    SeqTrans(SplitModulesByGoal("counterexample"), MoveDeclsToFront)
  case Problem.All =>
    SeqTrans(SplitModulesByGoal(""), MoveDeclsToFront)
})

object GuardsTrans extends Alternative(selectConfig(FinalEncoding) {
  // insert type guards for quantified metavariables
  case FinalEncoding.GuardedFOF =>
    InsertTypeGuardsForAllMetavars
  //insert only specific type guards for execution goals
  case _ =>
    Optional(SeqTrans(GenerateExecutionGuards, InsertTypeGuardsInExecutionGoals),
      c => (ifConfig(Problem, Problem.Execution)(c) || ifConfig(Problem, Problem.Consistency)(c)))
})

/**
 * determine whether subformulas in axioms/goals are inlined or named with an additional variable
 * (which adds an equation to the set of premises of axioms/goals)
 */
object VariableTrans extends Alternative(selectConfig(VariableEncoding) {
  // add InlineEverythingFP?
  case VariableEncoding.Unchanged =>
    Identity
  case VariableEncoding.NameEverything =>
    NameEverythingButMetaVars
  case VariableEncoding.InlineEverything =>
    InlineFP
  case VariableEncoding.NameParamsAndResults =>
    SeqTrans(NameFunctionResultsOnly, NameSubstituteFunctionDefParametersOnly)
})

object SimplificationTrans extends Alternative(selectConfig(Simplification) {
  case Simplification.None =>
    Identity
  case Simplification.Logical =>
    LogicalTermOptimization
  case Simplification.LogicalAndConstructors =>
    SeqTrans(ConstructorSimplification, LogicalTermOptimization)
})

object MainTrans extends SeqTrans(
  // desugar Veritas constructs
  BasicTrans,
  // determines whether and which inversion axioms are generated for functions/typing rules
  // update: always generate function inversion axioms!
  TotalFunctionInversionAxioms, // ignored: InversionAll
  // insert type guards for quantified metavariables
  GuardsTrans,
  // variable inlining/extraction
  // determines whether logical optimizations take place prior to fof/tff encoding
  Fixpoint(SeqTrans(SimplificationTrans, VariableTrans)),
  // select problem
  ProblemTrans)

object TypingTrans extends AlternativeTyping(selectConfig(FinalEncoding) {
  case FinalEncoding.BareFOF    => FofBare
  case FinalEncoding.GuardedFOF => FofGuard
  case FinalEncoding.TFF        => Tff
})

case class EncodingComparison(vm: VariabilityModel, module: Module) extends Iterable[(Configuration, Seq[PrettyPrintableFile])] {
  private var _lastConfig: Configuration = _
  def lastConfig = _lastConfig

  def iterator = vm.iterator.map { config =>
    _lastConfig = config
    val mods = MainTrans(Seq(module))(config)
    val files = mods.map(m => TypingTrans.finalEncoding(m)(config))
    (config, files)
  }
}



//  var encodingStrategies: Map[String, Seq[Module] => Seq[PrettyPrintableFile]] = TreeMap(
//    ("inconsistencies-partial-functions" ->
//      ((sm: Seq[Module]) => {
//        val transformedModules =
//          SetupConsistencyCheck(
//            MoveDeclsToFront(
//              SplitModulesByGoal(
//                LogicalTermOptimization(
//                  AllFunctionInversionAxioms(
//                    TranslateTypingJudgmentSimpleToFunction(
//                      TranslateTypingJudgmentToFunction(
//                        FunctionEqToAxiomsSimple(
//                          GenerateCtorAxioms(
//                            DesugarLemmas(
//                              VarToApp0(
//                                ReplaceImportsWithModuleDefs(ResolveImports(sm)))))))))))))
//        transformedModules map ToFof.toFofFile
//      })),
//    ("inconsistencies-wrong-constant-encoding" ->
//      ((sm: Seq[Module]) => {
//        val transformedModules =
//          SetupConsistencyCheck(
//            MoveDeclsToFront(
//              SplitModulesByGoal(
//                LogicalTermOptimization(
//                  AllFunctionInversionAxioms(
//                    TranslateTypingJudgmentSimpleToFunction(
//                      TranslateTypingJudgmentToFunction(
//                        FunctionEqToAxiomsSimple(
//                          GenerateCtorAxioms(
//                            DesugarLemmas(
//                              VarToApp0(
//                                ReplaceImportsWithModuleDefs(ResolveImports(sm)))))))))))))
//        transformedModules map ToFof.toFofFile
//      }))) ++ buildStrategies()
