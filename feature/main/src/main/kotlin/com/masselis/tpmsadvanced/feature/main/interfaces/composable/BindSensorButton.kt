@file:Suppress("NAME_SHADOWING")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.BindSensorTags.Button.tag
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.BindSensorTags.Dialog.addToFavoritesButton
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.BindSensorTags.Dialog.cancelButton
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.BindSensorButtonViewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.BindSensorButtonViewModel.State
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreBindings.Companion.BindSensorButtonViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun BindSensorButton(
    location: Location,
    modifier: Modifier = Modifier,
    viewModel: BindSensorButtonViewModel = LocalVehicleComponent.current
        .TyreComponent(location)
        .viewModel { BindSensorButtonViewModel(createSavedStateHandle()) }
) {
    val state by viewModel.stateFlow.collectAsState()
    BindSensorButton(
        location,
        state,
        viewModel.locations,
        viewModel::boundSensor,
        viewModel::configuredMacAddress,
        viewModel::bind,
        viewModel::saveMacAddress,
        modifier
    )
}

@Composable
private fun BindSensorButton(
    location: Location,
    state: State,
    locations: List<Location>,
    boundSensor: (Location) -> StateFlow<Sensor?>,
    configuredMacAddress: (Location) -> StateFlow<String?>,
    onBind: (Sensor) -> Unit,
    onSaveMacAddress: (Location, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    FilledTonalIconButton(
        onClick = { showDialog = true },
        modifier = modifier.testTag(tag(location))
    ) {
        Icon(
            ImageVector.vectorResource(R.drawable.link_variant_plus),
            contentDescription = "Select sensor for $location"
        )
    }
    if (showDialog)
        BindSensorDialog(
            initialLocation = location,
            locations = locations,
            state = state,
            boundSensor = boundSensor,
            configuredMacAddress = configuredMacAddress,
            onBind = onBind,
            onSaveMacAddress = onSaveMacAddress,
            onDismissRequest = { showDialog = false }
        )
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun BindSensorDialog(
    initialLocation: Location,
    locations: List<Location>,
    state: State,
    boundSensor: (Location) -> StateFlow<Sensor?>,
    configuredMacAddress: (Location) -> StateFlow<String?>,
    onBind: (Sensor) -> Unit,
    onSaveMacAddress: (Location, String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val location = initialLocation
    val configuredSensorFlow = remember(location) { boundSensor(location) }
    val configuredSensor by configuredSensorFlow.collectAsState()
    val configuredMacAddressFlow = remember(location) { configuredMacAddress(location) }
    val savedMacAddress by configuredMacAddressFlow.collectAsState()
    val currentMacAddress = savedMacAddress ?: configuredSensor?.macAddress
    val configuredMacAddresses = remember(locations) {
        locations.associateWith(configuredMacAddress)
    }.mapValues { (_, addressFlow) -> addressFlow.collectAsState().value }
    val sensorsByLocation = remember(locations) {
        locations.associateWith(boundSensor)
    }.mapValues { (_, sensorFlow) -> sensorFlow.collectAsState().value }
    val requestBond = state as? State.RequestBond
    var macAddress by remember(location) { mutableStateOf(currentMacAddress.orEmpty()) }
    val normalizedMacAddress = macAddress.trim().uppercase()
    val validMacAddress = MAC_ADDRESS.matches(normalizedMacAddress)
    val macAddressUsedElsewhere = locations.any { otherLocation ->
        otherLocation != location &&
            listOf(
                configuredMacAddresses[otherLocation],
                sensorsByLocation[otherLocation]?.macAddress
            ).any { it?.equals(normalizedMacAddress, ignoreCase = true) == true }
    }

    AlertDialog(
        title = {
            Text(
                "Select sensor for " +
                    location.toString()
                        .replace('_', ' ')
                        .lowercase()
                        .replaceFirstChar { it.uppercase() }
            )
        },
        text = {
            Column {
                Text(
                    currentMacAddress?.let { "Currently configured MAC address: $it" }
                        ?: "No MAC address is configured for this sensor."
                )
                requestBond?.let { request ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (request) {
                            is State.RequestBond.NewBinding ->
                                "A nearby sensor is available. You can bind it or enter its MAC address."

                            is State.RequestBond.AlreadyBound ->
                                "This sensor will be moved from \"${request.currentVehicle.name}\" " +
                                    "to \"${request.targetVehicle.name}\"."
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = macAddress,
                    onValueChange = { macAddress = it },
                    label = { Text("Sensor MAC address") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    singleLine = true,
                    isError = macAddress.isNotBlank() &&
                        (validMacAddress.not() || macAddressUsedElsewhere),
                    supportingText = {
                        if (macAddress.isNotBlank() && validMacAddress.not())
                            Text("Enter a valid Bluetooth MAC address.")
                        else if (macAddressUsedElsewhere)
                            Text("This MAC address is already assigned to another sensor.")
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii
                    ),
                    modifier = Modifier.testTag(BindSensorTags.Dialog.macAddressField)
                )
            }
        },
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                content = { Text("Cancel") },
                modifier = Modifier.testTag(cancelButton)
            )
        },
        confirmButton = {
            Row {
                TextButton(
                    enabled = requestBond != null ||
                        (validMacAddress && macAddressUsedElsewhere.not()),
                    onClick = {
                        onBind(
                            requestBond?.foundSensor?.copy(location = location)
                                ?: Sensor(normalizedMacAddress.hashCode(), location, normalizedMacAddress)
                        )
                        onDismissRequest()
                    },
                    content = { Text("Add to favorites") },
                    modifier = Modifier.testTag(addToFavoritesButton)
                )
                TextButton(
                    enabled = validMacAddress && macAddressUsedElsewhere.not(),
                    onClick = {
                        onSaveMacAddress(location, normalizedMacAddress)
                        onDismissRequest()
                    },
                    content = { Text("Save MAC") },
                    modifier = Modifier.testTag(BindSensorTags.Dialog.saveMacAddressButton)
                )
            }
        },
        modifier = Modifier.testTag(BindSensorTags.Dialog.root)
    )
}

@Preview
@Composable
private fun BindSensorButtonNewBindingPreview() {
    BindSensorButton(
        location = Location.Wheel.REAR_LEFT,
        state = State.RequestBond.NewBinding(previewSensor),
        locations = listOf(Location.Wheel.REAR_LEFT, Location.Wheel.REAR_RIGHT),
        boundSensor = { MutableStateFlow(null) },
        configuredMacAddress = { MutableStateFlow(null) },
        onBind = {},
        onSaveMacAddress = { _, _ -> }
    )
}

@Preview
@Composable
private fun BindSensorButtonAlreadyBoundPreview() {
    BindSensorButton(
        location = Location.Wheel.REAR_LEFT,
        state = State.RequestBond.AlreadyBound(
            previewSensor,
            previewVehicle,
            previewVehicle
        ),
        locations = listOf(Location.Wheel.REAR_LEFT),
        boundSensor = { MutableStateFlow(null) },
        configuredMacAddress = { MutableStateFlow(null) },
        onBind = {},
        onSaveMacAddress = { _, _ -> }
    )
}

private val MAC_ADDRESS = Regex("(?i)^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")

@Suppress("ConstPropertyName")
internal object BindSensorTags {
    object Button {
        fun tag(location: Location): String =
            "bindSensorButton_${location}"
    }

    object Dialog {
        const val root = "BindSensorTags_Dialog_root"
        const val addToFavoritesButton = "BindSensor_Dialog_addToFavoritesTag"
        const val cancelButton = "BindSensor_Dialog_cancel"
        const val macAddressField = "BindSensor_Dialog_macAddress"
        const val saveMacAddressButton = "BindSensor_Dialog_saveMacAddress"
    }
}
