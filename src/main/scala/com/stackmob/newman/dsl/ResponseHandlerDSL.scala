/**
 * Copyright 2012-2013 StackMob
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stackmob.newman
package dsl

import scalaz._
import Scalaz._
import scalaz.effects.IO
import com.stackmob.newman.response.{HttpResponseCode, HttpResponse}
import response.HttpResponse.JSONParsingError
import java.nio.charset.Charset
import Constants._
import jsonscalaz._

/**
 * Facilitates the handling of various response codes per response, where each handler for a given code may fail or may succeed with
 * a value of some type `Success` or fail with a value of some type `Failure`. `Failure` must have an implicit conversion from Throwable
 * in scope, and defaults to Throwable. `ResponseHandler` is an immutable wrapper; handlers, a `HTTPResponse => Validation[Failure,Success].
 * can be added by using handleCode`, which returns a new instance. After all desired handlers have been added calling `toIO`
 * or using the implicit provided in [[com.stackmob.newman.dsl]] will return an `IO[Validation[Failure,Success]`.
 *
 * For response codes without a "catchall" handler always `scalaz.Failure` with a [[com.stackmob.newman.dsl.UnhandledResponseCode]]
 * as its value.
 *
 * To begin using `ResponseHandler` call [[com.stackmob.newman.request.HttpRequest]]'s `prepare` method and then begin changing
 * calls to `handleCode`.
 *
 * Example:
 *
 * {{{
 *   GET(genURL("cnames", cName))
 *    .addHeaders(acceptHeader)
 *    .prepare
 *    .handleCode(HttpResponseCode.Ok, (resp: HttpResponse) => {
 *      val json = parse(resp.bodyString)
 *      (json \ "app-id", json \ "env") match {
 *        case (JInt(appID), JString(env)) => (appID.longValue(), env.toEnum[EnvironmentType]).some.success[Throwable]
 *         case _ => (UnexpectedLookupBody(resp.bodyString): Throwable).fail[Option[(Long,EnvironmentType)]]
 *       }
 *     })
 *    .handleCode(HttpResponseCode.NotFound, (_: HttpResponse) => none.success)
 *    .handleCode(HttpResponseCode.InternalServerError, (resp: HttpResponse) => {
 *      (new Exception("server error: %s" format resp.bodyString)).fail
 *    })
 *    .toIO // there's also an implicit called ResponseHandlerToResponse in the `com.stackmob.newman.dsl` package
 * }}}
 *
 * If the response is expected to be considered a success when it its code is `200 OK`
 * and is expected to have a body whose content can is valid JSON `expectJSONBody`
 * can be called given there is an implicit [[net.liftweb.json.scalaz.Types.JSONR]] in scope for `T`.
 * See `expectJSONBody` for more info.
 *
 * Example:
 *
 * {{{
 *   implicit def jsonrForBody: JSONR[(Long,EnvironmentType) = ...
 *   GET(genURL("cnames", cName))
 *    .addHeaders(acceptHeader)
 *    .prepare
 *    .expectJSONBody[(Long,EnvironmentType)] // if called in any other position in the chain the type parameter should not need to be specified
 *    .handleCode(HttpResponseCode.NotFound, (_: HttpResponse) => ...)
 *
 * }}}
 *
 * If the response is expected to be considered a success when it its code is `204 No Content`,
 * `expectNoContent(t: T)` can be called to return a `scalaz.Success` when the `IO` is performed.
 * See `expectNoContent` for more info.
 *
 */

