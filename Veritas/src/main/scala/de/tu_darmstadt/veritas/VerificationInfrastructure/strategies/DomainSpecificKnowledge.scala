package de.tu_darmstadt.veritas.VerificationInfrastructure.strategies

trait DomainSpecificKnowledge[Type, FDef, Prop] {
  def failableTypes: Seq[Type]
  def recursiveFunctions: Map[FDef, (Type, Seq[Seq[Int]])] //Second part of pair Seq[Seq[Int]] contains the position(s) at which the function is marked recursive
  def distinctionHints: Map[FDef, Seq[Seq[Int]]] //maps a function def to a position description for top-level case distinctions (to serve as manual hint for complex function patterns of non-recursive functions)
  def progressProperties: Map[FDef, Set[Prop]]
  def preservationProperties: Map[FDef, Set[Prop]]
  def auxiliaryProperties: Map[FDef, Set[Prop]]

  def properties: Set[Prop]
  def staticFunctions: Set[FDef]
  def dynamicFunctions: Set[FDef]

  def preservables: Set[FDef]
  def lemmaGeneratorHints: Map[FDef, Seq[(Seq[String], Seq[String], Seq[String], Boolean)]]

  def lookupByFunName[T](mp: Map[FDef, Set[T]], funname: String): Iterable[T] = {
    val allkeys: Iterable[FDef] = mp.keys.filter ((fd: FDef) => retrieveFunName(fd) == funname)
    allkeys.flatMap(mp)
  }

  def lookupByFunName(fs: Set[FDef], funname: String): Option[FDef] = {
    fs find (fd => retrieveFunName(fd) == funname)
  }

  def lookupTRByName(ts: Set[Prop], trname: String): Option[Prop] = {
    ts find (tr => retrievePropName(tr) == trname)
  }

  def retrieveFunName(fd: FDef): String
  def retrievePropName(p: Prop): String
}
