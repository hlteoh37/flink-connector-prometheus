## Request Signer for Amazon Managed Prometheus (AMP)

Request signer implementation for Amazon Managed Prometheus (AMP)

The signer retrieves AWS credential using a `software.amazon.awssdk.auth.credentials.AwsCredentialsProvider`.
The default `com.amazonaws.auth.DefaultAWSCredentialsProviderChain` works in most of the cases. 
Alternatively, you can pass an instance of `AwsCredentialsProvider` to the signer constructor.

The Flink application requires `RemoteWrite` permissions to the AMP workspace (e.g. `AmazonPromethusRemoteWriteAccess`
policy).

### Sample usage

To enable request signing for Amazon Managed Prometheus, and instance of `AmazonManagedPrometheusWriteRequestSigner`
must be provided when building the `PrometheusSink` instance. The only required parameters are the AWS region and the 
AMP remote-write URL.

```java

// AWS region of the AMP workspace
String prometheusRegion = "us-east-1";

// Remote-Write URL of the AMP workspace
String prometheusRemoteWriteUrl = "https://aps-workspaces.us-east-1.amazonaws.com/workspaces/ws-091245678-9abc-def0-1234-56789abcdef0/api/v1/remote_write";

// Build the sink to AMP using the request signer
AsyncSinkBase<PrometheusTimeSeries, Types.TimeSeries> sink = PrometheusSink.builder()
        .setPrometheusRemoteWriteUrl(prometheusRemoteWriteUrl)
        .setRequestSigner(new AmazonManagedPrometheusWriteRequestSigner(prometheusRemoteWriteUrl, prometheusRegion))
        .build();
```