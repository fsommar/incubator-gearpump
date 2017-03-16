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

package org.apache.gearpump.cluster.master

import java.util.concurrent.TimeUnit

import akka.actor.Stash
import akka.cluster.Cluster
import akka.cluster.ddata.{LWWMap, LWWMapKey, DistributedData}
import akka.cluster.ddata.Replicator._
import org.apache.gearpump.util.LogUtil
import org.slf4j.Logger
import lacasa.{Box, CanAccess, Safe}
import lacasa.akka.actor.{Actor, ActorRef, OnlyBoxReceive}

import scala.concurrent.TimeoutException
import scala.concurrent.duration.Duration

/**
 * A replicated simple in-memory KV service. The replications are stored on all masters.
 */
class InMemoryKVService extends Actor with Stash with OnlyBoxReceive {
  import org.apache.gearpump.cluster.master.InMemoryKVService._

  private val KV_SERVICE = "gearpump_kvservice"

  private val LOG: Logger = LogUtil.getLogger(getClass)
  private val replicator: ActorRef = DistributedData(context.system).replicator
  private implicit val cluster = Cluster(context.system)

  // Optimize write path, we can tolerate one master down for recovery.
  private val timeout = Duration(15, TimeUnit.SECONDS)
  private val readMajority = ReadMajority(timeout)
  private val writeMajority = WriteMajority(timeout)

  private def groupKey(group: String): LWWMapKey[Any] = {
    LWWMapKey[Any](KV_SERVICE + "_" + group)
  }

  override def receive(box: Box[Any])(implicit acc: CanAccess { type C = box.C }) = {
    box.open {
      val sender = ctx.sender
      msg => msg match {
        case GetKV(group: String, key: String) =>
          val request = Request(sender, key)
          Box.mkBoxFor(Get(groupKey(group), readMajority, Some(request))) {
            packed => implicit val acc = packed.access
            replicator !! packed.box
          }
        case success@GetSuccess(group: LWWMapKey[Any @unchecked], Some(request: Request)) =>
          val appData = success.get(group)
          LOG.info(s"Successfully retrived group: ${group.id}")
          Box.mkBoxFor(GetKVSuccess(request.key, appData.get(request.key).orNull)) {
            packed => implicit val acc = packed.access
            request.client !! packed.box
          }
        case NotFound(group: LWWMapKey[Any @unchecked], Some(request: Request)) =>
          LOG.info(s"We cannot find group $group")
          Box.mkBoxFor(GetKVSuccess(request.key, null)) {
            packed => implicit val acc = packed.access
            request.client !! packed.box
          }
        case GetFailure(group: LWWMapKey[Any @unchecked], Some(request: Request)) =>
          val error = s"Failed to get application data, the request key is ${request.key}"
          LOG.error(error)
          Box.mkBoxFor(GetKVFailed(new Exception(error))) {
            packed => implicit val acc = packed.access
            request.client !! packed.box
          }

        case PutKV(group: String, key: String, value: Any) =>
          val request = Request(sender, key)
          val update = Update(groupKey(group), LWWMap(), writeMajority, Some(request)) { map =>
            map + (key -> value)
          }
          Box.mkBoxFor(update) {
            packed => implicit val acc = packed.access
            replicator !! packed.box
          }
        case UpdateSuccess(group: LWWMapKey[Any @unchecked], Some(request: Request)) =>
          request.client ! PutKVSuccess
        case ModifyFailure(group: LWWMapKey[Any @unchecked], error, cause, Some(request: Request)) =>
          request.client ! PutKVFailed(request.key, new Exception(error, cause))
        case UpdateTimeout(group: LWWMapKey[Any @unchecked], Some(request: Request)) =>
          Box.mkBoxFor(PutKVFailed(request.key, new TimeoutException())) {
            packed => implicit val acc = packed.access
            request.client !! packed.box
          }
        case delete@DeleteKVGroup(group: String) =>
          Box.mkBoxFor(Delete(groupKey(group), writeMajority)) {
            packed => implicit val acc = packed.access
            replicator !! packed.box
          }
        case DeleteSuccess(group) =>
          LOG.info(s"KV Group ${group.id} is deleted")
        case ReplicationDeleteFailure(group) =>
          LOG.error(s"Failed to delete KV Group ${group.id}...")
        case DataDeleted(group) =>
          LOG.error(s"Group ${group.id} is deleted, you can no longer put/get/delete this group...")
      }
    }
  }
}

object InMemoryKVService {
  /**
   * KV Service related
   */
  case class GetKV(group: String, key: String)
  object GetKV {
    implicit val ev: Safe[GetKV] = new Safe[GetKV] {}
  }

  trait GetKVResult

  case class GetKVSuccess(key: String, value: Any) extends GetKVResult

  case class GetKVFailed(ex: Throwable) extends GetKVResult
  object GetKVFailed {
    implicit val ev: Safe[GetKVFailed] = new Safe[GetKVFailed] {}
  }

  case class PutKV(group: String, key: String, value: Any)

  case class DeleteKVGroup(group: String)
  object DeleteKVGroup {
    implicit val ev: Safe[DeleteKVGroup] = new Safe[DeleteKVGroup] {}
  }

  case class GroupDeleted(group: String) extends GetKVResult with PutKVResult

  trait PutKVResult

  case object PutKVSuccess extends PutKVResult {
    implicit val ev: Safe[PutKVSuccess.type] = new Safe[PutKVSuccess.type] {}
  }

  case class PutKVFailed(key: String, ex: Throwable) extends PutKVResult
  object PutKVFailed {
    implicit val ev: Safe[PutKVFailed] = new Safe[PutKVFailed] {}
  }

  case class Request(client: ActorRef, key: String)
  object Request {
    implicit val ev: Safe[Request] = new Safe[Request] {}
  }
}
