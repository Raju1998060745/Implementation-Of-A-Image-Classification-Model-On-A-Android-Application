package com.example.grocery
// Android imports.
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

// MainFragment class declaration, this class extends Fragment
class MainFragment : Fragment() {
    // Declare necessary variables and objects
    private lateinit var classifier: Classifier
    private val cart = Cart()
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var imageCapture: ImageCapture
    private lateinit var imageAnalyzer: ImageAnalysis
    private var latestImage: ImageProxy? = null
    private lateinit var cartText: TextView

    // This method is called to do initial creation of the fragment.
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_scanner, container, false)
        val scanButton = root.findViewById<Button>(R.id.button_scan) // replace with the ID of your button
        scanButton.setOnClickListener {
            // Process the image when the button is clicked
            processImage(classifier)
        }
        // Initialize the classifier
        classifier = Classifier(requireContext().assets, "fruit_model3.tflite")

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera(classifier) // start camera if permission has been granted by user
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        return root
    }
    // Method to process the image
    private fun processImage(classifier: Classifier) {
        latestImage?.let { image ->
            val rotationDegrees = image.imageInfo.rotationDegrees
            val bitmap = toBitmap(image)
            // Launch a coroutine to classify the image
            lifecycleScope.launch(Dispatchers.Default) {
                val result = classifier.classify(bitmap)
                withContext(Dispatchers.Main) {
                    val item = interpretResult(result)
                    showAddToCartDialog(item)
                }
            }
            // Close the image and set the latestImage to null
            image.close()
            latestImage = null
        }
    }
    // Method to show a dialog to add the item to the cart
    private fun showAddToCartDialog(item: String) {
        val currentContext = context
        if (currentContext != null) {
            val builder = AlertDialog.Builder(currentContext)
            builder.setTitle("Add to cart")
                .setMessage("Do you want to add $item to your cart?")
                .setPositiveButton("Yes") { _, _ ->
                    // Add item to the cart and start the CartActivity
                    cart.addItem(item)
                    val intent = Intent(currentContext, CartActivity::class.java)
                    startActivity(intent)
                }
                .setNegativeButton("No", null)
                .create()
                .show()
        }
    }

    // Method to start the camera
    private fun startCamera(classifier: Classifier) {
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider, classifier)
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    // Method to bind the camera preview
    private fun bindPreview(cameraProvider: ProcessCameraProvider, classifier: Classifier) {
        val previewView = view?.findViewById<PreviewView>(R.id.camera_view)

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView?.surfaceProvider)
            }
// Initialize the ImageCapture and ImageAnalysis
        imageCapture = ImageCapture.Builder().build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(Executors.newSingleThreadExecutor(), ImageAnalysis.Analyzer { image ->
                    latestImage = image
                })
            }
// Unbind all use cases before rebinding
        cameraProvider.unbindAll()
        // Bind use cases to camera
        try {
            cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, imageAnalyzer)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }
    // Method to check if all permissions are granted
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }
    // Method to handle the result of the user's permission request
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera(classifier)
            } else {
                Toast.makeText(requireContext(),
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                requireActivity().finish()
            }
        }
    }
    // Method to convert an ImageProxy to a Bitmap
    private fun toBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        //U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
    // Method to interpret the result from the classifier
    private fun interpretResult(result: Array<FloatArray>): String {
        // List of labels, in the order they were trained on
        val labels = listOf("Apple Braeburn", "Apple Crimson Snow", "Apple Golden 1", "Apple Golden 2", "Apple Golden 3", "Apple Granny Smith", "Apple Pink Lady", "Apple Red 1", "Apple Red 2", "Apple Red 3", "Apple Red Delicious", "Apple Red Yellow 1", "Apple Red Yellow 2", "Apricot", "Avocado", "Avocado ripe", "Banana", "Banana Lady Finger", "Banana Red", "Beetroot", "Blueberry", "Cactus fruit", "Cantaloupe 1", "Cantaloupe 2", "Carambula", "Cauliflower", "Cherry 1", "Cherry 2", "Cherry Rainier", "Cherry Wax Black", "Cherry Wax Red", "Cherry Wax Yellow", "Chestnut", "Clementine", "Cocos", "Corn", "Corn Husk", "Cucumber Ripe", "Cucumber Ripe 2", "Dates", "Eggplant", "Fig", "Ginger Root", "Granadilla", "Grape Blue", "Grape Pink", "Grape White", "Grape White 2", "Grape White 3", "Grape White 4", "Grapefruit Pink", "Grapefruit White", "Guava", "Hazelnut", "Huckleberry", "Kaki", "Kiwi", "Kohlrabi", "Kumquats", "Lemon", "Lemon Meyer", "Limes", "Lychee", "Mandarine", "Mango", "Mango Red", "Mangostan", "Maracuja", "Melon Piel de Sapo", "Mulberry", "Nectarine", "Nectarine Flat", "Nut Forest", "Nut Pecan", "Onion Red", "Onion Red Peeled", "Onion White", "Orange", "Papaya", "Passion Fruit", "Peach", "Peach 2", "Peach Flat", "Pear", "Pear 2", "Pear Abate", "Pear Forelle", "Pear Kaiser", "Pear Monster", "Pear Red", "Pear Stone", "Pear Williams", "Pepino", "Pepper Green", "Pepper Orange", "Pepper Red", "Pepper Yellow", "Physalis", "Physalis with Husk", "Pineapple", "Pineapple Mini", "Pitahaya Red", "Plum", "Plum 2", "Plum 3", "Pomegranate", "Pomelo Sweetie", "Potato Red", "Potato Red Washed", "Potato Sweet", "Potato White", "Quince", "Rambutan", "Raspberry", "Redcurrant", "Salak", "Strawberry", "Strawberry Wedge", "Tamarillo", "Tangelo", "Tomato 1", "Tomato 2", "Tomato 3", "Tomato 4", "Tomato Cherry Red", "Tomato Heart", "Tomato Maroon", "Tomato Yellow", "Tomato not Ripened", "Walnut", "Watermelon")


        // Find the index of the maximum result
        val maxIndex = result[0].indices.maxByOrNull { result[0][it] } ?: -1
        if (maxIndex >= labels.size || maxIndex < 0) {
            Log.e(TAG, "Max index out of bounds: $maxIndex")
            return ""
        }
        return labels[maxIndex]

    }

}

