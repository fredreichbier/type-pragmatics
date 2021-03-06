package de.tu_darmstadt.veritas.VerificationInfrastructure

import java.io.File

import de.tu_darmstadt.veritas.VerificationInfrastructure.specqueries.VeritasSpecEnquirer
import de.tu_darmstadt.veritas.VerificationInfrastructure.tactics._
import de.tu_darmstadt.veritas.VerificationInfrastructure.verifier._
import de.tu_darmstadt.veritas.VerificationInfrastructure.visualizer.Dot
import de.tu_darmstadt.veritas.backend.ast.function._
import de.tu_darmstadt.veritas.backend.ast._

class QLSoundnessProofGraph(file: File) {
  import QLSoundnessProofSteps._

  val g: ProofGraphXodus[VeritasConstruct, VeritasFormula] with ProofGraphTraversals[VeritasConstruct, VeritasFormula] =
    new ProofGraphXodus[VeritasConstruct, VeritasFormula](file) with ProofGraphTraversals[VeritasConstruct, VeritasFormula]
  SQLSoundnessProofGraph.initializeGraphTypes(g)

  val specenq = new VeritasSpecEnquirer(fullQLspec)

  def getCases(metaVar: MetaVar, goal: VeritasFormula): Map[String, VeritasConstruct] = {
    val goalBody = specenq.getQuantifiedBody(goal)
    val ivCases = specenq.getCases(metaVar, goalBody) map { case (n, ic) =>
      (n, specenq.assignCaseVariables(ic, goalBody))
    }
    ivCases
  }

  def getIntroducedMetaVars(expression: VeritasConstruct): Seq[MetaVar] = {
    def collectMetaVars(args: Seq[FunctionExpMeta]): Seq[MetaVar] =
      args.collect { case mv: FunctionMeta => mv.metavar }

    expression.asInstanceOf[FunctionExp] match {
      //case FunctionExpJudgment(FunctionExpEq(rhs, FunctionExpApp(_, args))) => collectMetaVars(args)
      //case FunctionExpJudgment(FunctionExpNeq(rhs, FunctionExpApp(_, args))) => collectMetaVars(args)
      case FunctionExpApp(_, args) => collectMetaVars(args)
      case FunctionExpNot(FunctionExpApp(_, args)) => collectMetaVars(args)
      case _ => Seq()
    }
  }

  //progress root obligation
  val progressObligation: g.Obligation = g.newObligation(fullQLspec, QLProgress)
  g.storeObligation("QL progress", progressObligation)

  private val rootInduction = StructuralInduction(MetaVar("q"), specenq)
  // first proof step: structural induction
  val rootinductionPS: g.ProofStep = g.applyTactic(progressObligation, rootInduction)

  val rootobl = g.findObligation("QL progress").get
  val rootsubobs = g.requiredObls(rootinductionPS)
  //val casenames = rootInduction.enumerateCaseNames[g.Obligation](rootsubobs)
  //val caseedges: Seq[StructInductCase[VeritasConstruct, VeritasFormula]] =
  //  (rootInduction.enumerateCases(rootsubobs) map
  //    {case (k, v) => v._2.asInstanceOf[StructInductCase[VeritasConstruct, VeritasFormula]]}).toSeq
  val matchingConds = getCases(MetaVar("q"), rootobl.goal)

  //apply simply Solve-tactic to qempty base case
  val qemptyObl = rootInduction.selectCase("QL-Progressqempty", rootsubobs)
  val qemptyPS = g.applyTactic(qemptyObl, Solve[VeritasConstruct, VeritasFormula])

  val qsingleObl = rootInduction.selectCase("QL-Progressqsingle", rootsubobs)
  val qsingleCaseDistinction = StructuralCaseDistinction(getIntroducedMetaVars(matchingConds("qsingle")).head, specenq)
  val qsinglePS = g.applyTactic(qsingleObl, qsingleCaseDistinction)

  val qsinglesubobs = g.requiredObls(qsinglePS)
  val qsingleMatchingConds = getCases(getIntroducedMetaVars(matchingConds("qsingle")).head, qsinglesubobs.toSeq(1)._1.goal)

  val questionPS = g.applyTactic(qsinglesubobs.toSeq(0)._1, Solve[VeritasConstruct, VeritasFormula])
  val valueCaseDistinction = BooleanCaseDistinction(FunctionExpJudgment(FunctionExpApp("expIsValue", Seq(FunctionMeta(getIntroducedMetaVars(qsingleMatchingConds("value"))(2))))), specenq)

