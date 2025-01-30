package com.example.driv_acc_serial;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;

import android.util.Log;

import java.io.File;
import java.io.Serial;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.machinezoo.sourceafis.FingerprintMatcher;
import com.machinezoo.sourceafis.FingerprintTemplate;
import com.machinezoo.sourceafis.FingerprintImage;

public class MainActivity extends FlutterActivity {
    private static final String CHANNEL = "com.driving_access/backend";

    private static final String TAG = "FingerprintScanner";
    private static final int IMAGE_WIDTH = 256;
    private static final int IMAGE_HEIGHT = 288;
    private static final int IMAGE_DEPTH = 8;
    private static final byte IMAGE_START_SIGNATURE = (byte) 0xAA;
    private static final byte START_SCAN_COMMAND = (byte) 0xBB;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler((call, result) -> {
                    if (call.method.equals("matchFingerprint")) {
                        byte[] imageData = call.argument("ImageData");

                        if (imageData == null || imageData.length == 0) {
                            result.error("ERROR", "Invalid fingerprint data", null);
                            return;
                        }

                        Context context = getApplicationContext();
                        File dbFile = new File(context.getFilesDir(), "localfingerprint.db");

                        SQLiteDatabase db = null;
                        Cursor cursor = null;

                        try {
                            // Open or create the database
                            db = SQLiteDatabase.openOrCreateDatabase(dbFile.getAbsolutePath(), null);
                            db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS fingerprints (id INTEGER PRIMARY KEY AUTOINCREMENT, template BLOB)");

                            // Query the stored fingerprints
                            cursor = db.rawQuery("SELECT template FROM fingerprints", null);

                            // Decode the input template

                            FingerprintImage image = new FingerprintImage(imageData);
                            FingerprintTemplate template = new FingerprintTemplate(image);
                            FingerprintMatcher matcher = new FingerprintMatcher(template);

                            double max = Double.NEGATIVE_INFINITY;

                            // Match against stored templates
                            while (cursor.moveToNext()) {
                                byte[] storedTemplate = cursor.getBlob(cursor.getColumnIndex("template"));
                                FingerprintTemplate candidate = new FingerprintTemplate(storedTemplate);
                                double similarity = matcher.match(candidate);

                                if (similarity > max) {
                                    max = similarity;
                                }
                                if (max > 50) {
                                    break;
                                }
                            }

                            if (max > 30) {
                                result.success("Fingerprint Found");
                            } else {
                                result.error("ERROR", "Fingerprint not found", null);
                            }

                        } catch (Exception e) {
                            Log.e("FingerprintMatching", "Error during fingerprint matching", e);
                            result.error("ERROR", e.getMessage(), null);
                        } finally {
                            if (cursor != null)
                                cursor.close();
                            if (db != null)
                                db.close();
                        }
                    } else if (call.method.equals("loadFingerprintTemplate")) {
                        String base64Template = call.argument("base64Template");

                        if (base64Template == null || base64Template.isEmpty()) {
                            result.error("ERROR", "Invalid fingerprint data", null);
                            return;
                        }

                        Context context = getApplicationContext();
                        File dbFile = new File(context.getFilesDir(), "localfingerprint.db");

                        SQLiteDatabase db = null;

                        try {
                            // Open or create the database
                            db = SQLiteDatabase.openOrCreateDatabase(dbFile.getAbsolutePath(), null);
                            db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS fingerprints (id INTEGER PRIMARY KEY AUTOINCREMENT, template BLOB)");

                            // Decode the Base64 template
                            byte[] decodedTemplate = Base64.getDecoder().decode(base64Template);

                            // Insert the fingerprint template into the database
                            ContentValues values = new ContentValues();
                            values.put("template", decodedTemplate);

                            long newRowId = db.insert("fingerprints", null, values);

                            if (newRowId == -1) {
                                result.error("ERROR", "Failed to insert template into database", null);
                            } else {
                                result.success("Fingerprint template stored successfully with ID: " + newRowId);
                            }

                        } catch (IllegalArgumentException e) {
                            result.error("ERROR", "Invalid Base64 input", e.getMessage());
                        } catch (Exception e) {
                            Log.e("LoadTemplate", "Error loading fingerprint template", e);
                            result.error("ERROR", e.getMessage(), null);
                        } finally {
                            if (db != null) {
                                db.close();
                            }
                        }
                    } else if (call.method.equals("captureFingerprint")) {
                        System.out.println("Capturing FP");

                        new Thread(() -> {
                            UsbManager usbManager = (UsbManager) getSystemService(USB_SERVICE);
                            List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber()
                                    .findAllDrivers(usbManager);

                            if (drivers.isEmpty()) {
                                runOnUiThread(() -> result.error("NO_DEVICE", "No USB device found", null));
                                return;
                            }

                            UsbSerialDriver driver = drivers.get(0);
                            UsbDeviceConnection connection = null;

                            try {
                                connection = usbManager.openDevice(driver.getDevice());
                                if (connection == null) {
                                    runOnUiThread(() -> result.error("NO_PERMISSION",
                                            "Permission required to access USB device", null));
                                    return;
                                }

                                UsbSerialPort port = driver.getPorts().get(0);
                                port.open(connection);
                                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                                byte[] header = assembleBMPHeader(256, 288, 8, true);
                                byte[] imageData = new byte[header.length + (256 * 288)];
                                System.arraycopy(header, 0, imageData, 0, header.length);

                                port.write(new byte[] { (byte) 0xBB }, 1);

                                int index = header.length;
                                while (index < imageData.length) {
                                    byte[] buffer = new byte[32];
                                    int len = port.read(buffer, 1000);
                                    if (len > 0) {
                                        boolean flag = false;
                                        for (int i = 0; i < len; i++) {
                                            if (buffer[i] == (byte) 0xAA) {
                                                for (int j = i + 1; j < len; j++) {
                                                    imageData[index++] = buffer[j];
                                                    imageData[index++] = buffer[j];
                                                }
                                                flag = true;
                                                break;
                                            }
                                        }
                                        if (flag) {
                                            break;
                                        }

                                    }
                                }
                                for (int i = 0; i < (256 * 288) / 2; i++) {
                                    boolean flag = false;
                                    // System.out.println("sent:" + index);
                                    byte[] buffer = new byte[32];
                                    int len = port.read(buffer, 1000);
                                    if (len > 0) {
                                        for (int j = 0; j < len; j++) {
                                            if (index >= (imageData.length - 1)) {
                                                flag = true;
                                                break;
                                            }
                                            imageData[index++] = buffer[j];
                                            imageData[index++] = buffer[j];
                                        }
                                        if (flag)
                                            break;
                                    }
                                }

                                runOnUiThread(() -> result.success(imageData));
                            } catch (IOException e) {
                                runOnUiThread(() -> result.error("IO_ERROR", e.getMessage(), null));
                            } finally {
                                try {
                                    if (connection != null) {
                                        connection.close();
                                    }
                                } catch (Error ignored) {
                                }
                            }
                        }).start();
                    } else {
                        result.notImplemented();
                    }

                });

    }

    private byte[] assembleBMPHeader(int width, int height, int depth, boolean includePalette) {
        int byteWidth = ((depth * width + 31) / 32) * 4;
        int numColors = 1 << depth;
        int bmpPaletteSize = numColors * 4;
        int imageSize = byteWidth * height;

        int fileSize = includePalette ? 54 + bmpPaletteSize + imageSize : 54 + imageSize;
        int rasterOffset = includePalette ? 54 + bmpPaletteSize : 54;

        ByteBuffer header = ByteBuffer.allocate(rasterOffset).order(ByteOrder.LITTLE_ENDIAN);
        header.put("BM".getBytes());
        header.putInt(fileSize);
        header.putInt(0);
        header.putInt(rasterOffset);
        header.putInt(40);
        header.putInt(width);
        header.putInt(-height);
        header.putShort((short) 1);
        header.putShort((short) depth);
        header.putInt(0);
        header.putInt(imageSize);
        header.putInt(2835);
        header.putInt(2835);
        header.putInt(0);
        header.putInt(0);

        if (includePalette) {
            for (int i = 0; i < numColors; i++) {
                header.put((byte) i).put((byte) i).put((byte) i).put((byte) 0);
            }
        }

        return header.array();
    }
}

