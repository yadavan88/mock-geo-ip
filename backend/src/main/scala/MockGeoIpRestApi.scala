import cats.effect.IO
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.{
  circeEntityDecoder,
  circeEntityEncoder
}
import org.http4s.dsl.io.*

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