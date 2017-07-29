package pviz

import java.io.File
import java.nio.file.Paths
import java.nio.file.Files

import java.nio.charset.StandardCharsets

import upickle.default._
import upickle.Js
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.collection.mutable.ListBuffer

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

object Template{
  import scalatags.Text.all._
  import scalatags.Text.tags2.title

  val txt: String =
    "<!DOCTYPE html>" +
    html(
      head(
        title("nMix Protocol Status"),
        meta(httpEquiv:="Content-Type", content:="text/html; charset=UTF-8"),
        script(`type`:="text/javascript", src:="/client-fastopt.js"),
        script(`type`:="text/javascript", src:="http://cdn.jsdelivr.net/jquery/2.1.1/jquery.js"),
        link(
          rel:="stylesheet",
          `type`:="text/css",
          href:="/style.css"
        )
      ),
      body(margin:=0, width:="100%", height:="100%")(
        script("pviz.Viz().main()")
      )
    )
}

object AutowireServer extends autowire.Server[Js.Value, Reader, Writer]{
  def read[Result: Reader](p: Js.Value) = upickle.default.readJs[Result](p)
  def write[Result: Writer](r: Result) = upickle.default.writeJs(r)
}

object Server extends Api with Names {
  val root = sys.props.get("pviz.target").get

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    if(!Files.isDirectory(Paths.get(root))) {
      println(s"The configured pviz.target property is invalid ('$root')")
      sys.exit
    }

    val route = {
      get{
        pathSingleSlash {
          complete{
            HttpEntity(
              ContentTypes.`text/html(UTF-8)`,
              Template.txt
            )
          }
        } ~
        getFromResourceDirectory("")
      } ~
      post {
        path("api" / Segments){ s =>
          extract(_.request.entity match {
            case HttpEntity.Strict(nb: ContentType.NonBinary, data) =>
              data.decodeString(nb.charset.value)
            case _ => ""
          }) { e =>
            if(e.length > 0) {
              complete {
                AutowireServer.route[Api](Server)(
                  autowire.Core.Request(
                    s,
                    upickle.json.read(e).asInstanceOf[Js.Obj].value.toMap
                  )
                ).map(upickle.json.write(_))
              }
            }
            else {
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,""))

            }
          }
        }
      }
    }
    Http().bindAndHandle(route, "0.0.0.0", port = 8080)
  }

  def getStatus(): Status = {
    val start = System.nanoTime()
    val files = getRecursiveListOfFiles(new File(root))
      .filter(_.isFile)
      .map(f => {
        Paths.get(root).relativize(f.toPath)
          .toString.replace("\\", "-").replace(".","-").replace("/","-")
          .replace("config-json", "root-config-json")
          .replace("^error", "root-error")
          .replace("pause", "root-pause")
      })

    val config = getConfig()
    val active = (for(i <- 1 to config.trustees.size) yield {
      val act = Protocol.execute(i, config, files)
      println("active for trustee " + i + " " + act)
      act
    }).flatten

    val end = System.nanoTime()
    println("getStatus: " + files.size + " artifacts, " + active.size + " active, time: " + ((end - start) / 1000000000.0) + " s")
    Status(files, active)
  }

  def getConfig(): Config = {
    val cfg = Source.fromFile(Paths.get(root).resolve("config.json").toFile)(StandardCharsets.UTF_8).mkString
    decode[Config](cfg).right.get
  }

  def getRecursiveListOfFiles(dir: File): Array[File] = {
    val these = dir.listFiles.filter(_.getName != ".git")
    these ++ these.filter(_.isDirectory).flatMap(getRecursiveListOfFiles)
  }
}

sealed trait Cond {

  def eval(files: Seq[String]): Boolean

  def and(other: Cond) = {
    JointCondition(this, other)
  }

  def and(file: String) = {
    JointCondition(this, Condition.yes(file))
  }

  def andNot(file: String) = {
    JointCondition(this, Condition.no(file))
  }
}

case class Condition(terms: List[(String, Boolean)], name: String = "condition", negate: Boolean = false) extends Cond {

  def eval(files: Seq[String]): Boolean = {
    val result = ev(files)
    result != negate
  }

  def yes(file: String) = {
    copy((file, true) :: terms)
  }

  def no(file: String) = {
    copy((file, false) :: terms)
  }

  def neg = {
    copy(negate = true)
  }

  private def ev(files: Seq[String]): Boolean = {
    terms.foreach { case(file, b) =>
      val result = files.contains(file)
      if(result != b) return false
    }

    true
  }
}

object Condition {
  def yes(file: String) = Condition(List((file, true)))
  def no(file: String) = Condition(List((file, false)))
  def empty() = Condition(List[(String, Boolean)]())
}

case class JointCondition(conditions: Cond*) extends Cond {
  def eval(files: Seq[String]): Boolean = {
    conditions.foreach{ condition =>
      val result = condition.eval(files)
      if(!result) return false
    }
    true
  }
}

object Protocol extends Names {

