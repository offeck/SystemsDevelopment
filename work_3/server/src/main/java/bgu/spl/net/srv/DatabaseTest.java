package bgu.spl.net.srv;

public class DatabaseTest {
    public static void main(String[] args) {
        System.out.println("Running DatabaseHandler Tests...");
        
        // 1. Test Connection & Registration
        String username = "javatestuser_" + System.currentTimeMillis();
        String password = "password";
        String regTime = "2023-01-01 00:00:00";
        
        System.out.println("Test 1: User Registration");
        String cmd = "INSERT INTO Users (username, password) VALUES ('" + username + "', '" + password + "')";
        String result = DatabaseHandler.sendSqlRequest(cmd);
        System.out.println("  Result: " + result);
        if (!"done".equals(result)) System.err.println("  FAILED");
        
        cmd = "INSERT INTO UserRegistrations (username, registration_datetime) VALUES ('" + username + "', '" + regTime + "')";
        DatabaseHandler.sendSqlRequest(cmd);

        // 2. Test Login
        System.out.println("Test 2: User Login");
        String loginTime = "2023-01-01 10:00:00";
        cmd = "INSERT INTO UserLogins (username, login_datetime) VALUES ('" + username + "', '" + loginTime + "')";
        result = DatabaseHandler.sendSqlRequest(cmd);
        System.out.println("  Result: " + result);
        if (!"done".equals(result)) System.err.println("  FAILED");

        // 3. Test File Upload
        System.out.println("Test 3: File Upload");
        String filename = "test_file.txt";
        cmd = "INSERT INTO FileTracking (filename, uploader, upload_datetime, game_channel) VALUES ('" + filename + "', '" + username + "', '" + loginTime + "', 'test_channel')";
        result = DatabaseHandler.sendSqlRequest(cmd);
        System.out.println("  Result: " + result);
        if (!"done".equals(result)) System.err.println("  FAILED");

        // Verify Data
        System.out.println("Test 4: Verify Data via Select");
        String query = "SELECT username FROM Users WHERE username='" + username + "'";
        String selectResult = DatabaseHandler.sendSqlRequest(query);
        System.out.println("  Result: " + selectResult);
        if (!selectResult.contains(username)) System.err.println("  FAILED");

        // 5. Test Multiple Users Login
        System.out.println("Test 5: Multiple Users Login");
        for (int i = 1; i <= 3; i++) {
            String multiUser = "multi_user_" + i + "_" + System.currentTimeMillis();
            String multiPass = "pass" + i;
            String multiTime = "2023-01-02 12:00:0" + i;

            System.out.println("  Registering " + multiUser);
            DatabaseHandler.sendSqlRequest("INSERT INTO Users (username, password) VALUES ('" + multiUser + "', '" + multiPass + "')");
            DatabaseHandler.sendSqlRequest("INSERT INTO UserRegistrations (username, registration_datetime) VALUES ('" + multiUser + "', '" + multiTime + "')");
            
            System.out.println("  Logging in " + multiUser);
            String res = DatabaseHandler.sendSqlRequest("INSERT INTO UserLogins (username, login_datetime) VALUES ('" + multiUser + "', '" + multiTime + "')");
            if (!"done".equals(res)) System.err.println("  FAILED for " + multiUser);
        }

        // 6. Test User Logout
        System.out.println("Test 6: User Logout");
        String logoutUser = "logout_user_" + System.currentTimeMillis();
        String logoutPass = "pass";
        String logoutRegTime = "2023-01-03 10:00:00";
        String logoutLoginTime = "2023-01-03 10:05:00";
        String logoutLogoutTime = "2023-01-03 11:00:00";

        // Register & Login first
        DatabaseHandler.sendSqlRequest("INSERT INTO Users (username, password) VALUES ('" + logoutUser + "', '" + logoutPass + "')");
        DatabaseHandler.sendSqlRequest("INSERT INTO UserRegistrations (username, registration_datetime) VALUES ('" + logoutUser + "', '" + logoutRegTime + "')");
        DatabaseHandler.sendSqlRequest("INSERT INTO UserLogins (username, login_datetime) VALUES ('" + logoutUser + "', '" + logoutLoginTime + "')");

        // Verify active login (expecting "None" or empty for logout_datetime)
        String activeLoginCheck = DatabaseHandler.sendSqlRequest("SELECT logout_datetime FROM UserLogins WHERE username='" + logoutUser + "'");
        System.out.println("  Active login check (expecting None): " + activeLoginCheck);

        // Perform Logout Update
        System.out.println("  Logging out " + logoutUser);
        String logoutCmd = "UPDATE UserLogins SET logout_datetime='" + logoutLogoutTime + "' WHERE username='" + logoutUser + "' AND logout_datetime IS NULL";
        String logoutRes = DatabaseHandler.sendSqlRequest(logoutCmd);
        if (!"done".equals(logoutRes)) System.err.println("  FAILED: Logout command failed");

        // Verify Logout Timestamp
        String logoutVerify = DatabaseHandler.sendSqlRequest("SELECT logout_datetime FROM UserLogins WHERE username='" + logoutUser + "' AND login_datetime='" + logoutLoginTime + "'");
        System.out.println("  Logout verification result: " + logoutVerify);
        if (!logoutVerify.contains(logoutLogoutTime)) System.err.println("  FAILED: Logout time verification failed");
        
        // Print Report
        System.out.println("\nTesting Report Generation:");
        DatabaseHandler.printReport();
    }
}
