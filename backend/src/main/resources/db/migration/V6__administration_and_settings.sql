ALTER TABLE sys_user ADD COLUMN last_login_at TIMESTAMPTZ;
ALTER TABLE sys_user ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0 CHECK(auth_version>=0);
CREATE INDEX idx_user_enabled_created ON sys_user(enabled,created_at DESC,id DESC);
CREATE TABLE app_settings (
 id INTEGER PRIMARY KEY CHECK(id=1),
 system_name VARCHAR(40) NOT NULL DEFAULT '材智通',
 system_description VARCHAR(200) NOT NULL DEFAULT '智慧材料库数字化管理系统',
 business_zone VARCHAR(64),
 academic_year INTEGER CHECK(academic_year BETWEEN 1990 AND 2200),
 autumn_start DATE, autumn_end DATE, spring_start DATE, spring_end DATE,
 notification_recipients VARCHAR(4000) NOT NULL DEFAULT '',
 revision BIGINT NOT NULL DEFAULT 0,
 updated_by BIGINT REFERENCES sys_user(id), updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK((academic_year IS NULL AND autumn_start IS NULL AND autumn_end IS NULL AND spring_start IS NULL AND spring_end IS NULL)
  OR (academic_year IS NOT NULL AND autumn_start IS NOT NULL AND autumn_end IS NOT NULL AND spring_start IS NOT NULL AND spring_end IS NOT NULL
  AND autumn_start<=autumn_end AND autumn_end<spring_start AND spring_start<=spring_end))
);
INSERT INTO app_settings(id) VALUES(1);
COMMENT ON COLUMN sys_user.auth_version IS '密码重置或停用递增，撤销已签发访问令牌；角色仍每次请求实时读取';
COMMENT ON TABLE app_settings IS '唯一全局业务配置；SMTP/JWT 密钥不在此表；时区在产生库存业务后锁定';
