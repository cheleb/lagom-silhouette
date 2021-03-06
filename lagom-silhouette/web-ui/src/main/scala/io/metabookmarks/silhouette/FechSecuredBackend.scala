package io.metabookmarks.silhouette

import io.circe._, io.circe.parser._
import io.circe.generic.auto._, io.circe.syntax._

import sttp.client3._
import scala.concurrent.Future
import io.circe.Decoder
import sttp.model.Uri
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext.Implicits.global
import sttp.model.StatusCode
import scala.util.Try
import slinky.core.annotations.react
import slinky.core.FunctionalComponent
import slinky.core.facade.Hooks._
import slinky.web.ReactDOM
import org.scalajs.dom.document
import slinky.materialui.core.Snackbar
import io.metabookmarks.slinky.RenderElement

object ErrorHandlers {

  def onError(message: String): Unit = {

    @react object Aieaie {
      case class Props(message: String, parent: org.scalajs.dom.raw.Element)
      val component = FunctionalComponent[Props] { props =>
        val (open, setOpen) = useState(true)

        val exit = () => {
          setOpen(false)
          ReactDOM.unmountComponentAtNode(props.parent)
          document.body.removeChild(props.parent)
        }

        Snackbar(open = open, message = s"Resquest failed ($message)", autoHideDuration = 5000, onClose = exit)
      }
    }

    RenderElement.temporary(container => Aieaie(message, container))
  }

  def onDisconnect(message: String): Unit = {

    @react object Byebye {
      case class Props(message: String, parent: org.scalajs.dom.raw.Element)
      val component = FunctionalComponent[Props] { props =>
        val (open, setOpen) = useState(true)

        val exit = () => {
          setOpen(false)
          ReactDOM.unmountComponentAtNode(props.parent)
          document.body.removeChild(props.parent)
          document.location.href = "/"
        }

        Snackbar(open = open, message = s"You've been disconnected ($message)", autoHideDuration = 5000, onClose = exit)
      }
    }

    RenderElement.temporary(container => Byebye(message, container))
  }
}

class FechSecuredBackendService(onError: String => Unit = ErrorHandlers.onError,
                                onDisconnect: String => Unit = ErrorHandlers.onDisconnect
)(implicit
    sttpBackend: SttpBackend[Future, Any] = FetchBackend()
) {
  private def nocheck =
    basicRequest
      .header("Csrf-Token", "nocheck")

  def get[A](uri: Uri)(f: A => Unit)(implicit dec: Decoder[A]) =
    send(
      nocheck
        .get(uri)
    )(f)

  def put[A](uri: Uri, params: (String, String)*)(f: A => Unit)(implicit dec: Decoder[A]): Unit =
    send(nocheck.put(uri).body(params: _*))(f)

  def post[A](uri: Uri, params: (String, String)*)(f: A => Unit)(implicit dec: Decoder[A]): Unit =
    send(nocheck.post(uri).body(params: _*))(f)

  def delete(uri: Uri)(handle: () => Unit): Unit =
    nocheck.delete(uri).send().onComplete {
      case Success(res) =>
        res.body match {
          case Right(body) =>
            handle()

          case Left(value) =>
            if (res.code == StatusCode.Unauthorized)
              onDisconnect("Unauthorized")
        }
      case Failure(exception) =>
        onError(exception.getMessage())
    }

  def send[A](request: Request[Either[String, String], Any])(handle: A => Unit)(implicit dec: Decoder[A]): Unit =
    request
      .send()
      .onComplete(handleResponse(handle))

  private def handleResponse[A](
      f: A => Unit
  )(implicit dec: Decoder[A]): Try[Response[Either[String, String]]] => Unit = {
    case Success(res) =>
      res.body match {
        case Right(body) =>
          decode(body) match {
            case Left(error) =>
              onError(error.getMessage())
            case Right(a) => f(a)
          }

        case Left(value) =>
          if (res.code == StatusCode.Unauthorized)
            onDisconnect("Unauthorized")

      }
    case Failure(error) =>
      onError(error.getMessage())
  }
}
