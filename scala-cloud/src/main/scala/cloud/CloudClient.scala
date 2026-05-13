package cloud

import java.io.DataOutputStream
import java.net.{HttpURLConnection, URL}

object CloudClient {

  private val API_URL: String =
    "https://<your-function-url>.lambda-url.<region>.on.aws/"

  def send(
    phase:       String,
    parameters:  String,
    result:      String,
    fullDetails: String,
    timestamp:   String,
    username:    String
  ): Boolean = {
    val payload =
      s"""{"phase":"${escape(phase)}","parameters":"${escape(parameters)}",""" +
      s""""result":"${escape(result)}","full_details":$fullDetails,""" +
      s""""timestamp":"${escape(timestamp)}","username":"${escape(username)}"}"""

    try {
      val url  = new URL(API_URL)
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("POST")
      conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
      conn.setDoOutput(true)
      conn.setConnectTimeout(8000)
      conn.setReadTimeout(8000)

      val out = new DataOutputStream(conn.getOutputStream)
      out.write(payload.getBytes("UTF-8"))
      out.flush()
      out.close()

      val code = conn.getResponseCode
      conn.disconnect()
      code >= 200 && code < 300
    } catch {
      case e: Exception =>
        println(s"[Cloud] Error al conectar: ${e.getMessage}")
        false
    }
  }

  private def escape(s: String): String =
    s.replace("\\", "\\\\")
     .replace("\"", "\\\"")
     .replace("\n", "\\n")
     .replace("\r", "\\r")
     .replace("\t", "\\t")
}
