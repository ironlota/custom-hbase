/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.regionserver;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hadoop.hbase.classification.InterfaceAudience;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.monitoring.LogMonitoring;
import org.apache.hadoop.hbase.monitoring.StateDumpServlet;
import org.apache.hadoop.hbase.monitoring.TaskMonitor;
import org.apache.hadoop.hbase.util.Threads;

@InterfaceAudience.Private
public class RSDumpServlet extends StateDumpServlet {
  private static final long serialVersionUID = 1L;
  private static final String LINE =
    "===========================================================";

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    HRegionServer hrs = (HRegionServer)getServletContext().getAttribute(
        HRegionServer.REGIONSERVER);
    assert hrs != null : "No RS in context!";

    response.setContentType("text/plain");

    if (!hrs.isOnline()) {
      response.getWriter().write("The RegionServer is initializing!");
      response.getWriter().close();
      return;
    }

    OutputStream os = response.getOutputStream();
    PrintWriter out = new PrintWriter(os);

    out.println("RegionServer status for " + hrs.getServerName()
        + " as of " + new Date());

    out.println("\n\nVersion Info:");
    out.println(LINE);
    dumpVersionInfo(out);

    out.println("\n\nTasks:");
    out.println(LINE);
    TaskMonitor.get().dumpAsText(out);

    out.println("\n\nRowLocks:");
    out.println(LINE);
    dumpRowLock(hrs, out);

    out.println("\n\nExecutors:");
    out.println(LINE);
    dumpExecutors(hrs.getExecutorService(), out);

    out.println("\n\nStacks:");
    out.println(LINE);
    PrintStream ps = new PrintStream(response.getOutputStream(), false, "UTF-8");
    Threads.printThreadInfo(ps, "");
    ps.flush();

    out.println("\n\nRS Configuration:");
    out.println(LINE);
    Configuration conf = hrs.getConfiguration();
    out.flush();
    conf.writeXml(os);
    os.flush();

    out.println("\n\nLogs");
    out.println(LINE);
    long tailKb = getTailKbParam(request);
    LogMonitoring.dumpTailOfLogs(out, tailKb);

    out.println("\n\nRS Queue:");
    out.println(LINE);
    if(isShowQueueDump(conf)) {
      dumpQueue(hrs, out);
    }

    out.flush();
  }

  public static void dumpRowLock(HRegionServer hrs, PrintWriter out) {
    StringBuilder sb = new StringBuilder();
    for (HRegion hRegion : hrs.getOnlineRegionsLocalContext()) {
      if (hRegion.getLockedRows().size() > 0) {
        for (HRegion.RowLockContext rowLockContext : hRegion.getLockedRows().values()) {
          sb.setLength(0);
          sb.append(hRegion.getTableDesc().getTableName()).append(",")
            .append(hRegion.getRegionInfo().getEncodedName()).append(",");
          sb.append(rowLockContext.toString());
          out.println(sb.toString());
        }
      }
    }
  }

  public static void dumpQueue(HRegionServer hrs, PrintWriter out)
      throws IOException {
    if (hrs.compactSplitThread != null) {
      // 1. Print out Compaction/Split Queue
      out.println("Compaction/Split Queue summary: "
          + hrs.compactSplitThread.toString() );
      out.println(hrs.compactSplitThread.dumpQueue());
    }

    if (hrs.cacheFlusher != null) {
      // 2. Print out flush Queue
      out.println("\nFlush Queue summary: "
          + hrs.cacheFlusher.toString());
      out.println(hrs.cacheFlusher.dumpQueue());
    }
  }
}
