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

import org.apache.gearpump.cluster.worker.{WorkerId, WorkerSummary}

import scala.util.Try
import akka.actor.ActorRef
import com.typesafe.config.Config
import org.apache.gearpump.TimeStamp
import org.apache.gearpump.cluster.appmaster.WorkerInfo
import org.apache.gearpump.cluster.master.MasterSummary
import org.apache.gearpump.cluster.scheduler.{Resource, ResourceAllocation, ResourceRequest}
import org.apache.gearpump.metrics.Metrics.MetricType
import lacasa.Safe

object ClientToMaster {
  case object AddMaster {
    implicit val ev: Safe[AddMaster.type] = new Safe[AddMaster.type] {}
  }
  case class AddWorker(count: Int)
  object AddWorker {
    implicit val ev: Safe[AddWorker] = new Safe[AddWorker] {}
  }
  case class RemoveMaster(masterContainerId: String)
  object RemoveMaster {
    implicit val ev: Safe[RemoveMaster] = new Safe[RemoveMaster] {}
  }
  case class RemoveWorker(workerContainerId: String)
  object RemoveWorker {
    implicit val ev: Safe[RemoveWorker] = new Safe[RemoveWorker] {}
  }

  /** Command result of AddMaster, RemoveMaster, and etc... */
  case class CommandResult(success: Boolean, exception: String = null) {
    override def toString: String = {
      val tag = getClass.getSimpleName
      if (success) {
        s"$tag(success)"
      } else {
        s"$tag(failure, $exception)"
      }
    }
  }
  object CommandResult {
    implicit val ev: Safe[CommandResult] = new Safe[CommandResult] {}
  }

  /** Submit an application to master */
  case class SubmitApplication(
      appDescription: AppDescription, appJar: Option[AppJar],
      username: String = System.getProperty("user.name"))
  object SubmitApplication {
    implicit val ev: Safe[SubmitApplication] = new Safe[SubmitApplication] {}
  }

  case class RestartApplication(appId: Int)
  object RestartApplication {
    implicit val ev: Safe[RestartApplication] = new Safe[RestartApplication] {}
  }
  case class ShutdownApplication(appId: Int)
  object ShutdownApplication {
    implicit val ev: Safe[ShutdownApplication] = new Safe[ShutdownApplication] {}
  }

  /** Client send ResolveAppId to Master to resolves AppMaster actor path by providing appId */
  case class ResolveAppId(appId: Int)
  object ResolveAppId {
    implicit val ev: Safe[ResolveAppId] = new Safe[ResolveAppId] {}
  }

  /** Client send ResolveWorkerId to master to get the Actor path of worker. */
  case class ResolveWorkerId(workerId: WorkerId)
  object ResolveWorkerId {
    implicit val ev: Safe[ResolveWorkerId] = new Safe[ResolveWorkerId] {}
  }

  /** Get an active Jar store to upload job jars, like wordcount.jar */
  case object GetJarStoreServer {
    implicit val ev: Safe[GetJarStoreServer.type] = new Safe[GetJarStoreServer.type] {}
  }

  /** Service address of JarStore */
  case class JarStoreServerAddress(url: String)
  object JarStoreServerAddress {
    implicit val ev: Safe[JarStoreServerAddress] = new Safe[JarStoreServerAddress] {}
  }

  /** Query AppMaster config by providing appId */
  case class QueryAppMasterConfig(appId: Int)
  object QueryAppMasterConfig {
    implicit val ev: Safe[QueryAppMasterConfig] = new Safe[QueryAppMasterConfig] {}
  }

  /** Query worker config */
  case class QueryWorkerConfig(workerId: WorkerId)
  object QueryWorkerConfig {
    implicit val ev: Safe[QueryWorkerConfig] = new Safe[QueryWorkerConfig] {}
  }

  /** Query master config */
  case object QueryMasterConfig {
    implicit val ev: Safe[QueryMasterConfig.type] = new Safe[QueryMasterConfig.type] {}
  }

  /** Options for read the metrics from the cluster */
  object ReadOption {
    type ReadOption = String

    val Key: String = "readOption"

    /** Read the latest record of the metrics, only return 1 record for one metric name (id) */
    val ReadLatest: ReadOption = "readLatest"

    /** Read recent metrics from cluster, typically it contains metrics in 5 minutes */
    val ReadRecent = "readRecent"

    /**
     * Read the history metrics, typically it contains metrics for 48 hours
     *
     * NOTE: Each hour only contain one or two data points.
     */
    val ReadHistory = "readHistory"
  }

