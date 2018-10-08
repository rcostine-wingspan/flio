import io.chrisdavenport.fuuid._
import java.util.concurrent.atomic.AtomicBoolean

import util.Random.nextInt
import util.Random.nextDouble
import scala.concurrent.duration._
import cats.syntax.apply._
import cats.effect.{ContextShift, IO, Timer}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.concurrent.{ExecutionContext, Future}
import cats.effect.Clock

import scala.concurrent.duration.{FiniteDuration, TimeUnit}
import scala.sys.process.Process

object Hyperparameters {

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

    case class Experiment(
       dimensions: Int,
       epochs: Int,
       neg: Int,
       threads: Int,
       lr: Double,
       loss: Double,
       mode: String,
       minCount: Int,
       index: Int
    )

    Stream.continually(1).zipWithIndex.map(
      (idx: (Int, Int)) => Experiment(
        (1 + nextInt(8) ) * 50,
        5 + nextInt(100),
        nextInt(20),
        1 + nextInt(100),
        nextDouble(),
        nextDouble(),
        if (nextInt(2) > 0) { "skipgram" } else { "cbow" },
        nextInt(10),
        idx._2
      )
    ).take(100).map(
      (e) => s"../fastText/fasttext supervised ${e.mode} " + 
             s"-input train.txt -output model${e.index} " +
             s"-dim ${e.dimensions} " +
             s"-epoch ${e.epochs} " +
             s"-lr ${e.lr} " + 
             s"-thread ${e.threads} " + 
             s"-loss ${e.loss} " +
             s"-neg ${e.neg} " +
             s"-minCount ${e.minCount}"
    ).map(
      println
    ).force

    /*val program = for (
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

    program.unsafeRunSync()*/
  }
}
