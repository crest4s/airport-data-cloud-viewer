package models

case class Flight(
  id:            Int,
  tailNum:       String,
  originSeqId:   Double,
  originAirport: String,
  destSeqId:     Double,
  destAirport:   String,
  depDelay:      Float,   // Float.NaN if missing
  arrDelay:      Float,   // Float.NaN if missing
  weatherDelay:  Float    // Float.NaN if missing
)
