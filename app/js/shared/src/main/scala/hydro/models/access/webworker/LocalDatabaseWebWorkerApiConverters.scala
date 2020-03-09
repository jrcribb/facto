package hydro.models.access.webworker

import hydro.models.access.webworker.LocalDatabaseWebWorkerApi.LokiQuery
import hydro.models.access.webworker.LocalDatabaseWebWorkerApi.WriteOperation
import hydro.models.access.webworker.LocalDatabaseWebWorkerApi.WriteOperation._
import hydro.scala2js.Scala2Js
import hydro.scala2js.StandardConverters._

import scala.collection.immutable.Seq
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

private[webworker] object LocalDatabaseWebWorkerApiConverters {

  implicit object WriteOperationConverter extends Scala2Js.Converter[WriteOperation] {
    private val insertNumber: Int = 1
    private val updateNumber: Int = 2
    private val removeNumber: Int = 3
    private val addCollectionNumber: Int = 4
    private val removeCollectionNumber: Int = 5

    override def toJs(operation: WriteOperation) = {
      operation match {
        case Insert(collectionName, obj) => js.Array[js.Any](insertNumber, collectionName, obj)
        case Update(collectionName, obj, abortUnlessExistingValueEquals) =>
          js.Array[js.Any](updateNumber, collectionName, obj, abortUnlessExistingValueEquals)
        case Remove(collectionName, id) => js.Array[js.Any](removeNumber, collectionName, id)
        case AddCollection(collectionName, uniqueIndices, indices, broadcastWriteOperations) =>
          js.Array[js.Any](
            addCollectionNumber,
            collectionName,
            uniqueIndices.toJSArray,
            indices.toJSArray,
            broadcastWriteOperations,
          )
        case RemoveCollection(collectionName) => js.Array[js.Any](removeCollectionNumber, collectionName)
      }
    }

    override def toScala(value: js.Any) = {
      val seq = value.asInstanceOf[js.Array[js.Any]].toVector
      val firstElement = seq(0).asInstanceOf[Int]

      (firstElement, seq) match {
        case (`insertNumber`, Seq(_, collectionName, obj)) =>
          Insert(collectionName.asInstanceOf[String], obj.asInstanceOf[js.Dictionary[js.Any]])
        case (`updateNumber`, Seq(_, collectionName, obj, abortUnlessExistingValueEquals)) =>
          Update(
            collectionName.asInstanceOf[String],
            obj.asInstanceOf[js.Dictionary[js.Any]],
            abortUnlessExistingValueEquals.asInstanceOf[js.UndefOr[js.Dictionary[js.Any]]],
          )
        case (`removeNumber`, Seq(_, collectionName, id)) =>
          Remove(collectionName.asInstanceOf[String], id)
        case (`addCollectionNumber`, Seq(_, collectionName, uniqueIndices, indices, broadcastWriteOperations)) =>
          AddCollection(
            collectionName = collectionName.asInstanceOf[String],
            uniqueIndices = uniqueIndices.asInstanceOf[js.Array[String]].toVector,
            indices = indices.asInstanceOf[js.Array[String]].toVector,
            broadcastWriteOperations = broadcastWriteOperations.asInstanceOf[Boolean],
          )
        case (`removeCollectionNumber`, Seq(_, collectionName)) =>
          RemoveCollection(collectionName.asInstanceOf[String])
      }
    }
  }

  implicit object LokiQueryConverter extends Scala2Js.Converter[LokiQuery] {
    override def toJs(query: LokiQuery) = {
      query match {
        case LokiQuery(collectionName, filter, sorting, limit) =>
          js.Array(
            collectionName,
            filter getOrElse js.undefined,
            sorting getOrElse js.undefined,
            limit map Scala2Js.toJs[Int] getOrElse js.undefined)
      }
    }

    override def toScala(value: js.Any) = {
      value.asInstanceOf[js.Array[js.UndefOr[js.Any]]].toVector match {
        case Seq(collectionName, filter, sorting, limit) =>
          LokiQuery(
            collectionName = collectionName.asInstanceOf[String],
            filter = filter.toOption.map(_.asInstanceOf[js.Dictionary[js.Any]]),
            sorting = sorting.toOption.map(_.asInstanceOf[js.Array[js.Array[js.Any]]]),
            limit = limit.toOption.map(_.asInstanceOf[Int])
          )
      }
    }
  }
}
