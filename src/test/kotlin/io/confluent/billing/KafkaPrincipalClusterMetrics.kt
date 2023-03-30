package io.confluent.billing

import com.github.kittinunf.fuel.Fuel.post
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.lang.System.getenv
import java.math.BigDecimal
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter.ofPattern

private const val clusterId = "lkc-r5j6v1"
private const val clusterUrl = "https://api.telemetry.confluent.cloud/v2/metrics/cloud/query"
private val clusterHeaders = mapOf(
    "Authorization" to "Basic ${getenv("SECRET")}",
    "Content-Type" to "application/json"
)
private val aggregatedMetricRequest = """
    {
      "aggregations": [ { "metric": "io.confluent.kafka.server/%s" } ],
      "filter": { "field": "resource.kafka.id", "op": "EQ", "value": "$clusterId"},
      "granularity": "P1D",
      "group_by": [ "metric.principal_id" ],
      "intervals": [ "${now().format(ofPattern("yyyy-MM-dd'T00:00:00'"))}-00:00/P1D" ],
      "limit": 1000
    }
""".trimIndent()
private val totalMetricRequest = """
    {
      "aggregations": [ { "metric": "io.confluent.kafka.server/%s" } ],
      "filter": { "field": "resource.kafka.id", "op": "EQ", "value": "$clusterId"},
      "granularity": "P1D",
      "intervals": [ "${now().format(ofPattern("yyyy-MM-dd'T00:00:00'"))}-00:00/P1D" ],
      "limit": 1000
    }
""".trimIndent()

class KafkaPrincipalClusterMetrics {
    //https://docs.confluent.io/cloud/current/monitoring/cluster-load-metric.html#details-about-cluster-load
    //https://docs.confluent.io/cloud/current/billing/overview.html#billing-dimensions
    // metrics for kafka cluster
    // https://api.telemetry.confluent.cloud/docs/descriptors/datasets/cloud
    // https://docs.confluent.io/cloud/current/monitoring/metrics-by-principal.html#use-the-metrics-api-to-track-usage-by-team
    // https://docs.confluent.io/cloud/current/clusters/client-quotas.html#monitoring-metrics-by-principal
    @Test
    fun metricsByPrincipal() {
        listOf("request_bytes", "response_bytes", "active_connection_count", "request_count").forEach {
            val aggregatedMetric = aggregatedMetricsQuery(it).toPrincipalMap()
            val totalMetric = totalMetricsQuery(it)["data"]
            System.err.println("------------\n$it total\n\t $totalMetric")
            System.err.println("\t $aggregatedMetric")
            System.err.println("\t ${aggregatedMetric.toPercentages()}")
        }
    }

    private fun aggregatedMetricsQuery(metric: String) =
        JSONObject(
            String(post(clusterUrl).header(clusterHeaders).body(aggregatedMetricRequest.format(metric)).responseString().second.body().toByteArray())
        )

    private fun totalMetricsQuery(metric: String) =
        JSONObject(
            String(post(clusterUrl).header(clusterHeaders).body(totalMetricRequest.format(metric)).responseString().second.body().toByteArray())
        )

    private fun JSONObject.toPrincipalMap(): Map<String, Double> {
        return (this["data"] as JSONArray).toList()
            .associate {
                Pair((it as Map<*, *>)["metric.principal_id"]!! as String, (it["value"]!! as BigDecimal).toDouble())
            }
    }

    private fun Map<String, Double>.toPercentages(): Map<String, String> =
        mapValues { ((it.value / values.sum()) * 100).toString().substringBefore(".") + "%" }

    fun Map<String, Double>.toString(): String = this.toString().replace(",", ",\n\t")

}