import java.nio.file.Paths
import java.nio.file.Files
import scala.io.Source
import scala.util.Using
import java.io.PrintWriter
import cats.effect.IO

object IpMappingRepo {
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