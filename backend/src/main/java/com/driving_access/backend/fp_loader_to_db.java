package com.driving_access.backend;

//import com.machinezoo.sourceafis.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class fp_loader_to_db {

    // Method to load fingerprint to DB
    public void loadFingerprintToDb(byte[] templateBytes, String name, int eligibility) {
        try {
            // SQL query to insert fingerprint data into the database
            String sql = "INSERT INTO fingerprints (name, template, eligibility) VALUES (?, ?, ?)";

            // Establish a database connection and insert data
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:fingerprint.db");
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name); // Set the name
                pstmt.setBytes(2, templateBytes); // Set the fingerprint template
                pstmt.setInt(3, eligibility); // Set the eligibility

                // Execute the update (insert)
                pstmt.executeUpdate();
                System.out.println("Fingerprint for " + name + " added successfully.");
            }

        } catch (SQLException e) {
            // Specific exception for database errors
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace(); // Log stack trace for easier debugging
        } catch (Exception e) {
            // Catch any other exceptions
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(); // Log stack trace
        }
    }
}
