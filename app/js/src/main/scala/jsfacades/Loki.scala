package jsfacades

import common.GuavaReplacement.Iterables.getOnlyElement
import common.ScalaUtils

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala2js.Scala2Js
import scala2js.Converters._

object Loki {
  @JSName("loki")
  @js.native
  private final class DatabaseFacade(dbName: String, args: js.Dictionary[js.Any] = null) extends js.Object {

    def addCollection(name: String): CollectionFacade = js.native
    def getCollection(name: String): CollectionFacade = js.native

    def saveDatabase(callback: js.Function0[Unit]): Unit = js.native
    def loadDatabase(properties: js.Dictionary[js.Any], callback: js.Function0[Unit]): Unit = js.native
  }

  final class Database(facade: DatabaseFacade) {
    def getOrAddCollection[E: Scala2Js.MapConverter](name: String): Collection[E] = {
      val collection = facade.getCollection(name)
      if (collection == null) {
        new Collection(facade.addCollection(name))
      } else {
        new Collection(collection)
      }
    }

    def saveDatabase(): Future[Unit] = {
      val (callback, future) = ScalaUtils.callbackSettingFuturePair()
      facade.saveDatabase(callback)
      future
    }

    def loadDatabase(): Future[Unit] = {
      val (callback, future) = ScalaUtils.callbackSettingFuturePair()
      facade.loadDatabase(properties = js.Dictionary(), callback)
      future
    }
  }

  object Database {
    def persistent(dbName: String): Database = {
      new Database(
        new DatabaseFacade(
          dbName, js.Dictionary("adapter" -> new IndexedAdapter(dbName))))
    }
  }

  @JSName("LokiIndexedAdapter")
  @js.native
  final class IndexedAdapter(name: String) extends js.Object

  @js.native
  private trait CollectionFacade extends js.Object {

    def chain(): ResultSetFacade = js.native

    def insert(obj: js.Dictionary[js.Any]): Unit = js.native
    def findAndRemove(filter: js.Dictionary[js.Any]): Unit = js.native
    def clear(): Unit = js.native
  }

  final class Collection[E: Scala2Js.MapConverter](facade: CollectionFacade) {

    def chain(): ResultSet[E] = new ResultSet[E](facade.chain())

    def insert(obj: E): Unit = facade.insert(Scala2Js.toJsMap(obj))
    def findAndRemove(filter: (String, js.Any)*): Unit = facade.findAndRemove(js.Dictionary(filter: _*))
    def clear(): Unit = facade.clear()
  }

  @js.native
  private trait ResultSetFacade extends js.Object {

    def find(filter: js.Dictionary[js.Any], firstOnly: Boolean = false): ResultSetFacade = js.native
    def data(): js.Array[js.Dictionary[js.Any]] = js.native
    def count(): Int = js.native
  }

  final class ResultSet[E: Scala2Js.MapConverter](facade: ResultSetFacade) {

    // **************** Intermediary operations **************** //
    def find(filter: (String, js.Any)*): ResultSet[E] = {
      new ResultSet[E](facade.find(js.Dictionary(filter: _*)))
    }

    // **************** Terminal operations **************** //
    def findOne(filter: (String, js.Any)*): Option[E] = {
      val data = facade.find(js.Dictionary(filter: _*), firstOnly = true).data()
      if (data.length >= 1) {
        Option(Scala2Js.toScala[E](getOnlyElement(data)))
      } else {
        None
      }
    }

    def data(): Seq[E] = {
      Scala2Js.toScala[Seq[E]](facade.data())
    }

    def count(): Int = {
      facade.count()
    }
  }

  object ResultSet {
    def empty[E: Scala2Js.MapConverter]: ResultSet[E] = ??? // TODO: Maybe create this from an empty Loki db?
  }
}
