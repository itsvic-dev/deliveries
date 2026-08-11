// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.allegro

import dev.itsvic.parceltracker.api.Status
import org.jsoup.Jsoup

internal object AllegroParser {
  private val trackingKeys =
      setOf(
          "trackingnumber",
          "trackingid",
          "waybill",
          "waybillnumber",
          "parcelnumber",
          "shipmentnumber",
      )
  private val statusKeys =
      setOf(
          "status",
          "statuslabel",
          "statustext",
          "deliverystatus",
          "parcelstatus",
          "shipmentstatus",
      )
  private val titleKeys = setOf("title", "name", "offername", "productname", "itemname", "label")
  private val carrierKeys =
      setOf("carrier", "carriername", "provider", "deliverycompany", "logisticsoperator")
  private val etaKeys =
      setOf(
          "eta",
          "estimateddelivery",
          "estimateddeliverydate",
          "expecteddelivery",
          "deliverytime",
          "deliverydate",
      )
  private val pickupKeys =
      setOf("pickuppoint", "deliverypoint", "pointaddress", "locker", "pickupaddress")
  private val idKeys = setOf("packageid", "parcelid", "shipmentid", "deliveryid", "id")
  private val displayTextKeys =
      setOf(
          "description",
          "html",
          "label",
          "message",
          "name",
          "sublabel",
          "subtitle",
          "text",
          "title")

  fun parsePackages(payload: Any?): List<AllegroPackage> {
    val concrete = parseMboxCards(payload)
    if (concrete.isNotEmpty()) return mergeDuplicates(concrete)

    val candidates = mutableListOf<Map<*, *>>()
    walkPairs(payload).forEach { (key, value) ->
      if (normalizeKey(key) in setOf("packages", "parcels", "shipments", "deliveries")) {
        (value as? List<*>)?.filterIsInstance<Map<*, *>>()?.let(candidates::addAll)
      }
    }
    walkMappings(payload).forEach { candidate ->
      val visible = displayStrings(candidate)
      val type = candidate["type"]?.toString()?.lowercase().orEmpty()
      val semanticType =
          listOf("package", "parcel", "shipment", "delivery", "card").any(type::contains)
      if (visible.any(::looksLikeStatus) && (semanticType || visible.size > 1)) {
        candidates += candidate
      }
    }

    return mergeDuplicates(candidates.distinct().mapIndexedNotNull(::parseCandidate))
  }

  fun parsePackageDetails(payload: Any?, original: AllegroPackage): AllegroPackage {
    val visible = visibleComponentStrings(payload)
    val status = visible.firstOrNull(::looksLikeStatus) ?: original.status
    return original.copy(
        status = clean(status),
        eta = visible.firstOrNull(::looksLikeEta)?.let(::clean) ?: original.eta,
        pickupPoint =
            visible.firstOrNull(::looksLikePickupPoint)?.let(::clean) ?: original.pickupPoint,
        readyForPickup = isReadyForPickup(status),
        history = parseHistory(visible).ifEmpty { original.history },
    )
  }

  fun statusToAppStatus(status: String, readyForPickup: Boolean = false): Status {
    val value = status.lowercase()
    return when {
      readyForPickup || isReadyForPickup(status) -> Status.AwaitingPickup
      listOf("pickup time", "ostatni dzień", "ostatni dzien").any(value::contains) ->
          Status.PickupTimeEndingSoon
      listOf("picked up", "odebrana", "odebrano").any(value::contains) -> Status.PickedUp
      listOf("delivered", "doręczona", "doreczona", "dostarczona").any(value::contains) ->
          Status.Delivered
      listOf(
              "out for delivery",
              "w doręczeniu",
              "w doreczeniu",
              "przekazana do doręczenia",
              "przekazana do doreczenia")
          .any(value::contains) -> Status.OutForDelivery
      listOf("delayed", "opóźniona", "opozniona").any(value::contains) -> Status.Delayed
      listOf(
              "waiting for dispatch",
              "oczekuje na nadanie",
              "przygotowana przez nadawcę",
              "przygotowana przez nadawce")
          .any(value::contains) -> Status.Preadvice
      listOf("warehouse", "oddziale", "magazyn").any(value::contains) -> Status.InWarehouse
      listOf("in transit", "w drodze", "wyruszyła", "wyruszyla", "nadana", "shipped")
          .any(value::contains) -> Status.InTransit
      else -> Status.Unknown
    }
  }

