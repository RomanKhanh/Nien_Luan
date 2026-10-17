-- =====================================================================
-- Thêm 60 sản phẩm mẫu: đủ độ tuổi 0-18 và đủ mức điểm kỹ năng 0-10.
-- Chạy SAU khi app đã khởi động ít nhất một lần (bảng + 4 skill gốc đã có).
-- Chạy lại nhiều lần vẫn an toàn (bỏ qua nếu trùng tên / trùng khóa).
-- search_text để NULL: khởi động lại app thì ProductSearchTextBackfillRunner tự điền.
-- =====================================================================
BEGIN;

-- 1. Danh mục (3 danh mục mới; 6 danh mục cũ nếu chưa có cũng được tạo)
INSERT INTO categories (name, description) VALUES
  ('Bộ lắp ráp', 'Lắp ghép mô hình, khối xây dựng'),
  ('Robot & lập trình', 'Robot, mạch điện, lập trình không màn hình'),
  ('Xếp hình & câu đố', 'Puzzle, khối gỗ, câu đố logic'),
  ('Khoa học thí nghiệm', 'Bộ thí nghiệm khoa học tại nhà'),
  ('Mỹ thuật sáng tạo', 'Vẽ, nặn, thủ công'),
  ('Board game tư duy', 'Trò chơi bàn cờ rèn chiến thuật'),
  ('Toán học & số', 'Học số, phép tính, hình học qua trò chơi'),
  ('Âm nhạc & giác quan', 'Nhạc cụ, đồ chơi kích thích giác quan'),
  ('Vận động & phối hợp', 'Đồ chơi vận động, phối hợp tay mắt')
ON CONFLICT (name) DO NOTHING;

-- 2. Dữ liệu sản phẩm (bảng tạm)
CREATE TEMP TABLE seed_products (
  name text, category text, price numeric, stock int, min_age int, max_age int,
  logic int, creative int, ps int, stem int,
  weight int, len int, wid int, hei int, descr text
) ON COMMIT DROP;

INSERT INTO seed_products VALUES
-- ===== 0 - 3 tuổi =====
('Bộ xếp chồng cốc màu sắc 10 chi tiết','Xếp hình & câu đố',120000,60,0,3, 2,3,3,1, 450,25,20,12,'Cốc nhựa an toàn để xếp tháp, lồng nhau, dùng được cả khi tắm.'),
('Thảm đàn piano chạm chân','Âm nhạc & giác quan',260000,30,0,3, 0,6,2,1, 900,60,30,6,'Thảm phát nhạc khi bé đạp lên phím, kích thích nghe nhìn.'),
('Xúc xắc vải mềm 6 mặt','Âm nhạc & giác quan',85000,100,0,2, 1,3,1,0, 200,15,15,15,'Xúc xắc vải có tiếng lục lạc, họa tiết tương phản cao.'),
('Khối gỗ thả hình 3D','Xếp hình & câu đố',155000,45,1,3, 4,2,5,1, 700,22,22,15,'Thả khối hình học vào đúng lỗ, tập nhận biết hình dạng và thử sai.'),
('Sách vải lật mở giác quan','Âm nhạc & giác quan',140000,50,0,3, 1,5,1,0, 300,20,20,4,'Sách vải mềm nhiều chất liệu, có tiếng sột soạt khi bóp.'),
('Bóng gai massage vận động 6 quả','Vận động & phối hợp',99000,70,0,3, 0,2,1,0, 350,25,18,12,'Bóng cao su mềm nhiều cỡ để bé lăn, nắm, ném.'),
('Đàn gỗ xylophone 8 âm','Âm nhạc & giác quan',210000,40,1,4, 2,7,2,2, 800,30,16,10,'Đàn 8 thanh màu kèm 2 dùi gõ, làm quen cao độ âm thanh.'),

