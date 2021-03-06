package veritas.benchmarking

import java.io.{File, PrintWriter}

import info.folone.scala.poi._
import info.folone.scala.poi.impure.load

import scala.collection.mutable.ListBuffer
import scala.reflect.NameTransformer
import scala.util.matching.Regex

case class TransformationError(m: String) extends RuntimeException(m)

abstract class DataLayout(files: Seq[File], timeout: String) {
  type ConfigValue = Any


  trait ConfigOption extends Enumeration with Iterable[ConfigValue] {
    override def iterator = values.iterator

    override def toString =
      ((getClass.getName stripSuffix NameTransformer.MODULE_SUFFIX_STRING split '.').last split
        Regex.quote(NameTransformer.NAME_JOIN_STRING)).last

    //sorting of a ConfigOption (converted to a string) - default: alphabetically
    //individual ConfigOption can override either value order to specify a manual order, or sort
    val order: Seq[Value] = iterator.toSeq

    def isSmaller: (this.Value, this.Value) => Boolean = (s1, s2) => order.indexOf(s1) < order.indexOf(s2)
  }

  //default sorting function
  //protected def sortConfsFunction[K] = (k1: K, k2: K) => k1.toString < k2.toString

  object ProverConfEnum extends ConfigOption {
    val Vampire_3 = Value("vampire-3.0")
    val Vampire_4 = Value("vampire-4.0")
    val Eprover = Value("eprover")
    val Princess_Casc = Value("princess")
  }

  object GoalCategoryEnum extends ConfigOption {
    val Counterexample = Value("counterexample")
    val Execution = Value("execution")
    val Proof = Value("proof")
    val Synthesis = Value("synthesis")
    val Test = Value("test")
  }

  object TypingConfEnum extends ConfigOption {
    val Barefof = Value("barefof")
    val Guardedfof = Value("guardedfof")
    val Tff = Value("tff")

    override val order = Seq(Tff, Guardedfof, Barefof)
  }

  object VariableConfEnum extends ConfigOption {
    val Unchanged = Value("unchanged")
    val Inlievery = Value("inlievery")
    val Nameevery = Value("nameevery")
    val Namparres = Value("namparres")

    override val order = Seq(Unchanged, Inlievery, Nameevery, Namparres)
  }

  object SimplConfEnum extends ConfigOption {
    val Nonsimpl = Value("nonsimpl")
    val Logsimpl = Value("logsimpl")
    val Patsimpl = Value("patsimpl")

    override val order = Seq(Nonsimpl, Logsimpl, Patsimpl)
  }

  object SelectionConfEnum extends ConfigOption {
    val Selectall = Value("selectall")
    val Selectusedfp = Value("selectusedfp")
    val Noinversion = Value("noinversion")
    val Noinversionselectusedfp = Value("noinversionselectusedfp")

    override val order = Seq(Selectall, Noinversion, Selectusedfp, Noinversionselectusedfp)
  }

  trait CSVTransformable {
    def getCSVcells(b: StringBuilder, end: Boolean = false)
  }

  abstract class Key(val typingConf: TypingConfEnum.Value,
                     val variableConf: VariableConfEnum.Value,
                     val simplConf: SimplConfEnum.Value,
                     val selectConf: SelectionConfEnum.Value)

  //this class is used in immediate representations, which group individual data points by a configuration
  //offers comparability method for configurations
  case class CommonConfKey(override val typingConf: TypingConfEnum.Value,
                           override val variableConf: VariableConfEnum.Value,
                           override val simplConf: SimplConfEnum.Value,
                           override val selectConf: SelectionConfEnum.Value)
    extends Key(typingConf, variableConf, simplConf, selectConf)

  object CommonConfKey {
    def isSmallerConf: (CommonConfKey, CommonConfKey) => Boolean = (k1, k2) =>
      (k1, k2) match {
        case (kl, kr) if kl.typingConf != kr.typingConf => TypingConfEnum.isSmaller(kl.typingConf, kr.typingConf)
        case (kl, kr) if k1.variableConf != k2.variableConf => VariableConfEnum.isSmaller(k1.variableConf, k2.variableConf)
        case (kl, kr) if k1.simplConf != k2.simplConf => SimplConfEnum.isSmaller(k1.simplConf, k2.simplConf)
        case (kl, kr) if k1.selectConf != k2.selectConf => SelectionConfEnum.isSmaller(k1.selectConf, k2.selectConf)
        case _ => false
      }
  }

  //abstraction for full keys of individual data points in overview tables
  abstract class FullKey(val proverConf: ProverConfEnum.Value,
                         val goalCategory: GoalCategoryEnum.Value,
                         override val typingConf: TypingConfEnum.Value,
                         override val variableConf: VariableConfEnum.Value,
                         override val simplConf: SimplConfEnum.Value,
                         override val selectConf: SelectionConfEnum.Value)
    extends Key(typingConf, variableConf, simplConf, selectConf) with CSVTransformable {
    override def getCSVcells(b: StringBuilder, end: Boolean = false) = {
      makeCSVcell(b, proverConf.toString)
      makeCSVcell(b, goalCategory.toString)
      makeCSVcell(b, typingConf.toString)
      makeCSVcell(b, variableConf.toString)
      makeCSVcell(b, simplConf.toString)
      makeCSVcell(b, selectConf.toString, end)
    }

    def commonKey = CommonConfKey(typingConf, variableConf, simplConf, selectConf)

  }


  case class ConfFullKey(override val proverConf: ProverConfEnum.Value,
                         override val goalCategory: GoalCategoryEnum.Value,
                         override val typingConf: TypingConfEnum.Value,
                         override val variableConf: VariableConfEnum.Value,
                         override val simplConf: SimplConfEnum.Value,
                         override val selectConf: SelectionConfEnum.Value)
    extends FullKey(proverConf, goalCategory, typingConf, variableConf, simplConf, selectConf)

