package ch.epfl.ts.engine

import scala.collection.mutable.TreeSet
import ch.epfl.ts.data.{ Message, Transaction, Order, LimitAskOrder, LimitBidOrder, MarketAskOrder, MarketBidOrder, DelOrder }
import ch.epfl.ts.data.Currency._

/**
 * represents the cost of placing a bid and market order
 */
case class Commission(limitOrder: Double, marketOrder: Double)

/**
 * Market Simulator Configuration class. Defines the orders books priority implementation, the matching function and the commission costs of limit and market orders.
 * Extend this class and override its method(s) to customize Market rules for specific markets.
 *
 */
class MarketRules {
  val commission = Commission(0, 0)

  // when used on TreeSet, head() and iterator() provide increasing order
  def asksOrdering = new Ordering[LimitAskOrder] {
    def compare(first: LimitAskOrder, second: LimitAskOrder): Int =
      if (first.price > second.price) 1 else if (first.price < second.price) -1 else {
        if (first.timestamp < second.timestamp) 1 else if (first.timestamp > second.timestamp) -1 else 0
      }
  }

  // when used on TreeSet, head() and iterator() provide decreasing order
  def bidsOrdering = new Ordering[LimitBidOrder] {
    def compare(first: LimitBidOrder, second: LimitBidOrder): Int =
      if (first.price > second.price) -1 else if (first.price < second.price) 1 else {
        if (first.timestamp < second.timestamp) 1 else if (first.timestamp > second.timestamp) -1 else 0
      }
  }

  def alwaysTrue(a: Double, b: Double) = true

