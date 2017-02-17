package de.tu_darmstadt.veritas.VerificationInfrastructure

/**
  * status of a particular verification attempt (for a node/leaf in a proof tree)
  *
  */
sealed trait VerificationStatus {
  val isVerified: Boolean = false
}

case object NotStarted extends VerificationStatus

case class Outdated[S, P](prevs: VerificationStatus, previousProofGraph: ProofGraph[S, P]) extends VerificationStatus

//TODO: maybe refine errorMessage: String later to include specific error objects
class VerificationFailure[S, P](val errorMessage: String,
                                val usedVerifier: Verifier[S, P],
                                val prevs: Option[VerificationStatus],
                                val previousProofGraph: Option[ProofGraph[S, P]]) extends VerificationStatus

//allow different constructors for VerificationFailure
object VerificationFailure {
  def apply[S, P](em: String, usedVerifier: Verifier[S, P]) =
    new VerificationFailure[S, P](em, usedVerifier, None, None)

  def apply[S, P](prevs: Option[VerificationStatus], previousProofGraph: Option[ProofGraph[S, P]],
                  errorMessage: String, usedVerifier: Verifier[S, P]) =
    new VerificationFailure[S, P](errorMessage, usedVerifier, prevs, previousProofGraph)

  def unapply[S, P](arg: VerificationFailure[S, P]): Option[(String, Verifier[S, P], Option[VerificationStatus], Option[ProofGraph[S, P]])] =
    Some((arg.errorMessage, arg.usedVerifier, arg.prevs, arg.previousProofGraph))

}

case class Finished[S, P, V](report: Map[VerificationConfiguration[S, P, V], ProverStatus]) extends VerificationStatus {
  private def bestAttempt(): (VerificationConfiguration[S, P, V], ProverStatus) = {
    def sortByProverStatus(ps: ProverStatus): Int = ps match {
      // proved <- disproved <- inconclusive <- ProverFailure
      case Proved(_) => 0
      case Disproved(_) => 1
      case Inconclusive(_) => 3
      case ProverFailure(_) => 4
    }

    report.toSeq.sortBy { case (_, ps) => sortByProverStatus(ps) }.head
  }

  // TODO: not sure if it should be def or val because it is immutable. more leaning to val
  // -> Does not really matter so much. Could also be lazy val, so that it is only computed if really requested.
  // As it is currently, bestAttempt could also as well be a private (lazy) val, since the val definitions force computation of it anyway
  val bestConf: VerificationConfiguration[S, P, V] = bestAttempt()._1

  val bestStatus: ProverStatus = bestAttempt()._2

  override val isVerified = bestStatus.isVerified
}