  val valuePS = g.applyTactic(qsinglesubobs.toSeq(1)._1, valueCaseDistinction)
  val valueCases = g.requiredObls(valuePS)

  val progressLookupQMapLemmaApplication = LemmaApplication(Seq(LookupQMapProgress), specenq)
  val askPS = g.applyTactic(qsinglesubobs.toSeq(3)._1, progressLookupQMapLemmaApplication)

  // apply CaseDistinction to qseq case
  val qseqObl = rootInduction.selectCase("QL-Progressqseq", rootsubobs)
  val qseqCaseDistinction = EqualityCaseDistinction(getIntroducedMetaVars(matchingConds("qseq")).head, FunctionExpApp("qempty", Nil), specenq)
  val qseqcasePS = g.applyTactic(qseqObl, qseqCaseDistinction)

  val qseqsubobs = g.requiredObls(qseqcasePS)
  val qseqsubPS = qseqsubobs.toSeq.map { case (obl, info) =>
    g.applyTactic(obl, Solve[VeritasConstruct, VeritasFormula])
  }

  val expIsValueTruePS = g.applyTactic(valueCases.toSeq.head._1, Solve[VeritasConstruct, VeritasFormula])

  val progressReduceExpLemmaApplication = LemmaApplication(Seq(ReduceExpProgress), specenq)
  val expIsValueFalsePS = g.applyTactic(valueCases.toSeq.last._1, progressReduceExpLemmaApplication)
  val progressReduceExpSubs = g.requiredObls(expIsValueFalsePS)

  val reduceExpInduction = StructuralInduction(MetaVar("exp"), specenq)
  val progressReduceExpInductionPS = g.applyTactic(progressReduceExpSubs.toSeq.head._1, reduceExpInduction)

  val progressReduceExpInductionCases = g.requiredObls(progressReduceExpInductionPS).toSeq
  val progressReduceExpMatchingConds = getCases(MetaVar("exp"), ReduceExpProgress)

  val constantProgressReduceExpPS = g.applyTactic(progressReduceExpInductionCases.head._1, Solve[VeritasConstruct, VeritasFormula])

  val progressLookupAnsMapLemma = LemmaApplication(Seq(LookupAnsMapProgress), specenq)
  val qvarPS = g.applyTactic(progressReduceExpInductionCases(1)._1, progressLookupAnsMapLemma)
  val progressLookupAnsMap = g.requiredObls(qvarPS).toSeq.head._1

  val progressLookupAnsMapInduction = StructuralInduction(MetaVar("am"), specenq)
  val progressLookupAnsMapInductionPS = g.applyTactic(progressLookupAnsMap, progressLookupAnsMapInduction)
  val progressLookupAnsMapInductionCases = g.requiredObls(progressLookupAnsMapInductionPS).toSeq
  val progressLookupAnsMapInductionCasesPS =  progressLookupAnsMapInductionCases.map { case (obl, info) =>
    g.applyTactic(obl, Solve[VeritasConstruct, VeritasFormula])
  }

  val binopProgressReduceExpDistinction = BooleanCaseDistinction(FunctionExpJudgment(FunctionExpAnd(
        FunctionExpApp("expIsValue", Seq(FunctionMeta(getIntroducedMetaVars(progressReduceExpMatchingConds("binop")).head))),
          FunctionExpApp("expIsValue", Seq(FunctionMeta(getIntroducedMetaVars(progressReduceExpMatchingConds("binop")).last)))
      )), specenq)
  val binopProgressReduceExpPS = g.applyTactic(progressReduceExpInductionCases(2)._1, binopProgressReduceExpDistinction)
  val binopProgressReduceExpCases = g.requiredObls(binopProgressReduceExpPS).toSeq
  val binopProgressReduceExpIsValuePS = g.applyTactic(binopProgressReduceExpCases.head._1, Solve[VeritasConstruct, VeritasFormula])

  val binopProgressReduceExpNoValueDistinction = BooleanCaseDistinction(FunctionExpJudgment(FunctionExpApp("isSomeExp", Seq(FunctionExpApp("reduceExp",
      Seq(FunctionMeta(getIntroducedMetaVars(progressReduceExpMatchingConds("binop")).head), FunctionMeta(MetaVar("am"))))))), specenq)

