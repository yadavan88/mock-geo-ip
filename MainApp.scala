import cats.effect.IOApp
import cats.effect.ExitCode
import org.http4s.ember.server.EmberServerBuilder
import cats.effect.IO
import com.comcast.ip4s._
object MainApp extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val mockGeoIpService = MockGeoIpRestApi()
    val server = EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(mockGeoIpService.apply().orNotFound)
      .build

    server.useForever.as(ExitCode.Success)
  }
}
