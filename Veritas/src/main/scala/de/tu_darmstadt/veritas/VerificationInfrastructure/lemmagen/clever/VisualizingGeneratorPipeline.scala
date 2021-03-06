package de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever

import java.io.{File, FileWriter}

import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.Lemma
import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.construction.GraphConstructor
import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.selection.SelectionHeuristic
import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.oracle.OracleConsultation
import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.clever.postprocessing.Postprocessor
import de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen.util.SimpleLaTeXLemmaPrinter
import de.tu_darmstadt.veritas.backend.util.prettyprint.PrettyPrintWriter
import de.tu_darmstadt.veritas.scalaspl.prettyprint.SimpleToScalaSPLSpecificationPrinter

/** A LemmaGeneratorPipeline subclass that writes GraphViz visualizations of
  * refinement graphs to a directory. This class will create a subdirectory
  * of `directory` which contains dot files visualizing the intermediate
  * refinement graphs, as well as files `lemmas.scala` and `lemmas.tex`
  * which contain the selected lemmas.
  */
class VisualizingGeneratorPipeline(val directory: File,
                                   graphConstructor: GraphConstructor,
                                   oracleConsultation: OracleConsultation,
                                   selectionHeuristic: SelectionHeuristic,
                                   postprocessor: Postprocessor)
  extends LemmaGeneratorPipeline(
    graphConstructor, oracleConsultation, selectionHeuristic, postprocessor) {

  private def makeDirectory(graph: RefinementGraph): File = {
    val subdirectory = new File(directory, graph.root.lemma.name)
    if(!subdirectory.exists())
      subdirectory.mkdirs()
    subdirectory
  }

  /** Write LaTeX representations of a sequence of lemmas to a file. */
  def writeLemmasLaTeX(file: File, lemmas: Seq[Lemma]): Unit = {
    val writer = new FileWriter(file)
    val latexWriter = new SimpleLaTeXLemmaPrinter {
      override val printer: PrettyPrintWriter = new PrettyPrintWriter(writer)
    }
    for (lemma <- lemmas) {
      latexWriter.printTypingRule(lemma)
      writer.write("\n")
      writer.flush()
    }
    writer.close()
  }

  /** Write ScalaSPL representations of a sequence of lemmas to a file. */
  def writeLemmasScalaSPL(file: File, lemmas: Seq[Lemma]): Unit = {
    val writer = new FileWriter(file)
    val lemmaWriter = new SimpleToScalaSPLSpecificationPrinter {
      override val printer: PrettyPrintWriter = new PrettyPrintWriter(writer)
    }
    for (lemma <- lemmas) {
      lemmaWriter.printTypingRule(lemma)
      writer.write("\n")
      writer.flush()
    }
    writer.close()
  }

  def writeLemmas(directory: File, lemmas: Seq[Lemma]): Unit = {
    writeLemmasLaTeX(new File(directory, "lemmas.tex"), lemmas)
    writeLemmasScalaSPL(new File(directory, "lemmas.scala"), lemmas)
  }

  override def invokeConstructor(): RefinementGraph = {
    val graph = super.invokeConstructor()
    graph.visualize(new File(makeDirectory(graph), "step1.dot"))
    graph
  }

  override def invokeOracle(graph: RefinementGraph): Unit = {
    super.invokeOracle(graph)
    graph.visualize(new File(makeDirectory(graph), "step2.dot"))
  }

  override def invokeSelection(graph: RefinementGraph): Unit = {
    super.invokeSelection(graph)
    graph.visualize(new File(makeDirectory(graph), "step3.dot"))
    graph.visualize(new File(makeDirectory(graph), "graph-small.dot"), detailed = false)
  }

  override def invokePostprocessor(graph: RefinementGraph): Seq[Lemma] = {
    val lemmas = super.invokePostprocessor(graph)
    writeLemmas(makeDirectory(graph), lemmas)
    lemmas
  }
}
