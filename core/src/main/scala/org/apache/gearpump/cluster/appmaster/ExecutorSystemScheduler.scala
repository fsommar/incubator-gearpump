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

package org.apache.gearpump.cluster.appmaster

import org.apache.gearpump.cluster.worker.WorkerId

import scala.concurrent.duration._

import akka.actor._
import com.typesafe.config.Config
import lacasa.Safe

import org.apache.gearpump.cluster.AppMasterToMaster.RequestResource
import org.apache.gearpump.cluster.MasterToAppMaster.ResourceAllocated
import org.apache.gearpump.cluster._
import org.apache.gearpump.cluster.appmaster.ExecutorSystemLauncher._
import org.apache.gearpump.cluster.appmaster.ExecutorSystemScheduler._
import org.apache.gearpump.cluster.scheduler.{ResourceAllocation, ResourceRequest}
import org.apache.gearpump.util.{Constants, LogUtil}

/**
 * ExecutorSystem is also a type of resource, this class schedules ExecutorSystem for AppMaster.
 * AppMaster can use this class to directly request a live executor actor systems. The communication
 * in the background with Master and Worker is hidden from AppMaster.
 *
 * Please use ExecutorSystemScheduler.props() to construct this actor
 */
private[appmaster]
class ExecutorSystemScheduler(appId: Int, masterProxy: ActorRef,
    executorSystemLauncher: (Int, Session) => Props) extends Actor {

  private val LOG = LogUtil.getLogger(getClass, app = appId)
  implicit val timeout = Constants.FUTURE_TIMEOUT
  implicit val actorSystem = context.system
  var currentSystemId = 0

  var resourceAgents = Map.empty[Session, ActorRef]

  def receive: Receive = {
    clientCommands orElse resourceAllocationMessageHandler orElse executorSystemMessageHandler
  }

  def clientCommands: Receive = {
    case start: StartExecutorSystems =>
      LOG.info(s"starting executor systems (ExecutorSystemConfig(${start.executorSystemConfig}), " +
        s"Resources(${start.resources.mkString(",")}))")
      val requestor = sender()
      val executorSystemConfig = start.executorSystemConfig
      val session = Session(requestor, executorSystemConfig)
      val agent = resourceAgents.getOrElse(session,
        context.actorOf(Props(new ResourceAgent(masterProxy, session))))
      resourceAgents = resourceAgents + (session -> agent)

      start.resources.foreach { resource =>
        agent ! RequestResource(appId, resource)
      }

    case StopExecutorSystem(executorSystem) =>
      executorSystem.shutdown
  }

  def resourceAllocationMessageHandler: Receive = {
    case ResourceAllocatedForSession(allocations, session) =>
      if (isSessionAlive(session)) {
        allocations.foreach { resourceAllocation =>
          val ResourceAllocation(resource, worker, workerId) = resourceAllocation

          val launcher = context.actorOf(executorSystemLauncher(appId, session))
          launcher ! LaunchExecutorSystem(WorkerInfo(workerId, worker), currentSystemId, resource)
          currentSystemId = currentSystemId + 1
        }
      }
    case ResourceAllocationTimeOut(session) =>
      if (isSessionAlive(session)) {
        resourceAgents = resourceAgents - session
        session.requestor ! StartExecutorSystemTimeout
      }
  }

  def executorSystemMessageHandler: Receive = {
    case LaunchExecutorSystemSuccess(system, session) =>
      if (isSessionAlive(session)) {
        LOG.info("LaunchExecutorSystemSuccess, send back to " + session.requestor)
        system.bindLifeCycleWith(self)
        session.requestor ! ExecutorSystemStarted(system, session.executorSystemJvmConfig.jar)
      } else {
        LOG.error("We get a ExecutorSystem back, but resource requestor is no longer valid. " +
          "Will shutdown the allocated system")
        system.shutdown
      }
    case LaunchExecutorSystemTimeout(session) =>
      if (isSessionAlive(session)) {
        LOG.error(s"Failed to launch executor system for ${session.requestor} due to timeout")
        session.requestor ! StartExecutorSystemTimeout
      }

    case LaunchExecutorSystemRejected(resource, reason, session) =>
      if (isSessionAlive(session)) {
        LOG.error(s"Failed to launch executor system, due to $reason, " +
          s"will ask master to allocate new resources $resource")
        resourceAgents.get(session).map { resourceAgent: ActorRef =>
          resourceAgent ! RequestResource(appId, ResourceRequest(resource, WorkerId.unspecified))
        }
      }
  }

  private def isSessionAlive(session: Session): Boolean = {
    Option(session).flatMap(session => resourceAgents.get(session)).nonEmpty
  }
}

object ExecutorSystemScheduler {

  case class StartExecutorSystems(
      resources: Array[ResourceRequest], executorSystemConfig: ExecutorSystemJvmConfig)

  case class ExecutorSystemStarted(system: ExecutorSystem, boundedJar: Option[AppJar])

  case class StopExecutorSystem(system: ExecutorSystem)
  object StopExecutorSystem {
    implicit val ev: Safe[StopExecutorSystem] = new Safe[StopExecutorSystem] {}
  }

  case object StartExecutorSystemTimeout {
    implicit val ev: Safe[StartExecutorSystemTimeout.type] = new Safe[StartExecutorSystemTimeout.type] {}
  }

  case class ExecutorSystemJvmConfig(classPath: Array[String], jvmArguments: Array[String],
      jar: Option[AppJar], username: String, executorAkkaConfig: Config = null)

  /**
   * For each client which ask for an executor system, the scheduler will create a session for it.
   *
   */
  private[appmaster]
  case class Session(requestor: ActorRef, executorSystemJvmConfig: ExecutorSystemJvmConfig)

  /**
   * This is a agent for session to request resource
   *
   * @param session the original requester of the resource requests
   */
  private[appmaster]
  class ResourceAgent(master: ActorRef, session: Session) extends Actor {
    private var resourceRequestor: ActorRef = null
    var timeOutClock: Cancellable = null
    private var unallocatedResource: Int = 0

    import context.dispatcher

    import org.apache.gearpump.util.Constants._

    val timeout = context.system.settings.config.getInt(GEARPUMP_RESOURCE_ALLOCATION_TIMEOUT)

    def receive: Receive = {
      case request: RequestResource =>
        unallocatedResource += request.request.resource.slots
        Option(timeOutClock).map(_.cancel)
        timeOutClock = context.system.scheduler.scheduleOnce(
          timeout.seconds, self, ResourceAllocationTimeOut(session))
        resourceRequestor = sender
        master ! request
      case ResourceAllocated(allocations) =>
        unallocatedResource -= allocations.map(_.resource.slots).sum
        resourceRequestor forward ResourceAllocatedForSession(allocations, session)
      case timeout: ResourceAllocationTimeOut =>
        if (unallocatedResource > 0) {
          resourceRequestor ! ResourceAllocationTimeOut(session)
          // We will not receive any ResourceAllocation after timeout
          context.stop(self)
        }
    }
  }

  private[ExecutorSystemScheduler]
  case class ResourceAllocatedForSession(resource: Array[ResourceAllocation], session: Session)

  private[ExecutorSystemScheduler]
  case class ResourceAllocationTimeOut(session: Session)

}
