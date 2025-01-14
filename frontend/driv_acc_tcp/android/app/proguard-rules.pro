# Keep classes related to image decoding
-keep class javax.imageio.** { *; }
-keep class java.awt.image.** { *; }

# Keep the ImageDecoder and related methods from optimization
-keep class com.machinezoo.sourceafis.ImageDecoder$DecodedImage { *; }
-keep class com.machinezoo.sourceafis.ImageDecoder$ImageIODecoder { *; }

# If you're using reflection, you may also need to keep those classes/methods
-keep class com.machinezoo.sourceafis.** { *; }
-dontwarn java.awt.image.BufferedImage
-dontwarn javax.imageio.ImageIO