-- ===== 3 - 6 tuổi =====
('Bộ ghép hình gỗ 12 khối động vật','Xếp hình & câu đố',175000,55,2,5, 5,4,5,1, 900,28,22,8,'Ghép các khối gỗ thành hình con vật, rèn quan sát và tư duy không gian.'),
('Khối xây dựng cỡ lớn 80 chi tiết','Bộ lắp ráp',420000,25,2,5, 4,8,5,3, 1900,40,30,15,'Khối nhựa lớn dễ cầm, bé tự do xây nhà, cầu, tháp.'),
('Bảng số nam châm 1-20 kèm phép tính','Toán học & số',165000,48,3,6, 7,2,4,5, 600,30,22,4,'Học đếm, so sánh và làm quen phép cộng trừ đơn giản.'),
('Bộ đếm số que gỗ Montessori','Toán học & số',230000,32,3,6, 8,2,4,6, 1100,32,24,8,'Que gỗ và thẻ số giúp bé hiểu số lượng và thứ tự.'),
('Xe lắp ghép vặn ốc cỡ lớn','Bộ lắp ráp',280000,28,3,6, 4,6,7,5, 1000,34,24,10,'Bé tự vặn ốc lắp xe, rèn tay khéo và hiểu cấu tạo cơ bản.'),
('Bộ cát động lực 1kg kèm khuôn','Mỹ thuật sáng tạo',190000,65,3,7, 1,9,3,1, 1300,28,20,10,'Cát mềm không dính tay, nặn tạo hình theo ý thích.'),
('Bộ nhạc cụ gõ 10 món','Âm nhạc & giác quan',350000,22,3,7, 2,8,3,2, 1600,36,28,14,'Trống, lục lạc, tambourine... để bé khám phá nhịp điệu.'),
('Trò chơi nhảy ô vận động 4 màu','Vận động & phối hợp',240000,38,3,8, 3,3,5,0, 1500,40,30,6,'Thảm nhảy ô theo màu và hình, vận động và ghi nhớ.'),
('Bộ lắp ráp ống nước mê cung','Bộ lắp ráp',325000,20,4,8, 5,5,8,6, 1100,32,24,12,'Nối ống dẫn nước theo mê cung, thử sai để nước chảy đúng đường.'),
('Domino màu sắc 100 quân','Xếp hình & câu đố',185000,52,4,9, 6,6,6,3, 1200,30,20,8,'Xếp domino thành đường dài, bé học về nguyên nhân kết quả.'),
('Cờ lô tô hình ảnh 48 thẻ','Board game tư duy',130000,60,3,6, 4,2,3,1, 400,24,18,6,'Trò chơi ghép thẻ hình, rèn quan sát và ghi nhớ.'),
('Robot lập trình hình ong không màn hình','Robot & lập trình',1150000,9,4,8, 8,4,7,8, 900,28,20,12,'Bé bấm nút ra lệnh cho robot đi theo đường, làm quen lập trình.'),

-- ===== 6 - 9 tuổi =====
('Bộ lắp ráp cầu treo STEM 90 chi tiết','Bộ lắp ráp',395000,26,6,10, 6,6,8,9, 1200,34,26,9,'Lắp cầu treo và thử tải, học về lực căng và kết cấu.'),
('Rubik 3x3 tốc độ','Xếp hình & câu đố',120000,90,6,18, 9,2,9,2, 150,8,8,8,'Rubik trơn mượt, phù hợp từ nhập môn đến thi đấu.'),
('Bộ thí nghiệm cầu vồng và ánh sáng','Khoa học thí nghiệm',310000,34,6,10, 3,5,5,9, 900,32,24,8,'Lăng kính, thấu kính, bé tự quan sát phân tách ánh sáng.'),
('Bộ lắp ráp xe điều khiển từ xa 3 in 1','Robot & lập trình',560000,18,7,12, 5,6,7,8, 1400,38,28,10,'Lắp một trong ba mô hình xe và điều khiển bằng remote.'),
('Cờ vua nam châm bàn gấp','Board game tư duy',210000,44,6,18, 10,2,9,2, 800,30,30,4,'Bàn cờ gấp gọn, quân nam châm không rơi khi di chuyển.'),
('Bộ vẽ tranh cát 12 màu','Mỹ thuật sáng tạo',145000,70,5,9, 0,9,2,0, 500,28,22,5,'Rắc cát màu lên tranh dán sẵn, rèn sự khéo léo và thẩm mỹ.'),
('Bộ hóa học trẻ em 20 thí nghiệm','Khoa học thí nghiệm',395000,0,7,12, 4,4,6,9, 1500,38,28,10,'20 thí nghiệm an toàn kèm hướng dẫn tiếng Việt (đang hết hàng).'),
('Bảng nhân chia vui học','Toán học & số',160000,40,6,10, 9,1,5,8, 500,30,22,3,'Bảng bóc dán học bảng cửu chương qua trò chơi.'),
('Bộ xếp hình khối Soma 7 mảnh','Xếp hình & câu đố',140000,2,6,12, 9,4,9,3, 300,15,12,6,'7 mảnh ghép thành hàng trăm hình, rèn tư duy không gian.'),
('Đồ chơi lắp ráp động cơ gió','Khoa học thí nghiệm',335000,24,7,12, 3,5,6,9, 1000,34,26,9,'Lắp chong chóng và máy phát điện mini, học về năng lượng gió.'),
('Bộ mô hình khung xương khủng long','Mỹ thuật sáng tạo',215000,36,6,10, 2,7,4,6, 800,30,22,8,'Lắp ráp và tô màu khung xương khủng long, kèm thẻ kiến thức.'),
('Board game Cướp biển giải mã','Board game tư duy',380000,21,7,12, 8,3,8,2, 1100,30,30,8,'Giải mật mã tìm kho báu, chơi nhóm 2-4 người.'),
('Bộ thẻ lập trình thuật toán','Robot & lập trình',620000,14,6,10, 9,3,7,8, 900,28,22,8,'Sắp thẻ lệnh để điều khiển nhân vật, học tư duy thuật toán.'),
('Bóng rổ mini thả vòng để bàn','Vận động & phối hợp',270000,33,5,10, 2,2,4,1, 1200,40,30,12,'Rổ gắn tường hoặc để bàn, rèn phối hợp tay mắt.'),

