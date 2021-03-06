package de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.construction

import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.{AnnotatedLemma, RefinementGraph, RefinementNode}
import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.{Problem, Refinement}

/** A graph constructor constructs a refinement graph:
  * The method `constructRoot` creates an `AnnotatedLemma` instance which is stored in the root node.
  * The method `expand` expands a given refinement node, i.e. returns a set of refinements that
  * should be applied.
  */
trait GraphConstructor {
  def problem: Problem
  def constructRoot(): AnnotatedLemma
  def expand(node: RefinementNode): Set[Refinement]

  def construct(): RefinementGraph = {
    val root = new RefinementNode(constructRoot())
    val graph = new RefinementGraph(problem, root)
    while(graph.openNodes.nonEmpty) {
      for(node <- graph.openNodes) {
        val refinements = expand(node)
        for (restriction <- refinements) {
          graph.refine(node, restriction)
        }
        node.open = false
      }
    }
    graph
  }
}
