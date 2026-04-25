import cloud.CloudClient
import core.{Phase01, Phase02, Phase03, Phase04}
import parsing.CsvParser
import models.Flight
import utils.ListUtils
import scala.annotation.tailrec
import java.time.Instant

object Main extends App {

  println("╔══════════════════════════════════════╗")
  println("║   Airport Data Cloud Viewer – PL2    ║")
  println("╚══════════════════════════════════════╝")

  // ── CSV loading ──────────────────────────────────────────────────────────
  val csvPath = {
    print("Ruta del CSV [data/Airline_dataset.csv]: ")
    val input = scala.io.StdIn.readLine().trim
    if (input.isEmpty) "data/Airline_dataset.csv" else input
  }

  println(s"Cargando dataset desde: $csvPath")
  val flights = CsvParser.parse(csvPath)
  println(s"Dataset cargado: ${ListUtils.myLength(flights)} vuelos.\n")

  menuLoop(flights)

  // ── Menu ─────────────────────────────────────────────────────────────────
  @tailrec
  def menuLoop(flights: List[Flight]): Unit = {
    println("\n╔══════════════════════════════════════╗")
    println("║               MENÚ                  ║")
    println("║  1. Fase 01 – Retraso en despegues  ║")
    println("║  2. Fase 02 – Retraso en aterrizajes║")
    println("║  3. Fase 03 – Reducción min/max     ║")
    println("║  4. Fase 04 – Histograma aeropuertos║")
    println("║  5. Salir                           ║")
    println("╚══════════════════════════════════════╝")
    print("Opción: ")

    val choice = scala.io.StdIn.readLine().trim

    choice match {
      case "1" =>
        print("Umbral de retraso en despegue (minutos): ")
        val threshold = readInt(0)
        val result    = Phase01.run(flights, threshold)
        val summary   = s"Encontrados ${ListUtils.myLength(result)} vuelos con DEP_DELAY umbral $threshold min"
        promptCloud("Phase01", s"threshold=$threshold", summary)
        menuLoop(flights)

      case "2" =>
        print("Umbral de retraso en aterrizaje (minutos): ")
        val threshold       = readInt(0)
        val (result, count) = Phase02.run(flights, threshold)
        val summary         = s"Encontrados $count vuelos con ARR_DELAY umbral $threshold min"
        promptCloud("Phase02", s"threshold=$threshold", summary)
        menuLoop(flights)

      case "3" =>
        val column    = readColumn()
        val operation = readOperation()
        val result    = Phase03.run(flights, column, operation)
        val summary   = result match {
          case Some((id, v)) => s"$operation($column)=$v en vuelo id=$id"
          case None          => "Sin datos válidos"
        }
        promptCloud("Phase03", s"column=$column operation=$operation", summary)
        menuLoop(flights)

      case "4" =>
        val airportType = readAirportType()
        print("Umbral mínimo de ocurrencias: ")
        val threshold = readInt(0)
        Phase04.run(flights, airportType, threshold)
        promptCloud("Phase04", s"airportType=$airportType threshold=$threshold", s"Histograma generado (umbral $threshold)")
        menuLoop(flights)

      case "5" =>
        println("Saliendo. ¡Hasta pronto!")

      case _ =>
        println("Opción no válida. Inténtalo de nuevo.")
        menuLoop(flights)
    }
  }

  // ── Cloud upload ─────────────────────────────────────────────────────────
  private def promptCloud(phase: String, params: String, result: String): Unit = {
    print("\n¿Enviar resultados al Cloud? (s/n): ")
    val answer = scala.io.StdIn.readLine().trim.toLowerCase
    if (answer == "s" || answer == "si" || answer == "sí" || answer == "y") {
      print("Nombre de usuario (alfanumérico): ")
      val username  = scala.io.StdIn.readLine().trim
      val timestamp = Instant.now().toString
      val ok        = CloudClient.send(phase, params, result, timestamp, username)
      if (ok) println("[Cloud] Datos enviados correctamente.")
      else    println("[Cloud] Error al enviar los datos.")
    }
  }

  // ── Input helpers ────────────────────────────────────────────────────────
  private def readInt(default: Int): Int = {
    val line = scala.io.StdIn.readLine().trim
    try line.toInt catch { case _: NumberFormatException => default }
  }

  private def readColumn(): String = {
    println("Columna:")
    println("  1. DEP_DELAY")
    println("  2. ARR_DELAY")
    println("  3. WEATHER_DELAY")
    print("Opción [1]: ")
    scala.io.StdIn.readLine().trim match {
      case "2" => "ARR_DELAY"
      case "3" => "WEATHER_DELAY"
      case _   => "DEP_DELAY"
    }
  }

  private def readOperation(): String = {
    println("Operación:")
    println("  1. min")
    println("  2. max")
    print("Opción [2]: ")
    scala.io.StdIn.readLine().trim match {
      case "1" => "min"
      case _   => "max"
    }
  }

  private def readAirportType(): String = {
    println("Tipo de aeropuerto:")
    println("  1. origin")
    println("  2. dest")
    print("Opción [1]: ")
    scala.io.StdIn.readLine().trim match {
      case "2" => "dest"
      case _   => "origin"
    }
  }
}
