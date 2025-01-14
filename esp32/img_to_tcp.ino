#include <WiFi.h>
#include <fpm.h>

/* Wi-Fi Configuration */
const char* ssid = "Draken";
const char* password = "12345678";
const uint16_t serverPort = 12345; // TCP server port

WiFiServer tcpServer(serverPort);
WiFiClient client;

HardwareSerial fserial(2); // Use UART2 for communication with the fingerprint sensor
FPM finger(&fserial);
FPMSystemParams params;

/* for convenience */
#define PRINTF_BUF_SZ   60
char printfBuf[PRINTF_BUF_SZ];

void setup() {
    Serial.begin(115200); // Debug Serial
    fserial.begin(57600, SERIAL_8N1, 25, 26); // Initialize UART2 on pins 25 (RX) and 26 (TX)

    Serial.println("Initializing...");

    // Connect to Wi-Fi
    WiFi.begin(ssid, password);
    while (WiFi.status() != WL_CONNECTED) {
        delay(1000);
        Serial.println("Connecting to Wi-Fi...");
    }
    Serial.println("Wi-Fi connected!");
    Serial.print("IP Address: ");
    Serial.println(WiFi.localIP());

    // Start TCP server
    tcpServer.begin();
    Serial.printf("TCP server started on port %d\n", serverPort);

    // Initialize the fingerprint sensor
    if (finger.begin()) {
        finger.readParams(&params);
        Serial.println("Found fingerprint sensor!");
        Serial.print("Capacity: ");
        Serial.println(params.capacity);
        Serial.print("Packet length: ");
        Serial.println(FPM::packetLengths[static_cast<uint8_t>(params.packetLen)]);
    } else {
        Serial.println("Did not find fingerprint sensor :(");
        while (1) yield();
    }
}

void loop() {
    // Check if a client is connected
    if (!client || !client.connected()) {
        client = tcpServer.available();
        if (client) {
            Serial.println("Client connected!");
        }
        return;
    }

    // Check for incoming data from the client
    if (client.available() > 0) {
        uint8_t command = client.read();
        if (command == 0xBB) {
            Serial.println("Command received: Starting fingerprint scan...");
            imageToClient();
        }
    }

    yield(); // Prevent watchdog reset
}

uint32_t imageToClient() {
    FPMStatus status;

    /* Take a snapshot of the finger */
    Serial.println("\r\nPlace a finger.");
    client.println("Place your finger on the sensor.");

    do {
        status = finger.getImage();

        switch (status) {
            case FPMStatus::OK:
                Serial.println("Image taken.");
                client.println("Image taken.");
                break;

            case FPMStatus::NOFINGER:
                Serial.print(".");
                delay(500); // Avoid spamming
                break;

            default:
                snprintf(printfBuf, PRINTF_BUF_SZ, "getImage(): error 0x%X", static_cast<uint16_t>(status));
                Serial.println(printfBuf);
                client.println(printfBuf);
                break;
        }

        delay(500);
    } while (status != FPMStatus::OK);

    /* Initiate the image transfer */
    status = finger.downloadImage();

    switch (status) {
        case FPMStatus::OK:
            Serial.println("Starting image stream...");
            client.println("Starting image stream...");
            break;

        default:
            snprintf(printfBuf, PRINTF_BUF_SZ, "downloadImage(): error 0x%X", static_cast<uint16_t>(status));
            Serial.println(printfBuf);
            client.println(printfBuf);
            return 0;
    }

    /* Send the start signature to the client */
    client.write(0xAA);

    uint32_t totalRead = 0;
    uint16_t readLen = 0;
    //uint8_t buffer[1028];
    /* Stream the image data directly to the TCP client */
    bool readComplete = false;

    while (!readComplete) {
        bool ret = finger.readDataPacket(NULL, &client, &readLen, &readComplete);

        if (!ret) {
            snprintf(printfBuf, PRINTF_BUF_SZ, "readDataPacket(): failed after reading %u bytes", totalRead);
            Serial.println(printfBuf);
            client.println(printfBuf);
            return 0;
        }
        
        totalRead += readLen;
        yield();
    }

    Serial.println();
    Serial.printf("%u bytes transferred.\n", totalRead);
    client.printf("%u bytes transferred.\n", totalRead);

    return totalRead;
}