  /** Query history metrics from master or app master. */
  case class QueryHistoryMetrics(
      path: String, readOption: ReadOption.ReadOption = ReadOption.ReadLatest,
      aggregatorClazz: String = "", options: Map[String, String] = Map.empty[String, String])

  /**
   * If there are message loss, the clock would pause for a while. This message is used to
   * pin-point which task has stalling clock value, and usually it means something wrong on
   * that machine.
   */
  case class GetStallingTasks(appId: Int)
  object GetStallingTasks {
    implicit val ev: Safe[GetStallingTasks] = new Safe[GetStallingTasks] {}
  }

  /**
   * Request app master for a short list of cluster app that administrators should be aware of.
   */
  case class GetLastFailure(appId: Int)
  object GetLastFailure {
    implicit val ev: Safe[GetLastFailure] = new Safe[GetLastFailure] {}
  }

  /**
   * Register a client to wait application's result
   */
  case class RegisterAppResultListener(appId: Int)
  object RegisterAppResultListener {
    implicit val ev: Safe[RegisterAppResultListener] = new Safe[RegisterAppResultListener] {}
  }
}

object MasterToClient {

  /** Result of SubmitApplication */
  // TODO: Merge with SubmitApplicationResultValue and change this to (appId: Option, ex: Exception)
  case class SubmitApplicationResult(appId: Try[Int])
  object SubmitApplicationResult {
    implicit val ev: Safe[SubmitApplicationResult] = new Safe[SubmitApplicationResult] {}
  }

  case class SubmitApplicationResultValue(appId: Int)
  object SubmitApplicationResultValue {
    implicit val ev: Safe[SubmitApplicationResultValue] = new Safe[SubmitApplicationResultValue] {}
  }

  case class ShutdownApplicationResult(appId: Try[Int])
  object ShutdownApplicationResult {
    implicit val ev: Safe[ShutdownApplicationResult] = new Safe[ShutdownApplicationResult] {}
  }
  case class ReplayApplicationResult(appId: Try[Int])
  object ReplayApplicationResult {
    implicit val ev: Safe[ReplayApplicationResult] = new Safe[ReplayApplicationResult] {}
  }

  /** Return Actor ref of app master */
  case class ResolveAppIdResult(appMaster: Try[ActorRef])
  object ResolveAppIdResult {
    implicit val ev: Safe[ResolveAppIdResult] = new Safe[ResolveAppIdResult] {}
  }

  /** Return Actor ref of worker */
  case class ResolveWorkerIdResult(worker: Try[ActorRef])
  object ResolveWorkerIdResult {
    implicit val ev: Safe[ResolveWorkerIdResult] = new Safe[ResolveWorkerIdResult] {}
  }

  case class AppMasterConfig(config: Config)
  object AppMasterConfig {
    implicit val ev: Safe[AppMasterConfig] = new Safe[AppMasterConfig] {}
  }

  case class WorkerConfig(config: Config)
  object WorkerConfig {
    implicit val ev: Safe[WorkerConfig] = new Safe[WorkerConfig] {}
  }

  case class MasterConfig(config: Config)
  object MasterConfig {
    implicit val ev: Safe[MasterConfig] = new Safe[MasterConfig] {}
  }

  case class HistoryMetricsItem(time: TimeStamp, value: MetricType)
  object HistoryMetricsItem {
    implicit val ev: Safe[HistoryMetricsItem] = new Safe[HistoryMetricsItem] {}
  }

  /**
   * History metrics returned from master, worker, or app master.
   *
   * All metric items are organized like a tree, path is used to navigate through the tree.
   * For example, when querying with path == "executor0.task1.throughput*", the metrics
   * provider picks metrics whose source matches the path.
   *
   * @param path The path client provided. The returned metrics are the result query of this path.
   * @param metrics The detailed metrics.
   */
  case class HistoryMetrics(path: String, metrics: List[HistoryMetricsItem])
  object HistoryMetrics {
    implicit val ev: Safe[HistoryMetrics] = new Safe[HistoryMetrics] {}
  }

  /** Return the last error of this streaming application job */
  case class LastFailure(time: TimeStamp, error: String)
  object LastFailure {
    implicit val ev: Safe[LastFailure] = new Safe[LastFailure] {}
  }

  sealed trait ApplicationResult
  object ApplicationResult {
    implicit val ev: Safe[ApplicationResult] = new Safe[ApplicationResult] {}
  }

