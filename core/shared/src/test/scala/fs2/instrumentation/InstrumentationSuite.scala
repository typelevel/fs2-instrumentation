/*
 * Copyright (c) 2025 Typelevel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

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
