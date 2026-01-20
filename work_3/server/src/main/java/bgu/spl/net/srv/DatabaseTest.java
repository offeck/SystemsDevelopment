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
        
        // Print Report
        System.out.println("\nTesting Report Generation:");
        DatabaseHandler.printReport();
    }
}