  case class ApplicationSucceeded(appId: Int) extends ApplicationResult
  object ApplicationSucceeded {
    implicit val ev1: Safe[ApplicationSucceeded] = new Safe[ApplicationSucceeded] {}
  }

  case class ApplicationFailed(appId: Int, error: Throwable) extends ApplicationResult
  object ApplicationFailed {
    implicit val ev1: Safe[ApplicationFailed] = new Safe[ApplicationFailed] {}
  }
}

object AppMasterToMaster {

  /**
   * Register an AppMaster by providing a ActorRef, and workerInfo which is running on
   */
  case class RegisterAppMaster(appId: Int, appMaster: ActorRef, workerInfo: WorkerInfo)
  object RegisterAppMaster {
    implicit val ev: Safe[RegisterAppMaster] = new Safe[RegisterAppMaster] {}
  }

  case class InvalidAppMaster(appId: Int, appMaster: String, reason: Throwable)
  object InvalidAppMaster {
    implicit val ev: Safe[InvalidAppMaster] = new Safe[InvalidAppMaster] {}
  }

  case class RequestResource(appId: Int, request: ResourceRequest)
  object RequestResource {
    implicit val ev: Safe[RequestResource] = new Safe[RequestResource] {}
  }

  /**
   * Each application job can save some data in the distributed cluster storage on master nodes.
   *
   * @param appId App Id of the client application who send the request.
   * @param key Key name
   * @param value Value to store on distributed cluster storage on master nodes
   */
  case class SaveAppData(appId: Int, key: String, value: Any)

  /** The application specific data is successfully stored */
  case object AppDataSaved {
    implicit val ev: Safe[AppDataSaved.type] = new Safe[AppDataSaved.type] {}
  }

  /** Fail to store the application data */
  case object SaveAppDataFailed {
    implicit val ev: Safe[SaveAppDataFailed.type] = new Safe[SaveAppDataFailed.type] {}
  }

  /** Fetch the application specific data that stored previously */
  case class GetAppData(appId: Int, key: String)
  object GetAppData {
    implicit val ev: Safe[GetAppData] = new Safe[GetAppData] {}
  }

  /** The KV data returned for query GetAppData */
  case class GetAppDataResult(key: String, value: Any)

  /**
   * AppMasterSummary returned to REST API query. Streaming and Non-streaming
   * have very different application info. AppMasterSummary is the common interface.
   */
  trait AppMasterSummary {
    def appType: String
    def appId: Int
    def appName: String
    def actorPath: String
    def status: ApplicationStatus
    def startTime: TimeStamp
    def uptime: TimeStamp
    def user: String
  }
  object AppMasterSummary {
    implicit val ev: Safe[AppMasterSummary] = new Safe[AppMasterSummary] {}
  }

  /** Represents a generic application that is not a streaming job */
  case class GeneralAppMasterSummary(
      appId: Int,
      appType: String = "general",
      appName: String = null,
      actorPath: String = null,
      status: ApplicationStatus = ApplicationStatus.ACTIVE,
      startTime: TimeStamp = 0L,
      uptime: TimeStamp = 0L,
      user: String = null)
    extends AppMasterSummary
  object GeneralAppMasterSummary {
    implicit val ev1: Safe[GeneralAppMasterSummary] = new Safe[GeneralAppMasterSummary] {}
  }

  /** Fetches the list of workers from Master */
  case object GetAllWorkers {
    implicit val ev: Safe[GetAllWorkers.type] = new Safe[GetAllWorkers.type] {}
  }

  /** Get worker data of workerId */
  case class GetWorkerData(workerId: WorkerId)
  object GetWorkerData {
    implicit val ev: Safe[GetWorkerData] = new Safe[GetWorkerData] {}
  }

  /** Response to GetWorkerData */
  case class WorkerData(workerDescription: WorkerSummary)

  /** Get Master data */
  case object GetMasterData {
    implicit val ev: Safe[GetMasterData.type] = new Safe[GetMasterData.type] {}
  }

  /** Response to GetMasterData */
  case class MasterData(masterDescription: MasterSummary)
  object MasterData {
    implicit val ev: Safe[MasterData] = new Safe[MasterData] {}
  }

  /**
   * Denotes the application state change of an app.
   */
  case class ApplicationStatusChanged(appId: Int, newStatus: ApplicationStatus,
      timeStamp: TimeStamp, error: Throwable)
  object ApplicationStatusChanged {
    implicit val ev: Safe[ApplicationStatusChanged] = new Safe[ApplicationStatusChanged] {}
  }
}

