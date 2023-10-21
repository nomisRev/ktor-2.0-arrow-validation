package io.github.nomisrev.service

import arrow.core.raise.either
import arrow.core.nonEmptyListOf
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.EmptyUpdate
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.InvalidEmail
import io.github.nomisrev.InvalidPassword
import io.github.nomisrev.InvalidUsername
import io.github.nomisrev.SuspendFun
import io.github.nomisrev.UsernameAlreadyExists
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.withDependencies
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.matchers.shouldBe

class UserServiceSpec :
  SuspendFun({
    val validUsername = "username"
    val validEmail = "valid@domain.com"
    val validPw = "123456789"

    "register" - {
      withDependencies {
        "username cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 1 characters)")
          val expected = IncorrectInput(InvalidUsername(errors))
          either {
            register(RegisterUser("", validEmail, validPw))
          } shouldBeLeft expected
        }

        "username longer than 25 chars" {
          val name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
          val errors = nonEmptyListOf("is too long (maximum is 25 characters)")
          val expected = IncorrectInput(InvalidUsername(errors))
          either {
            register(RegisterUser(name, validEmail, validPw))
          } shouldBeLeft expected
        }

        "email cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")
          val expected = IncorrectInput(InvalidEmail(errors))
          either {
            register(RegisterUser(validUsername, "", validPw))
          } shouldBeLeft expected
        }

        "email too long" {
          val email = "${(0..340).joinToString("") { "A" }}@domain.com"
          val errors = nonEmptyListOf("is too long (maximum is 350 characters)")
          val expected = IncorrectInput(InvalidEmail(errors))
          either {
            register(RegisterUser(validUsername, email, validPw))
          } shouldBeLeft expected
        }

        "email is not valid" {
          val email = "AAAA"
          val errors = nonEmptyListOf("'$email' is invalid email")
          val expected = IncorrectInput(InvalidEmail(errors))
          either {
            register(RegisterUser(validUsername, email, validPw))
          } shouldBeLeft expected
        }

        "password cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 8 characters)")
          val expected = IncorrectInput(InvalidPassword(errors))
          either {
            register(RegisterUser(validUsername, validEmail, ""))
          } shouldBeLeft expected
        }

        "password can be max 100" {
          val password = (0..100).joinToString("") { "A" }
          val errors = nonEmptyListOf("is too long (maximum is 100 characters)")
          val expected = IncorrectInput(InvalidPassword(errors))
          either {
            register(RegisterUser(validUsername, validEmail, password))
          } shouldBeLeft expected
        }

        "All valid returns a token" {
          either {
            register(RegisterUser(validUsername, validEmail, validPw))
          }.shouldBeRight()
        }

        "Register twice results in UsernameAlreadyExists" {
          either {
            register(RegisterUser(validUsername, validEmail, validPw))
            register(RegisterUser(validUsername, validEmail, validPw))
          } shouldBeLeft UsernameAlreadyExists(validUsername)
        }
      }
    }

    "login" - {
      withDependencies {
        "email cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")
          val expected = IncorrectInput(InvalidEmail(errors))
          either { login(Login("", validPw)) } shouldBeLeft expected
        }

        "email too long" {
          val email = "${(0..340).joinToString("") { "A" }}@domain.com"
          val errors = nonEmptyListOf("is too long (maximum is 350 characters)")
          val expected = IncorrectInput(InvalidEmail(errors))
          either { login(Login(email, validPw)) } shouldBeLeft expected
        }

        "email is not valid" {
          val email = "AAAA"
          val errors = nonEmptyListOf("'$email' is invalid email")
          val expected = IncorrectInput(InvalidEmail(errors))
          either { login(Login(email, validPw)) } shouldBeLeft expected
        }

        "password cannot be empty" {
          val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 8 characters)")
          val expected = IncorrectInput(InvalidPassword(errors))
          either { login(Login(validEmail, "")) } shouldBeLeft expected
        }

        "password can be max 100" {
          val password = (0..100).joinToString("") { "A" }
          val errors = nonEmptyListOf("is too long (maximum is 100 characters)")
          val expected = IncorrectInput(InvalidPassword(errors))
          either { login(Login(validEmail, password)) } shouldBeLeft expected
        }
      }
    }

    "update" - {
      withDependencies {
        "Update with all null" {
          val token =
            either {
              register(RegisterUser(validUsername, validEmail, validPw))
            }.shouldBeRight()

          either {
            update(Update(token.id(), null, null, null, null, null))
          } shouldBeLeft EmptyUpdate("Cannot update user with ${token.id()} with only null values")
        }
      }
    }
  })

private fun JwtToken.id(): UserId =
  JWT.decodeT(value, JWSHMAC512Algorithm)
    .shouldBeRight { "JWToken $value should be valid JWT but found $it" }
    .jwt
    .claimValueAsLong("id")
    .shouldBeSome { "JWTToken $value should have id but found None" }
    .let(::UserId)
