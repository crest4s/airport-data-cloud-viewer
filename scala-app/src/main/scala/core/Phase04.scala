package core

import models.Flight
import utils.ListUtils
import scala.annotation.tailrec

/**
 * Phase 04 – Airport histogram.
 * Counts occurrences of each airport code (origin or dest) using a custom
 * List[(String, Int)] association list, then prints a console histogram
 * for all airports with count >= threshold.
 */
object Phase04 {

  def run(flights: List[Flight], airportType: String, threshold: Int): List[(String, Int)] = {
    val selector: Flight => String = airportType match {
      case "origin" => _.originAirport
      case "dest"   => _.destAirport
      case _        => _ => ""
    }

    val counts      = buildCounts(flights, selector, Nil)
    val totalUnique = ListUtils.myLength(counts)

    println(s"\n--- Fase 04: Histograma de aeropuertos ($airportType, umbral: $threshold) ---")
    println(s"Total aeropuertos únicos: $totalUnique")

    val filtered = ListUtils.myFilter(counts, (pair: (String, Int)) => pair._2 >= threshold)
    val sorted   = sortByCount(filtered)

    val maxCount = sorted match {
      case Nil         => 1
      case (_, v) :: _ => v
    }

    printHistogram(sorted, maxCount)
    sorted
  }

  @tailrec
  private def buildCounts(
    flights:  List[Flight],
    selector: Flight => String,
    acc:      List[(String, Int)]
  ): List[(String, Int)] = flights match {
    case Nil    => acc
    case h :: tail =>
      val key = selector(h)
      if (key.isEmpty) buildCounts(tail, selector, acc)
      else             buildCounts(tail, selector, ListUtils.incrementCount(acc, key))
  }

  private def sortByCount(counts: List[(String, Int)]): List[(String, Int)] = {
    // Insertion sort by descending count (O(n²) on unique airports — acceptable)
    def insertSorted(sorted: List[(String, Int)], elem: (String, Int)): List[(String, Int)] = {
      @tailrec
      def go(remaining: List[(String, Int)], acc: List[(String, Int)]): List[(String, Int)] =
        remaining match {
          case Nil => ListUtils.myReverse(elem :: acc)
          case h :: tail =>
            if (elem._2 >= h._2) ListUtils.myConcat(ListUtils.myReverse(acc), elem :: h :: tail)
            else go(tail, h :: acc)
        }
      go(sorted, Nil)
    }

    @tailrec
    def sort(remaining: List[(String, Int)], sorted: List[(String, Int)]): List[(String, Int)] =
      remaining match {
        case Nil       => sorted
        case h :: tail => sort(tail, insertSorted(sorted, h))
      }

    sort(counts, Nil)
  }

  private def renderBar(count: Int, max: Int): String = {
    val maxWidth = 40
    val width    = if (max == 0) 0 else (count.toLong * maxWidth / max).toInt
    "█" * width
  }

  @tailrec
  private def printHistogram(counts: List[(String, Int)], max: Int): Unit = counts match {
    case Nil => ()
    case (airport, count) :: tail =>
      val bar = renderBar(count, max)
      println(f"$airport%-6s $bar $count")
      printHistogram(tail, max)
  }
}