object MasterToAppMaster {

  /** Resource allocated for application xx */
  case class ResourceAllocated(allocations: Array[ResourceAllocation])

  /** Master confirm reception of RegisterAppMaster message */
  case class AppMasterRegistered(appId: Int)
  object AppMasterRegistered {
    implicit val ev: Safe[AppMasterRegistered] = new Safe[AppMasterRegistered] {}
  }

  /** Master confirm reception of ActivateAppMaster message */
  case class AppMasterActivated(appId: Int)
  object AppMasterActivated {
    implicit val ev: Safe[AppMasterActivated] = new Safe[AppMasterActivated] {}
  }

  /** Shutdown the application job */
  case object ShutdownAppMaster {
    implicit val ev: Safe[ShutdownAppMaster.type] = new Safe[ShutdownAppMaster.type] {}
  }

  sealed trait StreamingType
  object StreamingType {
    implicit val ev: Safe[StreamingType] = new Safe[StreamingType] {}
  }
  case class AppMasterData(status: ApplicationStatus, appId: Int = 0, appName: String = null,
      appMasterPath: String = null, workerPath: String = null, submissionTime: TimeStamp = 0,
      startTime: TimeStamp = 0, finishTime: TimeStamp = 0, user: String = null)
  object AppMasterData {
    implicit val ev: Safe[AppMasterData] = new Safe[AppMasterData] {}
  }

  case class AppMasterDataRequest(appId: Int, detail: Boolean = false)
  object AppMasterDataRequest {
    implicit val ev: Safe[AppMasterDataRequest] = new Safe[AppMasterDataRequest] {}
  }

  case class AppMastersData(appMasters: List[AppMasterData])
  object AppMastersData {
    implicit val ev: Safe[AppMastersData] = new Safe[AppMastersData] {}
  }
  case object AppMastersDataRequest {
    implicit val ev: Safe[AppMastersDataRequest.type] = new Safe[AppMastersDataRequest.type] {}
  }
  case class AppMasterDataDetailRequest(appId: Int)
  object AppMasterDataDataDetailRequest {
    implicit val ev: Safe[AppMasterDataDetailRequest] = new Safe[AppMasterDataDetailRequest] {}
  }
  case class AppMasterMetricsRequest(appId: Int) extends StreamingType
  object AppMasterMetricsRequest {
    implicit val ev1: Safe[AppMasterMetricsRequest] = new Safe[AppMasterMetricsRequest] {}
  }

  case class ReplayFromTimestampWindowTrailingEdge(appId: Int)
  object ReplayFromTimestampWindowTrailingEdge {
    implicit val ev: Safe[ReplayFromTimestampWindowTrailingEdge] = new Safe[ReplayFromTimestampWindowTrailingEdge] {}
  }

  case class WorkerList(workers: List[WorkerId])
  object WorkerList {
    implicit val ev: Safe[WorkerList] = new Safe[WorkerList] {}
  }
}

object AppMasterToWorker {
  case class LaunchExecutor(
      appId: Int, executorId: Int, resource: Resource, executorJvmConfig: ExecutorJVMConfig)

  case class ShutdownExecutor(appId: Int, executorId: Int, reason: String)
  object ShutdownExecutor {
    implicit val ev: Safe[ShutdownExecutor] = new Safe[ShutdownExecutor] {}
  }
  case class ChangeExecutorResource(appId: Int, executorId: Int, resource: Resource)
  object ChangeExecutorResource {
    implicit val ev: Safe[ChangeExecutorResource] = new Safe[ChangeExecutorResource] {}
  }
}

object WorkerToAppMaster {
  case class ExecutorLaunchRejected(reason: String = null, ex: Throwable = null)
  object ExecutorLaunchRejected {
    implicit val ev: Safe[ExecutorLaunchRejected] = new Safe[ExecutorLaunchRejected] {}
  }
  case class ShutdownExecutorSucceed(appId: Int, executorId: Int)
  object ShutdownExecutorSucceed {
    implicit val ev: Safe[ShutdownExecutorSucceed] = new Safe[ShutdownExecutorSucceed] {}
  }
  case class ShutdownExecutorFailed(reason: String = null, ex: Throwable = null)
  object ShutdownExecutorFailed {
    implicit val ev: Safe[ShutdownExecutorFailed] = new Safe[ShutdownExecutorFailed] {}
  }
}
