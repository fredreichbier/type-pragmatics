package de.tu_darmstadt.veritas.backend.transformation

import de.tu_darmstadt.veritas.backend.veritas.VeritasConstruct

/**
 * abstract trait for transformations of arbitrary VeritasConstructs
 * when implementing, implement the transform method
 * when applying, use apply method
 * 
 * optionally, implement precheck if you need to check sth. for each Veritas construct
 * during a traversal or if you need to collect information about each Veritas construct
 * during traversal
 */
trait VeritasConstructTransformation {
  /**
   * translates a Veritas construct of interest into a sequence of other VeritasConstructs
   */
  def transform: PartialFunction[VeritasConstruct, Seq[VeritasConstruct]]
  
  /**
   * checks for each Veritas construct which is passed during a recursive traversal
   * whether a certain condition holds - if not, the construct is skipped during the traversal
   * (can for example be used to skip certain modules in a sequence of modules during a traversal)
   * 
   * this method can also be implemented with side-effects, 
   * in case e.g. some state has to be updated for each Veritas construct traversed
   */
  def precheck(vc: VeritasConstruct): Boolean = true

  /**
   * recursively apply the transformation defined by transform
   * to a given sequence of VeritasConstructs
   * * (top-down one pass traversal!)
   * (recursively traverses the AST behind each VeritasConstruct)
   */
  def apply(vcl: Seq[VeritasConstruct]): Seq[VeritasConstruct] = {
    (for (vc <- vcl if precheck(vc)) yield {
      if (transform.isDefinedAt(vc))
        apply(transform(vc))
      else {
        val newchildren = (vc.children map apply)
        try {
          Seq(vc.transformChildren(newchildren))
        } catch {
          case c: ClassCastException => throw TransformationError("Child could not be transformed!")
          case e: Exception          => throw e
        }
      }
    }).flatten
  }

}