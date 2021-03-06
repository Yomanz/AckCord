/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020 Katrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package ackcord.slashcommands.raw

import ackcord.data.raw.RawGuildMember
import ackcord.data.{ApplicationId, DiscordProtocol, GuildId, InteractionType, Permission, TextChannelId, User}
import ackcord.slashcommands.InteractionId
import cats.syntax.all._
import io.circe._
import io.circe.syntax._

trait CommandsProtocol extends DiscordProtocol {
  implicit val applicationCommandCodec: Codec[ApplicationCommand] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val applicationCommandOptionCodec: Codec[ApplicationCommandOption] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val interactionCodec: Codec[Interaction] = Codec.from(
    (c: HCursor) =>
      for {
        id            <- c.get[InteractionId]("id")
        applicationId <- c.get[ApplicationId]("application_id")
        tpe           <- c.get[InteractionType]("type")
        data          <- c.get[Option[ApplicationCommandInteractionData]]("data")
        guildId       <- c.get[Option[GuildId]]("guild_id")
        channelId     <- c.get[TextChannelId]("channel_id")
        member        <- c.get[Option[RawGuildMember]]("member")
        permissions   <- c.downField("member").get[Option[Permission]]("permissions")
        user          <- c.get[Option[User]]("user")
        token         <- c.get[String]("token")
        version       <- c.get[Option[Int]]("version")
      } yield Interaction(
        id,
        applicationId,
        tpe,
        data,
        guildId,
        channelId,
        member,
        permissions,
        user,
        token,
        version
      ),
    (a: Interaction) =>
      Json.obj(
        "id" := a.id,
        "application_id" := a.applicationId,
        "type" := a.tpe,
        "data" := a.data,
        "guild_id" := a.guildId,
        "channel_id" := a.channelId,
        "member" := a.member.map(
          _.asJson.withObject(o => Json.fromJsonObject(o.add("permissions", a.memberPermission.get.asJson)))
        ),
        "user" := a.user,
        "token" := a.token,
        "version" := a.version
      )
  )

  implicit val applicationCommandInteractionDataCodec: Codec[ApplicationCommandInteractionData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val interactionResponseCodec: Codec[InteractionResponse] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val interactionApplicationCommandCallbackDataCodec: Codec[InteractionApplicationCommandCallbackData] =
    derivation.deriveCodec(derivation.renaming.snakeCase, false, None)

  implicit val applicationCommandOptionChoiceCodec: Codec[ApplicationCommandOptionChoice] = Codec.from(
    (c: HCursor) =>
      for {
        name  <- c.get[String]("name")
        value <- c.get[String]("value").map(Left(_)).orElse(c.get[Int]("value").map(Right(_)))
      } yield ApplicationCommandOptionChoice(name, value),
    (a: ApplicationCommandOptionChoice) => Json.obj("name" := a.name, "value" := a.value.fold(_.asJson, _.asJson))
  )

  implicit val applicationCommandInteractionDataOptionCodec: Codec[ApplicationCommandInteractionDataOption] = {
    import ApplicationCommandInteractionDataOption._
    Codec.from(
      (c: HCursor) =>
        for {
          name    <- c.get[String]("name")
          value   <- c.get[Option[Json]]("value")
          options <- c.get[Option[Seq[ApplicationCommandInteractionDataOption]]]("options")
          res <- (value, options) match {
            case (Some(value), None)   => Right(ApplicationCommandInteractionDataOptionWithValue(name, value))
            case (None, Some(options)) => Right(ApplicationCommandInteractionDataOptionWithOptions(name, options))
            case (Some(_), Some(_)) =>
              Left(DecodingFailure("Expected either value or options", c.history))
            case (None, None) =>
              Right(ApplicationCommandInteractionDataOptionWithOptions(name, Nil))
          }
        } yield res,
      {
        case ApplicationCommandInteractionDataOptionWithValue(name, value) => Json.obj("name" := name, "value" := value)
        case ApplicationCommandInteractionDataOptionWithOptions(name, options) =>
          Json.obj("name" := name, "options" := options)
      }
    )
  }
}
object CommandsProtocol extends CommandsProtocol
