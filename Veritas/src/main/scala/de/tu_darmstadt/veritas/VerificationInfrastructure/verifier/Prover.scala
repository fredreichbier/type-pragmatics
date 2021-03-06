package de.tu_darmstadt.veritas.VerificationInfrastructure.verifier

import java.io._

import de.tu_darmstadt.veritas.VerificationInfrastructure.Evidence

import scala.sys.process.ProcessLogger

/**
  * return status of a prover call
  */
sealed trait ProverStatus extends Ordered[ProverStatus] {
  val isVerified: Boolean = false
  val proverResult: ResultDetails

  override def compare(that: ProverStatus): Int = that match {
    case that: this.type => proverResult compare that.proverResult
    case _ => this.getClass.getCanonicalName.compare(that.getClass.getCanonicalName)
  }
}

trait ResultDetails extends Ordered[ResultDetails] {
  /**
    * @return all details as string
    */
  def toString: String

  /**
    *
    * @return full logs of prover
    */
  def fullLogs: String

  def summaryDetails: String

  def proofEvidence: Option[Evidence]

  def message: Option[String]

  override def compare(that: ResultDetails): Int = this.fullLogs compare that.fullLogs
}


case class Proved(proverResult: ResultDetails) extends ProverStatus {
  override val isVerified: Boolean = true
}

case class Disproved(proverResult: ResultDetails) extends ProverStatus

case class Inconclusive(proverResult: ResultDetails) extends ProverStatus

case class ProverFailure(proverResult: ResultDetails) extends ProverStatus

/**
  * Interface for concrete provers
  */
trait Prover[V <: VerifierFormat] {

  // todo: automatically append .exe under windows systems
  protected def findBinaryInPath(command: String): File = {
    for (p <- System.getenv("PATH").split(File.pathSeparator);
         f = new File(p, command) if f.exists() && f.canExecute)
      return f
    null
  }

  protected def writeToFile(filehandler: File, s: String) = {
    if (!filehandler.getParentFile.exists())
      filehandler.getParentFile.mkdirs()
    filehandler.createNewFile()
    new PrintWriter(filehandler) {
      write(s);
      close
    }
  }

  def callProver(problem: V): ProverStatus
}

