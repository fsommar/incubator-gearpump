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
package org.apache.gearpump.streaming.dsl.task

import java.time.{Duration, Instant}

import org.apache.gearpump.Message
import org.apache.gearpump.cluster.UserConfig
import org.apache.gearpump.streaming.MockUtil
import org.apache.gearpump.streaming.dsl.window.api.{EventTimeTrigger, SlidingWindows}
import org.apache.gearpump.streaming.dsl.window.impl.{GroupAlsoByWindow, WindowRunner}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalacheck.Gen
import org.scalatest.{Matchers, PropSpec}
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.PropertyChecks

class EventTimeTriggerTaskSpec extends PropSpec with PropertyChecks
  with Matchers with MockitoSugar {

  property("EventTimeTriggerTask should trigger on watermark") {
    val longGen = Gen.chooseNum[Long](1L, 1000L)
    val windowSizeGen = longGen
    val windowStepGen = longGen
    val watermarkGen = longGen.map(Instant.ofEpochMilli)

    forAll(windowSizeGen, windowStepGen, watermarkGen) {
      (windowSize: Long, windowStep: Long, watermark: Instant) =>

        val window = SlidingWindows.apply[Any](Duration.ofMillis(windowSize),
          Duration.ofMillis(windowStep)).triggering(EventTimeTrigger)
        val groupBy = mock[GroupAlsoByWindow[Any, Any]]
        val windowRunner = mock[WindowRunner]
        val context = MockUtil.mockTaskContext
        val config = UserConfig.empty

        when(groupBy.window).thenReturn(window)

        val task = new EventTimeTriggerTask[Any, Any](groupBy, windowRunner, context, config)

        val message = mock[Message]
        task.onNext(message)
        verify(windowRunner).process(message)

        task.onWatermarkProgress(watermark)
        verify(windowRunner).trigger(any[Instant])
    }
  }

}
