package de.tu_darmstadt.veritas.backend.transformation.collect

import de.tu_darmstadt.veritas.backend.ast._
import de.tu_darmstadt.veritas.backend.ast.function._
import de.tu_darmstadt.veritas.backend.transformation.ModuleTransformation

trait CollectTypesDefs extends ModuleTransformation {
  //TODO: do we allow different data types declarations with the same name?
  // if yes, clarify semantics - if necessary, make sure that _dataTypes collects
  // ALL data type constructors that belong to a definition!
  private var _dataTypes: Map[String, (Boolean, Seq[DataTypeConstructor])] = Map()
  // constrTypes: datatype constructors and constants
  private var _constrTypes: Map[String, (Seq[SortRef], SortRef)] = Map()
  private var _consts: Set[String] = Set() // just the constants
  private var _functypes: Map[String, (Seq[SortRef], SortRef)] = Map()
  private var _pfunctypes: Map[String, (Seq[SortRef], SortRef)] = Map()
  private var _funcdefs: Map[String, Seq[FunctionEq]] = Map()

  def dataTypes = _dataTypes
  def constrTypes = _constrTypes
  def consts = _consts
  def functypes = _functypes
  def pfunctypes = _pfunctypes
  def funcdefs = _funcdefs

  def symbolType(name: String): Option[(Seq[SortRef], SortRef)] = constrTypes.get(name) match {
    case Some(t) => Some(t)
    case None => functypes.get(name) match {
      case Some(t) => Some(t)
      case None => pfunctypes.get(name)
    }
  }


  /**
   * top-level function for translating a Module to a TffFile
   */
  override def transModule(name: String, _is: Seq[Import], _mdefs: Seq[ModuleDef]): Seq[Module] = {
    _dataTypes = Map()
    _constrTypes = Map()
    _functypes = Map()
    _pfunctypes = Map()
    _funcdefs = Map()

    val is = trace(_is)(transModuleImport(_))
    val mdefs = transModuleTypedDefs(_mdefs)
    Seq(Module(name, is, mdefs))
  }

  /**
   * Make sure that type symbols are properly scoped by local and strategy blocks
   */
  override def transModuleDefs(mdef: ModuleDef): Seq[ModuleDef] = mdef match {
    case d: DataType =>
      _dataTypes += (d.name -> (d.open, d.constrs))
      super.transModuleDefs(d)

    case Local(defs) =>
      val oldDataTypes = _dataTypes
      val oldconstypes = _constrTypes
      val oldconsts = _consts
      val oldfunctypes = _functypes
      val oldpfunctypes = _pfunctypes
      val oldfuncdefs = _funcdefs
      val res = transModuleTypedDefs(defs)
      _dataTypes = oldDataTypes
      _constrTypes = oldconstypes
      _consts = oldconsts
      _functypes = oldfunctypes
      _pfunctypes = oldpfunctypes
      _funcdefs = oldfuncdefs
      Seq(Local(res))

    case Strategy(name,is,defs) =>
      val oldDataTypes = _dataTypes
      val oldconstypes = _constrTypes
      val oldconsts = _consts
      val oldfunctypes = _functypes
      val oldpfunctypes = _pfunctypes
      val oldfuncdefs = _funcdefs
      val res = transModuleTypedDefs(defs)
      _dataTypes = oldDataTypes
      _constrTypes = oldconstypes
      _consts = oldconsts
      _functypes = oldfunctypes
      _pfunctypes = oldpfunctypes
      _funcdefs = oldfuncdefs
      Seq(Strategy(name, is, res))

    case _ =>
      super.transModuleDefs(mdef)
  }

  /**
   * Visit type declarations first.
   */
  def transModuleTypedDefs(mdefs: Seq[ModuleDef]): Seq[ModuleDef] = {
    val mdefsTyped = mdefs map { mdef => mdef match {
      case DataType(_,_,_)
         | Functions(_)
         | PartialFunctions(_)
         | Sorts(_)
        => Left(trace(mdef)(transModuleDefs(_))) // Left = done in first phase
      case other => Right(Seq(other))  // Right = to be done in second phase
    }}
    mdefsTyped flatMap {
      case Left(seq) => seq
      case Right(Seq(mdef)) => trace(mdef)(transModuleDefs(_))
    }
  }

  override def transDataTypeConstructor(d: DataTypeConstructor, dataType: String): Seq[DataTypeConstructor] = {
    withSuper(super.transDataTypeConstructor(d, dataType)) {
      case d =>
        _constrTypes += (d.name -> (d.in -> SortRef(dataType)))
        Seq(d)
    }
  }

  override def transConstDecl(d: ConstDecl): Seq[ConstDecl] = {
    withSuper(super.transConstDecl(d)) {
      case d =>
        _constrTypes += (d.name -> (Seq() -> d.out))
        _consts += d.name
        Seq(d)
    }
  }

  override def transSortDefs(sd: SortDef): Seq[SortDef] =
    withSuper(super.transSortDefs(sd)) {
      case sd => {
        if (!_dataTypes.isDefinedAt(sd.name))
          _dataTypes += (sd.name -> (true, Seq()))
        Seq(sd)
      }
    }

  override def transFunctionSig(sig: FunctionSig): FunctionSig =
    withSuper(super.transFunctionSig(sig)) {
      case sig =>
        if (path(2).isInstanceOf[Functions])
          _functypes += (sig.name -> (sig.in, sig.out))
        else if (path(2).isInstanceOf[PartialFunctions])
          _pfunctypes += (sig.name -> (sig.in, sig.out))
        sig
    }

  override def transTypingRules(tr: TypingRule): Seq[TypingRule] =
    withSuper(super.transTypingRules(tr)) {
      case tr =>
        Seq(tr)
    }

  override def transFunctionDefs(fdef: FunctionDef): Seq[FunctionDef] =
    withSuper(super.transFunctionDefs(fdef)) {
      case fdef => {
        val fname = fdef.signature.name
        if (!_funcdefs.isDefinedAt(fname))
          _funcdefs += (fname -> fdef.eqn)
        Seq(fdef)
      }
    }

  def inferMetavarTypes(tr: TypingRule): Map[MetaVar, SortRef] =
    new TypeInference(symbolType).inferMetavarTypes(tr)

  def inferMetavarTypes(vars: Iterable[MetaVar], jdgs: Seq[TypingRuleJudgment]): Map[MetaVar, SortRef] =
    new TypeInference(symbolType).inferMetavarTypes(vars, jdgs)
}

class CollectTypesDefsClass extends CollectTypesDefs