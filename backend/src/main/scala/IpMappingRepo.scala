import java.nio.file.Paths
import java.nio.file.Files
import scala.io.Source
import scala.util.Using
import java.io.PrintWriter
import cats.effect.IO
import scala.collection.mutable.Queue
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import io.circe.generic.auto.*

object IpMappingRepo {
  // Use environment variable or fallback to local directory
  private val dataDir = sys.env.get("DATA_DIR")
    .map(Paths.get(_))
    .getOrElse(Paths.get("data"))
  private val csvFile = dataDir.resolve("ip_mappings.csv")

  // Request history tracking
  private val maxHistorySize = 20
  private val requestHistory: Queue[RequestHistoryEntry] = Queue.empty

  def addRequestHistory(ip: String, status: String, countryCode: Option[String]): Unit = {
    val entry = RequestHistoryEntry(ip, System.currentTimeMillis(), status, countryCode)
    requestHistory.enqueue(entry)
    if (requestHistory.size > maxHistorySize) {
      requestHistory.dequeue()
    }
  }

  def getRequestHistory: List[RequestHistoryEntry] = {
    requestHistory.toList.reverse
  }

  def getIpRequestHistory(): IO[List[RequestHistoryEntry]] = {
    IO.pure(getRequestHistory)
  }

  def readMappings(): Map[String, String] = {
    if (!Files.exists(dataDir)) {
      Files.createDirectories(dataDir)
    }

    if (!Files.exists(csvFile)) {
      println("Warning: ip_mappings.csv not found, using empty map")
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
        println("Error reading ip_mappings.csv, using empty map")
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
      // Check for exact matches only
      val exactMatch = existingMappings.find { case (pattern, _) =>
        pattern == newMapping.pattern
      }

      exactMatch match {
        case Some((pattern, _)) =>
          Left(s"Exact pattern already exists: $pattern")
        case None => Right(())
      }
    }
  }

  def deleteMapping(key: String): IO[Unit] = {
    val existingMappings = readMappings()
    val updatedMappings = existingMappings.filterNot(_._1 == key)
    println("no of mappings: " + updatedMappings.size)
    saveMappings(updatedMappings.toList.map { case (p, c) =>
      IpMapping(p, c)
    })
  }
}