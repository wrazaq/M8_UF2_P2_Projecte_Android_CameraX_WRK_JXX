package com.example.m8_uf2_p2_projecte_android_camerax_wrk_jxx;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;


import com.example.m8_uf2_p2_projecte_android_camerax_wrk_jxx.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    ImageButton cameraBtn, recordBtn, flipBtn, toggleFlash;
    private PreviewView previewView;
    private ImageView previewImageView;
    int cameraOrientation = CameraSelector.LENS_FACING_BACK;
    private final ActivityResultLauncher<String> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
        @Override
        public void onActivityResult(Boolean result) {
            if (result) {
                startCamera(cameraOrientation);
            }
        }
    });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        previewView = binding.cameraPreview;
        previewImageView = binding.previewImageView;
        cameraBtn = binding.cameraBtn;
        flipBtn = binding.flipCamera;
        toggleFlash = binding.toggleFlash;
        recordBtn = binding.recordBtn;

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(Manifest.permission.CAMERA);
        } else {
            startCamera(cameraOrientation);
        }
        flipBtn.setOnClickListener(v -> {
            if (cameraOrientation == CameraSelector.LENS_FACING_BACK) {
                cameraOrientation = CameraSelector.LENS_FACING_FRONT;
            } else {
                cameraOrientation = CameraSelector.LENS_FACING_BACK;
            }
            startCamera(cameraOrientation);
        });
    }
    private void startCamera(int cameraOrientation) {
        ListenableFuture<ProcessCameraProvider> listenableFuture = ProcessCameraProvider.getInstance(this);

        listenableFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = listenableFuture.get();
                Preview preview = new Preview.Builder().build();

                ImageCapture imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                        .build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(cameraOrientation)
                        .build();

                cameraProvider.unbindAll();

                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

                cameraBtn.setOnClickListener(v -> takePicture(imageCapture));
                toggleFlash.setOnClickListener(v -> setFlashIcon(camera));

                preview.setSurfaceProvider(previewView.getSurfaceProvider());

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private void takePicture(ImageCapture imageCapture) {
        final File file = new File(getExternalFilesDir(null), System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(outputFileOptions, Executors.newCachedThreadPool(), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                runOnUiThread(() -> {
                    saveImageToMediaStore(file);

                    startCamera(cameraOrientation);
                    showPreview(file.getPath());
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(MainActivity.this, "Failed to save: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                startCamera(cameraOrientation);
            }
        });
    }

    private void saveImageToMediaStore(File file) {
        ContentResolver contentResolver = getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, file.getName());
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator);

        Uri imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

        try {
            if (imageUri != null) {
                try (OutputStream outputStream = contentResolver.openOutputStream(imageUri);
                     FileInputStream inputStream = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        assert outputStream != null;
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showPreview(String imagePath) {
        previewImageView.setVisibility(View.VISIBLE);

        Picasso.get()
                .load(new File(imagePath))
                .into(previewImageView);

        new Handler().postDelayed(() -> {
            previewImageView.setVisibility(View.GONE);
            startCamera(cameraOrientation);
        }, 10000);
    }



    private void setFlashIcon(Camera camera) {
        if (camera.getCameraInfo().hasFlashUnit()) {
            if (camera.getCameraInfo().getTorchState().getValue() == 0) {
                camera.getCameraControl().enableTorch(true);
                toggleFlash.setImageResource(R.drawable.baseline_flash_on_24);
            } else {
                camera.getCameraControl().enableTorch(false);
                toggleFlash.setImageResource(R.drawable.baseline_flash_off_24);
            }
        } else {
           Toast.makeText(MainActivity.this, "Flash is not available", Toast.LENGTH_SHORT).show();
        }
    }
}
