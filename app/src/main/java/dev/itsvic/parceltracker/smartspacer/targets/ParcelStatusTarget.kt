package dev.itsvic.parceltracker.smartspacer.targets

import android.content.ComponentName
import android.util.Log
import android.graphics.drawable.Icon as AndroidIcon
import com.kieronquinn.app.smartspacer.sdk.model.SmartspaceTarget
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Icon
import com.kieronquinn.app.smartspacer.sdk.model.uitemplatedata.Text
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import com.kieronquinn.app.smartspacer.sdk.utils.TargetTemplate
import dev.itsvic.parceltracker.ParcelApplication
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.api.Status
import dev.itsvic.parceltracker.api.getParcel
import dev.itsvic.parceltracker.db.ParcelWithStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.String

class ParcelStatusTarget: SmartspacerTargetProvider() {
    override fun getSmartspaceTargets(smartspacerId: String): List<SmartspaceTarget> {
        val targets = mutableListOf<SmartspaceTarget>()

        val db = ParcelApplication.db
        val parcels: List<ParcelWithStatus> = runBlocking(Dispatchers.IO) {
            db.parcelDao().getAllNonArchivedWithStatusAsync()
        }

        // this doesn't limit the number of returned targets
        // it probably should, but I don't know whether to hardcode it or make configurable

        parcels.forEach { parcelWithStatus ->
            val parcel = parcelWithStatus.parcel

            val apiParcel = try {
                runBlocking(Dispatchers.IO) {
                    provideContext().getParcel(parcel.parcelId, parcel.postalCode, parcel.service)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to fetch, skipping", e)

                return@forEach
            }

            // TODO: is the one from API or db preferred
            val status = apiParcel.currentStatus //parcelWithStatus.status?.status

            targets.add(
                TargetTemplate.Basic(
                    id = "parcel_status_$smartspacerId",
                    componentName = ComponentName(
                        provideContext(),
                        ParcelStatusTarget::class.java
                    ),
//                    featureType = SmartspaceTarget.FEATURE_PACKAGE_TRACKING,
                    title = Text("${parcel.humanName}: ${status.name}"),
                    subtitle = Text(apiParcel.history.first().description),
                    icon = Icon(
                        AndroidIcon.createWithResource(
                            provideContext(),
                            // this when is the same as the one in ParcelRow, maybe unify those two?
                            when (status) {
                                Status.Preadvice -> R.drawable.outline_other_admission_24
                                Status.InTransit -> R.drawable.outline_local_shipping_24
                                Status.InWarehouse -> R.drawable.outline_warehouse_24
                                Status.Customs -> R.drawable.outline_search_24
                                Status.OutForDelivery -> R.drawable.outline_local_shipping_24
                                Status.DeliveryFailure -> R.drawable.outline_error_24
                                Status.AwaitingPickup -> R.drawable.outline_pin_drop_24
                                Status.Delivered, Status.PickedUp -> R.drawable.outline_check_24

                                else -> R.drawable.outline_question_mark_24
                            }
                        )
                    ),
                    onClick = null     // TODO: change
                ).create().apply {
                    isSensitive = true
                    canBeDismissed = false
                })
        }

        return targets
    }

    override fun getConfig(smartspacerId: String?): Config {
        return Config(
            label = "Parcel Status",
            description = "Shows the current status of a selected parcel",
            icon = AndroidIcon.createWithResource(provideContext(), R.drawable.package_2),
            allowAddingMoreThanOnce = true,
//            configActivity = // Optional: An Intent given as an option to the user in the settings UI after adding
//            setupActivity = // Optional: An Intent of an Activity presented to the user during setup (see below)
        )
    }

    override fun onDismiss(smartspacerId: String, targetId: String): Boolean {
        return false
    }

    companion object {
        private const val TAG = "ParcelStatusTarget"
    }

}