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
