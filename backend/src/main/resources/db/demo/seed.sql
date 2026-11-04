-- Immutable synthetic fixtures. Repeat execution only inserts missing IDs, never resets records.
INSERT INTO demo_category(id,name) VALUES
(1,'示例·电阻'),
(2,'示例·电容'),
(3,'示例·集成电路'),
(4,'示例·开发板'),
(5,'示例·传感与连接')
ON CONFLICT DO NOTHING;
INSERT INTO demo_material(id,category_id,material_code,name,brand,model,spec,unit,unit_price,currency,safe_stock,description) VALUES
('1','1','DEMO-001','示例·金属膜电阻 10kΩ','示例元件','R-10K','10kΩ ±1% / 0.25W','只','0.10','CNY','100','合成公开演示档案；不对应实际采购或个人信息'),
('2','1','DEMO-002','示例·金属膜电阻 1kΩ','示例元件','R-1K','1kΩ ±1% / 0.25W','只','0.08','CNY','100','合成公开演示档案；不对应实际采购或个人信息'),
('3','1','DEMO-003','示例·贴片电阻 100Ω','示例精工','R0603-100','0603 / ±1%','只','0.03','CNY','200','合成公开演示档案；不对应实际采购或个人信息'),
('4','1','DEMO-004','示例·精密电阻 4.7kΩ','示例精工','R-4K7','4.7kΩ ±0.1%','只','0.35','CNY','50','合成公开演示档案；不对应实际采购或个人信息'),
('5','1','DEMO-005','示例·可调电位器','示例元件','VR-10K','10kΩ / 多圈','只','2.80','CNY','10','合成公开演示档案；不对应实际采购或个人信息'),
('6','2','DEMO-006','示例·陶瓷电容 100nF','示例元件','C-104','50V / X7R','只','0.12','CNY','100','合成公开演示档案；不对应实际采购或个人信息'),
('7','2','DEMO-007','示例·电解电容 100μF','示例元件','E-100','25V / 径向','只','0.65','CNY','30','合成公开演示档案；不对应实际采购或个人信息'),
('8','2','DEMO-008','示例·贴片电容 10μF','示例精工','C0805-10U','16V / X5R','只','0.28','CNY','100','合成公开演示档案；不对应实际采购或个人信息'),
('9','2','DEMO-009','示例·薄膜电容 1μF','示例精工','F-1U','100V / 薄膜','只','1.25','CNY','20','合成公开演示档案；不对应实际采购或个人信息'),
('10','2','DEMO-010','示例·超级电容','示例元件','SC-1F','5.5V / 1F','只','5.60','CNY','10','合成公开演示档案；不对应实际采购或个人信息'),
('11','3','DEMO-011','示例·运算放大器','示例半导体','OP-DUAL','双通道 / DIP-8','片','1.80','CNY','20','合成公开演示档案；不对应实际采购或个人信息'),
('12','3','DEMO-012','示例·定时器芯片','示例半导体','TIMER-8','DIP-8 / 通用','片','0.95','CNY','20','合成公开演示档案；不对应实际采购或个人信息'),
('13','3','DEMO-013','示例·逻辑门芯片','示例逻辑','LOGIC-04','六反相器 / DIP-14','片','1.40','CNY','20','合成公开演示档案；不对应实际采购或个人信息'),
('14','3','DEMO-014','示例·线性稳压芯片','示例电源','REG-33','3.3V / 800mA','片','0.75','CNY','30','合成公开演示档案；不对应实际采购或个人信息'),
('15','3','DEMO-015','示例·模数转换芯片','示例逻辑','ADC-12','12bit / SPI','片','8.50','CNY','10','合成公开演示档案；不对应实际采购或个人信息'),
('16','4','DEMO-016','示例·微控制器开发板','示例创客','MCU-M4','Cortex-M4 / USB','块','42.00','CNY','5','合成公开演示档案；不对应实际采购或个人信息'),
('17','4','DEMO-017','示例·无线开发板','示例创客','WIFI-32','Wi-Fi + BLE','块','28.00','CNY','5','合成公开演示档案；不对应实际采购或个人信息'),
('18','4','DEMO-018','示例·可编程逻辑板','示例逻辑','FPGA-EDU','教学版 / 40pin','块','168.00','USD','3','合成公开演示档案；不对应实际采购或个人信息'),
('19','4','DEMO-019','示例·微型控制板','示例创客','MCU-NANO','小尺寸 / USB-C','块','18.00','CNY','5','合成公开演示档案；不对应实际采购或个人信息'),
('20','4','DEMO-020','示例·接口扩展板','示例创客','IO-EXT','I2C / 16通道','块','15.00','CNY','5','合成公开演示档案；不对应实际采购或个人信息'),
('21','5','DEMO-021','示例·温湿度传感器','示例感知','TH-21','I2C / 数字输出','只','12.00','CNY','10','合成公开演示档案；不对应实际采购或个人信息'),
('22','5','DEMO-022','示例·光照传感器','示例感知','LUX-10','I2C / 16bit','只','6.50','CNY','10','合成公开演示档案；不对应实际采购或个人信息'),
('23','5','DEMO-023','示例·超声波模块','示例感知','US-04','2cm–400cm','只','4.50','CNY','10','合成公开演示档案；不对应实际采购或个人信息'),
('24','5','DEMO-024','示例·杜邦线套装','示例连接','WIRE-40','20cm / 40根','套','5.00','CNY','10','合成公开演示档案；不对应实际采购或个人信息'),
('25','5','DEMO-025','示例·面包板','示例连接','BB-830','830孔 / 无焊接','块','7.50','CNY','10','合成公开演示档案；不对应实际采购或个人信息')
ON CONFLICT DO NOTHING;
INSERT INTO demo_stock_batch(id,material_id,batch_code,quantity,initial_quantity,unit_price,location,received_on)
SELECT m.id*10+b.n,m.id,'DEMO-B-'||LPAD(m.id::TEXT,3,'0')||'-'||b.n,
 CASE WHEN m.id%5=0 THEN 0 WHEN m.id%5=1 THEN m.safe_stock*0.2 ELSE m.safe_stock*2 END,
 m.safe_stock*3,m.unit_price,'演示库位 '||m.category_id||'-'||b.n,DATE '2026-09-01'+b.n
FROM demo_material m CROSS JOIN generate_series(1,2) b(n)
ON CONFLICT DO NOTHING;
INSERT INTO demo_stock_event(id,batch_id,event_type,quantity,occurred_on,description)
SELECT id*10+1,id,'IN',initial_quantity,received_on,'合成示例入库，无实际采购记录' FROM demo_stock_batch
ON CONFLICT DO NOTHING;
INSERT INTO demo_stock_event(id,batch_id,event_type,quantity,occurred_on,description)
SELECT id*10+2,id,'OUT',initial_quantity-quantity,received_on+7,'合成示例消耗，无关联用户或真实单据' FROM demo_stock_batch WHERE initial_quantity>quantity
ON CONFLICT DO NOTHING;