  def matchingFunction(newOrder: Order, newOrdersBook: TreeSet[Order], bestMatchsBook: TreeSet[Order], send: Message => Unit, matchExists: (Double, Double) => Boolean = alwaysTrue, oldTradingPrice: Double, enqueueOrElse: (Order, TreeSet[Order]) => Unit): Double = {
    println("Market: got new order: " + newOrder)

    if (bestMatchsBook.isEmpty) {
      println("Market: matching orders book empty")
      enqueueOrElse(newOrder, newOrdersBook)
      return oldTradingPrice
    } else {

      val bestMatch = bestMatchsBook.head
      // check if a matching order exists when used with a limit order, if market order: matchExists = true
      if (matchExists(bestMatch.price, newOrder.price)) {

        // perfect match
        if (bestMatch.volume == newOrder.volume) {
          println("Market: volume match with " + bestMatch)
          // send transaction
          if (bestMatch.isInstanceOf[LimitBidOrder]) {
            send(Transaction(bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.uid, bestMatch.oid, newOrder.uid, newOrder.oid))
          } else if (bestMatch.isInstanceOf[LimitAskOrder]) {
            send(Transaction(bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, newOrder.uid, newOrder.oid, bestMatch.uid, bestMatch.oid))
          } else {
            println("error")
          }
          // remove matched order
          println("removing order: " + bestMatch + " from bestMatch orders book.")
          bestMatchsBook -= bestMatch
          // send diff
          send(DelOrder(bestMatch.oid, bestMatch.uid, newOrder.timestamp, DEF, DEF, 0.0, 0.0))
          // update price
          return bestMatch.price

        } else if (bestMatch.volume > newOrder.volume) {
          println("Market: matched with " + bestMatch + ", new order volume inferior - cutting matched order.")
          // remove matched order and reinput it with updated volume
          println("removing order: " + bestMatch + " from match orders book. enqueuing same order with " + (bestMatch.volume - newOrder.volume) + " volume.")
          bestMatchsBook -= bestMatch
          // send diff
          send(DelOrder(bestMatch.oid, bestMatch.uid, newOrder.timestamp, DEF, DEF, 0.0, 0.0))
          if (bestMatch.isInstanceOf[LimitBidOrder]) {
            send(Transaction(bestMatch.price, newOrder.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.uid, bestMatch.oid, newOrder.uid, newOrder.oid))
            bestMatchsBook += LimitBidOrder(bestMatch.oid, bestMatch.uid, bestMatch.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.volume - newOrder.volume, bestMatch.price)
            // send diff
            send(LimitBidOrder(bestMatch.oid, bestMatch.uid, bestMatch.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.volume - newOrder.volume, bestMatch.price))
          } else if (bestMatch.isInstanceOf[LimitAskOrder]) {
            send(Transaction(bestMatch.price, newOrder.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, newOrder.uid, newOrder.oid, bestMatch.uid, bestMatch.oid))
            bestMatchsBook += LimitAskOrder(bestMatch.oid, bestMatch.uid, bestMatch.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.volume - newOrder.volume, bestMatch.price)
            // send diff
            send(LimitAskOrder(bestMatch.oid, bestMatch.uid, bestMatch.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.volume - newOrder.volume, bestMatch.price))
          } else {
            // error
            println("MarketRules: casting error")
          }

          // update price
          return bestMatch.price
        } else {
          println("Market: matched with " + bestMatch + ", new order volume superior - reiterate")
          // remove matched ask order
          println("removing order: " + bestMatch + " from match orders book.")
          bestMatchsBook -= bestMatch
          // send diff
          send(DelOrder(bestMatch.uid, bestMatch.oid, bestMatch.timestamp, DEF, DEF, 0.0, 0.0))
          // call handleNewOrder on bid with updated volume
          if (newOrder.isInstanceOf[LimitBidOrder]) {
            // create transaction
            send(Transaction(bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, newOrder.uid, newOrder.oid, bestMatch.uid, bestMatch.oid))
            matchingFunction(LimitBidOrder(newOrder.oid, newOrder.uid, newOrder.timestamp, newOrder.whatC, newOrder.withC, newOrder.volume - bestMatch.volume, bestMatch.price), newOrdersBook.asInstanceOf[TreeSet[Order]], bestMatchsBook, send, matchExists, oldTradingPrice, enqueueOrElse)
          } else if (newOrder.isInstanceOf[LimitAskOrder]) {
            // create transaction
            send(Transaction(bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.uid, bestMatch.oid, newOrder.uid, newOrder.oid))
            matchingFunction(LimitAskOrder(newOrder.oid, newOrder.uid, newOrder.timestamp, newOrder.whatC, newOrder.withC, newOrder.volume - bestMatch.volume, bestMatch.price), newOrdersBook.asInstanceOf[TreeSet[Order]], bestMatchsBook, send, matchExists, oldTradingPrice, enqueueOrElse)
          } else if (newOrder.isInstanceOf[MarketBidOrder]) {
            // create transaction
            send(Transaction(bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, newOrder.uid, newOrder.oid, bestMatch.uid, bestMatch.oid))
            matchingFunction(MarketBidOrder(newOrder.oid, newOrder.uid, newOrder.timestamp, newOrder.whatC, newOrder.withC, newOrder.volume - bestMatch.volume, newOrder.price), newOrdersBook.asInstanceOf[TreeSet[Order]], bestMatchsBook, send, matchExists, oldTradingPrice, enqueueOrElse)
          } else if (newOrder.isInstanceOf[MarketAskOrder]) {
            // create transaction
            send(Transaction(bestMatch.price, bestMatch.volume, newOrder.timestamp, bestMatch.whatC, bestMatch.withC, bestMatch.uid, bestMatch.oid, newOrder.uid, newOrder.oid))
            matchingFunction(MarketAskOrder(newOrder.oid, newOrder.uid, newOrder.timestamp, newOrder.whatC, newOrder.withC, newOrder.volume - bestMatch.volume, newOrder.price), newOrdersBook.asInstanceOf[TreeSet[Order]], bestMatchsBook, send, matchExists, oldTradingPrice, enqueueOrElse)
          } else {
            println("MarketRules: casting error")
          }
          // update price
          return bestMatch.price
        }
        // no match found
      } else {
        println("Market: no match found - enqueuing")
        // enqueue
        enqueueOrElse(newOrder, newOrdersBook.asInstanceOf[TreeSet[Order]])
        return oldTradingPrice
      }
    }
  }
}



/**
 * @param bidOrdersBookPriority
 *  Ordering used by the bid orders book
 * @param askOrdersBookPriority
 *  Ordering used by the ask orders book
 * @param matchingFunction
 *
 */
//case class MarketRules(bidOrdersBookPriority: Ordering[LimitBidOrder] = MarketRules.defaultBidsOrdering, askOrdersBookPriority: Ordering[LimitAskOrder] = MarketRules.defaultAsksOrdering, matchingFunction: (Order, TreeSet[Order], TreeSet[Order], Message => Unit, (Double, Double) => Boolean, Double, (Order, TreeSet[Order]) => Unit) => Double = MarketRules.defaultMatchingFunction, commission: Commission = MarketRules.noCommission) {
//
//}