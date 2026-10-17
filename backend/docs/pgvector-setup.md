# Cài pgvector và chuyển DB sang tìm kiếm vector

Từ branch `feature/pgvector-semantic-search`, chatbot tìm đoạn tri thức (RAG) và sản phẩm theo ngữ nghĩa ngay trong
PostgreSQL bằng extension **pgvector**. Máy nào chạy backend với PostgreSQL local đều phải cài pgvector **một lần**.
Thiếu extension thì app **không khởi động** và báo:

```
IllegalStateException: PostgreSQL extension 'vector' (pgvector) is not installed. Run backend/docs/sql/pgvector-migration.sql ...
```

`./mvnw test` không cần pgvector (test chạy H2).

## Thứ tự làm (tóm tắt)

1. Cài pgvector cho PostgreSQL trên máy (mục 1), chỉ làm một lần.
2. Pull branch, chạy script `docs/sql/pgvector-migration.sql` trên DB `brainblocks` (mục 2).
3. Chạy backend như bình thường. Lần đầu khởi động, app tự embed sản phẩm ở chế độ nền (mục 3).

## 1. Cài pgvector trên Windows

pgvector phải được biên dịch đúng với bản PostgreSQL đang chạy. Nhóm dùng PostgreSQL 17 và pgvector **0.8.7**
(cần 0.8.0 trở lên vì app dùng `hnsw.iterative_scan`).

### 1.1. Cần có

- **Visual Studio 2022 trở lên** (Community) hoặc **Build Tools for Visual Studio**, có workload
  **"Desktop development with C++"**. Nếu chưa có thì mở Visual Studio Installer → Modify → tick workload này → Install.
- **Git** (để tải mã nguồn pgvector).
- Biết thư mục cài PostgreSQL, tức thư mục chứa `bin\psql.exe`. Thường là `C:\Program Files\PostgreSQL\17`, nhưng có
  máy cài ở chỗ khác (vd `D:\PostgreSQL17`). Xem trong Services: dịch vụ `postgresql-x64-17` → Properties → "Path to
  executable".

### 1.2. Biên dịch và cài

Mở **Command Prompt bằng quyền Administrator** (Start → gõ `cmd` → chuột phải → *Run as administrator*). Bước
`install` ghi file vào thư mục cài PostgreSQL nên cần quyền admin. Dùng **CMD**, không dùng PowerShell hay Git Bash, vì
`vcvars64.bat` chỉ thiết lập môi trường cho CMD.

**a) Tìm `vcvars64.bat` bằng `vswhere`** (công cụ đi kèm Visual Studio Installer):

```bat
"%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
```

Lệnh in ra thư mục cài Visual Studio, ví dụ `C:\Program Files\Microsoft Visual Studio\2022\Community` (bản mới hơn có thể là
`...\Microsoft Visual Studio\18\Community`). File cần chạy
nằm ở `<thư mục đó>\VC\Auxiliary\Build\vcvars64.bat`. Nếu lệnh không in gì thì máy chưa có workload C++ (xem 1.1).

**b) Nạp môi trường biên dịch C++ 64-bit** (thay đường dẫn bằng kết quả bước a):

```bat
call "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
```

Thấy dòng `[vcvarsall.bat] Environment initialized for: 'x64'` là được.