// ========================================================================
// package com.example.flutter_application_2;

// import android.content.Context;
// import android.content.res.AssetManager;
// import android.database.Cursor;
// import android.database.sqlite.SQLiteDatabase;
// import android.database.sqlite.SQLiteException;

// import android.hardware.usb.UsbDevice;
// import android.hardware.usb.UsbManager;
// import android.os.Bundle;
// import android.util.Log;

// import androidx.annotation.NonNull;

// import com.hoho.android.usbserial.driver.UsbSerialDriver;
// import com.hoho.android.usbserial.driver.UsbSerialPort;
// import com.hoho.android.usbserial.driver.UsbSerialProber;
// import android.hardware.usb.UsbDeviceConnection;
// import com.fazecast.jSerialComm.*;

// import io.flutter.embedding.android.FlutterActivity;
// import io.flutter.embedding.engine.FlutterEngine;
// import io.flutter.plugin.common.MethodChannel;
// import io.flutter.plugin.common.MethodCall;

// import com.machinezoo.sourceafis.FingerprintMatcher;
// import com.machinezoo.sourceafis.FingerprintTemplate;
// import com.machinezoo.sourceafis.FingerprintImage;

// import java.io.IOException;
// import java.io.Serial;
// import java.nio.ByteBuffer;
// import java.nio.ByteOrder;
// import java.util.*;

