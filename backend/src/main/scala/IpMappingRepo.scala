import java.nio.file.Paths
import java.nio.file.Files
import scala.io.Source
import scala.util.Using
import java.io.PrintWriter
import cats.effect.IO

object IpMappingRepo {
  // Use environment variable or fallback to local directory
  private val dataDir = sys.env.get("DATA_DIR")
    .map(Paths.get(_))
    .getOrElse(Paths.get("data"))
  private val csvFile = dataDir.resolve("ip_mappings.csv")

  def readMappings(): Map[String, String] = {
    if (!Files.exists(dataDir)) {
      Files.createDirectories(dataDir)
    }

    if (!Files.exists(csvFile)) {
      println(s"Warning: ${csvFile} not found, using empty map")
      Map.empty
    } else {
      Using(Source.fromFile(csvFile.toFile)) { source =>
        source
          .getLines()
          .drop(1) // Skip header
          .map(_.split(","))
          .collect { case Array(pattern, countryCode) =>
            pattern -> countryCode
          }
          .toMap
      }.getOrElse {
        println(s"Error reading ${csvFile}, using empty map")
        Map.empty
      }
    }
  }

  def saveMappings(mappings: List[IpMapping]): IO[Unit] = {
    IO {
      if (!Files.exists(dataDir)) {
        Files.createDirectories(dataDir)
      }
      
      Using(new PrintWriter(csvFile.toFile)) { writer =>
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