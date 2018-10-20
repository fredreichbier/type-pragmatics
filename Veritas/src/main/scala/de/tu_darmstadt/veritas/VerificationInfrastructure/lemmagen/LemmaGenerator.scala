package de.tu_darmstadt.veritas.VerificationInfrastructure.lemmagen

import java.io.File

import de.tu_darmstadt.veritas.backend.ast.function.{FunctionDef, FunctionExpApp, FunctionExpMeta, FunctionMeta}
import de.tu_darmstadt.veritas.backend.ast._
import de.tu_darmstadt.veritas.backend.transformation.collect.TypeInference.Sort
import de.tu_darmstadt.veritas.backend.util.{FreeVariables, FreshNames}
import de.tu_darmstadt.veritas.scalaspl.dsk.DomainSpecificKnowledgeBuilder
import de.tu_darmstadt.veritas.scalaspl.translator.ScalaSPLTranslator

import scala.collection.mutable

class LemmaGenerator(specFile: File) {
  private val spec = new ScalaSPLTranslator().translate(specFile)
  private val dsk = DomainSpecificKnowledgeBuilder().build(specFile)
  private val enquirer = new LemmaGenSpecEnquirer(spec, dsk)
  private val fresh = new FreshNames


  def getVariablePrefix(typ: SortRef): String = {
    // get upper-cased prefix of type name
    val prefix = typ.name.takeWhile(_.isUpper)
    if(prefix.isEmpty) {
      "v"
    } else {
      prefix.toLowerCase
    }
  }

  def generateMetaVar(typ: SortRef): MetaVar = {
    val prefix = getVariablePrefix(typ)
    var argname = fresh.freshName(prefix)
    val meta = MetaVar(argname)
    meta.typ = Some(Sort(typ.name))
    meta
  }

  def buildSuccessLemma(function: FunctionDef): Lemma = {
    val arguments = function.signature.in.map(ref => (generateMetaVar(ref), ref))
    val (_, successConstructor) = enquirer.retrieveFailableConstructors(function.signature.out)
    val successVar = generateMetaVar(successConstructor.in.head)
    val invocationExp = FunctionExpApp(function.signature.name, arguments.map {
      case (name, typ) => FunctionMeta(name)
    })
    val successExp = FunctionExpApp(successConstructor.name, Seq(FunctionMeta(successVar)))
    val equality = enquirer.makeEquation(invocationExp, successExp).asInstanceOf[FunctionExpJudgment]
    val exists = ExistsJudgment(Seq(successVar), Seq(equality))
    new Lemma(arguments.toMap, TypingRule(s"${function.signature.name}Progress", Seq(), Seq(exists)))
  }

  def selectPredicate(baseLemma: Lemma, predicate: FunctionDef): Lemma = {
    val (boundTypes, unboundTypes) = predicate.signature.in.partition(baseLemma.boundTypes.contains(_))
    var lemma = baseLemma
    for(unboundType <- unboundTypes) {
      val newMetaVar = generateMetaVar(unboundType)
      lemma = lemma.bind(newMetaVar)
    }
    // collect vars of suitable type for invocation TODO: there might be choices here
    val arguments = predicate.signature.in.map(typ => lemma.bindings.find(_._2 == typ).get._1)
    val invocationExp = FunctionExpJudgment(
      FunctionExpApp(
        predicate.signature.name,
        arguments.map(v => FunctionMeta(v))
      )
    )
    if(lemma.rule.premises contains invocationExp)
      lemma
    else
      lemma.withPremise(invocationExp)
  }

  def selectSuccessPredicate(baseLemma: Lemma, function: FunctionDef): Lemma = {
    val (_, successConstructor) = enquirer.retrieveFailableConstructors(function.signature.out)
    val successVar = generateMetaVar(successConstructor.in.head)
    val (boundTypes, unboundTypes) = function.signature.in.partition(baseLemma.boundTypes.contains(_))
    val newMetaVars = unboundTypes.map(generateMetaVar) :+ successVar
    var lemma = baseLemma.bind(newMetaVars:_*)
    // collect vars of suitable type for invocation TODO: there might be choices here
    val arguments = function.signature.in.map(typ => lemma.bindings.find(_._2 == typ).get._1)
    val invocationExp = FunctionExpApp(
      function.signature.name,
      arguments.map(v => FunctionMeta(v))
    )
    val successExp = FunctionExpApp(successConstructor.name, Seq(FunctionMeta(successVar)))
    val equality = enquirer.makeEquation(invocationExp, successExp).asInstanceOf[FunctionExpJudgment]
    if(lemma.rule.premises contains equality)
      lemma
    else
      lemma.withPremise(equality)
  }

  def generateProgressLemma(dynamicFunctionName: String): Lemma = {
    val dynamicFunction = dsk.dynamicFunctions.find(_.signature.name == dynamicFunctionName).get
    // the function's return type must be failable
    var lemma = buildSuccessLemma(dynamicFunction)
    lemma
  }

  def evolveProgressLemma(lemma: Lemma): Iterable[Lemma] = {
    // build a map of predicates and producers of "in types"
    val predicates = lemma.boundTypes.flatMap(enquirer.retrievePredicates)
    val producers = lemma.boundTypes.flatMap(enquirer.retrieveProducers)
    val failableProducers = producers.filter(defn => enquirer.isFailableType(defn.signature.out))
    // we just have to find matching premises
    // find predicates that involve any of the given types
    val lemmas = mutable.HashSet.empty[Lemma]
    for(fn <- predicates)
      lemmas += selectPredicate(lemma, fn)
    for(fn <- failableProducers if dsk.staticFunctions.contains(fn))
      lemmas += selectSuccessPredicate(lemma, fn)
    lemmas
  }
}