/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.gearpump

import java.time.Instant
import lacasa.Safe

/**
 * Each message contains an immutable timestamp.
 *
 * For example, if you take a picture, the time you take the picture is the
 * message's timestamp.
 *
 * @param msg Accept any type except Null, Nothing and Unit
 */
sealed trait Message {
  def msg: Any
  def timeInMillis: TimeStamp
  def timestamp: Instant
  def copy(msg: Any = msg, timeInMillis: TimeStamp = timeInMillis): Message
}

object Message {
  import org.apache.gearpump
  implicit val ev: Safe[gearpump.Message] = new Safe[gearpump.Message] {}

  /**
   * Instant.EPOCH is used for default timestamp
   *
   * @param msg Accept any type except Null, Nothing and Uni
   */
  def apply[T: Safe](msg: T): gearpump.Message = {
    new Message(msg)
  }

  /**
   * @param msg Accept any type except Null, Nothing and Unit
   * @param timestamp timestamp cannot be larger than Instant.ofEpochMilli(Long.MaxValue)
   */
  def apply[T: Safe](msg: T, timestamp: Instant): gearpump.Message = {
    new Message(msg, timestamp)
  }

  def apply[T: Safe](msg: T, timeInMillis: TimeStamp): gearpump.Message = {
    new Message(msg, timeInMillis)
  }

  def unapply(msg: gearpump.Message): Option[(Any, TimeStamp)] = {
    Some((msg.msg, msg.timeInMillis))
  }

  private case class Message (msg: Any, timeInMillis: TimeStamp) extends gearpump.Message {

    /**
    * @param msg Accept any type except Null, Nothing and Unit
    * @param timestamp timestamp cannot be larger than Instant.ofEpochMilli(Long.MaxValue)
    */
    def this(msg: Any, timestamp: Instant) = {
      this(msg, timestamp.toEpochMilli)
    }

    /**
    * Instant.EPOCH is used for default timestamp
    *
    * @param msg Accept any type except Null, Nothing and Uni
    */
    def this(msg: Any) = {
      this(msg, Instant.EPOCH)
    }

    def timestamp: Instant = {
      Instant.ofEpochMilli(timeInMillis)
    }

    def copy(msg: Any = msg, timeInMillis: TimeStamp = timeInMillis): gearpump.Message = this.copy(msg, timeInMillis)
  }
}