// import java.util.Base64;

// public class MainActivity extends FlutterActivity {
// private static final String CHANNEL = "com.driving_access/backend";
// static {
// System.loadLibrary("jSerialComm");
// }
// // private UsbSerialPort serialPort;
// private static final String TAG = "FingerprintScanner";
// private static final int IMAGE_WIDTH = 256;
// private static final int IMAGE_HEIGHT = 288;
// private static final int IMAGE_DEPTH = 8;
// private static final byte IMAGE_START_SIGNATURE = (byte) 0xAA;
// private static final byte START_SCAN_COMMAND = (byte) 0xBB;

// @Override
// public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
// super.configureFlutterEngine(flutterEngine);

// new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(),
// CHANNEL)
// .setMethodCallHandler((call, result) -> {
// if (call.method.equals("matchFingerprint")) {
// String base64Template = call.argument("imagePath");

// try {
// // Copy the database to internal storage
// String dbPath = copyDatabaseToInternalStorage();

// SQLiteDatabase db = SQLiteDatabase.openDatabase(dbPath, null,
// SQLiteDatabase.OPEN_READONLY);

// Cursor cursor = db.rawQuery("SELECT name, template, eligibility FROM
// fingerprints", null);

// byte[] encodedimg = Base64.getDecoder().decode(base64Template);
// FingerprintImage image = new FingerprintImage(encodedimg);
// FingerprintTemplate template = new FingerprintTemplate(image);
// FingerprintMatcher matcher = new FingerprintMatcher(template);

// String match = null;
// Integer eligible = null;
// double max = Double.NEGATIVE_INFINITY;

// while (cursor.moveToNext()) {
// String name = cursor.getString(0);
// byte[] storedTemplate = cursor.getBlob(1);
// int eligibility = cursor.getInt(2);

// FingerprintTemplate candidate = new FingerprintTemplate(storedTemplate);

// double similarity = matcher.match(candidate);
// if (similarity > max) {
// max = similarity;
// match = name;
// eligible = eligibility;
// }
// if (max > 50) {
// break;
// }
// }
// cursor.close();
// db.close();
// if (max > 30) {
// final String finalMatch = match;
// final Integer finalEligible = eligible;
// final double finalMax = max;

// result.success(new java.util.HashMap<String, Object>() {
// {
// put("name", finalMatch);
// put("eligibility", finalEligible);
// put("similarity", finalMax);
// }
// });
// } else {
// result.error("ERROR", "Fingerprint not found", null);
// }

// } catch (Exception e) {
// result.error("ERROR", e.getMessage(), null);
// }
// } else {
// result.notImplemented();
// }
// });

// new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(),
// CHANNEL)
// .setMethodCallHandler((call, result) -> {
// if (call.method.equals("captureFingerprint")) {

// captureFingerprintImage(result);

// } else {
// result.notImplemented();
// }
// });
// }

// private UsbSerialPort port;

// private byte[] captureFingerprintImage(MethodChannel.Result result) {

// new Thread(() -> {
// System.out.println("Capture fingerprint image");
// UsbManager usbManager = (UsbManager) getSystemService(USB_SERVICE);
// List<UsbSerialDriver> drivers =
// UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

// if (drivers.isEmpty()) {
// runOnUiThread(() -> result.error("NO_DEVICE", "No USB device found", null));
// return;
// }

// UsbSerialDriver driver = drivers.get(0);
// UsbDeviceConnection connection = null;

