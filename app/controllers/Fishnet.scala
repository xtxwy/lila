package controllers

import play.api.data.Form
import play.api.libs.json._
import play.api.mvc._

import lila.app._
import lila.fishnet.JsonApi.readers._
import lila.fishnet.JsonApi.writers._
import lila.fishnet.{ JsonApi, Work }

object Fishnet extends LilaController {

  private def env = Env.fishnet
  private def api = env.api

  def acquire = ClientAction[JsonApi.Request.Acquire] { req =>
    client =>
      api acquire client
  }

  def move(workId: String) = ClientAction[JsonApi.Request.PostMove] { data =>
    client =>
      api.postMove(Work.Id(workId), client, data) >> api.acquire(client)
  }

  def analysis(workId: String) = ClientAction[JsonApi.Request.PostAnalysis] { data =>
    client =>
      api.postAnalysis(Work.Id(workId), client, data) >> api.acquire(client)
  }

  private def ClientAction[A <: JsonApi.Request](f: A => lila.fishnet.Client => Fu[Option[JsonApi.Work]])(implicit reads: Reads[A]) =
    Action.async(BodyParsers.parse.json) { req =>
      req.body.validate[A].fold(
        err => BadRequest(jsonError(JsError toJson err)).fuccess,
        data => api.authenticateClient(data) flatMap {
          case None => Unauthorized(jsonError("Invalid or revoked API key")).fuccess
          case Some(client) => f(data)(client).map {
            case Some(work) => Ok(Json toJson work)
            case _          => NotFound(jsonError("That's all we got!"))
          }
        })
    }
}