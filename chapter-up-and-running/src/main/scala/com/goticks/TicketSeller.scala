package com.goticks

import akka.actor.{Actor, Props, PoisonPill}
object TicketSeller {
  def props(event: String): Props = Props(new TicketSeller(event))

  /// メッセージ
  // TicketSellerにチケットを追加する
  case class Add(tickets: Vector[Ticket])
  // TicketSellerからチケットを購入する
  case class Buy(tickets: Int)
  // チケット
  case class Ticket(id: Int)
  // イベントのチケット一覧
  case class Tickets(
      event: String,
      entries: Vector[Ticket] = Vector.empty[Ticket]
  )
  // イベントのチケット残数
  case object GetEvent
  // イベントをキャンセルする
  case object Cancel
}

class TicketSeller(event: String) extends Actor {
  import TicketSeller._

  // チケットのリスト
  var tickets = Vector.empty[Ticket]

  def receive: Receive = {
    // Addメッセージを受け取ると、既存のチケットリストに新しいチケットを加える
    case Add(newTickets) => tickets = tickets ++ newTickets
    // リストからチケットを必要枚数分だけ取り出し、
    // チケットが十分あればチケットを含むTicketsメッセージを返す。
    // 足りなければ空のTicketsメッセージを返す。
    case Buy(nrOfTickets) =>
      val entries = tickets.take(nrOfTickets)
      if (entries.size >= nrOfTickets) {
        sender() ! Tickets(event, entries)
        tickets = tickets.drop(nrOfTickets)
      } else {
        sender() ! Tickets(event)
      }
    case GetEvent => sender() ! Some(BoxOffice.Event(event, tickets.size))
    case Cancel =>
      sender() ! Some(BoxOffice.Event(event, tickets.size))
      self ! PoisonPill
  }
}
