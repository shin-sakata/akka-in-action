package com.goticks

import spray.json._

// 初期枚数分のイベントチケットを保持したメッセージ
case class EventDescription(tickets: Int) {
  require(tickets > 0)
}

// 必要なチケットの枚数を保持したメッセージ
case class TicketRequest(tickets: Int) {
  require(tickets > 0)
}

// エラーを保持したメッセージ
case class Error(message: String)

trait EventMarshalling extends DefaultJsonProtocol {
  import BoxOffice._

  implicit val eventDescriptionFormat = jsonFormat1(EventDescription)
  implicit val eventFormat = jsonFormat2(Event)
  implicit val eventsFormat = jsonFormat1(Events)
  implicit val ticketRequestFormat = jsonFormat1(TicketRequest)
  implicit val ticketFormat = jsonFormat1(TicketSeller.Ticket)
  implicit val ticketsFormat = jsonFormat2(TicketSeller.Tickets)
  implicit val errorFormat = jsonFormat1(Error)
}
