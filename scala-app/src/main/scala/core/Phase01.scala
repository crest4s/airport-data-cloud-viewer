package core

import models.Flight
import utils.ListUtils
import scala.annotation.tailrec

/**
 * Phase 01 – Departure delay (DEP_DELAY).
 * threshold >= 0  →  flights with DEP_DELAY >= threshold
 * threshold <  0  →  flights with DEP_DELAY <= threshold  (early arrivals)
 */
object Phase01 {

  def run(flights: List[Flight], threshold: Int): List[Flight] = {
    val matches =
      if (threshold >= 0)
        ListUtils.myFilter(flights, (f: Flight) => !f.depDelay.isNaN && f.depDelay >= threshold)
      else
        ListUtils.myFilter(flights, (f: Flight) => !f.depDelay.isNaN && f.depDelay <= threshold)

    println(s"\n--- Fase 01: Retraso en despegues (umbral: $threshold min) ---")
    printResults(matches)
    println(s"Total encontrados: ${ListUtils.myLength(matches)}")
    matches
  }

  @tailrec
  private def printResults(matches: List[Flight]): Unit = matches match {
    case Nil => ()
    case h :: tail =>
      println(s"[id=${h.id}] DEP_DELAY = ${h.depDelay.toInt} min")
      printResults(tail)
  }
}
