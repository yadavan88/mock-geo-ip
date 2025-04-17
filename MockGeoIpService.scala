//> using scala 3.6.4
//> using dep org.http4s::http4s-ember-client:0.23.12
//> using dep org.http4s::http4s-ember-server:0.23.12
//> using dep org.http4s::http4s-dsl:0.23.12
//> using dep com.ibm.icu:icu4j:77.1
//> using dep org.http4s::http4s-circe:0.23.12
//> using dep io.circe::circe-generic:0.14.8

import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Server
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.ExitCode
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s._
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

object IpMappingReader {
  def readMappings(): Map[String, String] = {
    val path = Paths.get("ip_mappings.csv")
    if (!Files.exists(path)) {
      println("Warning: ip_mappings.csv not found, using empty map")
      Map.empty
    } else {
      Using(Source.fromFile("ip_mappings.csv")) { source =>
        source
          .getLines()
          .drop(1) // Skip header
          .map(_.split(","))
          .collect { case Array(pattern, countryCode) =>
            pattern -> countryCode
          }
          .toMap
      }.getOrElse {
        println("Error reading ip_mappings.csv, using empty map")
        Map.empty
      }
    }
  }

  def saveMappings(mappings: List[IpMapping]): IO[Unit] = {
    IO {
      Using(new PrintWriter("ip_mappings.csv")) { writer =>
        writer.println("pattern,country_code")
        mappings.foreach { mapping =>
          writer.println(s"${mapping.pattern},${mapping.countryCode}")
        }
      }
    }
  }

  def validateMapping(
      newMapping: IpMapping,
      existingMappings: Map[String, String]
  ): Either[String, Unit] = {
    // Check if the pattern is valid
    if (!newMapping.pattern.matches("^[0-9.*]+$")) {
      Left("Invalid IP pattern format")
    } else {
      // Check for conflicts with existing patterns
      val conflicts = existingMappings.find { case (pattern, _) =>
        val newPattern = newMapping.pattern.replace("*", ".*")
        val existingPattern = pattern.replace("*", ".*")
        newPattern.r.matches(pattern) || existingPattern.r.matches(
          newMapping.pattern
        )
      }

      conflicts match {
        case Some((pattern, _)) =>
          Left(s"Pattern conflicts with existing pattern: $pattern")
        case None => Right(())
      }
    }
  }
}

class MockGeoIpService {
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
        val mappings = IpMappingReader.readMappings()
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
          existingMappings = IpMappingReader.readMappings()
          result <- IpMappingReader.validateMapping(
            mapping,
            existingMappings
          ) match {
            case Right(_) =>
              val updatedMappings =
                existingMappings + (mapping.pattern -> mapping.countryCode)
              IpMappingReader
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


object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val mockGeoIpService = MockGeoIpService()
    val server = EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(mockGeoIpService.apply().orNotFound)
      .build

    server.useForever.as(ExitCode.Success)
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

object MockGeoIpInfoGenerator {
  def getCountryInfo(
      input: String
  ): Option[(String, List[String], String)] = {
    println("getting country info")
    val maybeCountryCode: Option[String] = {
      val loc = ULocale.forLanguageTag(input)
      Option(loc.getCountry)
        .filter(_.nonEmpty)
        .orElse {
          if (TimeZone.getAvailableIDs.contains(input)) {
            val tz = TimeZone.getTimeZone(input)
            Option(TimeZone.getRegion(tz.getID)).filter(_.nonEmpty)
          } else None
        }
        .orElse {
          Some(input.toUpperCase)
        }
    }

    maybeCountryCode.flatMap { countryCode =>
      try {
        val timezones = TimeZone.getAvailableIDs(countryCode).toList
        // Create locale with country code and get display name
        val countryName =
          new ULocale("", countryCode).getDisplayCountry(ULocale.ENGLISH)
        Some((countryCode, timezones, countryName))
      } catch {
        case ex: Exception =>
          println(s"Error getting country info: ${ex.getMessage}")
          None
      }
    }
  }

  def generate(ip: String): GeoIpInfo = {
    val ipMap = IpMappingReader.readMappings()
    // First try exact match
    val countryCode = ipMap.get(ip) match {
      case Some(code) => Some(code) // Exact match found
      case None =>
        println("no exact match, looking for wildcard patterns")
        // If no exact match, look for wildcard patterns
        ipMap
          .find { case (pattern, _) =>
            pattern.contains("*") && {
              val regex = pattern.replace("*", ".*").r
              regex.matches(ip)
            }
          }
          .map(_._2)
    }

    countryCode
      .flatMap { code =>
        getCountryInfo(code).map { case (countryCode, timezones, countryName) =>
          GeoIpInfo(
            ip = Some(ip),
            countryName = Some(countryName),
            countryCode = Some(countryCode),
            timezone = timezones.headOption
          )
        }
      }
      .getOrElse(GeoIpInfo.empty(ip))
  }
}
