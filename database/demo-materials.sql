-- Optional development data. No stock quantities or stock batches are created.
-- Repeated executions preserve existing matching material records.
DO $$
DECLARE
  c RECORD;
  m RECORD;
  root_id BIGINT;
  category_id_value BIGINT;
BEGIN
  PERFORM pg_advisory_xact_lock(260203, 1);
  FOR c IN SELECT * FROM (VALUES
    ('电子元器件','电阻',10), ('电子元器件','电容',20), ('电子元器件','集成电路',30),
    ('开发与连接','开发板',10), ('开发与连接','连接线',20)
  ) AS x(root_name,child_name,ordering)
  LOOP
    INSERT INTO material_category(name,description,sort_order)
      SELECT c.root_name,'开发演示类目',10 WHERE NOT EXISTS(SELECT 1 FROM material_category WHERE parent_id IS NULL AND name=c.root_name);
    SELECT id INTO root_id FROM material_category WHERE parent_id IS NULL AND name=c.root_name;
    INSERT INTO material_category(parent_id,name,description,sort_order)
      SELECT root_id,c.child_name,'典型实验室材料分类',c.ordering
      WHERE NOT EXISTS(SELECT 1 FROM material_category WHERE parent_id=root_id AND name=c.child_name);
  END LOOP;
  FOR m IN SELECT * FROM (VALUES
    ('电子元器件','电阻','金属膜电阻 10kΩ','国巨','MFR-25','10kΩ，±1%，0.25W','个',0.1200,100),
    ('电子元器件','电阻','贴片电阻 1kΩ','国巨','RC0603FR-071KL','0603，1kΩ，±1%','个',0.0300,200),
    ('电子元器件','电容','陶瓷电容 100nF','风华高科','0603B104K500NT','0603，100nF，50V','个',0.0500,100),
    ('电子元器件','电容','电解电容 100μF','红宝石','25ZLH100MEFC','100μF，25V，径向引脚','个',0.4200,30),
    ('电子元器件','集成电路','STM32 微控制器','意法半导体','STM32F103C8T6','ARM Cortex-M3，LQFP48','片',12.5000,10),
    ('电子元器件','集成电路','NE555 定时器','德州仪器','NE555P','DIP-8，通用定时器','片',1.2000,20),
    ('开发与连接','开发板','ESP32 开发板','乐鑫','ESP32-DevKitC','Wi-Fi / 蓝牙，USB 供电','块',28.0000,5),
    ('开发与连接','开发板','STM32 最小系统板','微雪电子','Core103','STM32F103，扩展引脚','块',18.0000,5),
    ('开发与连接','连接线','杜邦线 公对母','正点原子','DPX-20','20cm，40 根一排','排',3.5000,20),
    ('开发与连接','连接线','USB Type-C 数据线','绿联','US288','USB-A 转 Type-C，1m','根',8.9900,5)
  ) AS x(root_name,category_name,item_name,brand_name,model_name,specification,unit_name,price,safety)
  LOOP
    SELECT mc.id INTO category_id_value FROM material_category mc JOIN material_category pc ON pc.id=mc.parent_id
      WHERE pc.name=m.root_name AND pc.parent_id IS NULL AND mc.name=m.category_name;
    INSERT INTO material_item(category_id,name,brand,model,spec,unit,unit_price,currency,safe_stock,description)
      SELECT category_id_value,m.item_name,m.brand_name,m.model_name,m.specification,m.unit_name,m.price,'CNY',m.safety,
        '开发演示档案。价格仅供功能演示；未创建库存、批次或入库记录。'
      WHERE NOT EXISTS(SELECT 1 FROM material_item WHERE category_id=category_id_value AND name=m.item_name AND brand=m.brand_name AND model=m.model_name);
  END LOOP;
END $$;

