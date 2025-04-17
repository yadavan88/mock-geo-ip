import org.scalajs.dom
import org.scalajs.dom.{document, html}
import scalatags.JsDom.all._
import scala.scalajs.js.Thenable.Implicits._
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

object Main {
  def main(args: Array[String]): Unit = {
    val app = document.getElementById("app")
    app.appendChild(createUI())
  }

  def createUI(): html.Div = {
    div(
      h1("Mock GeoIP Service"),

      // Lookup IP section
      div(cls := "container")(
        h2("Lookup IP"),
        div(cls := "input-group")(
          input(id := "ipInput", placeholder := "Enter IP address"),
          button("Lookup", onclick := lookupIp)
        ),
        div(id := "ipResult")
      ),

      // View mappings section
      div(cls := "container")(
        h2("IP Mappings"),
        button("Refresh Mappings", onclick := loadMappings),
        div(id := "mappingsTable")
      ),

      // Add mapping section
      div(cls := "container")(
        h2("Add Mapping"),
        div(cls := "input-group")(
          input(
            id := "patternInput",
            placeholder := "IP Pattern (e.g., 192.168.1.*)"
          ),
          input(id := "countryInput", placeholder := "Country Code (e.g., US)"),
          button("Add", onclick := addMapping)
        ),
        div(id := "addResult")
      )
    ).render
  }

  def lookupIp(e: dom.Event): Unit = {
    val ip = document.getElementById("ipInput").asInstanceOf[html.Input].value
    val resultDiv = document.getElementById("ipResult")

    dom
      .fetch(s"/mock-geo-ip/csv/$ip")
      .flatMap(_.text())
      .map { response =>
        val result = io.circe.parser
          .parse(response)
          .toOption
          .flatMap(_.as[GeoIpInfo].toOption)

        resultDiv.innerHTML = result match {
          case Some(info) =>
            table(
              tr(th("IP"), td(info.ip.getOrElse(""))),
              tr(th("Country"), td(info.countryName.getOrElse(""))),
              tr(th("Country Code"), td(info.countryCode.getOrElse(""))),
              tr(th("Timezone"), td(info.timezone.getOrElse("")))
            ).render.outerHTML
          case None => "Error looking up IP"
        }
      }
  }

  def loadMappings(e: dom.Event): Unit = {
    val tableDiv = document.getElementById("mappingsTable")

    dom
      .fetch("/mock-geo-ip/mappings")
      .flatMap(_.text())
      .map { response =>
        val mappings = io.circe.parser
          .parse(response)
          .toOption
          .flatMap(_.as[List[IpMapping]].toOption)
          .getOrElse(Nil)

        tableDiv.innerHTML = table(
          thead(tr(th("Pattern"), th("Country Code"))),
          tbody(
            for (mapping <- mappings)
              yield tr(
                td(mapping.pattern),
                td(mapping.countryCode)
              )
          )
        ).render.outerHTML
      }
  }

  def addMapping(e: dom.Event): Unit = {
    val pattern =
      document.getElementById("patternInput").asInstanceOf[html.Input].value
    val country =
      document.getElementById("countryInput").asInstanceOf[html.Input].value
    val resultDiv = document.getElementById("addResult")

    dom
      .fetch(
        "/mock-geo-ip/mappings",
        new dom.RequestInit {
          method = "POST".asInstanceOf[dom.HttpMethod]
          headers = js.Dictionary("Content-Type" -> "application/json")
          body = s"""{"pattern": "$pattern", "countryCode": "$country"}"""
        }
      )
      .flatMap(_.text())
      .map { response =>
        resultDiv.innerHTML = response
        loadMappings(e)
      }
  }
}