  val binopProgressReduceExpNoValuePS = g.applyTactic(binopProgressReduceExpCases.last._1, binopProgressReduceExpNoValueDistinction)
  val binopProgressReduceExpNoValueCases = g.requiredObls(binopProgressReduceExpNoValuePS)
  val binopProgressReduceExpNoValueCasesPS = binopProgressReduceExpNoValueCases.map { case (obl, info) =>
    g.applyTactic(obl, Solve[VeritasConstruct, VeritasFormula])
  }


  val unopProgressReduceExpDistinction = BooleanCaseDistinction(FunctionExpJudgment(FunctionExpApp("expIsValue",
        Seq(FunctionMeta(getIntroducedMetaVars(progressReduceExpMatchingConds.last._2)(1))))), specenq)
  val unopProgressReduceExpDisitinctionPS = g.applyTactic(progressReduceExpInductionCases.last._1, unopProgressReduceExpDistinction)
  val unopProgressReduceExpCases = g.requiredObls(unopProgressReduceExpDisitinctionPS).toSeq
  val unopProgressReduceExpCasesPS = unopProgressReduceExpCases.map { case (obl, info) =>
    g.applyTactic(obl, Solve[VeritasConstruct, VeritasFormula])
  }

  val defquestionPS = g.applyTactic(qsinglesubobs.toSeq(2)._1, Solve[VeritasConstruct, VeritasFormula])

  val progressLookupQMap = g.requiredObls(askPS).toSeq.head._1

  val progressLookupQMapInduction = StructuralInduction(MetaVar("qm"), specenq)
  val progressLookupQMapInductionPS = g.applyTactic(progressLookupQMap, progressLookupQMapInduction)
  val progressLookupQMapInductionCases = g.requiredObls(progressLookupQMapInductionPS).toSeq
  val progressLookupQMapInductionCasesPS = progressLookupQMapInductionCases.map { case (obl, _) =>
    g.applyTactic(obl, Solve[VeritasConstruct, VeritasFormula])
  }

  val qcondObl = rootInduction.selectCase("QL-Progressqcond", rootsubobs)

  val expOfQcond = getIntroducedMetaVars(matchingConds("qcond")).head
  val qcondCaseDistinction = EqualityCaseDistinction[VeritasConstruct, VeritasFormula](
    FunctionMeta(expOfQcond), FunctionExpApp("constant", Seq(FunctionExpApp("B", Seq(FunctionMeta(MetaVar("b")))))), specenq)
  val qcondPS = g.applyTactic(qcondObl, qcondCaseDistinction)
  val qcondCases = g.requiredObls(qcondPS).toSeq
  val qcondBooleanCaseDistinction = StructuralCaseDistinction(MetaVar("b"), specenq)
  val qcondBooleanCaseDistinctionPS = g.applyTactic(qcondCases.head._1, qcondBooleanCaseDistinction)
  val qcondBooleanCases = g.requiredObls(qcondBooleanCaseDistinctionPS)
  val qcondBooleanCasesPS = qcondBooleanCases.map { case (obl, _) =>
    g.applyTactic(obl, Solve[VeritasConstruct, VeritasFormula])
  }

  val qcondNonBooleanPS = g.applyTactic(qcondCases.last._1, progressReduceExpLemmaApplication)

  //apply simply Solve-tactic to qgroup base case
  val qgroupObl = rootInduction.selectCase("QL-Progressqgroup", rootsubobs)
  val qgroupPS = g.applyTactic(qgroupObl, Solve[VeritasConstruct, VeritasFormula])