  case class RawFullKey(override val proverConf: ProverConfEnum.Value,
                        override val goalCategory: GoalCategoryEnum.Value,
                        override val typingConf: TypingConfEnum.Value,
                        override val variableConf: VariableConfEnum.Value,
                        override val simplConf: SimplConfEnum.Value,
                        override val selectConf: SelectionConfEnum.Value,
                        filename: String) extends FullKey(proverConf, goalCategory, typingConf, variableConf, simplConf, selectConf) {
    override def getCSVcells(b: StringBuilder, end: Boolean = false) = {
      super.getCSVcells(b)
      makeCSVcell(b, filename, end)
    }
  }

  // valid: Result is valid (not valid is for example eprover for typed logic, since eprover simply does not support typed logic)
  abstract class Result(valid: Boolean) extends CSVTransformable {
    def isValid: Boolean = valid
  }

  val NAString = "NA"


  case class OverviewResult(succnum: Int, filenum: Int, succrate: Double, avgSuccTime: Double, avgDev: Double, valid: Boolean = true) extends Result(valid) {
    override def getCSVcells(b: StringBuilder, end: Boolean = false) = {
      makeCSVcell(b, if (valid) succnum.toString else NAString)
      makeCSVcell(b, if (valid) filenum.toString else NAString)
      makeCSVcell(b, if (valid) succrate.toString else NAString)
      makeCSVcell(b, if (valid) avgSuccTime.toString else NAString, end)
      makeCSVcell(b, if (valid) avgDev.toString else NAString)
    }
  }


  case class RawResult(provertime: Double, status: ProverStatus, details: String, valid: Boolean = true) extends Result(valid) {
    override def getCSVcells(b: StringBuilder, end: Boolean = false) = {
      makeCSVcell(b, if (valid) provertime.toString else NAString)
      makeCSVcell(b, if (valid) status.toString else NAString)
      makeCSVcell(b, if (valid) details else NAString)
    }
  }

  //Parametric class for translating a given String to a Configuration value such as the ones above
  case class ConfValueExtractor[E <: ConfigOption](val e: E) {

    def extractConfVal(s: String): e.Value = {
      try {
        (for (v <- e.values if (v.toString == s)) yield v).head
      } catch {
        case e: NoSuchElementException => throw TransformationError(s"$s was not of type $e")
        case e: Exception => throw e
      }
    }
  }


  protected def makeCSVcell(b: StringBuilder, s: String, end: Boolean = false) = {
    b ++= escape(s, sep, "\"")
    if (end) b ++= "\n"
    else b ++= sep
  }

  val sep = ","

  private def escape(s: String, avoid: String, quote: String): String = {
    if (!s.contains(avoid) && !s.contains("\n"))
      s.trim
    else
      quote + s.trim.replace("\"", "\\\"").replace("\n", "\t\t") + quote
  }

  def invalidResult(k: FullKey): Boolean = k.proverConf == ProverConfEnum.Eprover && k.typingConf == TypingConfEnum.Tff

  def extractRawMap(workbook: Workbook) = {
    (for {sh <- workbook.sheets
          row <- sh.rows if (row.index != 0)} yield {
      val pc = extractString(ProverConfEnum, sh.name)
      val gc = extractAtIndex(GoalCategoryEnum, row, 2)
      val tc = extractAtIndex(TypingConfEnum, row, 3)
      val vc = extractAtIndex(VariableConfEnum, row, 4)
      val sc = extractAtIndex(SimplConfEnum, row, 5)
      val selc = extractAtIndex(SelectionConfEnum, row, 6)
      val filename = getCell(row, 7)
      val pt = getCell(row, 8).toDouble
      val stat = makeProverStatus(getCell(row, 9))
      val det = getCell(row, 10)
      val rk = RawFullKey(pc, gc, tc, vc, sc, selc, filename)
      if (invalidResult(rk))
        rk -> RawResult(pt, stat, det, false)
      else
        rk -> RawResult(pt, stat, det)
    }).toMap
  }


  def extractOverviewMap(workbook: Workbook) = {
    (for {sh <- workbook.sheets
          row <- sh.rows if (row.index != 0)} yield {
      val pc = extractAtIndex(ProverConfEnum, row, 0)
      val gc = extractAtIndex(GoalCategoryEnum, row, 2)
      val tc = extractAtIndex(TypingConfEnum, row, 3)
      val vc = extractAtIndex(VariableConfEnum, row, 4)
      val sc = extractAtIndex(SimplConfEnum, row, 5)
      val selc = extractAtIndex(SelectionConfEnum, row, 6)
      val succnum = getCell(row, 7).toDouble.toInt
      val filenum = getCell(row, 8).toDouble.toInt
      val succrate = getCell(row, 9).toDouble
      val avgsucctime = getCell(row, 10).toDouble
      val avgdev = getCell(row, 11).toDouble
      val ck = ConfFullKey(pc, gc, tc, vc, sc, selc)
      if (invalidResult(ck))
        ck -> OverviewResult(succnum, filenum, succrate, avgsucctime, avgdev, false)
      else
        ck -> OverviewResult(succnum, filenum, succrate, avgsucctime, avgdev)
    }).toMap
  }

  private def makeProverStatus(s: String): ProverStatus = {
    val stat = """([a-zA-Z]+)([\\(.+\\)]?)""".r.unanchored
    s match {
      case stat(ps, det) if (ps == "Proved") => Proved
      case stat(ps, det) if (ps == "Disproved") => Disproved
      case stat(ps, det) if (ps == "Inconclusive") => Inconclusive(det)
    }
  }

  private def extractString[E <: ConfigOption](e: E, s: String) = ConfValueExtractor(e).extractConfVal(s)

