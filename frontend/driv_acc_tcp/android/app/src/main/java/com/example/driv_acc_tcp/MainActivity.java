package com.example.driv_acc_tcp;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

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
                        String ServerIP = call.argument("ServerIP");
                        System.out.println("Capturing FingerPrint");

                        ExecutorService executor = Executors.newSingleThreadExecutor();
                        executor.execute(() -> {
                            // System.out.println("working2");
                            Socket socket = null;
                            try {
                                // Connect to the fingerprint server
                                socket = new Socket(ServerIP, 12345);
                                // System.out.println("working3");
                                OutputStream out = socket.getOutputStream();
                                InputStream in = socket.getInputStream();

                                byte[] bmpHeader = assembleBMPHeader(IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_DEPTH, true);
                                // System.out.println("working4");
                                // Prepare ByteBuffer for the full BMP file (header + image data)
                                int totalImageDataSize = IMAGE_WIDTH * IMAGE_HEIGHT;
                                int totalFileSize = bmpHeader.length + totalImageDataSize;
                                ByteBuffer imageDataBuffer = ByteBuffer.allocate(totalFileSize);
                                imageDataBuffer.put(bmpHeader);
                                // System.out.println("working5");
                                // Send the command to start scanning (0xBB)
                                out.write(0xBB);

                                // Wait for the IMAGE_START_SIGNATURE
                                // System.out.println("working6");
                                while (true) {
                                    int readData = in.read();
                                    if (readData == -1) {
                                        result.error("CONNECTION_ERROR", "Connection closed by server.", null);
                                        break;
                                    }
                                    if ((byte) readData == (byte) 0xAA) { // IMAGE_START_SIGNATURE
                                        break;
                                    }
                                }
                                // System.out.println("working3");
                                // Calculate the total bytes expected for the fingerprint image
                                int totalBytesExpected = (256 * 288) / 2; // IMAGE_WIDTH * IMAGE_HEIGHT / 2

                                int bytesRead = 0;
                                while (bytesRead < totalBytesExpected) {
                                    int readData = in.read();
                                    if (readData == -1) {
                                        result.error("READ_ERROR", "Read timed out.", null);
                                        break;
                                    }

                                    byte currByte = (byte) readData;
                                    // Add each byte twice to simulate two pixels in BMP format
                                    imageDataBuffer.put(currByte);
                                    imageDataBuffer.put(currByte);
                                    bytesRead++;
                                }

                                // Convert the ByteBuffer to a byte array
                                byte[] imageData = imageDataBuffer.array();
                                // Return the image data via the result
                                System.out.println("Fingerprint Captured");
                                result.success(imageData);

                            } catch (IOException e) {
                                result.error("IO_ERROR", "Error during image capture: " + e.getMessage(), null);

                            } finally {
                                try {
                                    if (socket != null) {
                                        socket.close();
                                    }
                                } catch (IOException e) {
                                    // Log any errors while closing the socket
                                    System.err.println("Failed to close socket: " + e.getMessage());
                                }
                            }

                        });

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
