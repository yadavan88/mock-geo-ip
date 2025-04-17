import com.ibm.icu.util.ULocale
import com.ibm.icu.util.TimeZone

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
    val ipMap = IpMappingRepo.readMappings()
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
