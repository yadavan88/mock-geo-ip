import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Server
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.ExitCode
import org.http4s.ember.server.EmberServerBuilder
import com.ibm.icu.util.ULocale
import com.ibm.icu.util.TimeZone
import com.ibm.icu.util.Region
import org.http4s.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import scala.io.Source
import java.nio.file.{Paths, Files}
import scala.util.Using
import java.io.PrintWriter
import cats.syntax.all._

case class IpMapping(pattern: String, countryCode: String)

object IpMapping {
  given decoder: io.circe.Decoder[IpMapping] = deriveDecoder[IpMapping]
  given encoder: io.circe.Encoder[IpMapping] = deriveEncoder[IpMapping]
}

class MockGeoIpRestApi {
  def apply(): HttpRoutes[IO] = {
    HttpRoutes.of[IO] {
      case GET -> Root / "mock-geo-ip" / "csv" / ip =>
        try {
          println(s"IP: $ip")
          val decodedIp = java.net.URLDecoder.decode(ip, "UTF-8")
          println(s"Decoded IP: $decodedIp")
          val geoIpInfo = MockGeoIpInfoGenerator.generate(decodedIp)
          println(s"Generated GeoIpInfo: $geoIpInfo")
          Ok(geoIpInfo.asJson)
        } catch {
          case ex: Exception =>
            println(s"Error processing request: ${ex.getMessage}")
            ex.printStackTrace()
            InternalServerError(ex.getMessage)
        }

      case GET -> Root / "mock-geo-ip" / "mappings" =>
        val mappings = IpMappingRepo.readMappings()
        Ok(
          mappings
            .map { case (pattern, countryCode) =>
              IpMapping(pattern, countryCode)
            }
            .toList
            .asJson
        )

      case req @ POST -> Root / "mock-geo-ip" / "mappings" =>
        for {
          mapping <- req.as[IpMapping]
          existingMappings = IpMappingRepo.readMappings()
          result <- IpMappingRepo.validateMapping(
            mapping,
            existingMappings
          ) match {
            case Right(_) =>
              val updatedMappings =
                existingMappings + (mapping.pattern -> mapping.countryCode)
              IpMappingRepo
                .saveMappings(updatedMappings.toList.map { case (p, c) =>
                  IpMapping(p, c)
                })
                .flatMap(_ => Ok("Mapping added successfully"))
            case Left(error) =>
              BadRequest(error)
          }
        } yield result
    }
  }
}

case class GeoIpInfo(
    ip: Option[String],
    countryName: Option[String],
    countryCode: Option[String],
    timezone: Option[String]
)

object GeoIpInfo {
  def empty(ip: String): GeoIpInfo = {
    GeoIpInfo(Some(ip), None, None, None)
  }

  implicit val encoder: io.circe.Encoder[GeoIpInfo] =
    io.circe.generic.semiauto.deriveEncoder[GeoIpInfo]
}