  private fun parseMboxCards(payload: Any?): List<AllegroPackage> =
      walkMappings(payload)
          .mapNotNull { candidate ->
            val id = candidate["id"] as? String ?: return@mapNotNull null
            if (id.count { it == ':' } != 1) return@mapNotNull null
            val (carrierId, tracking) = id.split(':', limit = 2)
            if (carrierId.isBlank() ||
                tracking.isBlank() ||
                candidate["childComponents"] !is List<*>) {
              return@mapNotNull null
            }

            val visible = visibleComponentStrings(candidate)
            val status = visible.firstOrNull(::looksLikeStatus) ?: return@mapNotNull null
            val eta = visible.firstOrNull(::looksLikeEta).orEmpty()
            val pickup = visible.firstOrNull(::looksLikePickupPoint).orEmpty()
            val title =
                visible.asReversed().firstOrNull {
                  it != tracking &&
                      it != pickup &&
                      !looksLikeStatus(it) &&
                      !looksLikeSupportingText(it) &&
                      !looksLikeControlText(it)
                } ?: tracking
            AllegroPackage(
                packageId = tracking,
                title = clean(title),
                status = clean(status),
                carrier = carrierName(carrierId),
                trackingNumber = tracking,
                eta = clean(eta),
                pickupPoint = clean(pickup),
                readyForPickup = isReadyForPickup(status),
                carrierId = carrierId,
            )
          }
          .toList()

  private fun parseCandidate(index: Int, candidate: Map<*, *>): AllegroPackage? {
    var packageId = asText(findFirst(candidate, idKeys))
    val tracking = asText(findFirst(candidate, trackingKeys))
    var status = asText(findFirst(candidate, statusKeys))
    var title = asText(findFirst(candidate, titleKeys))
    var carrier = asText(findFirst(candidate, carrierKeys))
    val carrierId = asText(findFirst(candidate, setOf("carrierid")))
    var eta = asText(findFirst(candidate, etaKeys))
    val pickupPoint = asText(findFirst(candidate, pickupKeys))
    val visible = displayStrings(candidate)

    if (status.isBlank()) status = visible.firstOrNull(::looksLikeStatus).orEmpty()
    if (eta.isBlank()) eta = visible.firstOrNull(::looksLikeEta).orEmpty()
    if (title.isBlank() || looksLikeStatus(title) || looksLikeControlText(title)) {
      title =
          visible
              .asReversed()
              .firstOrNull {
                it != tracking &&
                    !looksLikeStatus(it) &&
                    !looksLikeSupportingText(it) &&
                    !looksLikeControlText(it)
              }
              .orEmpty()
    }
    if (carrier.isBlank() && carrierId.isNotBlank()) carrier = carrierName(carrierId)
    if (packageId.isBlank()) packageId = tracking.ifBlank { "package-${index + 1}" }
    if (title.isBlank()) title = tracking.ifBlank { "Package" }
    if (status.isBlank() && tracking.isBlank()) return null

    return AllegroPackage(
        packageId = clean(packageId),
        title = clean(title),
        status = clean(status.ifBlank { "Unknown" }),
        carrier = clean(carrier),
        trackingNumber = clean(tracking),
        eta = clean(eta),
        pickupPoint = clean(pickupPoint),
        readyForPickup = isReadyForPickup(status),
        carrierId = clean(carrierId),
    )
  }

