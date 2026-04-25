package core

import models.Flight
import utils.ListUtils
import scala.annotation.tailrec

/**
 * Phase 02 – Arrival delay (ARR_DELAY).
 * threshold >= 0  →  flights with ARR_DELAY >= threshold
 * threshold <  0  →  flights with ARR_DELAY <= threshold  (early arrivals)
 * Returns (matching flights, count).
 */
object Phase02 {

  def run(flights: List[Flight], threshold: Int): (List[Flight], Int) = {
    val matches =
      if (threshold >= 0)
        ListUtils.myFilter(flights, (f: Flight) => !f.arrDelay.isNaN && f.arrDelay >= threshold)
      else
        ListUtils.myFilter(flights, (f: Flight) => !f.arrDelay.isNaN && f.arrDelay <= threshold)

    val count = ListUtils.myLength(matches)
    println(s"\n--- Fase 02: Retraso en aterrizajes (umbral: $threshold min) ---")
    printResults(matches)
    println(s"Total vuelos detectados: $count")
    (matches, count)
  }

  @tailrec
  private def printResults(matches: List[Flight]): Unit = matches match {
    case Nil => ()
    case h :: tail =>
      println(s"[id=${h.id}] TAIL_NUM=${h.tailNum} ARR_DELAY=${h.arrDelay.toInt} min")
      printResults(tail)
  }
}
