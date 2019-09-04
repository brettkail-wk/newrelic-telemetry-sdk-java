package com.newrelic.telemetry.spans.json;

import com.newrelic.telemetry.json.AttributesJson;
import com.newrelic.telemetry.json.JsonCommonBlockWriter;
import com.newrelic.telemetry.spans.Span;
import com.newrelic.telemetry.spans.SpanBatch;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SpanJsonCommonBlockWriter implements JsonCommonBlockWriter<Span, SpanBatch> {

  private AttributesJson attributesJson;

  @Override
  public void appendCommonJson(SpanBatch batch, StringBuilder builder) {
    if (!batch.hasCommonAttributes() && !batch.getTraceId().isPresent()) {
      return;
    }
    builder.append("\"common\":").append("{");
    appendTraceId(batch, builder);
    appendAttributes(batch, builder);
    builder.append("}");
  }

  private void appendTraceId(SpanBatch batch, StringBuilder builder) {
    if (batch.getTraceId().isPresent()) {
      builder.append("\"traceId\":\"").append(batch.getTraceId().get()).append("\"");
    }
  }

  private void appendAttributes(SpanBatch batch, StringBuilder builder) {
    if (batch.hasCommonAttributes()) {
      if (batch.getTraceId().isPresent()) {
        builder.append(",");
      }
      builder
          .append("\"attributes\":")
          .append(attributesJson.toJson(batch.getCommonAttributes().asMap()));
    }
  }
}
