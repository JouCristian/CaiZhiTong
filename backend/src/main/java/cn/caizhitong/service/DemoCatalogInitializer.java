package cn.caizhitong.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Only seeds demo_* tables; no credentials, users or formal business records. */
@Component
public class DemoCatalogInitializer implements ApplicationRunner {
    private final JdbcTemplate db;
    public DemoCatalogInitializer(JdbcTemplate db) { this.db=db; }
    @Override @Transactional public void run(ApplicationArguments args) {
        db.execute("SELECT pg_advisory_xact_lock(707001)");
        var seed=new ResourceDatabasePopulator(new ClassPathResource("db/demo/seed.sql"));
        seed.setSqlScriptEncoding("UTF-8");
        seed.execute(db.getDataSource());
    }
}
