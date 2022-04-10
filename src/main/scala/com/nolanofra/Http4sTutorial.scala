package com.nolanofra

import cats._
import cats.effect._
import cats.implicits._
import org.http4s.circe._
import org.http4s._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl._
import org.http4s.dsl.impl._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.server._

import java.time.Year
import java.util.UUID
import scala.collection.mutable
import scala.util.Try

object Http4sTutorial extends IOApp {

  type Actor = String
  case class Movie(id: String, title: String, year: Int, actors: List[Actor], director: String)

  case class Director(firstName: String, lastName: String) {
    override def toString = s"$firstName $lastName"
  }

  case class DirectorDetails(firstName: String, lastName: String, genre: String)

  val directorDetailsDB: mutable.Map[Director, DirectorDetails] =
    mutable.Map(Director("Zack", "Snyder") -> DirectorDetails("Zack", "Snyder", "superhero"))

  val snjl: Movie                                               = Movie(
    "6bcbca1e-efd3-411d-9f7c-14b872444fce",
    "Zack Snyder's Justice League",
    2021,
    List("Henry Cavill", "Gal Godot", "Ezra Miller", "Ben Affleck", "Ray Fisher", "Jason Momoa"),
    "Zack Snyder"
  )

  // internal database
  val movieDB: Map[String, Movie] = Map(snjl.id -> snjl)

  //business logic
  private def findMovieById(movieId: UUID) =
    movieDB.get(movieId.toString)

  private def findMoviesByDirector(director: String): List[Movie] =
    movieDB.values.filter(_.director == director).toList

  /*
  - GET all movies for a director under a given year
  - GET all actors for a movie
  - GET details about a director
  - POST add a new director
   */

  implicit val yearQueryParamDecoder: QueryParamDecoder[Year] =
    QueryParamDecoder[Int].emap(year =>
      Try(Year.of(year)).toEither
        .leftMap(e => ParseFailure(e.getMessage, e.getMessage))
    )

  object DirectorQueryParameterMatcher extends QueryParamDecoderMatcher[String]("director")
  object YearQueryParameterMatcher     extends OptionalValidatingQueryParamDecoderMatcher[Year]("year")

  //GET /movies?director=Zack&year=2021
  def movieRoutes[F[_]: Monad]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / "movies" :? DirectorQueryParameterMatcher(director: String) +& YearQueryParameterMatcher(
            maybeYear: Option[year]
          ) =>
        val moviesByDirector = findMoviesByDirector(director)
        maybeYear match {
          case Some(validatedYear) =>
            validatedYear.fold(
              _ => BadRequest("The year was not well formatted"),
              year => {
                val moviesByDirectorAndYear = moviesByDirector.filter(_.year.equals(year.getValue))
                Ok(moviesByDirectorAndYear.asJson)
              }
            )

          case None => Ok(moviesByDirector.asJson)
        }
      case GET -> Root / "movies" / UUIDVar(movieId) / "actors" =>
        findMovieById(movieId).map(_.actors) match {
          case Some(actors) => Ok(actors.asJson)
          case _            => NotFound(s"No movie with id  $movieId has been found")
        }
    }
  }

  object DirectorPath {

    def unapply(str: String): Option[Director] =
      Try {
        val tokens = str.split(" ")
        Director(tokens(0), tokens(1))
      }.toOption
  }

  def directorRoutes[F[_]: Monad]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] { case GET -> Root / "director" / DirectorPath(director) =>
      directorDetailsDB.get(director) match {
        case Some(directorDetails) => Ok(directorDetails.asJson)
        case _                     => NotFound(s"No director '$director' found")
      }
    }
  }

  def allRoutes[F[_]: Monad]: HttpRoutes[F]      = movieRoutes[F] <+> directorRoutes[F] //cats syntax semigroupk
  def allRoutesComplete[F[_]: Monad]: HttpApp[F] = allRoutes[F].orNotFound

  override def run(args: List[String]): IO[ExitCode] = {
    val apis = Router(
      "/api"       -> movieRoutes[IO],
      "/api/admin" -> directorRoutes[IO]
    ).orNotFound

    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(allRoutesComplete[IO]) // alternative: apis
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)

  }
}
