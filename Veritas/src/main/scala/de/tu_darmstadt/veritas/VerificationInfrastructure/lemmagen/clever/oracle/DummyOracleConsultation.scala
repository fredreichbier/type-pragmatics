package de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.oracle
import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.{Inconclusive, RefinementGraph}

/** Oracle consultation that simply adds the falsity status of all lemmas to Inconclusive. */
class DummyOracleConsultation extends OracleConsultation {
  override def consult(graph: RefinementGraph): Unit = {
    for(node <- graph.nodes)
      node.provabilityStatus = Inconclusive()
  }
}
