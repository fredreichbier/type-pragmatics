package de.tu_darmstadt.veritas.VerificationInfrastructure

import java.io.{File, PrintWriter}

import de.tu_darmstadt.veritas.VerificationInfrastructure.tactics.StructuralInduction
import de.tu_darmstadt.veritas.VerificationInfrastructure.verifier._
import de.tu_darmstadt.veritas.VerificationInfrastructure.visualizer.Dot
import de.tu_darmstadt.veritas.backend.Configuration
import de.tu_darmstadt.veritas.backend.Configuration._
import de.tu_darmstadt.veritas.backend.ast._
import de.tu_darmstadt.veritas.backend.util.FreshNames
import org.scalatest.FunSuite

class SQLVerificationTests extends FunSuite {

  def recursivedelete(file: File) {
    if (file.isDirectory)
      Option(file.listFiles).map(_.toList).getOrElse(Nil).foreach(recursivedelete(_))
    file.delete
  }


  //  test("Check induction application steps only") {
  //    //construct a new test database with SQL progress proof graph
  //    val file = new File("SQLProgressProof-inductionsteps")
  //    if (file.exists()) recursivedelete(file)
  //    if (!file.mkdir()) sys.error("Could not create new store for SQLSoundnessProofGraph-inductionsteps.")
  //
  //    val SQLPG = new SQLSoundnessProofGraph(file)
  //    val pg = SQLPG.g //actual ProofGraphXodus instance
  //
  //    def printStepResult(res: pg.StepResult): String =
  //      res.status match {
  //        case Finished(stat, ver) =>
  //          stat match {
  //            case Proved(ie@InductionSchemeEvidence(_,_,_)) => s"Proved: \n ${ie.toString}."
  //            case Proved(_) => "Proved (unknown details)."
  //            case Disproved(_) => "Disproved."
  //            case Inconclusive(_) => "Inconclusive."
  //            case ProverFailure(_) => "Failure."
  //          }
  //        case Unknown(_) => "Unknown"
  //        case VerifierFailure(err, _) => "VerifierFailure :" + err
  //      }
  //
  //
  //    //only induction steps!
  //    val indobls = pg.obligationDFS() filter (o => pg.appliedStep(o).get.tactic.isInstanceOf[StructuralInduction[VeritasConstruct, VeritasFormula]])
  //    val indps = for (obl <- indobls) yield pg.appliedStep(obl).get
  //
  //    val indver = new TrustInductionSchemeVerifier[VeritasConstruct, VeritasFormula]()
  //
  //    for (ps <- indps)
  //      println(printStepResult(pg.verifyProofStep(ps, indver, None)))
  //  }

//  test("Manual step simplification set cases") {
//    val file = new File("SQLProgressProof-manualsteps")
//    if (file.exists()) recursivedelete(file)
//    if (!file.mkdir()) sys.error("Could not create new store for SQLSoundnessProofGraph-allsteps.")
//
//    val SQLPG = new SQLSoundnessProofGraph(file)
//    val pg = SQLPG.g //actual ProofGraphXodus instance
//
//    val SQLPG_UI = new ProofGraphUI[VeritasConstruct, VeritasFormula](pg, ProofGraphUI.extractGoalOrLemmaName)
//
//    val testobl = SQLPG_UI.getObligation("SQL-Progress-icase2-case1")
//    val trans = new VeritasTransformer[TPTP](
//      Configuration(Map(FinalEncoding -> FinalEncoding.BareFOF,
//        Simplification -> Simplification.LogicalAndConstructors,
//        VariableEncoding -> VariableEncoding.InlineEverything,
//        Selection -> Selection.SelectAll,
//        Problem -> Problem.All)), x => x.asInstanceOf[TPTP])
//    val (aspec, assms, agoal) = SQLPG_UI.getAssembledProblem[TPTP](testobl, trans)
//
//    println("Assumptions: ")
//    println(assms.toPrettyString())
//    println("Goal: ")
//    println(agoal.toPrettyString())
//
//
//    //TODO: apply manual simplification steps to the obligation, test whether the steps can be verified
//
//    assert(true)
//  }

