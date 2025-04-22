import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import io.circe.Codec

case class IpMapping(pattern: String, countryCode: String)
case class GeoIpInfo(ip: Option[String], countryName: Option[String], countryCode: Option[String], timezone: Option[String])

object IpMapping {
  implicit val decoder: Decoder[IpMapping] = deriveDecoder[IpMapping]
  implicit val encoder: Encoder[IpMapping] = deriveEncoder[IpMapping]
}

object GeoIpInfo {
  implicit val decoder: Decoder[GeoIpInfo] = deriveDecoder[GeoIpInfo]
  implicit val encoder: Encoder[GeoIpInfo] = deriveEncoder[GeoIpInfo]

  def empty(ip: String): GeoIpInfo = {
    GeoIpInfo(Some(ip), None, None, None)
  }
}

case class RequestHistoryEntry(
  ip: String,
  timestamp: Long,
  status: String,
  countryCode: Option[String]
) derives Codec