-- ===== 9 - 12 tuổi =====
('Bộ lắp ráp mạch điện 100 thí nghiệm','Robot & lập trình',720000,19,8,13, 7,7,8,10, 1300,36,26,9,'Mô-đun mạch điện nam châm, thiết kế đèn, còi, radio theo ý bé.'),
('Kính thiên văn khúc xạ 70mm','Khoa học thí nghiệm',1450000,6,9,16, 3,2,5,10, 2800,70,20,16,'Quan sát Mặt Trăng và hành tinh, kèm chân đế và bản đồ sao.'),
('Rubik 4x4 Revenge','Xếp hình & câu đố',185000,55,9,18, 10,2,10,3, 200,8,8,8,'Rubik 4 lớp, thử thách kiên nhẫn và thuật toán.'),
('Bộ robot cánh tay thủy lực','Robot & lập trình',480000,17,8,14, 6,6,8,9, 1100,36,26,8,'Lắp cánh tay bơm thủy lực bằng ống tiêm, học về áp suất.'),
('Bộ lập trình Scratch robot lăn','Robot & lập trình',1390000,8,8,14, 9,6,9,10, 1200,34,26,12,'Lập trình robot bằng kéo thả, kết nối ứng dụng điện thoại.'),
('Board game Nhà đầu tư nhí','Board game tư duy',430000,23,8,14, 7,3,6,3, 1300,40,28,7,'Mua bán, quản lý tiền và đưa ra quyết định chiến lược.'),
('Bộ xây dựng thành phố nam châm 150 chi tiết','Bộ lắp ráp',780000,12,8,13, 6,10,8,7, 2500,42,32,14,'Khối nam châm xây thành phố, cầu, tòa nhà theo ý tưởng riêng.'),
('Bộ thí nghiệm điện hóa chanh pin','Khoa học thí nghiệm',250000,41,8,14, 3,3,5,9, 750,30,22,7,'Làm pin từ chanh và kim loại để thắp sáng bóng LED.'),
('Bút in 3D tạo hình sáng tạo','Mỹ thuật sáng tạo',650000,15,9,16, 3,10,6,7, 600,25,18,10,'Vẽ nổi mô hình 3D bằng nhựa nóng chảy (dùng có người lớn).'),
('Escape box phá án bí ẩn','Board game tư duy',350000,29,9,16, 9,4,10,2, 900,28,22,9,'Giải câu đố, mở khóa từng lớp để phá vụ án.'),
('Bộ toán hình học 3D que và khớp nối','Toán học & số',340000,27,8,14, 8,6,7,8, 1100,32,24,10,'Dựng khối đa diện từ que và khớp, hiểu hình học không gian.'),
('Bộ lắp ráp máy bay lượn cao su','Bộ lắp ráp',185000,5,8,12, 2,6,5,7, 400,45,20,6,'Lắp máy bay chạy bằng dây cao su, thử điều chỉnh cánh (sắp hết hàng).'),
('Bộ mô hình hệ Mặt Trời phát sáng','Khoa học thí nghiệm',295000,31,8,13, 2,6,3,8, 950,34,26,10,'Lắp và tô màu các hành tinh, đèn LED phát sáng trong tối.'),
('Đàn ukulele cho bé','Âm nhạc & giác quan',590000,16,8,15, 3,9,3,1, 1600,60,22,10,'Ukulele cỡ nhỏ kèm sách bài tập cho người mới.'),

