import java.sql.*;

/** Creates only project-owned development/test databases; credentials come from the process environment. */
class DatabaseAdmin {
    public static void main(String[] args) throws Exception {
        String db = args.length > 0 && args[0].equals("e2e-db") ? "caizhitong_v070_demo_e2e_test" : args.length > 0 && args[0].equals("test-db") ? "caizhitong_test" : "caizhitong";
        if (args.length > 0 && args[0].equals("test-db") && System.getenv("TEST_DATABASE_NAME") != null) {
            db=System.getenv("TEST_DATABASE_NAME");
            if(!db.matches("caizhitong_[a-z0-9_]+_test"))throw new IllegalArgumentException("Only project-owned isolated test database names are allowed");
        }
        String port = System.getenv().getOrDefault("POSTGRES_PORT", "5432");
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:" + port + "/postgres",
                System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
            try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname=?")) {
                query.setString(1, db);
                try (ResultSet results = query.executeQuery()) {
                    if (!results.next()) {
                        try (Statement create = connection.createStatement()) { create.executeUpdate("CREATE DATABASE " + db); }
                    }
                }
            }
        }
        if (args.length > 1 && args[1].equals("reset-business")) {
            if (!db.equals("caizhitong_v070_demo_e2e_test")) throw new IllegalArgumentException("Only the dedicated E2E database can be reset");
            try (Connection c=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:"+port+"/"+db,System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"))) {
                try(var check=c.createStatement();var r=check.executeQuery("SELECT current_database()")){r.next();if(!db.equals(r.getString(1)))throw new IllegalStateException("Wrong E2E database");}
                var owned=java.util.Set.of("mail_notification","inventory_alert","return_order_line","return_order","scrap_order","stock_attachment","stock_transaction","issue_order_line","issue_order","issue_monthly_usage","stock_batch","inbound_order_line","inbound_order","material_item","material_category");
                var tables=new java.util.ArrayList<String>();
                try(var s=c.createStatement();var r=s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public'")){while(r.next())if(owned.contains(r.getString(1)))tables.add("public.\""+r.getString(1)+"\"");}
                if(!tables.isEmpty())try(var s=c.createStatement()){s.execute("TRUNCATE TABLE "+String.join(",",tables)+" RESTART IDENTITY");}
                try(var s=c.createStatement();var r=s.executeQuery("SELECT to_regclass('public.app_settings')")){r.next();if(r.getString(1)!=null)try(var reset=c.createStatement()){reset.executeUpdate("UPDATE app_settings SET system_name='\u6750\u667a\u901a',system_description='\u667a\u6167\u6750\u6599\u5e93\u6570\u5b57\u5316\u7ba1\u7406\u7cfb\u7edf',business_zone=NULL,academic_year=NULL,autumn_start=NULL,autumn_end=NULL,spring_start=NULL,spring_end=NULL,notification_recipients='',revision=0,updated_by=NULL WHERE id=1");}}
            }
            System.out.println("Reset dedicated E2E business fixtures only; user accounts and daily database are preserved.");
        }
        System.out.println("PostgreSQL ready: 127.0.0.1:" + port + " / " + db);
    }
}
