/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.storage

import java.util.{HashMap => JHashMap}

import org.apache.spark.SparkFunSuite

class BlockManagerInfoSuite extends SparkFunSuite {

  private def assertMap[K, V](hashMap: JHashMap[K, V], map: Map[K, V]): Unit = {
    import scala.collection.JavaConverters._
    assert(hashMap.asScala === map)
  }

  Seq(true, false).foreach { externalShuffleServiceEnabled =>
    testBlockManagerInfo(externalShuffleServiceEnabled)
  }

  def testBlockManagerInfo(shuffleServiceEnabled: Boolean): Unit =
    test(s"test BlockManagerInfo with externalShuffleServiceEnabled=$shuffleServiceEnabled") {
      val blockManagerInfo = new BlockManagerInfo(
        blockManagerId = BlockManagerId("executor0", "host", 1234, None),
        timeMs = 300,
        maxOnHeapMem = 10000,
        maxOffHeapMem = 20000,
        slaveEndpoint = null,
        externalShuffleServiceEnabled = shuffleServiceEnabled)

      assert(blockManagerInfo.blocks.isEmpty)
      assert(blockManagerInfo.exclusiveCachedBlocks.isEmpty)

      // 1. add broadcast block
      val broadcastId1: BlockId = BroadcastBlockId(0, "field1")
      blockManagerInfo.updateBlockInfo(
        broadcastId1, StorageLevel.MEMORY_AND_DISK, memSize = 0, diskSize = 100)

      assertMap(blockManagerInfo.blocks,
        Map(broadcastId1 -> BlockStatus(StorageLevel.MEMORY_AND_DISK, 0, 100)))
      assert(blockManagerInfo.exclusiveCachedBlocks === Set())
      assert(blockManagerInfo.remainingMem == 30000)

      // 2. add RDD block with MEMORY_ONLY
      val rddId1: BlockId = RDDBlockId(1, 0)
      blockManagerInfo.updateBlockInfo(
        rddId1, StorageLevel.MEMORY_ONLY, memSize = 200, diskSize = 0)

      assertMap(
        blockManagerInfo.blocks,
        Map(
          broadcastId1 -> BlockStatus(StorageLevel.MEMORY_AND_DISK, 0, 100),
          rddId1 -> BlockStatus(StorageLevel.MEMORY_ONLY, 200, 0)))
      assert(blockManagerInfo.exclusiveCachedBlocks === Set(rddId1))
      assert(blockManagerInfo.remainingMem == 29800)

      // 3. add RDD block with DISK_ONLY
      val rddId2: BlockId = RDDBlockId(2, 0)
      blockManagerInfo.updateBlockInfo(rddId2, StorageLevel.DISK_ONLY, memSize = 0, diskSize = 200)

      assertMap(
        blockManagerInfo.blocks,
        Map(
          broadcastId1 -> BlockStatus(StorageLevel.MEMORY_AND_DISK, 0, 100),
          rddId1 -> BlockStatus(StorageLevel.MEMORY_ONLY, 200, 0),
          rddId2 -> BlockStatus(StorageLevel.DISK_ONLY, 0, 200)))

      val exclusiveCachedBlocksForOneMemoryOnly =
        if (shuffleServiceEnabled) Set(rddId1) else Set(rddId1, rddId2)
      assert(blockManagerInfo.exclusiveCachedBlocks === exclusiveCachedBlocksForOneMemoryOnly)
      assert(blockManagerInfo.remainingMem == 29800)

      // 4. update first RDD block to DISK_ONLY
      blockManagerInfo.updateBlockInfo(rddId1, StorageLevel.DISK_ONLY, memSize = 0, diskSize = 200)

      assertMap(
        blockManagerInfo.blocks,
        Map(
          broadcastId1 -> BlockStatus(StorageLevel.MEMORY_AND_DISK, 0, 100),
          rddId1 -> BlockStatus(StorageLevel.DISK_ONLY, 0, 200),
          rddId2 -> BlockStatus(StorageLevel.DISK_ONLY, 0, 200)))

      val exclusiveCachedBlocksForNoMemoryOnly =
        if (shuffleServiceEnabled) Set() else Set(rddId1, rddId2)
      assert(blockManagerInfo.exclusiveCachedBlocks === exclusiveCachedBlocksForNoMemoryOnly)
      assert(blockManagerInfo.remainingMem == 30000)

      // 5. using invalid StorageLevel
      blockManagerInfo.updateBlockInfo(rddId1, StorageLevel.NONE, memSize = 0, diskSize = 200)

      assertMap(
        blockManagerInfo.blocks,
        Map(
          broadcastId1 -> BlockStatus(StorageLevel.MEMORY_AND_DISK, 0, 100),
          rddId2 -> BlockStatus(StorageLevel.DISK_ONLY, 0, 200)))
      val exclusiveCachedBlocksAfterInvalidation =
        if (shuffleServiceEnabled) Set() else Set(rddId2)
      assert(blockManagerInfo.exclusiveCachedBlocks === exclusiveCachedBlocksAfterInvalidation)
      assert(blockManagerInfo.remainingMem == 30000)

      // 6. remove block
      blockManagerInfo.removeBlock(rddId2)

      assertMap(
        blockManagerInfo.blocks,
        Map(
          broadcastId1 -> BlockStatus(StorageLevel.MEMORY_AND_DISK, 0, 100)))
      assert(blockManagerInfo.exclusiveCachedBlocks === Set())
      assert(blockManagerInfo.remainingMem == 30000)
    }
}
