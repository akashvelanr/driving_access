package com.driving_access.backend;

import com.machinezoo.sourceafis.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class fp_matcher {

    public FingerprintMatchResult matchFingerprints(byte[] templateBytes) {
        String url = "jdbc:sqlite:fingerprint.db";
        String sql = "SELECT name, template, eligibility FROM fingerprints";
        FingerprintMatchResult result = new FingerprintMatchResult();

        try (Connection conn = DriverManager.getConnection(url);
                PreparedStatement pstmt = conn.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery()) {

            boolean found = false;
            FingerprintTemplate template = new FingerprintTemplate(templateBytes);
            FingerprintMatcher matcher = new FingerprintMatcher(template);
            String match = null;
            Integer eligible = null;
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
