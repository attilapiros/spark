package org.apache.spark.network;

import com.google.common.base.Throwables;

public class Attila {
  private String str = 
    Throwables.getStackTraceAsString(new Exception("open TransportClientFactory"));
}
