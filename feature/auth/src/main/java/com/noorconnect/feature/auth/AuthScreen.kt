package com.noorconnect.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noorconnect.domain.model.AuthState

/**
 * Public entry point for this feature. :app calls this — it never touches AuthViewModel
 * or any internal composable directly. That's what lets this whole screen be replaced
 * (e.g. a redesigned onboarding flow) without :app changing at all.
 */
@Composable
fun AuthRoute(onAuthenticated: () -> Unit) {
    val viewModel: AuthViewModel = hiltViewModel()
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    if (authState is AuthState.Ready) {
        onAuthenticated()
        return
    }

    AuthScreen(
        state = authState,
        onSubmitPhone = viewModel::submitPhone,
        onSubmitCode = viewModel::submitCode,
        onSubmitPassword = viewModel::submitPassword,
    )
}

@Composable
private fun AuthScreen(
    state: AuthState,
    onSubmitPhone: (String) -> Unit,
    onSubmitCode: (String) -> Unit,
    onSubmitPassword: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            AuthState.Uninitialized -> CircularProgressIndicator()
            AuthState.WaitingForPhoneNumber -> PhoneStep(onSubmitPhone)
            AuthState.WaitingForCode -> CodeStep(onSubmitCode)
            AuthState.WaitingForPassword -> PasswordStep(onSubmitPassword)
            is AuthState.Error -> Text("حصل خطأ: ${state.message}")
            AuthState.LoggedOut -> PhoneStep(onSubmitPhone)
            AuthState.Ready -> Unit // handled in AuthRoute
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class) // defensive — DropdownMenu is stable in this BOM, but cheap insurance given past drift
private fun PhoneStep(onSubmit: (String) -> Unit) {
    var selectedCountry by remember { mutableStateOf(DefaultCountryCode) }
    var nationalNumber by remember { mutableStateOf("") }
    var countryMenuExpanded by remember { mutableStateOf(false) }

    Text("رقم التليفون")

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Country picker — a fixed-width box, not full ExposedDropdownMenuBox, since we
        // only need "tap to open a list and pick one", not text-filtering/autocomplete.
        Box {
            OutlinedButton(onClick = { countryMenuExpanded = true }) {
                Text("${selectedCountry.flag} +${selectedCountry.dialCode}")
            }
            DropdownMenu(expanded = countryMenuExpanded, onDismissRequest = { countryMenuExpanded = false }) {
                CountryCodes.forEach { country ->
                    DropdownMenuItem(
                        text = { Text("${country.flag} ${country.nameAr} (+${country.dialCode})") },
                        onClick = {
                            selectedCountry = country
                            countryMenuExpanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = nationalNumber,
            onValueChange = { input -> nationalNumber = input.filter { it.isDigit() } },
            placeholder = { Text("رقم الهاتف بدون الصفر في الأول") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
    }

    Button(
        onClick = {
            // National numbers are usually written with a leading 0 locally, but the E.164
            // format TDLib expects drops it — e.g. Egypt "01001234567" -> "+201001234567".
            val digits = nationalNumber.trimStart('0')
            onSubmit("+${selectedCountry.dialCode}$digits")
        },
        enabled = nationalNumber.isNotBlank(),
    ) { Text("متابعة") }
}

@Composable
private fun CodeStep(onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Text("كود التفعيل اللي وصلك")
    OutlinedTextField(value = code, onValueChange = { code = it }, modifier = Modifier.padding(vertical = 8.dp))
    Button(onClick = { onSubmit(code) }) { Text("تأكيد") }
}

@Composable
private fun PasswordStep(onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    Text("كلمة مرور التحقق بخطوتين")
    OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.padding(vertical = 8.dp))
    Button(onClick = { onSubmit(password) }) { Text("دخول") }
}
