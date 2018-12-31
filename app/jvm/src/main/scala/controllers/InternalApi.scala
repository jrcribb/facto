package controllers

import java.nio.ByteBuffer

import akka.stream.scaladsl._
import app.api.Picklers._
import app.api.ScalaJsApi.ModificationsWithToken
import app.api.ScalaJsApi.UpdateToken
import app.api.ScalaJsApi.UserPrototype
import hydro.api.PicklableDbQuery
import hydro.api.ScalaJsApiRequest
import app.api.ScalaJsApiServerFactory
import app.api.UpdateTokens.toInstant
import app.api.UpdateTokens.toUpdateToken
import boopickle.Default._
import com.google.inject.Inject
import app.common.publisher.Publishers
import hydro.common.time.Clock
import controllers.helpers.AuthenticatedAction
import hydro.models.Entity
import app.models.access.JvmEntityAccess
import app.models.modification.EntityModification
import app.models.modification.EntityModificationEntity
import app.models.modification.EntityType
import app.models.money.ExchangeRateMeasurement
import app.models.accounting.TransactionGroup
import app.models.accounting.Transaction
import app.models.accounting.BalanceCheck
import app.models.user.User
import app.models.slick.SlickUtils.dbApi._
import app.models.slick.SlickUtils.dbRun
import app.models.slick.SlickUtils.instantToSqlTimestampMapper
import app.models.user.User
import play.api.i18n.I18nSupport
import play.api.i18n.MessagesApi
import play.api.mvc._

import scala.collection.immutable.Seq

final class InternalApi @Inject()(implicit override val messagesApi: MessagesApi,
                                  components: ControllerComponents,
                                  clock: Clock,
                                  entityAccess: JvmEntityAccess,
                                  scalaJsApiServerFactory: ScalaJsApiServerFactory,
                                  playConfiguration: play.api.Configuration,
                                  env: play.api.Environment)
    extends AbstractController(components)
    with I18nSupport {

  def scalaJsApiPost(path: String) = AuthenticatedAction(parse.raw) { implicit user => implicit request =>
    val requestBuffer: ByteBuffer = request.body.asBytes(parse.UNLIMITED).get.asByteBuffer
    val argsMap = Unpickle[Map[String, ByteBuffer]].fromBytes(requestBuffer)

    val bytes = doScalaJsApiCall(path, argsMap)
    Ok(bytes)
  }

  def scalaJsApiGet(path: String) = AuthenticatedAction(parse.raw) { implicit user => implicit request =>
    val bytes = doScalaJsApiCall(path, argsMap = Map())
    Ok(bytes)
  }

  def scalaJsApiWebsocket = WebSocket.accept[Array[Byte], Array[Byte]] { request =>
    implicit val user = AuthenticatedAction.requireAuthenticatedUser(request)

    Flow[Array[Byte]].map { requestBytes =>
      val request = Unpickle[ScalaJsApiRequest].fromBytes(ByteBuffer.wrap(requestBytes))

      doScalaJsApiCall(request.path, request.args)
    }
  }

  def entityModificationPushWebsocket(updateToken: UpdateToken) = WebSocket.accept[Array[Byte], Array[Byte]] {
    request =>
      def modificationsToBytes(modificationsWithToken: ModificationsWithToken): Array[Byte] = {
        val responseBuffer = Pickle.intoBytes(modificationsWithToken)
        val data: Array[Byte] = Array.ofDim[Byte](responseBuffer.remaining())
        responseBuffer.get(data)
        data
      }

      // Start recording all updates
      val entityModificationPublisher =
        Publishers.delayMessagesUntilFirstSubscriber(entityAccess.entityModificationPublisher)

      // Calculate updates from the update token onwards
      val firstMessage = {
        // All modifications are idempotent so we can use the time when we started getting the entities as next
        // update token.
        val nextUpdateToken: UpdateToken = toUpdateToken(clock.nowInstant)

        val modifications = {
          val modificationEntities = dbRun(
            entityAccess
              .newSlickQuery[EntityModificationEntity]()
              .filter(_.instant >= toInstant(updateToken))
              .sortBy(m => (m.instant, m.instantNanos)))
          modificationEntities.toStream.map(_.modification).toVector
        }

        ModificationsWithToken(modifications, nextUpdateToken)
      }

      val in = Sink.ignore
      val out = Source
        .single(modificationsToBytes(firstMessage))
        .concat(Source.fromPublisher(Publishers.map(entityModificationPublisher, modificationsToBytes)))
      Flow.fromSinkAndSource(in, out)
  }

  // Note: This action manually implements what autowire normally does automatically. Unfortunately, autowire
  // doesn't seem to work for some reason.
  private def doScalaJsApiCall(path: String, argsMap: Map[String, ByteBuffer])(
      implicit user: User): Array[Byte] = {
    val scalaJsApiServer = scalaJsApiServerFactory.create()

    val responseBuffer = path match {
      case "getInitialData" =>
        Pickle.intoBytes(scalaJsApiServer.getInitialData())
      case "getAllEntities" =>
        val types = Unpickle[Seq[EntityType.any]].fromBytes(argsMap("types"))
        Pickle.intoBytes(scalaJsApiServer.getAllEntities(types))
      case "persistEntityModifications" =>
        val modifications = Unpickle[Seq[EntityModification]].fromBytes(argsMap("modifications"))
        Pickle.intoBytes(scalaJsApiServer.persistEntityModifications(modifications))
      case "executeDataQuery" =>
        val dbQuery = Unpickle[PicklableDbQuery].fromBytes(argsMap("dbQuery"))
        Pickle.intoBytes[Seq[Entity]](scalaJsApiServer.executeDataQuery(dbQuery))
      case "executeCountQuery" =>
        val dbQuery = Unpickle[PicklableDbQuery].fromBytes(argsMap("dbQuery"))
        Pickle.intoBytes(scalaJsApiServer.executeCountQuery(dbQuery))
      case "upsertUser" =>
        val userPrototype = Unpickle[UserPrototype].fromBytes(argsMap("userPrototype"))
        Pickle.intoBytes(scalaJsApiServer.upsertUser(userPrototype))
    }

    val data: Array[Byte] = Array.ofDim[Byte](responseBuffer.remaining())
    responseBuffer.get(data)
    data
  }
}
