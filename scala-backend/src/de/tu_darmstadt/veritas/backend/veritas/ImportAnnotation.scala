package de.tu_darmstadt.veritas.backend.veritas

import de.tu_darmstadt.veritas.backend.stratego.StrategoAppl
import de.tu_darmstadt.veritas.backend.stratego.StrategoTerm
import de.tu_darmstadt.veritas.backend.util.prettyprint.SimplePrettyPrintable

// NOTE xxxImport for these case object names, since they clash with the ModuleDef subclasses
sealed trait ImportAnnotation extends VeritasConstruct with SimplePrettyPrintable
final case object ImportNames extends ImportAnnotation {
  override val children = Seq()

  override def transformChildren(newchildren: Seq[Seq[VeritasConstruct]]): VeritasConstruct = {
    if (!newchildren.isEmpty) throw new ClassCastException

    //return myself
    ImportNames
  }

  override val prettyString = "false"
}

final case object ImportAxioms extends ImportAnnotation {
  override val children = Seq()

  override def transformChildren(newchildren: Seq[Seq[VeritasConstruct]]): VeritasConstruct = {
    if (!newchildren.isEmpty) throw new ClassCastException

    //return myself
    ImportAxioms
  }

  override def prettyString = "axioms"
}

final case object ImportFunctions extends ImportAnnotation {
  override val children = Seq()

  override def transformChildren(newchildren: Seq[Seq[VeritasConstruct]]): VeritasConstruct = {
    if (!newchildren.isEmpty) throw new ClassCastException

    //return myself
    ImportFunctions
  }

  override def prettyString = "functions"
}

final case object ImportGoals extends ImportAnnotation {
  override val children = Seq()

  override def transformChildren(newchildren: Seq[Seq[VeritasConstruct]]): VeritasConstruct = {
    if (!newchildren.isEmpty) throw new ClassCastException

    //return myself
    ImportGoals
  }

  override def prettyString = "goals"
}

final case object ImportConstructors extends ImportAnnotation {
  override val children = Seq()

  override def transformChildren(newchildren: Seq[Seq[VeritasConstruct]]): VeritasConstruct = {
    if (!newchildren.isEmpty) throw new ClassCastException

    //return myself
    ImportConstructors
  }

  override def prettyString = "constructors"
}

object ImportAnnotation {
  def from(term: StrategoTerm): ImportAnnotation = term match {
    case StrategoAppl("ImportAnnoNames")        => ImportNames
    case StrategoAppl("ImportAnnoAxioms")       => ImportAxioms
    case StrategoAppl("ImportAnnoFunctions")    => ImportFunctions
    case StrategoAppl("ImportAnnoGoals")        => ImportGoals
    case StrategoAppl("ImportAnnoConstructors") => ImportConstructors
    case t                                      => throw VeritasParseError("Expected import annotation ala ImportAnnoNames(), got: " + t)
  }
}