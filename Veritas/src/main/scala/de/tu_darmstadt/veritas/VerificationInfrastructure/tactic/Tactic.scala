package de.tu_darmstadt.veritas.VerificationInfrastructure.tactic

import de.tu_darmstadt.veritas.VerificationInfrastructure._

/**
  * Tactics for labeling edges of ProofTrees
  */
trait Tactic[Spec, Goal] extends Ordered[Tactic[Spec, Goal]] {
  def allRequiredOblsVerified(g: IProofGraph[Spec, Goal])(obl: g.Obligation, edges: Iterable[(g.Obligation, EdgeLabel)]): Boolean =
    edges.forall { case (subobl, label) => g.isOblVerified(subobl) }

  /**
    * verifying a step via its edges generates a step result
    * the caller has to decide whether this result will be integrated into a proof graph or not
    * @param obl
    * @param edges
    * @param verifier
    * @return
    */
  def verifyStep[Result](obl: GenObligation[Spec, Goal], edges: Iterable[(GenObligation[Spec, Goal], EdgeLabel)], verifier: Verifier[Spec, Goal], produce: StepResultProducer[Spec, Goal, Result]): Result =
    verifier.verify(obl.goal, obl.spec, edges.map(_._1.goal), produce)

  /**
    * applying a tactic to a ProofStep returns the edges generated from this application
    * edges include edge labels and sub-ProofSteps
    * caller has to decide whether the edges will be integrated into a proof graph or not
    * @param obl
    * @throws TacticApplicationException
    * @return
    */
  def apply[Obligation](obl: GenObligation[Spec, Goal], produce: ObligationProducer[Spec, Goal, Obligation]): Iterable[(Obligation, EdgeLabel)]
}

trait TacticApplicationException[Spec, Goal] extends Exception {
  val tactic: Tactic[Spec, Goal]
}