trait ResponseHandlerDSL {
  case class ResponseHandler[Failure, Success](handlers: List[(HttpResponseCode => Boolean, HttpResponse => Validation[Failure, Success])],
                                               respIO: IO[HttpResponse])(implicit errorConv: Throwable => Failure) {

    /**
     * Adds a handler (a function that is called when the code matches the given function) and returns a new ResponseHandler
     * @param check response code this handler is for
     * @param handler function to call when response with given code is encountered
     * @return
     */
    def handleCodesSuchThat(check: HttpResponseCode => Boolean)
                           (handler: HttpResponse => Validation[Failure, Success]): ResponseHandler[Failure, Success] = {
      copy(handlers = (check, handler) :: handlers)
    }

    /**
     * Adds a handler (a function that is called when the given code is matched) and returns a new ResponseHandler
     * @param code response code this handler is for
     * @param handler function to call when response with given code is encountered
     * @return
     */
    def handleCode(code: HttpResponseCode)(handler: HttpResponse => Validation[Failure, Success]): ResponseHandler[Failure, Success] = {
      handleCodesSuchThat({c: HttpResponseCode => c === code})(handler)
    }

    /**
     * Adds a handler (a function that is called when any of the given codes are matched) and returns a new ResponseHandler
     * @param codes response code this handler matches
     * @param handler function to call when response with given code is encountered
     * @return
     */
    def handleCodes(codes: Seq[HttpResponseCode])(handler: HttpResponse => Validation[Failure, Success]): ResponseHandler[Failure, Success] = {
      handleCodesSuchThat(codes.contains(_))(handler)
    }

    /**
     * Adds a handler that expects the specified response code and a JSON body readable by
     * a JSONR for the type of this ResponseHandler. A Response can only return
     * one successful type per request so multiple calls to this method should not be
     * made, however, if there are there is no effect on the handling of the response.
     * @return a new [[com.stackmob.newman.dsl.ResponseHandler]]
     */
    def expectJSONBody(code: HttpResponseCode)(implicit reader: CachingJSONR[Success], charset: Charset = UTF8Charset): ResponseHandler[Failure, Success] = {
      handleJSONBody[Success](code)(_.success[Failure])
    }

    /**
     * Adds a handler that expects the specified response code and a
     * JSON body readable by a JSONR for the type of this
     * ResponseHandler, which is then transformed by the supplied
     * function. A Response can only return one successful type per
     * request so multiple calls to this method should not be made,
     * however, if there are there is no effect on the handling of the
     * response.  @return a new [[com.stackmob.newman.dsl.ResponseHandler]]
     */
    def handleJSONBody[S](code: HttpResponseCode)
                         (handler: S => Validation[Failure, Success])
                         (implicit reader: CachingJSONR[S], charset: Charset = UTF8Charset): ResponseHandler[Failure, Success] = {
      handleCode(code)((resp: HttpResponse) => resp.bodyAs[S].mapFailure { t =>
        errorConv(JSONParsingError(t): Throwable): Failure
      }.flatMap(handler))
    }

    /**
     * Adds a handler that expects a 204 response. If encountered, the value passed to this method
     * will be returned when the handler is sealed and the IO is performed. This method should
     * only be called once per response but multiple calls have no effect
     * @param successValue - the value to return successfully when a 204 is encountered
     * @return a new ResponseHandler
     */
    def expectNoContent(successValue: Success): ResponseHandler[Failure, Success] = {
      handleCode(HttpResponseCode.NoContent)((_: HttpResponse) => successValue.success)
    }

    /**
     * Provide a default handler for all unhandled status codes. Must be the last handler in the chain
     */
    def default(handler: HttpResponse => Validation[Failure, Success]): IOValidation[Failure, Success] = {
      respIO.map { response =>
        handlers.reverse.find(_._1(response.code)).map(_._2 apply response) | handler(response)
      }.except(t => errorConv(t).fail[Success].pure[IO])
    }

    def toIO: IOValidation[Failure, Success] = {
      default { resp =>
        errorConv(UnhandledResponseCode(resp.code, resp.bodyString)).fail[Success]
      }
    }
  }

  trait IOResponseW {

    protected def value: IO[HttpResponse]

    private def emptyHandlerList[Failure,Success] = List[(HttpResponseCode => Boolean, HttpResponse => Validation[Failure, Success])]()

    def handleCodesSuchThat[Failure, Success](check: HttpResponseCode => Boolean)
                                             (handler: HttpResponse => Validation[Failure, Success])
                                             (implicit errorConv: Throwable => Failure): ResponseHandler[Failure, Success] = {
      ResponseHandler(emptyHandlerList[Failure,Success], value).handleCodesSuchThat(check)(handler)
    }

    def handleCode[Failure, Success](code: HttpResponseCode)
                                    (handler: HttpResponse => Validation[Failure, Success])
                                    (implicit errorConv: Throwable => Failure): ResponseHandler[Failure, Success] = {
      ResponseHandler(emptyHandlerList[Failure,Success], value).handleCode(code)(handler)
    }

    def handleCodes[Failure, Success](codes: Seq[HttpResponseCode])
                                     (handler: HttpResponse => Validation[Failure, Success])
                                     (implicit errorConv: Throwable => Failure): ResponseHandler[Failure, Success] = {
      ResponseHandler(emptyHandlerList[Failure,Success], value).handleCodes(codes)(handler)
    }

    def expectJSONBody[Failure, Success](code: HttpResponseCode)
                                        (implicit reader: CachingJSONR[Success],
                                         charset: Charset = UTF8Charset,
                                         errorConv: Throwable => Failure): ResponseHandler[Failure, Success] = {
      ResponseHandler(emptyHandlerList[Failure,Success], value).expectJSONBody(code)(reader, charset)
    }

    def handleJSONBody[Failure, S, Success](code: HttpResponseCode)
                                           (handler: S => Validation[Failure, Success])
                                           (implicit reader: CachingJSONR[S],
                                            charset: Charset = UTF8Charset,
                                            errorConv: Throwable => Failure): ResponseHandler[Failure, Success] = {
      ResponseHandler(emptyHandlerList[Failure,Success], value).handleJSONBody(code)(handler)(reader, charset)
    }

    def expectNoContent[Failure, Success](successValue: Success)
                                         (implicit errorConv: Throwable => Failure): ResponseHandler[Failure, Success] = {
      ResponseHandler(emptyHandlerList[Failure,Success], value).expectNoContent(successValue)
    }
  }

  case class UnhandledResponseCode(code: HttpResponseCode, body: String)
    extends Exception("unhandled response code %d and body %s".format(code.code, body))
}
