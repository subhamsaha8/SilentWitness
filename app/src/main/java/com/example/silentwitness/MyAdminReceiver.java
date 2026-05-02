package com.example.silentwitness;

import android.annotation.SuppressLint;
import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;

public class MyAdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onPasswordFailed(@NonNull Context context, @NonNull Intent intent) {
        super.onPasswordFailed(context, intent);

        Log.d("SilentWitness", "ভুল পাসওয়ার্ড ধরা পড়েছে! ছবি তোলার প্রস্তুতি শুরু...");

        // ৩ বার বা তার বেশি ভুল হলেই ছবি তুলবে
        // দেরি না করে দ্রুত ক্যামেরা ওপেন করার চেষ্টা করবে
        new Handler(Looper.getMainLooper()).postDelayed(() -> openCamera2(context), 500);
    }

    @SuppressLint("MissingPermission")
    private void openCamera2(Context context) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            // ১ নম্বর আইডি সাধারণত সামনের ক্যামেরা
            String cameraId = manager.getCameraIdList()[1];
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d("SilentWitness", "ক্যামেরা সফলভাবে খুলেছে!");
                    takePhoto(context, camera);
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e("SilentWitness", "ক্যামেরা এরর কোড: " + error);
                    camera.close();
                }
            }, null);
        } catch (Exception e) {
            Log.e("SilentWitness", "ক্যামেরা খুলতে ব্যর্থ: " + e.getMessage());
        }
    }

    private void takePhoto(Context context, CameraDevice camera) {
        try {
            // ছবির রেজোলিউশন ৬৪০x৪৮০ সেট করা হয়েছে যাতে দ্রুত প্রসেস হয়
            ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
            reader.setOnImageAvailableListener(r -> {
                try (Image image = r.acquireLatestImage()) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    saveToFile(context, bytes);
                } catch (Exception e) {
                    Log.e("SilentWitness", "ছবি সেভ করতে সমস্যা: " + e.getMessage());
                } finally {
                    camera.close();
                }
            }, null);

            camera.createCaptureSession(Collections.singletonList(reader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        builder.addTarget(reader.getSurface());
                        session.capture(builder.build(), null, null);
                    } catch (Exception e) {
                        Log.e("SilentWitness", "ক্যাপচার সেশন এরর: " + e.getMessage());
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    camera.close();
                }
            }, null);

        } catch (Exception e) {
            Log.e("SilentWitness", "সেশন তৈরি করতে ব্যর্থ: " + e.getMessage());
        }
    }

    private void saveToFile(Context context, byte[] bytes) {
        try {
            File folder = new File(context.getExternalFilesDir(null), "ThiefLogs");
            if (!folder.exists()) folder.mkdirs();

            File file = new File(folder, "thief_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
            }
            Log.d("SilentWitness", "সাফল্য! ছবি এখানে আছে: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e("SilentWitness", "ফাইল রাইটিং এরর: " + e.getMessage());
        }
    }
}