package controllers

import scala.concurrent.Future
import scala.util.{ Try, Success, Failure }
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import play.api.libs.json._

import models.Identity
import services.CRUDService

/**
 * Generic async CRUD controller
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
class CRUDController[E: Format, ID](val service: CRUDService[E, ID])(redirectUrl: ID => Call)(implicit identity: Identity[E, ID])
    extends Controller {

  val DEFAULT_LIMIT = Seq("50")

  def create = Action.async(parse.json) { implicit request =>
    parseValidateAndProcess[E] { entity =>
      service.create(entity).map {
        case Right(id) => Created.withHeaders(LOCATION -> redirectUrl(id).url)
        case Left(err) => BadRequest(err)
      }
    }
  }

  def read(id: ID) = Action.async {
    service.read(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    )(entity =>
        Ok(Json.toJson(entity)))
    )
  }

  def update(id: ID) = Action.async(parse.json) { implicit request =>
    parseValidateAndProcess[E] { entity =>
      service.update(id, entity).map {
        case Right(id) => Ok.withHeaders(LOCATION -> redirectUrl(id).url)
        case Left(err) => BadRequest(err)
      }
    }
  }

  def delete(id: ID) = Action.async {
    service.delete(id).map {
      case Right(id) => Ok
      case Left(err) => BadRequest(err)
    }
  }

  def parseValidateAndProcess[T: Reads](t: T => Future[Result])(implicit request: Request[JsValue]) = {
    request.body.validate[T].map(t) match {
      case JsSuccess(result, _) => result
      case JsError(err) => Future.successful(BadRequest(Json.toJson(err.map {
        case (path, errors) => Json.obj("path" -> path.toString, "errors" -> JsArray(errors.flatMap(e => e.messages.map(JsString(_)))))
      })))
    }
  }

  def parseJsonParam(param: String)(implicit request: Request[Any]): (String, Try[JsValue]) = (param, Try(request.queryString.get(param).map(_.head).map(Json.parse(_)).getOrElse(Json.obj())))

  def toError(t: (String, Try[JsValue])): JsObject = t match {
    case (paramName, Failure(e)) => Json.obj(paramName -> e.getMessage)
    case _ => Json.obj()
  }

}

