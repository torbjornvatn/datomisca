import scala.language.reflectiveCalls

import datomisca._
import Datomic._

import scala.concurrent._
import scala.concurrent.duration.Duration

import java.util.{Date => JDate}

object PersonSchema {
  object ns {
    val person = new Namespace("person") {
      val character = Namespace("person.character")
    }
  }
  val name      = Attribute(ns.person / "name",      SchemaType.string,  Cardinality.one) .withDoc("The name of a person")
  val age       = Attribute(ns.person / "age",       SchemaType.long,    Cardinality.one) .withDoc("The age of a person")
  val birth     = Attribute(ns.person / "birth",     SchemaType.instant, Cardinality.one) .withDoc("The birth date of a person")
  val interests = Attribute(ns.person / "interests", SchemaType.ref,     Cardinality.many).withDoc("The interests of a person")

  val cooking = AddIdent(ns.person.character / "cooking")
  val sports  = AddIdent(ns.person.character / "sports")
  val travel  = AddIdent(ns.person.character / "travel")
  val movies  = AddIdent(ns.person.character / "movies")
  val books   = AddIdent(ns.person.character / "books")

  val txData = Seq(name, age, birth, interests, cooking, sports, travel, movies, books)
}

object Demo {
  import datomisca.executioncontext.ExecutionContextHelper._

  val uri = "datomic:mem://datomisca-demo-fagdag"

  Datomic.createDatabase(uri)

  implicit val conn = Datomic.connect(uri)

  def main(args: Array[String]) {
    val janeId = DId(Partition.USER)
    val jane = Entity.add(janeId)(
      // the keyword ident for PersonSchema.name attribute
      PersonSchema.name.ident        -> "Jane",
      // the keyword constructed from namespaces
      PersonSchema.ns.person / "age" -> 30,
      // a raw keyword
      Datomic.KW(":person/birth")    -> new JDate,
      // The set of references to the 'interests' idents
      PersonSchema.interests.ident -> Set( PersonSchema.movies, PersonSchema.books ))

    val johnId = DId(Partition.USER)
    val john = SchemaEntity.add(johnId)(Props() +
      (PersonSchema.name       -> "John") +
      (PersonSchema.age        -> 31) +
      (PersonSchema.birth      -> new JDate) +
      // Please note that we use Datomic References here
      (PersonSchema.interests -> Set( PersonSchema.sports, PersonSchema.travel )))

    
    println(s"Temporary identity for Jane: $janeId")
    println(s"Temporary identity for John: $johnId\n")

    val schemaFuture = Datomic.transact(PersonSchema.txData)
    val insertFuture = Datomic.transact(jane, john) 
    
    val tx = Await.result(
      for {
        _ <- schemaFuture
        tx <- insertFuture
      } yield tx, Duration("2 seconds")
    )

    println(s"Persisted identity for Jane: ${tx.resolve(janeId)}")
    println(s"Persisted identity for John: ${tx.resolve(johnId)}")
    println()

    val queryFindByName = Query("""
      [
      :find ?e ?name ?age ?birth
      :in $ ?limit
      :where
      [?e :person/name  ?name]
      [?e :person/age   ?age]
      [?e :person/birth ?birth]
      [(< ?age ?limit)]
      ]
      """)

    val results = Datomic.q(queryFindByName, Datomic.database, DLong(32)).toSeq sortBy (_._3.as[Long])

    println(s"""Results:
      |${results.mkString("[\n  ", ",\n  ", "\n]")}
      |""".stripMargin)

    results foreach {

      case (DLong(eid), DString(qname), DLong(qage), DInstant(qbirth)) =>

        val entity = Datomic.database.entity(eid)

        val name = entity(PersonSchema.name)
        assert(qname == name) 

        val Some(age) = entity.get(PersonSchema.age)
        assert(qage == age)

        val birth     = entity.as[JDate](Datomic.KW(":person/birth"))
        assert(qbirth == birth)

        val interests = entity.read[Set[Keyword]](PersonSchema.interests)
        assert(interests.size == 2)

        println(s"""$name's
          |  age:       $age
          |  birth:     $birth
          |  interests: $interests
          |""".stripMargin)
    } 


    Datomic.shutdown(true)

    defaultExecutorService.shutdownNow()
  }
}
