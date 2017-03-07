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
package org.apache.gearpump.cluster

import akka.actor.ActorRef
import org.apache.gearpump.cluster.master.Master.MasterInfo
import org.apache.gearpump.cluster.scheduler.Resource
import org.apache.gearpump.cluster.worker.WorkerId
import lacasa.Safe

/**
 * Cluster Bootup Flow
 */
object WorkerToMaster {

  /** When an worker is started, it sends RegisterNewWorker */
  case object RegisterNewWorker {
    implicit val ev: Safe[RegisterNewWorker.type] = new Safe[RegisterNewWorker.type] {}
  }

  /** When worker lose connection with master, it tries to register itself again with old Id. */
  case class RegisterWorker(workerId: WorkerId)
  object RegisterWorker {
    implicit val ev: Safe[RegisterWorker] = new Safe[RegisterWorker] {}
  }

  /** Worker is responsible to broadcast its current status to master */
  case class ResourceUpdate(worker: ActorRef, workerId: WorkerId, resource: Resource)
  object ResourceUpdate {
    implicit val ev: Safe[ResourceUpdate] = new Safe[ResourceUpdate] {}
  }
}

object MasterToWorker {

  /** Master confirm the reception of RegisterNewWorker or RegisterWorker */
  case class WorkerRegistered(workerId: WorkerId, masterInfo: MasterInfo)
  object WorkerRegistered {
    implicit val ev: Safe[WorkerRegistered] = new Safe[WorkerRegistered] {}
  }

  /** Worker have not received reply from master */
  case class UpdateResourceFailed(reason: String = null, ex: Throwable = null)
  object UpdateResourceFailed {
    implicit val ev: Safe[UpdateResourceFailed] = new Safe[UpdateResourceFailed] {}
  }

  /** Master is synced with worker on resource slots managed by current worker */
  case object UpdateResourceSucceed {
    implicit val ev: Safe[UpdateResourceSucceed.type] = new Safe[UpdateResourceSucceed.type] {}
  }
}
