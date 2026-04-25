package parsing

import models.Flight
import utils.ListUtils
import scala.annotation.tailrec
import scala.io.Source

/**
 * Tail-recursive CSV parser for the US Airline Dataset.
 * External library (scala.io.Source) used only for file I/O — allowed per spec.
 * All data manipulation is custom.
 */
object CsvParser {

  def parse(path: String): List[Flight] = {
    val source   = Source.fromFile(path)
    val allLines = source.getLines().toList
    source.close()
    val dataLines = allLines.tail  // skip header
    val total     = ListUtils.myLength(dataLines)
    println(s"[CSV] Total de líneas a procesar: $total")
    val reversed = parseLines(dataLines, 0, Nil, total)
    println(s"\r[CSV] Carga completada.                          ")
    ListUtils.myReverse(reversed)
  }

  @tailrec
  private def parseLines(
    lines: List[String],
    count: Int,
    acc:   List[Flight],
    total: Int
  ): List[Flight] = lines match {
    case Nil => acc
    case h :: tail =>
      if (count % 100000 == 0 && count > 0)
        print(s"\r[CSV] Procesadas $count / $total filas...")
      val newAcc = parseLine(h) match {
        case Some(f) => f :: acc
        case None    => acc
      }
      parseLines(tail, count + 1, newAcc, total)
  }

  private def parseLine(line: String): Option[Flight] = {
    val cols = line.split(",", -1)
    if (cols.length < 14) None
    else {
      val id            = parseIntSafe(cols(0))
      val tailNum       = cols(3).trim
      val originSeqId   = parseDoubleSafe(cols(5))
      val originAirport = cols(6).trim
      val destSeqId     = parseDoubleSafe(cols(7))
      val destAirport   = cols(8).trim
      val depDelay      = parseFloat(cols(10))
      val arrDelay      = parseFloat(cols(12))
      val weatherDelay  = parseFloat(cols(13))
      Some(Flight(id, tailNum, originSeqId, originAirport, destSeqId, destAirport,
                  depDelay, arrDelay, weatherDelay))
    }
  }

  private def parseFloat(s: String): Float = {
    val t = s.trim
    if (t.isEmpty || t == "NA" || t == "nan" || t == "NaN") Float.NaN
    else try t.toFloat catch { case _: NumberFormatException => Float.NaN }
  }

  private def parseDoubleSafe(s: String): Double = {
    val t = s.trim
    if (t.isEmpty || t == "NA") Double.NaN
    else try t.toDouble catch { case _: NumberFormatException => Double.NaN }
  }

  private def parseIntSafe(s: String): Int = {
    val t = s.trim
    try t.toInt
    catch {
      case _: NumberFormatException =>
        try t.toDouble.toInt catch { case _: NumberFormatException => -1 }
    }
  }
}