  private fun mergeDuplicates(packages: List<AllegroPackage>): List<AllegroPackage> {
    val merged = linkedMapOf<String, AllegroPackage>()
    packages.forEachIndexed { index, packageItem ->
      val key =
          packageItem.trackingNumber
              .ifBlank { packageItem.packageId.ifBlank { index.toString() } }
              .lowercase()
      val previous = merged[key]
      merged[key] =
          if (previous == null) packageItem
          else
              previous.copy(
                  title =
                      listOf(previous.title, packageItem.title)
                          .filterNot { it == previous.trackingNumber || it == "Package" }
                          .maxByOrNull(String::length) ?: previous.title,
                  status =
                      listOf(previous.status, packageItem.status).firstOrNull {
                        it.isNotBlank() && it != "Unknown"
                      } ?: "Unknown",
                  carrier = previous.carrier.ifBlank { packageItem.carrier },
                  trackingNumber = previous.trackingNumber.ifBlank { packageItem.trackingNumber },
                  eta = previous.eta.ifBlank { packageItem.eta },
                  pickupPoint = previous.pickupPoint.ifBlank { packageItem.pickupPoint },
                  readyForPickup = previous.readyForPickup || packageItem.readyForPickup,
                  carrierId = previous.carrierId.ifBlank { packageItem.carrierId },
              )
    }
    return merged.values.toList()
  }

  private fun parseHistory(strings: List<String>): List<AllegroTrackingEvent> {
    val result = mutableListOf<AllegroTrackingEvent>()
    var previous = ""
    strings.forEach { text ->
      if (looksLikeTimestamp(text) && previous.isNotBlank() && looksLikeHistoryStatus(previous)) {
        AllegroTrackingEvent(previous, text).let { if (it !in result) result += it }
      }
      if (text.isNotBlank()) previous = text
    }
    return result
  }

  private fun visibleComponentStrings(value: Any?): List<String> {
    val result = mutableListOf<String>()
    fun visit(component: Any?) {
      val map = component as? Map<*, *> ?: return
      (map["text"] as? String)?.let(::clean)?.takeIf(String::isNotBlank)?.let(result::add)
      listOf("childComponents", "variants", "labelParts").forEach { key ->
        (map[key] as? List<*>)?.forEach(::visit)
      }
      listOf("content", "component").forEach { visit(map[it]) }
    }
    when (value) {
      is Map<*, *> -> visit(value)
      is List<*> -> value.forEach(::visit)
    }
    return result.distinct()
  }

  private fun displayStrings(value: Any?): List<String> =
      walkPairs(value)
          .filter { (key, child) -> normalizeKey(key) in displayTextKeys && child is String }
          .map { clean(it.second as String) }
          .filter(String::isNotBlank)
          .distinct()
          .toList()

  private fun findFirst(value: Any?, keys: Set<String>): Any? =
      walkPairs(value)
          .firstOrNull { (key, child) ->
            normalizeKey(key) in keys &&
                child !in listOf(null, "", emptyList<Any>(), emptyMap<Any, Any>())
          }
          ?.second

  private fun walkPairs(value: Any?): Sequence<Pair<String, Any?>> = sequence {
    when (value) {
      is Map<*, *> ->
          value.forEach { (key, child) ->
            yield(key.toString() to child)
            yieldAll(walkPairs(child))
          }
      is List<*> -> value.forEach { yieldAll(walkPairs(it)) }
    }
  }

  private fun walkMappings(value: Any?): Sequence<Map<*, *>> = sequence {
    when (value) {
      is Map<*, *> -> {
        yield(value)
        value.values.forEach { yieldAll(walkMappings(it)) }
      }
      is List<*> -> value.forEach { yieldAll(walkMappings(it)) }
    }
  }

