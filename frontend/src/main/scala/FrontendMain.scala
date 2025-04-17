import org.scalajs.dom
import org.scalajs.dom.{document, html}
import scalatags.JsDom.all._
import scala.scalajs.js.Thenable.Implicits._
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

object FrontendMain {
  def main(args: Array[String]): Unit = {
    val app = document.getElementById("app")
    app.appendChild(createUI())
    // Load mappings immediately after creating the UI
    loadMappings(null)
  }

  def createUI(): html.Div = {
    div(
      h1("Mock GeoIP Service"),

      // Lookup IP section
      div(cls := "container")(
        h2("Lookup IP"),
        div(cls := "input-group")(
          input(
            id := "ipInput",
            placeholder := "Enter IP address (e.g., 192.168.1.1)",
            `type` := "text"
          ),
          button("Lookup", onclick := lookupIp)
        ),
        div(id := "ipResult", cls := "result-container")
      ),

      // View mappings section
      div(cls := "container")(
        h2("IP Mappings"),
        button("Refresh Mappings", onclick := loadMappings),
        div(id := "mappingsTable", cls := "result-container")
      ),

      // Add mapping section
      div(cls := "container")(
        h2("Add Mapping"),
        div(cls := "input-group")(
          input(
            id := "patternInput",
            placeholder := "IP Pattern (e.g., 192.168.1.*)",
            `type` := "text"
          ),
          input(
            id := "countryInput",
            placeholder := "Country Code (e.g., US)",
            `type` := "text"
          ),
          button("Add", onclick := addMapping)
        ),
        div(id := "addResult", cls := "result-container")
      )
    ).render
  }

  def lookupIp(e: dom.Event): Unit = {
    val ip = document.getElementById("ipInput").asInstanceOf[html.Input].value
    val resultDiv = document.getElementById("ipResult")

    if (ip.isEmpty) {
      resultDiv.innerHTML = div(cls := "error")("Please enter an IP address").render.outerHTML
      return
    }

    resultDiv.innerHTML = div("Loading...").render.outerHTML

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
              thead(
                tr(
                  th("Field"),
                  th("Value")
                )
              ),
              tbody(
                tr(td("IP"), td(info.ip.getOrElse("N/A"))),
                tr(td("Country"), td(info.countryName.getOrElse("N/A"))),
                tr(td("Country Code"), td(info.countryCode.getOrElse("N/A"))),
                tr(td("Timezone"), td(info.timezone.getOrElse("N/A")))
              )
            ).render.outerHTML
          case None => 
            div(cls := "error")("Error looking up IP").render.outerHTML
        }
      }
      .recover { case ex =>
        resultDiv.innerHTML = div(cls := "error")(s"Error: ${ex.getMessage}").render.outerHTML
      }
  }

  def loadMappings(e: dom.Event): Unit = {
    val tableDiv = document.getElementById("mappingsTable")
    tableDiv.innerHTML = div("Loading...").render.outerHTML

    dom
      .fetch("/mock-geo-ip/mappings")
      .flatMap(_.text())
      .map { response =>
        val mappings = io.circe.parser
          .parse(response)
          .toOption
          .flatMap(_.as[List[IpMapping]].toOption)
          .getOrElse(Nil)

        if (mappings.isEmpty) {
          tableDiv.innerHTML = div("No mappings found").render.outerHTML
        } else {
          tableDiv.innerHTML = table(
            thead(
              tr(
                th("IP Pattern"),
                th("Country Code")
              )
            ),
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
      .recover { case ex =>
        tableDiv.innerHTML = div(cls := "error")(s"Error: ${ex.getMessage}").render.outerHTML
      }
  }

  def addMapping(e: dom.Event): Unit = {
    val pattern = document.getElementById("patternInput").asInstanceOf[html.Input].value
    val country = document.getElementById("countryInput").asInstanceOf[html.Input].value
    val resultDiv = document.getElementById("addResult")

    if (pattern.isEmpty || country.isEmpty) {
      resultDiv.innerHTML = div(cls := "error")("Please fill in all fields").render.outerHTML
      return
    }

    resultDiv.innerHTML = div("Adding mapping...").render.outerHTML

    dom
      .fetch(
        "/mock-geo-ip/mappings",
        new dom.RequestInit {
          method = "POST".asInstanceOf[dom.HttpMethod]
          headers = js.Dictionary("Content-Type" -> "application/json")
          body = s"""{"pattern": "$pattern", "countryCode": "$country"}"""
        }
      )
      .flatMap { response =>
        if (response.status >= 200 && response.status < 300) {
          response.text().map { text =>
            resultDiv.innerHTML = div(cls := "success")(text).render.outerHTML
            loadMappings(e)
          }
        } else {
          response.text().map { text =>
            resultDiv.innerHTML = div(cls := "error")(text).render.outerHTML
          }
        }
      }
      .recover { case ex =>
        resultDiv.innerHTML = div(cls := "error")(s"Error: ${ex.getMessage}").render.outerHTML
      }
  }
}
