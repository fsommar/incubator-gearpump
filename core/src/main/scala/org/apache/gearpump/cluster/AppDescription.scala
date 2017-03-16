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

import java.io.Serializable

import akka.actor.{Actor, ActorRef, ActorSystem}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.gearpump.cluster.appmaster.WorkerInfo
import org.apache.gearpump.cluster.scheduler.Resource
import org.apache.gearpump.jarstore.FilePath
import lacasa.Safe

import scala.reflect.ClassTag

/**
 * This contains all information to run an application
 *
 * @param name The name of this application
 * @param appMaster The class name of AppMaster Actor
 * @param userConfig user configuration.
 * @param clusterConfig User provided cluster config, it overrides gear.conf when starting
 *                      new applications. In most cases, you should not need to change it. If you do
 *                      really need to change it, please use ClusterConfigSource(filePath) to
 *                      construct the object, while filePath points to the .conf file.
 */
case class AppDescription(name: String, appMaster: String, userConfig: UserConfig,
    clusterConfig: Config = ConfigFactory.empty())
object AppDescription {
  implicit val ev: Safe[AppDescription] = new Safe[AppDescription] {}
}

/**
 * Each job, streaming or not streaming, need to provide an Application class.
 * The master uses this class to start AppMaster.
 */
trait Application {

  /** Name of this application, must be unique in the system */
  def name: String

  /** Custom user configuration  */
  def userConfig(implicit system: ActorSystem): UserConfig

  /**
   * AppMaster class, must have a constructor like this:
   * this(appContext: AppMasterContext, app: AppDescription)
   */
  def appMaster: Class[_ <: ApplicationMaster]
}

object Application {
  def apply[T <: ApplicationMaster](
      name: String, userConfig: UserConfig)(implicit tag: ClassTag[T]): Application = {
    new DefaultApplication(name, userConfig,
      tag.runtimeClass.asInstanceOf[Class[_ <: ApplicationMaster]])
  }

  class DefaultApplication(
      override val name: String, inputUserConfig: UserConfig,
      val appMaster: Class[_ <: ApplicationMaster]) extends Application {
    override def userConfig(implicit system: ActorSystem): UserConfig = inputUserConfig
  }

  def ApplicationToAppDescription(app: Application)(implicit system: ActorSystem)
    : AppDescription = {
    val filterJvmReservedKeys = ClusterConfig.filterOutDefaultConfig(system.settings.config)
    AppDescription(app.name, app.appMaster.getName, app.userConfig, filterJvmReservedKeys)
  }
}

/**
 * Used for verification. All AppMaster must extend this interface
 */
abstract class ApplicationMaster extends Actor

/**
 * This contains context information when starting an AppMaster
 *
 * @param appId application instance id assigned, it is unique in the cluster
 * @param username The username who submitted this application
 * @param resource Resouce allocated to start this AppMaster daemon. AppMaster are allowed to
 *                 request more resource from Master.
 * @param appJar application Jar. If the jar is already in classpath, then it can be None.
 * @param masterProxy The proxy to master actor, it bridges the messages between appmaster
 *                    and master
 */
case class AppMasterContext(
    appId: Int,
    username: String,
    resource: Resource,
    workerInfo: WorkerInfo,
    appJar: Option[AppJar],
    masterProxy: ActorRef)

/**
 * Jar file container in the cluster
 *
 * @param name A meaningful name to represent this jar
 * @param filePath Where the jar file is stored.
 */
case class AppJar(name: String, filePath: FilePath)
object AppJar {
  implicit val ev: Safe[AppJar] = new Safe[AppJar] {}
}

/**
 * Serves as the context to start an Executor JVM.
 */
// TODO: ExecutorContext doesn't belong to this package in logic.
case class ExecutorContext(
    executorId: Int, worker: WorkerInfo, appId: Int, appName: String,
    appMaster: ActorRef, resource: Resource)
object ExecutorContext {
  implicit val ev: Safe[ExecutorContext] = new Safe[ExecutorContext] {}
}

/**
 * JVM configurations to start an Executor JVM.
 *
 * @param classPath When executor is created by a worker JVM, executor automatically inherits
 *                  parent worker's classpath. Sometimes, you still want to add some extra
 *                  classpath, you can do this by specify classPath option.
 * @param jvmArguments java arguments like -Dxx=yy
 * @param mainClass Executor main class name like org.apache.gearpump.xx.AppMaster
 * @param arguments Executor command line arguments
 * @param jar application jar
 * @param executorAkkaConfig Akka config used to initialize the actor system of this executor.
 *                           It uses org.apache.gearpump.util.Constants.GEARPUMP_CUSTOM_CONFIG_FILE
 *                           to pass the config to executor process
 */
// TODO: ExecutorContext doesn't belong to this package in logic.
case class ExecutorJVMConfig(
    classPath: Array[String], jvmArguments: Array[String], mainClass: String,
    arguments: Array[String], jar: Option[AppJar], username: String,
    executorAkkaConfig: Config = ConfigFactory.empty())

sealed abstract class ApplicationStatus(val status: String)
  extends Serializable{
  override def toString: String = status
}

sealed abstract class ApplicationTerminalStatus(override val status: String)
  extends ApplicationStatus(status)

object ApplicationStatus {
  implicit val ev: Safe[ApplicationStatus] = new Safe[ApplicationStatus] {}
  case object PENDING extends ApplicationStatus("pending") {
    implicit val ev1: Safe[PENDING.type] = new Safe[PENDING.type] {}
  }

  case object ACTIVE extends ApplicationStatus("active") {
    implicit val ev1: Safe[ACTIVE.type] = new Safe[ACTIVE.type] {}
  }

  case object SUCCEEDED extends ApplicationTerminalStatus("succeeded") {
    implicit val ev1: Safe[SUCCEEDED.type] = new Safe[SUCCEEDED.type] {}
  }

  case object FAILED extends ApplicationTerminalStatus("failed") {
    implicit val ev1: Safe[FAILED.type] = new Safe[FAILED.type] {}
  }

  case object TERMINATED extends ApplicationTerminalStatus("terminated") {
    implicit val ev1: Safe[TERMINATED.type] = new Safe[TERMINATED.type] {}
  }

  case object NONEXIST extends ApplicationStatus("nonexist") {
    implicit val ev1: Safe[NONEXIST.type] = new Safe[NONEXIST.type] {}
  }
}
