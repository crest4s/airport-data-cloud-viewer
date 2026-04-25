package cloud

import java.io.{DataOutputStream, InputStreamReader, BufferedReader}
import java.net.{HttpURLConnection, URL}

/**
 * Minimal HTTP client that POSTs phase results to the AWS API Gateway endpoint.
 * Uses only java.net – no external libraries.
 *
 * Set CLOUD_API_URL to the ApiUrl output from `sam deploy`.
 * Example: https://<api-id>.execute-api.<region>.amazonaws.com/Prod/results
 */
object CloudClient {

  private val API_URL: String =
    sys.env.getOrElse("CLOUD_API_URL", "https://REPLACE_WITH_API_GATEWAY_URL/results")

  /**
   * Sends a result payload to the cloud.
   *
   * @param phase      e.g. "Phase01"
   * @param parameters human-readable description of the input parameters
   * @param result     serialised result (use ResultSerializer)
   * @param timestamp  ISO-8601 timestamp
   * @param username   alphanumeric username entered by the user
   * @return true if the server returned 2xx, false otherwise
   */
  def send(
    phase:      String,
    parameters: String,
    result:     String,
    timestamp:  String,
    username:   String
  ): Boolean = {
    val payload =
      s"""{"phase":"${escape(phase)}","parameters":"${escape(parameters)}",""" +
      s""""result":"${escape(result)}","timestamp":"${escape(timestamp)}",""" +
      s""""username":"${escape(username)}"}"""

    try {
      val url  = new URL(API_URL)
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("POST")
      conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
      conn.setDoOutput(true)
      conn.setConnectTimeout(8000)
      conn.setReadTimeout(8000)

      val out = new DataOutputStream(conn.getOutputStream)
      out.writeBytes(payload)
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

  /** Escapes a string for safe inclusion in a JSON string value. */
  private def escape(s: String): String =
    s.replace("\\", "\\\\")
     .replace("\"", "\\\"")
     .replace("\n", "\\n")
     .replace("\r", "\\r")
     .replace("\t", "\\t")
}
