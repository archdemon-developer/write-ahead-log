package io.writeahead.log.segments.stores;

import io.writeahead.log.metrics.WalMetricsQuery;
import java.io.IOException;

public interface MetricStore {
  WalMetricsQuery getMetrics() throws IOException;
}
