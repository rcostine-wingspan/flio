import java.util.UUID
import scala.concurrent.duration._
import cats.syntax.apply._

import cats.effect.{ContextShift, IO, Timer}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext
import cats.effect.Clock
import scala.concurrent.duration.{FiniteDuration, TimeUnit}

object Main {
  def putStrlLn(value: String) = IO(println(value))
  val readLn = IO(scala.io.StdIn.readLine)

  def main(args: Array[String]): Unit = {
    val ioa1 = IO.pure(2)
    val ioa2 = (a: Int) => IO(println(a))


    def youtubeDl(id: String): IO[Unit] = {
      import sys.process._
      val uid = UUID.randomUUID()

      implicit val timer = IO.timer(ExecutionContext.global)

      val info = (msg: String) => println("OUT " + msg)
      val err = (msg: String) => println("ERR " + msg)

      val url = "https://www.youtube.com/watch?v=" + id

      val finalDestination = new java.io.File("/projects/complete/" + id)

      val directory = java.io.File.createTempFile("youtubeDl-" + uid, "")
      directory.delete
      directory.mkdir // move when complete - return instantly if it's already there

      val cmd =
        s"""youtube-dl --skip-download ${url}"
           |    --sub-format srt --write-sub --write-auto-sub --ignore-errors --youtube-skip-dash-manifest
           |     -o ${directory}/v%(id)s --write-info-json --write-description
           |    --write-annotations --sub-lang en --no-call-home
        """.stripMargin

        val log = ProcessLogger(
          (msg) => info(s"${uid} ${msg}"),
          (msg) => err(s"${uid} ${msg}")
        )

        IO.cancelable { cb =>
          if (finalDestination.exists()) {
            info("Using cached version")
            cb(Right({}))

            IO({}) // cancelling is a no-op if the thing is already there
          } else {
            val proc = Process(cmd)
            info(s"${uid} ${cmd}")

            val process = proc.run(log)
            var cancelled = false

            def wait(p: ProcessBuilder): IO[Unit] = {
              p.hasExitValue match {
                case true => IO({
                  info("Success!")
                })
                case false =>
                  IO.sleep(25 milliseconds) *> wait(p)
              }
            }

            /*cb({
              Right({
                while (process.isAlive() && !cancelled) {
                  Thread.sleep(10)
                  //println("Monitoring...")
                }

                if (!cancelled) {
                  directory.renameTo(finalDestination)
                }
              })
            })*/

            //val r = new Runnable { def run() = cb(Right(())) }
            //val f = sc.schedule(r, d.length, d.unit)

            // check error code
            // test cancellation

            // Returning the cancellation token needed to cancel
            // the scheduling and release resources early
            //IO(f.cancel(false))
            IO({
              err("Cancelling process by request")
              cancelled = true
              process.destroy()
            })
          }
          // test cancellation
          // test logging
      }
    }

  //IO.race()
  val contextShift = IO.contextShift(global)

  val program =
    IO.race(
      youtubeDl("U9onI0MzmuI"),
      youtubeDl("q9qZveIjXp4"),
    )(contextShift).flatMap {
      case Left(a) => IO.pure(a)
      case Right(_) => IO {
        println("Right")
      }
    }

    program.unsafeRunSync()
  }
}
