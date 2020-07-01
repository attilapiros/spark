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

package org.apache.spark.network.shuffle

import java.io.File

import org.apache.spark.benchmark.{Benchmark, BenchmarkBase}

/**
 * Benchmark for NormalizedInternedPathname.
 * To run this benchmark:
 * {{{
 *   1. without sbt:
 *      bin/spark-submit --class <this class> --jars <spark core test jar>
 *   2. build/sbt "core/test:runMain <this class>"
 *   3. generate result:
 *      SPARK_GENERATE_BENCHMARK_FILES=1 build/sbt "core/test:runMain <this class>"
 *      Results will be written to "benchmarks/NormalizedInternedPathname-results.txt".
 * }}}
 * */
object NormalizedInternedPathnameBenchmark extends BenchmarkBase {
  val seed = 0x1337

  private def normalizePathnames(numIters: Int, newBefore: Boolean): Unit = {
    val numLocalDir = 100
    val numSubDir = 100
    val numFilenames = 100
    val sumPathNames = numLocalDir * numSubDir * numFilenames
    val benchmark =
      new Benchmark(s"Normalize pathnames newBefore=$newBefore", sumPathNames, output = output)
    val localDir = s"/a//b//c/d/e//f/g//$newBefore"
    val files = (1 to numLocalDir).flatMap { localDirId =>
      (1 to numSubDir).flatMap { subDirId =>
        (1 to numFilenames).map { filenameId =>
          (localDir + localDirId, subDirId.toString, s"filename_$filenameId")
        }
      }
    }
    val namedNewMethod = "new" -> normalizeNewMethod
    val namedOldMethod = "old" -> normalizeOldMethod

    val ((firstName, firstMethod), (secondName, secondMethod)) =
      if (newBefore) (namedNewMethod, namedOldMethod) else (namedOldMethod, namedNewMethod)

    benchmark.addCase(
      s"Normalize with the $firstName method", numIters) { _ =>
        firstMethod(files)
    }
    benchmark.addCase(
      s"Normalize with the $secondName method", numIters) { _ =>
        secondMethod(files)
    }
    benchmark.run()
  }

  private val normalizeOldMethod = (files: Seq[(String, String, String)]) => {
    files.map { case (localDir, subDir, filename) =>
      ExecutorDiskUtils.createNormalizedInternedPathname(localDir, subDir, filename)
    }.size
  }

  private val normalizeNewMethod = (files: Seq[(String, String, String)]) => {
    files.map { case (localDir, subDir, filename) =>
      new File(s"localDir${File.separator}subDir${File.separator}filename").getPath().intern()
    }.size
  }

  override def runBenchmarkSuite(mainArgs: Array[String]): Unit = {
    val numIters = 25
    runBenchmark("Normalize pathnames new method first") {
      normalizePathnames(numIters, newBefore = true)
    }
    runBenchmark("Normalize pathnames old method first") {
      normalizePathnames(numIters, newBefore = false)
    }
  }
}
