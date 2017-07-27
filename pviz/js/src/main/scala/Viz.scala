package pviz

import scala.scalajs.js.annotation.JSExport
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.MouseEvent
import scala.util.Random
import scala.concurrent.Future
import scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scalatags.JsDom.all._
import upickle.default._
import upickle.Js
import autowire._
import org.scalajs.jquery.jQuery
import scala.scalajs.js.timers._

object Client extends autowire.Client[Js.Value, Reader, Writer]{
  override def doCall(req: Request): Future[Js.Value] = {
    dom.ext.Ajax.post(
      url = "/api/" + req.path.mkString("/"),
      data = upickle.json.write(Js.Obj(req.args.toSeq:_*))
    ).map(_.responseText)
     .map(upickle.json.read)
  }

  def read[Result: Reader](p: Js.Value) = readJs[Result](p)
  def write[Result: Writer](r: Result) = writeJs(r)
}

@JSExport
object Viz extends Names {
  @JSExport
  def main(): Unit = {

    val headerBox = div.render
    val statusBox = div.render
    val pauseButton = button("Freeze").render
    var paused: Boolean = false

    def init() = {
      Client[Api].getConfig().call().foreach { config =>
        val items = config.items
        val trustees = config.trustees.size
        headerBox.appendChild(
          p(
            "election '" + config.name + "' (id = " + config.id + "), questions = "
              + config.items + ", trustees = " + trustees + ", ",
            for(i <- 1 to trustees) yield {
              span(cls:="present-" + i, "trustee " + i + " ")
            },
            span(cls:="present-bb", " ballotbox ")
          ).render
        )

        dom.window.localStorage.setItem("trustees", config.trustees.size.toString)
        dom.window.localStorage.setItem("items", config.items.toString)
        statusBox.appendChild(
          table(
            tbody(
              tr(td("election config"), td(id:=CONFIG)),

              tr(td("config signatures"),
                for(i <- 1 to trustees) yield {
                  td(id:=CONFIG_SIG(i))
                }
              ),
              tr(td("key shares"),
                for(i <- 1 to trustees; j <- 1 to items) yield {
                  td(id:=SHARE(j, i))
                }
              ),
              tr(td("joint public key"),
                for(i <- 1 to items) yield {
                  td(id:=PUBLIC_KEY(i))
                }
              ),
              tr(td("public key verification"),
                for(i <- 2 to trustees; j <- 1 to items) yield {
                  td(id:=PUBLIC_KEY_SIG(j, i))
                }
              ),
              tr(td("ciphertexts"),
                for(i <- 1 to items) yield {
                  td(id:=BALLOTS(i))
                }
              ),
              tr(td("mix"),
                for(i <- 1 to trustees) yield {
                  for(j <- 1 to items) yield {
                    td(id:=MIX(j, i))
                  }
                }
              ),
              for(i <- 1 to items) yield {
                  tr(td("mix verification (q = " + i + ")"),
                  for(j <- 1 to trustees; k <- 1 to trustees if j != k) yield {
                    td(id:=MIX_SIG(i, j, k))
                  }
                )
              },
              tr(td("partial decryptions"),
                for(i <- 1 to trustees; j <- 1 to items) yield {
                  td(id:=DECRYPTION(j, i))
                }
              ),
              tr(td("decrypted plaintexts"),
                for(i <- 1 to items) yield {
                  val decryptor = ((i - 1) % config.trustees.size) + 1
                  td(id:=PLAINTEXTS(i, decryptor))
                }
              ),
              tr(td("plaintext verification"),
                for(i <- 1 to trustees; j <- 1 to items if i != ((j - 1) % config.trustees.size) + 1) yield {
                  td(id:=PLAINTEXTS_SIG(j, i))
                }
              ),
              tr(td()),
              tr(td("idle"), td(id:=IDLE)),
              tr(td("complete"), td(id:=DONE)),
              tr(td("paused"), td(id:=PAUSE)),
              tr(td("error (global)"), td(id:=ERROR)),
              tr(td("error"),
                for(i <- 1 to trustees) yield {
                  td(id:=ERROR(i))
                }
              )
            )
          ).render
        )
      }

      pauseButton.onclick = (_: MouseEvent) => {
        paused = !paused
        val text = if(paused) {
          "Resume"
        }
        else {
          "Freeze"
        }
        jQuery("button").html(text)
      }
    }

    def update() = {
      Client[Api].getStatus().call().foreach { status =>
        val trustees = dom.window.localStorage.getItem("trustees").toInt
        val items = dom.window.localStorage.getItem("items").toInt

        // println("present ==========")
        // println(status.present)
        println("active ===========")
        println(status.active)
        // clear all
        jQuery("td").removeClass()
        status.present.foreach { file =>
          val prefix = file.substring(0, file.indexOf('-'))

          if(file.contains("error")) {
            jQuery("#" + file).addClass("error")

          }
          else if(file.contains(PAUSE)) {
            jQuery("#" + file).addClass("pause")

          }
          else {
            jQuery("#" + file).addClass("present-" + prefix)
          }
        }
        if(status.active.size == 0) {
          val done = (for(i <- 1 to trustees; j <- 1 to items if i != ((j - 1) % trustees) + 1) yield {
            jQuery("#" + PLAINTEXTS_SIG(j, i)).hasClass("present-" + i)
          }).filter(!_)

          if(done.size == 0) {
            jQuery("#"+DONE).addClass("done")
          }
          else {
            jQuery("#"+IDLE).addClass("idle")
          }
        }
        else {
          status.active.foreach { file =>
            jQuery("#" + file).addClass("active")
          }
        }
      }
    }
    dom.document.body.appendChild(
      div(
        cls:="container",
        h3("nMix Protocol Status"),
        headerBox,
        statusBox,
        hr(),
        pauseButton
      ).render
    )

    init()
    setInterval(2000) {
      if(!paused) update()
    }
  }
}