  test("Construct and visualize complete graph, save inconclusive problem descriptions to files") {
    //construct a new test database with SQL progress proof graph
    val file = new File("SQLProgressProof-allsteps")
    if (file.exists()) recursivedelete(file)
    if (!file.mkdir()) sys.error("Could not create new store for SQLSoundnessProofGraph-allsteps.")

    val SQLPG = new SQLSoundnessProofGraph(file)
    val pg = SQLPG.g //actual ProofGraphXodus instance

    //handle for easier access of some proof graph components from pg (circumventing some issues with path-dependent types)
    val SQLPG_UI = new ProofGraphUI[VeritasConstruct, VeritasFormula](pg, ProofGraphUI.extractGoalOrLemmaName)


    def makeCustomVampire(timeout: Int, logic: String) = new TPTPVampireVerifier(timeout, "4.1", logic)

    // create separate folder for collecting the inconclusive problem descriptions
    val file_inconclusive = new File("SQLProgressProof-allsteps-inconclusive")
    if (file_inconclusive.exists()) recursivedelete(file_inconclusive)
    if (!file_inconclusive.mkdir()) sys.error("Could not create new folder for SQLSoundnessProofGraph-allsteps-inconclusive.")

    val fresh = new FreshNames()

    def extractGoalName(vc: VeritasConstruct): String =
      vc match {
        case Goals(gl, _) => gl.head.name
        case Lemmas(ll, _) => ll.head.name
        case Axioms(axl) => axl.head.name
        case TypingRule(name, _, _) => name
        case _ => fresh.freshRuleName("obl_")
      }

    def writeToFile(filehandler: File, s: String) = {
      if (!filehandler.getParentFile.exists())
        filehandler.getParentFile.mkdirs()
      filehandler.createNewFile()
      new PrintWriter(filehandler) {
        write(s);
        close
      }
    }

    // print step results on console and log inconclusive problem descriptions as files
    def printStepResult(ps: pg.ProofStep, res: pg.StepResult): String =
      res.status match {
        case Finished(stat, ver) =>
          stat match {
            case Proved(ie@InductionSchemeEvidence(_, _, _)) => s"Proved (induction scheme application)."
            case Proved(ATPResultDetails(_, _, _, _, Some(t))) => "Proved (" + t + "s)."
            case Proved(_) => "Proved (unknown details)."
            case Disproved(_) => "Disproved."
            case Inconclusive(_) => {
              // write pretty printed version of assembled proof problem to a file
              val goalobl = pg.targetedObl(ps)
              val goalname = extractGoalName(goalobl.goal)

              val goalobl_ui = SQLPG_UI.getObligation(goalname)

              val trans = new VeritasTransformer[TPTP](
                Configuration(Map(FinalEncoding -> FinalEncoding.BareFOF,
                  Simplification -> Simplification.LogicalAndConstructors,
                  VariableEncoding -> VariableEncoding.InlineEverything,
                  Selection -> Selection.SelectAll,
                  Problem -> Problem.All)), x => x.asInstanceOf[TPTP])

              //write pretty-printed version of problem to file
              val (aspec, assms, agoal) = SQLPG_UI.getAssembledProblem[TPTP](goalobl_ui, trans)
              val assembledproblem_str = s"DEFINITIONS: \n ${aspec.toPrettyString()} \n\n " +
                s"GOAL-SPECIFIC ASSUMPTIONS: \n ${assms.toPrettyString()} \n\n" +
                s"GOAL: \n ${agoal.toPrettyString()}"
              val assembledProblemFile = new File(s"SQLProgressProof-allsteps-inconclusive/$goalname-assembledProblem")
              writeToFile(assembledProblemFile, assembledproblem_str)

              //write translated version of problem to file
              val transformedProblem = trans.translateProblem((aspec, assms, agoal))
              val translatedProblemFile = new File(s"SQLProgressProof-allsteps-inconclusive/$goalname-translatedProblem")
              writeToFile(translatedProblemFile, transformedProblem.get.toString)

              "Inconclusive."
            }
            case ProverFailure(_) => "Failure."
          }
        case Unknown(_) => "Unknown"
        case VerifierFailure(err, _) => "VerifierFailure :" + err
      }


    //partition goals in induction scheme application steps and all other steps
    val (indobls, noindobls) = pg.obligationDFS() partition (o => pg.appliedStep(o).get.tactic.isInstanceOf[StructuralInduction[VeritasConstruct, VeritasFormula]])

    val indver = new TrustInductionSchemeVerifier[VeritasConstruct, VeritasFormula]()
    //use Vampire (4.1) with 90 sec timeout and fof encoding for verifying all steps that are not induction scheme applications
    val noindver = makeCustomVampire(120, "tff")

    //logging on console and saving problems that cannot be proved to extra files:
    for (obl <- indobls) {
      val ps = pg.appliedStep(obl).get
      val goalname = extractGoalName(obl.goal)
      println(s"$goalname: " + printStepResult(ps, pg.verifyProofStep(ps, indver, None)))
    }

    for (obl <- noindobls) {
      val ps = pg.appliedStep(obl).get
      val goalname = extractGoalName(obl.goal)
      println(s"$goalname: " + printStepResult(ps, pg.verifyProofStep(ps, noindver, None)))
    }

    //visualize proof graph
    def visualizeGraph(filename: String) {
      val graphfile = new File(filename)
      if (file.exists()) recursivedelete(file)
      Dot(pg, graphfile)
    }

    visualizeGraph("SQLProgressCompleteVerification.png")


  }

}
