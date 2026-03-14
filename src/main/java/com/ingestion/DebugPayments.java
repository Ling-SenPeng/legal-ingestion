package com.ingestion;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

public class DebugPayments {
	public static void main(String[] args) throws Exception {
		Properties props = new Properties();
		try (InputStream input = DebugPayments.class.getClassLoader().getResourceAsStream("config.properties")) {
			if (input != null) {
				props.load(input);
			}
		}

		String dbUrl = props.getProperty("db.url", "jdbc:postgresql://localhost:5432/legal_ingestion");
		String dbUser = props.getProperty("db.user", "ingestion_user");
		String dbPassword = props.getProperty("db.password", "ingestion_pass");

		try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
			System.out.println("Connected to database: " + dbUrl);
			System.out.println();

			// Check payment_records for pdf_document_id = 1
			try (Statement stmt = conn.createStatement()) {
				System.out.println("=== Payment Records for PDF Document ID 1 ===");
				ResultSet rs = stmt.executeQuery(
					"SELECT id, pdf_document_id, total_amount, payment_date, category FROM payment_records WHERE pdf_document_id = 1 ORDER BY payment_date"
				);

				int count = 0;
				while (rs.next()) {
					count++;
					System.out.println(
						"ID: " + rs.getLong(1) + 
						" | PDF: " + rs.getLong(2) + 
						" | Amount: " + rs.getBigDecimal(3) + 
						" | Date: " + rs.getDate(4) + 
						" | Category: " + rs.getString(5)
					);
				}
				System.out.println("Total records: " + count);
			}

			System.out.println();

			// Check extraction runs
			try (Statement stmt = conn.createStatement()) {
				System.out.println("=== Recent Extraction Runs for PDF Document ID 1 ===");
				ResultSet rs = stmt.executeQuery(
					"SELECT id, pdf_document_id, status, statement_count, created_at FROM pdf_payment_extraction_runs WHERE pdf_document_id = 1 ORDER BY created_at DESC LIMIT 5"
				);

				int count = 0;
				while (rs.next()) {
					count++;
					System.out.println(
						"Run ID: " + rs.getLong(1) + 
						" | PDF: " + rs.getLong(2) + 
						" | Status: " + rs.getString(3) + 
						" | Statements: " + rs.getInt(4) + 
						" | Created: " + rs.getTimestamp(5)
					);
				}
				System.out.println("Total runs: " + count);
			}

			System.out.println();

			// Check overall payment record count
			try (Statement stmt = conn.createStatement()) {
				ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM payment_records");
				if (rs.next()) {
					System.out.println("Total payment records in database: " + rs.getLong(1));
				}
			}

		}
	}
}
