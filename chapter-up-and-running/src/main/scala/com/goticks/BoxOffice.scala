package com.goticks

import scala.concurrent.Future

import akka.actor._
import akka.util.Timeout

object BoxOffice {
  def props(implicit timeout: Timeout) = Props(new BoxOffice)
  def name = "boxOffice"

  /// メッセージ
  // イベントを作成する
  case class CreateEvent(name: String, tickets: Int)
  // イベントを取得する
  case class GetEvent(name: String)
  // 全てのイベントを取得する
  case object GetEvents
  // イベントのチケットを取得する
  case class GetTickets(event: String, tickets: Int)
  // イベントをキャンセルする
  case class CancelEvent(name: String)

  // イベントを表す
  case class Event(name: String, tickets: Int)
  // イベントのリストを表す
  case class Events(events: Vector[Event])

  // CreateEventに対して応答する
  sealed trait EventResponse
  // イベントが作成されたことを示す
  case class EventCreated(event: Event) extends EventResponse
  // イベントが既に存在することを示す
  case object EventExists extends EventResponse
}

class BoxOffice(implicit timeout: Timeout) extends Actor {
  import BoxOffice._
  import context._

  // contextを使ってTicketSellerを生成する。
  // テスト時にオーバーライドできるように別のメソッドとして定義
  def createTicketSeller(name: String): ActorRef =
    context.actorOf(TicketSeller.props(name), name)

  def receive: Receive = {
    case CreateEvent(name, tickets) =>
      // EventCreatedを作成して返すか、EventExistsを返す
      context.child(name).fold(create())(_ => sender() ! EventExists)
      // TicketSellerを作成し、 作成したTicketSellerにチケットを加えて、
      // EventCreatedで応答するローカルメソッド
      def create(): Unit = {
        val eventTickets = createTicketSeller(name)
        val newTickets = (1 to tickets).map { ticketId =>
          TicketSeller.Ticket(ticketId)
        }.toVector
        eventTickets ! TicketSeller.Add(newTickets)
        sender() ! EventCreated(Event(name, tickets))
      }

    case GetTickets(event, tickets) =>
      context.child(event).fold(notFound())(buy)
      // TicketSellerが見つからない場合は空のTicketsメッセージを送信
      def notFound(): Unit = sender() ! TicketSeller.Tickets(event)
      // 見つかったTicketSellerから購入する
      def buy(child: ActorRef): Unit =
        // forwardを利用するとBoxOfficeではなく、
        // 親のRestApiアクターの代理としてTicketSellerにメッセージを送信できる
        // そのため、TicketSellerの応答は直接RestApiに返される
        child.forward(TicketSeller.Buy(tickets))

    case GetEvent(event) =>
      context.child(event).fold(notFound())(getEvent)
      def notFound() = sender() ! None
      def getEvent(child: ActorRef) = child forward TicketSeller.GetEvent

    case GetEvents =>
      import akka.pattern.ask
      import akka.pattern.pipe
      // askはFutureを返す。これは最終的に何らかの値を含む型
      // getEventsは   Iterable[Future[Option[Event]]]を返し、
      // sequenceは値を Future[Iterable[Option[Event]]]に変換できる
      // pipeは処理の完了時に値をFutureで包んでアクターに送信する
      // この場合のGetEventsメッセージのsenderはRestApiとなる
      pipe(convertToEvents(Future.sequence(getEvents))) to sender()

      // 全てのTicketSellerに対して、
      // それぞれが販売しているチケットのイベントに関する問い合わせを行うローカルメソッドを定義する
      def getEvents: Iterable[Future[Option[Event]]] =
        context.children.map { child =>
          self.ask(GetEvent(child.path.name)).mapTo[Option[Event]]
        }
      // 全てのTicketSellerに問い合わせを行う。
      // GetEventへの問い合わせ(ask)はOption[Event]を返すため、
      // 全てのTicketSellerに対してmapを行った後、Iterable[Option[Event]]によって処理を完了する。
      // このメソッドはIterable[Option[Event]]をIterable[Event]に平坦化し、
      // 空のOptionの結果を除去する。IterableはEventsメッセージに変換される
      def convertToEvents(f: Future[Iterable[Option[Event]]]): Future[Events] =
        f.map(_.flatten).map(l => Events(l.toVector))

    case CancelEvent(event) =>
      def notFound() = sender() ! None
      def cancelEvent(child: ActorRef) = child forward TicketSeller.Cancel
      context.child(event).fold(notFound())(cancelEvent)
  }
}
