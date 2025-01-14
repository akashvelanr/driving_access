#include <HardwareSerial.h>
#include <fpm.h>

/* Pin Configuration
 * pin #25 is Arduino RX <==> Sensor TX
 * pin #26 is Arduino TX <==> Sensor RX
 */
HardwareSerial fserial(2); // Use UART2

FPM finger(&fserial);
FPMSystemParams params;

/* for convenience */
#define PRINTF_BUF_SZ   60
char printfBuf[PRINTF_BUF_SZ];

void setup()
{
    Serial.begin(115200); // Debug Serial for PC communication
    fserial.begin(57600, SERIAL_8N1, 25, 26); // Initialize UART2 on pins 25 (RX) and 26 (TX)
    
    Serial.println("IMAGE-TO-SERIAL");

    if (finger.begin()) {
        finger.readParams(&params);
        Serial.println("Found fingerprint sensor!");
        Serial.print("Capacity: "); Serial.println(params.capacity);
        Serial.print("Packet length: "); Serial.println(FPM::packetLengths[static_cast<uint8_t>(params.packetLen)]);
    } 
    else {
        Serial.println("Did not find fingerprint sensor :(");
        while (1) yield();
    }    
}

void loop()
{
    // Wait for the "0xBB" command from the PC to start scanning
    if (Serial.available() > 0) {
        uint8_t command = Serial.read();
        if (command == 0xBB) {
            Serial.println("Command received: Starting fingerprint scan...");
            imageToPc();
        }
    }

    yield(); // Prevent watchdog reset
}

uint32_t imageToPc(void)
{
    FPMStatus status;
    
    /* Take a snapshot of the finger */
    Serial.println("\r\nPlace a finger.");
    
    do {
        status = finger.getImage();
        
        switch (status) 
        {
            case FPMStatus::OK:
                Serial.println("Image taken.");
                break;
                
            case FPMStatus::NOFINGER:
                Serial.print(".");
                break;
                
            default:
                /* Allow retries even when an error happens */
                snprintf(printfBuf, PRINTF_BUF_SZ, "getImage(): error 0x%X", static_cast<uint16_t>(status));
                Serial.println(printfBuf);
                break;
        }
        
        delay(500); // Slight delay to avoid spamming the sensor
    }
    while (status != FPMStatus::OK);
    
    /* Initiate the image transfer */
    status = finger.downloadImage();
    
    switch (status) 
    {
        case FPMStatus::OK:
            Serial.println("Starting image stream...");
            break;
            
        default:
            snprintf(printfBuf, PRINTF_BUF_SZ, "downloadImage(): error 0x%X", static_cast<uint16_t>(status));
            Serial.println(printfBuf);
            return 0;
    }

    /* Send the start signature to the PC to indicate the image stream */
    Serial.write(0xAA);
    
    uint32_t totalRead = 0;
    uint16_t readLen = 0;
    
    /* Stream the image data directly to the Serial interface */
    bool readComplete = false;

    while (!readComplete) 
    {
        bool ret = finger.readDataPacket(NULL, &Serial, &readLen, &readComplete);
        
        if (!ret)
        {
            snprintf(printfBuf, PRINTF_BUF_SZ, "readDataPacket(): failed after reading %u bytes", totalRead);
            Serial.println(printfBuf);
            return 0;
        }
        
        totalRead += readLen;
        yield();
    }

    Serial.println();
    Serial.print(totalRead); Serial.println(" bytes transferred.");
    return totalRead;
}
