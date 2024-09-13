/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.flink.connector.prometheus.sink;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.connector.base.sink.AsyncSinkBase;
import org.apache.flink.connector.base.sink.AsyncSinkBaseBuilder;
import org.apache.flink.connector.prometheus.sink.http.PrometheusAsyncHttpClientBuilder;
import org.apache.flink.connector.prometheus.sink.prometheus.Types;
import org.apache.flink.util.Preconditions;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

/** Builder for Sink implementation. */
@PublicEvolving
public class PrometheusSinkBuilder
        extends AsyncSinkBaseBuilder<
                PrometheusTimeSeries, Types.TimeSeries, PrometheusSinkBuilder> {
    private static final Logger LOG = LoggerFactory.getLogger(PrometheusSinkBuilder.class);

    // Max batch size, in number of samples
    private static final int DEFAULT_MAX_BATCH_SIZE_IN_SAMPLES = 500;
    // Max time a record is buffered
    private static final long DEFAULT_MAX_TIME_IN_BUFFER_MS = 5000;
    // Max nr of requestEntry that will be buffered
    private static final int DEFAULT_MAX_BUFFERED_REQUESTS = 1000;
    // Metric Group name added to the custom metrics
    private static final String DEFAULT_METRIC_GROUP_NAME = "Prometheus";

    // Max in-flight requests is always = 1, to retain ordering
    private static final int MAX_IN_FLIGHT_REQUESTS = 1;

    private String prometheusRemoteWriteUrl;
    private PrometheusSinkConfiguration.RetryConfiguration retryConfiguration;
    private Integer socketTimeoutMs;
    private PrometheusRequestSigner requestSigner = null;
    private Integer maxBatchSizeInSamples;
    private Integer maxRecordSizeInSamples;
    private String httpUserAgent = null;
    private PrometheusSinkConfiguration.SinkWriterErrorHandlingBehaviorConfiguration
            errorHandlingBehaviorConfig = null;
    private String metricGroupName = null;

    @Override
    public AsyncSinkBase<PrometheusTimeSeries, Types.TimeSeries> build() {

        int actualMaxBatchSizeInSamples =
                Optional.ofNullable(maxBatchSizeInSamples)
                        .orElse(DEFAULT_MAX_BATCH_SIZE_IN_SAMPLES);
        int actualMaxBufferedRequests =
                Optional.ofNullable(getMaxBufferedRequests()).orElse(DEFAULT_MAX_BUFFERED_REQUESTS);
        long actualMaxTimeInBufferMS =
                Optional.ofNullable(getMaxTimeInBufferMS()).orElse(DEFAULT_MAX_TIME_IN_BUFFER_MS);

        int actualMaxRecordSizeInSamples =
                Optional.ofNullable(maxRecordSizeInSamples).orElse(maxBatchSizeInSamples);

        int actualSocketTimeoutMs =
                Optional.ofNullable(socketTimeoutMs)
                        .orElse(PrometheusAsyncHttpClientBuilder.DEFAULT_SOCKET_TIMEOUT_MS);

        String actualHttpUserAgent =
                Optional.ofNullable(httpUserAgent)
                        .orElse(PrometheusRemoteWriteHttpRequestBuilder.DEFAULT_USER_AGENT);

        PrometheusSinkConfiguration.SinkWriterErrorHandlingBehaviorConfiguration
                actualErrorHandlingBehaviorConfig =
                        Optional.ofNullable(errorHandlingBehaviorConfig)
                                .orElse(
                                        PrometheusSinkConfiguration
                                                .SinkWriterErrorHandlingBehaviorConfiguration
                                                .DEFAULT_BEHAVIORS);

        String actualMetricGroupName =
                Optional.ofNullable(metricGroupName).orElse(DEFAULT_METRIC_GROUP_NAME);

        Preconditions.checkArgument(
                StringUtils.isNotBlank(prometheusRemoteWriteUrl),
                "Missing or blank Prometheus Remote-Write URL");
        checkValidRemoteWriteUrl(prometheusRemoteWriteUrl);
        Preconditions.checkNotNull(retryConfiguration, "Missing retry configuration");
        Preconditions.checkArgument(
                actualMaxBatchSizeInSamples > 0, "Max batch size (in samples) must be positive");
        Preconditions.checkArgument(
                actualMaxRecordSizeInSamples <= actualMaxBatchSizeInSamples,
                "Max record size (in samples) must be <= Max batch size");

        LOG.info(
                "Prometheus sink configuration:"
                        + "\n\t\tmaxBatchSizeInSamples={}\n\t\tmaxRecordSizeInSamples={}"
                        + "\n\t\tmaxTimeInBufferMs={}\n\t\tmaxInFlightRequests={}\n\t\tmaxBufferedRequests={}"
                        + "\n\t\tinitialRetryDelayMs={}\n\t\tmaxRetryDelayMs={}\n\t\tmaxRetryCount={}"
                        + "\n\t\tsocketTimeoutMs={}\n\t\thttpUserAgent={}"
                        + "\n\t\tErrorHandlingBehaviour: onMaxRetryExceeded={}, onHttpClientIOFailure={}, onNonRetriableError={}",
                actualMaxBatchSizeInSamples,
                actualMaxRecordSizeInSamples,
                actualMaxTimeInBufferMS,
                MAX_IN_FLIGHT_REQUESTS,
                actualMaxBufferedRequests,
                retryConfiguration.getInitialRetryDelayMS(),
                retryConfiguration.getMaxRetryDelayMS(),
                retryConfiguration.getMaxRetryCount(),
                socketTimeoutMs,
                actualHttpUserAgent,
                actualErrorHandlingBehaviorConfig.getOnMaxRetryExceeded(),
                actualErrorHandlingBehaviorConfig.getOnHttpClientIOFail(),
                actualErrorHandlingBehaviorConfig.getOnPrometheusNonRetriableError());

        return new PrometheusSink(
                new PrometheusTimeSeriesConverter(),
                MAX_IN_FLIGHT_REQUESTS,
                actualMaxBufferedRequests,
                actualMaxBatchSizeInSamples,
                actualMaxRecordSizeInSamples,
                actualMaxTimeInBufferMS,
                prometheusRemoteWriteUrl,
                new PrometheusAsyncHttpClientBuilder(retryConfiguration)
                        .setSocketTimeout(actualSocketTimeoutMs),
                requestSigner,
                actualHttpUserAgent,
                actualErrorHandlingBehaviorConfig,
                actualMetricGroupName);
    }

    private static void checkValidRemoteWriteUrl(String url) {
        try {
            new URL(url);
        } catch (MalformedURLException mue) {
            throw new IllegalArgumentException("Invalid Remote-Write URL: " + url, mue);
        }
    }

    public PrometheusSinkBuilder setPrometheusRemoteWriteUrl(String prometheusRemoteWriteUrl) {
        this.prometheusRemoteWriteUrl = prometheusRemoteWriteUrl;
        return this;
    }

    public PrometheusSinkBuilder setRequestSigner(PrometheusRequestSigner requestSigner) {
        this.requestSigner = requestSigner;
        return this;
    }

    public PrometheusSinkBuilder setMaxBatchSizeInSamples(int maxBatchSizeInSamples) {
        this.maxBatchSizeInSamples = maxBatchSizeInSamples;
        return this;
    }

    public PrometheusSinkBuilder setMaxRecordSizeInSamples(int maxRecordSizeInSamples) {
        this.maxRecordSizeInSamples = maxRecordSizeInSamples;
        return this;
    }

    public PrometheusSinkBuilder setRetryConfiguration(
            PrometheusSinkConfiguration.RetryConfiguration retryConfiguration) {
        this.retryConfiguration = retryConfiguration;
        return this;
    }

    public PrometheusSinkBuilder setSocketTimeoutMs(int socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
        return this;
    }

    public PrometheusSinkBuilder setHttpUserAgent(String httpUserAgent) {
        this.httpUserAgent = httpUserAgent;
        return this;
    }

    public PrometheusSinkBuilder setErrorHandlingBehaviourConfiguration(
            PrometheusSinkConfiguration.SinkWriterErrorHandlingBehaviorConfiguration
                    errorHandlingBehaviorConfig) {
        this.errorHandlingBehaviorConfig = errorHandlingBehaviorConfig;
        return this;
    }

    public PrometheusSinkBuilder setMetricGroupName(String metricGroupName) {
        this.metricGroupName = metricGroupName;
        return this;
    }

    /// Disable accessing maxBatchSize, maxBatchSizeInBytes, and maxRecordSizeInBytes directly

    /** Not supported. Use setMaxBatchSizeInSamples(int) instead */
    @Override
    public PrometheusSinkBuilder setMaxBatchSize(int maxBatchSize) {
        throw new UnsupportedOperationException("maxBatchSize is not supported by this sink");
    }

    /** Not supported. Use setMaxBatchSizeInSamples(int) instead */
    @Override
    public PrometheusSinkBuilder setMaxBatchSizeInBytes(long maxBatchSizeInBytes) {
        throw new UnsupportedOperationException(
                "maxBatchSizeInBytes is not supported by this sink");
    }

    /** Not supported. Use setMaxRecordSizeInSamples(int) instead */
    @Override
    public PrometheusSinkBuilder setMaxRecordSizeInBytes(long maxRecordSizeInBytes) {
        throw new UnsupportedOperationException(
                "maxRecordSizeInBytes is not supported by this sink");
    }

    /** Not supported. Use getMaxBatchSizeInSamples() instead */
    @Override
    protected Integer getMaxBatchSize() {
        throw new UnsupportedOperationException("maxBatchSize is not supported by this sink");
    }

    /** Not supported. Use getMaxBatchSizeInSamples() instead */
    @Override
    protected Long getMaxBatchSizeInBytes() {
        throw new UnsupportedOperationException(
                "maxRecordSizeInBytes is not supported by this sink");
    }

    /** Not supported. Use getMaxRecordSizeInSamples() instead */
    @Override
    protected Long getMaxRecordSizeInBytes() {
        throw new UnsupportedOperationException(
                "maxRecordSizeInBytes is not supported by this sink");
    }
}