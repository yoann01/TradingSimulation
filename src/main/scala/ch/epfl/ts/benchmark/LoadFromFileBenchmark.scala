package ch.epfl.ts.benchmark

import java.io._
import ch.epfl.ts.data.{ Currency, Transaction }
import scala.io.Source
import scala.util.Random
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorRef
import sun.security.provider.certpath.ssl.SSLServerCertStore.CS

/**
 * Benchmarking reading data from a csv file, parsing it and instantiating Transaction objects from it
 *
 */
object LoadFromFileBenchmark {

  def main(args: Array[String]) = {

    //    generateFakeData
    val filename = "fakeData.csv"

    println("#####----- Java: using FileReader and BufferedReader -----#####")
    var br: BufferedReader = null;
    var line: String = "";
    //    String s = null;
    val cvsSplitBy: String = ",";
    var rawList: List[String] = List()

    br = new BufferedReader(new FileReader(filename));
    print("read file and store into List: ")
    val startTime = System.currentTimeMillis()
    while ({ line = br.readLine(); line != null }) {
//      println("read: " + line)
      rawList = line :: rawList
    }
    println((System.currentTimeMillis() - startTime) + "ms.")
    if (br != null) {
      br.close();
    }

    var splitList: List[Array[String]] = List()
    br = new BufferedReader(new FileReader(filename));
    print("read file, parse and store into List: ")
    val startTime2 = System.currentTimeMillis()
    while ({ line = br.readLine(); line != null }) {
      splitList = line.split(cvsSplitBy) :: splitList
    }
    println((System.currentTimeMillis() - startTime) + "ms.")

    if (br != null) {
      br.close();
    }
    println

    println("#####----- Scala: using scala.io.Source -----#####")
    val source = Source.fromFile(filename)
    println("###-- Unbuffered run ---###")
    print("read file and store into list: ")
    val entriesList = timed(source.getLines().toList)
    println("entries count: " + entriesList.size)
    print("from list: parse each element and instantiate Transaction objects: ")
    timed(
      entriesList.map(_.split(",")).map(l => Transaction(l(1).toDouble, l(2).toDouble, l(0).toLong, Currency.withName(l(3)), l(4), l(5))))

    println("###--- Buffered run ---###")
    print("read from file, instantiate Iterator, parse and instantiate Transaction objects: ")
    timed(Source.fromFile(filename).getLines().foreach(
      s => {
        val l = s.split(",")
        Transaction(l(1).toDouble, l(2).toDouble, l(0).toLong, Currency.withName(l(3)), l(4), l(5))
      }))
    println

    println("#####----- scala using actor -----#####")
    val system = ActorSystem("ReadingBenchmarking")
    val bufferedReader = system.actorOf(Props(new BufferedReaderActor(filename)), "bufferedReader")
    val simpleReader = system.actorOf(Props(new SimpleReaderActor(filename, bufferedReader)), "simpleReader")
    simpleReader ! "Start"

  }

  class SimpleReaderActor(filename: String, next: ActorRef) extends Actor {
    val source = Source.fromFile(filename)

    def receive = {
      case "Start" => {
        print("read file and store into list: ")
        val entriesList = timed(source.getLines().toList)
        next ! "Start"
        context.system.shutdown()
      }
    }
  }

  class BufferedReaderActor(filename: String) extends Actor {
    val source = Source.fromFile(filename)

    def receive = {
      case "Start" => {
        print("read from file, instantiate Iterator, parse and instantiate Transaction objects: ")

        timed(source.getLines().foreach(
          s => {
            val l = s.split(",")
            Transaction(l(1).toDouble, l(2).toDouble, l(0).toLong, Currency.withName(l(3)), l(4), l(5))
          }))

        context.system.shutdown()
      }
    }
  }

  def timed[A](block: => A) = {
    val t0 = System.currentTimeMillis
    val result = block
    println((System.currentTimeMillis - t0) + "ms")
    result
  }

  def generateFakeData = {
    val writer = new PrintWriter(new File("fakeData.csv"))
    val rnd = new Random()

    for (a <- 1 to 1000000) {
      writer.write(a + "," + (rnd.nextInt(150) + 100) + "," + (rnd.nextInt(30) + 1) + "," + "usd" + "," + "buyer" + "," + "seller" + "\n")
    }
  }
}