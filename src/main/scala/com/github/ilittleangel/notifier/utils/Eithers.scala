package com.github.ilittleangel.notifier.utils

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object Eithers {

  val separator = "; "
  val concatStrings: (String, String) => String = (s1, s2) => s1 + separator + s2

  implicit private class EitherOps(self: Either[String, String]) {
    def applyAndForceLeft(s: String, f: (String, String) => String): Left[String, Nothing] = {
      self match {
        case Right(status) => Left(f(status, s))
        case Left(error) => Left(f(error, s))
      }
    }

    def applyAndForceRight(s: String, f: (String, String) => String): Right[Nothing, String] = {
      self match {
        case Right(status) => Right(f(status, s))
        case Left(error) => Right(f(error, s))
      }
    }
  }

  val concatLeft: (Either[String, String], Either[String, String]) => Left[String, Nothing] = (acc, curr) => {
    curr match {
      case Right(currStatus) => acc.applyAndForceLeft(currStatus, concatStrings)
      case Left(currError) => acc.applyAndForceLeft(currError, concatStrings)
    }
  }

  val concatRight: (Either[String, String], Either[String, String]) => Either[String, String] = (acc, curr) => {
    curr match {
      case Right(currStatus) => acc.applyAndForceRight(currStatus, concatStrings)
      case Left(currError) => acc.applyAndForceRight(currError, concatStrings)
    }
  }

  implicit class FuturesEitherOps(future: List[Future[Either[String, String]]]) {
    def reduceEithers(): Future[Either[String, String]] = {
      Future.sequence(future).map { responses =>
        if (responses.exists(_.isLeft)) {
          responses.reduceLeft(concatLeft)
        } else {
          responses.reduceLeft(concatRight)
        }
      }
    }
  }

}