  private def getCell(row: Row, i: Int): String = row.cells.find(c => c.index == i).getOrElse(null) match {
    case StringCell(i, s) => s
    case NumericCell(i, n) => n.toString
    case c => c.toString()
  }

  private def extractAtIndex[E <: ConfigOption](e: E, row: Row, i: Int) = extractString(e, getCell(row, i))

  def filterProver[K <: FullKey, R <: Result](dataMap: K Map R, prover: List[ProverConfEnum.Value]): (K Map R) = {
    for ((k, v) <- dataMap if (prover contains k.proverConf)) yield (k, v)
  }

  def filterGoalCategory[K <: FullKey, R <: Result](dataMap: K Map R, goalcats: List[GoalCategoryEnum.Value]): (K Map R) = {
    for ((k, v) <- dataMap if (goalcats contains k.goalCategory)) yield (k, v)
  }

  def filterTypingConf[K <: FullKey, R <: Result](dataMap: K Map R, typingconf: List[TypingConfEnum.Value]): (K Map R) = {
    for ((k, v) <- dataMap if (typingconf contains k.typingConf)) yield (k, v)
  }

  def filterVariableConf[K <: FullKey, R <: Result](dataMap: K Map R, variableconf: List[VariableConfEnum.Value]): (K Map R) = {
    for ((k, v) <- dataMap if (variableconf contains k.variableConf)) yield (k, v)
  }

  def filterSimplConf[K <: FullKey, R <: Result](dataMap: K Map R, simplconf: List[SimplConfEnum.Value]): (K Map R) = {
    for ((k, v) <- dataMap if (simplconf contains k.simplConf)) yield (k, v)
  }

  def filterSelectionConf[K <: FullKey, R <: Result](dataMap: K Map R, selectConf: List[SelectionConfEnum.Value]): (K Map R) = {
    for ((k, v) <- dataMap if (selectConf contains k.selectConf)) yield (k, v)
  }

  protected def writeToFile(filepath: String, s: String) = {
    val filehandler = new File(filepath)
    if (!filehandler.getParentFile.exists())
      filehandler.getParentFile.mkdirs()
    filehandler.createNewFile()
    new PrintWriter(filehandler) {
      write(s);
      close
    }
  }

  protected def layoutSuccessRateIndividualOpt[K <: ConfigOption](confopt: K)(accessConfKey: ConfFullKey => confopt.Value)(filteredoverview: ConfFullKey Map OverviewResult): String = {
    val intermediateMap: (confopt.Value Map List[String]) = (for (opt <- confopt.iterator) yield {
      val succRateList = (for ((k, v) <- filteredoverview
                               if (accessConfKey(k) == opt)) yield
        if (v.isValid) v.succrate.toString else NAString).toList
      (opt -> succRateList)
    }).toMap

    makeCSVColBased(intermediateMap, confopt.isSmaller)
  }

  // parameter axsel: true - also append key for axiom selection strategy, false - do not append key for axiom selection strategy
  protected def layoutSuccessRateOfCompStrat(axsel: Boolean)(filteredoverview: ConfFullKey Map OverviewResult): String = {
    val groupedoverview: Map[CommonConfKey, Map[ConfFullKey, OverviewResult]] = filteredoverview.groupBy[CommonConfKey](kr => kr._1.commonKey)
    val intermediateMap: (CommonConfKey Map List[String]) = for ((cnf, confmap) <- groupedoverview) yield
      cnf -> (confmap.toList map (kr => if (kr._2.isValid) kr._2.succrate.toString else NAString))

    makeCSVColBased(intermediateMap, CommonConfKey.isSmallerConf, (k: CommonConfKey) => createShortenedConfCell(k, axsel))
  }

  // parameter axsel: true - also append key for axiom selection strategy, false - do not append key for axiom selection strategy
  protected def createShortenedConfCell(ck: Key, axsel: Boolean = true): String = {
    val typshort = ck.typingConf match {
      case TypingConfEnum.Barefof => "e"
      case TypingConfEnum.Tff => "t"
      case TypingConfEnum.Guardedfof => "g"
    }

    val varshort = ck.variableConf match {
      case VariableConfEnum.Inlievery => "in"
      case VariableConfEnum.Nameevery => "ne"
      case VariableConfEnum.Namparres => "np"
      case VariableConfEnum.Unchanged => "u"
    }

    val simplshort = ck.simplConf match {
      case SimplConfEnum.Nonsimpl => "n"
      case SimplConfEnum.Logsimpl => "g"
      case SimplConfEnum.Patsimpl => "d"
    }

    val selectshort = if (axsel)
      ck.selectConf match {
        case SelectionConfEnum.Selectall => "a"
        case SelectionConfEnum.Selectusedfp => "r"
        case SelectionConfEnum.Noinversion => "ni"
        case SelectionConfEnum.Noinversionselectusedfp => "nir"
      }
    else ""

    s"$typshort$varshort$simplshort$selectshort"
  }


  //keys of maps designate columns
  protected def makeCSVColBased[K, V](dataMap: (K Map Seq[V]), lt: (K, K) => Boolean, kToString : K => String = (k: K) => k.toString): String = {
    val b = StringBuilder.newBuilder
    val orderedkeys = dataMap.keys.toList.sortWith(lt)

    def maxvlength: Int = {
      var max = 0
      for (v <- dataMap.values)
        if ((v.length) > max)
          max = v.length
      max
    }

    def makeRow(i: Int): Unit = {
      for (k <- orderedkeys) {
        val last = (k == orderedkeys.last)
        if (dataMap(k).isDefinedAt(i)) {
          val csvtransformablev = new SingleCSVWrapper[V](dataMap(k)(i))
          csvtransformablev.getCSVcells(b, last)
        } else //make an empty cell
          makeCSVcell(b, "", last)
      }
    }

    //make key row first
    for (k <- orderedkeys) {
      val csvtransformablek = new SingleCSVWrapper[K](k)
      val last = (k == orderedkeys.last)
      csvtransformablek.getCSVcells(b, kToString, last)
    }

    //attach value rows
    for (i <- 0 until maxvlength) {
      makeRow(i)
    }

    b.toString()
  }