-- ===== 13 - 18 tuổi =====
('Bộ Arduino Starter Kit cho học sinh','Robot & lập trình',1190000,11,13,18, 9,6,9,10, 1000,30,24,9,'Board Arduino, cảm biến, LED và 20 dự án mẫu.'),
('Bộ Raspberry Pi lập trình Python','Robot & lập trình',2390000,5,13,18, 10,7,10,10, 1100,32,24,10,'Máy tính nhúng kèm linh kiện, học Python và điều khiển phần cứng.'),
('Drone mini lập trình được','Robot & lập trình',1890000,7,12,18, 8,6,8,10, 800,30,26,10,'Drone nhỏ lập trình bay theo kịch bản bằng khối lệnh hoặc code.'),
('Rubik 5x5 chuyên nghiệp','Xếp hình & câu đố',260000,38,12,18, 10,2,10,3, 300,10,10,10,'Rubik 5 lớp nam châm, dành cho người chơi nâng cao.'),
('Bộ mô hình động cơ 4 thì','Bộ lắp ráp',1650000,4,13,18, 7,5,9,10, 2200,40,30,15,'Lắp động cơ 4 kỳ chạy thật, hiểu nguyên lý cơ khí (sắp hết hàng).'),
('Bộ hóa học nâng cao 60 thí nghiệm','Khoa học thí nghiệm',990000,13,13,18, 5,4,8,10, 2500,42,32,12,'Dụng cụ thủy tinh và hóa chất an toàn, 60 thí nghiệm chương trình THCS-THPT.'),
('Cờ Go nhập môn bàn gỗ','Board game tư duy',450000,19,12,18, 10,3,9,2, 1500,36,34,6,'Bàn cờ vây 19x19 kèm sách hướng dẫn cho người mới.'),
('Bộ vẽ kỹ thuật kiến trúc','Mỹ thuật sáng tạo',380000,25,12,18, 4,9,5,4, 1000,40,30,8,'Thước, compa, bút kỹ thuật để vẽ bản vẽ kiến trúc cơ bản.'),
('Máy tính khoa học kèm sách toán nâng cao','Toán học & số',520000,30,12,18, 8,2,7,9, 500,24,20,6,'Máy tính khoa học học sinh kèm bài tập toán tư duy.'),
('Hộp mật mã Cryptex giải đố','Xếp hình & câu đố',690000,10,12,18, 9,3,10,5, 800,28,10,10,'Hộp khóa chữ cái cần giải câu đố để mở, rèn tư duy suy luận.'),
('Bộ tên lửa nước thí nghiệm','Khoa học thí nghiệm',420000,16,10,16, 2,5,6,9, 1200,60,22,12,'Phóng tên lửa bằng áp suất nước, thử nghiệm góc bắn và lượng nước.'),
('Bộ trống điện tử mini','Âm nhạc & giác quan',1250000,8,10,18, 2,9,3,3, 3000,50,40,20,'Trống điện tử có tai nghe, nhiều bản nhạc mẫu để luyện tập.');

-- 3. Thêm sản phẩm (bỏ qua nếu đã có sản phẩm cùng tên)
INSERT INTO products (name, description, price, stock_quantity, min_age, max_age,
                      weight_grams, length_cm, width_cm, height_cm, active, created_at, category_id)
SELECT sp.name, sp.descr, sp.price, sp.stock, sp.min_age, sp.max_age,
       sp.weight, sp.len, sp.wid, sp.hei, TRUE,
       now() - (random() * interval '90 days'),
       c.id
FROM seed_products sp
JOIN categories c ON lower(c.name) = lower(sp.category)
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.name = sp.name);

-- 4. Chỉ số tác động kỹ năng (0 = không lưu dòng)
INSERT INTO product_skill_impacts (product_id, skill_id, impact_index)
SELECT p.id, s.id, v.val
FROM seed_products sp
JOIN products p ON p.name = sp.name
CROSS JOIN LATERAL (VALUES ('LOGIC', sp.logic), ('CREATIVE', sp.creative),
                           ('PROBLEM_SOLVING', sp.ps), ('STEM', sp.stem)) AS v(code, val)
JOIN skills s ON s.code = v.code
WHERE v.val > 0
ON CONFLICT (product_id, skill_id) DO NOTHING;

COMMIT;

-- Kiểm tra nhanh:
-- SELECT count(*) FROM products;
-- SELECT min_age, max_age, count(*) FROM products GROUP BY 1, 2 ORDER BY 1, 2;
