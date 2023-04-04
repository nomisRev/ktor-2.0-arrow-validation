package io.github.nomisrev.repo

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.github.nomisrev.DomainError
import io.github.nomisrev.PasswordNotMatched
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.UsernameAlreadyExists
import io.github.nomisrev.service.UserInfo
import io.github.nomisrev.sqldelight.UsersQueries
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

@JvmInline
value class UserId(val serial: Long)

interface UserPersistence {
  /** Creates a new user in the database, and returns the [UserId] of the newly created user */
  context(Raise<DomainError>)
  suspend fun insert(username: String, email: String, password: String): UserId

  /** Verifies is a password is correct for a given email */
  context(Raise<DomainError>)
  suspend fun verifyPassword(
    email: String,
    password: String
  ): Pair<UserId, UserInfo>

  /** Select a User by its [UserId] */
  context(Raise<DomainError>)
  suspend fun select(userId: UserId): UserInfo

  /** Select a User by its username */
  context(Raise<DomainError>)
  suspend fun select(username: String): UserInfo

  context(Raise<DomainError>)
  @Suppress("LongParameterList")
  suspend fun update(
    userId: UserId,
    email: String?,
    username: String?,
    password: String?,
    bio: String?,
    image: String?
  ): UserInfo
}

/** UserPersistence implementation based on SqlDelight and JavaX Crypto */
fun userPersistence(
  usersQueries: UsersQueries,
  defaultIterations: Int = 64000,
  defaultKeyLength: Int = 512,
  secretKeysFactory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
) = object : UserPersistence {

  context(Raise<DomainError>)
  override suspend fun insert(
    username: String,
    email: String,
    password: String
  ): UserId {
    val salt = generateSalt()
    val key = generateKey(password, salt)
    return Either.catchOrThrow<PSQLException, UserId> {
      usersQueries.create(salt, key, username, email)
    }
      .mapLeft { psqlException ->
        if (psqlException.sqlState == PSQLState.UNIQUE_VIOLATION.state)
          UsernameAlreadyExists(username)
        else throw psqlException
      }.bind()
  }

  context(Raise<DomainError>)
  override suspend fun verifyPassword(
    email: String,
    password: String
  ): Pair<UserId, UserInfo> {
    val (userId, username, salt, key, bio, image) =
      ensureNotNull(usersQueries.selectSecurityByEmail(email).executeAsOneOrNull()) {
        UserNotFound("email=$email")
      }

    val hash = generateKey(password, salt)
    ensure(hash contentEquals key) { PasswordNotMatched }
    return Pair(userId, UserInfo(email, username, bio, image))
  }

  context(Raise<DomainError>)
  override suspend fun select(userId: UserId): UserInfo {
    val userInfo =
      usersQueries
        .selectById(userId) { email, username, _, _, bio, image ->
          UserInfo(email, username, bio, image)
        }
        .executeAsOneOrNull()
    return ensureNotNull(userInfo) { UserNotFound("userId=$userId") }
  }

  context(Raise<DomainError>)
  override suspend fun select(username: String): UserInfo {
    val userInfo = usersQueries.selectByUsername(username, ::UserInfo).executeAsOneOrNull()
    return ensureNotNull(userInfo) { UserNotFound("username=$username") }
  }

  context(Raise<DomainError>)
  override suspend fun update(
    userId: UserId,
    email: String?,
    username: String?,
    password: String?,
    bio: String?,
    image: String?
  ): UserInfo {
    val info =
      usersQueries.transactionWithResult {
        usersQueries.selectById(userId).executeAsOneOrNull()
          ?.let { (oldEmail, oldUsername, salt, oldPassword, oldBio, oldImage) ->
            val newPassword = password?.let { generateKey(it, salt) } ?: oldPassword
            val newEmail = email ?: oldEmail
            val newUsername = username ?: oldUsername
            val newBio = bio ?: oldBio
            val newImage = image ?: oldImage
            usersQueries.update(newEmail, newUsername, newPassword, newBio, newImage, userId)
            UserInfo(newEmail, newUsername, newBio, newImage)
          }
      }
    return ensureNotNull(info) { UserNotFound("userId=$userId") }
  }

  private fun generateSalt(): ByteArray = UUID.randomUUID().toString().toByteArray()

  private fun generateKey(password: String, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, defaultIterations, defaultKeyLength)
    return secretKeysFactory.generateSecret(spec).encoded
  }
}

private fun UsersQueries.create(
  salt: ByteArray,
  key: ByteArray,
  username: String,
  email: String
): UserId =
  insertAndGetId(
    username = username,
    email = email,
    salt = salt,
    hashed_password = key,
    bio = "",
    image = ""
  ).executeAsOne()
