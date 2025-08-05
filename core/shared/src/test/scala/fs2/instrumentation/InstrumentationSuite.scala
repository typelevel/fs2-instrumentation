package fs2
package instrumentation

import cats.effect.IO
import munit.CatsEffectSuite
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.sdk.metrics.data.PointData
import org.typelevel.otel4s.sdk.testkit.metrics.MetricsTestkit

import fs2.io.net.Network

class InstrumentationSuite extends CatsEffectSuite {

  test("socket") {
    MetricsTestkit.inMemory[IO]().use { testkit =>
      implicit val meterProvider: MeterProvider[IO] = testkit.meterProvider
      Instrumentation.getMeter.flatMap { implicit meter =>
        Network[IO].bind().use { server =>
          val echoServer = server.accept.map(s => s.reads.through(s.writes)).parJoinUnbounded

          val client = Network[IO].connect(server.address).use { socket =>
            Instrumentation.registerSocket("client", socket, Attributes.empty).use { _ =>
              val msg = Chunk.array("Hello, world!".getBytes)
              socket.write(msg) >> socket.readN(msg.size) >> testkit.collectMetrics
            }
          }

          Stream.eval(client).concurrently(echoServer).compile.onlyOrError.map { collected =>
            val valuesByName = collected.map(md => md.name -> md.data.points.head.asInstanceOf[PointData.LongNumber].value).toMap
            assertEquals(valuesByName.get("fs2.io.net.socket.tx"), Option(13L))
            assertEquals(valuesByName.get("fs2.io.net.socket.rx"), Option(13L))
            assertEquals(valuesByName.get("fs2.io.net.socket.incomplete_writes"), Option(0L))
          }
        }
      }
    }
  }
}
