package de.tu_darmstadt.veritas.scalaspl.dskbuilderfiles

import de.tu_darmstadt.veritas.scalaspl.lang.ScalaSPLSpecification

object DSKRecursiveInnerHasMoreThanOneCtor extends ScalaSPLSpecification {
  sealed trait outer2 extends Expression
  case class outerouter(outer: enclosing) extends outer2

  sealed trait enclosing extends Expression
  case class outer(inner: inner) extends enclosing
  case class otherouter(inner: inner) extends enclosing

  sealed trait value extends Expression
  case class valuebind() extends value

  sealed trait inner extends Expression
  case class cons(x: value, inner: inner) extends inner
  case class nil() extends inner


  @Recursive(1, 0)
  def recursiveOneLevel(inner: inner, enclosing: enclosing): inner = (inner, enclosing) match {
    case (a, outer(b)) => b
  }

  @Recursive(0, 0, 0)
  def recursiveTwoLevel(outer2: outer2): inner = outer2 match {
    case outerouter(outer(cons(x, rest))) => rest
  }

}
