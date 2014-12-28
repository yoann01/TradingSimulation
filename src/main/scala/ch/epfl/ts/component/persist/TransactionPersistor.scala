package ch.epfl.ts.component.persist

import ch.epfl.ts.data.{Currency, Transaction}

import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.JdbcBackend.Database
import scala.slick.jdbc.JdbcBackend.Database.dynamicSession
import scala.slick.jdbc.meta.MTable
import scala.slick.lifted.{Column, TableQuery, Tag}

/**
 * Transaction persistance component
 */
class TransactionPersistanceComponent(dbFilename: String)
  extends PersistanceComponent[Transaction](new TransactionPersistor(dbFilename))

/**
 * Implementation of the Persistance trait for Transaction
 */
class TransactionPersistor(dbFilename: String) extends Persistance[Transaction] {

  class Transactions(tag: Tag) extends Table[(Int, Double, Double, Long, String, Long, Long, Long, Long)](tag, "TRANSACTIONS") {
    def * = (id, price, quantity, timestamp, currency, buyerId, buyerOrderId, sellerId, sellerOrderId)
    def id: Column[Int] = column[Int]("TRANSACTION_ID", O.PrimaryKey, O.AutoInc)
    def price: Column[Double] = column[Double]("PRICE")
    def quantity: Column[Double] = column[Double]("QUANTITY")
    def timestamp: Column[Long] = column[Long]("TIMESTAMP")
    def currency: Column[String] = column[String]("CURRENCY")
    def buyerId: Column[Long] = column[Long]("BUYER_ID")
    def buyerOrderId: Column[Long] = column[Long]("BUYER_ORDER_ID")
    def sellerId: Column[Long] = column[Long]("SELLER_ID")
    def sellerOrderId: Column[Long] = column[Long]("SELLER_ORDER_ID")
  }

  type TransactionEntry = (Int, Double, Double, Long, String, Long, Long, Long, Long)
  lazy val transaction = TableQuery[Transactions]
  val db = Database.forURL("jdbc:sqlite:" + dbFilename + ".db", driver = "org.sqlite.JDBC")

  /**
   * create table if it does not exist
   */
  def init() = {
    db.withDynSession {
      if (MTable.getTables("TRANSACTIONS").list.isEmpty) {
        transaction.ddl.create
      }
    }
  }

  /**
   * save single entry
   */
  def save(newTransaction: Transaction) = {
    db.withDynSession {
      transaction +=(1, newTransaction.price, newTransaction.quantity, newTransaction.timestamp, newTransaction.currency.toString, newTransaction.buyerId, newTransaction.buyOrderId, newTransaction.sellerId, newTransaction.sellOrderId) // AutoInc are implicitly ignored
    }
  }

  /**
   * save entries
   */
  def save(ts: List[Transaction]) = {
    db.withDynSession {
      transaction ++= ts.toIterable.map { x => (1, x.price, x.quantity, x.timestamp, x.currency.toString, x.buyerId, x.buyOrderId, x.sellerId, x.sellOrderId)}
    }
  }

  /**
   * load entry with id
   */
  def loadSingle(id: Int): Transaction = {
    db.withDynSession {
      val r = transaction.filter(_.id === id).invoker.firstOption.get
      return Transaction(r._2, r._3, r._4, Currency.withName(r._5), r._6, r._7, r._8, r._9)
    }
  }

  /**
   * load entries with timestamp value between startTime and endTime
   */
  def loadBatch(startTime: Long, endTime: Long): List[Transaction] = {
    var res: List[Transaction] = List()
    db.withDynSession {
      val r = transaction.filter(e => e.timestamp >= startTime && e.timestamp <= endTime).invoker.foreach { r => res = new Transaction(r._2, r._3, r._4, Currency.withName(r._5), r._6, r._7, r._8, r._9) :: res}
    }
    res
  }

  /**
   * delete entry with id
   */
  def deleteSingle(id: Int) = {
    db.withDynSession {
      transaction.filter(_.id === id).delete
    }
  }

  /**
   * delete entries with timestamp values between startTime and endTime
   */
  def deleteBatch(startTime: Long, endTime: Long) = {
    db.withDynSession {
      transaction.filter(e => e.timestamp >= startTime && e.timestamp <= endTime).delete
    }
  }

  /**
   * delete all entries
   */
  def clearAll = {
    db.withDynSession {
      transaction.delete
    }
  }
}