  def execute(position: Int, config: Config, files: Seq[String]): Seq[String] = {
    val ret = ListBuffer[String]()

    val rules = globalRules(config, position)

    rules.find{ case (c, a) => c.eval(files)}
      .foreach(ret += _._2)

    val items = config.items
    val irules = (1 to items).map(i => itemRules(config, position, i, files))

    val hits = irules.flatMap { rules =>
      rules.find{ case (c, a) => c.eval(files)}
      .map(_._2)
    }.sorted

    hits.foreach(ret += _)

    ret
  }

  private def globalRules(config: Config, position: Int) = {

    val configNo = Condition.yes(CONFIG).yes(CONFIG_STMT).no(CONFIG_SIG(position))

    ListBuffer[(Cond, String)](
      configNo -> CONFIG_SIG(position)
    )
  }

  private def itemRules(config: Config, position: Int, item: Int, files: Seq[String]) = {

    val allConfigsYes = Condition(
      (1 to config.trustees.size).map(auth => CONFIG_SIG(auth) -> true)
      .toList
    )

    val myShareNo = Condition.no(SHARE(item, position)).no(SHARE_STMT(item, position))
      .no(SHARE_SIG(item, position))
    val allShares = Condition((1 to config.trustees.size).flatMap { auth =>
        List(SHARE(item, auth) -> true, SHARE_STMT(item, auth) -> true, SHARE_SIG(item, auth) -> true)
      }
      .toList
    )

    val noPublicKey = Condition.no(PUBLIC_KEY(item))
    val noPublicKeySig = Condition.yes(PUBLIC_KEY(item)).no(PUBLIC_KEY_SIG(item, position))

    val myMixPosition = getMixPosition(position, item, config.trustees.size)
    val previousMixesYes = Condition((1 to myMixPosition - 1).flatMap { auth =>
        val mixAuth = getMixPositionInverse(auth, item, config.trustees.size)
        List(MIX(item, mixAuth) -> true, MIX_STMT(item, mixAuth) -> true, MIX_SIG(item, mixAuth, mixAuth) -> true)
      }
      .toList
    )

    val ballotsYes = Condition.yes(BALLOTS(item)).yes(BALLOTS_STMT(item)).yes(BALLOTS_SIG(item))

    val myPreShuffleNo = ballotsYes.andNot(MIX(item, position)).andNot(PERM_DATA(item, position))
    val myPreShuffleYes = Condition.yes(PERM_DATA(item, position))

    val myMixNo = ballotsYes.and(previousMixesYes).andNot(MIX(item, position))

    /** verify mixes other than our own */
    val missingMixSigs = (1 to config.trustees.size).filter(_ != position).map { auth =>
      (auth, item, Condition(List(MIX(item, auth) -> true, MIX_STMT(item, auth) -> true,
        MIX_SIG(item, auth, auth) -> true, MIX_SIG(item, auth, position) -> false)))
    }
    val allMixSigs = Condition((1 to config.trustees.size).map { auth =>
      MIX_SIG(item, auth, position) -> true
    }.toList)

    val noDecryptions = Condition.no(DECRYPTION(item, position))
      .no(DECRYPTION_STMT(item, position)).no(DECRYPTION_SIG(item, position))
    val allDecryptions = Condition((1 to config.trustees.size).flatMap { auth =>
        List(DECRYPTION(item, auth) -> true, DECRYPTION_STMT(item, auth) -> true,
          DECRYPTION_SIG(item, auth) -> true)
      }
      .toList
    )

    val decryptor = getDecryptingTrustee(item, config.trustees.size)

    val noPlaintexts = Condition.no(PLAINTEXTS(item, decryptor))
    val noPlaintextsSig = Condition.yes(PLAINTEXTS(item, decryptor)).no(PLAINTEXTS_SIG(item, position))

    val rules = ListBuffer[(Cond, String)]()

    rules += allConfigsYes.and(myShareNo) -> SHARE(item, position)

    if(position == 1) {
      rules += allShares.and(noPublicKey) -> PUBLIC_KEY(item)
    }

    rules += allShares.and(noPublicKeySig) -> PUBLIC_KEY_SIG(item, position)

    rules += myMixNo -> MIX(item, position)

    missingMixSigs.foreach { case(auth, item, noMixSig) =>
      rules += noMixSig -> MIX_SIG(item, auth, position)
    }

    rules += allMixSigs.and(noDecryptions) -> DECRYPTION(item, position)
    if(position == decryptor) {

      rules += allDecryptions.and(noPlaintexts) -> PLAINTEXTS(item, position)
    }

    rules += allDecryptions.and(noPlaintextsSig) -> PLAINTEXTS_SIG(item, position)

    rules
  }

  def getMixPosition(auth: Int, item: Int, trustees: Int): Int = {
    val permuted = (auth + (item - 1)) % trustees
    permuted + 1
  }

  def getMixPositionInverse(auth: Int, item: Int, trustees: Int): Int = {
    val gap = trustees - item

    val permuted = (auth + gap) % trustees
    if(permuted == 0) {
      trustees
    }
    else {
      permuted
    }
  }

  def getDecryptingTrustee(item: Int, trustees: Int): Int = {
    val permuted = (item - 1) % trustees
    permuted + 1
  }
}