**c) Chỉ đường tới PostgreSQL** (thay bằng thư mục cài của máy mình, KHÔNG có `\bin` ở cuối, không có dấu `\` cuối):

```bat
set "PGROOT=C:\Program Files\PostgreSQL\17"
```

**d) Tải mã nguồn, biên dịch và cài:**

```bat
cd %TEMP%
git clone --branch v0.8.7 https://github.com/pgvector/pgvector.git
cd pgvector
nmake /F Makefile.win
nmake /F Makefile.win install
```

Các bước a–d phải chạy trong **cùng một cửa sổ CMD**: đóng cửa sổ thì phải chạy lại bước b và c.

### 1.3. Bật extension trong DB

```bat
"%PGROOT%\bin\psql.exe" -U postgres -h localhost -d brainblocks -c "CREATE EXTENSION IF NOT EXISTS vector;"
"%PGROOT%\bin\psql.exe" -U postgres -h localhost -d brainblocks -c "SELECT extversion FROM pg_extension WHERE extname = 'vector';"
```

Lệnh thứ hai phải in ra `0.8.7`. Không cần khởi động lại dịch vụ PostgreSQL. Script ở mục 2 cũng tự chạy
`CREATE EXTENSION IF NOT EXISTS vector`, nên có thể bỏ qua bước này nếu chạy script ngay.

### 1.4. Lỗi hay gặp khi cài

| Lỗi | Cách sửa |
|---|---|
| `'nmake' is not recognized` | Chưa chạy `vcvars64.bat` trong cửa sổ hiện tại (bước b), hoặc chưa cài workload C++. |
| `cannot open include file 'postgres.h'` | `PGROOT` sai: phải là thư mục chứa `bin`, `include`, `lib` của PostgreSQL. |
| `Access is denied` khi `install` | CMD chưa mở bằng *Run as administrator*. |
| `could not open extension control file ... vector.control` khi `CREATE EXTENSION` | Đã cài vào một bản PostgreSQL khác bản đang chạy: kiểm tra lại `PGROOT`, rồi chạy lại `install`. |
| `permission denied to create extension "vector"` | Chạy `CREATE EXTENSION` bằng user `postgres` (superuser). |
| Lỗi liên kết kiểu `LNK1112 machine type 'x86' conflicts with target 'x64'` | Đã chạy nhầm `vcvars32.bat`; mở CMD mới và chạy `vcvars64.bat`. |

## 2. Chạy script migration

Script `backend/docs/sql/pgvector-migration.sql` thực hiện:
- tạo extension `vector` nếu chưa có;
- đổi cột `document_chunks.embedding` từ `real[]` sang `vector(768)` bằng cast, giữ nguyên dữ liệu cũ (**không phải
  embed lại** tài liệu); nếu có vector không đủ 768 chiều thì dừng và không đổi gì;
- tạo bảng `product_embeddings`;
- tạo 2 index HNSW (`vector_cosine_ops`).

Toàn bộ script nằm trong một transaction và chạy lại nhiều lần vẫn an toàn. Chạy được trước hoặc sau lần khởi động đầu
với code mới. Từ thư mục `backend`:

```bat
"%PGROOT%\bin\psql.exe" -U postgres -h localhost -d brainblocks -v ON_ERROR_STOP=1 -f docs\sql\pgvector-migration.sql
```

Kết quả đúng kết thúc bằng `COMMIT`. Kiểm tra:

```bat
"%PGROOT%\bin\psql.exe" -U postgres -h localhost -d brainblocks -c "\d document_chunks"
```

Cột `embedding` phải có kiểu `vector(768)` và có index `idx_document_chunks_embedding_hnsw ... hnsw (embedding vector_cosine_ops)`.

> Có chạy app với code mới trước khi chạy script cũng không mất dữ liệu: `ddl-auto=update` của Hibernate tự thử đổi kiểu
> cột, chỉ ghi WARN nếu lệnh lỗi. Sau đó `PgVectorSchemaCheck` dừng app với thông báo chỉ tới script này. Hibernate
> không tạo cột trùng và không xóa dữ liệu.

## 3. Embed sản phẩm (backfill)

Không cần làm tay. Mỗi lần khởi động, `ProductEmbeddingBackfillRunner` chạy nền:
- duyệt các sản phẩm đang bán theo lô 50;
- chỉ embed những món chưa có vector hoặc có nội dung đã đổi (so `content_hash`);
- mỗi lô tối đa **1 request** Gemini, ví dụ 16 sản phẩm thì 1 request, 312 sản phẩm thì 7 request.

Log khi chạy:

```
Product embeddings: 16/16 checked, 16 refreshed     <- lần đầu
Product embeddings: 16/16 checked, 0 refreshed      <- các lần sau: không gọi Gemini
```

- Cần `GOOGLE_API_KEY` trong `backend/.env`. Thiếu key hoặc hết quota thì log `Product embedding backfill stopped`, app
  vẫn chạy bình thường: chatbot tạm tìm sản phẩm theo từ khóa như trước, lần khởi động sau backfill làm tiếp.
- Tắt backfill: thêm `PRODUCT_EMBEDDING_BACKFILL=false` vào `.env`.
- Admin thêm / sửa sản phẩm, đổi tên danh mục hoặc kỹ năng: vector tự cập nhật ở chế độ nền sau khi lưu.
- Kiểm tra nhanh: `SELECT count(*) FROM product_embeddings;` bằng số sản phẩm đang bán.

## 4. Tinh chỉnh (tùy chọn, trong `.env`)

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `KNOWLEDGE_MIN_SCORE` | 0.6 | Cosine tối thiểu để một đoạn tài liệu được đưa vào câu trả lời. |
| `CHAT_SEMANTIC_PRODUCTS_MIN_SCORE` | 0.65 | Cosine tối thiểu để sản phẩm gần nghĩa được thêm vào ứng viên. Đo trên dữ liệu mẫu: câu lạc đề ("xin chào", hỏi phí ship) ≤ 0.61, câu hỏi về đồ chơi ≥ 0.66. |
| `CHAT_SEMANTIC_PRODUCTS_LIMIT` | 8 | Số sản phẩm gần nghĩa tối đa mỗi câu hỏi. |
| `PRODUCT_EMBEDDING_BACKFILL` | true | Embed bù sản phẩm lúc khởi động. |

## 5. Test với pgvector thật (tùy chọn)

`./mvnw test` mặc định chạy trên H2 và không động tới pgvector. Muốn kiểm tra các câu SQL `<=>` thật thì tạo một DB
test **riêng**. Test sẽ tạo rồi xóa bảng trong DB đó, và tự từ chối chạy nếu trỏ vào `brainblocks`:

```bat
"%PGROOT%\bin\psql.exe" -U postgres -h localhost -c "CREATE DATABASE brainblocks_it;"
set PGVECTOR_TEST_URL=jdbc:postgresql://localhost:5432/brainblocks_it
set PGVECTOR_TEST_PASSWORD=<mật khẩu user postgres>
mvnw.cmd test -Ppgvector-it
```

Lệnh này chỉ chạy `PgVectorSearchIntegrationTest` (các test gắn tag `pgvector`).
