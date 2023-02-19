package io.github.nomisrev.repo

import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.continuations.ensureNotNull
import at.favre.lib.crypto.bcrypt.BCrypt
import io.github.nomisrev.DomainError
import io.github.nomisrev.PasswordNotMatched
import io.github.nomisrev.Unexpected
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.UsernameAlreadyExists
import io.github.nomisrev.service.UserInfo
import io.github.nomisrev.sqldelight.UsersQueries
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

@JvmInline value class UserId(val serial: Long)

interface UserPersistence {
  /** Creates a new user in the database, and returns the [UserId] of the newly created user */
  suspend fun insert(username: String, email: String, password: String): Either<DomainError, UserId>

  /** Verifies is a password is correct for a given email */
  suspend fun verifyPassword(
    email: String,
    password: String
  ): Either<DomainError, Pair<UserId, UserInfo>>

  /** Select a User by its [UserId] */
  suspend fun select(userId: UserId): Either<DomainError, UserInfo>

  /** Select a User by its username */
  suspend fun select(username: String): Either<DomainError, UserInfo>

  @Suppress("LongParameterList")
  suspend fun update(
    userId: UserId,
    email: String?,
    username: String?,
    password: String?,
    bio: String?,
    image: String?
  ): Either<DomainError, UserInfo>
}

/** UserPersistence implementation based on SqlDelight and JavaX Crypto */
fun userPersistence(usersQueries: UsersQueries, rounds: Int = 10) =
  object : UserPersistence {

    override suspend fun insert(
      username: String,
      email: String,
      password: String
    ): Either<DomainError, UserId> {
      val hash = BCrypt.withDefaults().hash(rounds, password.toByteArray())
      return Either.catch { usersQueries.create(hash, username, email) }
        .mapLeft { error ->
          if (error is PSQLException && error.sqlState == PSQLState.UNIQUE_VIOLATION.state) {
            UsernameAlreadyExists(username)
          } else {
            Unexpected("Failed to persist user: $username:$email", error)
          }
        }
    }

    override suspend fun verifyPassword(
      email: String,
      password: String
    ): Either<DomainError, Pair<UserId, UserInfo>> = either {
      val (userId, username, key, bio, image) =
        ensureNotNull(usersQueries.selectSecurityByEmail(email).executeAsOneOrNull()) {
          UserNotFound("email=$email")
        }

      val result = BCrypt.verifyer().verify(password.toByteArray(), key)
      ensure(result.verified) { PasswordNotMatched }
      Pair(userId, UserInfo(email, username, bio, image))
    }

    override suspend fun select(userId: UserId): Either<DomainError, UserInfo> = either {
      val userInfo =
        Either.catch {
            usersQueries
              .selectById(userId) { email, username, _, bio, image ->
                UserInfo(email, username, bio, image)
              }
              .executeAsOneOrNull()
          }
          .mapLeft { e -> Unexpected("Failed to select user with userId: $userId", e) }
          .bind()
      ensureNotNull(userInfo) { UserNotFound("userId=$userId") }
    }

    override suspend fun select(username: String): Either<DomainError, UserInfo> = either {
      val userInfo =
        Either.catch { usersQueries.selectByUsername(username, ::UserInfo).executeAsOneOrNull() }
          .mapLeft { e -> Unexpected("Failed to select user with username: $username", e) }
          .bind()
      ensureNotNull(userInfo) { UserNotFound("username=$username") }
    }

    override suspend fun update(
      userId: UserId,
      email: String?,
      username: String?,
      password: String?,
      bio: String?,
      image: String?
    ): Either<DomainError, UserInfo> = either {
      val info =
        usersQueries.transactionWithResult<UserInfo?> {
          usersQueries.selectById(userId).executeAsOneOrNull()?.let {
            (oldEmail, oldUsername, oldPassword, oldBio, oldImage) ->
            val newPassword =
              password?.let { BCrypt.withDefaults().hash(rounds, password.toByteArray()) }
                ?: oldPassword
            val newEmail = email ?: oldEmail
            val newUsername = username ?: oldUsername
            val newBio = bio ?: oldBio
            val newImage = image ?: oldImage
            usersQueries.update(newEmail, newUsername, newPassword, newBio, newImage, userId)
            UserInfo(newEmail, newUsername, newBio, newImage)
          }
        }
      ensureNotNull(info) { UserNotFound("userId=$userId") }
    }
  }

private fun UsersQueries.create(hash: ByteArray, username: String, email: String): UserId =
  insertAndGetId(username = username, email = email, hashed_password = hash, bio = "", image = "")
    .executeAsOne()
