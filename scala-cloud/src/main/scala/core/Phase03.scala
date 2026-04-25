package core

import models.Flight
import scala.annotation.tailrec

/**
 * Phase 03 – Delay reduction, variant 3.1 Simple.
 * Scans the list once with tail recursion, accumulating min or max.
 * Decimal values are truncated to integers (toInt truncates toward zero).
 */
object Phase03 {

  def run(flights: List[Flight], column: String, operation: String): Option[(Int, Int)] = {
    val selector: Flight => Float = column match {
      case "DEP_DELAY"     => _.depDelay
      case "ARR_DELAY"     => _.arrDelay
      case "WEATHER_DELAY" => _.weatherDelay
      case _               => _ => Float.NaN
    }
    val isBetter: (Int, Int) => Boolean = operation match {
      case "min" => (a, b) => a < b
      case "max" => (a, b) => a > b
      case _     => (_, _) => false
    }

    val result = reduce(flights, selector, isBetter, None)

    println(s"\n--- Fase 03: $operation de $column ---")
    result match {
      case Some((id, value)) => println(s"Resultado: $value min (vuelo id=$id)")
      case None              => println("No se encontraron datos válidos.")
    }
    result
  }

  @tailrec
  private def reduce(
    flights:  List[Flight],
    selector: Flight => Float,
    isBetter: (Int, Int) => Boolean,
    acc:      Option[(Int, Int)]
  ): Option[(Int, Int)] = flights match {
    case Nil => acc
    case h :: tail =>
      val value = selector(h)
      val newAcc =
        if (value.isNaN) acc
        else {
          val truncated = value.toInt
          acc match {
            case None             => Some((h.id, truncated))
            case Some((_, bestV)) => if (isBetter(truncated, bestV)) Some((h.id, truncated)) else acc
          }
        }
      reduce(tail, selector, isBetter, newAcc)
  }
}
