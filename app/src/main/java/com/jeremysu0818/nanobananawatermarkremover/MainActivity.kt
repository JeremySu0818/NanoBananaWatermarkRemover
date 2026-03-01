package com.jeremysu0818.nanobananawatermarkremover

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    private var selectedImageUris by mutableStateOf<List<Uri>>(emptyList())
    private var currentImageIndex by mutableStateOf(0)
    private var currentOriginalBitmap by mutableStateOf<Bitmap?>(null)
    private var currentResultBitmap by mutableStateOf<Bitmap?>(null)
    private var isProcessing by mutableStateOf(false)
    private var processProgress by mutableStateOf(0)
    private var totalToProcess by mutableStateOf(0)

    private val selectImagesLauncher =
            registerForActivityResult(ActivityResultContracts.GetMultipleContents()) {
                    uris: List<Uri> ->
                if (uris.isNotEmpty()) {
                    selectedImageUris = uris
                    currentImageIndex = 0
                    currentOriginalBitmap = loadBitmapFromUri(uris[0])
                    currentResultBitmap = null
                    processProgress = 0
                    totalToProcess = uris.size
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) { WatermarkRemoverScreen() }
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val resolver = contentResolver
        val contentValues =
                ContentValues().apply {
                    put(
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            "watermark_removed_${System.currentTimeMillis()}.png"
                    )
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/WatermarkRemover")
                }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            try {
                resolver.openOutputStream(imageUri)?.use { outputStream: OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadCurrentImage(index: Int) {
        if (index in selectedImageUris.indices) {
            currentImageIndex = index
            currentOriginalBitmap = loadBitmapFromUri(selectedImageUris[index])
            currentResultBitmap = null
        }
    }

    @Composable
    fun WatermarkRemoverScreen() {
        val context = LocalContext.current

        Column(
                modifier =
                        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Watermark Remover", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { selectImagesLauncher.launch("image/*") }, enabled = !isProcessing) {
                Text(
                        if (selectedImageUris.isEmpty()) "Select Image(s)"
                        else "Select Other Image(s)"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedImageUris.isNotEmpty()) {
                Text(
                        text = "Selected: ${selectedImageUris.size} image(s)",
                        style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isProcessing) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Processing: $processProgress / $totalToProcess")
                Spacer(modifier = Modifier.height(16.dp))
            }

            val displayBitmap = currentResultBitmap ?: currentOriginalBitmap

            if (displayBitmap != null) {
                Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = "Image to process",
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedImageUris.size > 1) {
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                                onClick = { loadCurrentImage(currentImageIndex - 1) },
                                enabled = !isProcessing && currentImageIndex > 0
                        ) { Text("Previous") }
                        Text(
                                text = "${currentImageIndex + 1} / ${selectedImageUris.size}",
                                modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        Button(
                                onClick = { loadCurrentImage(currentImageIndex + 1) },
                                enabled =
                                        !isProcessing &&
                                                currentImageIndex < selectedImageUris.size - 1
                        ) { Text("Next") }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                            onClick = {
                                isProcessing = true
                                processProgress = 0
                                totalToProcess = selectedImageUris.size
                                val urisToProcess = selectedImageUris.toList()

                                Thread {
                                            val remover = WatermarkRemover(context)
                                            urisToProcess.forEachIndexed { index, uri ->
                                                try {
                                                    val original = loadBitmapFromUri(uri)
                                                    if (original != null) {
                                                        runOnUiThread {
                                                            currentImageIndex = index
                                                            currentOriginalBitmap = original
                                                            currentResultBitmap = null
                                                        }
                                                        val result =
                                                                remover.removeWatermark(original)
                                                        runOnUiThread {
                                                            currentResultBitmap = result
                                                        }
                                                        saveBitmapToGallery(result)
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                                runOnUiThread { processProgress = index + 1 }
                                            }
                                            runOnUiThread {
                                                isProcessing = false
                                                Toast.makeText(
                                                                context,
                                                                "Processed and saved $totalToProcess image(s)",
                                                                Toast.LENGTH_LONG
                                                        )
                                                        .show()
                                            }
                                        }
                                        .start()
                            },
                            enabled =
                                    !isProcessing &&
                                            selectedImageUris.isNotEmpty() &&
                                            processProgress < selectedImageUris.size
                    ) {
                        Text(
                                if (selectedImageUris.size > 1) "Process All & Save"
                                else "Remove & Save"
                        )
                    }
                }
            }
        }
    }
}
