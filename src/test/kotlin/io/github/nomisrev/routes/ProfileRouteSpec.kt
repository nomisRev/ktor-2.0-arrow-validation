package io.github.nomisrev.routes

import io.github.nomisrev.env.Dependencies
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class ProfileRouteSpec :
  StringSpec({
    val validUsername = "username"
    val validEmail = "valid@domain.com"
    val validPw = "123456789"
    val validUsernameFollowed = "username2"
    val validEmailFollowed = "valid2@domain.com"

    "Can follow profile" {
      withServer { dependencies ->
        val token =
          dependencies.userService
            .register(RegisterUser(validUsername, validEmail, validPw))
            .shouldBeRight()
        dependencies.userService
          .register(RegisterUser(validUsernameFollowed, validEmailFollowed, validPw))
          .shouldBeRight()

        val response =
          post(ProfilesResource.Follow(username = validUsernameFollowed)) {
            bearerAuth(token.value)
          }

        response.status shouldBe HttpStatusCode.OK
        with(response.body<ProfileWrapper<Profile>>().profile) {
          username shouldBe validUsernameFollowed
          bio shouldBe ""
          image shouldBe ""
          following shouldBe true
        }
      }
    }

    "Can unfollow profile" {
      withServer { dependencies ->
        val token =
          dependencies.userService
            .register(RegisterUser(validUsername, validEmail, validPw))
            .shouldBeRight()
        dependencies.userService
          .register(RegisterUser(validUsernameFollowed, validEmailFollowed, validPw))
          .shouldBeRight()

        val response =
          delete(ProfilesResource.Follow(username = validUsernameFollowed)) {
            bearerAuth(token.value)
          }

        response.status shouldBe HttpStatusCode.OK
        with(response.body<ProfileWrapper<Profile>>().profile) {
          username shouldBe validUsernameFollowed
          bio shouldBe ""
          image shouldBe ""
          following shouldBe false
        }
      }
    }

    "Needs token to follow" {
      withServer {
        val response = post(ProfilesResource.Follow(username = validUsernameFollowed))

        response.status shouldBe HttpStatusCode.Unauthorized
      }
    }

    "Needs token to unfollow" {
      withServer {
        val response = delete(ProfilesResource.Follow(username = validUsernameFollowed))

        response.status shouldBe HttpStatusCode.Unauthorized
      }
    }

    "Username invalid to follow" {
      withServer { dependencies ->
        val token =
          dependencies.userService
            .register(RegisterUser(validUsername, validEmail, validPw))
            .shouldBeRight()

        val response =
          post(ProfilesResource.Follow(username = validUsernameFollowed)) {
            bearerAuth(token.value)
          }

        response.status shouldBe HttpStatusCode.UnprocessableEntity
      }
    }

    "Username invalid to unfollow" {
      withServer { dependencies ->
        val token =
          dependencies.userService
            .register(RegisterUser(validUsername, validEmail, validPw))
            .shouldBeRight()

        val response =
          delete(ProfilesResource.Follow(username = validUsernameFollowed)) {
            bearerAuth(token.value)
          }

        response.status shouldBe HttpStatusCode.UnprocessableEntity
      }
    }

    "Get profile with no following" {
      withServer { dependencies: Dependencies ->
        dependencies.userService
          .register(RegisterUser(validUsername, validEmail, validPw))
          .shouldBeRight()
        val response =
          get(ProfilesResource.Username(username = validUsername)) {
            contentType(ContentType.Application.Json)
          }

        response.status shouldBe HttpStatusCode.OK
        with(response.body<Profile>()) {
          username shouldBe validUsername
          bio shouldBe ""
          image shouldBe ""
          following shouldBe false
        }
      }
    }

    "Get profile invalid username" {
      withServer {
        val response =
          get(ProfilesResource.Username(username = validUsername)) {
            contentType(ContentType.Application.Json)
          }

        response.status shouldBe HttpStatusCode.UnprocessableEntity
        response.body<GenericErrorModel>().errors.body shouldBe
          listOf("User with username=$validUsername not found")
      }
    }

    "Get profile by username missing username" {
      withServer {
        val response =
          get(ProfilesResource.Username(username = " ")) {
            contentType(ContentType.Application.Json)
          }

        response.status shouldBe HttpStatusCode.UnprocessableEntity
        response.body<GenericErrorModel>().errors.body shouldBe
          listOf("Missing username parameter in request")
      }
    }
  })
