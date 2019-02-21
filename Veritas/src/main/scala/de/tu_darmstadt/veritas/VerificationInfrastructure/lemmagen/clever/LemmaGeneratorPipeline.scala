package de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever

import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.constructor.GraphConstructor
import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.extraction.ExtractionHeuristic
import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.oracle.OracleConsultation
import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.postprocessor.Postprocessor
import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.Lemma

trait LemmaGeneratorPipeline {
  def graphConstructor: GraphConstructor
  def oracleConsultation: OracleConsultation
  def extractionHeuristic: ExtractionHeuristic
  def postProcessor: Postprocessor

  def invokeConstructor(): RefinementGraph = {
    graphConstructor.construct()
  }

  def invokeOracle(graph: RefinementGraph): Unit = {
    oracleConsultation.consult(graph)
  }

  def invokeExtraction(graph: RefinementGraph): Unit = {
    extractionHeuristic.extract(graph)
  }

  def invokePostprocessor(graph: RefinementGraph): Seq[Lemma] = {
    postProcessor.process(graph)
  }

  def invokePipeline(): Seq[Lemma] = {
    val graph = invokeConstructor()
    invokeOracle(graph)
    invokeExtraction(graph)
    invokePostprocessor(graph)
  }
}