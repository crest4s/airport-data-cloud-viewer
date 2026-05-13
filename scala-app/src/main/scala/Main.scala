import core.{Phase01, Phase02, Phase03, Phase04}
import parsing.CsvParser
import models.Flight
import utils.ListUtils
import scala.annotation.tailrec
import java.net.{HttpURLConnection, URL}
import java.io.DataOutputStream
import java.time.Instant

object Main extends App {

  println("╔══════════════════════════════════════╗")
  println("║      Airport Data – PL2 (Scala)      ║")
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
        val thr1     = readInt(0)
        val hits1    = Phase01.run(flights, thr1)
        val op1      = if (thr1 >= 0) ">=" else "<="
        val summary1 = s"${ListUtils.myLength(hits1)} vuelos con DEP_DELAY $op1 $thr1 min"
        promptCloud("Phase01", s"threshold=$thr1", summary1, phase01Json(myTake(hits1, 20, Nil), Nil))
        menuLoop(flights)

      case "2" =>
        print("Umbral de retraso en aterrizaje (minutos): ")
        val thr2          = readInt(0)
        val (hits2, cnt2) = Phase02.run(flights, thr2)
        val op2           = if (thr2 >= 0) ">=" else "<="
        val summary2      = s"$cnt2 vuelos con ARR_DELAY $op2 $thr2 min"
        promptCloud("Phase02", s"threshold=$thr2", summary2, phase02Json(myTake(hits2, 20, Nil), Nil))
        menuLoop(flights)

      case "3" =>
        val col3     = readColumn()
        val op3      = readOperation()
        val result3  = Phase03.run(flights, col3, op3)
        val summary3 = result3.map(r => s"$op3 $col3 = ${r._2} min (vuelo id=${r._1})").getOrElse("Sin datos válidos")
        val details3 = result3.map(r => s"""{"id":${r._1},"column":"${ej(col3)}","operation":"${ej(op3)}","value":${r._2}}""").getOrElse("null")
        promptCloud("Phase03", s"column=$col3,operation=$op3", summary3, details3)
        menuLoop(flights)

      case "4" =>
        val aType    = readAirportType()
        print("Umbral mínimo de ocurrencias: ")
        val thr4     = readInt(0)
        val counts4  = Phase04.run(flights, aType, thr4)
        val summary4 = s"${ListUtils.myLength(counts4)} aeropuertos con count >= $thr4 ($aType)"
        promptCloud("Phase04", s"airportType=$aType,threshold=$thr4", summary4, phase04Json(myTake(counts4, 20, Nil), Nil))
        menuLoop(flights)

      case "5" =>
        println("Saliendo. ¡Hasta pronto!")

      case _ =>
        println("Opción no válida. Inténtalo de nuevo.")
        menuLoop(flights)
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

  // ── Cloud upload ──────────────────────────────────────────────────────────
  private val cloudApiUrl: String =
    sys.env.getOrElse("CLOUD_API_URL", "")

  private def promptCloud(
    phase:       String,
    params:      String,
    result:      String,
    fullDetails: String
  ): Unit = {
    print("\n¿Enviar resultados a la nube? (s/n): ")
    val ans = scala.io.StdIn.readLine().trim.toLowerCase
    if (ans == "s" || ans == "y") {
      print("Nombre de usuario: ")
      val username = scala.io.StdIn.readLine().trim
      postToCloud(phase, params, result, fullDetails, Instant.now().toString, username)
    }
  }

  private def postToCloud(
    phase:       String,
    params:      String,
    result:      String,
    fullDetails: String,
    timestamp:   String,
    username:    String
  ): Unit =
    if (cloudApiUrl.isEmpty) {
      println("CLOUD_API_URL no está configurado. No se ha enviado nada.")
    } else {
      try {
        val body = s"""{"phase":"${ej(phase)}","parameters":"${ej(params)}","result":"${ej(result)}","full_details":$fullDetails,"timestamp":"${ej(timestamp)}","username":"${ej(username)}"}"""
        val conn = new URL(cloudApiUrl).openConnection().asInstanceOf[HttpURLConnection]
        conn.setRequestMethod("POST")
        conn.setDoOutput(true)
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        val out = new DataOutputStream(conn.getOutputStream)
        out.write(body.getBytes("UTF-8"))
        out.flush()
        out.close()
        println(s"Enviado correctamente. HTTP ${conn.getResponseCode}")
      } catch {
        case e: Exception => println(s"Error al enviar a la nube: ${e.getMessage}")
      }
    }

  private def ej(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")
     .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

  // ── JSON serialisers (tail-recursive, no prohibited ops) ─────────────────
  @tailrec
  private def myTake[A](list: List[A], n: Int, acc: List[A]): List[A] =
    if (n <= 0) ListUtils.myReverse(acc)
    else list match {
      case Nil    => ListUtils.myReverse(acc)
      case h :: t => myTake(t, n - 1, h :: acc)
    }

  @tailrec
  private def joinParts(parts: List[String], acc: String, first: Boolean): String =
    parts match {
      case Nil    => acc
      case h :: t => joinParts(t, if (first) h else s"$acc,$h", false)
    }

  @tailrec
  private def phase01Json(flights: List[Flight], acc: List[String]): String =
    flights match {
      case Nil    => "[" + joinParts(ListUtils.myReverse(acc), "", true) + "]"
      case h :: t => phase01Json(t, s"""{"id":${h.id},"dep_delay":${h.depDelay.toInt}}""" :: acc)
    }

  @tailrec
  private def phase02Json(flights: List[Flight], acc: List[String]): String =
    flights match {
      case Nil    => "[" + joinParts(ListUtils.myReverse(acc), "", true) + "]"
      case h :: t => phase02Json(t, s"""{"id":${h.id},"tail_num":"${ej(h.tailNum)}","arr_delay":${h.arrDelay.toInt}}""" :: acc)
    }

  @tailrec
  private def phase04Json(counts: List[(String, Int)], acc: List[String]): String =
    counts match {
      case Nil          => "[" + joinParts(ListUtils.myReverse(acc), "", true) + "]"
      case (ap, n) :: t => phase04Json(t, s"""{"airport":"${ej(ap)}","count":$n}""" :: acc)
    }
}
