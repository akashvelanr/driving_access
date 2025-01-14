package com.driving_access.backend;

//import com.machinezoo.sourceafis.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
//import java.io.IOException;
import java.util.Base64;

@RestController
@RequestMapping("/fingerprint")
public class AccessController {
    private final fp_loader_to_db loader;
    private final fp_matcher matcher;

    public AccessController() {
        this.loader = new fp_loader_to_db();
        this.matcher = new fp_matcher();
    }

    @PostMapping("/load")
    public ResponseEntity<String> loadFingerprint(
            @RequestParam String name,
            @RequestParam int eligibility,
            @RequestBody String templateString) {
        try {
            byte[] templateBytes = Base64.getDecoder().decode(templateString);

            loader.loadFingerprintToDb(templateBytes, name, eligibility);
            return ResponseEntity.ok("Fingerprint loaded successfully!");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/check")
    public String hello() {
        return "Server working properly";
    }

    @PostMapping("/match")
    public ResponseEntity<Object> matchFingerprint(@RequestBody String templateString) {
        try {
            byte[] templateBytes = Base64.getDecoder().decode(templateString);
            // Match the fingerprint
            fp_matcher.FingerprintMatchResult result = matcher.matchFingerprints(templateBytes);

            if (result.getError() != null) {
                return ResponseEntity.status(456).body(result);
            }

            // Return match result
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/mat")
    public ResponseEntity<Object> mat(@RequestParam String a) {
        return ResponseEntity.status(500).body(a);
    }

}