  class SingleCSVWrapper[K](value: K) extends CSVTransformable {
    override def getCSVcells(b: StringBuilder, end: Boolean = false) = {
      makeCSVcell(b, value.toString, end)
    }

    def getCSVcells(b: StringBuilder, confToString: K => String, end: Boolean) = {
      makeCSVcell(b, confToString(value), end)
    }
  }

  def doForProvers[K <: FullKey, R <: Result](proverlist: List[ProverConfEnum.Value], filepath: String, filename: String, layoutfun: (K Map R) => String, data: (K Map R)) = {
    for (prover <- proverlist) {
      val file = s"$filepath/${prover.toString}-$filename"
      val filteredoverview = filterProver(data, List(prover))
      val layouted = layoutfun(filteredoverview)
      if (!layouted.isEmpty) writeToFile(file, layouted)
    }
  }

  def doSingle[K <: FullKey, R <: Result](filepath: String, filename: String, layoutfun: (K Map R) => String, data: (K Map R)) = {
    val file = s"$filepath/$filename"
    val layouted = layoutfun(data)
    if (!layouted.isEmpty) writeToFile(file, layouted)
  }

  def layoutAll(outputPath: String): Unit
}

/**
  * Created by sylvia on 01/03/16.
  *
  * assumes that it receives two excel files:
  * 1) raw data (base name)
  * 2) overview data (base name)
  *
  */

