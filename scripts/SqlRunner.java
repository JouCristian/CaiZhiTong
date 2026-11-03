import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
class SqlRunner {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("One SQL file is required");
        String sql=Files.readString(Path.of(args[0]),StandardCharsets.UTF_8);
        try (Connection connection=DriverManager.getConnection(System.getenv("DB_URL"),System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (Statement statement=connection.createStatement()) { statement.execute(sql); }
            connection.commit();
        }
        System.out.println("Development SQL applied successfully.");
    }
}

