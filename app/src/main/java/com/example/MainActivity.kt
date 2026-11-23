package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.authStateFlow
import com.example.core.configureAppCheck
import com.example.data.CloudSyncRepository
import com.example.data.DownloadBridge
import com.example.data.IndependentSearchRepository
import com.example.data.VortexAiRepository
import com.example.ui.AuthScreen
import com.example.ui.BrowserShell
import com.example.ui.BrowserViewModel
import com.example.ui.theme.VortexTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModel, or Firebase call
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val externalUrl = extractExternalUrl(intent)

        setContent {
            VortexTheme {
                VortexAppRoot(initialExternalUrl = externalUrl)
            }
        }
    }

    private fun extractExternalUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.dataString ?: return null
        return if (data.startsWith("http://") || data.startsWith("https://")) data else null
    }
}

@Composable
private fun VortexAppRoot(initialExternalUrl: String?) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(
        initialValue = auth.currentUser
    )
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { })
    } else {
        key(user.uid) {
            val viewModel: BrowserViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        BrowserViewModel(
                            cloudRepo = CloudSyncRepository(db),
                            searchRepo = IndependentSearchRepository(),
                            aiRepo = VortexAiRepository(),
                            downloadBridge = DownloadBridge(app.applicationContext),
                            currentUserId = user.uid,
                            initialExternalUrl = initialExternalUrl
                        )
                    }
                }
            )
            BrowserShell(
                viewModel = viewModel,
                userEmail = user.email
            )
        }
    }
}