case class SingleDataLayout(files: Seq[File], stimeout: String) extends DataLayout(files, stimeout) {
  val workbooks = for (f <- files) yield load(f.getAbsolutePath)

  val rawworkbook = workbooks.head
  val overviewworkbook = workbooks.last

  val rawMap: RawFullKey Map RawResult = extractRawMap(rawworkbook)

  val overviewMap: ConfFullKey Map OverviewResult = extractOverviewMap(overviewworkbook)

  //keys of maps designate rows
  private def makeCSVRowBased[K, V](dataMap: (K Map V), sortValues: ((K, V), (K, V)) => Boolean, kToString: K => String = (k: K) => k.toString): String = {
    val b = StringBuilder.newBuilder
    val orderedMap = dataMap.toList.sortWith(sortValues)

    for ((k, v) <- orderedMap) {
      val csvtransformablek = new SingleCSVWrapper[K](k)
      val csvtransformablev = new SingleCSVWrapper[V](v)
      csvtransformablek.getCSVcells(b, kToString, false)
      csvtransformablev.getCSVcells(b, true)
    }

    b.toString()
  }

  def doForCategories[K <: FullKey, R <: Result](catlist: List[GoalCategoryEnum.Value], filepath: String, filename: String, layoutfun: (K Map R) => String, data: (K Map R) = overviewMap) = {
    for (cat <- catlist) {
      val file = s"$filepath/${cat.toString}-$filename"
      val filteredoverview = filterGoalCategory(data, List(cat))
      val layouted = layoutfun(filteredoverview)
      if (!layouted.isEmpty) writeToFile(file, layouted)
    }
  }

  def doForallProvers[K <: FullKey, R <: Result](filepath: String, filename: String, layoutfun: (K Map R) => String, data: (K Map R) = overviewMap) =
    doForProvers(ProverConfEnum.iterator.toList, filepath, filename, layoutfun, data)

  def doForallCategories[K <: FullKey, R <: Result](filepath: String, filename: String, layoutfun: (K Map R) => String, data: (K Map R) = overviewMap) =
    doForCategories(GoalCategoryEnum.iterator.toList, filepath, filename, layoutfun, data)

  def doForProversCategories[K <: FullKey, R <: Result](proverlist: List[ProverConfEnum.Value], catlist: List[GoalCategoryEnum.Value], filepath: String, filename: String, layoutfun: (K Map R) => String, data: (K Map R) = overviewMap) = {
    for {prover <- proverlist
         cat <- catlist} {
      val file = s"$filepath/${prover.toString}-${cat.toString}-$filename"
      val filteredoverview = filterGoalCategory(filterProver(data, List(prover)), List(cat))
      val layouted = layoutfun(filteredoverview)
      if (!layouted.isEmpty) writeToFile(file, layouted)
    }
  }

  def doForallProversCategories[K <: FullKey, R <: Result](filepath: String, filename: String, layoutfun: (K Map R) => String, data: (K Map R) = overviewMap) =
    doForProversCategories(ProverConfEnum.iterator.toList, GoalCategoryEnum.iterator.toList, filepath, filename, layoutfun, data)

  // merges data of the given categories in one table
  def doForProversCategoriesMerge[K <: FullKey, R <: Result](proverlist: List[ProverConfEnum.Value], catlist: List[GoalCategoryEnum.Value], filepath: String, filename: String, layoutfun: (K Map R) => String, data: (K Map R) = overviewMap) = {
    val filtereddata = filterGoalCategory(data, catlist)
    doForProvers(proverlist, filepath, filename, layoutfun, filtereddata)
  }


  private def layoutAvgSuccessTimeIndividualOpt[K <: ConfigOption](confopt: K)(accessConfKey: FullKey => confopt.Value)(filteredoverview: ConfFullKey Map OverviewResult): String = {

    val intermediateMap: (confopt.Value Map List[String]) = (for (opt <- confopt.iterator) yield {
      val avgSuccTimeList = (for ((k, v) <- filteredoverview
                                  if (accessConfKey(k) == opt)) yield
        if (v.isValid) v.avgSuccTime.toString else NAString).toList
      val filterzeroes = avgSuccTimeList filter (p => p != 0.0)
      (opt -> filterzeroes)
    }).toMap

    makeCSVColBased(intermediateMap, confopt.isSmaller)
  }


  private def layoutRawDetailedTime(filteredraw: (RawFullKey Map RawResult)): String = {
    val groupedraw: Map[CommonConfKey, Map[RawFullKey, RawResult]] = filteredraw.groupBy[CommonConfKey](kr => kr._1.commonKey)
    val intermediateMap: (CommonConfKey Map List[String]) = for ((cnf, confmap) <- groupedraw) yield
      cnf -> (confmap.toList map (kr => if (kr._2.isValid) kr._2.provertime.toString else NAString))

    makeCSVColBased(intermediateMap, CommonConfKey.isSmallerConf, (k: CommonConfKey) => createShortenedConfCell(k))
  }

  private def layoutIndividualSuccessRates(axsel: Boolean)(filteredoverview: (ConfFullKey Map OverviewResult)): String = {
    val intermediateMap: (CommonConfKey Map String) = for ((k, v) <- filteredoverview) yield {
      (k.commonKey -> (if (v.isValid) v.succrate.toString else NAString))
    }

    val valuesorting: (String, String) => Boolean = (s1, s2) => if (s1 == NAString) false else if (s2 == NAString) true else s1.toDouble > s2.toDouble //put NA-values to the end of the list, otherwise compare by number
    makeCSVRowBased(intermediateMap, ((k1v1: (CommonConfKey, String), k2v2: (CommonConfKey, String)) => valuesorting(k1v1._2, k2v2._2)), (k: CommonConfKey) => createShortenedConfCell(k, axsel))
  }


  def layoutAll(outputPath: String): Unit = {
    println("Layouting!")

    //first, ignore axiom selection (generates data for original PPDP paper)
    val filterselectall = filterSelectionConf(overviewMap, List(SelectionConfEnum.Selectall))


    //Overview graphs all categories for each prover, success rates & average success time
    doForallProvers(s"$outputPath/PerProver/$stimeout/SuccRate", "successrate_per_goalcategory.csv", layoutSuccessRateIndividualOpt(GoalCategoryEnum)(k => k.goalCategory), filterselectall)
    doForallProvers(s"$outputPath/PerProver/$stimeout/SuccRate", "successrate_per_typingconfiguration.csv", layoutSuccessRateIndividualOpt(TypingConfEnum)(k => k.typingConf), filterselectall)
    doForallProvers(s"$outputPath/PerProver/$stimeout/SuccRate", "successrate_per_variableconfiguration.csv", layoutSuccessRateIndividualOpt(VariableConfEnum)(k => k.variableConf), filterselectall)
    doForallProvers(s"$outputPath/PerProver/$stimeout/SuccRate", "successrate_per_simplificationconfiguration.csv", layoutSuccessRateIndividualOpt(SimplConfEnum)(k => k.simplConf), filterselectall)
    doForallProvers(s"$outputPath/PerProver/$stimeout/AvgSuccTime", "avgsuccesstime_per_goalcategory.csv", layoutAvgSuccessTimeIndividualOpt(GoalCategoryEnum)(k => k.goalCategory), filterselectall)
    doForallProvers(s"$outputPath/PerProver/$stimeout/AvgSuccTime", "avgsuccesstime_per_typingconfiguration.csv", layoutAvgSuccessTimeIndividualOpt(TypingConfEnum)(k => k.typingConf), filterselectall)
    doForallProvers(s"$outputPath/PerProver/$stimeout/AvgSuccTime", "avgsuccesstime_per_variableconfiguration.csv", layoutAvgSuccessTimeIndividualOpt(VariableConfEnum)(k => k.variableConf), filterselectall)
    doForallProvers(s"$outputPath/PerProver/$stimeout/AvgSuccTime", "avgsuccesstime_per_simplificationconfiguration.csv", layoutAvgSuccessTimeIndividualOpt(SimplConfEnum)(k => k.simplConf), filterselectall)

    //Overview graphs per category for each prover, success rates & average success time
    doForallProversCategories(s"$outputPath/PerProverPerCategory/$stimeout/SuccRate", "successrate_per_typingconfiguration.csv", layoutSuccessRateIndividualOpt(TypingConfEnum)(k => k.typingConf), filterselectall)
    doForallProversCategories(s"$outputPath/PerProverPerCategory/$stimeout/SuccRate", "successrate_per_variableconfiguration.csv", layoutSuccessRateIndividualOpt(VariableConfEnum)(k => k.variableConf), filterselectall)
    doForallProversCategories(s"$outputPath/PerProverPerCategory/$stimeout/SuccRate", "successrate_per_simplificationconfiguration.csv", layoutSuccessRateIndividualOpt(SimplConfEnum)(k => k.simplConf), filterselectall)
    doForallProversCategories(s"$outputPath/PerProverPerCategory/$stimeout/AvgSuccTime", "avgsuccesstime_per_typingconfiguration.csv", layoutAvgSuccessTimeIndividualOpt(TypingConfEnum)(k => k.typingConf), filterselectall)
    doForallProversCategories(s"$outputPath/PerProverPerCategory/$stimeout/AvgSuccTime", "avgsuccesstime_per_variableconfiguration.csv", layoutAvgSuccessTimeIndividualOpt(VariableConfEnum)(k => k.variableConf), filterselectall)
    doForallProversCategories(s"$outputPath/PerProverPerCategory/$stimeout/AvgSuccTime", "avgsuccesstime_per_simplificationconfiguration.csv", layoutAvgSuccessTimeIndividualOpt(SimplConfEnum)(k => k.simplConf), filterselectall)


    //TODO: is it sensible to compare encoding strategies *including* the axiom selection domain?

    //Detailed overview (based on raw data): performance of individual conf combinations in each category
    //doForallProversCategories(s"$outputPath/DetailedOverviewPerCat/$stimeout", "time_per_file.csv", layoutRawDetailedTime, rawMap)

    //Detailed layout (based on overview data): success rates per individual conf combination in each category
    doForallProversCategories(s"$outputPath/IndividualConfSuccessRatesPerCat/$stimeout", "individual_conf_succ_rate.csv", layoutIndividualSuccessRates(false), filterselectall)

    //Success rates for individual combinations
    //doSingle(s"$outputPath/PerCompStrat/$stimeout", "stratperformance_allprovers_allcategories.csv", layoutSuccessRateOfCompStrat) //all provers and all categories together
    doForallProvers(s"$outputPath/PerCompStrat/$stimeout", "stratperformance.csv", layoutSuccessRateOfCompStrat(false), filterselectall)
    doForallCategories(s"$outputPath/PerCompStrat/$stimeout", "stratperformance.csv", layoutSuccessRateOfCompStrat(false), filterselectall)
    doForallProversCategories(s"$outputPath/PerCompStrat/$stimeout", "stratperformance.csv", layoutSuccessRateOfCompStrat(false), filterselectall)


    //val allpbutprincess = List(ProverConfEnum.Vampire_4, ProverConfEnum.Vampire_3, ProverConfEnum.Eprover)
    //val allcatbutexecution = List(GoalCategoryEnum.Synthesis, GoalCategoryEnum.Test, GoalCategoryEnum.Proof, GoalCategoryEnum.Counterexample)

    //layout for paper graph RQ1 (success rate per goal category, all provers)
    doForProvers(ProverConfEnum.iterator.toList,
      s"$outputPath/$stimeout/Graph1", "successrate_per_goalcategory.csv", layoutSuccessRateIndividualOpt(GoalCategoryEnum)(k => k.goalCategory), filterselectall)


    //layout for paper graph RQ2 (influence of sort encoding, success rate per sort encoding alternative, all provers except princess, all categories together except execution)
    doForallProvers(
      s"$outputPath/$stimeout/Graph2", "successrate_per_typingconfiguration.csv", layoutSuccessRateIndividualOpt(TypingConfEnum)(k => k.typingConf), filterselectall)

    //layout for paper graph RQ3 (influence of variable encoding, success rate per sort encoding alternative, all provers and all categories)
    doForallProvers(
      s"$outputPath/$stimeout/Graph3", "successrate_per_variableconfiguration.csv", layoutSuccessRateIndividualOpt(VariableConfEnum)(k => k.variableConf), filterselectall)

    //layout for paper graph RQ4 (influence of variable encoding, success rate per sort encoding alternative, all provers except princess, categories: proof + test)
    doForallProvers(
      s"$outputPath/$stimeout/Graph4", "successrate_per_simplificationconfiguration.csv", layoutSuccessRateIndividualOpt(SimplConfEnum)(k => k.simplConf), filterselectall)

    //layout for paper graph RQ5 (influence of simplifications, success rate per simplification alternative, all provers except princess, all categories)
    val filteroutgoodtyping = filterTypingConf(filterselectall, List(TypingConfEnum.Barefof, TypingConfEnum.Tff))
    val filteroutgoodinlining = filterVariableConf(filteroutgoodtyping, List(VariableConfEnum.Inlievery, VariableConfEnum.Unchanged))
    val filtered = filterProver(filteroutgoodinlining, List(ProverConfEnum.Vampire_3, ProverConfEnum.Vampire_4, ProverConfEnum.Eprover))
    doSingle(s"$outputPath/$stimeout/Graph5", "simplificationperformance_allprovers_allcategories.csv", layoutSuccessRateIndividualOpt(SimplConfEnum)(k => k.simplConf), filtered)

    //doForProvers(allpbutprincess, s"$outputPath/$stimeout/Graph5", "successrate_per_simplificationconfiguration.csv", layoutSuccessRateIndividualOpt(SimplConfEnum)(k => k.simplConf))

    //layout for paper graph RQ6 (performance of all comp strategies for all provers and categories together)
    doSingle(s"$outputPath/$stimeout/Graph6", "stratperformance_allprovers_allcategories.csv", layoutSuccessRateOfCompStrat(false), filterselectall)

    //second, use overviewmap everywhere to compare axiom selection strategies (ignoring the encoding dimensions)
    //Layouts for axiom selection study (new)
    //Overview graphs axiom selection strategies success rates & average success time summarizing all categories, for each prover
    // TODO: is possibly the most interesting graph of all
    // RQ: "Does axiom selection strategy improve success rate? Which one works best?"
    doForallProvers(s"$outputPath/AxiomSelection/PerProver/$stimeout/SuccRate", "successrate_per_axiomselectionconfiguration.csv", layoutSuccessRateIndividualOpt(SelectionConfEnum)(k => k.selectConf))
    doForallProvers(s"$outputPath/AxiomSelection/PerProver/$stimeout/AvgSuccTime", "avgsuccesstime_per_axiomselectionconfiguration.csv", layoutAvgSuccessTimeIndividualOpt(SelectionConfEnum)(k => k.selectConf))

    //Overview graphs axiom selection strategies per category for each prover, success rates & axiom selection strategies
    doForallProversCategories(s"$outputPath/AxiomSelection/PerProverPerCategory/$stimeout/SuccRate", "successrate_per_axiomselectionconfiguration.csv", layoutSuccessRateIndividualOpt(SelectionConfEnum)(k => k.selectConf))
    doForallProversCategories(s"$outputPath/AxiomSelection/PerProverPerCategory/$stimeout/AvgSuccTime", "avgsuccesstime_per_axiomselectionconfiguration.csv", layoutAvgSuccessTimeIndividualOpt(SelectionConfEnum)(k => k.selectConf))

    // TODO: compare axiomselection without bad encoding strategies
    // RQ: "Does axiom selection strategy improve success rate in combination with successful encoding strategies? Which one works best?"
    val filteroutgoodtyping_all = filterTypingConf(overviewMap, List(TypingConfEnum.Barefof, TypingConfEnum.Tff))
    val filteroutgoodinlining_all = filterVariableConf(filteroutgoodtyping_all, List(VariableConfEnum.Inlievery, VariableConfEnum.Unchanged))
    doForallProvers(s"$outputPath/AxiomSelection/PerProverGood/$stimeout/SuccRate", "successrate_per_axiomselectionconfiguration.csv", layoutSuccessRateIndividualOpt(SelectionConfEnum)(k => k.selectConf), filteroutgoodinlining_all)
    doForallProvers(s"$outputPath/AxiomSelection/PerProverGood/$stimeout/AvgSuccTime", "avgsuccesstime_per_axiomselectionconfiguration.csv", layoutAvgSuccessTimeIndividualOpt(SelectionConfEnum)(k => k.selectConf), filteroutgoodinlining_all)

    // same again, but separately for each goal category
    // RQ: "Does axiom selection strategy improve success rate in combination with successful encoding strategies? Which one works best?"
    doForallProversCategories(s"$outputPath/AxiomSelection/PerProverPerCategoryGood/$stimeout/SuccRate", "successrate_per_axiomselectionconfiguration.csv", layoutSuccessRateIndividualOpt(SelectionConfEnum)(k => k.selectConf), filteroutgoodinlining_all)
    doForallProversCategories(s"$outputPath/AxiomSelection/PerProverPerCategoryGood/$stimeout/AvgSuccTime", "avgsuccesstime_per_axiomselectionconfiguration.csv", layoutAvgSuccessTimeIndividualOpt(SelectionConfEnum)(k => k.selectConf), filteroutgoodinlining_all)


    // like previous, but do not distinguish between the different provers
    val filteredgoodprovers = filterProver(filteroutgoodinlining_all, List(ProverConfEnum.Vampire_3, ProverConfEnum.Vampire_4, ProverConfEnum.Eprover))
    doSingle(s"$outputPath/AxiomSelection/$stimeout/AllGoodProvers", "selectionperformance_allgoodprovers_allcategories.csv", layoutSuccessRateIndividualOpt(SelectionConfEnum)(k => k.selectConf), filteredgoodprovers)

    //compare *each* combination of strategies (including axiom selection strategies)
    doSingle(s"$outputPath/AxiomSelection/$stimeout/OverviewAll", "allstratperformance_allprovers_allcategories.csv", layoutSuccessRateOfCompStrat(true), overviewMap)

  }
}

