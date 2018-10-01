import io.chrisdavenport.fuuid._
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.duration._
import cats.syntax.apply._
import cats.effect.{ContextShift, IO, Timer}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.concurrent.{ExecutionContext, Future}
import cats.effect.Clock

import scala.concurrent.duration.{FiniteDuration, TimeUnit}
import scala.sys.process.Process

object Main {

  implicit val timer = IO.timer(ExecutionContext.global)

  val info = (msg: String) => println("OUT " + msg)
  val err = (msg: String) => println("ERR " + msg)

  def putStrlLn(value: String) = IO(println(value))
  val readLn = IO(scala.io.StdIn.readLine)

  def cmd(cmd: String, runId: String): IO[Int] = {

    IO.cancelable(
      (cb: (Either[Throwable, Int] => Unit)) => {
        val isCancelled = new AtomicBoolean(false)

        var process: Option[Process] = None

        val asyncResult = Future {
          import sys.process._
          info(s"${runId} Running `${cmd}`:")

          val log = ProcessLogger(
            (msg) => info(s"${runId}   ${msg}"),
            (msg) => err(s"${runId}   ${msg}")
          )

          val proc = Process(cmd)
          process = Some(proc.run(log))

          process.get.exitValue()
        }

        asyncResult.onComplete {
          case Success(value) => cb(Right(value))
          case Failure(e) => cb(Left(e))
        }

        IO {
          isCancelled.set(true)

          process match {
            case Some(process) => process.destroy()
            case None => {
              info("No process to cancel")
            }
          }

          info("# # # set isCancelled = true")
        }
      }
    )
  }

  def docker(id: String, params: String, runId: String) = cmd(s"docker run --rm -i ${params} ${id}", runId)

  def portAvailable(i: Int) = IO({

  })

  def main(args: Array[String]): Unit = {
    val contextShift = IO.contextShift(global)

    import doobie._
    import doobie.implicits._

    val xa = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver", "jdbc:postgresql://localhost:6432/postgres", "postgres", "pwd"
    )

    val program = for (
      runId <- FUUID.randomFUUID[IO];
      IO.race(
        docker("postgres", "-p 6432:5432 -e POSTGRES_PASSWORD=pwd", runId.show),
        IO.sleep(5 seconds) *> sql"select 42".query[Int].unique.transact(xa).flatMap(
          x => IO({
            println(x)
          })
        )
      )(contextShift) *>
      IO({
        println("success")
      })) yield program

    /*IO.race(
      //youtubeDl("U9onI0MzmuI"),

      IO.sleep(10 seconds)
    )(contextShift)*/
      /*case Left(e) => IO.pure({
        println("Fail: " + e)
      })
      case Right(res) => IO {
        println("Success: " + res)
      }
    })*/

    program.unsafeRunSync()
  }
}


/*def youtubeDl(id: String): IO[Unit] = {
  import sys.process._
  val uid = UUID.randomUUID()

  val url = "https://www.youtube.com/watch?v=" + id

  val finalDestination = new java.io.File("/home/gary/Documents/projects/flio/complete/" + id)

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

        def wait(): IO[Try[Unit]] = {
            println(process.isAlive)
            process.isAlive match {
              case false => IO({
                Success({
                  if (!cancelled) {
                    info("Completed, renaming to final location")
                    directory.renameTo(finalDestination)
                  } else {
                    err("Completed, ignoring files due to cancellation")
                  }
                })
              })
              case true =>
                IO.sleep(100 milliseconds) *> wait
            }
        }

        wait().attempt.map(
          (a) =>
            a match {
              case Left(a) => cb(Right({}))
              case Right(err) =>
                err match {
                  case Success(_) => cb(Right({}))
                  case Failure(e) => cb(Left(e))
                }
            }
        )

        /*cb({
          Right({
            while (process.isAlive() && !cancelled) {
              Thread.sleep(10)
              //println("Monitoring...")
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
*/
//IO.race()
//val contextShift = IO.contextShift(global)
