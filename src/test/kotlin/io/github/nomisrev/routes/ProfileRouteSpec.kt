package io.github.nomisrev.routes

import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.plugins.resources.delete
import io.ktor.http.HttpStatusCode

class ProfileRouteSpec :
  StringSpec({
    val validUsername = "username"
    val validEmail = "valid@domain.com"
    val validPw = "123456789"
    val validUsernameFollowed = "username2"
    val validEmailFollowed = "valid2@domain.com"

    "Can unfollow profile" {
      withServer { dependencies ->
        val token = dependencies.userService
          .register(RegisterUser(validUsername, validEmail, validPw))
          .shouldBeRight()
        dependencies.userService
          .register(RegisterUser(validUsernameFollowed, validEmailFollowed, validPw))
          .shouldBeRight()

        val response = delete(ProfilesResource.Follow(username = validUsernameFollowed)) {
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

    "Needs token to unfollow" {
      withServer {
        val response = delete(ProfilesResource.Follow(username = validUsernameFollowed))

        response.status shouldBe HttpStatusCode.Unauthorized
      }
    }

    "Username invalid to unfollow" {
      withServer {dependencies ->
        val token = dependencies.userService
          .register(RegisterUser(validUsername, validEmail, validPw))
          .shouldBeRight()

        val response = delete(ProfilesResource.Follow(username = validUsernameFollowed)) {
          bearerAuth(token.value)
        }

        response.status shouldBe HttpStatusCode.UnprocessableEntity
      }
    }
  })