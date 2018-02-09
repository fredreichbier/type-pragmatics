package de.tu_darmstadt.veritas.VerificationInfrastructure

import de.tu_darmstadt.veritas.backend.ast._
import de.tu_darmstadt.veritas.inputdsl.DataTypeDSL.consts
import de.tu_darmstadt.veritas.inputdsl.{DataTypeDSL, FunctionDSL, SymTreeDSL}


object QLSoundnessProofSteps {
  import DataTypeDSL._
  import FunctionDSL._
  import SymTreeDSL._
  import de.tu_darmstadt.veritas.inputdsl.TypingRuleDSL._
  import de.tu_darmstadt.veritas.inputdsl.ProofDSL._

  import de.tu_darmstadt.veritas.inputdsl.QLDefs._

  val fullQLspec: Module = Module("QLspec", Seq(),
    QLSyntax.defs ++ QLSemanticsData.defs ++ QLSemantics.defs ++ QLTypeSystem.defs ++ QLTypeSystemInv.defs)

  val QLProgress = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))).===>("QL-Progress")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqempty = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qempty) &
      ('typeAM (~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
    ).===>("QL-Progress-T-qempty")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqseq = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qseq(~'qs1, ~'qs2)) &
      ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
    ).===>("QL-Progress-T-qseq")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqcond = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qcond(~'exp, ~'qs1, ~'qs2)) &
      ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
    ).===>("QL-Progress-T-qcond")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqsingle = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qsingle(~'qs)) &
      ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
    ).===>("QL-Progress-T-qsingle")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqgroup = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qgroup(~'qid, ~'qs)) &
      ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
    ).===>("QL-Progress-T-qgroup")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqseqqempty = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qseq(~'qs1, ~'qs2)) &
      (~'qs1 === 'qempty) &
      ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
      ).===>("QL-Progress-T-qseq-qempty")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqseqnotqempty = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qseq(~'qs1, ~'qs2)) &
      (~'qs1 ~= 'qempty) &
      ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
      ).===>("QL-Progress-T-qseq-not-qempty")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqconde = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qcond(~'exp, ~'qs1, ~'qs2)) &
      (!'expIsValue(~'exp)) &
      ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
      ).===>("QL-Progress-T-qcond-e")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqcondno = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qcond(~'exp, ~'qs1, ~'qs2)) &
      (~'exp === 'constant('B('no))) &
      ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
      ).===>("QL-Progress-T-qcond-no")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqcondyes = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qcond(~'exp, ~'qs1, ~'qs2)) &
      (~'exp === 'constant('B('yes))) &
      ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
      ).===>("QL-Progress-T-qcond-yes")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqsingleask = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qsingle(~'qs)) &
      (~'qs === 'ask(~'qid)) &
      ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
      ).===>("QL-Progress-T-qsingle-ask")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqsingledefquestion = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qsingle(~'qs)) &
      (~'qs === 'defquestion(~'qid, ~'l, ~'at)) &
        ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
      ).===>("QL-Progress-T-qsingle-defquestion")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqsinglequestion = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qsingle(~'qs)) &
      (~'qs === 'question(~'qid, ~'l, ~'at)) &
      ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
      ).===>("QL-Progress-T-qsingle-question")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )

  val QLProgressTqsinglevalue = goal(
    ((!'isValue('QConf(~'am, ~'qm, ~'q))) &
      (~'q === 'qsingle(~'qs)) &
      (~'qs === 'value(~'qid, ~'at, ~'exp)) &
      ('typeAM(~'am) === ~'atm) &
      ('typeQM(~'qm) === ~'qtm) &
      ('MC(~'atm, ~'qtm) |- ~'q :: 'MC(~'atm2, ~'qtm2))
      ).===>("QL-Progress-T-qsingle-value")(
      exists(~'am0, ~'qm0, ~'q0) |
        'reduce('QConf(~'am, ~'qm, ~'q)) === 'someQConf('QC(~'am0, ~'qm0, ~'q0)))
  )
}
