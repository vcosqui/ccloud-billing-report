package io.confluent.billing

import com.github.kittinunf.fuel.Fuel.get
import com.github.kittinunf.fuel.Fuel.post
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.lang.System.getenv
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter.ofPattern

private val clusterId = "lkc-r5j6v1"

private const val clusterUrl = "https://api.telemetry.confluent.cloud/v2/metrics/cloud/query"
private const val tagsUrl = "https://psrc-68gz8.us-east-2.aws.confluent.cloud/catalog/v1/search/basic"

private val clusterHeaders = mapOf(
    "Authorization" to "Basic ${getenv("CLUSTER_SECRET")}",
    "Content-Type" to "application/json"
)
private val srHeaders = mapOf(
    "Authorization" to "Basic ${getenv("SR_SECRET")}"
)

private val receivedBytesPerTopicRequest = """{
      "aggregations": [ { "metric": "io.confluent.kafka.server/%s" } ],
      "filter": { 
          "filters": [
              { "field": "resource.kafka.id", "op": "EQ", "value": "$clusterId" },
              { "field": "metric.topic", "op": "EQ", "value": "%s" }
          ],
          "op": "AND"
      },
      "group_by": [ "metric.topic" ],
      "granularity": "P1D",
      "intervals": [ "${now().format(ofPattern("yyyy-MM-dd'T00:00:00'"))}-00:00/P1D" ],
      "limit": 1000
    }
""".trimIndent()

class KafkaTagsClusterMetrics {

    // https://docs.confluent.io/cloud/current/api.html#tag/Search-(v1)
    // https://docs.confluent.io/cloud/current/api.html
    // https://api.telemetry.confluent.cloud/docs/descriptors/datasets/cloud
    @Test
    fun metricsByTag() {
        listOf("account_1", "account_2").forEach { accountTag ->
            val taggedTopicNames = taggedResources(accountTag).getResourceName()
            taggedTopicNames.forEach {
                val topicReceivedBytes = topicMetric("received_bytes", it)
                System.err.println("$accountTag received_bytes $topicReceivedBytes")
            }
        }
    }

    private fun taggedResources(tagName: String): JSONArray {
        return JSONObject(String(get(tagsUrl, listOf(Pair("tag", tagName))).header(srHeaders).responseString().second.data))["entities"] as JSONArray
    }

    private fun topicMetric(metricName: String, topicName: String): JSONObject {
        return (JSONObject(
            String(
                post(clusterUrl).header(clusterHeaders)
                    .body(receivedBytesPerTopicRequest.format(metricName, topicName))
                    .responseString().second.body().toByteArray()
            )
        )["data"] as JSONArray)[0] as JSONObject
    }

    private fun JSONArray.getResourceName() =
        filter { (it as JSONObject)["typeName"] == "kafka_topic" }
            .map { ((it as JSONObject)["attributes"] as JSONObject)["name"] as String }

}
