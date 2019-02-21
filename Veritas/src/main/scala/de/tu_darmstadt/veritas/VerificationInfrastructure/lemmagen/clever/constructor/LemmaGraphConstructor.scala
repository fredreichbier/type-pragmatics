package de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.constructor

import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.assignments.Constraint
import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.{AnnotatedLemma, Hints, RefinementNode}
import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.{LemmaGenSpecEnquirer, Query, Refinement, StrategyHelpers}
import de.tu_darmstadt.veritas.backend.ast.MetaVar
import de.tu_darmstadt.veritas.backend.ast.function.{FunctionExpMeta, FunctionMeta}

import scala.collection.mutable

trait LemmaGraphConstructor extends GraphConstructor with StrategyHelpers {
  import Query._
  implicit private val enquirer: LemmaGenSpecEnquirer = problem.enquirer

  def generateBase(): AnnotatedLemma
  def hints: Hints
  def invocationArguments: Seq[MetaVar]
  def restrictableVariables(node: RefinementNode): Set[MetaVar]

  def generateEquations(node: RefinementNode): Set[Refinement] = {
    val restrictable = restrictableVariables(node)
    val partitioned = restrictable.groupBy(_.sortType)
    var restrictions = new mutable.ListBuffer[Refinement]()
    for((typ, metaVars) <- partitioned) {
      if(metaVars.size > 1) {
        val equals = metaVars.subsets.filter(_.size == 2)
        for(equal <- equals) {
          if(!equal.subsetOf(invocationArguments.toSet))
            restrictions += Refinement.Equation(equal.head, FunctionMeta(equal.tail.head))
        }
      }
    }
    restrictions.toSet
  }

  def generateApplications(node: RefinementNode): Set[Refinement] = {
    val sideArguments = invocationArguments.map(_.sortType)
    val notConstrainedYet: Set[FunctionExpMeta] = (node.lemma.boundVariables -- node.constrainedVariables).map(FunctionMeta(_))
    val staticFunctions = problem.enquirer.staticFunctions.filter(_.signature.in.intersect(sideArguments).nonEmpty)
    staticFunctions.flatMap(staticFn =>
      if (staticFn.signature.out.name == "Bool") {
        var refinements = selectPredicate(node.lemma, staticFn)
        refinements = refinements.filter(r => r.arguments.toSet.intersect(notConstrainedYet).nonEmpty)
        // do not want refinements which pass the same argument twice
        refinements = refinements.filterNot(r => r.arguments.toSet.size != r.arguments.size)
        // do not want refinements whose in arguments contain post variables
        val postVars: Set[FunctionExpMeta] = node.postVariables.map(FunctionMeta(_))
        refinements = refinements.filterNot(r => r.arguments.exists(arg => postVars.contains(arg)))
        refinements
      } else {
        var refinements = selectSuccessfulApplication(node.lemma, staticFn,
          Constraint.preferBound(staticFn.inTypes),
          Constraint.preferBound(staticFn.successfulOutType))
        refinements = refinements.filter(r => r.arguments.toSet.intersect(notConstrainedYet).nonEmpty)
        // do not want refinements which pass the same argument twice
        refinements = refinements.filterNot(r => r.arguments.toSet.size != r.arguments.size)
        // do not want refinements which assume no change
        refinements = refinements.filterNot(r => r.arguments.contains(FunctionMeta(r.result)))
        // do not want refinements whose in arguments contain post variables
        val postVars: Set[FunctionExpMeta] = node.postVariables.map(FunctionMeta(_))
        refinements = refinements.filterNot(r => r.arguments.exists(arg => postVars.contains(arg)))
        refinements
      }
    )
  }

  override def expand(node: RefinementNode): Set[Refinement] = {
    generateEquations(node) ++ generateApplications(node)
  }

  override def constructRoot(): AnnotatedLemma = {
    val base = generateBase()
    hints.apply(base)
  }
}