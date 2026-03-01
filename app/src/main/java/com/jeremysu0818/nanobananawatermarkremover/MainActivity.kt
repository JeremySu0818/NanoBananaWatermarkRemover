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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    private var selectedImageUri by mutableStateOf<Uri?>(null)
    private var originalBitmap by mutableStateOf<Bitmap?>(null)
    private var resultBitmap by mutableStateOf<Bitmap?>(null)
    private var isProcessing by mutableStateOf(false)

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            originalBitmap = loadBitmapFromUri(it)
            resultBitmap = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WatermarkRemoverScreen()
                }
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
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "watermark_removed_${System.currentTimeMillis()}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/WatermarkRemover")
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            try {
                resolver.openOutputStream(imageUri)?.use { outputStream: OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    Toast.makeText(this, "Saved to gallery", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Failed to create MediaStore entry", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun WatermarkRemoverScreen() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Watermark Remover", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { selectImageLauncher.launch("image/*") }) {
                Text("Select Image")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isProcessing) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Display current bitmap
            val displayBitmap = resultBitmap ?: originalBitmap

            if (displayBitmap != null) {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = "Image to process",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            if (originalBitmap != null) {
                                isProcessing = true
                                val remover = WatermarkRemover(context)
                                // Move to background thread since image processing might be slow
                                Thread {
                                    try {
                                        val newBitmap = remover.removeWatermark(originalBitmap!!)
                                        // Update UI on main thread
                                        runOnUiThread {
                                            resultBitmap = newBitmap
                                            isProcessing = false
                                        }
                                    } catch (e: Exception) {
                                        runOnUiThread {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                            isProcessing = false
                                        }
                                    }
                                }.start()
                            }
                        },
                        enabled = originalBitmap != null && !isProcessing && resultBitmap == null
                    ) {
                        Text("Remove")
                    }

                    Button(
                        onClick = {
                            resultBitmap?.let { saveBitmapToGallery(it) }
                        },
                        enabled = resultBitmap != null && !isProcessing
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}