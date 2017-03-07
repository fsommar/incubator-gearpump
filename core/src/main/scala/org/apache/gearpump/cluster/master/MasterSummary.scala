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

import org.apache.gearpump.util.HistoryMetricsService.HistoryMetricsConfig
import lacasa.Safe

/** Master status. Synced means all masters are live and synced. */
object MasterStatus {
  type Type = String
  val Synced = "synced"
  val UnSynced = "unsynced"
}

case class MasterNode(host: String, port: Int) {
  def toTuple: (String, Int) = {
    (host, port)
  }
}
object MasterNode {
  implicit val ev: Safe[MasterNode] = new Safe[MasterNode] {}
}

/**
 * Master information returned for REST API call
 */
case class MasterSummary(
    leader: MasterNode,
    cluster: List[MasterNode],
    aliveFor: Long,
    logFile: String,
    jarStore: String,
    masterStatus: MasterStatus.Type,
    homeDirectory: String,
    activities: List[MasterActivity],
    jvmName: String,
    historyMetricsConfig: HistoryMetricsConfig = null)
object MasterSummary {
  implicit val ev: Safe[MasterSummary] = new Safe[MasterSummary] {}
}

case class MasterActivity(time: Long, event: String)
object MasterActivity {
  implicit val ev: Safe[MasterActivity] = new Safe[MasterActivity] {}
}
