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

import cats.effect.{Resource, Sync}
import cats.syntax.all.*
import fs2.io.net.Socket
import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.otel4s.metrics.{Meter, MeterProvider}

object Instrumentation {

  val MeterNamespace: String = "fs2"
  val NetworkNamespace: String = "fs2.io.net"

  def getMeter[F[_]: MeterProvider]: F[Meter[F]] =
    MeterProvider[F].get(MeterNamespace)

  def registerSocket[F[_]: Sync: Meter](socketId: String, socket: Socket[F], extraAttributes: Attributes): Resource[F, Unit] = {
    val prefix = NetworkNamespace + ".socket"
    Meter[F].batchCallback.of(
      Meter[F].observableCounter[Long](s"$prefix.rx")
        .withDescription("Total number of bytes read from the network through this socket.")
        .createObserver,
      Meter[F].observableCounter[Long](s"$prefix.tx")
        .withDescription("Total number of bytes written to the network through this socket.")
        .createObserver,
      Meter[F].observableCounter[Long](s"$prefix.incomplete_writes")
        .withDescription("Number of times a write request consumed only part of the write buffer.")
        .createObserver,
    ) { (rx, tx, incompleteWrites) =>
      val m = socket.metrics
      val attributes = Attributes(Attribute("socket.id", socketId)) ++ extraAttributes
      for {
        snapshot <- Sync[F].delay((m.totalBytesRead(), m.totalBytesWritten(), m.incompleteWriteCount()))
        _ <- rx.record(snapshot._1, attributes)
        _ <- tx.record(snapshot._2, attributes)
        _ <- incompleteWrites.record(snapshot._3, attributes)
      } yield ()
    }
  }
}