case class MergedBaseDataLayout(files: Seq[File], stimeout: String) extends DataLayout(files, stimeout) {
  val workbooks = for (f <- files) yield load(f.getAbsolutePath)

  val overviews = Seq(workbooks(2), workbooks(3))
  val raws = Seq(workbooks(0), workbooks(1))
  val overviewMaps = overviews map extractOverviewMap _
  val rawMaps = raws map extractRawMap _


  def addOverviewResult(x: OverviewResult, y: OverviewResult): OverviewResult = {
    OverviewResult(x.succnum + y.succnum, x.filenum + y.filenum, (x.succrate + y.succrate) / 2, (x.avgSuccTime + y.avgSuccTime) / 2, (x.avgDev + y.avgDev) / 2)
  }

  def addMapValues(maps: Seq[ConfFullKey Map OverviewResult]): ConfFullKey Map OverviewResult = {
    val flattendMap = scala.collection.mutable.Map() ++ maps.head
    for {
      map <- maps.tail
      (k, v) <- map
    } {
      flattendMap(k) = addOverviewResult(flattendMap(k), v)
    }
    Map.empty ++ flattendMap
  }

  case class MergedConfFullKey(val caseStudy: String,
                               override val proverConf: ProverConfEnum.Value,
                               override val goalCategory: GoalCategoryEnum.Value,
                               override val typingConf: TypingConfEnum.Value,
                               override val variableConf: VariableConfEnum.Value,
                               override val simplConf: SimplConfEnum.Value,
                               override val selectConf: SelectionConfEnum.Value)
    extends FullKey(proverConf, goalCategory, typingConf, variableConf, simplConf, selectConf) {
    override def getCSVcells(b: StringBuilder, end: Boolean = false) = {
      makeCSVcell(b, caseStudy)
      super.getCSVcells(b, end)
    }
  }

  def mergeMaps(maps: Seq[ConfFullKey Map OverviewResult]): MergedConfFullKey Map OverviewResult = {
    val sqlMap = maps(0)
    val qlMap = maps(1)
    val mergedMap = scala.collection.mutable.Map[MergedConfFullKey, OverviewResult]()

    def addElements(caseStudy: String, map: ConfFullKey Map OverviewResult): Unit = {
      for ((k, v) <- map) {
        val newKey = MergedConfFullKey(caseStudy, k.proverConf, k.goalCategory, k.typingConf, k.variableConf, k.simplConf, k.selectConf)
        mergedMap(newKey) = v
      }
    }

    addElements("SQL", sqlMap)
    addElements("QL", qlMap)
    Map.empty ++ mergedMap
  }

  private def layoutSuccessRateIndividualOptMerged[K <: ConfigOption](confopt: K)(accessConfKey: MergedConfFullKey => confopt.Value)(filteredoverview: MergedConfFullKey Map OverviewResult): String = {
    val casestudyList = ListBuffer[String]()
    val intermediateMap: (confopt.Value Map List[String]) = (for (opt <- confopt.iterator) yield {
      val succRateList =
        (for ((k, v) <- filteredoverview
              if (accessConfKey(k) == opt)) yield {
          if (confopt.toSeq.head == opt) {
            casestudyList += k.caseStudy
          }
          if (v.isValid) v.succrate.toString else NAString
        }).toList

      (opt -> succRateList)
    }).toMap
    makeCSVColBasedMerged(casestudyList.toSeq, intermediateMap, confopt.isSmaller)
  }

  protected def makeCSVColBasedMerged[K, V](casestudyList: Seq[String], dataMap: (K Map Seq[V]), lt: (K, K) => Boolean): String = {
    val b = StringBuilder.newBuilder
    val orderedkeys = dataMap.keys.toList.sortWith(lt)

    def maxvlength: Int = {
      var max = 0
      for (v <- dataMap.values)
        if ((v.length) > max)
          max = v.length
      max
    }

    def makeCaseStudyRow(i: Int): Unit = {
      if (casestudyList.isDefinedAt(i)) {
        val csvtransformablev = new SingleCSVWrapper[String](casestudyList(i))
        csvtransformablev.getCSVcells(b, false)
      } else
        makeCSVcell(b, "", false)
    }

    def makeRow(i: Int): Unit = {
      for (k <- orderedkeys) {
        val last = (k == orderedkeys.last)
        if (dataMap(k).isDefinedAt(i)) {
          val csvtransformablev = new SingleCSVWrapper[V](dataMap(k)(i))
          csvtransformablev.getCSVcells(b, last)
        } else //make an empty cell
          makeCSVcell(b, "", last)
      }
    }

    //make key row first
    val csvtransformablek = new SingleCSVWrapper[String]("casestudy")
    csvtransformablek.getCSVcells(b, false)
    for (k <- orderedkeys) {
      val csvtransformablek = new SingleCSVWrapper[K](k)
      val last = (k == orderedkeys.last)
      csvtransformablek.getCSVcells(b, last)
    }

    //attach value rows
    for (i <- 0 until maxvlength) {
      makeCaseStudyRow(i)
      makeRow(i)
    }

    b.toString()
  }

  def layoutAll(outputPath: String): Unit = {
    val filterselectall = overviewMaps map {
      filterSelectionConf(_, List(SelectionConfEnum.Selectall))
    }
    //val addedfilterselectall = addMapValues(filterselectall) //this computes the average success rates of QL/SQL for the same compilation strategy (not what we want)

    val mergedfilterselectall = mergeMaps(filterselectall)

    // RQ1 merged from QL and SQL
    doForProvers(ProverConfEnum.iterator.toList,
      s"$outputPath/$stimeout/Graph1", "successrate_per_goalcategory.csv", layoutSuccessRateIndividualOptMerged(GoalCategoryEnum)(k => k.goalCategory), mergedfilterselectall)
    // RQ2 merged from QL and SQL but indicating to which case study values belong
    doForProvers(ProverConfEnum.iterator.toList,
      s"$outputPath/$stimeout/Graph2", "successrate_per_typingconfiguration.csv", layoutSuccessRateIndividualOptMerged(TypingConfEnum)(k => k.typingConf), mergedfilterselectall)
    // RQ3 merged from QL and SQL
    doForProvers(ProverConfEnum.iterator.toList,
      s"$outputPath/$stimeout/Graph3", "successrate_per_variableconfiguration.csv", layoutSuccessRateIndividualOptMerged(VariableConfEnum)(k => k.variableConf), mergedfilterselectall)
    // RQ4 merged from QL and SQL but indicating to which case study values belong
    doForProvers(ProverConfEnum.iterator.toList,
      s"$outputPath/$stimeout/Graph4", "successrate_per_simplificationconfiguration.csv", layoutSuccessRateIndividualOptMerged(SimplConfEnum)(k => k.simplConf), mergedfilterselectall)
    // RQ5 merged from QL and SQL
    val filteroutgoodtyping = filterTypingConf(mergedfilterselectall, List(TypingConfEnum.Barefof, TypingConfEnum.Tff))
    val filteroutgoodinlining = filterVariableConf(filteroutgoodtyping, List(VariableConfEnum.Inlievery, VariableConfEnum.Unchanged))
    val filtered = filterProver(filteroutgoodinlining, List(ProverConfEnum.Vampire_3, ProverConfEnum.Vampire_4, ProverConfEnum.Eprover))
    doSingle(s"$outputPath/$stimeout/Graph5", "simplificationperformance_allprovers_allcategories.csv", layoutSuccessRateIndividualOptMerged(SimplConfEnum)(k => k.simplConf), filtered)

    //layout for paper graph RQ6 (performance of all comp strategies for all provers and categories together)
    val filterselectallsql = filterselectall(0)
    val filterselectallql = filterselectall(1)
    doSingle(s"$outputPath/$stimeout/Graph6", "sql_stratperformance_allprovers_allcategories.csv", layoutSuccessRateOfCompStrat(false), filterselectallsql)
    doSingle(s"$outputPath/$stimeout/Graph6", "ql_stratperformance_allprovers_allcategories.csv", layoutSuccessRateOfCompStrat(false), filterselectallql)

    // RQ: "Does axiom selection strategy improve success rate? Which one works best?"
    //val addedMaps = addMapValues(overviewMaps)
    val mergedMaps = mergeMaps(overviewMaps)
    doForProvers(ProverConfEnum.iterator.toList,
      s"$outputPath/AxiomSelection/PerProver/$stimeout/SuccRate", "successrate_per_axiomselectionconfiguration.csv", layoutSuccessRateIndividualOptMerged(SelectionConfEnum)(k => k.selectConf), mergedMaps)
  }
}