  private fun asText(value: Any?): String =
      when (value) {
        null -> ""
        is String -> value
        is Number -> value.toString()
        is Map<*, *> ->
            listOf("text", "label", "name", "title", "value", "formatted")
                .firstNotNullOfOrNull { key ->
                  value[key]?.let(::asText)?.takeIf(String::isNotBlank)
                }
                .orEmpty()
        is List<*> -> value.map(::asText).filter(String::isNotBlank).joinToString(" - ")
        else -> value.toString()
      }

  private fun clean(value: String): String =
      Jsoup.parseBodyFragment(value)
          .text()
          .replace('\u00a0', ' ')
          .replace(Regex("\\s+"), " ")
          .trim()

  private fun normalizeKey(value: String): String =
      value.lowercase().replace(Regex("[^a-z0-9]"), "")

  private fun carrierName(carrierId: String): String =
      when (carrierId.lowercase()) {
        "inpost" -> "InPost"
        "allegro" -> "Allegro One"
        else -> carrierId.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
      }

  private fun looksLikeEta(value: String): Boolean =
      listOf("przewidywana dostawa", "estimated delivery", "delivery by")
          .any(value.lowercase()::contains)

  private fun looksLikePickupPoint(value: String): Boolean =
      listOf(
              "punkt wybrany przy zakupie",
              "allegro one box",
              "paczkomat",
              "pickup point",
              "parcel locker")
          .any(value.lowercase()::contains)

  private fun looksLikeStatus(value: String): Boolean =
      listOf(
              "ready for pickup",
              "gotowa do odbioru",
              "gotowe do odbioru",
              "odbierz przesyłkę",
              "w drodze",
              "wyruszyła do punktu",
              "wyruszyla do punktu",
              "wyruszyła w drogę",
              "wyruszyla w droge",
              "in transit",
              "out for delivery",
              "dzisiaj w punkcie",
              "przekazana do doręczenia",
              "przekazana do doreczenia",
              "w doręczeniu",
              "nadana",
              "shipped",
              "oczekuje na nadanie",
              "waiting for dispatch",
              "delivered",
              "doręczona",
              "doreczona",
              "dostarczona",
              "opóźniona",
              "opozniona",
              "delayed",
          )
          .any(value.lowercase()::contains)

  private fun isReadyForPickup(value: String): Boolean =
      listOf("ready for pickup", "gotowa do odbioru", "gotowe do odbioru", "odbierz")
          .any(value.lowercase()::contains)

  private fun looksLikeTimestamp(value: String): Boolean =
      Regex("\\b\\d{1,2}\\s+[\\p{L}.]+\\s+\\d{4},\\s*\\d{1,2}:\\d{2}\\b", RegexOption.IGNORE_CASE)
          .containsMatchIn(value) ||
          Regex("\\b\\d{4}-\\d{2}-\\d{2}[ T]\\d{1,2}:\\d{2}\\b").containsMatchIn(value)

  private fun looksLikeHistoryStatus(value: String): Boolean {
    if (looksLikeTimestamp(value) ||
        looksLikeEta(value) ||
        looksLikePickupPoint(value) ||
        looksLikeControlText(value))
        return false
    return normalizeKey(value) !in
        setOf("historiaprzesylki", "shipmenthistory", "twojzakup", "yourpurchase")
  }

  private fun looksLikeSupportingText(value: String): Boolean =
      looksLikeEta(value) ||
          listOf(
                  "pokaż na mapie",
                  "pokaz na mapie",
                  "show on map",
                  "szczegóły dostawy",
                  "szczegoly dostawy",
                  "delivery details",
                  "coś poszło nie tak",
                  "something went wrong")
              .any(value.lowercase()::contains)

  private fun looksLikeControlText(value: String): Boolean =
      normalizeKey(value) in
          setOf(
              "archivepackage",
              "deletepackage",
              "deliverydetails",
              "dodatkoweopcje",
              "moreoptions",
              "removepackage",
              "sharepackage",
              "szczegolydostawy",
              "udostepnijdopki",
              "udostepnijdoapki",
              "udostepnijprzesylke",
          )
}
