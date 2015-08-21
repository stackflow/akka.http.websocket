import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._

object WSServer extends App {
  // required actorsystem and materializer
  implicit val system = ActorSystem("websockets")
  implicit val materializer = ActorMaterializer()

  val config = ConfigFactory.load()

  val interface = config.getString("http.interface")
  val port = config.getInt("http.port")

  def reflectionRoute = path("reflection") {
    handleWebsocketMessages(Flows.reverseFlow)
  }
  def battleRoute = path("battle" / Rest) {
    id => handleWebsocketMessages(Flows.echoFlow(id))
  }
  val routes: Route = battleRoute ~ reflectionRoute

  val bindingFuture = Http().bindAndHandle(routes, interface, port)

  try {
    Await.result(bindingFuture, 1 second)
    println("Server online at http://" + interface + ":" + port)
  } catch {
    case exc: TimeoutException =>
      println("Server took to long to startup, shutting down")
      system.shutdown()
  }
}

/**
 * This object contains the flows the handle the websockets messages. Each flow is attached
 * to a websocket and gets executed whenever a message comes in from the client.
 */
object Flows {

  /**
   * The simple flow just reverses the sent message and returns it to the client. There
   * are two types of messages, streams and normal textmessages. We only process the
   * normal ones here, and ignore the others.
   */
  def reverseFlow: Flow[Message, Message, Unit] = {
    Flow[Message].map {
      case TextMessage.Strict(txt) => TextMessage.Strict(txt.reverse)
      case _ => TextMessage.Strict("Not supported message type")
    }
  }

  /**
   * Simple flow which just returns the original message
   * back to the client
   */
  def echoFlow(id: String): Flow[Message, Message, Unit] = {
    Flow[Message].map {
      case TextMessage.Strict(txt) => TextMessage.Strict("Hello, " + txt + ", " + id)
      case _ => TextMessage.Strict("Not supported message type")
    }
  }
}