  //verify chosen steps with chosen verifiers
  def verifySingleStepsSimple() = {
    val simpleVampire4_1 = new TPTPVampireVerifier(5)
    val simpleVampire4_1_20 = new TPTPVampireVerifier(20)
    val simpleVampire4_1_120 = new TPTPVampireVerifier(120)

    // g.proofstepsDFS().distinct.foreach { ps =>
    //   g.verifyProofStep(ps, simpleVampire4_1_20)
    // }

    //// println(g.verifyProofStep(rootinductionPS, simpleVampire4_1_120).status)

    //verify case distinction steps
    // println(g.verifyProofStep(qemptyPS, simpleVampire4_1_120).status.isVerified)
    // println(g.verifyProofStep(qgroupPS, simpleVampire4_1_120).status.isVerified)
    // println(g.verifyProofStep(qseqcasePS, simpleVampire4_1_120).status.isVerified)
    // println(g.verifyProofStep(qsinglePS, simpleVampire4_1_120).status.isVerified)
    // println(g.verifyProofStep(askPS, simpleVampire4_1_120).status)
    // println(g.verifyProofStep(valuePS, simpleVampire4_1_120).status.isVerified)
    // println(g.verifyProofStep(qcondPS, simpleVampire4_1_120).status.isVerified)
    // qcondBooleanCasesPS.map { case ps =>
    //   println(g.verifyProofStep(ps, simpleVampire4_1_120).status.isVerified)
    // }
    //// println(g.verifyProofStep(qcondNonBooleanPS, simpleVampire4_1_20).status)


    //// println(g.verifyProofStep(progressLookupAnsMapInductionPS, simpleVampire4_1_120).status)
    // progressLookupQMapInductionCasesPS.foreach { ps =>
    //   println(g.verifyProofStep(ps, simpleVampire4_1_20).status.isVerified)
    // }
    // println(g.verifyProofStep(defquestionPS, simpleVampire4_1_120).status.isVerified)
    // println(g.verifyProofStep(questionPS, simpleVampire4_1_120).status.isVerified)

    // println(g.verifyProofStep(expIsValueTruePS, simpleVampire4_1_120).status.isVerified)
    //// println(g.verifyProofStep(expIsValueFalsePS, simpleVampire4_1_120).status)
    println(g.verifyProofStep(binopProgressReduceExpIsValuePS, simpleVampire4_1_120).status)

    //// println(g.verifyProofStep(progressReduceExpInductionPS, simpleVampire4_1_120).status)
    // println(g.verifyProofStep(qvarPS, simpleVampire4_1_120).status.isVerified)
    //// println(g.verifyProofStep(progressLookupQMapInductionPS, simpleVampire4_1_120).status)
    // progressLookupAnsMapInductionCasesPS.foreach { ps =>
    //   println(g.verifyProofStep(ps, simpleVampire4_1_120).status.isVerified)
    // }
    // println(g.verifyProofStep(constantProgressReduceExpPS, simpleVampire4_1_120).status.isVerified)
    // println(g.verifyProofStep(binopProgressReduceExpPS, simpleVampire4_1_120).status.isVerified)
    // println(g.verifyProofStep(binopProgressReduceExpSomeExpPS, simpleVampire4_1_120).status)
    // println(g.verifyProofStep(binopProgressReduceExpNoExpPS, simpleVampire4_1_120).status.isVerified)
    // println(g.verifyProofStep(unopProgressReduceExpDisitinctionPS, simpleVampire4_1_120).status.isVerified)
    // unopProgressReduceExpCasesPS.foreach { ps =>
    //   println(g.verifyProofStep(ps, simpleVampire4_1_120).status.isVerified)
    // }

    // qseqsubPS.tail.foreach { ps =>
    // println(g.verifyProofStep(ps, simpleVampire4_1_120).status)
    // }
  }

  def checkConsistency(): Unit = {
    val vampireProver = VampireTPTP("4.1", 3600)
    val checker = ConsistencyChecker(VeritasTransformerBestStrat.config, vampireProver)
    println(checker.consistent(fullQLspec))
  }
}

object QLSoundnessProofGraph {
  def initializeGraphTypes(g: ProofGraphXodus[VeritasConstruct, VeritasFormula]) = {
    PropertyTypes.registerWrapperType(g.store)
  }
}


// Executing this object creates a new QL Soundness Proof Graph,
// attempting to verify as much as possible
object ConstructQLSoundnessGraph extends App {

  def recursivedelete(file: File) {
    if (file.isDirectory)
      Option(file.listFiles).map(_.toList).getOrElse(Nil).foreach(recursivedelete(_))
    file.delete
  }

  val file = new File("QLSoundnessProofGraph-store")
  recursivedelete(file) //simply overwrite any old folder
  //try to create new folder
  if (!file.mkdir()) sys.error("Could not create new store for QLSoundnessProofGraph-store.")

  val pg = new QLSoundnessProofGraph(file)

  //TODO currently there is some problem within the verification of the manually created proof graph for QL Progress...
  //pg.verifySingleStepsSimple()
  Dot(pg.g, new File("ql_progress.png"))

  val rootobl = pg.g.findObligation("QL progress").get
  //val subobs = pg.g.requiredObls(pg.rootinductionPS)

  //println("Root obligation: " + rootobl)
  //println("Induction cases:")
  //println(subobs)
}
