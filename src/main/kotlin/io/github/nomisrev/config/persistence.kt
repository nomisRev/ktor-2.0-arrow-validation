package io.github.nomisrev.config

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.resource
import arrow.fx.coroutines.fromCloseable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.sqldelight.SqlDelight
import io.github.nomisrev.sqldelight.Users
import javax.sql.DataSource

fun hikari(config: Config.DataSource): Resource<HikariDataSource> =
  Resource.fromCloseable {
    HikariDataSource(
      HikariConfig().apply {
        jdbcUrl = config.url
        username = config.username
        password = config.password
        driverClassName = config.driver
      }
    )
  }

fun sqlDelight(dataSource: DataSource): Resource<SqlDelight> = resource {
  val driver = Resource.fromCloseable(dataSource::asJdbcDriver).bind()
  SqlDelight.Schema.create(driver)
  SqlDelight(driver, Users.Adapter(userIdAdapter))
}

private val userIdAdapter = columnAdapter(::UserId, UserId::serial)

private inline fun <A : Any, B> columnAdapter(
  crossinline decode: (databaseValue: B) -> A,
  crossinline encode: (value: A) -> B
): ColumnAdapter<A, B> =
  object : ColumnAdapter<A, B> {
    override fun decode(databaseValue: B): A = decode(databaseValue)
    override fun encode(value: A): B = encode(value)
  }
