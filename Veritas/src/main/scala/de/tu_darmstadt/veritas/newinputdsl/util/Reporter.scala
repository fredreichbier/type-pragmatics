package de.tu_darmstadt.veritas.newinputdsl.util

import de.tu_darmstadt.veritas.newinputdsl.translator.SPLTranslationError

trait Reporter {
  def report[T](msg: String, line: Int): T = {
    throw new SPLTranslationError(s"Error($line): $msg")
  }

  def report[T](msg: String): T = {
    throw new SPLTranslationError(msg)
  }
}

object Reporter extends {
  def apply(): Reporter = new Reporter {}
}