package de.tu_darmstadt.veritas.backend

import java.io.PrintWriter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.spoofax.interpreter.terms.IStrategoList
import org.spoofax.interpreter.terms.IStrategoTerm
import org.spoofax.interpreter.terms.IStrategoTuple
import de.tu_darmstadt.veritas.backend.nameresolution.NameResolution
import de.tu_darmstadt.veritas.backend.stratego.StrategoString
import de.tu_darmstadt.veritas.backend.stratego.StrategoTerm
import de.tu_darmstadt.veritas.backend.stratego.StrategoTuple
import de.tu_darmstadt.veritas.backend.util.BackendError
import de.tu_darmstadt.veritas.backend.util.Context
import de.tu_darmstadt.veritas.backend.util.Context.debug
import de.tu_darmstadt.veritas.backend.util.prettyprint.PrettyPrintWriter
import de.tu_darmstadt.veritas.backend.util.prettyprint.PrettyPrintableFile
import de.tu_darmstadt.veritas.backend.util.stacktraceToString
import de.tu_darmstadt.veritas.backend.veritas.Module
import de.tu_darmstadt.veritas.backend.veritas.FunctionExpApp
import de.tu_darmstadt.veritas.backend.transformation.ToFof
import de.tu_darmstadt.veritas.backend.transformation.ToTff
import de.tu_darmstadt.veritas.backend.transformation.lowlevel._
import de.tu_darmstadt.veritas.backend.transformation.defs._

object Backend {

  private var outputfolder: String = ""
  private var study = new EncodingComparisonStudy
  
  @throws[BackendError[_]]("and the appropriate subclasses on internal error at any stage")
  private def run(input: StrategoTerm): Seq[PrettyPrintableFile] = {
    val mod = Module.from(input)

    //    debug("Imported modules:")
    //    val res = for {
    //      imp <- mod.imports
    //      resolved <- NameResolution.getModuleDef(imp)
    //    } {
    //      debug(resolved)
    //      resolved
    //    }

    //    Seq()

    //    ToFof.toFofFiles(mod)

    //    Seq(PrettyPrintableFile("debug.out", "just a test"))

    // NOTE without the "Out", calling the Strategy from Spoofax fails, because it would overwrite
    // the original file!
    //Seq(Module(mod.name + "Out", mod.imports, mod.body))
    val (sname, output) = study.currEncoding(mod)
    outputfolder = sname
    output
  }

  /**
   * Run backend as strategy from inside Veritas editor
   */
  def runAsStrategy(context: org.strategoxt.lang.Context, inputFromEditor: IStrategoTerm): IStrategoList = {
    // check and destructure input
    val (inputDirectory, ast) = StrategoTerm(inputFromEditor) match {
      case StrategoTuple(StrategoString(inputDirectory), ast) => (inputDirectory, ast)
      case _ => throw new IllegalArgumentException("Illegal input to backend-strategy: " +
        "Argument must be a tuple: (input file directory, AST of file as Stratego term)")
    }

    Context.initStrategy(context)
    study = new EncodingComparisonStudy

    // NOTE we need to capture exceptions with Try, so we can print the full stack trace below
    // (otherwise Stratego will silence the stack trace...)
    //val backendResult = Try(run(ast))

    var resseq: Seq[IStrategoTuple] = Seq()
    
    for (i <- (0 until study.encodingnum)) yield {
      val result = Try(run(ast))
      
      result match {
        case Failure(ex) => {
          context.getIOAgent.printError(stacktraceToString(ex))
          // skip files that don't work
        }

        case Success(outputFiles) => {
          val scalaSeq = for {
            outputFile <- outputFiles
            // map files to IStrategoTuples with (filename, contents)
            filename = context.getFactory.makeString(inputDirectory + "/" + outputfolder + "/" + outputFile.filename)
            content = context.getFactory.makeString(outputFile.toPrettyString)
          } yield context.getFactory.makeTuple(filename, content)
          // convert Scala Seq to Spoofax IStrategoList
          resseq = resseq ++ scalaSeq
        }
      }
      
      study.moveToNextEncoding()
    }

    if (resseq.isEmpty)
      // return empty list on failure
      context.getFactory.makeList()
    else
      context.getFactory.makeList(resseq.asJava)
  }

  /**
   * Run backend as console application, with optional arguments for giving the ATerm and Index/Tasks
   */
  val DefaultATerm = "test/TableAux.noimports.aterm"
  val DefaultIndexAndTaskenginePath = "test/"

  def main(args: Array[String]) {
    val (aterm, indexAndTaskenginePath) = args match {
      case Array()                                      => (StrategoTerm.fromATermFile(DefaultATerm), DefaultIndexAndTaskenginePath)
      case Array(atermFilename)                         => (StrategoTerm.fromATermFile(atermFilename), DefaultIndexAndTaskenginePath)
      case Array(atermFilename, indexAndTaskenginePath) => (StrategoTerm.fromATermFile(atermFilename), indexAndTaskenginePath)
    }

    Context.initStandalone(indexAndTaskenginePath)

    // call the strategy on the IStrategoTerm
    val outputPrettyPrinter = new PrettyPrintWriter(new PrintWriter(System.out))
    for (outputFiles <- run(aterm)) {
      // print a filename header, then the contents
      outputPrettyPrinter.write("File '", outputFiles.filename, "':")
      outputPrettyPrinter.indent().write(outputFiles).unindent()
      outputPrettyPrinter.writeln()
    }
    outputPrettyPrinter.flush()
  }
}
