package de.tu_darmstadt.veritas.inputdsl

import java.io.PrintWriter

import de.tu_darmstadt.veritas.backend.util.prettyprint.PrettyPrintWriter

object SQLDefsInputDSLTest {
  
  def main(args: Array[String]) {
    val outputPrettyPrinter = new PrettyPrintWriter(new PrintWriter(System.out))
    outputPrettyPrinter.writeln()
    outputPrettyPrinter.writeln("Here are the test modules:")
    SQLDefs.Tables.prettyPrint(outputPrettyPrinter)
    SQLDefs.TableAux.prettyPrint(outputPrettyPrinter)
    SQLDefs.TStore.prettyPrint(outputPrettyPrinter)
    SQLDefs.TContext.prettyPrint(outputPrettyPrinter)
    SQLDefs.Syntax.prettyPrint(outputPrettyPrinter)
    SQLDefs.Semantics.prettyPrint(outputPrettyPrinter)
    SQLDefs.TypeSystem.prettyPrint(outputPrettyPrinter)
    SQLDefs.TypeSystemInv.prettyPrint(outputPrettyPrinter)
    SQLDefs.SoundnessAuxDefs.prettyPrint(outputPrettyPrinter)
    outputPrettyPrinter.close()
  }

}