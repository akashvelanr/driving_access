package com.driving_access.backend;

import com.machinezoo.sourceafis.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

public class fp_matcher {

    @SuppressWarnings("null")
    public FingerprintMatchResult matchFingerprints(byte[] templateBytes) {
        String url = "jdbc:sqlite:fingerprint.db";
        String sql = "SELECT name, template, eligibility FROM fingerprints";
        FingerprintMatchResult result = new FingerprintMatchResult();

        try (Connection conn = DriverManager.getConnection(url);
                PreparedStatement pstmt = conn.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery()) {

            boolean found = false;
            FingerprintImage image = new FingerprintImage(templateBytes);
            FingerprintTemplate template = new FingerprintTemplate(image);
            FingerprintMatcher matcher = new FingerprintMatcher(template);
            String match = null;
            Integer eligible = null;
            byte[] matchTemplate = null;
            double max = Double.NEGATIVE_INFINITY;

            while (rs.next()) {
                String name = rs.getString("name");
                byte[] storedTemplate = rs.getBytes("template");
                int eligibility = rs.getInt("eligibility");

                FingerprintTemplate candidate = new FingerprintTemplate(storedTemplate);

                double similarity = matcher.match(candidate);
                if (similarity > max) {
                    max = similarity;
                    match = name;
                    eligible = eligibility;
                    matchTemplate = storedTemplate;
                }
                if (max > 50) {
                    break;
                }
            }

            if (max > 30) {
                found = true;
                result.setName(match);
                result.setEligibility(eligible);
                result.setSimilarity(max);
                if (eligible == 1) {
                    result.setMatchTempleteString(Base64.getEncoder().encodeToString(matchTemplate));
                }
            }

            if (!found) {
                result.setError("Fingerprint not found.");
            }

        } catch (Exception e) {
            result.setError("Error: " + e.getMessage());
        }

        return result;
    }

    // Helper class to store the result of the matching
    public static class FingerprintMatchResult {
        private String name;
        private Integer eligibility;
        private String matchTempleteString;
        private Double similarity;
        private String error;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getEligibility() {
            return eligibility;
        }

        public void setEligibility(Integer eligibility) {
            this.eligibility = eligibility;
        }

        public String getMatchTempleteString() {
            return matchTempleteString;
        }

        public void setMatchTempleteString(String matchTempleteString) {
            this.matchTempleteString = matchTempleteString;
        }

        public Double getSimilarity() {
            return similarity;
        }

        public void setSimilarity(Double similarity) {
            this.similarity = similarity;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}
