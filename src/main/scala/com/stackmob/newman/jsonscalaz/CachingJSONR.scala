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

package com.stackmob.newman.jsonscalaz

import net.liftweb.json.JValue
import net.liftweb.json.scalaz.JsonScalaz.{JSONR, Result, fromJSON}
import java.util.concurrent.{Executors, ConcurrentHashMap}
import scalaz.concurrent.{Strategy, Promise}

sealed trait CachingJSONR[T] {
  protected def jsonR: JSONR[T]
  protected def backgroundUpdaterStrategy: Strategy = CachingJSONR.defaultBackgroundUpdateStrategy

  private lazy val valueMap = new ConcurrentHashMap[JValue, Result[T]]

  def readCached(json: JValue) = {
    Option(valueMap.get(json)).getOrElse {
      val res = fromJSON(json)(jsonR)
      //do the map update in the background so there's no thread contention for the actual value.
      //costs some CPU if there's another call to readCached immediately
      Promise(valueMap.put(json, res))(backgroundUpdaterStrategy)
      res
    }
  }

}

object CachingJSONR {
  private[CachingJSONR] lazy val defaultBackgroundUpdateStrategy = {
    Strategy.Executor(Executors.newSingleThreadExecutor())
  }

  /**
   * create a CachingJSONR[T]
   * @param j the JSONR[T] from which to create the CachingJSONR[T]
   * @param b the concurrent Strategy for doing background cache updates
   * @tparam T the type to which the JSONR decodes to
   * @return the new CachingJSONR[T]
   */
  def apply[T](j: JSONR[T])
              (implicit b: Strategy = defaultBackgroundUpdateStrategy): CachingJSONR[T] = new CachingJSONR[T] {
    override protected lazy val jsonR: JSONR[T] = j
    override protected lazy val backgroundUpdaterStrategy = b
  }

  /**
   * alias for apply[T](j)(b)
   */
  def fromJSONR[T](j: JSONR[T])
                  (implicit b: Strategy = defaultBackgroundUpdateStrategy): CachingJSONR[T] = {
    apply(j)(b)
  }
}