// try {
// connection = usbManager.openDevice(driver.getDevice());
// if (connection == null) {
// runOnUiThread(
// () -> result.error("NO_PERMISSION", "Permission required to access USB
// device", null));
// return;
// }

// port = driver.getPorts().get(0);
// port.open(connection);
// port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1,
// UsbSerialPort.PARITY_NONE);

// // Assemble BMP header and prepare for image data
// byte[] header = assembleBMPHeader(256, 288, 8, true);
// byte[] imageData = new byte[header.length + (256 * 288)];
// System.arraycopy(header, 0, imageData, 0, header.length);

// // Send command to request fingerprint image
// port.write(new byte[] { (byte) 0xBB }, 1);

// // byte[] buffer = new byte[32];
// int index = header.length;

// // Wait for image data to start
// while (true) {
// byte[] buffer = new byte[32];
// System.out.println("Waiting for image data...");
// int len = port.read(buffer, 1000);
// System.out.println("Received " + len + " bytes.");
// if (len > 0) { // Check for start byte
// boolean flag = false;
// for (int i = 0; i < len; i++) {
// if (buffer[i] == (byte) 0xAA) {
// for (int j = i + 1; j < len; j++) {
// imageData[index++] = buffer[j];
// imageData[index++] = buffer[j];
// }
// flag = true;
// break;
// }
// }
// if (flag) {
// break;
// }
// }
// System.out.print("Buffer received: " + (char) buffer[0]);
// }
// System.out.println("Successs");
// // Read the image data
// for (int i = 0; i < (256 * 288) / 2; i++) {
// boolean flag = false;
// System.out.println("sent:" + index);
// byte[] buffer = new byte[32];
// int len = port.read(buffer, 1000);
// if (len > 0) {
// for (int j = 0; j < len; j++) {
// if (index >= (imageData.length - 2)) {
// flag = true;
// break;
// }
// imageData[index++] = buffer[j];
// imageData[index++] = buffer[j];
// }
// if (flag)
// break;
// }
// }
// System.out.println("success2");

// // Return the captured image
// runOnUiThread(() -> result.success(imageData));
// } catch (IOException e) {
// runOnUiThread(() -> result.error("IO_ERROR", e.getMessage(), null));
// } finally {
// try {
// if (port != null) {
// port.close();
// }
// if (connection != null) {
// connection.close();
// }
// } catch (IOException ignored) {
// }
// }
// }).start();
// return null; // This is async, you can modify as per your return structure
// }

// private byte[] assembleBMPHeader(int width, int height, int depth, boolean
// includePalette) {
// int byteWidth = ((depth * width + 31) / 32) * 4;
// int numColors = 1 << depth;
// int bmpPaletteSize = numColors * 4;
// int imageSize = byteWidth * height;

// int fileSize = includePalette ? 54 + bmpPaletteSize + imageSize : 54 +
// imageSize;
// int rasterOffset = includePalette ? 54 + bmpPaletteSize : 54;

// ByteBuffer header =
// ByteBuffer.allocate(rasterOffset).order(ByteOrder.LITTLE_ENDIAN);
// header.put("BM".getBytes());
// header.putInt(fileSize);
// header.putInt(0);
// header.putInt(rasterOffset);
// header.putInt(40);
// header.putInt(width);
// header.putInt(-height);
// header.putShort((short) 1);
// header.putShort((short) depth);
// header.putInt(0);
// header.putInt(imageSize);
// header.putInt(2835);
// header.putInt(2835);
// header.putInt(0);
// header.putInt(0);

// if (includePalette) {
// for (int i = 0; i < numColors; i++) {
// header.put((byte) i).put((byte) i).put((byte) i).put((byte) 0);
// }
// }

// return header.array();
// }

// private String copyDatabaseToInternalStorage() {
// try {
// String dbName = "fingerprint.db";
// Context context = getApplicationContext();
// java.io.File dbFile = new java.io.File(context.getFilesDir(), dbName);

// if (!dbFile.exists()) {
// try (java.io.InputStream in = context.getAssets().open(dbName);
// java.io.OutputStream out = new java.io.FileOutputStream(dbFile)) {

// byte[] buffer = new byte[1024];
// int read;
// while ((read = in.read(buffer)) != -1) {
// out.write(buffer, 0, read);
// }
// }
// }
// return dbFile.getAbsolutePath();
// } catch (Exception e) {
// e.printStackTrace();
// return null;
// }
// }
// }
